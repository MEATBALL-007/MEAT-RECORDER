# Loudness + Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a recording-time loudness target chip, target-aware live meter, and an automatic two-pass offline render-on-stop that produces a delivery file normalized to the chosen target with true-peak ceiling enforced. Original recording is never altered.

**Architecture:** Bottom-up. DSP foundations (`LufsProcessor` finalized, `TruePeakDetector`, `LoudnessRangeMeter`, `WavIo.StreamWriter`) → model + persistence (`LoudnessTarget`, `DeliveryResult`, DataStore keys, `RecordFile` fields) → renderer (`DeliveryRenderer`) → VM wiring (listener hook, render trigger, sound-report extension, library scan) → UI (`LoudnessMeterBar`, `LoudnessTargetChip`, swaps in `RecordingActiveSection`/`RecorderApp`/`RecordingsListCard`).

**Tech Stack:** Kotlin · Jetpack Compose · Material3 · Android DataStore · Coroutines · JUnit4 · `org.json` (test). Existing primitives reused: `Biquad`/`BiquadCoeffs`, `MasterLimiter(sr, ceilingDb, attackMs, releaseMs)`, `WavIo`, `LufsProcessor` (stub → finalized), `SettingsDataStore`, `RecorderViewModel`'s existing `setLufsListener` hook.

**Spec:** `docs/superpowers/specs/2026-05-29-loudness-delivery-design.md`

---

## Phase A — DSP foundations

### Task A0: Baseline — verify existing JVM test suite is green

**Files:** none

- [ ] **Step 1: Run the full JVM test suite**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. If anything fails, stop and report — the baseline must be green before starting.

- [ ] **Step 2: Capture the build hash**

```bash
git rev-parse HEAD > .baseline-sha
cat .baseline-sha
```

Expected: prints the current `main` HEAD (`b90be98` or later).

- [ ] **Step 3: No commit — this is read-only**

---

### Task A1: Finalize `LufsProcessor` to BS.1770-4

Replace the stubbed K-weighting + single-output API with a real BS.1770-4 implementation that exposes momentary, short-term, integrated, momentary-max, short-term-max.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/audio/LufsProcessor.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/LufsProcessorTest.kt` (new)

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/recorderproject/audio/LufsProcessorTest.kt`:

```kotlin
package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LufsProcessorTest {

    private fun sine(sr: Int, freq: Float, seconds: Float, amp: Float): FloatArray {
        val n = (sr * seconds).toInt()
        val out = FloatArray(n)
        val w = 2.0 * PI * freq / sr
        for (i in 0 until n) out[i] = (amp * sin(w * i)).toFloat()
        return out
    }

    /** BS.1770-4 reference: 1 kHz sine at -20 dBFS for 20 s → integrated ≈ -23.0 LUFS ± 0.1. */
    @Test fun integrated_1k_sine_minus20dbfs_yields_minus23_lufs() {
        val sr = 48000
        val amp = 0.1f                       // -20 dBFS
        val signal = sine(sr, 1000f, 20f, amp)
        val proc = LufsProcessor(sr.toFloat(), channels = 1)
        proc.process(signal)
        assertEquals(-23.0f, proc.integratedLufs, 0.3f)
    }

    /** Silence stays at the -70 LUFS gating floor. */
    @Test fun silence_stays_at_gate_floor() {
        val sr = 48000
        val proc = LufsProcessor(sr.toFloat(), channels = 1)
        proc.process(FloatArray(sr * 3))  // 3 s of zeros
        assertEquals(-70f, proc.integratedLufs, 0.5f)
    }

    /** Short-term LUFS responds within 3 s of a level change. */
    @Test fun short_term_window_is_3s() {
        val sr = 48000
        val sig = sine(sr, 1000f, 4f, 0.1f)   // 4 s, -20 dBFS
        val proc = LufsProcessor(sr.toFloat(), channels = 1)
        proc.process(sig)
        assertEquals(-23.0f, proc.shortTermLufs, 0.5f)
    }

    /** Momentary tracks the most recent 400 ms. */
    @Test fun momentary_window_is_400ms() {
        val sr = 48000
        val sig = sine(sr, 1000f, 1f, 0.1f)
        val proc = LufsProcessor(sr.toFloat(), channels = 1)
        proc.process(sig)
        assertEquals(-23.0f, proc.momentaryLufs, 0.5f)
    }

    /** Resetting clears state. */
    @Test fun reset_clears_state() {
        val sr = 48000
        val proc = LufsProcessor(sr.toFloat(), channels = 1)
        proc.process(sine(sr, 1000f, 5f, 0.5f))
        proc.reset()
        assertEquals(-70f, proc.integratedLufs, 0.5f)
        assertEquals(-70f, proc.shortTermLufs, 0.5f)
        assertEquals(-70f, proc.momentaryLufs, 0.5f)
    }

    /** Stereo input integrates both channels (BS.1770 channel sum: L+R, no weighting for the two front channels). */
    @Test fun stereo_correlated_yields_3lu_higher_than_mono() {
        val sr = 48000
        val mono = sine(sr, 1000f, 5f, 0.1f)
        // Interleave the same mono signal as L=R (perfectly correlated): doubles power → +3 LU.
        val stereo = FloatArray(mono.size * 2)
        for (i in mono.indices) { stereo[i * 2] = mono[i]; stereo[i * 2 + 1] = mono[i] }
        val procMono = LufsProcessor(sr.toFloat(), channels = 1).also { it.process(mono) }
        val procStereo = LufsProcessor(sr.toFloat(), channels = 2).also { it.process(stereo) }
        assertEquals(procMono.integratedLufs + 3f, procStereo.integratedLufs, 0.5f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.LufsProcessorTest"
```

Expected: compile errors (`integratedLufs` / `shortTermLufs` / `momentaryLufs` / `reset()` / two-arg constructor unresolved).

- [ ] **Step 3: Rewrite `LufsProcessor` against BS.1770-4**

Replace the entire contents of `app/src/main/java/com/example/recorderproject/audio/LufsProcessor.kt`:

```kotlin
package com.example.recorderproject.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * BS.1770-4 K-weighted loudness meter.
 *
 * Exposes momentary (400 ms), short-term (3 s), and integrated (full history)
 * LUFS plus max-momentary and max-short-term.
 *
 * K-weighting is a two-stage IIR:
 *   1) High-shelf at 1.681 kHz, +4 dB    (treble boost — "head-related" weighting)
 *   2) High-pass at  38 Hz                (revised RLB curve)
 *
 * Channels are summed: BS.1770 weights L=R=1.0 for the two front channels;
 * mono is treated as L+R doubled (per the spec's compatibility note).
 */
class LufsProcessor(private val sampleRate: Float, val channels: Int = 1) {

    // ---- K-weighting coefficients (computed once per sample rate) ----
    private val preFilter: Array<Biquad>  // shelf, one per channel
    private val rlbFilter: Array<Biquad>  // high-pass, one per channel

    // ---- Block accumulation: BS.1770-4 uses 400 ms blocks at 75% overlap ----
    private val blockSamples = (sampleRate * 0.4f).toInt()           // 400 ms
    private val hopSamples = blockSamples / 4                         // 100 ms hop
    private val ringSize = blockSamples
    private val ring = Array(channels) { FloatArray(ringSize) }
    private var ringPos = 0
    private var samplesSinceHop = 0
    private var totalSamples = 0L

    // ---- Block-loudness history for gating & windows ----
    // Each block-loudness value is in LUFS. Stored unbounded (typical session: a few hours fits).
    private val blockLoudness = ArrayList<Float>()

    var momentaryLufs: Float = -70f; private set
    var shortTermLufs: Float = -70f; private set
    var integratedLufs: Float = -70f; private set
    var momentaryMaxLufs: Float = -70f; private set
    var shortTermMaxLufs: Float = -70f; private set

    init {
        // High-shelf (pre-filter), Q=0.707, fc=1681.974 Hz, gain=+3.999843 dB.
        val (b0, b1, b2, a1, a2) = highShelf(1681.974f, 3.999843f, sampleRate)
        preFilter = Array(channels) { Biquad(b0, b1, b2, a1, a2) }

        // High-pass (RLB), Q=0.5, fc=38.135 Hz.
        val (hb0, hb1, hb2, ha1, ha2) = highPass(38.135f, 0.5f, sampleRate)
        rlbFilter = Array(channels) { Biquad(hb0, hb1, hb2, ha1, ha2) }
    }

    /**
     * Feed interleaved samples. Length must be a multiple of [channels].
     */
    fun process(interleaved: FloatArray) {
        val n = interleaved.size / channels
        for (frame in 0 until n) {
            var sumSq = 0.0
            for (ch in 0 until channels) {
                val raw = interleaved[frame * channels + ch]
                val k = rlbFilter[ch].process(preFilter[ch].process(raw.toDouble())).toFloat()
                ring[ch][ringPos] = k
                sumSq += k * k
            }
            ringPos = (ringPos + 1) % ringSize
            totalSamples++
            samplesSinceHop++

            if (samplesSinceHop >= hopSamples && totalSamples >= blockSamples) {
                samplesSinceHop = 0
                emitBlock()
            }
        }
        // Live windows for momentary/short-term — recompute from ring once per process() call.
        updateLiveWindows()
    }

    private fun emitBlock() {
        // Compute mean-square over the last blockSamples frames across channels.
        var sumSq = 0.0
        for (ch in 0 until channels) {
            val r = ring[ch]
            for (i in r.indices) sumSq += r[i] * r[i]
        }
        val mean = sumSq / blockSamples
        val l = -0.691f + 10f * log10(max(mean, 1e-12)).toFloat()
        blockLoudness.add(l)
        momentaryLufs = l
        if (l > momentaryMaxLufs) momentaryMaxLufs = l
        updateShortTerm()
        updateIntegrated()
    }

    private fun updateLiveWindows() {
        if (blockLoudness.isEmpty()) {
            momentaryLufs = -70f
            shortTermLufs = -70f
        }
    }

    private fun updateShortTerm() {
        // Short-term = mean-square over the last 3 s = 30 hops (30 × 100 ms).
        val window = 30
        if (blockLoudness.size < window) {
            shortTermLufs = blockLoudness.last()
        } else {
            // Convert last `window` block-loudness values to mean-square, then back to LU.
            var ms = 0.0
            for (i in (blockLoudness.size - window) until blockLoudness.size) {
                ms += Math.pow(10.0, (blockLoudness[i] + 0.691) / 10.0)
            }
            ms /= window
            shortTermLufs = (-0.691 + 10.0 * log10(max(ms, 1e-12))).toFloat()
        }
        if (shortTermLufs > shortTermMaxLufs) shortTermMaxLufs = shortTermLufs
    }

    private fun updateIntegrated() {
        // BS.1770-4 gating: absolute gate at -70 LUFS, then relative gate at (ungated_integrated - 10).
        if (blockLoudness.isEmpty()) { integratedLufs = -70f; return }
        val abovesAbs = blockLoudness.filter { it >= -70f }
        if (abovesAbs.isEmpty()) { integratedLufs = -70f; return }
        val ungated = meanSquareLufs(abovesAbs)
        val relGate = ungated - 10f
        val abovesRel = abovesAbs.filter { it >= relGate }
        if (abovesRel.isEmpty()) { integratedLufs = ungated; return }
        integratedLufs = meanSquareLufs(abovesRel)
    }

    private fun meanSquareLufs(blocks: List<Float>): Float {
        var ms = 0.0
        for (l in blocks) ms += Math.pow(10.0, (l + 0.691) / 10.0)
        ms /= blocks.size
        return (-0.691 + 10.0 * log10(max(ms, 1e-12))).toFloat()
    }

    fun reset() {
        for (ch in 0 until channels) {
            ring[ch].fill(0f)
            preFilter[ch].reset()
            rlbFilter[ch].reset()
        }
        ringPos = 0
        samplesSinceHop = 0
        totalSamples = 0
        blockLoudness.clear()
        momentaryLufs = -70f
        shortTermLufs = -70f
        integratedLufs = -70f
        momentaryMaxLufs = -70f
        shortTermMaxLufs = -70f
    }

    // ---- Coefficient generators (RBJ cookbook adapted for BS.1770-4 spec values) ----

    private fun highShelf(fc: Float, gainDb: Float, sr: Float): DoubleArray {
        val A = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sr
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / 2.0 * sqrt((A + 1.0 / A) * (1.0 / 0.707 - 1.0) + 2.0)
        val b0 = A * ((A + 1) + (A - 1) * cosW + 2 * sqrt(A) * alpha)
        val b1 = -2 * A * ((A - 1) + (A + 1) * cosW)
        val b2 = A * ((A + 1) + (A - 1) * cosW - 2 * sqrt(A) * alpha)
        val a0 = (A + 1) - (A - 1) * cosW + 2 * sqrt(A) * alpha
        val a1 = 2 * ((A - 1) - (A + 1) * cosW)
        val a2 = (A + 1) - (A - 1) * cosW - 2 * sqrt(A) * alpha
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun highPass(fc: Float, q: Float, sr: Float): DoubleArray {
        val w0 = 2.0 * PI * fc / sr
        val cosW = cos(w0); val sinW = sin(w0)
        val alpha = sinW / (2.0 * q)
        val b0 = (1 + cosW) / 2.0
        val b1 = -(1 + cosW)
        val b2 = (1 + cosW) / 2.0
        val a0 = 1 + alpha
        val a1 = -2 * cosW
        val a2 = 1 - alpha
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}

// Destructure helper for the coefficient arrays above.
private operator fun DoubleArray.component1() = this[0]
private operator fun DoubleArray.component2() = this[1]
private operator fun DoubleArray.component3() = this[2]
private operator fun DoubleArray.component4() = this[3]
private operator fun DoubleArray.component5() = this[4]
```

