# Monitor Level Meter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pre-record input level meter to MEAT REC: while Monitor is on (and we are not recording), show a horizontal RMS bar + peak-hold marker + clip dot + numeric dB readout below the feature chips, sourced from the audio AudioMonitor reads anyway.

**Architecture:** AudioMonitor pushes (rmsDb, peakDb) per PCM buffer to a listener. RecorderViewModel feeds those into a pure `applyAudioSample` function and exposes a `monitorLevel: StateFlow<MonitorLevel>`. A parallel 50 ms coroutine in the ViewModel applies `applyDecayTick` (peak hold decays 1 dB/tick toward RMS, clip flag clears after 2 s). MonitorLevelMeter composable renders the bar with the brand `Spectrum*` gradient. RecorderApp shows it inside `AnimatedVisibility(monitorOn && !isRecording)`.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2024.02.00), Material 3, kotlinx.coroutines, JUnit 4. Project root `/Users/meatball_mac/RECORDER_PROJECT/`.

**Spec:** `/Users/meatball_mac/RECORDER_PROJECT/docs/superpowers/specs/2026-05-26-monitor-level-meter-design.md` (commit `9efc743`)

---

## Pre-task setup

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
git status        # Expect: working tree clean on main
git log --oneline -3
# Expect: top commit is b6dd501 "feat(recordings): wire SortOrder..."

./gradlew assembleDebug    # Expect: BUILD SUCCESSFUL
./gradlew test             # Expect: BUILD SUCCESSFUL
```

If anything is dirty or red, bail and fix before starting.

---

## File structure

```
app/src/main/java/com/example/recorderproject/
├── model/
│   └── MonitorLevel.kt          NEW · data class + LevelUpdate + applyAudioSample + applyDecayTick (pure)
├── audio/
│   └── AudioMonitor.kt          MODIFY · add setLevelListener + per-buffer RMS/peak computation
├── ui/components/
│   └── MonitorLevelMeter.kt     NEW · composable (label, bar, peak marker, clip dot, dB readout)
├── ui/
│   └── RecorderApp.kt           MODIFY · observe monitorLevel + render meter conditionally
└── RecorderViewModel.kt         MODIFY · _monitorLevel StateFlow + decay coroutine + AudioMonitor listener wiring

app/src/test/java/com/example/recorderproject/
└── model/
    └── MonitorLevelTest.kt      NEW · unit tests for applyAudioSample + applyDecayTick
```

---

## Task 1: `MonitorLevel` data class + pure helpers (TDD)

**Goal:** Define the state shape and the two pure decision functions, with failing tests first.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/MonitorLevel.kt`
- Create: `app/src/test/java/com/example/recorderproject/model/MonitorLevelTest.kt`

- [ ] **Step 1: Write the failing test file**

Create `app/src/test/java/com/example/recorderproject/model/MonitorLevelTest.kt`:

