# Peregrine — Design & Build Guide (handoff)

Peregrine is a native Android (Kotlin + Jetpack Compose) client for **Frigate NVR**. This document is the single source of truth for the app's visual design and tells another agent how to (a) read the HTML mockups, (b) translate them into Compose, and (c) build *new* screens (e.g. Settings) that match what already exists.

Read this top to bottom before writing any UI code. If anything here conflicts with a mockup, this document wins.

---

## 0. Context you must load first

- **Design system skill**: a Material 3 (Material You) skill is vendored at `../material-3-skill/skills/material-3/`. Read its `SKILL.md` and the files in `references/` (color, typography/shape, components, navigation, layout). It is **Compose-first** and is the authority for anything not spelled out here.
- **The mockups** live in this folder (`/mockups`). They are **visual specs, not production code** — see §5 for how to use them.
- **Platform**: Jetpack Compose with `androidx.compose.material3`. Target a recent Material3 BOM. Do **not** build this in XML/Views.

---

## 1. The mockup files

| File | Screen | Notes |
|------|--------|-------|
| `live-view-md3-dark.html` | Live (home tab) | Active-alerts carousel, "All cameras" divider, single-column camera previews |
| `review-md3-dark.html` | Review tab | Alerts/Detections segmented control, Today/Yesterday groups, review cards |
| `explore-md3-dark.html` | Explore tab | Filter chips, results count/sort, 2-column event grid (no search box) |
| `camera-detail-md3-dark.html` | Single camera (pushed view) | Large feed, mic + speaker FABs, recent-activity strip, Pixel 8 proportions |
| `PEREGRINE-DESIGN-GUIDE.md` | This document | |

Settings is **not yet designed** — §6 tells you how to build it so it matches.

Each HTML file is a self-contained phone mockup: a black phone frame wrapping a `.lv-screen` (or `.cd-*`) that uses the real Material 3 dark tokens. Open them in a browser to see the intended result.

---

## 2. Design language

### 2.1 Principles
- **Dark Material You, always.** The app is dark-themed. Surfaces are near-black tonal greys; color comes from accents, not backgrounds.
- **Color is meaning.** Color is used to (a) encode detection categories and (b) signal the active/enabled state. Never decorative-rainbow.
- **Calm base, deliberate pops.** Most of the UI is neutral tonal surface. The "pops" (bright lavender nav indicator, color-coded detection chips, the alerts badge) stand out *because* the rest is restrained. Don't add color everywhere.
- **Rounded, soft, expressive.** Generous corner radii (cards 16–18dp), pill-shaped indicators, subtle motion.

### 2.2 Color tokens (dark scheme)
These are the exact values used across every mockup. Map them to `MaterialTheme.colorScheme` — do **not** hardcode hex in composables; define a `darkColorScheme(...)` once and use the role names.

| Role (Compose `colorScheme`) | Hex | Used for |
|------|-----|----------|
| `surface` | `#141218` | App background |
| `surfaceContainer` | `#211F26` | Nav bar, camera card bg, divider box |
| `surfaceContainerHigh` | `#2B2930` | Carousel cards, search/chip fills, hover |
| `onSurface` | `#E6E0E9` | Primary text/icons |
| `onSurfaceVariant` | `#CAC4D0` | Secondary text, inactive icons |
| `primary` | `#D0BCFF` | **The pop** — active nav pill, links, FAB-enabled, knobs |
| `onPrimary` | `#381E72` | Icon/text on `primary` fills |
| `secondaryContainer` | `#4A4458` | Tonal/segmented selection (lower emphasis) |
| `onSecondaryContainer` | `#E8DEF8` | Text on `secondaryContainer` |
| `outline` / `outlineVariant` | `#49454F` | Chip borders, divider hairlines |
| `errorContainer`-ish | `#FFB4AB` (bg) / `#690005` (text) | Alerts count badge (warm pop) |

Live "recording/LIVE" dot is `#FF5449` (a red pop used **only** for the live indicator).

