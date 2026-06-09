# Record / Library Section Pager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the home screen into two swipeable pages — Record and Library — selectable by a tappable segmented pill or by swiping.

**Architecture:** `MeatRecHome` becomes a thin scaffold: shared orange top bar → `HomeSegmentedControl` → `HorizontalPager(2)` hosting two extracted composables, `RecordPage` and `LibraryPage`. Page selection is local UI state (`rememberPagerState`, `initialPage = 0`); no ViewModel/DataStore changes. The bottom mini-player stays an overlay in `RecorderApp` (untouched), so it remains pinned across both pages. `RecorderApp`'s call site to `MeatRecHome` is unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.foundation.pager.HorizontalPager` (already used in `OnboardingOverlay`/`DesignPicker`/`ProUpgradeSheet`).

**Spec:** `docs/superpowers/specs/2026-06-09-section-pager-design.md`

---

## Conventions used by every task

- **Build/compile check:** `./gradlew :app:compileDebugKotlin` (fast Kotlin compile). Full APK at the end: `./gradlew :app:assembleDebug`.
- **Why no unit tests:** this is pure Compose UI; the project's test suite is JVM-only (e.g. `EqHistoryTest`) with no Compose UI test harness. Per the spec, the per-task "test" is **the Kotlin compiler plus a `@Preview`**, and final acceptance is **manual device verification** (Task 5). Adding `androidx.compose.ui.test` is explicitly out of v1 scope.
- **Accent color:** the codebase uses `MeatYellow = Color(0xFFFFC72C)` (see `MeatRecHome.kt`). Reuse that exact value.

---

## Task 1: `HomeSegmentedControl` component

A stateless two-segment pill. Tapping a segment calls `onSelect(index)`; the active segment is filled with the yellow accent.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/HomeSegmentedControl.kt`

- [ ] **Step 1: Create the component**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MeatYellow = Color(0xFFFFC72C)

/**
 * Two-segment pill toggle for the home section pager (Record | Library).
 * Stateless: [selectedIndex] is the highlighted segment; tapping segment i calls [onSelect](i).
 * Tappable companion to the swipe gesture — keeps the pager reachable without a swipe (a11y).
 */
@Composable
fun HomeSegmentedControl(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labels: List<String> = listOf("Record", "Library"),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isSel = i == selectedIndex
            Text(
                text = label,
                color = if (isSel) Color(0xFF0C0C10) else Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSel) MeatYellow else Color.Transparent)
                    .clickable { onSelect(i) }
                    .semantics { role = Role.Tab; selected = isSel }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF101014)
@Composable
private fun HomeSegmentedControlRecordPreview() {
    HomeSegmentedControl(selectedIndex = 0, onSelect = {})
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF101014)
@Composable
private fun HomeSegmentedControlLibraryPreview() {
    HomeSegmentedControl(selectedIndex = 1, onSelect = {})
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. (`Row` has a `weight` modifier in scope; `selected`/`role` resolve from the semantics imports.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/HomeSegmentedControl.kt
git commit -m "feat: add HomeSegmentedControl pill for section pager"
```

---

## Task 2: Extract `RecordPage`

Move the **record half** of `MeatRecHome`'s scrollable body into a new composable. The record half is everything from the hero record button through the `RecordingSettingsCard` — i.e. the body content that today sits **above** the `// Q6: bulk-select action bar` comment (currently around line 457).

In this task the page is a **plain `Column` with no `verticalScroll` of its own** — it is still rendered inside `MeatRecHome`'s existing outer scroll, so behavior is identical. The independent per-page scroll is added in Task 4.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/RecordPage.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt`

- [ ] **Step 1: Create `RecordPage.kt` with the moved content**

Create the file with this skeleton, then **cut** the record-section composables out of `MeatRecHome`'s body (hero `BoxWithConstraints` block, the `AnimatedVisibility { RecordingActiveSection(...) }`, the `Box(Modifier.height(8.dp))` spacer, and the `RecordingSettingsCard(...)` call) and paste them inside the `Column` below:

```kotlin
package com.example.recorderproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.RecordFile

/**
 * The "Record" page of the home pager: hero record button, live recording-active
 * section, and the recording-settings card. Pure presentation — all state is hoisted.
 */
@Composable
fun RecordPage(
    // PARAMS: see Step 2 — add exactly the parameters the moved composables reference.
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // <-- paste the cut record-section composables here, verbatim -->
    }
}
```

- [ ] **Step 2: Add `RecordPage`'s parameters and forward them from `MeatRecHome`**

