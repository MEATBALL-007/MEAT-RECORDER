# Code Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all 10 findings from the code review: recording-limit bypass, incomplete loudness default fix, failing test, wrong paywall slide, stale context, duplicate parameter, double toast, stale comment, and hardcoded warning string.

**Architecture:** Six independent fixes applied across ViewModel, data layer, UI, tests, and components. The recording-limit timer moves from a Compose `LaunchedEffect` (resets on Activity recreation) into a `viewModelScope` Job (survives configuration changes). All other fixes are surgical edits with no structural changes.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel / viewModelScope, Coroutines, JUnit 4

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/java/com/example/recorderproject/data/Defaults.kt:58` | `"PODCAST"` → `"OFF"` |
| `app/src/test/java/com/example/recorderproject/model/LoudnessTargetTest.kt:10-15` | Rename test, assert DEFAULT is Off |
| `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` | Add `recordingLimitJob`, `startFreeTierLimitTimer()`, `cancelFreeTierLimitTimer()`; fix double toast |
| `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt:124-145` | Remove timer enforcement from LaunchedEffect; fix stale comment |
| `app/src/main/java/com/example/recorderproject/ui/components/ProUpgradeSheet.kt` | Add "Unlimited Recording" slide; map `RECORDING_LIMIT` → new index |
| `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt:169` | Remove `onPickSaveLocation2` param; banner uses `onPickSaveLocation` |
| `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt:205` | Remove `onPickSaveLocation2` argument |

---

## Task 1: Fix incomplete loudness default + failing test

**Findings fixed:** #2 (Defaults.kt still says PODCAST), #3 (LoudnessTargetTest fails)

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/data/Defaults.kt:58`
- Modify: `app/src/test/java/com/example/recorderproject/model/LoudnessTargetTest.kt:10-15`

- [ ] **Step 1: Change `defaultLoudnessTarget` in Defaults.kt**

  In `Defaults.kt` line 58, change:
  ```kotlin
  const val defaultLoudnessTarget: String = "PODCAST"
  ```
  to:
  ```kotlin
  const val defaultLoudnessTarget: String = "OFF"
  ```

- [ ] **Step 2: Update the failing test in LoudnessTargetTest.kt**

  Replace lines 10–15 (the `default_is_podcast_at_minus_16` test) with:
  ```kotlin
  @Test fun default_is_off_with_null_targets() {
      val t = LoudnessTarget.DEFAULT
      assertTrue(t is LoudnessTarget.Off)
      assertNull(t.targetLufs)
      assertNull(t.tpCeilingDbtp)
  }
  ```
  The `assertNull` import is already present at line 4.

- [ ] **Step 3: Run tests to confirm they pass**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.model.LoudnessTargetTest" 2>&1 | tail -20
  ```
  Expected: `BUILD SUCCESSFUL`, all 5 tests in `LoudnessTargetTest` pass.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/example/recorderproject/data/Defaults.kt \
          app/src/test/java/com/example/recorderproject/model/LoudnessTargetTest.kt
  git commit -m "fix: complete loudness default → Off; update test to match"
  ```

---

## Task 2: Move recording limit timer to ViewModel

**Findings fixed:** #1 (bypass via Activity recreation), #5 (stale Activity ctx), #8 (hardcoded "1 minute left")

The timer lives in `viewModelScope` so it survives screen rotation. The warn message is derived from `FREE_RECORDING_WARN_SECONDS` so it stays accurate if the constant changes.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Add `recordingLimitJob` field near the other Job fields (~line 1183)**

  After `private var autoStopJob: kotlinx.coroutines.Job? = null` add:
  ```kotlin
  private var recordingLimitJob: Job? = null
  ```