> The seed is the Material baseline purple, so `primary` is `#D0BCFF` in dark. If you switch to Android 12+ **dynamic color** (wallpaper-based), keep the *semantics* identical — active nav = `primary`, etc. — and let the hues change. The category palette below is custom and should be **harmonized** to the active scheme rather than left fixed (see material-color-utilities `Blend.harmonize`).

### 2.3 Detection category palette (custom, harmonized)
Every detected object type has a hue with two states: a **container** (rest / chip-on-image) and a **bright** (selected / enabled / bounding box). Same hue, two tones. This is the backbone of the whole app's color identity — reuse it everywhere a detection appears (Live chips, Review chips, Explore chips & filters, bounding boxes).

| Category | Container bg / text | Bright (fill / on) | Bounding box border | Icon (Tabler / Material) |
|----------|--------------------|--------------------|--------------------|--------------------------|
| Person | `#4F378B` / `#EADDFF` | `#D0BCFF` / `#381E72` | `#D0BCFF` | person |
| Vehicle (car) | `#234B6E` / `#CFE5FF` | `#A6CBFF` / `#0A2A4D` | `#A6CBFF` | car |
| Animal (dog/cat) | `#2E4B33` / `#BCEFBE` | `#9BE0A0` / `#0A3311` | `#9BE0A0` | dog |

When you add new categories (bike, package, bird…) pick a new distinct hue and generate the same container/bright pair (container ≈ tone 30 fill + tone 90 text; bright ≈ tone 80 fill + tone 20 text in dark). Keep them harmonized with `primary`.

### 2.4 Typography
Roboto / Roboto Flex (the MD3 default — do **not** swap it out). Only **two weights**: 400 regular and 500 medium. Never 600/700.

| Element | Size | Weight | Compose style |
|---------|------|--------|---------------|
| Screen title (top app bar) | 22sp | 500 | `headlineSmall` / `titleLarge` |
| Camera detail title | 20sp | 500 | `titleLarge` |
| Section header ("Active alerts", "Recent activity") | 15sp | 500 | `titleMedium` |
| Body / chips / labels | 12–14sp | 500 | `labelLarge` / `bodyMedium` |
| Meta (times, counts) | 11–13sp | 400–500 | `labelMedium` / `bodySmall` |

Sentence case everywhere. Never Title Case, never ALL CAPS (the one exception is the small "LIVE" indicator on the feed).

### 2.5 Shape & spacing
- Corner radii: camera/preview cards **18dp**, carousel & grid cards **16dp**, chips/labels **7–9dp**, FAB **14dp**, nav active pill **16dp** (pill), section box **~11dp**.
- Screen horizontal padding: **16dp**.
- Vertical gap between stacked cards: **14dp**; grid gap **10dp**.
- Everything sits on an **8dp spacing grid** (MD3 expressive). Touch targets ≥ 48dp.

### 2.6 Motion
Subtle only. A slow light "scan" sweep across active previews (~5s loop) signals a live/active feed without faking a stream; the LIVE dot blinks (~1.6s). Use spring-based MD3 motion for component transitions; keep ambient animation low-contrast.

---

## 3. Components (spec → Compose mapping)

### 3.1 Bottom navigation — `NavigationBar`
- Four destinations: **Live · Review · Explore · Settings**. This is the correct MD3 component for 3–5 destinations on a compact (phone) width.
- Active indicator pill: **`primary` (`#D0BCFF`) fill with `onPrimary` (`#381E72`) icon**, and the active label tinted `primary`. This is a deliberate expressive upgrade from MD3's default `secondaryContainer` indicator — it's the app's signature pop. Keep it.
- Bar background: `surfaceContainer`. Icons: `video` (Live), history/`player-track-prev` (Review), `compass`/explore (Explore), `settings` (Settings).
- **Adaptive rule:** bottom bar on compact width only. At ≥600dp move these four to a `NavigationRail` (left), and at ≥840dp a navigation drawer. Don't scale the phone bar up.