```kotlin
package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorLevelTest {

    // ----- applyAudioSample -----

    @Test fun `audio sample with new peak above current peak raises peak`() {
        val curr = MonitorLevel(rmsDb = -30f, peakDb = -20f, clipped = false)
        val out = applyAudioSample(curr, newRmsDb = -25f, newPeakDb = -10f, nowMs = 1_000L, clipUntilMs = 0L)
        assertEquals(-25f, out.level.rmsDb, 0.001f)
        assertEquals(-10f, out.level.peakDb, 0.001f)
        assertFalse(out.level.clipped)
        assertEquals(0L, out.clipUntilMs)
    }

    @Test fun `audio sample with new peak below current peak holds the higher peak`() {
        val curr = MonitorLevel(rmsDb = -30f, peakDb = -10f, clipped = false)
        val out = applyAudioSample(curr, newRmsDb = -35f, newPeakDb = -20f, nowMs = 1_000L, clipUntilMs = 0L)
        assertEquals(-35f, out.level.rmsDb, 0.001f)
        assertEquals(-10f, out.level.peakDb, 0.001f) // held
    }

    @Test fun `audio sample above clip threshold sets clipped and schedules clip-clear 2s out`() {
        val curr = MonitorLevel.Silent
        val out = applyAudioSample(curr, newRmsDb = -3f, newPeakDb = 0.5f, nowMs = 10_000L, clipUntilMs = 0L)
        assertTrue(out.level.clipped)
        assertEquals(12_000L, out.clipUntilMs)
    }

    @Test fun `audio sample below clip threshold preserves an unexpired clip window`() {
        val curr = MonitorLevel(rmsDb = -20f, peakDb = -5f, clipped = true)
        val out = applyAudioSample(curr, newRmsDb = -20f, newPeakDb = -5f, nowMs = 10_500L, clipUntilMs = 12_000L)
        assertTrue(out.level.clipped) // 10.5s < 12s, still clipped
        assertEquals(12_000L, out.clipUntilMs)
    }

    @Test fun `audio sample below clip threshold after window expiry clears clipped`() {
        val curr = MonitorLevel(rmsDb = -20f, peakDb = -5f, clipped = true)
        val out = applyAudioSample(curr, newRmsDb = -20f, newPeakDb = -5f, nowMs = 13_000L, clipUntilMs = 12_000L)
        assertFalse(out.level.clipped) // 13s > 12s, expired
    }

    // ----- applyDecayTick -----

    @Test fun `decay tick reduces peak by default 1dB`() {
        val curr = MonitorLevel(rmsDb = -30f, peakDb = -10f, clipped = false)
        val out = applyDecayTick(curr, nowMs = 1_000L, clipUntilMs = 0L)
        assertEquals(-11f, out.level.peakDb, 0.001f)
        assertEquals(-30f, out.level.rmsDb, 0.001f) // RMS unchanged by decay
    }

    @Test fun `decay tick floor at rmsDb`() {
        val curr = MonitorLevel(rmsDb = -30f, peakDb = -29.5f, clipped = false)
        val out = applyDecayTick(curr, nowMs = 1_000L, clipUntilMs = 0L)
        assertEquals(-30f, out.level.peakDb, 0.001f) // floored, not -30.5
    }

    @Test fun `decay tick clears clipped after window expiry`() {
        val curr = MonitorLevel(rmsDb = -20f, peakDb = -5f, clipped = true)
        val out = applyDecayTick(curr, nowMs = 13_000L, clipUntilMs = 12_000L)
        assertFalse(out.level.clipped)
    }

    @Test fun `decay tick preserves clipped within window`() {
        val curr = MonitorLevel(rmsDb = -20f, peakDb = -5f, clipped = true)
        val out = applyDecayTick(curr, nowMs = 10_500L, clipUntilMs = 12_000L)
        assertTrue(out.level.clipped)
    }
}
```

- [ ] **Step 2: Run the test, expect compile failure**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew testDebugUnitTest --tests "com.example.recorderproject.model.MonitorLevelTest"
```

Expected: FAIL — references to `MonitorLevel`, `MonitorLevel.Silent`, `applyAudioSample`, `applyDecayTick` are unresolved.

- [ ] **Step 3: Create `MonitorLevel.kt` to make tests pass**

```kotlin
package com.example.recorderproject.model

/**
 * Pre-record input level snapshot, derived from the AudioMonitor PCM loop.
 *
 * - [rmsDb] is the smoothed input loudness in dBFS, range -60..0 (floor at -60).
 * - [peakDb] is the peak-hold value in dBFS, decays toward [rmsDb] over time.
 * - [clipped] flips true when the peak exceeded -0.1 dBFS in the last ~2 seconds.
 */
data class MonitorLevel(
    val rmsDb: Float,
    val peakDb: Float,
    val clipped: Boolean,
) {
    companion object {
        val Silent = MonitorLevel(rmsDb = -60f, peakDb = -60f, clipped = false)
    }
}

