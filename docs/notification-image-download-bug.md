# Bug brief: FCM notification thumbnail fails to load when the app is closed

Status: **investigation only, no code changes made yet.** This document is a handoff summary of everything found so far, written for someone (or some agent) with zero prior context on this issue.

## 1. The symptom

Peregrine (this Frigate NVR Android client) receives push alerts via `PeregrineMessagingService` (`app/src/main/java/com/rayner/peregrine/data/remote/messaging/PeregrineMessagingService.kt`). Each Frigate event notification is supposed to:

1. Post a bare notification immediately (title/body/actions, no image).
2. Asynchronously download the event's thumbnail image and update the notification in place with a `BigPictureStyle` image.

The bug: **when the app process is fully closed (killed) before the FCM message arrives, the notification appears but the thumbnail never loads.** When the same captured payload is resent while the app is open (foregrounded), the image loads immediately. This used to work reliably; it broke at some point in the last several commits. The user suspects (but is not certain) it's connected to the "Show latest only" notification-grouping feature.

A controlled test was done: the user has a tool that captures real Frigate review payloads and can resend a single one on demand via FCM. Sending **one isolated message** (no other notifications in flight) to the closed app reproduced the bug. This is important — it rules out any theory that depends on *multiple* messages racing/colliding with each other.

## 2. Where the image is supposed to come from

Frigate sends the thumbnail as a relative path in the FCM data payload, keyed `image` (e.g. `/api/events/<id>/thumbnail.jpg`), to be resolved against the configured server's base URL. The client should prefer this `image` property. Current code (lines 110–117) builds a candidate list and tries them in order:

```kotlin
val imageCandidates = listOfNotNull(
    data["image"],
    data["photo"],
    data["thumbnail"],
    data["snapshot"],
    data["image_url"],
    remoteMessage.notification?.imageUrl?.toString()
).distinct()
```

This part of the contract is not in question — `data["image"]` (the thumbnail.jpg URL) is tried first, as expected.

## 3. Code path for a single incoming message (current HEAD)

File: `PeregrineMessagingService.kt`

1. `onCreate()` — nothing notable.
2. `onMessageReceived(remoteMessage)`:
   - Line 53–55: `runBlocking { repository.restorePersistedAuthCookie() }` — synchronously hydrates the in-memory `CookieJar` from whatever's persisted in Room, *before* any other work. This is a pure restore, not a refresh/login (see §4).
   - Extracts `title`, `body`, `url`, `tag` (Frigate event id), `group`, `status`, `alertOnce` from the data payload.
   - Loads `prefs` (includes `showLatestOnly`) from the preferences DB.
   - If `status != "new"` and there's a `tag`, checks whether that tag is still an active notification in the tray; bails out early if not (this is the "ignore stale updates" guard — irrelevant to a brand-new single message, since `status == "new"` skips it).
   - Computes `channelId` (= `group`, or a default), creates the notification channel.
   - Computes `notificationTag`/`notificationId` (lines 100–104): if `showLatestOnly`, uses `channelId` + `0` for every event in that group; otherwise uses the event's own `tag` + `0`, or a random id if no tag.
   - **Line 107**: posts the bare notification immediately via `sendRichNotification(...)`.
   - **Lines 119–169**: if there's an image candidate, enters a `runBlocking` block that:
     - Resolves the base server URL from `repository.getServerConfig()`.
     - Loops up to 3 times, cycling through image candidates, each attempt wrapped in `withTimeoutOrNull(8000)` (an 8-second hard cap per attempt), with a growing delay between retries (`1500ms * retry`).
     - Uses Coil (`imageLoader.execute(request)`) to fetch and decode the bitmap.
   - **Lines 171–186**: if a bitmap was obtained, re-checks whether the original bare notification is *still* the active one in the tray (the `stillActive` guard, keyed the same way as the tag/id logic above). If yes, calls `sendRichNotification(...)` again with the bitmap to update it. **If no, the image is silently discarded** — only a debug log line, no retry, no fallback.