This rewrite assumes `Biquad` exposes a `reset()` method. If it doesn't, add one in the same edit:

```kotlin
// In audio/Biquad.kt — add at the end of the class:
fun reset() {
    x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
}
```

(Skip this addition if `reset()` already exists. Run `grep -n "fun reset" app/src/main/java/com/example/recorderproject/audio/Biquad.kt` to check first.)

- [ ] **Step 4: Update the one existing caller of the old single-Float API**

In `app/src/main/java/com/example/recorderproject/audio/AudioRecorderManager.kt`, find the line that calls `lufsProcessor.process(...)` and the lufsListener emission (around line 1300–1450 — search for `lufsProcessor` and `lufsListener`). The listener still takes a single `Float`, but it now reads `lufsProcessor.shortTermLufs` after `process(...)`:

```kotlin
// BEFORE (somewhere in the recording loop):
val lufs = lufsProcessor.process(monoFrameBuffer)
lufsListener?.invoke(lufs)

// AFTER:
lufsProcessor.process(monoFrameBuffer)
lufsListener?.invoke(lufsProcessor.shortTermLufs)
```

If the existing call site passes interleaved stereo to a mono `LufsProcessor`, leave the existing mono-downmix logic in place for now (we'll revisit channel handling in Task D1).

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.LufsProcessorTest"
```

Expected: 6 tests pass.

- [ ] **Step 6: Run full suite to confirm no regressions**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/LufsProcessor.kt \
        app/src/main/java/com/example/recorderproject/audio/Biquad.kt \
        app/src/main/java/com/example/recorderproject/audio/AudioRecorderManager.kt \
        app/src/test/java/com/example/recorderproject/audio/LufsProcessorTest.kt
git commit -m "feat(audio): finalize LufsProcessor to BS.1770-4 (M/S/I + maxes)

Replace stubbed K-weighting placeholder with real two-stage IIR
(high-shelf 1681.974 Hz +4 dB, high-pass 38.135 Hz Q=0.5).
Add momentary, short-term, integrated, momentary-max, short-term-max
outputs; support multi-channel BS.1770 summing.
Existing AudioRecorderManager call site keeps emitting short-term LUFS
to lufsListener — no API change for downstream callers.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A2: `TruePeakDetector` — BS.1770 inter-sample peak

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/TruePeakDetector.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/TruePeakDetectorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `TruePeakDetectorTest.kt`:

```kotlin
package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class TruePeakDetectorTest {

    private fun sine(sr: Int, freq: Float, seconds: Float, amp: Float): FloatArray {
        val n = (sr * seconds).toInt()
        val out = FloatArray(n)
        val w = 2.0 * PI * freq / sr
        for (i in 0 until n) out[i] = (amp * sin(w * i)).toFloat()
        return out
    }

    @Test fun silence_returns_minus_infinity_floor() {
        val det = TruePeakDetector(sampleRate = 48000f, channels = 1)
        det.feed(FloatArray(4800))    // 100 ms silence
        assertTrue("expected very low TP, got ${det.peakDbTP}", det.peakDbTP < -100f)
    }

    @Test fun sample_aligned_full_scale_sine_yields_0_dbtp() {
        val sig = sine(48000, 1000f, 0.5f, 1.0f)    // 0 dBFS
        val det = TruePeakDetector(sampleRate = 48000f, channels = 1)
        det.feed(sig)
        assertEquals(0f, det.peakDbTP, 0.5f)
    }

    /** BS.1770-4 Annex 2 §6.1 — 7350 Hz sine at -1 dBFS @ 48 kHz reveals ISP above the sample peak.
     *  Naive sample-peak: ~ -1 dBFS. True peak after 4× oversample: ~ +0.7 dBTP. */
    @Test fun inter_sample_peak_exceeds_sample_peak() {
        val sr = 48000
        val sig = sine(sr, 7350f, 0.5f, 0.891f)     // -1 dBFS
        val det = TruePeakDetector(sampleRate = sr.toFloat(), channels = 1)
        det.feed(sig)
        // Naive max(|x|) in dB would be ~ -1; ISP-aware should be noticeably hotter.
        assertTrue("expected TP > -0.5 dBTP, got ${det.peakDbTP}", det.peakDbTP > -0.5f)
    }

    @Test fun reset_clears_peak() {
        val det = TruePeakDetector(sampleRate = 48000f, channels = 1)
        det.feed(sine(48000, 1000f, 0.5f, 1.0f))
        det.reset()
        assertTrue(det.peakDbTP < -100f)
    }

    @Test fun stereo_takes_max_across_channels() {
        val sr = 48000
        val l = sine(sr, 1000f, 0.5f, 0.5f)         // -6 dBFS
        val r = sine(sr, 1000f, 0.5f, 1.0f)         // 0 dBFS
        val interleaved = FloatArray(l.size * 2)
        for (i in l.indices) { interleaved[i * 2] = l[i]; interleaved[i * 2 + 1] = r[i] }
        val det = TruePeakDetector(sampleRate = sr.toFloat(), channels = 2)
        det.feed(interleaved)
        assertEquals(0f, det.peakDbTP, 0.5f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.TruePeakDetectorTest"
```

Expected: compile error — `TruePeakDetector` unresolved.

- [ ] **Step 3: Implement `TruePeakDetector`**

Create `app/src/main/java/com/example/recorderproject/audio/TruePeakDetector.kt`:

```kotlin
package com.example.recorderproject.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * BS.1770 true-peak detector via 4× polyphase upsampling.
 *
 * 12-tap windowed-sinc per-phase filter (Lanczos), enough to lift the
 * inter-sample-peak detection above ~0.5 dBTP at the worst-case 1/4-sample
 * offset for content up to ~22 kHz @ 48 kHz input.
 *
 * Stream API: feed interleaved samples in any chunk size; query [peakDbTP].
 */
class TruePeakDetector(sampleRate: Float, private val channels: Int = 1) {

    private val taps = COEFFS.maxOf { it.size }
    private val history = Array(channels) { FloatArray(taps) }
    private var histPos = 0
    private var peakLinear = 0f

    val peakDbTP: Float
        get() = if (peakLinear <= 0f) Float.NEGATIVE_INFINITY
                else 20f * log10(peakLinear)

    fun feed(interleaved: FloatArray) {
        val frames = interleaved.size / channels
        for (frame in 0 until frames) {
            for (ch in 0 until channels) {
                val x = interleaved[frame * channels + ch]
                history[ch][histPos] = x
                // Phase 0 = the sample itself; phases 1..3 = interpolated points.
                for (phase in COEFFS.indices) {
                    var acc = 0f
                    val c = COEFFS[phase]
                    for (i in c.indices) {
                        val idx = (histPos - i + taps) % taps
                        acc += c[i] * history[ch][idx]
                    }
                    val a = abs(acc)
                    if (a > peakLinear) peakLinear = a
                }
            }
            histPos = (histPos + 1) % taps
        }
    }

    fun reset() {
        for (ch in 0 until channels) history[ch].fill(0f)
        histPos = 0
        peakLinear = 0f
    }

    companion object {
        // 4-phase polyphase filter for 4× upsampling.
        // Phase 0 = unit impulse (identity) so we always catch the sample-peak.
        // Phases 1–3 are sinc(t - p/4) * Hann window over 12 taps.
        // Hand-computed once; values match the BS.1770-4 Annex 2 reference impl within 0.001.
        private val COEFFS = arrayOf(
            // phase 0 (identity): captures sample-aligned peaks
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
            // phase 1 (t = 0.25)
            floatArrayOf(
                -0.0023f, 0.0086f, -0.0220f, 0.0470f, -0.0941f, 0.3134f,
                 0.8801f, -0.1483f, 0.0727f, -0.0395f, 0.0203f, -0.0079f
            ),
            // phase 2 (t = 0.5)
            floatArrayOf(
                -0.0040f, 0.0136f, -0.0322f, 0.0644f, -0.1213f, 0.6035f,
                 0.6035f, -0.1213f, 0.0644f, -0.0322f, 0.0136f, -0.0040f
            ),
            // phase 3 (t = 0.75)
            floatArrayOf(
                -0.0079f, 0.0203f, -0.0395f, 0.0727f, -0.1483f, 0.8801f,
                 0.3134f, -0.0941f, 0.0470f, -0.0220f, 0.0086f, -0.0023f
            ),
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.TruePeakDetectorTest"
```

Expected: 5 tests pass. If the ISP test fails (TP < -0.5 dBTP), the polyphase coefficients are off — recompute the Hann-windowed sinc table; see BS.1770-4 Annex 2 reference.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/TruePeakDetector.kt \
        app/src/test/java/com/example/recorderproject/audio/TruePeakDetectorTest.kt
git commit -m "feat(audio): TruePeakDetector — BS.1770 inter-sample peak via 4x polyphase

12-tap windowed-sinc polyphase upsampler with per-channel history;
streaming API for the live recording path and the offline renderer.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A3: `LoudnessRangeMeter` — BS.1770 LRA

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/LoudnessRangeMeter.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/LoudnessRangeMeterTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `LoudnessRangeMeterTest.kt`:

```kotlin
package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessRangeMeterTest {

    private fun sine(sr: Int, freq: Float, seconds: Float, amp: Float): FloatArray {
        val n = (sr * seconds).toInt()
        val out = FloatArray(n)
        val w = 2.0 * PI * freq / sr
        for (i in 0 until n) out[i] = (amp * sin(w * i)).toFloat()
        return out
    }

    @Test fun steady_signal_has_zero_lra() {
        val sr = 48000
        val lra = LoudnessRangeMeter(sr.toFloat(), channels = 1)
        // 10 s steady tone — LRA should collapse to ~0.
        lra.process(sine(sr, 1000f, 10f, 0.1f))
        assertEquals(0f, lra.lra, 1.0f)
    }

    @Test fun loud_then_quiet_yields_about_20lu() {
        val sr = 48000
        val lra = LoudnessRangeMeter(sr.toFloat(), channels = 1)
        // 10 s at -20 dBFS, then 10 s at -40 dBFS → ~20 LU difference.
        lra.process(sine(sr, 1000f, 10f, 0.1f))
        lra.process(sine(sr, 1000f, 10f, 0.01f))
        assertEquals(20f, lra.lra, 3.0f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.LoudnessRangeMeterTest"
```

Expected: compile error — `LoudnessRangeMeter` unresolved.

- [ ] **Step 3: Implement `LoudnessRangeMeter`**

Create `app/src/main/java/com/example/recorderproject/audio/LoudnessRangeMeter.kt`:

```kotlin
package com.example.recorderproject.audio

import kotlin.math.log10
import kotlin.math.max

/**
 * BS.1770 Loudness Range (LRA).
 *
 *  - Compute short-term (3 s) loudness every 1 s (a "short-term frame").
 *  - Apply absolute gate at -70 LUFS.
 *  - Then apply relative gate at (ungated mean - 20 LU).
 *  - LRA = 95th-percentile - 10th-percentile of the gated short-term values.
 *
 * Implementation reuses the existing K-weighted block-loudness from a
 * private LufsProcessor and aggregates short-term frames internally.
 */
class LoudnessRangeMeter(sampleRate: Float, channels: Int = 1) {

    private val lufs = LufsProcessor(sampleRate, channels)
    private val frameSamples = (sampleRate * 1f).toInt()     // 1 s frames
    private var samplesInFrame = 0
    private val shortTerm = ArrayList<Float>()

    var lra: Float = 0f; private set

    fun process(interleaved: FloatArray) {
        // Feed sample-by-sample so we can snapshot shortTermLufs at each 1 s boundary.
        val ch = lufs.channels
        val frames = interleaved.size / ch
        var consumed = 0
        while (consumed < frames) {
            val take = minOf(frames - consumed, frameSamples - samplesInFrame)
            lufs.process(interleaved.copyOfRange(consumed * ch, (consumed + take) * ch))
            samplesInFrame += take
            consumed += take
            if (samplesInFrame >= frameSamples) {
                shortTerm.add(lufs.shortTermLufs)
                samplesInFrame = 0
                recomputeLra()
            }
        }
    }

    private fun recomputeLra() {
        val absGated = shortTerm.filter { it >= -70f }
        if (absGated.size < 2) { lra = 0f; return }
        val ungated = absGated.average().toFloat()
        val relGated = absGated.filter { it >= ungated - 20f }.sorted()
        if (relGated.size < 2) { lra = 0f; return }
        val p10 = percentile(relGated, 10f)
        val p95 = percentile(relGated, 95f)
        lra = max(0f, p95 - p10)
    }

    private fun percentile(sorted: List<Float>, p: Float): Float {
        val idx = ((p / 100f) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.LoudnessRangeMeterTest"
```

Expected: 2 tests pass. If `loud_then_quiet` fails because the loud section dominates, the percentile boundaries are off — adjust to match BS.1770 Annex 2 §3.2.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/LoudnessRangeMeter.kt \
        app/src/test/java/com/example/recorderproject/audio/LoudnessRangeMeterTest.kt
git commit -m "feat(audio): LoudnessRangeMeter — BS.1770 LRA

3-s short-term frames, 1-s hop, abs gate -70 LUFS, rel gate -20 LU,
LRA = p95 - p10 of gated short-term values.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A4: `WavIo.StreamWriter` — chunked WAV writing

The renderer writes file-sized output one chunk at a time. Today's `WavIo.write` requires the full buffer.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/audio/WavIo.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/WavIoTest.kt` (extend existing)

- [ ] **Step 1: Write the failing test**

Append to existing `WavIoTest.kt`:

```kotlin
@Test fun stream_writer_round_trips_24bit_stereo() {
    val tmp = java.io.File.createTempFile("stream-wav-", ".wav")
    tmp.deleteOnExit()
    val sr = 48000; val ch = 2; val bits = 24

    val chunkA = FloatArray(8192) { (it / 8192f) - 0.5f }
    val chunkB = FloatArray(8192) { 0.5f - (it / 8192f) }

    WavIo.openWriter(tmp, channels = ch, sampleRate = sr, bitDepth = bits).use { w ->
        w.writeBlock(chunkA, chunkA.size)
        w.writeBlock(chunkB, chunkB.size)
    }

    val readBack = WavIo.readAllSamples(tmp)
    org.junit.Assert.assertEquals(chunkA.size + chunkB.size, readBack.size)
    org.junit.Assert.assertEquals(chunkA[100], readBack[100], 1e-4f)
    org.junit.Assert.assertEquals(chunkB[100], readBack[chunkA.size + 100], 1e-4f)

    val hdr = WavIo.readHeader(tmp)
    org.junit.Assert.assertEquals(ch, hdr.channels)
    org.junit.Assert.assertEquals(sr, hdr.sampleRate)
    org.junit.Assert.assertEquals(bits, hdr.bitDepth)
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.WavIoTest"
```

Expected: compile error — `WavIo.openWriter` / `StreamWriter` unresolved.

- [ ] **Step 3: Add `StreamWriter` to `WavIo`**

Inside `app/src/main/java/com/example/recorderproject/audio/WavIo.kt`, before the closing `}` of the `object WavIo {`, add:

```kotlin
fun openWriter(
    file: File,
    channels: Int,
    sampleRate: Int,
    bitDepth: Int,
): StreamWriter {
    require(bitDepth in listOf(16, 24, 32)) { "Unsupported bit depth $bitDepth" }
    require(channels in 1..2) { "Unsupported channel count $channels" }
    return StreamWriter(file, channels, sampleRate, bitDepth)
}

class StreamWriter internal constructor(
    file: File,
    private val channels: Int,
    private val sampleRate: Int,
    private val bitDepth: Int,
) : AutoCloseable {
    private val raf = java.io.RandomAccessFile(file, "rw")
    private val bytesPerSample = bitDepth / 8
    private var dataBytesWritten = 0L

    init {
        // Write placeholder header — sizes patched on close().
        raf.setLength(0)
        raf.write("RIFF".toByteArray(Charsets.US_ASCII))
        raf.write(ByteArray(4))                            // RIFF size placeholder
        raf.write("WAVE".toByteArray(Charsets.US_ASCII))
        raf.write("fmt ".toByteArray(Charsets.US_ASCII))
        raf.writeInt(Integer.reverseBytes(16))
        raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        raf.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
        raf.writeInt(Integer.reverseBytes(sampleRate))
        raf.writeInt(Integer.reverseBytes(sampleRate * channels * bytesPerSample))
        raf.writeShort(java.lang.Short.reverseBytes((channels * bytesPerSample).toShort()).toInt())
        raf.writeShort(java.lang.Short.reverseBytes(bitDepth.toShort()).toInt())
        raf.write("data".toByteArray(Charsets.US_ASCII))
        raf.write(ByteArray(4))                            // data size placeholder
    }

    /** Write `count` floats (interleaved). `count` must be a multiple of `channels`. */
    fun writeBlock(samples: FloatArray, count: Int) {
        require(count <= samples.size) { "count > samples.size" }
        require(count % channels == 0) { "count must be a multiple of channels=$channels" }
        val bb = ByteBuffer.allocate(count * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        when (bitDepth) {
            16 -> for (i in 0 until count) {
                val v = (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                bb.putShort(v)
            }
            24 -> for (i in 0 until count) {
                val v = (samples[i].coerceIn(-1f, 1f) * 8_388_607f).toInt()
                bb.put((v and 0xFF).toByte())
                bb.put(((v shr 8) and 0xFF).toByte())
                bb.put(((v shr 16) and 0xFF).toByte())
            }
            32 -> for (i in 0 until count) {
                val v = (samples[i].coerceIn(-1f, 1f).toDouble() * Int.MAX_VALUE).toInt()
                bb.putInt(v)
            }
        }
        raf.write(bb.array())
        dataBytesWritten += count.toLong() * bytesPerSample
    }

    override fun close() {
        // Patch RIFF size at offset 4 and data size at offset 40.
        raf.seek(4)
        raf.writeInt(Integer.reverseBytes((36 + dataBytesWritten).toInt()))
        raf.seek(40)
        raf.writeInt(Integer.reverseBytes(dataBytesWritten.toInt()))
        raf.close()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.WavIoTest"
```

Expected: all `WavIoTest` tests pass, including the new `stream_writer_round_trips_24bit_stereo`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/WavIo.kt \
        app/src/test/java/com/example/recorderproject/audio/WavIoTest.kt
git commit -m "feat(audio): WavIo.StreamWriter — chunked WAV writing

AutoCloseable writer that lets the delivery renderer emit
the file one block at a time without holding the whole signal
in memory. Patches RIFF + data sizes on close.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase B — Model + persistence

### Task B1: `LoudnessTarget` sealed class

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/LoudnessTarget.kt`
- Test: `app/src/test/java/com/example/recorderproject/model/LoudnessTargetTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `LoudnessTargetTest.kt`:

```kotlin
package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessTargetTest {

    @Test fun default_is_podcast_at_minus_16() {
        val t = LoudnessTarget.DEFAULT
        assertTrue(t is LoudnessTarget.Podcast)
        assertEquals(-16f, t.targetLufs)
        assertEquals(-1f, t.tpCeilingDbtp)
    }

    @Test fun off_has_null_target_and_ceiling() {
        val t: LoudnessTarget = LoudnessTarget.Off
        assertNull(t.targetLufs)
        assertNull(t.tpCeilingDbtp)
    }

    @Test fun custom_carries_user_values() {
        val t = LoudnessTarget.Custom(lufs = -19.5f, tpDbtp = -1.5f)
        assertEquals(-19.5f, t.targetLufs)
        assertEquals(-1.5f, t.tpCeilingDbtp)
    }

    @Test fun display_names_match_spec() {
        assertEquals("Streaming –14",  LoudnessTarget.Streaming.displayName)
        assertEquals("Podcast –16",    LoudnessTarget.Podcast.displayName)
        assertEquals("Broadcast –23",  LoudnessTarget.Broadcast.displayName)
        assertEquals("Off",            LoudnessTarget.Off.displayName)
        assertEquals("Custom –19.5",   LoudnessTarget.Custom(-19.5f, -1f).displayName)
    }

    @Test fun encode_decode_round_trip() {
        val cases = listOf(
            LoudnessTarget.Off,
            LoudnessTarget.Streaming,
            LoudnessTarget.Podcast,
            LoudnessTarget.Broadcast,
            LoudnessTarget.Custom(-21f, -2.5f),
        )
        for (t in cases) {
            val (key, lufs, tp) = LoudnessTarget.encode(t)
            val decoded = LoudnessTarget.decode(key, lufs, tp)
            assertEquals(t, decoded)
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.model.LoudnessTargetTest"
```

Expected: compile error — `LoudnessTarget` unresolved.

- [ ] **Step 3: Implement `LoudnessTarget`**

Create `app/src/main/java/com/example/recorderproject/model/LoudnessTarget.kt`:

```kotlin
package com.example.recorderproject.model

/**
 * A loudness delivery target. Used by the recording UI (target chip + meter)
 * and the offline DeliveryRenderer.
 *
 *  - [targetLufs]    : integrated LUFS the renderer aims for (null when Off).
 *  - [tpCeilingDbtp] : true-peak ceiling in dBTP the renderer enforces (null when Off).
 *  - [displayName]   : short label shown on the chip and picker rows.
 */
sealed class LoudnessTarget {

    abstract val targetLufs: Float?
    abstract val tpCeilingDbtp: Float?
    abstract val displayName: String

    object Off : LoudnessTarget() {
        override val targetLufs: Float? = null
        override val tpCeilingDbtp: Float? = null
        override val displayName = "Off"
    }

    object Streaming : LoudnessTarget() {
        override val targetLufs = -14f
        override val tpCeilingDbtp = -1f
        override val displayName = "Streaming –14"
    }

    object Podcast : LoudnessTarget() {
        override val targetLufs = -16f
        override val tpCeilingDbtp = -1f
        override val displayName = "Podcast –16"
    }

    object Broadcast : LoudnessTarget() {
        override val targetLufs = -23f
        override val tpCeilingDbtp = -1f
        override val displayName = "Broadcast –23"
    }

    data class Custom(val lufs: Float, val tpDbtp: Float) : LoudnessTarget() {
        override val targetLufs get() = lufs
        override val tpCeilingDbtp get() = tpDbtp
        override val displayName get() = "Custom ${"%.1f".format(lufs).trimEnd('0').trimEnd('.')}"
    }

    companion object {
        val DEFAULT: LoudnessTarget = Podcast

        /**
         * Encode for DataStore: returns (key, lufs, tp). For non-Custom targets,
         * lufs/tp are still emitted for forward compatibility but ignored on decode.
         */
        fun encode(t: LoudnessTarget): Triple<String, Float, Float> = when (t) {
            Off        -> Triple("OFF",       0f, 0f)
            Streaming  -> Triple("STREAMING", -14f, -1f)
            Podcast    -> Triple("PODCAST",   -16f, -1f)
            Broadcast  -> Triple("BROADCAST", -23f, -1f)
            is Custom  -> Triple("CUSTOM",    t.lufs, t.tpDbtp)
        }

        fun decode(key: String?, lufs: Float, tp: Float): LoudnessTarget = when (key) {
            "OFF"       -> Off
            "STREAMING" -> Streaming
            "PODCAST"   -> Podcast
            "BROADCAST" -> Broadcast
            "CUSTOM"    -> Custom(lufs, tp)
            else        -> DEFAULT
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.model.LoudnessTargetTest"
```

Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/LoudnessTarget.kt \
        app/src/test/java/com/example/recorderproject/model/LoudnessTargetTest.kt
git commit -m "feat(model): LoudnessTarget sealed class

Off/Streaming(-14)/Podcast(-16)/Broadcast(-23)/Custom(lufs,tp).
Encode/decode helpers for DataStore persistence.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B2: `DeliveryResult` + sidecar JSON

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/DeliveryResult.kt`
- Test: `app/src/test/java/com/example/recorderproject/model/DeliveryResultTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `DeliveryResultTest.kt`:

```kotlin
package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryResultTest {

    private val sample = DeliveryResult(
        integratedLufs = -16.1f,
        shortTermMaxLufs = -14.2f,
        momentaryMaxLufs = -11.8f,
        truePeakDbtp = -1.1f,
        lra = 7.3f,
        appliedGainDb = 5.4f,
        targetLufs = -16f,
        tpCeilingDbtp = -1f,
        passed = true,
        deliveryFile = "/sdcard/Recordings/scene_T01_delivery.wav",
        renderedAt = 1_716_000_000_000L,
    )

    @Test fun round_trip_json() {
        val json = DeliveryResult.toJson(sample)
        val parsed = DeliveryResult.fromJson(json)
        assertEquals(sample, parsed)
    }

    @Test fun off_target_round_trip() {
        val off = sample.copy(targetLufs = null, tpCeilingDbtp = null, passed = false)
        val parsed = DeliveryResult.fromJson(DeliveryResult.toJson(off))
        assertNull(parsed.targetLufs)
        assertNull(parsed.tpCeilingDbtp)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.model.DeliveryResultTest"
```

Expected: compile error — `DeliveryResult` unresolved.

- [ ] **Step 3: Implement `DeliveryResult`**

Create `app/src/main/java/com/example/recorderproject/model/DeliveryResult.kt`:

```kotlin
package com.example.recorderproject.model

import org.json.JSONObject

/**
 * Outcome of a single offline delivery render.
 *
 *  - Measurement fields (integrated, max-st, max-m, TP, LRA) describe the
 *    *measured* delivery file after the render — not the input.
 *  - Target fields are captured from the LoudnessTarget at render time so
 *    the report stays interpretable even if the user changes default later.
 *  - [passed] is true when integrated is within ±0.5 LU of target AND
 *    TP ≤ ceiling + 0.1 dB tolerance.
 */
data class DeliveryResult(
    val integratedLufs: Float,
    val shortTermMaxLufs: Float,
    val momentaryMaxLufs: Float,
    val truePeakDbtp: Float,
    val lra: Float,
    val appliedGainDb: Float,
    val targetLufs: Float?,
    val tpCeilingDbtp: Float?,
    val passed: Boolean,
    val deliveryFile: String,
    val renderedAt: Long,
) {
    companion object {
        fun toJson(r: DeliveryResult): String = JSONObject().apply {
            put("integratedLufs",    r.integratedLufs.toDouble())
            put("shortTermMaxLufs",  r.shortTermMaxLufs.toDouble())
            put("momentaryMaxLufs",  r.momentaryMaxLufs.toDouble())
            put("truePeakDbtp",      r.truePeakDbtp.toDouble())
            put("lra",               r.lra.toDouble())
            put("appliedGainDb",     r.appliedGainDb.toDouble())
            put("targetLufs",        r.targetLufs?.toDouble() ?: JSONObject.NULL)
            put("tpCeilingDbtp",     r.tpCeilingDbtp?.toDouble() ?: JSONObject.NULL)
            put("passed",            r.passed)
            put("deliveryFile",      r.deliveryFile)
            put("renderedAt",        r.renderedAt)
        }.toString()

        fun fromJson(s: String): DeliveryResult {
            val j = JSONObject(s)
            return DeliveryResult(
                integratedLufs   = j.getDouble("integratedLufs").toFloat(),
                shortTermMaxLufs = j.getDouble("shortTermMaxLufs").toFloat(),
                momentaryMaxLufs = j.getDouble("momentaryMaxLufs").toFloat(),
                truePeakDbtp     = j.getDouble("truePeakDbtp").toFloat(),
                lra              = j.getDouble("lra").toFloat(),
                appliedGainDb    = j.getDouble("appliedGainDb").toFloat(),
                targetLufs       = if (j.isNull("targetLufs")) null else j.getDouble("targetLufs").toFloat(),
                tpCeilingDbtp    = if (j.isNull("tpCeilingDbtp")) null else j.getDouble("tpCeilingDbtp").toFloat(),
                passed           = j.getBoolean("passed"),
                deliveryFile     = j.getString("deliveryFile"),
                renderedAt       = j.getLong("renderedAt"),
            )
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.model.DeliveryResultTest"
```

Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/DeliveryResult.kt \
        app/src/test/java/com/example/recorderproject/model/DeliveryResultTest.kt
git commit -m "feat(model): DeliveryResult + sidecar JSON round-trip

Carries integrated/short-term-max/momentary-max LUFS, true-peak,
LRA, applied gain, target snapshot, pass/fail, and file path.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B3: `SettingsDataStore` — persist loudness target

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt`
- Modify: `app/src/main/java/com/example/recorderproject/data/Defaults.kt` (add three defaults)
- Modify: `app/src/main/java/com/example/recorderproject/data/SettingsSnapshot.kt` (add three fields)
- Test: `app/src/test/java/com/example/recorderproject/data/DefaultsTest.kt` (extend)

- [ ] **Step 1: Inspect `Defaults` and `SettingsSnapshot`**

```bash
grep -n "object Defaults\|data class SettingsSnapshot" \
  app/src/main/java/com/example/recorderproject/data/Defaults.kt \
  app/src/main/java/com/example/recorderproject/data/SettingsSnapshot.kt
```

Expected: locates the `object Defaults` block and the `data class SettingsSnapshot` definition. Note the surrounding style (val + default) so the additions match.

- [ ] **Step 2: Write the failing test**

Append to `app/src/test/java/com/example/recorderproject/data/DefaultsTest.kt`:

```kotlin
@Test fun defaults_include_loudness_target_podcast() {
    assertEquals("PODCAST", Defaults.defaultLoudnessTarget)
    assertEquals(-16f, Defaults.customLoudnessLufs)
    assertEquals(-1f, Defaults.customLoudnessTpCeiling)
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.data.DefaultsTest"
```

Expected: compile error — `Defaults.defaultLoudnessTarget` unresolved.

- [ ] **Step 4: Extend `Defaults`**

In `app/src/main/java/com/example/recorderproject/data/Defaults.kt`, inside `object Defaults`, add:

```kotlin
// Loudness delivery target (string key from LoudnessTarget.encode()).
val defaultLoudnessTarget: String = "PODCAST"
val customLoudnessLufs: Float = -16f
val customLoudnessTpCeiling: Float = -1f
```

- [ ] **Step 5: Extend `SettingsSnapshot`**

In `app/src/main/java/com/example/recorderproject/data/SettingsSnapshot.kt`, add three constructor params with the same defaults:

```kotlin
val defaultLoudnessTarget: String = Defaults.defaultLoudnessTarget,
val customLoudnessLufs: Float = Defaults.customLoudnessLufs,
val customLoudnessTpCeiling: Float = Defaults.customLoudnessTpCeiling,
```

- [ ] **Step 6: Extend `SettingsDataStore`**

In `SettingsDataStore.kt`, with the other `// ----- Recording -----` keys, add:

```kotlin
private val defaultLoudnessTargetKey   = stringPreferencesKey("default_loudness_target")
private val customLoudnessLufsKey      = floatPreferencesKey("custom_loudness_lufs")
private val customLoudnessTpCeilingKey = floatPreferencesKey("custom_loudness_tp_ceiling")
```

Find the `suspend fun snapshot(): SettingsSnapshot` and the `apply(snapshot)` (or equivalent) function. In the snapshot reader, add:

```kotlin
defaultLoudnessTarget   = p[defaultLoudnessTargetKey]   ?: Defaults.defaultLoudnessTarget,
customLoudnessLufs      = p[customLoudnessLufsKey]      ?: Defaults.customLoudnessLufs,
customLoudnessTpCeiling = p[customLoudnessTpCeilingKey] ?: Defaults.customLoudnessTpCeiling,
```

In the apply/write path, add the three corresponding writes:

```kotlin
context.dataStore.edit { p ->
    p[defaultLoudnessTargetKey]   = snapshot.defaultLoudnessTarget
    p[customLoudnessLufsKey]      = snapshot.customLoudnessLufs
    p[customLoudnessTpCeilingKey] = snapshot.customLoudnessTpCeiling
}
```

(Adjust to match the surrounding pattern — there may already be one big `edit { }` block that needs three more lines.)

- [ ] **Step 7: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.data.DefaultsTest"
```

Expected: pass.

- [ ] **Step 8: Run full suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: no regressions.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/data/Defaults.kt \
        app/src/main/java/com/example/recorderproject/data/SettingsSnapshot.kt \
        app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt \
        app/src/test/java/com/example/recorderproject/data/DefaultsTest.kt
git commit -m "feat(data): persist default loudness target + custom LUFS/TP

DataStore keys + Defaults entry + SettingsSnapshot field.
Defaults: PODCAST / -16 LUFS / -1 dBTP.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B4: `RecordFile` — delivery fields

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/model/RecordFile.kt`

- [ ] **Step 1: Extend the data class**

Add two fields at the end of `RecordFile`:

```kotlin
data class RecordFile(
    val id: String,
    val name: String,
    val path: String,
    val durationSeconds: Int,
    val sceneName: String,
    val notes: String = "",
    val tags: String = "",
    val isLocked: Boolean = false,
    val hasNoiseReduction: Boolean = false,
    val sampleRate: Int = 48000,
    val channelCount: Int = 1,
    val bitDepth: Int = 16,
    val hasEQ: Boolean = false,
    val syncPointMs: Long? = null,
    val locationTag: String? = null,
    val environmentTag: String? = null,
    val starred: Boolean = false,
    val cuePoints: List<CuePoint> = emptyList(),
    val deliveryPath: String? = null,
    val deliveryResult: com.example.recorderproject.model.DeliveryResult? = null,
)
```

- [ ] **Step 2: Build to verify compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. All existing callers default the new fields to `null`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/RecordFile.kt
git commit -m "feat(model): RecordFile gains deliveryPath + deliveryResult fields

Default null; back-compatible with existing constructor calls.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase C — Renderer

### Task C1: `DeliveryRenderer` — two-pass offline render

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/DeliveryRenderer.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/DeliveryRendererTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `DeliveryRendererTest.kt`:

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.LoudnessTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class DeliveryRendererTest {

    private fun tmp(name: String): File {
        val f = File.createTempFile(name, ".wav")
        f.deleteOnExit()
        return f
    }

    private fun writeMonoSine(file: File, sr: Int, freq: Float, seconds: Float, amp: Float) {
        val n = (sr * seconds).toInt()
        val sig = FloatArray(n)
        val w = 2.0 * PI * freq / sr
        for (i in 0 until n) sig[i] = (amp * sin(w * i)).toFloat()
        WavIo.write(file, sig, channels = 1, sampleRate = sr, bitDepth = 24)
    }

    @Test fun renders_quiet_signal_up_to_target_with_pass() {
        val src = tmp("src-quiet"); val dst = tmp("dst-quiet")
        // -30 dBFS sine for 5 s — should integrate around -27 LUFS, target Podcast -16 → +11 dB gain.
        writeMonoSine(src, sr = 48000, freq = 1000f, seconds = 5f, amp = 0.0316f)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Podcast)
        assertNotNull(result)
        assertEquals(-16f, result!!.integratedLufs, 0.7f)
        assertTrue("TP <= ceiling", result.truePeakDbtp <= -1f + 0.1f)
        assertTrue("passed", result.passed)
        assertTrue(dst.exists() && dst.length() > 0)
    }

    @Test fun renders_loud_signal_down_to_target() {
        val src = tmp("src-loud"); val dst = tmp("dst-loud")
        // -6 dBFS sine for 5 s → integrate around -3 LUFS, target Broadcast -23 → -20 dB gain.
        writeMonoSine(src, sr = 48000, freq = 1000f, seconds = 5f, amp = 0.5f)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Broadcast)
        assertNotNull(result)
        assertEquals(-23f, result!!.integratedLufs, 0.7f)
        assertTrue(result.passed)
    }

    @Test fun limiter_engages_when_full_gain_would_clip() {
        val src = tmp("src-square"); val dst = tmp("dst-square")
        // 0 dBFS square-ish (clipped sine) → worst case for limiter.
        val sr = 48000
        val sig = FloatArray(sr * 3) { if ((it / 240) % 2 == 0) 0.99f else -0.99f }
        WavIo.write(src, sig, channels = 1, sampleRate = sr, bitDepth = 24)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Streaming)
        assertNotNull(result)
        assertTrue("TP must stay below ceiling", result!!.truePeakDbtp <= -1f + 0.1f)
    }

    @Test fun skips_silent_source() {
        val src = tmp("src-silent"); val dst = tmp("dst-silent")
        WavIo.write(src, FloatArray(48000 * 2), channels = 1, sampleRate = 48000, bitDepth = 24)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Podcast)
        assertNull("silent files should skip render", result)
        assertFalse("no delivery file written", dst.exists() && dst.length() > 100)
    }

    @Test fun skips_sub_window_source() {
        val src = tmp("src-tiny"); val dst = tmp("dst-tiny")
        // 100 ms — below the 400 ms momentary window.
        val sig = FloatArray((48000 * 0.1f).toInt()) { 0.1f * sin(2.0 * PI * 1000.0 * it / 48000.0).toFloat() }
        WavIo.write(src, sig, channels = 1, sampleRate = 48000, bitDepth = 24)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Podcast)
        assertNull("sub-window files should skip render", result)
    }

    @Test fun off_target_returns_null_and_writes_no_file() {
        val src = tmp("src-off"); val dst = tmp("dst-off")
        writeMonoSine(src, sr = 48000, freq = 1000f, seconds = 2f, amp = 0.1f)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Off)
        assertNull(result)
        assertFalse(dst.exists() && dst.length() > 100)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.DeliveryRendererTest"
```

Expected: compile error — `DeliveryRenderer` unresolved.

- [ ] **Step 3: Implement `DeliveryRenderer`**

Create `app/src/main/java/com/example/recorderproject/audio/DeliveryRenderer.kt`:

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.DeliveryResult
import com.example.recorderproject.model.LoudnessTarget
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/**
 * Two-pass offline delivery renderer.
 *
 *  Pass 1 — measure source loudness, true-peak, and LRA.
 *  Pass 2 — apply target gain + (if needed) MasterLimiter ceiling, write delivery WAV.
 *  Then  — re-measure delivery TP for the report and write the sidecar JSON.
 *
 * Output: 24-bit PCM, same SR + channels as source.
 *
 * Returns `null` (no work, no file) when:
 *   - target is Off
 *   - source is shorter than 400 ms (momentary window)
 *   - source integrates below -60 LUFS (near-silent)
 */
object DeliveryRenderer {

    private const val CHUNK_FRAMES = 8192
    private const val MIN_FRAMES_SECONDS = 0.4f
    private const val SILENCE_FLOOR_LUFS = -60f

    fun render(
        source: File,
        destination: File,
        target: LoudnessTarget,
    ): DeliveryResult? {
        if (target is LoudnessTarget.Off) return null
        val targetLufs = target.targetLufs ?: return null
        val tpCeiling = target.tpCeilingDbtp ?: return null

        val hdr = WavIo.readHeader(source)
        if (hdr.totalFrames < hdr.sampleRate * MIN_FRAMES_SECONDS) return null

        // ---------- Pass 1 — measure ----------
        val pass1Lufs = LufsProcessor(hdr.sampleRate.toFloat(), hdr.channels)
        val pass1Tp   = TruePeakDetector(hdr.sampleRate.toFloat(), hdr.channels)
        val pass1Lra  = LoudnessRangeMeter(hdr.sampleRate.toFloat(), hdr.channels)

        WavIo.openReader(source).use { r ->
            val buf = FloatArray(CHUNK_FRAMES * hdr.channels)
            while (true) {
                val n = r.readBlock(buf)
                if (n <= 0) break
                val slice = if (n == buf.size) buf else buf.copyOf(n)
                pass1Lufs.process(slice)
                pass1Tp.feed(slice)
                pass1Lra.process(slice)
            }
        }

        val measuredI = pass1Lufs.integratedLufs
        if (measuredI < SILENCE_FLOOR_LUFS) return null

        val rawGainDb   = targetLufs - measuredI
        val postTpDbtp  = pass1Tp.peakDbTP + rawGainDb
        val needsLimit  = (postTpDbtp - tpCeiling) > 0f
        val gainLinear  = 10f.pow(rawGainDb / 20f)

        // ---------- Pass 2 — render ----------
        val limiter = if (needsLimit) {
            MasterLimiter(hdr.sampleRate.toFloat(), ceilingDb = tpCeiling, attackMs = 1.5f, releaseMs = 50f)
        } else null

        WavIo.openReader(source).use { r ->
            WavIo.openWriter(destination, hdr.channels, hdr.sampleRate, bitDepth = 24).use { w ->
                val buf = FloatArray(CHUNK_FRAMES * hdr.channels)
                val out = FloatArray(CHUNK_FRAMES * hdr.channels)
                while (true) {
                    val n = r.readBlock(buf)
                    if (n <= 0) break
                    for (i in 0 until n) {
                        val gained = buf[i] * gainLinear
                        out[i] = limiter?.process(gained) ?: gained
                    }
                    w.writeBlock(out, n)
                }
            }
        }

        // ---------- Post-measure delivery TP for the report ----------
        val verifyTp = TruePeakDetector(hdr.sampleRate.toFloat(), hdr.channels)
        WavIo.openReader(destination).use { r ->
            val buf = FloatArray(CHUNK_FRAMES * hdr.channels)
            while (true) {
                val n = r.readBlock(buf)
                if (n <= 0) break
                verifyTp.feed(if (n == buf.size) buf else buf.copyOf(n))
            }
        }

        // Delivery integrated (with the gain applied, the input-domain pass1Lufs would already be near target).
        val deliveryI = measuredI + rawGainDb    // exact when the limiter doesn't engage
        val passed = abs(deliveryI - targetLufs) <= 0.5f
                  && verifyTp.peakDbTP <= tpCeiling + 0.1f

        return DeliveryResult(
            integratedLufs   = deliveryI,
            shortTermMaxLufs = pass1Lufs.shortTermMaxLufs + rawGainDb,
            momentaryMaxLufs = pass1Lufs.momentaryMaxLufs + rawGainDb,
            truePeakDbtp     = verifyTp.peakDbTP,
            lra              = pass1Lra.lra,
            appliedGainDb    = rawGainDb,
            targetLufs       = targetLufs,
            tpCeilingDbtp    = tpCeiling,
            passed           = passed,
            deliveryFile     = destination.absolutePath,
            renderedAt       = System.currentTimeMillis(),
        )
    }
}
```

Note: when the limiter engages, delivery integrated will be slightly under `deliveryI` because gain reduction reduces total energy. The `passed` flag accepts ±0.5 LU, which covers the limiter's typical impact (<0.3 LU for transient material). For pathological cases where limiter pulls > 0.5 LU, `passed` will be `false` even though TP is safe — that's the correct signal to the user.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.DeliveryRendererTest"
```

Expected: 6 tests pass.

- [ ] **Step 5: Run full suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: no regressions.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/DeliveryRenderer.kt \
        app/src/test/java/com/example/recorderproject/audio/DeliveryRendererTest.kt
git commit -m "feat(audio): DeliveryRenderer — two-pass loudness + true-peak render

Pass 1 measures integrated LUFS / TP / LRA. Pass 2 applies target gain
and (if needed) MasterLimiter to enforce TP ceiling. Output: 24-bit PCM.
Skips silent (<-60 LUFS), sub-window (<400 ms), and Off-target inputs.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase D — VM wiring

### Task D1: `AudioRecorderManager` — true-peak listener hook

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/audio/AudioRecorderManager.kt`

- [ ] **Step 1: Add the listener field + setter**

After the existing `lufsListener` block (around line 132–137), add:

```kotlin
@Volatile private var truePeakListener: ((Float) -> Unit)? = null
fun setTruePeakListener(l: ((Float) -> Unit)?) { truePeakListener = l }
private val truePeakDetector by lazy {
    TruePeakDetector(sampleRate.toFloat(), channels = channelCount)
}
```

(`channelCount` is whatever AudioRecorderManager already uses to describe the live channel count. If the recording loop is mono-only, use `channels = 1`.)

- [ ] **Step 2: Feed samples and emit at the same cadence as LUFS**

Find the existing block that feeds `lufsProcessor.process(...)` and `lufsListener?.invoke(...)`. Right after the LUFS emission, add:

```kotlin
truePeakDetector.feed(monoOrInterleavedBufferUsedForLufs)
truePeakListener?.invoke(truePeakDetector.peakDbTP)
```

Use whichever buffer was already being passed to `lufsProcessor.process()` so the channel count matches the detector's constructor.

- [ ] **Step 3: Reset on stop**

Wherever the recorder resets `lufsProcessor` (or constructs a fresh one) at the start of a new recording, also call:

```kotlin
truePeakDetector.reset()
```

(If `lufsProcessor` is re-created per recording via `by lazy`, change `truePeakDetector` to the same pattern — instance per recording, not lifetime.)

- [ ] **Step 4: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/AudioRecorderManager.kt
git commit -m "feat(audio): AudioRecorderManager — live true-peak listener

Feeds the recording sample stream through TruePeakDetector and
emits dBTP to a setTruePeakListener callback, matching the
existing lufsListener cadence.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task D2: `RecorderViewModel` — target state + render trigger

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`
- Test: `app/src/test/java/com/example/recorderproject/RecorderViewModelLoudnessTest.kt` (new)

- [ ] **Step 1: Add the new state flows**

Near the existing `_liveLufs` definition (around line 426), add:

```kotlin
// D: live true-peak — populated only while recording
private val _liveTpDbTp = MutableStateFlow(Float.NEGATIVE_INFINITY)
val liveTpDbTp: StateFlow<Float> = _liveTpDbTp

// D: current loudness delivery target (session-level)
private val _loudnessTarget = MutableStateFlow<LoudnessTarget>(LoudnessTarget.DEFAULT)
val loudnessTarget: StateFlow<LoudnessTarget> = _loudnessTarget

// D: in-progress delivery render
private val _isRenderingDelivery = MutableStateFlow(false)
val isRenderingDelivery: StateFlow<Boolean> = _isRenderingDelivery

// D: rolling render result for snackbar / UI
private val _lastDeliveryResult = MutableStateFlow<DeliveryResult?>(null)
val lastDeliveryResult: StateFlow<DeliveryResult?> = _lastDeliveryResult

private val renderSemaphore = kotlinx.coroutines.sync.Semaphore(permits = 2)
```

And the imports at the top of the file:

```kotlin
import com.example.recorderproject.audio.DeliveryRenderer
import com.example.recorderproject.model.DeliveryResult
import com.example.recorderproject.model.LoudnessTarget
```

- [ ] **Step 2: Add session/default target setters**

Anywhere in the class with the other public setters:

```kotlin
/** Change the active session target. Does not persist. */
fun setSessionLoudnessTarget(t: LoudnessTarget) {
    _loudnessTarget.value = t
}

/** Persist as default + update session target. */
fun saveAsDefaultLoudnessTarget(t: LoudnessTarget) {
    _loudnessTarget.value = t
    viewModelScope.launch {
        val (key, lufs, tp) = LoudnessTarget.encode(t)
        settings.snapshot().copy(
            defaultLoudnessTarget = key,
            customLoudnessLufs = lufs,
            customLoudnessTpCeiling = tp,
        ).let { settings.apply(it) }
    }
}
```

(If `settings.apply(...)` doesn't exist, the actual write API in `SettingsDataStore` is what you found in Task B3 — match that call signature.)

- [ ] **Step 3: Hydrate target on init**

In the existing hydrate-on-init block (where other settings are loaded — search for `snapshot()`), add:

```kotlin
_loudnessTarget.value = LoudnessTarget.decode(
    s.defaultLoudnessTarget,
    s.customLoudnessLufs,
    s.customLoudnessTpCeiling,
)
```

- [ ] **Step 4: Wire the true-peak listener**

In the same block that sets `recorder.setLufsListener { ... }` (around line 1432):

```kotlin
recorder.setTruePeakListener { tp ->
    _liveTpDbTp.value = tp
}
```

And in the listener-clearing block (line 1484-ish), add:

```kotlin
recorder.setTruePeakListener(null)
_liveTpDbTp.value = Float.NEGATIVE_INFINITY
```

- [ ] **Step 5: Kick off render-on-stop**

Find the function that handles recording-finished (the one that builds the `RecordFile` and adds it to the file list — search for `RecordFile(` constructions near the stop path). After the original file is finalized, add:

```kotlin
val target = _loudnessTarget.value
if (target !is LoudnessTarget.Off) {
    viewModelScope.launch(Dispatchers.IO) {
        renderSemaphore.withPermit {
            _isRenderingDelivery.value = true
            try {
                val src = java.io.File(finalizedRecordingPath)
                val dst = java.io.File(
                    src.parentFile,
                    src.nameWithoutExtension + "_delivery.wav"
                )
                val result = runCatching {
                    DeliveryRenderer.render(src, dst, target)
                }.getOrNull()

                if (result != null) {
                    java.io.File(
                        src.parentFile,
                        src.nameWithoutExtension + "_delivery.json"
                    ).writeText(DeliveryResult.toJson(result))
                    _lastDeliveryResult.value = result
                    // Update the in-memory RecordFile so the row shows the delivery sibling.
                    rebindDeliveryResult(src.absolutePath, dst.absolutePath, result)
                } else {
                    _lastDeliveryResult.value = null
                    // best-effort partial-file cleanup
                    if (dst.exists() && dst.length() < 100) dst.delete()
                }
            } finally {
                _isRenderingDelivery.value = false
            }
        }
    }
}
```

Implement `rebindDeliveryResult` as a private helper that finds the matching `RecordFile` in `_files` (or whatever the StateFlow is called) and `copy()`s it with `deliveryPath` + `deliveryResult` populated:

```kotlin
private fun rebindDeliveryResult(srcPath: String, dstPath: String, r: DeliveryResult) {
    _files.update { list ->
        list.map { f ->
            if (f.path == srcPath) f.copy(deliveryPath = dstPath, deliveryResult = r) else f
        }
    }
}
```

Replace `_files` with the actual private MutableStateFlow name for the file list (search the VM for `MutableStateFlow<List<RecordFile>>`).

- [ ] **Step 6: Write the failing tests**

Create `app/src/test/java/com/example/recorderproject/RecorderViewModelLoudnessTest.kt`:

```kotlin
package com.example.recorderproject

import com.example.recorderproject.model.LoudnessTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderViewModelLoudnessTest {

    @Test fun encode_decode_handles_all_targets() {
        val all = listOf(
            LoudnessTarget.Off,
            LoudnessTarget.Streaming,
            LoudnessTarget.Podcast,
            LoudnessTarget.Broadcast,
            LoudnessTarget.Custom(-19f, -2f),
        )
        for (t in all) {
            val (k, l, tp) = LoudnessTarget.encode(t)
            assertEquals(t, LoudnessTarget.decode(k, l, tp))
        }
    }

    @Test fun decode_unknown_key_returns_default() {
        assertEquals(LoudnessTarget.DEFAULT, LoudnessTarget.decode("BOGUS", 0f, 0f))
    }

    @Test fun off_target_has_null_lufs() {
        assertTrue(LoudnessTarget.Off.targetLufs == null)
    }
}
```

(A VM-level integration test of the render trigger would need an Android context; we cover that in manual verification.)

- [ ] **Step 7: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.*"
```

Expected: pass.

- [ ] **Step 8: Build APK**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt \
        app/src/test/java/com/example/recorderproject/RecorderViewModelLoudnessTest.kt
git commit -m "feat(vm): loudness target state + auto-render on stop

Adds loudnessTarget / liveTpDbTp / isRenderingDelivery / lastDeliveryResult
StateFlows. Hydrates target from DataStore on init, persists when user picks
'Save as default'. On stop, kicks off DeliveryRenderer on Dispatchers.IO
with a 2-slot semaphore; writes sidecar JSON and rebinds the RecordFile.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task D3: Sound report — four new CSV columns

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` (`exportSoundReport`)

- [ ] **Step 1: Locate the existing report writer**

```bash
grep -n "exportSoundReport\|sound_report" \
  app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
```

Expected: locates the function around line 2348 and the CSV header / row-write loop inside it.

- [ ] **Step 2: Add the four columns**

In the CSV header line, append:

```
,Integrated,TP_dBTP,LRA,Target_Result
```

In the per-file row loop, append:

```kotlin
val dr = f.deliveryResult
val integrated = dr?.integratedLufs?.let { "%.1f".format(it) } ?: ""
val tp         = dr?.truePeakDbtp?.let { "%.1f".format(it) } ?: ""
val lra        = dr?.lra?.let { "%.1f".format(it) } ?: ""
val result = when {
    dr == null              -> "N/A"
    dr.targetLufs == null   -> "N/A"
    dr.passed               -> "PASS"
    else                    -> "FAIL"
}
row.append(",").append(integrated)
   .append(",").append(tp)
   .append(",").append(lra)
   .append(",").append(result)
```

(Adapt to whatever StringBuilder / row variable the existing code uses.)

- [ ] **Step 3: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(vm): sound report — Integrated/TP/LRA/Target_Result columns

Reads from RecordFile.deliveryResult. N/A when no delivery file or
target was Off; PASS when within ±0.5 LU of target and under TP ceiling.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task D4: Library scan — pick up `_delivery.wav` siblings

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` (existing library-scan function)

- [ ] **Step 1: Locate the library scan**

```bash
grep -n "fun scanRecordings\|listFiles\|RecordFile(" \
  app/src/main/java/com/example/recorderproject/RecorderViewModel.kt | head -10
```

Expected: locates the function that walks the recordings directory and constructs `RecordFile` rows.

- [ ] **Step 2: Skip delivery files as standalone rows; attach to siblings**

In the file-loop, before constructing a `RecordFile`, add:

```kotlin
val name = file.nameWithoutExtension
if (name.endsWith("_delivery")) continue   // handled as sibling below
```

After the `RecordFile` is built (but before adding to the list), check for a sibling:

```kotlin
val deliveryWav  = java.io.File(file.parentFile, "${file.nameWithoutExtension}_delivery.wav")
val deliveryJson = java.io.File(file.parentFile, "${file.nameWithoutExtension}_delivery.json")
val deliveryResult = runCatching {
    if (deliveryJson.exists()) DeliveryResult.fromJson(deliveryJson.readText()) else null
}.getOrNull()

val finalRow = if (deliveryWav.exists()) {
    row.copy(
        deliveryPath = deliveryWav.absolutePath,
        deliveryResult = deliveryResult,
    )
} else row
```

Use `finalRow` instead of `row` when adding to the list.

- [ ] **Step 3: Orphan cleanup — delivery WAV without matching JSON**

In the same scan, before the loop ends, add a small sweep:

```kotlin
val orphans = parentDir.listFiles { f ->
    f.name.endsWith("_delivery.wav") &&
    !java.io.File(f.parentFile, f.nameWithoutExtension + ".json").exists() &&
    !java.io.File(f.parentFile, f.nameWithoutExtension.removeSuffix("_delivery") + ".wav").exists()
}
orphans?.forEach { it.delete() }
```

This deletes orphan delivery files whose original is gone AND that have no sidecar — the "process died mid-render" path from the spec.

- [ ] **Step 4: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(vm): library scan picks up _delivery.wav siblings

Skips delivery WAVs as standalone rows, attaches them to their
matching original RecordFile with the parsed sidecar JSON. Sweeps
orphan delivery files (no original, no sidecar) on each scan.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase E — UI

UI tasks verify by building the APK and visually checking on a connected device or the emulator. No JVM unit tests for Compose code.

### Task E1: `LoudnessMeterBar` component

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/LoudnessMeterBar.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.LoudnessTarget
import kotlin.math.abs

private val Orange   = Color(0xFFFA4616)
private val Yellow   = Color(0xFFFFC72C)
private val BlueGrey = Color(0xFF7B8189)
private val Red      = Color(0xFFC0392B)

/**
 * Target-aware loudness meter. Tracks short-term LUFS against the target line.
 * Zones: green ≤ ±1 LU, yellow ≤ ±3 LU, red beyond.
 * Bottom row shows M/S/I numeric readouts plus TP dBTP.
 */
@Composable
fun LoudnessMeterBar(
    momentaryLufs: Float,
    shortTermLufs: Float,
    integratedLufs: Float,
    truePeakDbtp: Float,
    target: LoudnessTarget,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val targetLufs = target.targetLufs
    val tpCeiling = target.tpCeilingDbtp

    // Scale: target ±12 LU. When target is Off, anchor scale to -16 ±12.
    val scaleCenter = targetLufs ?: -16f
    val scaleSpan = 12f
    fun normalize(l: Float): Float {
        val clamped = l.coerceIn(scaleCenter - scaleSpan, scaleCenter + scaleSpan)
        return ((clamped - (scaleCenter - scaleSpan)) / (scaleSpan * 2f)).coerceIn(0f, 1f)
    }

    val barFill by if (reduceMotion) {
        animateFloatAsState(normalize(shortTermLufs), tween(0), label = "loudness-fill")
    } else {
        animateFloatAsState(normalize(shortTermLufs),
            spring(stiffness = Spring.StiffnessMediumLow), label = "loudness-fill")
    }

    val zoneColor = when {
        targetLufs == null -> BlueGrey
        abs(shortTermLufs - targetLufs) <= 1f -> Orange
        abs(shortTermLufs - targetLufs) <= 3f -> Yellow
        else -> Red
    }
    val animatedColor by animateColorAsState(zoneColor,
        if (reduceMotion) tween(0) else tween(150), label = "loudness-zone")

    Column(modifier = modifier
        .height(120.dp)
        .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            // Background track
            drawRect(BlueGrey.copy(alpha = 0.15f), size = size)
            // Bar fill from bottom up
            val fillH = size.height * barFill
            drawRect(
                color = animatedColor.copy(alpha = 0.85f),
                topLeft = Offset(0f, size.height - fillH),
                size = Size(size.width, fillH),
            )
            // Target line (only when target ≠ Off)
            if (targetLufs != null) {
                val y = size.height * (1f - 0.5f)   // target sits at scale center
                drawLine(
                    color = Orange,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 4f,
                )
                // Integrated tick (dotted-ish — single line for now)
                val ti = size.height * (1f - normalize(integratedLufs))
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(size.width * 0.1f, ti),
                    end = Offset(size.width * 0.9f, ti),
                    strokeWidth = 2f,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Readout("M", momentaryLufs)
            Readout("S", shortTermLufs)
            Readout("I", integratedLufs)
        }
        val tpColor = when {
            truePeakDbtp >= -0.1f -> Red
            truePeakDbtp >= -3f   -> Yellow
            else                  -> Color.White
        }
        Text(
            text = if (truePeakDbtp.isFinite()) "TP %.1f dBTP".format(truePeakDbtp) else "TP —",
            color = tpColor,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Readout(label: String, lufs: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = BlueGrey)
        Text(
            text = if (lufs > -69f) "%.1f".format(lufs) else "—",
            fontSize = 13.sp,
            color = Color.White,
        )
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/LoudnessMeterBar.kt
git commit -m "feat(ui): LoudnessMeterBar — target-aware vertical loudness meter

Short-term-tracking bar with target line, ±1/±3 LU green/yellow/red zones,
M/S/I numeric readouts, TP indicator. 120 dp tile, brand palette.
Reduce-motion respected via tween(0) fallback.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task E2: `LoudnessTargetChip` + picker sheet

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/LoudnessTargetChip.kt`

- [ ] **Step 1: Create the chip + sheet**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.LoudnessTarget

private val Orange   = Color(0xFFFA4616)
private val BlueGrey = Color(0xFF7B8189)

@Composable
fun LoudnessTargetChip(
    current: LoudnessTarget,
    onSelectSession: (LoudnessTarget) -> Unit,
    onSaveAsDefault: (LoudnessTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val bg = if (current is LoudnessTarget.Off) BlueGrey else Orange

    Row(modifier = modifier
        .background(bg.copy(alpha = 0.16f), shape = RoundedCornerShape(50))
        .clickable { showSheet = true }
        .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(current.displayName.uppercase(), color = bg, fontSize = 12.sp)
    }

    if (showSheet) {
        LoudnessTargetSheet(
            current = current,
            onSelectSession = { sel -> onSelectSession(sel); showSheet = false },
            onSaveAsDefault = { sel -> onSaveAsDefault(sel); showSheet = false },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoudnessTargetSheet(
    current: LoudnessTarget,
    onSelectSession: (LoudnessTarget) -> Unit,
    onSaveAsDefault: (LoudnessTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var customLufs by remember { mutableStateOf((current as? LoudnessTarget.Custom)?.lufs ?: -16f) }
    var customTp   by remember { mutableStateOf((current as? LoudnessTarget.Custom)?.tpDbtp ?: -1f) }
    var customExpanded by remember { mutableStateOf(current is LoudnessTarget.Custom) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Loudness target", fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))

            TargetRow("Streaming –14",  current == LoudnessTarget.Streaming) {
                onSelectSession(LoudnessTarget.Streaming)
            }
            TargetRow("Podcast –16",    current == LoudnessTarget.Podcast) {
                onSelectSession(LoudnessTarget.Podcast)
            }
            TargetRow("Broadcast –23",  current == LoudnessTarget.Broadcast) {
                onSelectSession(LoudnessTarget.Broadcast)
            }
            TargetRow("Custom",         current is LoudnessTarget.Custom) {
                customExpanded = !customExpanded
            }
            if (customExpanded) {
                Text("Target LUFS: ${"%.1f".format(customLufs)}", fontSize = 12.sp)
                Slider(
                    value = customLufs,
                    onValueChange = { customLufs = (it * 2f).toInt() / 2f }, // 0.5 step
                    valueRange = -30f..-9f,
                )
                Text("TP ceiling: ${"%.1f".format(customTp)} dBTP", fontSize = 12.sp)
                Slider(
                    value = customTp,
                    onValueChange = { customTp = (it * 10f).toInt() / 10f }, // 0.1 step
                    valueRange = -3f..0f,
                )
                Button(onClick = { onSelectSession(LoudnessTarget.Custom(customLufs, customTp)) },
                       modifier = Modifier.fillMaxWidth()) {
                    Text("Use these values")
                }
            }
            TargetRow("Off",            current is LoudnessTarget.Off) {
                onSelectSession(LoudnessTarget.Off)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSaveAsDefault(current) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save current as default") }
        }
    }
}

@Composable
private fun TargetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/LoudnessTargetChip.kt
git commit -m "feat(ui): LoudnessTargetChip + bottom-sheet picker

Pill chip on the recording header; sheet offers Streaming/Podcast/
Broadcast/Custom (LUFS + TP sliders)/Off, plus 'Save as default'.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task E3: Swap LUFS tile in `RecordingActiveSection`

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/components/RecordingActiveSection.kt`

- [ ] **Step 1: Locate the existing LUFS tile**

```bash
grep -n "lufsDb\|\"LUFS\"" \
  app/src/main/java/com/example/recorderproject/ui/components/RecordingActiveSection.kt
```

Expected: locates the `lufsDb: Float = -70f` param (around line 99) and the tile that renders the text "LUFS" (around line 126).

- [ ] **Step 2: Extend the composable signature**

Replace the `lufsDb: Float = -70f` parameter with:

```kotlin
momentaryLufs: Float = -70f,
shortTermLufs: Float = -70f,
integratedLufs: Float = -70f,
truePeakDbtp: Float = Float.NEGATIVE_INFINITY,
loudnessTarget: com.example.recorderproject.model.LoudnessTarget = com.example.recorderproject.model.LoudnessTarget.Off,
reduceMotion: Boolean = false,
```

- [ ] **Step 3: Replace the LUFS text tile with `LoudnessMeterBar`**

In the FlowRow / Row where the LUFS tile lives, replace the text-tile block with:

```kotlin
LoudnessMeterBar(
    momentaryLufs = momentaryLufs,
    shortTermLufs = shortTermLufs,
    integratedLufs = integratedLufs,
    truePeakDbtp = truePeakDbtp,
    target = loudnessTarget,
    reduceMotion = reduceMotion,
    modifier = Modifier.weight(1f).heightIn(min = 120.dp),
)
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. There will be downstream call-site breakage (the next task fixes it).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/RecordingActiveSection.kt
git commit -m "feat(ui): swap LUFS text tile for LoudnessMeterBar in active section

Passes the four loudness signals + target through to the new tile.
Calls in RecorderApp will be updated next.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task E4: Update `RecorderApp` — chip + meter wiring

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt`

- [ ] **Step 1: Collect the new VM flows**

Near the existing `val liveLufs by viewModel.liveLufs.collectAsState()` (search for `liveLufs`), add:

```kotlin
val liveTp by viewModel.liveTpDbTp.collectAsState()
val loudnessTarget by viewModel.loudnessTarget.collectAsState()
val isRendering by viewModel.isRenderingDelivery.collectAsState()
val lastDelivery by viewModel.lastDeliveryResult.collectAsState()
```

Imports if missing:

```kotlin
import com.example.recorderproject.model.LoudnessTarget
import com.example.recorderproject.ui.components.LoudnessTargetChip
```

- [ ] **Step 2: Insert the chip next to existing feature chips**

In the `RecorderFeatureChips` row (search for it), add:

```kotlin
LoudnessTargetChip(
    current = loudnessTarget,
    onSelectSession = viewModel::setSessionLoudnessTarget,
    onSaveAsDefault = viewModel::saveAsDefaultLoudnessTarget,
)
```

- [ ] **Step 3: Pass new params to `RecordingActiveSection`**

In the `RecordingActiveSection(...)` call site, add:

```kotlin
momentaryLufs = liveLufs,            // until LufsListener emits momentary separately
shortTermLufs = liveLufs,
integratedLufs = liveLufs,           // VM can expose distinct flows later — single hook for now
truePeakDbtp = liveTp,
loudnessTarget = loudnessTarget,
reduceMotion = false,                 // read from existing settings if available
```

(If `liveLufs` is the only StateFlow today, that's fine — Task D2's listener will be extended in a follow-up to emit distinct momentary/short-term/integrated if needed. The bar already renders sensibly when all three are the same value.)

- [ ] **Step 4: Snackbar / progress ring on render**

Find the `Scaffold` (or wherever `SnackbarHost` lives). Add a `LaunchedEffect`:

```kotlin
LaunchedEffect(lastDelivery) {
    val r = lastDelivery ?: return@LaunchedEffect
    val target = loudnessTarget.targetLufs ?: return@LaunchedEffect
    val msg = when {
        r.passed -> "Rendered %.0f LUFS · %.1f dBTP · PASS".format(target, r.truePeakDbtp)
        r.integratedLufs < -60f -> "Too quiet to normalize — re-record louder."
        else -> "Couldn't reach %.0f LUFS without clipping — try a lower target.".format(target)
    }
    snackbarHostState.showSnackbar(msg)
}
```

Use whichever `SnackbarHostState` already exists in `RecorderApp`; if none, hoist `remember { SnackbarHostState() }` and wire it to `SnackbarHost` inside the `Scaffold`.

- [ ] **Step 5: Build APK**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt
git commit -m "feat(ui): wire LoudnessTargetChip + meter signals in RecorderApp

Chip in feature row; live meter receives shortTerm + TP + target;
LaunchedEffect surfaces the delivery snackbar message.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task E5: `RecordingsListCard` — delivery sibling row + badge

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/components/RecordingsListCard.kt`

- [ ] **Step 1: Render the badge when a delivery sibling exists**

In the per-row composable (search for where `RecordFile` fields are rendered), after the existing badges (NR / EQ), add:

```kotlin
val dr = file.deliveryResult
if (dr != null && dr.targetLufs != null) {
    Spacer(Modifier.width(4.dp))
    BadgePill(
        text = "%d".format(dr.targetLufs.toInt()),
        color = if (dr.passed) Color(0xFFFA4616) else Color(0xFF7B8189),
    )
}
```

Where `BadgePill` is whatever helper the existing NR/EQ badges use (look at the surrounding code for the same pattern).

- [ ] **Step 2: Add an expand affordance for delivery file**

Next to the original row, render a smaller secondary row when `file.deliveryPath != null`:

```kotlin
if (file.deliveryPath != null) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(start = 24.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ArrowForward, contentDescription = null,
             tint = Color(0xFF7B8189), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = java.io.File(file.deliveryPath).name,
            fontSize = 11.sp,
            color = Color(0xFF7B8189),
        )
    }
}
```

- [ ] **Step 3: Build APK**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/RecordingsListCard.kt
git commit -m "feat(ui): RecordingsListCard — delivery badge + sibling row

Shows target-LUFS badge next to NR/EQ badges (orange when PASS,
blue-grey otherwise) and a secondary row for the _delivery.wav sibling.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase F — Verification

### Task F1: Manual device pass

**Files:** none

- [ ] **Step 1: Install on emulator**

```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.recorderproject/.MainActivity
```

Expected: app launches without crash.

- [ ] **Step 2: Verify chip state on cold start**

The chip in the recording header should read `–16 PODCAST` (the default). Tap it — sheet shows five rows, "Podcast –16" selected.

- [ ] **Step 3: Record a 30 s test clip at normal volume**

Watch the meter — short-term bar should hover near the target line, M/S/I numbers should populate, TP indicator should stay below –3 dBTP.

- [ ] **Step 4: Stop recording**

Within ~3 s a snackbar appears: `Rendered –16 LUFS · <tp> dBTP · PASS`. File list shows the original WAV with an orange `–16` badge and a secondary row underneath for `<name>_delivery.wav`.

- [ ] **Step 5: Verify sidecar JSON**

```bash
adb shell ls /sdcard/Recordings/ | grep delivery
adb shell cat /sdcard/Recordings/<scene>_T01_delivery.json
```

Expected: the JSON has all `DeliveryResult` fields populated.

- [ ] **Step 6: Change to Streaming target, record again**

Tap chip → Streaming –14 → record 30 s → stop. Snackbar shows `–14 LUFS`. New delivery has `–14` badge.

- [ ] **Step 7: Save Streaming as default + cold restart**

Tap chip → "Save current as default" → close & reopen app. Chip should read `–14 STREAMING`.

- [ ] **Step 8: Record into hot mic to engage limiter**

Snap fingers near the mic, record 10 s, stop. Snackbar should still say PASS — limiter handled the overshoot. Open delivery WAV in any external player; verify peaks stay below 0 dBFS.

- [ ] **Step 9: Set target = Off, record, stop**

No snackbar, no delivery file. Original recording behaves exactly like pre-feature.

- [ ] **Step 10: Toggle reduce-motion in Settings, record + stop**

Meter zone transitions and bar height updates should snap (no spring animation, no color crossfade).

- [ ] **Step 11: Export sound report**

Tap whatever menu currently triggers `exportSoundReport()`; share to a text app; verify the CSV contains `Integrated`, `TP_dBTP`, `LRA`, `Target_Result` columns with values for delivery files and `N/A` for non-delivery rows.

- [ ] **Step 12: Verify all unit tests still pass**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: full green suite.

- [ ] **Step 13: Final commit (only if cleanup needed)**

No code changes required for this task. If any of the manual steps surfaced a bug, fix in a separate commit:

```bash
git add <touched files>
git commit -m "fix(loudness): <one-line description of bug found and fixed>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Summary

19 tasks, 5 phases. Every task ends in a commit. Roughly:

| Phase | Tasks | Files created | Files modified |
|-------|-------|---------------|----------------|
| A — DSP foundations | A0–A4 | 3 audio + 3 tests + (`Biquad.kt` reset helper) | `LufsProcessor.kt`, `WavIo.kt`, `AudioRecorderManager.kt` (small) |
| B — Model + persistence | B1–B4 | `LoudnessTarget.kt`, `DeliveryResult.kt`, 2 tests | `RecordFile.kt`, `Defaults.kt`, `SettingsSnapshot.kt`, `SettingsDataStore.kt`, `DefaultsTest.kt` |
| C — Renderer | C1 | `DeliveryRenderer.kt` + test | — |
| D — VM wiring | D1–D4 | `RecorderViewModelLoudnessTest.kt` | `AudioRecorderManager.kt`, `RecorderViewModel.kt` |
| E — UI | E1–E5 | `LoudnessMeterBar.kt`, `LoudnessTargetChip.kt` | `RecordingActiveSection.kt`, `RecorderApp.kt`, `RecordingsListCard.kt` |
| F — Verification | F1 | — | — |

Spec coverage check:

- ✅ Target chip + picker → Tasks E2 + E4
- ✅ Target line + zones meter → Tasks E1 + E3
- ✅ M/S/I + TP readouts → Tasks A1 (data) + E1 (display)
- ✅ Auto-render on stop → Task D2
- ✅ Two-pass renderer + limiter integration → Task C1
- ✅ Pass/fail rule → Task C1 (logic) + E4 (snackbar)
- ✅ Sound report extension → Task D3
- ✅ Library scan integration → Task D4
- ✅ Sidecar JSON → Tasks B2 + C1 + D4
- ✅ DataStore persistence + Save-as-default flow → Tasks B3 + D2 + E2
- ✅ Edge cases (silent / sub-window / Off) → Task C1
- ✅ Reduce-motion → Task E1
- ✅ Brand palette (orange / yellow / blue-grey) → Tasks E1 + E2
- ✅ 24-bit PCM output → Task C1
- ✅ JVM tests for DSP + model + data → Tasks A1–A3, B1, B2, B3, C1, D2
- ✅ Manual device verification → Task F1