- [ ] **Step 2: Add `startFreeTierLimitTimer()` and `cancelFreeTierLimitTimer()` after `cancelAutoStop()`**

  Insert after `fun cancelAutoStop() { autoStopJob?.cancel() }` (~line 1198):
  ```kotlin
  private fun startFreeTierLimitTimer() {
      recordingLimitJob?.cancel()
      if (isPro.value) return
      val limitMs = com.example.recorderproject.billing.ProFeature.FREE_RECORDING_LIMIT_SECONDS * 1000L
      val warnMs  = (com.example.recorderproject.billing.ProFeature.FREE_RECORDING_LIMIT_SECONDS -
                     com.example.recorderproject.billing.ProFeature.FREE_RECORDING_WARN_SECONDS) * 1000L
      val warnMinutes = com.example.recorderproject.billing.ProFeature.FREE_RECORDING_WARN_SECONDS / 60
      recordingLimitJob = viewModelScope.launch {
          delay(warnMs)
          if (_isRecording.value && !isPro.value) {
              withContext(Dispatchers.Main) {
                  Toast.makeText(
                      app,
                      "$warnMinutes minute left — upgrade to Pro for unlimited recording",
                      Toast.LENGTH_LONG,
                  ).show()
              }
          }
          delay(limitMs - warnMs)
          if (_isRecording.value && !isPro.value) {
              stopRecording()
              openPaywall(com.example.recorderproject.billing.ProFeature.RECORDING_LIMIT)
          }
      }
  }

  private fun cancelFreeTierLimitTimer() {
      recordingLimitJob?.cancel()
      recordingLimitJob = null
  }
  ```

- [ ] **Step 3: Call `startFreeTierLimitTimer()` at the end of the `try` block in `startRecordingNow()`**

  In `startRecordingNow()`, after `Toast.makeText(app, "Recording started", Toast.LENGTH_SHORT).show()` (~line 1733), add:
  ```kotlin
  startFreeTierLimitTimer()
  ```

- [ ] **Step 4: Call `cancelFreeTierLimitTimer()` at the top of `stopRecording()`**

  In `stopRecording()`, right after the early-return guard:
  ```kotlin
  fun stopRecording() {
      Log.d(TAG, "stopRecording() called")
      if (!_isRecording.value) {
          Log.d(TAG, "Not recording, ignoring")
          return
      }
      cancelFreeTierLimitTimer()   // ← add this line
      abandonAudioFocus()
      ...
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
  git commit -m "fix: move recording limit timer to ViewModel — survives Activity recreation"
  ```

---

## Task 3: Clean up RecorderApp.kt

**Findings fixed:** #6 (stale "15-min" comment), removes UI-layer enforcement now in ViewModel

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt`

- [ ] **Step 1: Strip limit enforcement from the elapsed LaunchedEffect**

  Find the block starting with:
  ```kotlin
  // Elapsed seconds tracker — increments while recording; enforces 15-min free limit
  var elapsed by remember { mutableStateOf(0) }
  val limitSecs = com.example.recorderproject.billing.ProFeature.FREE_RECORDING_LIMIT_SECONDS
  val warnSecs  = limitSecs - com.example.recorderproject.billing.ProFeature.FREE_RECORDING_WARN_SECONDS
  LaunchedEffect(isRecording) {
      elapsed = 0
      while (isRecording) {
          kotlinx.coroutines.delay(1000)
          elapsed++
          if (!isPro) {
              when (elapsed) {
                  warnSecs  -> android.widget.Toast.makeText(
                      ctx,
                      "1 minute left — upgrade to Pro for unlimited recording",
                      android.widget.Toast.LENGTH_LONG,
                  ).show()
                  limitSecs -> {
                      viewModel.stopRecording()
                      viewModel.openPaywall(com.example.recorderproject.billing.ProFeature.RECORDING_LIMIT)
                  }
              }
          }
      }
  }
  ```

  Replace the entire block with:
  ```kotlin
  // Elapsed seconds — UI display only; limit enforcement is in RecorderViewModel
  var elapsed by remember { mutableStateOf(0) }
  LaunchedEffect(isRecording) {
      elapsed = 0
      while (isRecording) {
          kotlinx.coroutines.delay(1000)
          elapsed++
      }
  }
  ```

  Also remove the now-unused `limitSecs` and `warnSecs` local vals (they are deleted above).

- [ ] **Step 2: Remove the unused `isPro` usage from this area**

  The `isPro` state is still needed for the `MeatRecHome(isPro = isPro, ...)` call lower down — do NOT remove the `val isPro by ...` declaration. Just verify the `limitSecs`/`warnSecs` vals are gone.

- [ ] **Step 3: Build to confirm no compile errors**

  ```bash
  ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -20
  ```
  Expected: no errors.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt
  git commit -m "fix: remove UI-layer recording limit enforcement (now in ViewModel)"
  ```