Worst-case blocking time for step 2's retry loop: roughly 8s + 1.5s + 8s + 3s + 8s ≈ **28.5 seconds**, all inside a synchronous `runBlocking` call inside `onMessageReceived`.

## 4. The auth/cookie machinery involved in the image fetch

The thumbnail URL lives on the same Frigate server as the authenticated API, and the image request goes through **the same shared `OkHttpClient`** as all other API calls (confirmed: `di/NetworkModule.kt` builds one `@Singleton OkHttpClient` with the shared `CookieJar`, and `provideImageLoader()` wires that *same* client instance into Coil via `OkHttpNetworkFetcherFactory`). So whatever auth state the app is in applies equally to API calls and thumbnail downloads.

Three pieces cooperate to keep that client authenticated:

- **`FrigateRepositoryImpl.restorePersistedAuthCookie()`** (`data/repository/FrigateRepositoryImpl.kt`, ~lines 64–84): loads the persisted `authCookie`/`authCookieExpiresAt`/`serverUrl` from Room and injects them into the live `CookieJar`. It **silently no-ops** (does nothing, no error, no login attempt) if any of: no server config row, no `authCookie`, no `authCookieExpiresAt`, or `authCookieExpiresAt <= now`. It never attempts to log in — it's a pure restore of whatever was last durably saved.

- **`FrigateAuthenticator`** (`data/remote/api/FrigateAuthenticator.kt`): an OkHttp `Authenticator` that fires automatically whenever any request on the shared client gets a 401. It reads `username`/`encryptedPassword` from the Room server-config row (`ServerConfigEntity`, both nullable) and attempts a fresh login. **If those credentials aren't stored, it returns `null` at lines 35–36 and the 401 stands** — Coil then surfaces an `ErrorResult` and the image fetch fails outright, with no further recovery.

- **`CookiePersistenceInterceptor`** (`data/remote/api/CookiePersistenceInterceptor.kt`): a plain OkHttp `Interceptor` that runs on every response through the shared client. If the response carries a `frigate_token` cookie that differs from what's in Room, it should persist the new value/expiry back to Room so future cold starts can restore it. **This write is fire-and-forget**:

  ```kotlin
  private val scope = CoroutineScope(Dispatchers.IO)   // line 26, no lifecycle ties

  override fun intercept(chain: Interceptor.Chain): Response {
      val response = chain.proceed(request)
      ...
      if (authCookie != null) {
          scope.launch {                                // line 39
              ...
              serverConfigDao.insertServerConfig(...)     // never awaited
          }
      }
      return response                                     // returns before the write lands
  }
  ```

  `intercept()` returns before this launch completes. Confirmed still present, unchanged, in current HEAD.

## 5. Timeline of relevant commits (for context, not blame)

