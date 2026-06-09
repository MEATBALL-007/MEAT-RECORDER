# Top-level Record / Library section pager

**Date:** 2026-06-09
**Status:** Approved design, pending implementation plan.

## Problem

The home screen (`MeatRecHome`, ~1,093 lines, hosted by `RecorderApp`) packs two distinct jobs into one vertically-scrolling body: the **recording** controls (hero record button, live meters, active-recording section, input-gain / live-EQ sliders, live toggles) flow straight into the **library** (recordings toolbar, search/filter, file list, bulk-select). Users scroll a long screen to get from "record" to "browse my takes," and `MeatRecHome` is a monolith that's hard to reason about or test.

This design adds a **horizontal section pager** that splits the home into two swipeable pages — **Record** and **Library** — selectable by swipe or by a tappable segmented control, and decomposes `MeatRecHome` into a thin scaffold plus two focused page composables.

## Scope decision

In scope: split the existing home **body** into a `Record` page and a `Library` page behind a 2-page `HorizontalPager`, add a segmented-pill control, and extract the two page bodies into their own composables.

Out of scope (unchanged): the shared orange top bar, the bottom mini-player (stays an overlay in `RecorderApp`), all pushed screens (EQ, Settings, Stats, etc., still driven by the `when {}` in `MainActivity`), and all ViewModel state. No navigation library is introduced; this is local UI state only.

## Decisions (from brainstorming)

- **Pages:** two — `Record` | `Library`. Settings/EQ remain pushed screens, not pager pages (they have their own scroll/back semantics).
- **Affordance:** a **segmented pill** at the top (in the app's yellow accent), tappable *and* swipeable. The underline/highlight tracks the current page.
- **Landing:** **always open on Record** (`initialPage = 0`). No cross-launch persistence.
- **Mini-player:** **pinned across both pages.** It already lives in `RecorderApp` as a bottom overlay over `MeatRecHome`, so this is preserved with no change.
- **Implementation approach:** pager lives **inside `MeatRecHome`**; the two page bodies are **extracted** into new composables (Approach 1). `RecorderApp`'s call site to `MeatRecHome` is unchanged.

## Design

### Component structure

```
MeatRecHome (scaffold — owns layout + pager state)
├── Orange top bar              shared, unchanged
├── OrangeUnderglow             unchanged
├── HomeSegmentedControl        NEW — "Record | Library" pill
└── HorizontalPager(pageCount = 2)
     ├── page 0 → RecordPage(...)    NEW — record-half of today's body
     └── page 1 → LibraryPage(...)   NEW — library-half of today's body
```

Mini-player and dialogs (quick settings, source picker) remain in `RecorderApp`, overlaying the scaffold.

### New units

- **`ui/components/HomeSegmentedControl.kt`** — stateless. Params: `selectedIndex: Int`, `onSelect: (Int) -> Unit`, plus the two labels (`"Record"`, `"Library"`). Renders a rounded pill with two segments; the active segment is filled with the yellow accent. Tapping a segment calls `onSelect(index)`. Carries `semantics`/`contentDescription` so TalkBack offers a tap path equivalent to swiping. Reusable for any 2-segment toggle.
- **`ui/RecordPage.kt`** — `@Composable RecordPage(...)`. The record-half of the current `MeatRecHome` body (current lines ~329–520: hero record button + `LiquidBlob`/aura, monitor/level meters, `RecordingActiveSection`, input-gain card, live-EQ band sliders, live toggles, mode/loudness controls). Owns its own `verticalScroll`.
- **`ui/LibraryPage.kt`** — `@Composable LibraryPage(...)`. The library-half (current lines ~521+: `RecordingsToolbar`, `RecordingsSearchAndFilter`, `RecordingFileList`, bulk-select bar). Owns its own scroll.

The split point is the existing `RecordingsToolbar` boundary — a clean cut, not a re-layout.

### `MeatRecHome` after

`MeatRecHome` keeps its full parameter list (so `RecorderApp` is untouched) and becomes a scaffold:

```kotlin
val pagerState = rememberPagerState(initialPage = 0) { 2 }
val scope = rememberCoroutineScope()

Column {
    OrangeTopBar(...)               // existing inline content
    OrangeUnderglow(...)
    HomeSegmentedControl(
        selectedIndex = pagerState.currentPage,
        onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
    )
    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
        when (page) {
            0 -> RecordPage(/* record-related params */)
            else -> LibraryPage(/* library-related params */)
        }
    }
}
```

It forwards each existing param to whichever page needs it.

### Data flow

No new ViewModel state. State still flows VM → `RecorderApp` → `MeatRecHome` → page. Page selection is pure UI state (`pagerState`):

- **Tap** a segment → `animateScrollToPage(i)`.
- **Swipe** → `pagerState.currentPage` changes → `HomeSegmentedControl` recomposes and re-highlights.

`rememberPagerState` persists via `rememberSaveable`, so **rotation/config-change keeps the current page**, while a fresh process launch resets to page 0 — matching "always open on Record."

## Edge cases

- **Recording while on Library:** allowed. Recording runs in the foreground service / ViewModel, independent of the visible page; the record button and timers reflect VM state when Record re-composes.
- **Off-screen composition:** `HorizontalPager` composes only the current page by default (`beyondViewportPageCount = 0`), so Record's heavy canvases (`LiquidBlobCanvas`, live spectrum) stop drawing while Library is shown — a small performance win. Live audio capture is unaffected (it's in the VM/service, not the composable).
- **Gesture conflicts:** none expected. `GainSlider`/`Slider` consume their own horizontal drag within their bounds, so the input-gain and live-EQ sliders do not trigger a page swipe; the spectrum/waveform views are display-only. Swipe is also secondary to the tappable pill.
- **Accessibility:** segmented control is tappable with semantics labels, giving a non-gesture path; required because gesture-only navigation fails TalkBack.

## Testing

The change is pure UI; the existing suite is JVM unit tests (e.g. `EqHistoryTest`), with no Compose UI tests today.

- `@Preview` composables for `RecordPage`, `LibraryPage`, and `HomeSegmentedControl` (both selected states) for visual review.
- Manual verification on a device via the run flow: launch lands on Record; tap "Library" → file list; tap "Record" → record button; swipe both directions; rotate on Library and confirm the page is retained; start a recording, swipe to Library, swipe back, confirm the timer/state are intact; play a file and confirm the mini-player stays visible on both pages.
- **Out of scope for v1:** an instrumented `androidx.compose.ui.test` (would require adding that test dependency). Can be added later if desired.

## Files

New:
- `app/src/main/java/com/example/recorderproject/ui/components/HomeSegmentedControl.kt`
- `app/src/main/java/com/example/recorderproject/ui/RecordPage.kt`
- `app/src/main/java/com/example/recorderproject/ui/LibraryPage.kt`

Modified:
- `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt` — reduced to a scaffold; body content moves into the two new page composables.

Unchanged:
- `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt` — call site and mini-player overlay untouched.
- `app/src/main/java/com/example/recorderproject/MainActivity.kt` — pushed-screen `when {}` untouched.