Add these parameters to `RecordPage`'s signature (these are exactly the `MeatRecHome` parameters that the moved composables reference). Then in `MeatRecHome`, replace the cut block with a single `RecordPage(...)` call that forwards each one by name:

```kotlin
    isRecording: Boolean,
    fileName: String,
    sceneName: String,
    notes: String,
    noiseReductionEnabled: Boolean,
    sampleRate: Int,
    bitDepth: Int,
    channelCount: Int,
    onFileNameChange: (String) -> Unit,
    onSceneNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onToggleNR: (Boolean) -> Unit,
    onChangeSampleRate: (Int) -> Unit,
    onChangeBitDepth: (Int) -> Unit,
    onChangeChannelCount: (Int) -> Unit,
    onTapRecord: () -> Unit,
    onOpenSourcePicker: () -> Unit,
    onPickSaveLocation: () -> Unit,
    onAnalyzeRoom: () -> Unit,
    elapsedSeconds: Int,
    waveform: List<Float>,
    inputLevelPercent: Int,
    spectrumHistory: List<FloatArray>,
    pitchHz: Float,
    cueCount: Int,
    isPaused: Boolean,
    onDropCue: () -> Unit,
    onTogglePause: () -> Unit,
    liveEqOn: Boolean,
    onToggleLiveEq: () -> Unit,
    onOpenEqEditor: () -> Unit,
    liveNoiseGateOn: Boolean,
    onToggleLiveNoiseGate: () -> Unit,
    liveEqBandGains: FloatArray,
    onChangeLiveEqBand: (Int, Float) -> Unit,
    preRollOn: Boolean,
    onTogglePreRoll: () -> Unit,
    vadOn: Boolean,
    onToggleVad: () -> Unit,
    lufsDb: Float,
    liveTpDbtp: Float,
    loudnessTarget: com.example.recorderproject.model.LoudnessTarget,
    onSlateTone: () -> Unit,
    micSource: String,
    phaseCorrelation: Float,
    monitorOn: Boolean,
    onToggleMonitor: () -> Unit,
    monitorRmsDb: Float,
    maxDurationMinutes: Int,
    onChangeMaxDuration: (Int) -> Unit,
    liveRawPeakDbfs: Float,
    inputGainDb: Float,
    onChangeInputGain: (Float) -> Unit,
    onBumpTake: () -> Unit,
    onBumpSubscene: () -> Unit,
    onSubsceneMinus: () -> Unit,
    onTakeMinus1: () -> Unit,
    onSceneMinus1: () -> Unit,
    onScenePlus1: () -> Unit,
    onSelectLoudnessTarget: (com.example.recorderproject.model.LoudnessTarget) -> Unit,
    onSaveLoudnessAsDefault: (com.example.recorderproject.model.LoudnessTarget) -> Unit,
```

These cover the `LoudnessTargetChip` (`onSelectLoudnessTarget`, `onSaveLoudnessAsDefault`), `InputGainCard` (`liveRawPeakDbfs`, `inputGainDb`, `onChangeInputGain`), and `TakeSceneBumpRow` (the six bump handlers) calls inside the moved record region.

The corresponding call in `MeatRecHome` (placed where the cut block was):

```kotlin
            RecordPage(
                isRecording = isRecording,
                fileName = fileName,
                sceneName = sceneName,
                notes = notes,
                noiseReductionEnabled = noiseReductionEnabled,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                channelCount = channelCount,
                onFileNameChange = onFileNameChange,
                onSceneNameChange = onSceneNameChange,
                onNotesChange = onNotesChange,
                onToggleNR = onToggleNR,
                onChangeSampleRate = onChangeSampleRate,
                onChangeBitDepth = onChangeBitDepth,
                onChangeChannelCount = onChangeChannelCount,
                onTapRecord = onTapRecord,
                onOpenSourcePicker = onOpenSourcePicker,
                onPickSaveLocation = onPickSaveLocation,
                onAnalyzeRoom = onAnalyzeRoom,
                elapsedSeconds = elapsedSeconds,
                waveform = waveform,
                inputLevelPercent = inputLevelPercent,
                spectrumHistory = spectrumHistory,
                pitchHz = pitchHz,
                cueCount = cueCount,
                isPaused = isPaused,
                onDropCue = onDropCue,
                onTogglePause = onTogglePause,
                liveEqOn = liveEqOn,
                onToggleLiveEq = onToggleLiveEq,
                onOpenEqEditor = onOpenEqEditor,
                liveNoiseGateOn = liveNoiseGateOn,
                onToggleLiveNoiseGate = onToggleLiveNoiseGate,
                liveEqBandGains = liveEqBandGains,
                onChangeLiveEqBand = onChangeLiveEqBand,
                preRollOn = preRollOn,
                onTogglePreRoll = onTogglePreRoll,
                vadOn = vadOn,
                onToggleVad = onToggleVad,
                lufsDb = lufsDb,
                liveTpDbtp = liveTpDbtp,
                loudnessTarget = loudnessTarget,
                onSlateTone = onSlateTone,
                micSource = micSource,
                phaseCorrelation = phaseCorrelation,
                monitorOn = monitorOn,
                onToggleMonitor = onToggleMonitor,
                monitorRmsDb = monitorRmsDb,
                maxDurationMinutes = maxDurationMinutes,
                onChangeMaxDuration = onChangeMaxDuration,
                liveRawPeakDbfs = liveRawPeakDbfs,
                inputGainDb = inputGainDb,
                onChangeInputGain = onChangeInputGain,
                onBumpTake = onBumpTake,
                onBumpSubscene = onBumpSubscene,
                onSubsceneMinus = onSubsceneMinus,
                onTakeMinus1 = onTakeMinus1,
                onSceneMinus1 = onSceneMinus1,
                onScenePlus1 = onScenePlus1,
                onSelectLoudnessTarget = onSelectLoudnessTarget,
                onSaveLoudnessAsDefault = onSaveLoudnessAsDefault,
            )
```