/**
 * Result of applying an audio event or decay tick. Carries the new [level] and the
 * scheduled clip-clear timestamp (epoch ms) so the ViewModel can hold the deadline
 * across calls without keeping state inside the pure functions.
 */
data class LevelUpdate(val level: MonitorLevel, val clipUntilMs: Long)

/** Clip threshold: a peak above this triggers the clip flag. */
const val CLIP_THRESHOLD_DB = -0.1f

/** How long the clip flag stays lit after a clip event, in ms. */
const val CLIP_HOLD_MS = 2_000L

/** Default peak-hold decay per tick, in dB. */
const val PEAK_DECAY_DB_PER_TICK = 1f

/**
 * Apply a fresh audio buffer's RMS/peak readings. Peak is held against the current
 * peak (whichever is louder wins); clip is set if [newPeakDb] crosses the threshold,
 * and the clip window is extended.
 */
fun applyAudioSample(
    curr: MonitorLevel,
    newRmsDb: Float,
    newPeakDb: Float,
    nowMs: Long,
    clipUntilMs: Long,
): LevelUpdate {
    val heldPeak = maxOf(newPeakDb, curr.peakDb)
    val isNewClip = newPeakDb > CLIP_THRESHOLD_DB
    val nextClipUntilMs = if (isNewClip) nowMs + CLIP_HOLD_MS else clipUntilMs
    val isClipped = isNewClip || nowMs < clipUntilMs
    return LevelUpdate(
        level = MonitorLevel(rmsDb = newRmsDb, peakDb = heldPeak, clipped = isClipped),
        clipUntilMs = nextClipUntilMs,
    )
}

/**
 * Apply a periodic decay tick (no new audio). Peak decays by [peakDecayDb] toward
 * [MonitorLevel.rmsDb] but never below it. The clip flag clears once [nowMs] has
 * passed the scheduled [clipUntilMs] deadline.
 */
fun applyDecayTick(
    curr: MonitorLevel,
    nowMs: Long,
    clipUntilMs: Long,
    peakDecayDb: Float = PEAK_DECAY_DB_PER_TICK,
): LevelUpdate {
    val decayed = (curr.peakDb - peakDecayDb).coerceAtLeast(curr.rmsDb)
    val isClipped = nowMs < clipUntilMs
    return LevelUpdate(
        level = MonitorLevel(rmsDb = curr.rmsDb, peakDb = decayed, clipped = isClipped),
        clipUntilMs = clipUntilMs,
    )
}
```

- [ ] **Step 4: Run tests, expect PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.model.MonitorLevelTest"
```

Expected: BUILD SUCCESSFUL, 9 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/MonitorLevel.kt \
        app/src/test/java/com/example/recorderproject/model/MonitorLevelTest.kt
git commit -m "feat(model): MonitorLevel data class + applyAudioSample/applyDecayTick pure helpers"
```

---

## Task 2: AudioMonitor — `setLevelListener` + per-buffer RMS/peak

**Goal:** Add a level callback to AudioMonitor that fires once per PCM buffer with the computed (rmsDb, peakDb).

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/audio/AudioMonitor.kt`

- [ ] **Step 1: Add the listener field + setter + computation**

Open `app/src/main/java/com/example/recorderproject/audio/AudioMonitor.kt`. After `@Volatile private var chain: EQChain = EQChain.empty()` (around line 25), add:

```kotlin
    @Volatile private var levelListener: ((rmsDb: Float, peakDb: Float) -> Unit)? = null

    /** Set or clear the per-buffer (rmsDb, peakDb) callback. Pass null to clear. */
    fun setLevelListener(cb: ((rmsDb: Float, peakDb: Float) -> Unit)?) {
        levelListener = cb
    }
```

Then in `run()`, find the line `track.write(buf, 0, read)`. **Immediately after** that line, insert:

```kotlin
                // Compute level for any registered meter listener (~5-15µs per buffer)
                val cb = levelListener
                if (cb != null && read > 0) {
                    var sumSq = 0.0
                    var peakAbs = 0
                    for (i in 0 until read) {
                        val s = buf[i].toInt()
                        sumSq += s.toDouble() * s.toDouble()
                        val a = if (s < 0) -s else s
                        if (a > peakAbs) peakAbs = a
                    }
                    val rms = kotlin.math.sqrt(sumSq / read) / Short.MAX_VALUE.toDouble()
                    val pk  = peakAbs.toDouble() / Short.MAX_VALUE.toDouble()
                    val rmsDb  = if (rms <= 0.0) -60f else (20.0 * kotlin.math.log10(rms)).toFloat().coerceAtLeast(-60f)
                    val peakDb = if (pk  <= 0.0) -60f else (20.0 * kotlin.math.log10(pk )).toFloat().coerceAtLeast(-60f)
                    cb(rmsDb, peakDb)
                }
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run existing tests (sanity — audio tests should not regress)**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all existing tests still pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/AudioMonitor.kt
git commit -m "feat(audio): AudioMonitor per-buffer RMS/peak callback via setLevelListener"
```

---

## Task 3: ViewModel — `monitorLevel` flow + decay coroutine + listener wiring

**Goal:** Expose `monitorLevel: StateFlow<MonitorLevel>` to the UI. Drive it from AudioMonitor's callback (on the audio thread) and the decay coroutine (on viewModelScope at 50 ms).

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Add imports**

Open `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`. Find the existing imports (around lines 1–30). After `import com.example.recorderproject.model.SortOrder` (added in a recent commit), add:

```kotlin
import com.example.recorderproject.model.MonitorLevel
import com.example.recorderproject.model.applyAudioSample
import com.example.recorderproject.model.applyDecayTick
```

- [ ] **Step 2: Add the StateFlow + clip deadline + decay job fields**

Find the existing `private val _monitorEnabled = MutableStateFlow(false)` line and **immediately after the `val monitorEnabled: StateFlow<Boolean> = _monitorEnabled` line that follows it**, insert this block:

```kotlin
    private val _monitorLevel = MutableStateFlow(MonitorLevel.Silent)
    val monitorLevel: StateFlow<MonitorLevel> = _monitorLevel

    private var monitorClipUntilMs: Long = 0L
    private var monitorDecayJob: Job? = null

    /** Called from AudioMonitor's audio thread once per PCM buffer. */
    private fun onMonitorPcm(rmsDb: Float, peakDb: Float) {
        val now = System.currentTimeMillis()
        val update = applyAudioSample(
            curr = _monitorLevel.value,
            newRmsDb = rmsDb,
            newPeakDb = peakDb,
            nowMs = now,
            clipUntilMs = monitorClipUntilMs,
        )
        monitorClipUntilMs = update.clipUntilMs
        _monitorLevel.value = update.level
    }
```

- [ ] **Step 3: Wire listener + decay coroutine into `toggleMonitor()`**

Find the existing `fun toggleMonitor() { ... }` (around lines 87–115). In the ON-path (`if (on) { ... }`), find the existing line `audioMonitor.setChain(_currentEQChain.value)`. **Immediately before** `audioMonitor.start()`, insert:

```kotlin
            // Wire level callback BEFORE start so the very first buffer is observed
            audioMonitor.setLevelListener(::onMonitorPcm)
```

Then **immediately after** `audioMonitor.start()` (still inside the ON-path), insert:

```kotlin
            // Peak-hold decay tick — every 50 ms, decay peakDb by 1 toward rmsDb
            monitorDecayJob?.cancel()
            monitorDecayJob = viewModelScope.launch {
                while (true) {
                    delay(50)
                    val now = System.currentTimeMillis()
                    val update = applyDecayTick(
                        curr = _monitorLevel.value,
                        nowMs = now,
                        clipUntilMs = monitorClipUntilMs,
                    )
                    monitorClipUntilMs = update.clipUntilMs
                    _monitorLevel.value = update.level
                }
            }
```