---

## Task 4: Add Unlimited Recording slide to paywall

**Finding fixed:** #4 (RECORDING_LIMIT paywall shows wrong slide)

`proSlides` is a zero-indexed list; the new slide is appended at index 6. Only the `RECORDING_LIMIT` mapping changes — all other mappings stay at their current indices.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/components/ProUpgradeSheet.kt`

- [ ] **Step 1: Add color constant for the new slide (after existing slide color constants)**

  Near the top of the file where `SlideCyan`, `SlideGreen`, etc. are defined, add:
  ```kotlin
  private val SlideAmber = Color(0xFFFF9800)
  ```

- [ ] **Step 2: Append the new slide to `proSlides`**

  In the `proSlides` list, after the last entry (`All Themes + Analysis Suite` slide), add:
  ```kotlin
  ProSlide(
      title = "Unlimited Recording",
      blurb = "Remove the 5-min cap — record as long as you need",
      accentColor = SlideAmber,
      draw = { drawUnlimitedRecordingVisual() },
  ),
  ```

- [ ] **Step 3: Add `drawUnlimitedRecordingVisual()` at the bottom of the file (after `drawThemesVisual()`)**

  ```kotlin
  private fun DrawScope.drawUnlimitedRecordingVisual() {
      val midY = size.height * 0.55f
      val path = Path()
      val pts  = 100
      for (i in 0..pts) {
          val fx       = i / pts.toFloat()
          val envelope = sin(fx * PI.toFloat()) * size.height * 0.20f
          val y        = midY + sin(fx * 14f * PI.toFloat()).toFloat() * envelope
          val px       = fx * size.width * 0.75f
          if (i == 0) path.moveTo(px, y) else path.lineTo(px, y)
      }
      drawPath(path, color = SlideAmber.copy(alpha = 0.85f), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
      // Dashed continuation — signals "keeps going"
      drawLine(
          color = SlideAmber.copy(alpha = 0.40f),
          start = Offset(size.width * 0.75f, midY),
          end   = Offset(size.width * 0.95f, midY),
          strokeWidth = 2.5.dp.toPx(),
          pathEffect  = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
      )
      // Record indicator dot
      drawCircle(
          color = SlideAmber,
          radius = size.width * 0.04f,
          center = Offset(size.width * 0.08f, midY - size.height * 0.18f),
      )
  }
  ```
  
  `PI`, `sin`, `Path`, `Offset`, `Size`, `Stroke`, `StrokeCap`, `PathEffect` are already imported — confirm by checking imports at the top of the file.

- [ ] **Step 4: Update `initialSlide()` mapping**

  Change:
  ```kotlin
  ProFeature.RECORDING_LIMIT               -> 0
  ```
  to:
  ```kotlin
  ProFeature.RECORDING_LIMIT               -> 6
  ```

- [ ] **Step 5: Build to confirm**

  ```bash
  ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
  ```
  Expected: no errors.

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/example/recorderproject/ui/components/ProUpgradeSheet.kt
  git commit -m "fix: add Unlimited Recording paywall slide; RECORDING_LIMIT routes to correct slide"
  ```

---

## Task 5: Remove duplicate `onPickSaveLocation2` parameter

**Finding fixed:** #7 (onPickSaveLocation2 duplicates onPickSaveLocation)

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt`

- [ ] **Step 1: In `MeatRecHome.kt`, remove the `onPickSaveLocation2` parameter and wire the banner to `onPickSaveLocation`**

  Remove the parameter:
  ```kotlin
  onPickSaveLocation2: () -> Unit = {},
  ```

  In the banner's `clickable` modifier (a few lines below), change:
  ```kotlin
  .clickable(onClick = onPickSaveLocation2)
  ```
  to:
  ```kotlin
  .clickable(onClick = onPickSaveLocation)
  ```

- [ ] **Step 2: In `RecorderApp.kt`, remove the `onPickSaveLocation2` argument**

  Find:
  ```kotlin
  onPickSaveLocation = onSelectSaveLocation,
  hasSaveLocation = saveDirectoryUri != null,
  onPickSaveLocation2 = onSelectSaveLocation,
  ```
  Replace with:
  ```kotlin
  onPickSaveLocation = onSelectSaveLocation,
  hasSaveLocation = saveDirectoryUri != null,
  ```

- [ ] **Step 3: Build**

  ```bash
  ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
  ```
  Expected: no errors.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt \
          app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt
  git commit -m "fix: remove duplicate onPickSaveLocation2 — banner uses onPickSaveLocation"
  ```

---

## Task 6: Fix double toast on recording stop

**Finding fixed:** #9 (auto-rename toast + App Storage toast overlap)

When auto-rename fires AND there's no save location, two toasts fire. Merge them into one.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` (~line 1794)

- [ ] **Step 1: Refactor the rename + toast block in `stopRecording()`**

  Find the block:
  ```kotlin
  val renamedFile = if (renamedPath != recordedFile.path) {
      val newFile = File(renamedPath)
      Toast.makeText(app, "Auto-named file: ${newFile.name}", Toast.LENGTH_SHORT).show()
      recordedFile.copy(name = newFile.name, path = newFile.absolutePath)
  } else {
      recordedFile
  }.copy(locationTag = pendingLocationTag ?: recordedFile.locationTag)

  // Tell the user exactly where the file landed, especially important when
  // no save folder is set and the file went to internal app storage.
  if (_saveDirectoryUri.value == null) {
      Toast.makeText(
          app,
          "✅ Saved to App Storage: ${renamedFile.name}\nTip: Set a Save Location in Settings to choose your folder.",
          Toast.LENGTH_LONG,
      ).show()
  }
  ```

  Replace with:
  ```kotlin
  val wasRenamed = renamedPath != recordedFile.path
  val renamedFile = if (wasRenamed) {
      val newFile = File(renamedPath)
      recordedFile.copy(name = newFile.name, path = newFile.absolutePath)
  } else {
      recordedFile
  }.copy(locationTag = pendingLocationTag ?: recordedFile.locationTag)

  if (_saveDirectoryUri.value == null) {
      val msg = if (wasRenamed)
          "✅ Auto-named & saved to App Storage: ${renamedFile.name}\nTip: Set a Save Location in Settings to choose your folder."
      else
          "✅ Saved to App Storage: ${renamedFile.name}\nTip: Set a Save Location in Settings to choose your folder."
      Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
  } else if (wasRenamed) {
      Toast.makeText(app, "Auto-named file: ${renamedFile.name}", Toast.LENGTH_SHORT).show()
  }
  ```

- [ ] **Step 2: Build + run unit tests**

  ```bash
  ./gradlew :app:testDebugUnitTest 2>&1 | tail -15
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
  git commit -m "fix: merge auto-rename and app-storage toasts to avoid double notification"
  ```

---

## Self-Review

**Spec coverage:**
- #1 Recording limit bypass → Task 2 (moves timer to ViewModel) + Task 3 (removes UI timer) ✓
- #2 Defaults.kt PODCAST → Task 1 ✓
- #3 LoudnessTargetTest failure → Task 1 ✓
- #4 Wrong paywall slide → Task 4 ✓
- #5 Stale ctx in LaunchedEffect → Resolved by Task 2+3 (timer moved out of LaunchedEffect) ✓
- #6 Stale "15-min" comment → Task 3 (comment updated when block is replaced) ✓
- #7 Duplicate onPickSaveLocation2 → Task 5 ✓
- #8 Hardcoded "1 minute left" → Task 2 (derived from `FREE_RECORDING_WARN_SECONDS / 60`) ✓
- #9 Double toast → Task 6 ✓
- #10 decode() fallback is Off → No code change needed; resolved by Task 1 making Defaults consistent with DEFAULT=Off ✓

**Placeholder scan:** All steps contain exact code. No TBDs.

**Type consistency:** `recordingLimitJob: Job?` uses the same `Job` import already at line 37. `startFreeTierLimitTimer()` / `cancelFreeTierLimitTimer()` are private fun — consistent naming with `armAutoStop`/`cancelAutoStop` pattern.