> If the compiler reports an **unresolved reference** inside `RecordPage`, that symbol is a `MeatRecHome` parameter the moved code uses that isn't in the list above — add it to `RecordPage`'s signature and forward it. If it reports an **unused parameter** warning on `MeatRecHome`, leave it; a param not referenced by either page (e.g. `onToggleSelect`, if selection isn't wired in the moved blocks) stays in the signature as today. Also copy any non-Compose imports the moved code needs (e.g. `RecordingSettingsCard` is in the same `ui` package — no import needed; component calls under `ui.components.*` are already fully-qualified).

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Resolve any unresolved-reference errors per the note above until it passes.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/RecordPage.kt app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt
git commit -m "refactor: extract RecordPage from MeatRecHome body"
```

---

## Task 3: Extract `LibraryPage`

Move the **library half** of `MeatRecHome`'s body — the `// Q6: bulk-select action bar` block, the `// Batch 4` `RecordingsToolbar(...)` block, and the `RecordingsListCard(...)` call — into a new composable. Same approach as Task 2: plain `Column`, no own scroll yet.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/LibraryPage.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt`

- [ ] **Step 1: Create `LibraryPage.kt` and move the content**

```kotlin
package com.example.recorderproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.model.SortOrder

/**
 * The "Library" page of the home pager: bulk-select action bar, recordings
 * search/filter/sort toolbar, and the recordings list. Pure presentation.
 */
@Composable
fun LibraryPage(
    files: List<RecordFile>,
    selectedFileId: String?,
    isPlaying: Boolean,
    onTapFile: (RecordFile) -> Unit,
    onShareFile: (RecordFile) -> Unit,
    onRenameFile: (RecordFile, String) -> Unit,
    onToggleStarFile: (RecordFile) -> Unit,
    onToggleLockFile: (RecordFile) -> Unit,
    onDeleteFile: (RecordFile) -> Unit,
    onTrimFile: (RecordFile) -> Unit,
    onEQFile: (RecordFile) -> Unit,
    selectedIds: Set<String>,
    onClearSelection: () -> Unit,
    onBulkDelete: () -> Unit,
    onBulkCompareAb: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    fileFilter: com.example.recorderproject.RecorderViewModel.FileFilter,
    onFilterChange: (com.example.recorderproject.RecorderViewModel.FileFilter) -> Unit,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // <-- paste the cut library-section composables here, verbatim -->
    }
}
```

> The moved block references `MeatOrange` and the Material/Compose symbols already imported in `MeatRecHome`. `MeatOrange` is defined in the `ui` package — confirm with `grep -rn "val MeatOrange" app/src/main/java/com/example/recorderproject/ui`; if it lives in a different file in the same package no import is needed, otherwise add the import the compiler asks for. `RecordingsListCard`/`RecordingsToolbar` calls are already fully-qualified (`com.example.recorderproject.ui.components.*`).

- [ ] **Step 2: Replace the cut block in `MeatRecHome` with the call**

```kotlin
            LibraryPage(
                files = files,
                selectedFileId = selectedFileId,
                isPlaying = isPlaying,
                onTapFile = onTapFile,
                onShareFile = onShareFile,
                onRenameFile = onRenameFile,
                onToggleStarFile = onToggleStarFile,
                onToggleLockFile = onToggleLockFile,
                onDeleteFile = onDeleteFile,
                onTrimFile = onTrimFile,
                onEQFile = onEQFile,
                selectedIds = selectedIds,
                onClearSelection = onClearSelection,
                onBulkDelete = onBulkDelete,
                onBulkCompareAb = onBulkCompareAb,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                fileFilter = fileFilter,
                onFilterChange = onFilterChange,
                sortOrder = sortOrder,
                onSortChange = onSortChange,
            )
```

> `onToggleSelect` is passed to `MeatRecHome` but the bulk bar block does not call it (selection is toggled from inside `RecordingsListCard`, which already receives its own handlers). If the compiler flags `onToggleSelect` as referenced by the moved code, add it to `LibraryPage`; otherwise leave it as an (existing) `MeatRecHome` parameter.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. At this point `MeatRecHome`'s body is `RecordPage(...)` followed by `LibraryPage(...)` inside the original outer scroll — visually identical to before.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/LibraryPage.kt app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt
git commit -m "refactor: extract LibraryPage from MeatRecHome body"
```

---

## Task 4: Wire the pager into `MeatRecHome`

Replace the single scrolling body with the segmented control + a 2-page `HorizontalPager`. Move the scroll + tablet-width-cap + horizontal padding **into each page** so they scroll independently.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecordPage.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/LibraryPage.kt`

- [ ] **Step 1: Give each page its own scroll + width cap**

In **both** `RecordPage.kt` and `LibraryPage.kt`, change the page's root `Column` modifier to own the scroll, the tablet centering, and the padding that previously lived on `MeatRecHome`'s body Column:

```kotlin
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
```

Add the imports to each page file:

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

- [ ] **Step 2: Replace `MeatRecHome`'s body Column with the segmented control + pager**

In `MeatRecHome.kt`, the current body is a `Column(Modifier.weight(1f)...verticalScroll(scroll)...)` (starts ~line 315) that now contains just `RecordPage(...)` and `LibraryPage(...)`. Replace that whole `Column(...) { ... }` with:

```kotlin
        // ============== 2. SECTION PAGER (Record | Library) ==============
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { 2 }
        val pagerScope = androidx.compose.runtime.rememberCoroutineScope()

        com.example.recorderproject.ui.components.HomeSegmentedControl(
            selectedIndex = pagerState.currentPage,
            onSelect = { i -> pagerScope.launch { pagerState.animateScrollToPage(i) } },
        )

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> RecordPage(
                    isRecording = isRecording,
                    fileName = fileName,
                    sceneName = sceneName,
                    notes = notes,
                    noiseReductionEnabled = noiseReductionEnabled,
                    sampleRate = sampleRate,
                    bitDepth = bitDepth,
                    channelCount = channelCount,
                    onFileNameChange = onFileNameChange,
                    onSceneNameChange = onSceneNameChange,
                    onNotesChange = onNotesChange,
                    onToggleNR = onToggleNR,
                    onChangeSampleRate = onChangeSampleRate,
                    onChangeBitDepth = onChangeBitDepth,
                    onChangeChannelCount = onChangeChannelCount,
                    onTapRecord = onTapRecord,
                    onOpenSourcePicker = onOpenSourcePicker,
                    onPickSaveLocation = onPickSaveLocation,
                    onAnalyzeRoom = onAnalyzeRoom,
                    elapsedSeconds = elapsedSeconds,
                    waveform = waveform,
                    inputLevelPercent = inputLevelPercent,
                    spectrumHistory = spectrumHistory,
                    pitchHz = pitchHz,
                    cueCount = cueCount,
                    isPaused = isPaused,
                    onDropCue = onDropCue,
                    onTogglePause = onTogglePause,
                    liveEqOn = liveEqOn,
                    onToggleLiveEq = onToggleLiveEq,
                    onOpenEqEditor = onOpenEqEditor,
                    liveNoiseGateOn = liveNoiseGateOn,
                    onToggleLiveNoiseGate = onToggleLiveNoiseGate,
                    liveEqBandGains = liveEqBandGains,
                    onChangeLiveEqBand = onChangeLiveEqBand,
                    preRollOn = preRollOn,
                    onTogglePreRoll = onTogglePreRoll,
                    vadOn = vadOn,
                    onToggleVad = onToggleVad,
                    lufsDb = lufsDb,
                    liveTpDbtp = liveTpDbtp,
                    loudnessTarget = loudnessTarget,
                    onSlateTone = onSlateTone,
                    micSource = micSource,
                    phaseCorrelation = phaseCorrelation,
                    monitorOn = monitorOn,
                    onToggleMonitor = onToggleMonitor,
                    monitorRmsDb = monitorRmsDb,
                    maxDurationMinutes = maxDurationMinutes,
                    onChangeMaxDuration = onChangeMaxDuration,
                    liveRawPeakDbfs = liveRawPeakDbfs,
                    inputGainDb = inputGainDb,
                    onChangeInputGain = onChangeInputGain,
                    onBumpTake = onBumpTake,
                    onBumpSubscene = onBumpSubscene,
                    onSubsceneMinus = onSubsceneMinus,
                    onTakeMinus1 = onTakeMinus1,
                    onSceneMinus1 = onSceneMinus1,
                    onScenePlus1 = onScenePlus1,
                    onSelectLoudnessTarget = onSelectLoudnessTarget,
                    onSaveLoudnessAsDefault = onSaveLoudnessAsDefault,
                )
                else -> LibraryPage(
                    files = files,
                    selectedFileId = selectedFileId,
                    isPlaying = isPlaying,
                    onTapFile = onTapFile,
                    onShareFile = onShareFile,
                    onRenameFile = onRenameFile,
                    onToggleStarFile = onToggleStarFile,
                    onToggleLockFile = onToggleLockFile,
                    onDeleteFile = onDeleteFile,
                    onTrimFile = onTrimFile,
                    onEQFile = onEQFile,
                    selectedIds = selectedIds,
                    onClearSelection = onClearSelection,
                    onBulkDelete = onBulkDelete,
                    onBulkCompareAb = onBulkCompareAb,
                    searchQuery = searchQuery,
                    onSearchChange = onSearchChange,
                    fileFilter = fileFilter,
                    onFilterChange = onFilterChange,
                    sortOrder = sortOrder,
                    onSortChange = onSortChange,
                )
            }
        }
```

Then delete the now-unused `val scroll = rememberScrollState()` line near the top of `MeatRecHome` (it was the outer body scroll). Ensure `launch` is imported (`import kotlinx.coroutines.launch`) — add it if the compiler flags `launch` as unresolved.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If `scroll` is reported unused or any leftover reference to it remains, remove it.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt app/src/main/java/com/example/recorderproject/ui/RecordPage.kt app/src/main/java/com/example/recorderproject/ui/LibraryPage.kt
git commit -m "feat: two-page Record/Library pager in MeatRecHome"
```

---

## Task 5: Build APK and verify on device

**Files:** none (verification only).

- [ ] **Step 1: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`; APK produced under `app/build/outputs/apk/debug/`.

- [ ] **Step 2: Manual verification (install + drive the app)**

Install and check each item from the spec's test plan:
- App launches on the **Record** page (segmented pill shows "Record" highlighted).
- Tap **Library** → recordings list appears; tap **Record** → hero record button appears.
- **Swipe** left/right moves between the two pages and the pill highlight follows.
- Drag the **input-gain** / **live-EQ** sliders on Record — the value changes and the page does **not** swipe.
- **Rotate** the device while on Library → still on Library after rotation.
- Start a **recording**, swipe to Library, swipe back → the elapsed timer and recording state are intact.
- Play a recording (tap a file) → the bottom **mini-player** stays visible on both pages.

- [ ] **Step 3: Commit (only if Step 2 surfaced fixes)**

If manual verification required code changes, commit them:

```bash
git add -A
git commit -m "fix: address section pager verification findings"
```

If no changes were needed, there is nothing to commit — the feature is complete at Task 4's commit.

---

## Self-review notes

- **Spec coverage:** two pages (Tasks 2–4) ✓; segmented pill, tappable + swipeable (Tasks 1, 4) ✓; always-Record landing via `initialPage = 0` (Task 4) ✓; rotation retains page via `rememberPagerState`'s saver (Task 4) ✓; mini-player pinned — `RecorderApp` untouched, no task needed ✓; `MeatRecHome` → scaffold + `RecordPage`/`LibraryPage`/`HomeSegmentedControl` (Tasks 1–4) ✓; `@Preview`s (Task 1) ✓; manual verification (Task 5) ✓; instrumented test out of scope ✓.
- **Param-name consistency:** the parameter names in `RecordPage`/`LibraryPage` and their call sites match `MeatRecHome`'s existing parameter names verbatim (e.g. `onToggleStarFile`, `onChangeLiveEqBand`, `liveTpDbtp`), so forwarding is name-for-name.
- **Compiler-as-test:** because exact param membership depends on the moved code, each extraction task uses `compileDebugKotlin` as the gate and documents how to resolve unresolved/unused-reference results, rather than guessing a frozen list.