```kotlin
NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
    destinations.forEach { d ->
        NavigationBarItem(
            selected = d.route == current,
            onClick = { nav.navigate(d.route) },
            icon = { Icon(d.icon, contentDescription = d.label) },
            label = { Text(d.label) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primary,
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        )
    }
}
```

### 3.2 Top app bar — `TopAppBar`
Start-aligned title, `surface` background. Live/Review/Explore use a plain title + trailing action icons. The camera detail uses a **leading back arrow** (it's a pushed view) and **no bottom nav**.

### 3.3 Camera preview card (Live)
Rounded 18dp `Card`/`Box`, `surfaceContainer` bg, the feed fills it. Overlays: camera-name pill bottom-left (translucent dark scrim `rgba(20,18,24,0.62)`, `onSurface` text), color-coded detection chip bottom-right, optional detection **bounding box** (2dp border in the category's bright color, tiny label tab top-left of the box). The "active preview" scan sweep lives here.

### 3.4 Detection chip
Small rounded (7–9dp) chip. On an image use the **container** colors (e.g. person `#4F378B`/`#EADDFF`). As an Explore filter when selected, use the same container fill; unselected = outlined (`outline` border, `onSurfaceVariant` text). Leading category icon, 12–14sp/500 label.

### 3.5 Alerts carousel (Live)
Horizontal `LazyRow` under the app bar. Header row: "Active alerts" (`titleMedium`) + a **count badge** (`#FFB4AB` bg / `#690005` text, the warm pop) + a `primary` "Review all ›" text button that deep-links to the Review tab. Cards are 130dp wide, `surfaceContainerHigh`, 16dp radius: thumbnail (84dp) with a color-coded category chip top-left, then a meta row (camera name + relative time).

### 3.6 Section divider ("All cameras")
A lighter `surfaceContainer` **boxed label** (camera icon + text, 11dp radius) on the left, with a 1px `outlineVariant` hairline filling the rest of the row. Separates the alerts section from the camera list. Do **not** show invented data here (no camera count / online status — Frigate doesn't reliably expose per-camera online state to the client).

### 3.7 Review screen pieces
- **Segmented control** (Alerts | Detections): MD3 `SingleChoiceSegmentedButtonRow`. Selected segment = `secondaryContainer` / `onSecondaryContainer` with a leading check. This split mirrors Frigate's review model.
- **Date group headers** ("Today", "Yesterday"): `onSurfaceVariant` 13sp/500 label + hairline.
- **Review item card**: like a camera card (18dp, feed thumbnail), with category chip top-left, camera-name pill bottom-left, time bottom-right, and a small **`primary` "unreviewed" dot** top-right for new items.

### 3.8 Explore screen pieces
- **Filter chips** `LazyRow`: a leading "Filters" chip (filter icon, opens the full camera/label/zone/time filter sheet), then label chips that use the category container color when selected. No free-text search box (removed by request).
- **Count + sort row**: "142 events" (`onSurfaceVariant`) left, a sort affordance ("Newest" + sort icon) right.
- **Event grid**: 2-column `LazyVerticalGrid`, 16dp cards, each a thumbnail with category chip + camera name + time overlays. Tap → event/camera detail.

### 3.9 Camera detail FABs (two-way audio)
Two **equal-size 48dp** FABs, 14dp radius, 26sp icon (icon should fill the shape — avoid big-box/small-icon). Placed in a right-aligned row **just under the feed** (not over it). Each is a **toggle with two states of the same hue**:

| FAB | Rest (container) | Enabled (bright pop) | Icon |
|-----|------------------|----------------------|------|
| Mic (talk) | `#4F378B` / `#EADDFF` | `#D0BCFF` / `#381E72` | microphone |
| Speaker (listen) | `#633B48` / `#FFD8E4` | `#EFB8C8` / `#492532` | volume |

Rest = tonal colored container; enabled = bright fill (the "additional pop"). Use `aria`/state semantics; in Compose drive the colors off a `remember`'d boolean and animate the color.

### 3.10 Camera detail layout (Pixel 8)
Solid top app bar **above** the feed (feed must not sit behind the bar). Feed ~208dp tall with LIVE pill (top-left, blinking red dot), running timestamp (bottom-left), detection box. Then the FAB row, then **open whitespace**, then a "Recent activity" `LazyRow` anchored to the bottom. The screen is sized to ~20:9 (Pixel 8) so the whitespace is honest — represent real device proportions in any new mockup.

---

## 4. The Compose theme (define once)

```kotlin
private val PeregrineDark = darkColorScheme(
    surface = Color(0xFF141218),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    outline = Color(0xFF49454F),
    outlineVariant = Color(0xFF49454F),
)

// Custom detection palette — keep OUTSIDE colorScheme, expose via a small object.
object DetectionColors {
    data class Pair(val container: Color, val onContainer: Color, val bright: Color, val onBright: Color)
    val Person  = Pair(Color(0xFF4F378B), Color(0xFFEADDFF), Color(0xFFD0BCFF), Color(0xFF381E72))
    val Vehicle = Pair(Color(0xFF234B6E), Color(0xFFCFE5FF), Color(0xFFA6CBFF), Color(0xFF0A2A4D))
    val Animal  = Pair(Color(0xFF2E4B33), Color(0xFFBCEFBE), Color(0xFF9BE0A0), Color(0xFF0A3311))
}

@Composable
fun PeregrineTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = PeregrineDark, typography = PeregrineType, content = content)
```

Force dark (or default to it). If you later support dynamic color, branch on API 31+ with `dynamicDarkColorScheme(context)` but keep the dark-only stance and harmonize `DetectionColors` to the resulting scheme.

---

## 5. How to use the HTML mockups

1. **They are specifications, not source.** Do not port HTML/CSS into a WebView or transpile it. Rebuild every screen with native composables.
2. **Read the CSS for exact numbers** — colors, radii, paddings, sizes are all real and intentional. The `--token` custom properties in `.lv-screen` map 1:1 to the `colorScheme` roles in §2.2; the `.cat-*` classes map to `DetectionColors`.
3. **Class → composable cheatsheet:** `.lv-nav` → `NavigationBar`; `.lv-bar`/`.cd-bar` → `TopAppBar`; `.lv-cam`/`.rv-item`/`.ex-card` → `Card` with a `Box` overlay; `.lv-car`/`.ex-grid` → `LazyRow`/`LazyVerticalGrid`; `.lv-acat`/`.ex-cat`/`.lv-det` → detection chip; `.cd-fab` → `FloatingActionButton`; `.lv-sep` → boxed-label + `HorizontalDivider`.
4. **The phone frame and scenes are mock-only.** The black bezel, the fake "camera scenes" (stacked colored `div`s) and the status bar exist only to make the mockup readable. In the app, the feed is a real player surface (go2rtc/WebRTC/MSE) and thumbnails are Frigate snapshot images.
5. **Match proportions.** Build and preview on a Pixel 8-class device (~20:9). Expect and allow whitespace.

---

## 6. Worked example — building the **Settings** page to match

Settings isn't mocked yet. Build it like this so it's indistinguishable from a screen I designed:

**Structure**
- `Scaffold` with the standard `NavigationBar` (Settings item active → `primary` pill), and a `TopAppBar` titled "Settings" (22sp/500, `surface`).
- Body = a single scrolling `LazyColumn` of **grouped settings**, 16dp horizontal padding.

**Grouping pattern**
- Each group starts with a section header in `onSurfaceVariant` 13–15sp/500 (same style as "Today"/"Active alerts"), optionally with a hairline — reuse the §3.6 divider treatment.
- Group the rows inside a `surfaceContainer` rounded **16dp** card (or plain list with dividers — prefer the card for the soft, grouped Material You look).

**List rows** (`ListItem`-style)
- Leading: a category-tinted icon in a small rounded container when it helps (e.g. a `primary`-tinted circle), otherwise a plain `onSurfaceVariant` icon.
- Headline `onSurface` 14–16sp/500, supporting text `onSurfaceVariant` 13sp/400.
- Trailing: `Switch` (track/thumb use `primary` when on — that's the pop), or a value + chevron for drill-ins, or a small chip.

**Suggested Frigate-appropriate groups** (rebuild from real config, don't invent status):
- *Server*: Frigate URL, connection status, API/token.
- *Notifications*: enable, per-camera, per-object-type (reuse the category chips/colors here for object toggles).
- *Cameras*: list of cameras → each drills into per-camera settings (streams, audio/two-way, recording).
- *Appearance*: theme (System/Dark — default Dark), dynamic color toggle (Android 12+).
- *About*: version, links, logs.

**Color usage in Settings**
- Backgrounds neutral (`surface` / `surfaceContainer`). The only pops: `Switch` on-state and any selected chips use `primary`; object-type toggles may use `DetectionColors` to stay consistent with the rest of the app. Don't introduce new accent hues.

**Example skeleton**

```kotlin
Scaffold(
    topBar = { TopAppBar(title = { Text("Settings") }) },
    bottomBar = { PeregrineNavBar(current = Route.Settings) },
    containerColor = MaterialTheme.colorScheme.surface,
) { pad ->
    LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp)) {
        item { SectionHeader("Server") }
        item {
            SettingsGroup {
                SettingRow(icon = Icons.Outlined.Dns, title = "Frigate server",
                    subtitle = "https://frigate.local:5000", onClick = { /* … */ })
                SettingToggle(icon = Icons.Outlined.Notifications, title = "Notifications",
                    checked = notif, onCheckedChange = { notif = it })
            }
        }
        item { SectionHeader("Appearance") }
        // …
    }
}
```
where `SettingsGroup` is a `Surface(color = surfaceContainer, shape = RoundedCornerShape(16.dp))` wrapping rows, and `SectionHeader` matches §3.6.

---

## 7. Consistency rules (do / don't)

**Do**
- Reuse the exact tokens in §2.2 and the category palette in §2.3.
- Keep the bright-`primary` nav indicator as the signature pop.
- Use the container→bright two-state pattern for any toggle (FABs, selected chips).
- Keep corners soft, text sentence-case, weights 400/500 only.
- Anchor scroll lists, allow honest whitespace, use `LazyRow`/`LazyColumn`/`LazyVerticalGrid`.
- Gate features on real Frigate capabilities (see §8).

**Don't**
- Don't add a light theme unless asked; this is a dark-first app.
- Don't introduce new accent colors outside the defined palette.
- Don't hardcode hex in composables — go through `colorScheme` / `DetectionColors`.
- Don't show data the backend can't provide (camera online counts, etc.).
- Don't use bottom tabs on tablet widths — switch to rail/drawer.
- Don't fake a live stream with heavy effects; the subtle scan sweep is enough.

---

## 8. Frigate data/capability notes
- **Alerts carousel & Review** use Frigate's review/events API (real). Counts and timestamps are safe.
- **Detection chips/boxes** come from tracked-object labels + scores (real).
- **Two-way audio (mic FAB)** requires go2rtc + a camera that supports it — show/hide per camera config.
- **Timeline scrubbing / recordings** require recordings enabled per camera.
- **Per-camera "online" status** is not reliably exposed — avoid UI that asserts it.
- **Explore** maps to Frigate's tracked-object explorer; the "Filters" chip opens camera/label/zone/time filters. (Semantic/natural-language search exists in Frigate but the manual search box was intentionally omitted from this client.)

---

## 9. Quick start for the next agent
1. Read `../material-3-skill/skills/material-3/SKILL.md` + references.
2. Open all four HTML mockups in a browser.
3. Implement §4 theme.
4. Build shared components first: `PeregrineNavBar`, detection chip, camera card, carousel, section header/divider.
5. Build screens: Live → Review → Explore → camera detail → (new) Settings per §6.
6. Verify against the mockups on a Pixel 8 emulator; check every color resolves through `colorScheme`/`DetectionColors` (mental test: would it still read on near-black?).
