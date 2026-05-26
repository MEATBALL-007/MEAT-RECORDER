# Monitor Level Meter — Design

**Date:** 2026-05-26
**Sibling to:** `2026-05-25-recorder-roadmap.md` (small feature; not a full phase — a discrete add-on)
**Status:** Approved design, awaiting user review
**Target:** Android, Kotlin + Jetpack Compose, minSdk 24

---

## Goal

Add a pre-record input level meter that appears whenever Monitor mode is on (and we're not already recording), so the user can adjust gain / mic placement against a visible RMS + peak-hold readout before pressing Record.

This is a small, focused add-on. Not a phase. One spec → one plan → ship.

## Why now

`AudioMonitor` already pipes mic → headphones in real time and applies the live EQ chain. The "Monitor" chip already toggles it. What the user can't do today: see how loud their input is before committing to a take. Setting gain blindly leads to clipped takes or whisper-quiet takes that need retries. A meter that shows the same audio the user is hearing closes the loop.

## Non-goals

- Latency measurement / display (deferred to a future Oboe/AAudio migration)
- Stereo separate L/R meters (mic capture is mono)
- LUFS / EBU R128 metering (different concept; `LufsProcessor` already handles recording-side LUFS)
- Saving meter readings into recording metadata
- Replacing the in-recording `RecordingMeterBar` (different use; covered)

## Architecture

```
AudioMonitor.run()                       RecorderViewModel                       RecorderApp
─────────────────                       ─────────────────                       ───────────
read PCM buf  ──compute RMS/peak──▶   _monitorLevel: StateFlow<MonitorLevel> ──▶ MonitorLevelMeter
                                       │                                       (visible iff monitorOn && !isRecording)
                                       │
                                       └─ peak-hold decay coroutine (50ms tick, -1dB/tick toward RMS)
```

Single source of truth: the same PCM samples the user hears are the samples that drive the meter. No second AudioRecord. Computation lives in `AudioMonitor.run()` as one extra line per buffer.

## Components

### 1. `model/MonitorLevel.kt` — NEW

```kotlin
package com.example.recorderproject.model

data class MonitorLevel(
    /** Smoothed RMS in dBFS, range [-60f, 0f]. -60f = silence. */
    val rmsDb: Float,
    /** Peak-hold value in dBFS, range [-60f, 0f]. Decays toward rmsDb. */
    val peakDb: Float,
    /** True if signal exceeded -0.1 dBFS in the last 2 seconds. */
    val clipped: Boolean,
) {
    companion object {
        val Silent = MonitorLevel(rmsDb = -60f, peakDb = -60f, clipped = false)
    }
}
```

### 2. `audio/AudioMonitor.kt` — additive change

Add:
- `private var levelListener: ((rmsDb: Float, peakDb: Float) -> Unit)? = null`
- `fun setLevelListener(cb: ((Float, Float) -> Unit)?) { levelListener = cb }`
- Inside `run()`, after `track.write(buf, 0, read)`:
  ```kotlin
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
      val rmsDb  = if (rms <= 0.0)  -60f else (20.0 * kotlin.math.log10(rms)).toFloat().coerceAtLeast(-60f)
      val peakDb = if (pk  <= 0.0)  -60f else (20.0 * kotlin.math.log10(pk )).toFloat().coerceAtLeast(-60f)
      cb(rmsDb, peakDb)
  }
  ```

No new threads. No allocations per buffer (cb invocation is one Float×2). Listener cleared when `stop()` is called by the caller's lifecycle.

### 3. `RecorderViewModel.kt` — additive change

Add:
- `private val _monitorLevel = MutableStateFlow(MonitorLevel.Silent)`
- `val monitorLevel: StateFlow<MonitorLevel> = _monitorLevel`
- `private var peakHoldJob: Job? = null`
- New ViewModel field: `private var clipUntilMs: Long = 0L`
- New private helper:
  ```kotlin
  private fun onMonitorPcm(rmsDb: Float, peakDb: Float) {
      val curr = _monitorLevel.value
      val newPeak = maxOf(peakDb, curr.peakDb)  // peak hold
      val newClipped = peakDb > -0.1f || (curr.clipped && System.currentTimeMillis() < clipUntilMs)
      if (peakDb > -0.1f) clipUntilMs = System.currentTimeMillis() + 2_000
      _monitorLevel.value = MonitorLevel(rmsDb = rmsDb, peakDb = newPeak, clipped = newClipped)
  }
  ```
- Extract the pure decision logic as a top-level function `fun nextLevel(curr: MonitorLevel, newRms: Float, newPeak: Float, nowMs: Long, clipUntilMs: Long): Pair<MonitorLevel, Long>` so it can be unit-tested without ViewModel scaffolding. The `onMonitorPcm` helper and the decay coroutine both call `nextLevel`.
- Start/stop peak-hold decay coroutine in `toggleMonitor()`:
  - On: launch a `viewModelScope` job that ticks every 50 ms, decays `peakDb` by `1f` toward `rmsDb`, and clears stale clip flag. Store as `peakHoldJob`.
  - Off: `peakHoldJob?.cancel(); peakHoldJob = null; _monitorLevel.value = MonitorLevel.Silent`
- In `toggleMonitor()` ON-path: call `audioMonitor.setLevelListener(::onMonitorPcm)` **before** `audioMonitor.start()` so the very first PCM buffer is observed
- In `toggleMonitor()` OFF-path: call `audioMonitor.setLevelListener(null)` **after** `audioMonitor.stop()` so the run-loop has finished before the listener field is cleared (avoids races even though atomic ref assignment makes this a defensive ordering)

### 4. `ui/components/MonitorLevelMeter.kt` — NEW

```kotlin
@Composable
fun MonitorLevelMeter(level: MonitorLevel, modifier: Modifier = Modifier) {
    // Horizontal bar: 24dp tall, full width
    // Layout (left to right inside a Row):
    //   - Label "INPUT" (labelTiny, RecorderBlueGrey), width-fixed
    //   - Bar (weight 1f, height 12dp)
    //   - Numeric readout "-12.3 dB" (numericMedium tabular figures), width-fixed
    // Bar internals:
    //   - Background: SurfaceContainerHigh, MaterialTheme.shapes.small
    //   - Tick marks at -60/-40/-20/-6/0 dB (thin 1dp lines, RecorderBlueGrey alpha 0.4)
    //   - Fill width: fractionFromDb(level.rmsDb) * barWidth
    //   - Fill color: horizontal linear gradient applied across the full bar width
    //     with stops positioned non-linearly so the visible color encodes "headroom":
    //       SpectrumLow at fraction 0.0    (= -60 dB, deep blue)
    //       SpectrumMid at fraction 0.80   (= -12 dB, yellow)
    //       SpectrumHigh at fraction 0.95  (= -3 dB, orange)
    //       SemanticError at fraction 1.0  (= 0 dB, red)
    //     The fill width is then clipped to fractionFromDb(rmsDb) — the gradient
    //     shows through whichever stops the fill reaches.
    //   - Peak hold marker: 2dp vertical bar at fractionFromDb(level.peakDb)
    //   - Clip dot: 6dp circle in SemanticError, top-right of bar, visible iff level.clipped
    //
    // fractionFromDb(db): linear mapping `(db + 60f) / 60f`, clamped to [0, 1].
}
```

Width-fixed sides ensure the bar always has the same starting/ending pixel regardless of readout value (tabular figures from `numericMedium` keep digits aligned).

### 5. `RecorderApp.kt` — additive change

Below the `RecorderFeatureChips(...)` call, insert:

```kotlin
val monitorLevel by viewModel.monitorLevel.collectAsStateWithLifecycle()
AnimatedVisibility(
    visible = monitorOn && !isRecording,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
) {
    MonitorLevelMeter(level = monitorLevel, modifier = Modifier.fillMaxWidth())
}
```

## Performance

- Per-buffer work: one pass over `read` shorts to accumulate sumSq + peakAbs. Buffer sizes 1024-4096 shorts. ~5-15 µs per buffer on a midrange device. Negligible.
- StateFlow emission rate: ~20-200 Hz depending on buffer size; Compose recomposition naturally collapses fast-changing values via state-snapshot. Acceptable.
- Peak-hold coroutine: 50 ms ticks = 20 Hz. Trivial.

## Testing

- **Unit:** `MonitorLevelTest` (pure-JVM) — verify `MonitorLevel` data class invariants (clamp [-60, 0]) and the peak-hold decay logic (extracted as a pure function `nextLevel(curr, newRms, newPeak, nowMs)`).
- **Manual:** Toggle Monitor → speak → verify meter responds. Pinch input to mic → verify clip dot. Speak then go silent → verify peak hold decays smoothly.
- No instrumented test (would require Oboe/AAudio test fixtures; the meter is observable enough via manual check).

## Migration / token usage

This feature is built on the design-system foundation that shipped 2026-05-26. Uses tokens throughout:
- `SurfaceContainerHigh` for bar background
- `Spectrum{Low,Mid,High}` for the bar gradient
- `SemanticError` for clip dot
- `LocalAppTypography.current.labelTiny` for "INPUT" label
- `LocalAppTypography.current.numericMedium` for "-12.3 dB" readout (tabular figures)
- `Spacing.sm` / `Spacing.md` for padding/gaps
- `MaterialTheme.shapes.small` for bar rounding

No new design tokens added. This is a consumer of the foundation, not an extender of it.

## Definition of done

- [ ] `model/MonitorLevel.kt` exists with `data class MonitorLevel(rmsDb, peakDb, clipped)` + `Silent` companion default
- [ ] `AudioMonitor.setLevelListener(cb)` API added; level computed in `run()` and pushed per buffer
- [ ] `RecorderViewModel.monitorLevel: StateFlow<MonitorLevel>` exposed; peak-hold + clip-decay coroutine starts on monitor-on, cancels on monitor-off
- [ ] `MonitorLevelMeter` composable exists in `ui/components/` and renders bar, peak marker, clip dot, label, numeric readout
- [ ] `RecorderApp` shows the meter inside `AnimatedVisibility(monitorOn && !isRecording)`
- [ ] One unit test (`nextLevel` pure function) covers peak-hold decay + clip-timeout logic
- [ ] `./gradlew assembleDebug` + `./gradlew test` both green
- [ ] Manual walk: speak → bar moves, peak holds and decays, clip dot lights at loud signal, hides on stop/record
- [ ] Spec + plan + implementation commits all live in git

## Open questions

None.

## Risks

- **Bluetooth SCO latency:** when monitor is on via BT, the round-trip from mic → meter is the same ~80 ms the user hears. The meter is "honest" — what it shows is what's been measured, lagging by the chain delay. Acceptable; documenting it in case a future feature surfaces "actual" vs "what you hear" separately.
- **Concurrent listener change:** if `setLevelListener(null)` runs concurrently with `cb?.invoke(...)`, the listener could be invoked after being cleared. The `@Volatile`-equivalent for the listener field is via the natural `var listener` field on the JVM — atomic for reference assignment. The worst case is one extra invocation; harmless.