| Commit | Date | What changed |
|---|---|---|
| `c3205dc` | Jun 26 | First image-fetch implementation: single attempt, 6s timeout, fetched *before* posting the notification (single-shot, no tag/id tracking). |
| `b1e116d` | Jun 27 | Switched to "post immediately, then update" two-phase pattern. Single attempt, 8s timeout. Added `restorePersistedAuthCookie()` call (with a comment noting it's "crucial if the app process was cold-started"). |
| **`5dfbcd1`** | Jul 3 | **Introduced the whole cookie-persistence/auto-relogin system**: added `CookiePersistenceInterceptor` (fire-and-forget write, §4) and `FrigateAuthenticator` (at this point, its login helper used a throwaway `OkHttpClient` **without** the shared `CookieJar` attached — so even a successful auto-relogin never actually got applied to the retried request; the code's own comment acknowledged this gap). Wired both into `NetworkModule`. |
| `2fbb795` | Jul 10 | Added notification grouping/channels (`group`, `status`, `channelId`) and switched to `notify(tag, id, ...)`. **Does not touch timeouts, retries, or any auth code.** |
| `ef7af74` | Jul 21 | Added `showLatestOnly` tag/id collapsing logic (§3) and the `isActive`/early-return guard. Added the retry loop with backoff (3 attempts, 10s timeout initially). **Also fixed** the `FrigateAuthenticator` cookie-jar gap from `5dfbcd1` — attached the shared `CookieJar` to the login client and added an explicit, properly-`suspend`-awaited `persistAuthCookie()` call after a successful re-login. |
| `37b6fff` | Jul 22 | Tuned retry timeout back to 8s/attempt, added the `stillActive` guard before posting the image update (§3), added multiple image-candidate keys, moved `restorePersistedAuthCookie()` to the very top of `onMessageReceived`. Commit message: "Notification images still causing issues. Improvements" — i.e. this was already a second attempt at a fix that didn't fully resolve it. |

Two things worth noting from this table: the grouping commit (`2fbb795`) itself doesn't touch anything download/auth/timing related, so it's unlikely to be the mechanism on its own. The auth/cookie system's known gap (missing cookie jar on the re-login client) predates grouping by a week and was later half-fixed in `ef7af74` — the fire-and-forget Room write from `5dfbcd1` was never fixed and is still present today.

## 6. Leading hypotheses

Both hypotheses below predict the exact same observable behavior as the two manual tests done so far (fails when closed, works instantly when reopened and resent) — **neither test distinguishes between them.** They are not mutually exclusive; both could be contributing.

### Hypothesis A — stale/lost persisted auth cookie

Mechanism: the fire-and-forget Room write in `CookiePersistenceInterceptor` (§4) means a freshly-rotated `frigate_token` cookie obtained during one FCM-triggered cold start may never get durably saved before Android reclaims that short-lived process. The next cold start's `restorePersistedAuthCookie()` then restores a stale/superseded cookie (or one that's already past `authCookieExpiresAt`, causing it to no-op entirely with zero auth). The image request 401s. Recovery via `FrigateAuthenticator` only works if `username`/`encryptedPassword` are actually stored in Room (nullable fields) — if not, or if some other gap remains, the 401 stands and Coil returns an error.

A running foregrounded app never depends on this path — its live in-memory `CookieJar` is already valid from normal use, so it never touches the DB restore or the authenticator at all.

**Confidence:** medium-high. This is the strongest concretely-verified defect in the codebase that specifically affects the *cold-start-only* path and nothing else.

### Hypothesis B — cold radio / DNS / TLS latency exceeding the per-attempt timeout

Mechanism: waking a killed process via a high-priority FCM message grants temporary execution and network access, but a genuinely Doze-idle device may need real wall-clock time to bring the radio back up, re-resolve DNS, and complete a TLS handshake before the image transfer even starts. Each attempt is hard-capped at `withTimeoutOrNull(8000)` (line 145 in current HEAD). If cold-start network setup alone eats several seconds, an 8s budget may not be enough, causing every attempt to time out. A foregrounded app already has a warm radio, cached DNS, and a warm connection pool, so the same request finishes almost instantly.

Supporting detail: the original single-attempt implementations (`c3205dc`, `b1e116d`) used a similar or shorter timeout and reportedly worked fine; the retry loop added later (`ef7af74`, `37b6fff`) increased *total* worst-case blocking to ~28.5s without necessarily fixing the underlying per-attempt time budget, and arguably increases risk of the whole process being killed mid-retry by the OS before anything succeeds.

**Confidence:** medium. Plausible and consistent with known Android battery-optimization behavior, but less directly evidenced in the code than Hypothesis A.

### Related but distinct bug (confirmed real, but ruled out as the cause of this specific single-message reproduction)

Under `showLatestOnly`, every notification in a group shares the same system tag+id (`channelId`, `0`) (§3, lines 100–104). If a second event for the same group arrives while an earlier one's image download is still in flight, its immediate bare-notification post overwrites the tray entry, and the `stillActive` guard (added in `37b6fff`) then correctly detects the mismatch and silently drops the first event's image update. This is a real bug for bursts of events (which do happen — Frigate can fire 2–3 messages/second for one camera, and a closed app reconnecting after a while commonly receives a batch of queued events at once). **However, the user's single-message test proves this mechanism is not what's reproducing right now**, since there's no second message to cause a collision. Worth fixing regardless, just not the current lead.

## 7. Recommended diagnostic step before fixing anything

Reproduce with the app fully closed, capture `adb logcat` filtered to tags `PeregrineFCM` and `FrigateAuth`, and look at the line logged at `PeregrineMessagingService.kt:158`:

```kotlin
Log.w(TAG, "Image fetch failed (Attempt ${retry + 1}): $error")
```

- If `$error` shows an HTTP 401/403 or an auth-related message → Hypothesis A (auth/cookie).
- If `$error` shows a timeout / `SocketTimeoutException` / `UnknownHostException` / `ConnectException` with no HTTP response at all → Hypothesis B (network/Doze timing).

Also worth a direct read of the `server_config` table in Room (via `adb shell` + `sqlite3` on the app's DB, or a debug screen) to check: is `username`/`encryptedPassword` actually populated (required for Hypothesis A's auto-recovery to ever work)? Does `authCookieExpiresAt` look stale/near-expired relative to when the test was run?

## 8. Candidate fixes (not yet applied — pick based on diagnostic results)

### If Hypothesis A (auth/cookie) is confirmed

1. Make `CookiePersistenceInterceptor`'s Room write synchronous before `intercept()` returns (e.g. block on the DAO call, since this runs on an OkHttp dispatcher thread, not the main thread) so a freshly-rotated cookie is guaranteed durable before the calling code — and the FCM service — can finish and let the process die.
2. Instrument the interceptor with logging (success/failure/timing) so this failure mode is observable without needing a manual repro session.
3. Confirm `username`/`encryptedPassword` are actually being stored by the app's login flow for this deployment; if the app supports login without persisting credentials, `FrigateAuthenticator` can never auto-recover from an expired cookie in *any* context, which would need a different fix (e.g. persisting credentials, or prompting for re-auth and queuing the notification image for retry once re-authenticated).
4. Consider having `restorePersistedAuthCookie()` (or the FCM handler) proactively verify the cookie is still valid / trigger a refresh rather than passively restoring and hoping, especially given it currently no-ops silently on missing/expired state with no fallback.

### If Hypothesis B (network/timeout) is confirmed

1. Lengthen the per-attempt timeout for the FCM-triggered download (especially the first attempt) to account for cold radio/DNS/TLS latency — possibly at the cost of fewer total retries, since Android's execution grace period for a woken process is finite and retries mostly just consume that budget rather than improving success odds on a fundamentally cold radio.
2. Investigate whether a dedicated wake lock (or brief foreground-service promotion) is needed to guarantee execution time for the download beyond whatever FCM's own temporary exemption grants.
3. Instrument each attempt with elapsed wall-clock timing logs to empirically see how long a genuinely cold attempt takes, and right-size the timeout from real data rather than guessing.
4. Consider reverting to something closer to the original single-attempt design (which reportedly worked) if the retry loop turns out to just be adding overhead without improving reliability.

### For the separate group-collision bug (§6, "related but distinct")

1. Stop overloading the system notification's `(tag, id)` for tracking "is this event's download still relevant" under `showLatestOnly`. Give each event its own unique tag/id for the purposes of posting/updating asynchronously, and achieve the "only show the latest per group" UX via `NotificationCompat`'s native grouping (`setGroup` + a `setGroupSummary` notification) instead of literally overwriting the same tray slot. This lets each event's own image download complete and apply without being clobbered by a sibling event in the same burst.

## 9. What's explicitly ruled out

- Burst/multi-message tag collisions as the cause of *this specific* single-message reproduction (confirmed by the user's controlled test).
- `2fbb795` (the grouping/channel commit) as a direct mechanism — its diff touches only tag/channel plumbing, no timeouts, retries, or auth code.
- The `data["image"]` candidate ordering/selection logic — this is unchanged and correctly prioritizes the Frigate-provided thumbnail URL; not the source of the bug.