In the OFF-path (`} else { ... }`), find the existing line `audioMonitor.stop()`. **Immediately after** `audioMonitor.stop()`, insert:

```kotlin
            // Cancel decay coroutine, clear listener (after stop so the run-loop has exited)
            monitorDecayJob?.cancel()
            monitorDecayJob = null
            audioMonitor.setLevelListener(null)
            monitorClipUntilMs = 0L
            _monitorLevel.value = MonitorLevel.Silent
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, including the 9 MonitorLevelTest cases plus all pre-existing tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(vm): expose monitorLevel StateFlow + decay coroutine + AudioMonitor listener wiring"
```

---

## Task 4: `MonitorLevelMeter` composable

**Goal:** Render the meter — caps label, dB readout, horizontal bar with brand spectrum gradient, peak-hold marker, clip dot.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/MonitorLevelMeter.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.MonitorLevel
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.SemanticError
import com.example.recorderproject.ui.theme.Spacing
import com.example.recorderproject.ui.theme.SpectrumHigh
import com.example.recorderproject.ui.theme.SpectrumLow
import com.example.recorderproject.ui.theme.SpectrumMid
import com.example.recorderproject.ui.theme.SurfaceContainerHigh

/**
 * Pre-record input level meter.
 *
 *   INPUT LEVEL                              -12.3 dB
 *   ▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░│░░  •
 *                                         ↑
 *                                       peak hold
 *
 * Visible only when AudioMonitor is on and we are not recording — caller decides.
 *
 * Layout precedent: old MEATrec RecorderApp's INPUT LEVEL meter used `labelSmall`
 * + percentage. We upgrade to dB readout via `numericMedium` (tabular figures, no
 * digit jitter) and add peak-hold + clip indicator.
 */
@Composable
fun MonitorLevelMeter(
    level: MonitorLevel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Top row: caps label + dB readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "INPUT LEVEL",
                style = LocalAppTypography.current.labelTiny,
                color = RecorderBlueGrey,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = formatDb(level.rmsDb),
                    style = LocalAppTypography.current.numericSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (level.clipped) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(SemanticError),
                    )
                }
            }
        }

        // The bar itself
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(MaterialTheme.shapes.small)
                .background(SurfaceContainerHigh),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 0.dp)) {
                val w = size.width
                val h = size.height
                val rmsFrac = fractionFromDb(level.rmsDb)
                val peakFrac = fractionFromDb(level.peakDb)

                // Fill — gradient stops positioned so red appears only near 0 dB
                val gradient = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f  to SpectrumLow,
                        0.80f to SpectrumMid,
                        0.95f to SpectrumHigh,
                        1.0f  to SemanticError,
                    ),
                    startX = 0f,
                    endX = w,
                )
                drawRect(brush = gradient, topLeft = Offset.Zero, size = Size(width = w * rmsFrac, height = h))

                // Peak-hold marker: thin vertical bar
                if (peakFrac > 0f) {
                    val x = (w * peakFrac).coerceIn(0f, w - 2f)
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(x, 0f),
                        size = Size(width = 2f, height = h),
                    )
                }
            }
        }
    }
}

/** Map dB [-60..0] → fraction [0..1] linearly, clamped. */
internal fun fractionFromDb(db: Float): Float {
    val f = (db + 60f) / 60f
    return f.coerceIn(0f, 1f)
}

