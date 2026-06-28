# Fix Random Focus When Returning to Main Page

The issue where the focused item seems "random" (often jumping to the 3rd item) when returning to the main page from the alerts carousel is likely caused by a combination of factors:
1. The `LiveViewModel`'s `uiState` flow uses `WhileSubscribed(5000)`, which can cause it to restart and emit an initial empty state if the user spends more than 5 seconds in the detail view.
2. The `AlertsCarousel` is removed from the `LazyVerticalGrid` when its data is empty, causing its internal `scrollState` to be lost.
3. The `LazyVerticalGrid` items lack stable keys, which can cause the grid to reshuffle focus when items (like the carousel) are added or removed.

## Proposed Changes

### UI Layer

#### [LiveViewScreen.kt](file:///Users/dan/AndroidStudioProjects/Peregrine/app/src/main/java/com/rayner/peregrine/ui/screens/live/LiveViewScreen.kt)

- Add stable keys to all items in the `LazyVerticalGrid` within `LiveHomeContent`.
- Keep the `AlertsCarousel` item in the grid even if empty (but render nothing inside) to maintain stable item indices and keys.
- Move the carousel's `LazyListState` to `LiveHomeContent` and use `rememberLazyListState()` to ensure it persists as long as the main content is in the composition.
- Pass this `scrollState` to `AlertsCarousel`.

```kotlin
// In LiveHomeContent
val carouselScrollState = rememberLazyListState()

LazyVerticalGrid(...) {
    item(key = "alerts_carousel") {
        if (uiState.activeReviews.isNotEmpty()) {
            AlertsCarousel(
                reviews = uiState.activeReviews,
                scrollState = carouselScrollState,
                // ...
            )
        }
    }
    item(key = "section_divider") {
        SectionDivider("All cameras")
    }
    items(uiState.cameras, key = { it.name }) { camera ->
        // ...
    }
}
```

### ViewModel Layer

#### [LiveViewModel.kt](file:///Users/dan/AndroidStudioProjects/Peregrine/app/src/main/java/com/rayner/peregrine/ui/screens/live/LiveViewModel.kt)

- Change `SharingStarted.WhileSubscribed(5000)` to `SharingStarted.Lazily`. This ensures the `uiState` is preserved in memory while the user is in the detail view, preventing the "empty flash" when they return.
- Trigger `loadData()` in `onResume()` to ensure the list is refreshed when the user returns, while the existing data is still shown.

## Verification Plan

### Automated Tests
- I will check if there are any UI tests for `LiveViewScreen` and update them if they depend on the carousel's presence.
- Since this is a focus/scroll issue, manual verification is more effective.

### Manual Verification
1. Open the app and scroll the Recent Alerts carousel.
2. Click on an alert to view it.
3. Stay in the alert view for more than 5 seconds.
4. Swipe back to the main page.
5. Verify that the carousel is at the same scroll position and focus is not lost or jumped to a random item.
6. Verify that the carousel correctly updates (the viewed item is removed and others shift) without jumping to the 3rd item.