/** Render dB with one decimal place. Forces Locale.US so the decimal separator is a dot, never a comma. */
internal fun formatDb(db: Float): String {
    val rounded = (db * 10f).toInt() / 10f
    return String.format(java.util.Locale.US, "%.1f dB", rounded)
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/MonitorLevelMeter.kt
git commit -m "feat(ui): MonitorLevelMeter composable — INPUT LEVEL label + bar + peak hold + clip dot"
```

---

## Task 5: RecorderApp integration

**Goal:** Show the meter below `RecorderFeatureChips`, only when monitor is on and we are not recording.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt`

- [ ] **Step 1: Add the import**

Find the existing `import com.example.recorderproject.ui.components.RecorderFeatureChips` line. **Immediately below it**, add:

```kotlin
import com.example.recorderproject.ui.components.MonitorLevelMeter
```

- [ ] **Step 2: Observe monitorLevel state**

Find the existing `val monitorOn by viewModel.monitorEnabled.collectAsStateWithLifecycle()` line. **Immediately below it**, add:

```kotlin
    val monitorLevel by viewModel.monitorLevel.collectAsStateWithLifecycle()
```

- [ ] **Step 3: Render the meter**

Find the `RecorderFeatureChips(...)` call (around lines 207–213). **Immediately after the closing `)` of that call** (and before the `AnimatedVisibility(visible = !isRecording, ...)` block that wraps `PreRecordInputsCard`), insert:

```kotlin
            // Pre-record input level meter — visible only when monitoring and not recording
            AnimatedVisibility(
                visible = monitorOn && !isRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MonitorLevelMeter(level = monitorLevel, modifier = Modifier.fillMaxWidth())
            }
```

The `AnimatedVisibility`, `fadeIn`, `fadeOut`, `expandVertically`, `shrinkVertically` symbols are already imported at the top of the file (used by the `PreRecordInputsCard` block below).

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt
git commit -m "feat(ui): show MonitorLevelMeter in RecorderApp when monitorOn and not recording"
```

---

## Task 6: Final verification

**Goal:** Clean build + tests + manual sanity walk.

**Files:** None — verification only.

- [ ] **Step 1: Clean build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew clean assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run full test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, MonitorLevelTest's 9 tests pass, all pre-existing audio/model tests still pass.

- [ ] **Step 3: Install + manual walk on a connected device or emulator**

```bash
./gradlew installDebug
```

Walk:
1. Launch MEAT REC. Verify the recorder home screen still renders correctly (no layout regression).
2. Tap the **🎧 Monitor** feature chip → it turns active (orange background).
3. The **INPUT LEVEL** meter appears below the chips with a fade+expand animation.
4. Speak into the mic at conversational volume → the bar fills proportionally and the dB readout updates (~-30 to -20 dB).
5. Pause speaking → the peak-hold marker visibly decays toward the rms level over ~1 second.
6. Speak loudly (clap, shout) → peak hits the right edge, red clip dot appears next to the dB readout. Stop the loud signal → clip dot stays lit for ~2 s then disappears.
7. Tap Monitor chip again → meter disappears (fade+shrink).
8. Toggle Monitor on, then press **Record** → meter disappears (because `!isRecording` is false), the existing recording UI takes over.
9. Stop recording → if Monitor is still on, meter reappears.

- [ ] **Step 4: Verify git log**

```bash
git log --oneline -7
```

Expected: 5 task commits (Tasks 1–5) plus this plan's commit and the spec's commit:

```
<sha> feat(ui): show MonitorLevelMeter in RecorderApp when monitorOn and not recording
<sha> feat(ui): MonitorLevelMeter composable — INPUT LEVEL label + bar + peak hold + clip dot
<sha> feat(vm): expose monitorLevel StateFlow + decay coroutine + AudioMonitor listener wiring
<sha> feat(audio): AudioMonitor per-buffer RMS/peak callback via setLevelListener
<sha> feat(model): MonitorLevel data class + applyAudioSample/applyDecayTick pure helpers
<sha> docs(plan): monitor level meter implementation plan (6 tasks)
9efc743 docs(spec): monitor level meter design (pre-record input metering)
```

- [ ] **Step 5: No commit**

This task is verification, not implementation. Nothing to commit.

---

## Done

After Task 6 passes, the pre-record monitor level meter is shipped. Next up (separate spec/plan cycles): L/R stereo level (when mono → stereo capture lands), latency display, or porting the old per-Settings layout (Sample Rate / Bit Depth / Channels / Countdown / Max Duration cards + Theme picker).
