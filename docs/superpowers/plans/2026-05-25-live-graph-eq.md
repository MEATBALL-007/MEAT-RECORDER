# Live Graph EQ — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an interactive 8-band parametric EQ for Android Compose with a live frequency-response curve (2D + 3D spectrogram modes), three noise-cut interaction modes, ten plugin-emulation presets, and a user-selectable save mode that produces `_eq.wav` sidecars (or replaces the original).

**Architecture:** Pure-Kotlin offline render. RBJ biquad cookbook math in `audio/`. Compose Canvas curve view. State held in `RecorderViewModel`. Sidecar JSON for chain persistence (`org.json`, no new deps). Phase 1 of the 7-phase roadmap at `docs/superpowers/specs/2026-05-25-recorder-roadmap.md`.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2024.02.00), Material 3, AndroidX DataStore Preferences, kotlinx-coroutines, JUnit 4. Project root `/Users/meatball_mac/RECORDER_PROJECT/`. minSdk 24, compileSdk/targetSdk 34, AGP 8.13.2, Gradle 9.3.1, JVM 17.

**Spec:** `/Users/meatball_mac/RECORDER_PROJECT/docs/superpowers/specs/2026-05-25-live-graph-eq-design.md`

---

## Pre-task setup

Run these once before starting Task 1.

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
# Initialize git if not yet a repo (the eviction-recovered tree isn't a git repo)
if [ ! -d .git ]; then
  git init
  printf "build/\n.gradle/\n.idea/\nlocal.properties\n.superpowers/\n*.iml\n.DS_Store\n" > .gitignore
  git add -A
  git commit -m "chore: initial commit of compile-passing recovery build"
fi

# Delete the boilerplate example tests so they don't pollute results
rm -f app/src/test/java/com/example/recorderproject/ExampleUnitTest.kt
rm -f app/src/androidTest/java/com/example/recorderproject/ExampleInstrumentedTest.kt

# Verify build still passes
./gradlew assembleDebug
# Expected: BUILD SUCCESSFUL, APK at app/build/outputs/apk/debug/app-debug.apk

git add -A && git commit -m "chore: remove boilerplate example tests"
```

After this point every task ends with a git commit.

---

## Task 1: Brand colors

**Goal:** Add the orange / yellow / blue-grey palette to `Color.kt` so subsequent UI tasks can reference them.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/theme/Color.kt`

- [ ] **Step 1: Add the three brand colors plus charcoal surfaces**

Open `app/src/main/java/com/example/recorderproject/ui/theme/Color.kt` and replace the placeholder Meat* lines (19–21) with the real palette while keeping the existing MEATrec legacy and surface colors.

```kotlin
package com.example.recorderproject.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Legacy palette from the recovered Color.kt (mislabeled as Kmutt; project is MEATrec).
// Keep variable names for now to avoid breaking other recovered files; rename in Phase 6 cleanup.
val LegacyMaroon = Color(0xFFA31F34)
val LegacyGold = Color(0xFFD4AF37)

// Surfaces
val SurfaceDark = Color(0xFF121212)
val SurfaceVariantDark = Color(0xFF1F1F1F)
val OnSurfaceDark = Color(0xFFE6E6E6)
val OnSurfaceVariantDark = Color(0xFFB3B3B3)

// Recorder brand palette (Phase 1 spec 2026-05-25)
val RecorderOrange = Color(0xFFFA4616)   // primary accent — response curve, primary buttons
val RecorderYellow = Color(0xFFFFC72C)   // secondary accent — band handles, active state
val RecorderBlueGrey = Color(0xFF7B8189) // neutral — grid, dividers, spectrum hills, inactive UI
val RecorderCharcoal = Color(0xFF0C0C10) // canvas background
val RecorderCharcoalCard = Color(0xFF161618) // card / panel background

// Legacy aliases (kept so existing references still compile)
val MeatYellow = RecorderYellow
val MeatOrange = RecorderOrange
val MeatRed = Color(0xFFC0392B)
val TextPrimary = OnSurfaceDark
val TextSecondary = OnSurfaceVariantDark

// Compatibility aliases — the original recovered Color.kt exported these names.
// Will be removed in Phase 6 cleanup; for now they alias to the new MEATrec palette.
val KmuttMaroon = LegacyMaroon
val KmuttGold = LegacyGold

@Immutable
data class AppColors(
    val primary: Color = RecorderOrange,
    val secondary: Color = RecorderYellow,
    val accentYellow: Color = RecorderYellow,
    val accentOrange: Color = RecorderOrange,
    val accentRed: Color = MeatRed,
    val surface: Color = RecorderCharcoal,
    val surfaceVariant: Color = RecorderCharcoalCard,
    val onSurface: Color = OnSurfaceDark,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary
)

val LocalAppColors = compositionLocalOf { AppColors() }
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/theme/Color.kt
git commit -m "feat(theme): add Recorder brand palette (orange/yellow/blue-grey)"
```

---

## Task 2: Model enums

**Goal:** All EQ-related enums in one file. Cheap, no logic.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/EQEnums.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.recorderproject.model

enum class ApplySaveMode { BOTH, EQ_ONLY, ORIGINAL_ONLY }
enum class EQEditMode { PARAMETRIC, NOISE_CUT }
enum class EQViewMode { TWO_D, THREE_D }
enum class PresetCategory { VOCAL, DRUM, MASTER, REPAIR, VINTAGE, NEUTRAL }
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/model/EQEnums.kt
git commit -m "feat(model): add EQ-related enums"
```

---

## Task 3: EQBand + EQChain data classes

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/EQBand.kt`
- Create: `app/src/main/java/com/example/recorderproject/model/EQChain.kt`
- Test: `app/src/test/java/com/example/recorderproject/model/EQChainTest.kt`

- [ ] **Step 1: Write `EQBand.kt`**

```kotlin
package com.example.recorderproject.model

/**
 * A single band in an EQ chain.
 *
 * @param id stable identifier 1..8 used as UI key
 * @param frequencyHz center frequency in Hz, range 20f..20000f (log-mapped in UI)
 * @param gainDb gain in dB, range -24f..+24f; ignored for PASS/CUT/NOTCH/BAND_PASS
 * @param q resonance / bandwidth, range 0.1f..18f
 */
data class EQBand(
    val id: Int,
    val type: EQBandType,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float,
    val enabled: Boolean = true,
    val soloed: Boolean = false,
    val muted: Boolean = false,
    val locked: Boolean = false,
) {
    companion object {
        const val MAX_BANDS = 8
        const val MIN_FREQ_HZ = 20f
        const val MAX_FREQ_HZ = 20000f
        const val MIN_GAIN_DB = -24f
        const val MAX_GAIN_DB = 24f
        const val MIN_Q = 0.1f
        const val MAX_Q = 18f

        /** Default log-spaced bell at the given slot, all disabled. */
        fun defaultForSlot(id: Int): EQBand {
            // 8 bands log-spaced 60 Hz .. 12 kHz
            val freqs = floatArrayOf(60f, 150f, 320f, 700f, 1500f, 3200f, 7000f, 12000f)
            val f = freqs.getOrElse(id - 1) { 1000f }
            return EQBand(
                id = id,
                type = EQBandType.BELL,
                frequencyHz = f,
                gainDb = 0f,
                q = 1.0f,
                enabled = false,
            )
        }
    }
}
```

- [ ] **Step 2: Write `EQChain.kt`**

```kotlin
package com.example.recorderproject.model

data class EQChain(
    val bands: List<EQBand>,
    val noiseCutSuggestions: List<EQBand> = emptyList(),
    val bypassed: Boolean = false,
    val gainCompensation: Boolean = false,
) {
    companion object {
        /** A chain of 8 disabled default bands — Flat starting state. */
        fun empty(): EQChain = EQChain(
            bands = (1..EQBand.MAX_BANDS).map { EQBand.defaultForSlot(it) }
        )
    }

    /** Replace a band by id, returns new chain. */
    fun withBand(updated: EQBand): EQChain =
        copy(bands = bands.map { if (it.id == updated.id) updated else it })

    /** Add a band, capped at MAX_BANDS. Returns null if full. */
    fun withAddedBand(band: EQBand): EQChain? {
        if (bands.count { it.enabled } >= EQBand.MAX_BANDS) return null
        // Replace first disabled slot if any, else replace lowest-priority disabled
        val firstDisabledIdx = bands.indexOfFirst { !it.enabled }
        if (firstDisabledIdx < 0) return null
        val rebuilt = bands.toMutableList()
        rebuilt[firstDisabledIdx] = band.copy(id = bands[firstDisabledIdx].id, enabled = true)
        return copy(bands = rebuilt)
    }
}
```

- [ ] **Step 3: Write `EQChainTest.kt`**

```kotlin
package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EQChainTest {

    @Test fun `empty chain has 8 disabled default bands`() {
        val chain = EQChain.empty()
        assertEquals(8, chain.bands.size)
        assertTrue(chain.bands.all { !it.enabled })
        assertEquals((1..8).toList(), chain.bands.map { it.id })
    }

    @Test fun `withBand replaces matching id`() {
        val chain = EQChain.empty()
        val updated = chain.bands[2].copy(gainDb = 6f, enabled = true)
        val newChain = chain.withBand(updated)
        assertEquals(6f, newChain.bands[2].gainDb, 0.001f)
        assertTrue(newChain.bands[2].enabled)
        // Other bands unchanged
        assertFalse(newChain.bands[0].enabled)
    }

    @Test fun `withAddedBand fills first disabled slot`() {
        val chain = EQChain.empty()
        val newBand = EQBand(id = 99, type = EQBandType.BELL, frequencyHz = 440f, gainDb = 3f, q = 1.4f, enabled = true)
        val result = chain.withAddedBand(newBand)
        assertNotNull(result)
        assertTrue(result!!.bands[0].enabled)
        assertEquals(440f, result.bands[0].frequencyHz, 0.001f)
        assertEquals(1, result.bands[0].id) // id reassigned to slot
    }

    @Test fun `withAddedBand returns null when 8 enabled`() {
        var chain = EQChain.empty()
        for (i in 1..8) {
            chain = chain.withBand(chain.bands[i - 1].copy(enabled = true))
        }
        val newBand = EQBand(id = 0, type = EQBandType.BELL, frequencyHz = 1000f, gainDb = 0f, q = 1f)
        assertNull(chain.withAddedBand(newBand))
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.model.EQChainTest"
```
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/EQBand.kt \
        app/src/main/java/com/example/recorderproject/model/EQChain.kt \
        app/src/test/java/com/example/recorderproject/model/EQChainTest.kt
git commit -m "feat(model): add EQBand + EQChain data classes with tests"
```

---

## Task 4: Biquad direct-form II transposed (core)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/Biquad.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/BiquadTest.kt`

- [ ] **Step 1: Write the failing test first**

```kotlin
package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BiquadTest {

    /** Identity coefficients (b0=1, all others 0) should pass samples through unchanged. */
    @Test fun `identity biquad is a no-op`() {
        val b = Biquad(b0 = 1.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0)
        for (x in listOf(0.1, -0.5, 0.999, -0.999, 0.0)) {
            assertEquals(x, b.process(x), 1e-12)
        }
    }

    /** A unit-delay biquad (b1=1) outputs the previous input. */
    @Test fun `unit delay biquad delays by one sample`() {
        val b = Biquad(b0 = 0.0, b1 = 1.0, b2 = 0.0, a1 = 0.0, a2 = 0.0)
        assertEquals(0.0, b.process(0.7), 1e-12)
        assertEquals(0.7, b.process(0.0), 1e-12)
        assertEquals(0.0, b.process(0.3), 1e-12)
        assertEquals(0.3, b.process(0.0), 1e-12)
    }

    @Test fun `reset clears state`() {
        val b = Biquad(b0 = 0.0, b1 = 1.0, b2 = 0.0, a1 = 0.0, a2 = 0.0)
        b.process(0.5)
        b.reset()
        // After reset, next sample sees no delay-line history
        assertEquals(0.0, b.process(0.0), 1e-12)
    }
}
```

- [ ] **Step 2: Run, verify FAIL with "unresolved reference: Biquad"**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.BiquadTest"
```
Expected: compile failure, Biquad doesn't exist yet.

- [ ] **Step 3: Implement Biquad minimally**

```kotlin
package com.example.recorderproject.audio

/**
 * Direct-form II transposed biquad filter.
 * Stable numerically for time-varying coefficients (we change coeffs when band edits land).
 *
 * Difference equation:
 *   y[n] = b0*x[n] + s1[n-1]
 *   s1[n] = b1*x[n] - a1*y[n] + s2[n-1]
 *   s2[n] = b2*x[n] - a2*y[n]
 *
 * All coefficients pre-normalized by a0 (caller divides).
 */
class Biquad(
    private var b0: Double,
    private var b1: Double,
    private var b2: Double,
    private var a1: Double,
    private var a2: Double,
) {
    private var s1 = 0.0
    private var s2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + s1
        s1 = b1 * x - a1 * y + s2
        s2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        s1 = 0.0
        s2 = 0.0
    }

    fun setCoefficients(b0: Double, b1: Double, b2: Double, a1: Double, a2: Double) {
        this.b0 = b0; this.b1 = b1; this.b2 = b2; this.a1 = a1; this.a2 = a2
    }
}
```

- [ ] **Step 4: Run, verify PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.BiquadTest"
```
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/Biquad.kt \
        app/src/test/java/com/example/recorderproject/audio/BiquadTest.kt
git commit -m "feat(audio): add Biquad direct-form II transposed core"
```

---

## Task 5: RBJ biquad coefficients per filter type

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/BiquadCoeffs.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/BiquadCoeffsTest.kt`

Filter-type mapping (per spec): `LOW_PASS == HIGH_CUT` (LPF math), `HIGH_PASS == LOW_CUT` (HPF math). 4 user names, 2 formulas. `TILT` is a composite of two shelves.

- [ ] **Step 1: Write tests first (one per filter family)**

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class BiquadCoeffsTest {

    private val sr = 48_000f

    /** Frequency-response magnitude of a biquad at frequency f. */
    private fun magnitudeDb(b: Biquad, fHz: Float): Double {
        // Drive with a sine, measure peak after settle (1024 samples settle, then 4096 measure)
        val omega = 2.0 * PI * fHz / sr
        b.reset()
        repeat(1024) { n -> b.process(sin(omega * n)) }
        var peak = 0.0
        for (n in 1024 until 1024 + 4096) {
            val y = b.process(sin(omega * n))
            if (kotlin.math.abs(y) > peak) peak = kotlin.math.abs(y)
        }
        // Input peak is 1.0; magnitude = peak / 1.0
        return 20.0 * log10(peak.coerceAtLeast(1e-12))
    }

    @Test fun `bell at 1kHz with +6dB gives roughly +6dB at center`() {
        val band = EQBand(id = 1, type = EQBandType.BELL, frequencyHz = 1000f, gainDb = 6f, q = 1.4f)
        val b = BiquadCoeffs.forBand(band, sr)
        val gain = magnitudeDb(b, 1000f)
        assertEquals(6.0, gain, 0.6)
    }

    @Test fun `notch at 1kHz attenuates the center frequency strongly`() {
        val band = EQBand(id = 1, type = EQBandType.NOTCH, frequencyHz = 1000f, gainDb = 0f, q = 10f)
        val b = BiquadCoeffs.forBand(band, sr)
        val gain = magnitudeDb(b, 1000f)
        assertTrue("Notch at center should attenuate >20 dB but was $gain", gain < -20.0)
    }

    @Test fun `low pass at 1kHz passes 100Hz and rolls off at 10kHz`() {
        val band = EQBand(id = 1, type = EQBandType.LOW_PASS, frequencyHz = 1000f, gainDb = 0f, q = 0.707f)
        val b = BiquadCoeffs.forBand(band, sr)
        assertTrue("100 Hz should pass", magnitudeDb(b, 100f) > -3.0)
        assertTrue("10 kHz should roll off", magnitudeDb(b, 10_000f) < -15.0)
    }

    @Test fun `high pass at 1kHz blocks 100Hz`() {
        val band = EQBand(id = 1, type = EQBandType.HIGH_PASS, frequencyHz = 1000f, gainDb = 0f, q = 0.707f)
        val b = BiquadCoeffs.forBand(band, sr)
        assertTrue("100 Hz should be blocked", magnitudeDb(b, 100f) < -15.0)
        assertTrue("10 kHz should pass", magnitudeDb(b, 10_000f) > -3.0)
    }

    @Test fun `low cut is alias of high pass`() {
        val band1 = EQBand(id = 1, type = EQBandType.LOW_CUT, frequencyHz = 800f, gainDb = 0f, q = 0.707f)
        val band2 = band1.copy(type = EQBandType.HIGH_PASS)
        val b1 = BiquadCoeffs.forBand(band1, sr)
        val b2 = BiquadCoeffs.forBand(band2, sr)
        // Same input should give same output
        b1.reset(); b2.reset()
        for (n in 0 until 256) {
            val x = sin(2.0 * PI * 1234.0 * n / sr)
            assertEquals(b2.process(x), b1.process(x), 1e-9)
        }
    }

    @Test fun `low shelf at 80Hz with +6dB lifts the low end`() {
        val band = EQBand(id = 1, type = EQBandType.LOW_SHELF, frequencyHz = 80f, gainDb = 6f, q = 0.707f)
        val b = BiquadCoeffs.forBand(band, sr)
        val low = magnitudeDb(b, 40f)
        val high = magnitudeDb(b, 5_000f)
        assertTrue("Low should be lifted: low=$low high=$high", low > 4.0)
        assertTrue("High should be near 0 dB", kotlin.math.abs(high) < 1.0)
    }

    @Test fun `tilt with positive gain lifts highs and cuts lows`() {
        val band = EQBand(id = 1, type = EQBandType.TILT, frequencyHz = 500f, gainDb = 4f, q = 0.5f)
        val chain = BiquadCoeffs.cascadeForBand(band, sr)
        // Run a low and high tone through the cascade
        fun cascadeGainDb(fHz: Float): Double {
            chain.forEach { it.reset() }
            val omega = 2.0 * PI * fHz / sr
            repeat(1024) { n ->
                var y = sin(omega * n)
                chain.forEach { y = it.process(y) }
            }
            var peak = 0.0
            for (n in 1024 until 1024 + 4096) {
                var y = sin(omega * n)
                chain.forEach { y = it.process(y) }
                if (kotlin.math.abs(y) > peak) peak = kotlin.math.abs(y)
            }
            return 20.0 * log10(peak.coerceAtLeast(1e-12))
        }
        assertTrue("Lows should be cut", cascadeGainDb(80f) < -2.0)
        assertTrue("Highs should be lifted", cascadeGainDb(8000f) > 2.0)
    }
}
```

- [ ] **Step 2: Run, verify all fail (unresolved BiquadCoeffs)**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.BiquadCoeffsTest"
```
Expected: compile failure.

- [ ] **Step 3: Implement `BiquadCoeffs.kt`**

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * RBJ Audio EQ Cookbook biquad coefficient generator.
 * Reference: http://shepazu.github.io/Audio-EQ-Cookbook/audio-eq-cookbook.html
 */
object BiquadCoeffs {

    /** Produce a single Biquad for the given band (TILT returns one of the two cascaded biquads). */
    fun forBand(band: EQBand, sampleRate: Float): Biquad {
        // TILT is a 2-biquad cascade — for single-biquad API we return just the low-shelf half
        if (band.type == EQBandType.TILT) return cascadeForBand(band, sampleRate).first()
        return singleBiquad(band, band.type, sampleRate)
    }

    /** Produce the biquad cascade for a band (length 1 except TILT which is 2). */
    fun cascadeForBand(band: EQBand, sampleRate: Float): List<Biquad> {
        if (band.type != EQBandType.TILT) return listOf(singleBiquad(band, band.type, sampleRate))
        // TILT: low-shelf at freq with -gain + high-shelf at freq with +gain
        val lowHalf = singleBiquad(band.copy(gainDb = -band.gainDb), EQBandType.LOW_SHELF, sampleRate)
        val highHalf = singleBiquad(band, EQBandType.HIGH_SHELF, sampleRate)
        return listOf(lowHalf, highHalf)
    }

    private fun singleBiquad(band: EQBand, forcedType: EQBandType, sampleRate: Float): Biquad {
        val f0 = band.frequencyHz.toDouble().coerceIn(20.0, (sampleRate / 2.0) - 100.0)
        val q = band.q.toDouble().coerceAtLeast(0.05)
        val gainDb = band.gainDb.toDouble()
        val omega = 2.0 * PI * f0 / sampleRate
        val sinW = sin(omega)
        val cosW = cos(omega)
        val alpha = sinW / (2.0 * q)
        val a = 10.0.pow(gainDb / 40.0) // sqrt(linear gain), used by shelves/bell

        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1Coef: Double; val a2Coef: Double

        when (forcedType) {
            EQBandType.BELL -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha / a
            }
            EQBandType.LOW_SHELF -> {
                val sqrtA = sqrt(a)
                b0 = a * ((a + 1) - (a - 1) * cosW + 2 * sqrtA * alpha)
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW)
                b2 = a * ((a + 1) - (a - 1) * cosW - 2 * sqrtA * alpha)
                a0 = (a + 1) + (a - 1) * cosW + 2 * sqrtA * alpha
                a1Coef = -2 * ((a - 1) + (a + 1) * cosW)
                a2Coef = (a + 1) + (a - 1) * cosW - 2 * sqrtA * alpha
            }
            EQBandType.HIGH_SHELF -> {
                val sqrtA = sqrt(a)
                b0 = a * ((a + 1) + (a - 1) * cosW + 2 * sqrtA * alpha)
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW)
                b2 = a * ((a + 1) + (a - 1) * cosW - 2 * sqrtA * alpha)
                a0 = (a + 1) - (a - 1) * cosW + 2 * sqrtA * alpha
                a1Coef = 2 * ((a - 1) - (a + 1) * cosW)
                a2Coef = (a + 1) - (a - 1) * cosW - 2 * sqrtA * alpha
            }
            // LPF: LOW_PASS == HIGH_CUT
            EQBandType.LOW_PASS, EQBandType.HIGH_CUT -> {
                b0 = (1.0 - cosW) / 2.0
                b1 = 1.0 - cosW
                b2 = (1.0 - cosW) / 2.0
                a0 = 1.0 + alpha
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha
            }
            // HPF: HIGH_PASS == LOW_CUT
            EQBandType.HIGH_PASS, EQBandType.LOW_CUT -> {
                b0 = (1.0 + cosW) / 2.0
                b1 = -(1.0 + cosW)
                b2 = (1.0 + cosW) / 2.0
                a0 = 1.0 + alpha
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha
            }
            EQBandType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cosW
                b2 = 1.0
                a0 = 1.0 + alpha
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha
            }
            EQBandType.BAND_PASS -> {
                // constant-skirt-gain band-pass
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                a0 = 1.0 + alpha
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha
            }
            EQBandType.TILT -> error("TILT handled via cascadeForBand, not singleBiquad")
        }

        return Biquad(
            b0 = b0 / a0,
            b1 = b1 / a0,
            b2 = b2 / a0,
            a1 = a1Coef / a0,
            a2 = a2Coef / a0,
        )
    }
}
```

- [ ] **Step 4: Run, verify PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.BiquadCoeffsTest"
```
Expected: 7 tests PASS (tolerances chosen generously for sine-sweep measurement).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/BiquadCoeffs.kt \
        app/src/test/java/com/example/recorderproject/audio/BiquadCoeffsTest.kt
git commit -m "feat(audio): add RBJ biquad coefficient generator for all 10 band types"
```

---

## Task 6: WAV I/O helper

**Goal:** A small reader/writer for 16/24/32-bit PCM WAV files that the EQ pipeline can stream through. The existing `AudioRecorderManager` writes WAVs but doesn't expose a generic streaming API.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/WavIo.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/WavIoTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class WavIoTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `write then read 16-bit mono round trip preserves samples`() {
        val sr = 48_000
        val n = 4800 // 0.1s
        val samples = FloatArray(n) { i -> 0.5f * sin(2.0 * PI * 1000.0 * i / sr).toFloat() }
        val file = tmp.newFile("test16.wav")

        WavIo.write(file, samples, channels = 1, sampleRate = sr, bitDepth = 16)

        val header = WavIo.readHeader(file)
        assertEquals(1, header.channels)
        assertEquals(sr, header.sampleRate)
        assertEquals(16, header.bitDepth)
        assertEquals(n.toLong(), header.totalFrames)

        val readBack = WavIo.readAllSamples(file)
        assertEquals(n, readBack.size)
        // 16-bit quantization tolerance: ~3e-5
        for (i in samples.indices) {
            assertEquals("sample $i", samples[i], readBack[i], 1e-4f)
        }
    }

    @Test fun `write then read stereo interleaves channels correctly`() {
        val sr = 44_100
        val frames = 1000
        val interleaved = FloatArray(frames * 2)
        for (i in 0 until frames) {
            interleaved[2 * i] = 0.3f          // L
            interleaved[2 * i + 1] = -0.3f      // R
        }
        val file = tmp.newFile("stereo.wav")
        WavIo.write(file, interleaved, channels = 2, sampleRate = sr, bitDepth = 16)

        val header = WavIo.readHeader(file)
        assertEquals(2, header.channels)
        assertEquals(frames.toLong(), header.totalFrames)

        val readBack = WavIo.readAllSamples(file)
        assertEquals(frames * 2, readBack.size)
        for (i in 0 until frames) {
            assertEquals(0.3f, readBack[2 * i], 1e-4f)
            assertEquals(-0.3f, readBack[2 * i + 1], 1e-4f)
        }
    }

    @Test fun `streaming read returns blocks until EOF`() {
        val sr = 48_000
        val n = 10_000
        val samples = FloatArray(n) { it.toFloat() / n - 0.5f }
        val file = tmp.newFile("stream.wav")
        WavIo.write(file, samples, channels = 1, sampleRate = sr, bitDepth = 16)

        val reader = WavIo.openReader(file)
        val buf = FloatArray(4096)
        var total = 0
        while (true) {
            val read = reader.readBlock(buf)
            if (read <= 0) break
            total += read
        }
        reader.close()
        assertEquals(n, total)
    }
}
```

- [ ] **Step 2: Run, verify fail**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.WavIoTest"
```

- [ ] **Step 3: Implement `WavIo.kt`**

```kotlin
package com.example.recorderproject.audio

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavIo {

    data class Header(
        val channels: Int,
        val sampleRate: Int,
        val bitDepth: Int,
        val totalFrames: Long,
        val dataOffset: Long,   // byte offset where PCM data begins
        val dataSize: Long,     // size of PCM data in bytes
    )

    /** Read the WAV header from a file. Throws IllegalArgumentException if format unsupported. */
    fun readHeader(file: File): Header {
        FileInputStream(file).use { fis ->
            val buf = ByteArray(44)
            val read = fis.read(buf)
            require(read >= 44) { "WAV file truncated (header missing)" }
            // Verify RIFF / WAVE
            require(buf.sliceArray(0..3).toString(Charsets.US_ASCII) == "RIFF") { "Not a RIFF file" }
            require(buf.sliceArray(8..11).toString(Charsets.US_ASCII) == "WAVE") { "Not a WAVE file" }
            // Walk chunks to find "fmt " and "data"
            // Simple WAVs are 44 bytes; real-world WAVs have extra chunks (JUNK, LIST, iXML, cue)
            var pos = 12L
            val raf = java.io.RandomAccessFile(file, "r")
            raf.use {
                var channels = 0; var sampleRate = 0; var bitDepth = 0
                var dataOffset = 0L; var dataSize = 0L
                while (pos < it.length()) {
                    it.seek(pos)
                    val idBytes = ByteArray(4); it.read(idBytes)
                    val sizeBytes = ByteArray(4); it.read(sizeBytes)
                    val chunkSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    val id = idBytes.toString(Charsets.US_ASCII)
                    when (id) {
                        "fmt " -> {
                            val fmt = ByteArray(chunkSize); it.read(fmt)
                            val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                            bb.short // audio format (1 = PCM)
                            channels = bb.short.toInt()
                            sampleRate = bb.int
                            bb.int // byte rate
                            bb.short // block align
                            bitDepth = bb.short.toInt()
                        }
                        "data" -> {
                            dataOffset = pos + 8
                            dataSize = chunkSize.toLong()
                            val bytesPerFrame = channels * (bitDepth / 8)
                            require(bytesPerFrame > 0) { "fmt chunk must precede data chunk" }
                            return Header(
                                channels = channels,
                                sampleRate = sampleRate,
                                bitDepth = bitDepth,
                                totalFrames = dataSize / bytesPerFrame,
                                dataOffset = dataOffset,
                                dataSize = dataSize,
                            )
                        }
                    }
                    pos += 8 + chunkSize + (chunkSize and 1) // chunks padded to even
                }
                error("data chunk not found")
            }
        }
    }

    /** Read the entire file as interleaved float samples in [-1, 1]. */
    fun readAllSamples(file: File): FloatArray {
        val header = readHeader(file)
        val reader = StreamReader(file, header)
        val out = FloatArray(header.totalFrames.toInt() * header.channels)
        var written = 0
        val block = FloatArray(8192)
        while (true) {
            val read = reader.readBlock(block)
            if (read <= 0) break
            System.arraycopy(block, 0, out, written, read)
            written += read
        }
        reader.close()
        return out
    }

    fun openReader(file: File): StreamReader = StreamReader(file, readHeader(file))

    /** Streaming reader; returns interleaved samples per readBlock(buf). */
    class StreamReader internal constructor(file: File, val header: Header) {
        private val raf = java.io.RandomAccessFile(file, "r")
        private var framePos = 0L
        private val bytesPerSample = header.bitDepth / 8
        private val bytesPerFrame = bytesPerSample * header.channels

        init { raf.seek(header.dataOffset) }

        /** Reads up to buf.size interleaved samples; returns count (0 at EOF). */
        fun readBlock(buf: FloatArray): Int {
            val framesAvail = (header.totalFrames - framePos)
            if (framesAvail <= 0) return 0
            val framesToRead = minOf(buf.size / header.channels, framesAvail.toInt())
            val byteCount = framesToRead * bytesPerFrame
            val bytes = ByteArray(byteCount)
            val readBytes = raf.read(bytes)
            if (readBytes <= 0) return 0
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val samplesRead = (readBytes / bytesPerSample)
            for (i in 0 until samplesRead) {
                buf[i] = when (header.bitDepth) {
                    16 -> bb.short.toFloat() / Short.MAX_VALUE.toFloat()
                    24 -> {
                        val b0 = bb.get().toInt() and 0xFF
                        val b1 = bb.get().toInt() and 0xFF
                        val b2 = bb.get().toInt()
                        val s = (b2 shl 16) or (b1 shl 8) or b0
                        // sign-extend 24 → 32
                        val signed = if (s and 0x800000 != 0) s or 0xFF000000.toInt() else s
                        signed.toFloat() / 8388608f
                    }
                    32 -> bb.int.toFloat() / Int.MAX_VALUE.toFloat()
                    else -> error("Unsupported bit depth ${header.bitDepth}")
                }
            }
            framePos += samplesRead / header.channels
            return samplesRead
        }

        fun close() = raf.close()
    }

    /** Write interleaved float samples to a WAV file. Supports 16/24/32-bit PCM. */
    fun write(
        file: File,
        interleavedSamples: FloatArray,
        channels: Int,
        sampleRate: Int,
        bitDepth: Int,
    ) {
        require(bitDepth in listOf(16, 24, 32)) { "Unsupported bit depth $bitDepth" }
        require(channels in 1..2) { "Unsupported channel count $channels" }
        val bytesPerSample = bitDepth / 8
        val dataSize = interleavedSamples.size * bytesPerSample
        FileOutputStream(file).use { fos ->
            val out = DataOutputStream(fos)
            // RIFF header
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.writeInt(Integer.reverseBytes(36 + dataSize))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            // fmt chunk
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.writeInt(Integer.reverseBytes(16))                          // PCM fmt subchunk size
            out.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())     // PCM = 1
            out.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
            out.writeInt(Integer.reverseBytes(sampleRate))
            out.writeInt(Integer.reverseBytes(sampleRate * channels * bytesPerSample))  // byte rate
            out.writeShort(java.lang.Short.reverseBytes((channels * bytesPerSample).toShort()).toInt()) // block align
            out.writeShort(java.lang.Short.reverseBytes(bitDepth.toShort()).toInt())
            // data chunk
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.writeInt(Integer.reverseBytes(dataSize))
            // samples
            val bb = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            when (bitDepth) {
                16 -> for (s in interleavedSamples) {
                    val v = (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                    bb.putShort(v)
                }
                24 -> for (s in interleavedSamples) {
                    val v = (s.coerceIn(-1f, 1f) * 8_388_607f).toInt()
                    bb.put((v and 0xFF).toByte())
                    bb.put(((v shr 8) and 0xFF).toByte())
                    bb.put(((v shr 16) and 0xFF).toByte())
                }
                32 -> for (s in interleavedSamples) {
                    val v = (s.coerceIn(-1f, 1f).toDouble() * Int.MAX_VALUE).toInt()
                    bb.putInt(v)
                }
            }
            out.write(bb.array())
        }
    }
}
```

- [ ] **Step 4: Run, verify PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.WavIoTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/WavIo.kt \
        app/src/test/java/com/example/recorderproject/audio/WavIoTest.kt
git commit -m "feat(audio): add WAV streaming I/O (16/24/32-bit PCM, mono/stereo)"
```

---

## Task 7: EQProcessor — render WAV through the biquad cascade

**Goal:** Take a source WAV + an `EQChain` + a target file, stream the source through cascaded biquads per channel, write the result.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/EQProcessor.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/EQProcessorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.model.EQChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class EQProcessorTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun writeSine(file: File, fHz: Float, sr: Int, durationSec: Float, amp: Float = 0.5f, channels: Int = 1) {
        val frames = (sr * durationSec).toInt()
        val samples = FloatArray(frames * channels) { i ->
            val frame = i / channels
            amp * sin(2.0 * PI * fHz * frame / sr).toFloat()
        }
        WavIo.write(file, samples, channels, sr, bitDepth = 16)
    }

    /** Approximate the RMS of the read-back file. */
    private fun rms(file: File): Float {
        val s = WavIo.readAllSamples(file)
        var acc = 0.0
        for (v in s) acc += v * v
        return kotlin.math.sqrt(acc / s.size).toFloat()
    }

    @Test fun `flat chain produces output near-identical to input`() {
        val input = tmp.newFile("in.wav")
        val output = tmp.newFile("out.wav")
        writeSine(input, fHz = 1000f, sr = 48_000, durationSec = 0.2f)

        EQProcessor.process(input, output, EQChain.empty(), progress = {})

        // Flat chain (all bands disabled) → output equals input within 16-bit quantization
        val a = WavIo.readAllSamples(input)
        val b = WavIo.readAllSamples(output)
        assertEquals(a.size, b.size)
        var maxDiff = 0f
        for (i in a.indices) {
            val d = kotlin.math.abs(a[i] - b[i])
            if (d > maxDiff) maxDiff = d
        }
        assertTrue("Flat chain altered samples by $maxDiff", maxDiff < 1e-3f)
    }

    @Test fun `notch at 1kHz strongly attenuates 1kHz tone`() {
        val input = tmp.newFile("in.wav")
        val output = tmp.newFile("out.wav")
        writeSine(input, fHz = 1000f, sr = 48_000, durationSec = 0.5f)

        val chain = EQChain.empty().withBand(
            EQBand(id = 1, type = EQBandType.NOTCH, frequencyHz = 1000f, gainDb = 0f, q = 10f, enabled = true)
        )
        EQProcessor.process(input, output, chain, progress = {})

        val inRms = rms(input)
        val outRms = rms(output)
        val ratioDb = 20.0 * kotlin.math.log10((outRms / inRms).toDouble())
        assertTrue("Expected >15 dB attenuation but got ${ratioDb} dB (inRms=$inRms outRms=$outRms)", ratioDb < -15.0)
    }

    @Test fun `progress callback reaches 1f at end`() {
        val input = tmp.newFile("in.wav")
        val output = tmp.newFile("out.wav")
        writeSine(input, fHz = 500f, sr = 48_000, durationSec = 0.1f)
        var lastProgress = -1f
        EQProcessor.process(input, output, EQChain.empty(), progress = { lastProgress = it })
        assertEquals(1f, lastProgress, 0.01f)
    }
}
```

- [ ] **Step 2: Run, verify fail**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.EQProcessorTest"
```

- [ ] **Step 3: Implement `EQProcessor.kt`**

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.model.EQChain
import java.io.File
import kotlin.math.tanh

object EQProcessor {

    private const val BLOCK_FRAMES = 8192

    /**
     * Render src through the EQ chain to dst.
     *
     * @param progress callback in [0f, 1f]; called approximately once per block plus 1f at the very end.
     */
    fun process(
        src: File,
        dst: File,
        chain: EQChain,
        clipProtection: Boolean = true,
        progress: (Float) -> Unit,
    ) {
        val reader = WavIo.openReader(src)
        val header = reader.header
        val channels = header.channels.coerceAtMost(2)
        val sr = header.sampleRate.toFloat()

        // Build per-channel biquad cascades
        val activeBands = if (chain.bypassed) emptyList() else chain.bands.filter { it.enabled && !it.muted }
        val cascadesPerChannel: List<List<Biquad>> = (0 until channels).map {
            activeBands.flatMap { BiquadCoeffs.cascadeForBand(it, sr) }
        }

        // Pre-allocate buffers
        val readBuf = FloatArray(BLOCK_FRAMES * header.channels)
        val writeBuf = FloatArray(BLOCK_FRAMES * header.channels)

        val outSamples = ArrayList<FloatArray>()
        var totalFramesProcessed = 0L
        val totalFrames = header.totalFrames.coerceAtLeast(1)

        while (true) {
            val n = reader.readBlock(readBuf)
            if (n <= 0) break
            val frames = n / header.channels
            for (frame in 0 until frames) {
                for (ch in 0 until channels) {
                    var x = readBuf[frame * header.channels + ch].toDouble()
                    val cascade = cascadesPerChannel[ch]
                    for (biq in cascade) x = biq.process(x)
                    if (clipProtection && (x > 0.999 || x < -0.999)) x = tanh(x)
                    writeBuf[frame * header.channels + ch] = x.toFloat()
                }
                // Copy unprocessed channels (>2) untouched
                for (ch in channels until header.channels) {
                    writeBuf[frame * header.channels + ch] = readBuf[frame * header.channels + ch]
                }
            }
            outSamples.add(writeBuf.copyOf(n))
            totalFramesProcessed += frames
            progress((totalFramesProcessed.toFloat() / totalFrames).coerceIn(0f, 0.99f))
        }
        reader.close()

        // Flatten then write
        val total = outSamples.sumOf { it.size }
        val flat = FloatArray(total)
        var pos = 0
        for (chunk in outSamples) {
            System.arraycopy(chunk, 0, flat, pos, chunk.size)
            pos += chunk.size
        }
        WavIo.write(dst, flat, header.channels, header.sampleRate, header.bitDepth)
        progress(1f)
    }
}
```

- [ ] **Step 4: Run, verify PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.EQProcessorTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/EQProcessor.kt \
        app/src/test/java/com/example/recorderproject/audio/EQProcessorTest.kt
git commit -m "feat(audio): add EQProcessor offline render pipeline (mono+stereo)"
```

---

## Task 8: Spectrum analyzer

**Goal:** A static FFT-based magnitude spectrum across the whole file, returned as a 256-bin float array in dB. Reuses the recovered `FFTAnalyzer.kt`.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/SpectrumAnalyzer.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/SpectrumAnalyzerTest.kt`

- [ ] **Step 1: Check the existing FFTAnalyzer API**

```bash
grep -n "fun\|class\|object" /Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/com/example/recorderproject/audio/FFTAnalyzer.kt | head
```
Note its public API. Adapt the implementation below if names differ — `SpectrumAnalyzer` is a thin wrapper.

- [ ] **Step 2: Write the test**

```kotlin
package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class SpectrumAnalyzerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun writeSine(file: File, fHz: Float, sr: Int, durationSec: Float) {
        val frames = (sr * durationSec).toInt()
        val samples = FloatArray(frames) { i -> 0.5f * sin(2.0 * PI * fHz * i / sr).toFloat() }
        WavIo.write(file, samples, channels = 1, sampleRate = sr, bitDepth = 16)
    }

    @Test fun `1kHz tone produces a peak near 1kHz`() {
        val sr = 48_000
        val f = tmp.newFile("tone.wav")
        writeSine(f, fHz = 1000f, sr = sr, durationSec = 1f)

        val spectrum = SpectrumAnalyzer.analyzeFile(f, bins = 256)
        assertEquals(256, spectrum.magnitudeDb.size)
        // Peak bin should map to a frequency near 1000 Hz on the log scale
        var peakIdx = 0
        for (i in spectrum.magnitudeDb.indices) if (spectrum.magnitudeDb[i] > spectrum.magnitudeDb[peakIdx]) peakIdx = i
        val peakFreq = spectrum.frequencyForBin(peakIdx)
        assertTrue("Peak should be near 1000 Hz, was $peakFreq", kotlin.math.abs(peakFreq - 1000.0) < 200.0)
    }
}
```

- [ ] **Step 3: Implement `SpectrumAnalyzer.kt`**

```kotlin
package com.example.recorderproject.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.exp

/** Static spectrum result for the EQ curve view to overlay. */
data class StaticSpectrum(
    val magnitudeDb: FloatArray,    // 256 bins of magnitude in dB, log-frequency mapped
    val minFreqHz: Float,
    val maxFreqHz: Float,
) {
    fun frequencyForBin(idx: Int): Double {
        val t = idx.toDouble() / (magnitudeDb.size - 1)
        return exp(ln(minFreqHz.toDouble()) + t * (ln(maxFreqHz.toDouble()) - ln(minFreqHz.toDouble())))
    }
}

object SpectrumAnalyzer {

    private const val FFT_SIZE = 4096
    private const val HOP = FFT_SIZE / 2 // 50% overlap

    fun analyzeFile(file: File, bins: Int = 256, minFreqHz: Float = 20f, maxFreqHz: Float = 20_000f): StaticSpectrum {
        val header = WavIo.readHeader(file)
        val sr = header.sampleRate
        val reader = WavIo.openReader(file)
        val buf = FloatArray(FFT_SIZE * header.channels)
        // Linear bins from the FFT; we'll re-map to log bins at the end
        val fftMagAcc = DoubleArray(FFT_SIZE / 2)
        var nWindows = 0

        // Hann window
        val window = DoubleArray(FFT_SIZE) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1)) }
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)

        var leftover = FloatArray(0)
        while (true) {
            val n = reader.readBlock(buf)
            if (n <= 0) break
            // Average channels to mono, accumulate
            val monoLen = n / header.channels
            val mono = FloatArray(monoLen) { i ->
                var sum = 0f
                for (ch in 0 until header.channels) sum += buf[i * header.channels + ch]
                sum / header.channels
            }
            val combined = leftover + mono
            var pos = 0
            while (pos + FFT_SIZE <= combined.size) {
                for (i in 0 until FFT_SIZE) {
                    real[i] = combined[pos + i].toDouble() * window[i]
                    imag[i] = 0.0
                }
                fftInPlace(real, imag)
                for (k in 0 until FFT_SIZE / 2) {
                    val mag = kotlin.math.sqrt(real[k] * real[k] + imag[k] * imag[k])
                    fftMagAcc[k] += mag
                }
                nWindows++
                pos += HOP
            }
            leftover = if (pos < combined.size) combined.copyOfRange(pos, combined.size) else FloatArray(0)
        }
        reader.close()

        if (nWindows == 0) {
            return StaticSpectrum(FloatArray(bins) { -120f }, minFreqHz, maxFreqHz)
        }
        for (k in fftMagAcc.indices) fftMagAcc[k] /= nWindows

        // Re-map linear bins to log-spaced bins
        val out = FloatArray(bins)
        for (i in 0 until bins) {
            val t = i.toDouble() / (bins - 1)
            val freq = exp(ln(minFreqHz.toDouble()) + t * (ln(maxFreqHz.toDouble()) - ln(minFreqHz.toDouble())))
            val binIdx = (freq * FFT_SIZE / sr).toInt().coerceIn(0, FFT_SIZE / 2 - 1)
            val mag = fftMagAcc[binIdx]
            out[i] = (20.0 * log10(max(mag, 1e-12))).toFloat()
        }
        return StaticSpectrum(out, minFreqHz, maxFreqHz)
    }

    /** Iterative in-place Cooley-Tukey FFT (radix-2). FFT_SIZE must be a power of 2. */
    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        // Bit-reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlenR = cos(ang); val wlenI = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var wR = 1.0; var wI = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]; val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                    val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                    real[i + k] = uR + vR; imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR; imag[i + k + len / 2] = uI - vI
                    val nwR = wR * wlenR - wI * wlenI
                    val nwI = wR * wlenI + wI * wlenR
                    wR = nwR; wI = nwI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
```

- [ ] **Step 4: Run, verify PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.SpectrumAnalyzerTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/SpectrumAnalyzer.kt \
        app/src/test/java/com/example/recorderproject/audio/SpectrumAnalyzerTest.kt
git commit -m "feat(audio): add SpectrumAnalyzer with built-in radix-2 FFT"
```

---

## Task 9: EQAutoDetect — propose notches from a spectrum

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/EQAutoDetect.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/EQAutoDetectTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBandType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class EQAutoDetectTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `detects 60Hz hum injected into pink noise`() {
        val sr = 48_000
        val frames = sr * 2
        val rnd = java.util.Random(42)
        val samples = FloatArray(frames) { i ->
            val hum = 0.3f * sin(2.0 * PI * 60.0 * i / sr).toFloat()
            val noise = (rnd.nextFloat() - 0.5f) * 0.1f
            hum + noise
        }
        val f = tmp.newFile("hum.wav")
        WavIo.write(f, samples, channels = 1, sampleRate = sr, bitDepth = 16)

        val spectrum = SpectrumAnalyzer.analyzeFile(f, bins = 512)
        val suggestions = EQAutoDetect.proposeNotches(spectrum, maxBands = 4)
        assertTrue("Expected at least one suggestion", suggestions.isNotEmpty())
        val near60 = suggestions.any { kotlin.math.abs(it.frequencyHz - 60f) < 15f }
        assertTrue("Expected a notch near 60 Hz, got: ${suggestions.map { it.frequencyHz }}", near60)
        assertTrue("All suggestions should be NOTCH type", suggestions.all { it.type == EQBandType.NOTCH })
    }
}
```

- [ ] **Step 2: Run, verify fail**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.EQAutoDetectTest"
```

- [ ] **Step 3: Implement `EQAutoDetect.kt`**

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType

object EQAutoDetect {

    /** Identify prominent peaks in the spectrum and propose narrow notch filters. */
    fun proposeNotches(spectrum: StaticSpectrum, maxBands: Int = 8): List<EQBand> {
        val mag = spectrum.magnitudeDb
        val n = mag.size
        // Local mean using a sliding window of ±10 bins
        val window = 10
        val proposals = mutableListOf<Pair<Int, Float>>() // (binIdx, prominenceDb)
        for (i in window until n - window) {
            var meanAcc = 0f
            for (j in i - window..i + window) if (j != i) meanAcc += mag[j]
            val mean = meanAcc / (window * 2)
            val prominence = mag[i] - mean
            val isLocalMax = mag[i] > mag[i - 1] && mag[i] > mag[i + 1]
            if (prominence > 8f && isLocalMax) {
                proposals.add(i to prominence)
            }
        }
        // Sort by prominence, keep top maxBands, then re-sort by frequency for stable ordering
        val top = proposals.sortedByDescending { it.second }.take(maxBands).sortedBy { it.first }
        return top.mapIndexed { idx, (binIdx, prom) ->
            val freq = spectrum.frequencyForBin(binIdx).toFloat()
            // Heuristic Q: narrower for higher-prominence peaks
            val q = (prom.toDouble().coerceIn(4.0, 24.0) * 0.7 + 4.0).toFloat()
            EQBand(
                id = idx + 1,
                type = EQBandType.NOTCH,
                frequencyHz = freq.coerceIn(20f, 20_000f),
                gainDb = 0f,
                q = q,
                enabled = true,
            )
        }
    }
}
```

- [ ] **Step 4: Run, verify PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.EQAutoDetectTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/EQAutoDetect.kt \
        app/src/test/java/com/example/recorderproject/audio/EQAutoDetectTest.kt
git commit -m "feat(audio): add EQAutoDetect peak-picking for noise-cut suggestions"
```

---

## Task 10: EQCurveFitter — freeform draw → biquad chain

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/EQCurveFitter.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/EQCurveFitterTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBandType
import org.junit.Assert.assertTrue
import org.junit.Test

class EQCurveFitterTest {

    @Test fun `fits a single bump near 1kHz to a bell band there`() {
        // 32 log-spaced freq points from 20 Hz to 20 kHz, target = 0 dB everywhere except +6 dB at 1k
        val bins = 64
        val target = FloatArray(bins) { idx ->
            val t = idx.toFloat() / (bins - 1)
            val freq = kotlin.math.exp(kotlin.math.ln(20.0) + t * (kotlin.math.ln(20_000.0) - kotlin.math.ln(20.0)))
            val centerOctaves = kotlin.math.ln(freq / 1000.0) / kotlin.math.ln(2.0)
            (6.0 * kotlin.math.exp(-centerOctaves * centerOctaves * 4)).toFloat()
        }
        val bands = EQCurveFitter.fitToCurve(target, minFreqHz = 20f, maxFreqHz = 20_000f, maxBands = 4)
        assertTrue(bands.isNotEmpty())
        val nearK = bands.any { kotlin.math.abs(it.frequencyHz - 1000f) < 250f }
        assertTrue("Expected a band near 1 kHz, got ${bands.map { it.frequencyHz }}", nearK)
        assertTrue("Band gain should be roughly +6dB", bands.any { it.gainDb > 3f && it.gainDb < 10f })
        assertTrue(bands.all { it.type == EQBandType.BELL })
    }
}
```

- [ ] **Step 2: Run, verify fail**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.EQCurveFitterTest"
```

- [ ] **Step 3: Implement `EQCurveFitter.kt`**

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import kotlin.math.exp
import kotlin.math.ln

object EQCurveFitter {

    /**
     * Convert a freeform target curve (dB values at log-spaced frequencies) into up to
     * maxBands Bell EQBands. The fitter finds local extrema of the curve and assigns
     * each to a band; Q is estimated from the local width.
     */
    fun fitToCurve(
        targetDb: FloatArray,
        minFreqHz: Float,
        maxFreqHz: Float,
        maxBands: Int = 8,
    ): List<EQBand> {
        val n = targetDb.size
        if (n < 5) return emptyList()
        val extrema = mutableListOf<Pair<Int, Float>>() // (idx, |gain|)
        for (i in 2 until n - 2) {
            val v = targetDb[i]
            val isMax = v > targetDb[i - 1] && v > targetDb[i + 1] && v > 1f
            val isMin = v < targetDb[i - 1] && v < targetDb[i + 1] && v < -1f
            if (isMax || isMin) extrema.add(i to kotlin.math.abs(v))
        }
        val top = extrema.sortedByDescending { it.second }.take(maxBands).sortedBy { it.first }
        return top.mapIndexed { slot, (idx, _) ->
            val gain = targetDb[idx]
            val t = idx.toDouble() / (n - 1)
            val freq = exp(ln(minFreqHz.toDouble()) + t * (ln(maxFreqHz.toDouble()) - ln(minFreqHz.toDouble())))

            // Estimate Q from how quickly the curve returns toward 0 from this extremum
            var halfWidthBins = 0
            for (off in 1 until 12) {
                val l = (idx - off).coerceAtLeast(0)
                val r = (idx + off).coerceAtMost(n - 1)
                if (kotlin.math.abs(targetDb[l]) < kotlin.math.abs(gain) / 2 ||
                    kotlin.math.abs(targetDb[r]) < kotlin.math.abs(gain) / 2
                ) {
                    halfWidthBins = off
                    break
                }
            }
            val halfWidthOctaves = (halfWidthBins.coerceAtLeast(1).toFloat() / n) * (kotlin.math.ln(maxFreqHz / minFreqHz) / kotlin.math.ln(2f))
            val q = (1.0 / (2.0 * halfWidthOctaves + 0.1)).coerceIn(0.4, 8.0)

            EQBand(
                id = slot + 1,
                type = EQBandType.BELL,
                frequencyHz = freq.toFloat().coerceIn(20f, 20_000f),
                gainDb = gain.coerceIn(-24f, 24f),
                q = q.toFloat(),
                enabled = true,
            )
        }
    }
}
```

- [ ] **Step 4: Run, verify PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.audio.EQCurveFitterTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/EQCurveFitter.kt \
        app/src/test/java/com/example/recorderproject/audio/EQCurveFitterTest.kt
git commit -m "feat(audio): add EQCurveFitter for freeform-draw → biquad chain"
```

---

## Task 11: 10 plugin-emulation presets + EQPreset model

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/EQPreset.kt`
- Test: `app/src/test/java/com/example/recorderproject/model/EQPresetTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EQPresetTest {

    @Test fun `there are exactly 10 presets`() {
        assertEquals(10, EQPresets.ALL.size)
    }

    @Test fun `every preset name is unique`() {
        val names = EQPresets.ALL.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun `Flat preset has all bands disabled`() {
        val flat = EQPresets.ALL.first { it.name == "Flat" }
        assertTrue(flat.bands.all { !it.enabled })
    }

    @Test fun `every preset has 8 bands and parameters in range`() {
        for (preset in EQPresets.ALL) {
            assertEquals("Preset ${preset.name} should have 8 bands", 8, preset.bands.size)
            for (b in preset.bands) {
                assertTrue("${preset.name}: freq ${b.frequencyHz} out of range",
                    b.frequencyHz in EQBand.MIN_FREQ_HZ..EQBand.MAX_FREQ_HZ)
                assertTrue("${preset.name}: gain ${b.gainDb} out of range",
                    b.gainDb in EQBand.MIN_GAIN_DB..EQBand.MAX_GAIN_DB)
                assertTrue("${preset.name}: Q ${b.q} out of range",
                    b.q in EQBand.MIN_Q..EQBand.MAX_Q)
            }
        }
    }

    @Test fun `expected preset names ship`() {
        val expected = setOf(
            "Flat", "Air Lift", "Vintage Console", "Pultec Smooth", "API Punch",
            "Massive Low", "Neve Warmth", "Broadcast Voice", "De-Ess", "Master Bus",
        )
        assertEquals(expected, EQPresets.ALL.map { it.name }.toSet())
    }
}
```

- [ ] **Step 2: Run, verify fail**

- [ ] **Step 3: Implement `EQPreset.kt`**

```kotlin
package com.example.recorderproject.model

data class EQPreset(
    val name: String,
    val description: String,
    val category: PresetCategory,
    val bands: List<EQBand>,
)

object EQPresets {

    private fun band(id: Int, type: EQBandType, hz: Float, db: Float, q: Float, on: Boolean = true): EQBand =
        EQBand(id = id, type = type, frequencyHz = hz, gainDb = db, q = q, enabled = on)

    private fun off(id: Int, hz: Float): EQBand =
        EQBand(id = id, type = EQBandType.BELL, frequencyHz = hz, gainDb = 0f, q = 1f, enabled = false)

    private val Flat = EQPreset(
        name = "Flat",
        description = "All bands disabled — true bypass for A/B reference",
        category = PresetCategory.NEUTRAL,
        bands = listOf(
            off(1, 60f), off(2, 150f), off(3, 320f), off(4, 700f),
            off(5, 1500f), off(6, 3200f), off(7, 7000f), off(8, 12000f),
        ),
    )

    private val AirLift = EQPreset(
        name = "Air Lift",
        description = "Light high-shelf opening — FabFilter Pro-Q-style sparkle",
        category = PresetCategory.MASTER,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 30f, 0f, 0.707f),
            off(2, 150f), off(3, 320f), off(4, 700f), off(5, 1500f),
            band(6, EQBandType.BELL, 4000f, 1.5f, 1.2f),
            band(7, EQBandType.HIGH_SHELF, 8000f, 3f, 0.7f),
            band(8, EQBandType.HIGH_SHELF, 14000f, 2.5f, 0.5f),
        ),
    )

    private val VintageConsole = EQPreset(
        name = "Vintage Console",
        description = "SSL E-Channel-style mid scoop + smooth highs",
        category = PresetCategory.VINTAGE,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 60f, 0f, 0.707f),
            band(2, EQBandType.LOW_SHELF, 120f, 2f, 0.7f),
            band(3, EQBandType.BELL, 350f, -2.5f, 1.0f),
            band(4, EQBandType.BELL, 900f, -1.5f, 1.2f),
            off(5, 1500f),
            band(6, EQBandType.BELL, 4500f, 2f, 1.4f),
            band(7, EQBandType.HIGH_SHELF, 10000f, 2f, 0.7f),
            off(8, 14000f),
        ),
    )

    private val PultecSmooth = EQPreset(
        name = "Pultec Smooth",
        description = "Pultec EQP-1A signature: bass boost-and-cut at 100 Hz + 16 kHz silk",
        category = PresetCategory.VINTAGE,
        bands = listOf(
            band(1, EQBandType.LOW_SHELF, 100f, 3f, 0.6f),     // boost
            band(2, EQBandType.BELL, 100f, -2f, 1.5f),          // cut at same freq (Pultec trick)
            off(3, 320f), off(4, 700f), off(5, 1500f), off(6, 3200f),
            band(7, EQBandType.BELL, 8000f, 1f, 0.9f),
            band(8, EQBandType.HIGH_SHELF, 16000f, 4f, 0.5f),
        ),
    )

    private val ApiPunch = EQPreset(
        name = "API Punch",
        description = "Drum-oriented punch — low thump + 5 kHz attack",
        category = PresetCategory.DRUM,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 40f, 0f, 0.707f),
            band(2, EQBandType.BELL, 100f, 3.5f, 1.0f),
            band(3, EQBandType.BELL, 400f, -2f, 1.0f),
            off(4, 700f), off(5, 1500f),
            band(6, EQBandType.BELL, 5000f, 3f, 1.2f),
            band(7, EQBandType.BELL, 8000f, 1.5f, 1.0f),
            off(8, 14000f),
        ),
    )

    private val MassiveLow = EQPreset(
        name = "Massive Low",
        description = "Manley-Massive-Passive-style wide warm low boost",
        category = PresetCategory.VINTAGE,
        bands = listOf(
            band(1, EQBandType.LOW_SHELF, 80f, 4f, 0.5f),
            band(2, EQBandType.BELL, 200f, 1.5f, 0.7f),
            off(3, 320f), off(4, 700f), off(5, 1500f), off(6, 3200f),
            band(7, EQBandType.BELL, 8000f, 1f, 0.7f),
            off(8, 14000f),
        ),
    )

    private val NeveWarmth = EQPreset(
        name = "Neve Warmth",
        description = "1073-style vintage musical curves",
        category = PresetCategory.VINTAGE,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 50f, 0f, 0.707f),
            band(2, EQBandType.LOW_SHELF, 220f, 2.5f, 0.6f),
            band(3, EQBandType.BELL, 700f, -1f, 1.0f),
            band(4, EQBandType.BELL, 1600f, 1.5f, 1.0f),
            off(5, 1500f),
            band(6, EQBandType.BELL, 4000f, 2.5f, 1.2f),
            band(7, EQBandType.HIGH_SHELF, 12000f, 2f, 0.5f),
            off(8, 14000f),
        ),
    )

    private val BroadcastVoice = EQPreset(
        name = "Broadcast Voice",
        description = "High-pass + presence boost for spoken word",
        category = PresetCategory.VOCAL,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 80f, 0f, 0.707f),
            band(2, EQBandType.BELL, 200f, -2f, 1.0f),
            band(3, EQBandType.BELL, 350f, -1.5f, 1.2f),
            off(4, 700f),
            band(5, EQBandType.BELL, 2500f, 2f, 1.0f),
            band(6, EQBandType.BELL, 5000f, 3f, 1.2f),
            band(7, EQBandType.HIGH_SHELF, 8000f, 1.5f, 0.7f),
            off(8, 14000f),
        ),
    )

    private val DeEss = EQPreset(
        name = "De-Ess",
        description = "Narrow 6 kHz notch — sibilance reduction",
        category = PresetCategory.REPAIR,
        bands = listOf(
            off(1, 60f), off(2, 150f), off(3, 320f), off(4, 700f), off(5, 1500f),
            band(6, EQBandType.BELL, 6000f, -5f, 8f),
            band(7, EQBandType.BELL, 8500f, -3f, 6f),
            off(8, 14000f),
        ),
    )

    private val MasterBus = EQPreset(
        name = "Master Bus",
        description = "Gentle mastering polish — 200 Hz dip + high shelf",
        category = PresetCategory.MASTER,
        bands = listOf(
            off(1, 60f),
            band(2, EQBandType.BELL, 200f, -1f, 1.0f),
            off(3, 320f), off(4, 700f), off(5, 1500f),
            band(6, EQBandType.BELL, 3500f, 0.5f, 1.2f),
            band(7, EQBandType.HIGH_SHELF, 10000f, 1f, 0.5f),
            band(8, EQBandType.HIGH_SHELF, 16000f, 1f, 0.5f),
        ),
    )

    val ALL: List<EQPreset> = listOf(
        Flat, AirLift, VintageConsole, PultecSmooth, ApiPunch,
        MassiveLow, NeveWarmth, BroadcastVoice, DeEss, MasterBus,
    )
}
```

- [ ] **Step 4: Run, verify PASS**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.model.EQPresetTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/EQPreset.kt \
        app/src/test/java/com/example/recorderproject/model/EQPresetTest.kt
git commit -m "feat(model): add 10 plugin-emulation EQ presets"
```

---

## Task 12: EQChain JSON serialization (sidecar)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/EQChainJson.kt`
- Test: `app/src/test/java/com/example/recorderproject/model/EQChainJsonTest.kt`

Use `org.json.JSONObject` (built into Android, no extra dependency).

- [ ] **Step 1: Write the test**

```kotlin
package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EQChainJsonTest {

    @Test fun `round trip preserves every field`() {
        val original = EQChain.empty().withBand(
            EQBand(id = 3, type = EQBandType.NOTCH, frequencyHz = 1234.5f, gainDb = -3f, q = 8f,
                enabled = true, soloed = false, muted = true, locked = true)
        ).copy(bypassed = true, gainCompensation = true)

        val json = EQChainJson.toJsonString(original)
        val restored = EQChainJson.fromJsonString(json)
        assertEquals(original, restored)
    }

    @Test fun `corrupt JSON returns null`() {
        assertEquals(null, EQChainJson.fromJsonString("not json"))
        assertEquals(null, EQChainJson.fromJsonString("{\"bands\": [\"oops\"]}"))
    }
}
```

- [ ] **Step 2: Implement `EQChainJson.kt`**

```kotlin
package com.example.recorderproject.model

import org.json.JSONArray
import org.json.JSONObject

object EQChainJson {

    private const val SCHEMA_VERSION = 1

    fun toJsonString(chain: EQChain): String {
        val root = JSONObject()
        root.put("schema", SCHEMA_VERSION)
        root.put("bypassed", chain.bypassed)
        root.put("gainCompensation", chain.gainCompensation)
        val arr = JSONArray()
        for (b in chain.bands) {
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("type", b.type.name)
                put("frequencyHz", b.frequencyHz.toDouble())
                put("gainDb", b.gainDb.toDouble())
                put("q", b.q.toDouble())
                put("enabled", b.enabled)
                put("soloed", b.soloed)
                put("muted", b.muted)
                put("locked", b.locked)
            })
        }
        root.put("bands", arr)
        return root.toString()
    }

    /** Returns null if JSON is malformed or required fields are missing. */
    fun fromJsonString(json: String): EQChain? = try {
        val root = JSONObject(json)
        val arr = root.getJSONArray("bands")
        val bands = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            EQBand(
                id = o.getInt("id"),
                type = EQBandType.valueOf(o.getString("type")),
                frequencyHz = o.getDouble("frequencyHz").toFloat(),
                gainDb = o.getDouble("gainDb").toFloat(),
                q = o.getDouble("q").toFloat(),
                enabled = o.optBoolean("enabled", true),
                soloed = o.optBoolean("soloed", false),
                muted = o.optBoolean("muted", false),
                locked = o.optBoolean("locked", false),
            )
        }
        EQChain(
            bands = bands,
            bypassed = root.optBoolean("bypassed", false),
            gainCompensation = root.optBoolean("gainCompensation", false),
        )
    } catch (e: Exception) {
        null
    }
}
```

- [ ] **Step 3: Run, verify PASS, commit**

```bash
./gradlew testDebugUnitTest --tests "com.example.recorderproject.model.EQChainJsonTest"
git add app/src/main/java/com/example/recorderproject/model/EQChainJson.kt \
        app/src/test/java/com/example/recorderproject/model/EQChainJsonTest.kt
git commit -m "feat(model): add EQChain JSON serialization for sidecar persistence"
```

---

## Task 13: SettingsDataStore extensions

**Goal:** Persist `ApplySaveMode` and `EQViewMode` user preferences.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt`

- [ ] **Step 1: Inspect existing SettingsDataStore**

```bash
cat app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt
```
Note the Preferences DataStore setup. Likely has `Context.dataStore` extension.

- [ ] **Step 2: Add the new keys + flows + setters**

Append to the existing `SettingsDataStore.kt` (place near other key definitions, copy the pattern that's already there). Don't remove existing fields.

```kotlin
// New keys (place inside the existing object/class — adapt to its style)
private val APPLY_SAVE_MODE_KEY = stringPreferencesKey("eq_apply_save_mode")
private val EQ_VIEW_MODE_KEY = stringPreferencesKey("eq_view_mode")

val applySaveModeFlow: Flow<com.example.recorderproject.model.ApplySaveMode> = context.dataStore.data
    .map { prefs ->
        runCatching {
            com.example.recorderproject.model.ApplySaveMode.valueOf(
                prefs[APPLY_SAVE_MODE_KEY] ?: com.example.recorderproject.model.ApplySaveMode.BOTH.name
            )
        }.getOrDefault(com.example.recorderproject.model.ApplySaveMode.BOTH)
    }

suspend fun setApplySaveMode(mode: com.example.recorderproject.model.ApplySaveMode) {
    context.dataStore.edit { it[APPLY_SAVE_MODE_KEY] = mode.name }
}

val eqViewModeFlow: Flow<com.example.recorderproject.model.EQViewMode> = context.dataStore.data
    .map { prefs ->
        runCatching {
            com.example.recorderproject.model.EQViewMode.valueOf(
                prefs[EQ_VIEW_MODE_KEY] ?: com.example.recorderproject.model.EQViewMode.TWO_D.name
            )
        }.getOrDefault(com.example.recorderproject.model.EQViewMode.TWO_D)
    }

suspend fun setEqViewMode(mode: com.example.recorderproject.model.EQViewMode) {
    context.dataStore.edit { it[EQ_VIEW_MODE_KEY] = mode.name }
}
```

(If the existing file is in a top-level singleton-style API rather than class, mirror its pattern.)

- [ ] **Step 3: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt
git commit -m "feat(data): persist ApplySaveMode + EQViewMode in SettingsDataStore"
```

---

## Task 14: RecorderViewModel — EQ state additions

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Add EQ-related imports near top**

```kotlin
import com.example.recorderproject.audio.EQAutoDetect
import com.example.recorderproject.audio.EQCurveFitter
import com.example.recorderproject.audio.EQProcessor
import com.example.recorderproject.audio.SpectrumAnalyzer
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQChainJson
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.model.EQPreset
import com.example.recorderproject.model.EQPresets
import com.example.recorderproject.model.EQViewMode
import androidx.compose.ui.geometry.Offset
```

- [ ] **Step 2: Add EQ state holders inside the `RecorderViewModel` class, after the existing flows**

```kotlin
// ------------- EQ state -------------

private val _currentEQChain = MutableStateFlow(EQChain.empty())
val currentEQChain: StateFlow<EQChain> = _currentEQChain

private val _eqMode = MutableStateFlow(EQEditMode.PARAMETRIC)
val eqMode: StateFlow<EQEditMode> = _eqMode

private val _eqViewMode = MutableStateFlow(EQViewMode.TWO_D)
val eqViewMode: StateFlow<EQViewMode> = _eqViewMode

private val _eqSelectedBandId = MutableStateFlow<Int?>(null)
val eqSelectedBandId: StateFlow<Int?> = _eqSelectedBandId

private val _eqSnapshot = MutableStateFlow<EQChain?>(null)
val eqSnapshot: StateFlow<EQChain?> = _eqSnapshot

private val _eqApplySaveMode = MutableStateFlow(ApplySaveMode.BOTH)
val eqApplySaveMode: StateFlow<ApplySaveMode> = _eqApplySaveMode

private val _eqRenderProgress = MutableStateFlow(-1f)
val eqRenderProgress: StateFlow<Float> = _eqRenderProgress

private val _eqSourceFile = MutableStateFlow<RecordFile?>(null)
val eqSourceFile: StateFlow<RecordFile?> = _eqSourceFile

private val _eqSourceSpectrum = MutableStateFlow<StaticSpectrum?>(null)
val eqSourceSpectrum: StateFlow<StaticSpectrum?> = _eqSourceSpectrum

private val _eqBypassed = MutableStateFlow(false)
val eqBypassed: StateFlow<Boolean> = _eqBypassed

// EQ navigation: set true when an EQ file is opened, MainActivity observes for nav
private val _eqOpen = MutableStateFlow(false)
val eqOpen: StateFlow<Boolean> = _eqOpen

private val eqHistory = ArrayDeque<EQChain>()
private val eqRedo = ArrayDeque<EQChain>()
private val EQ_HISTORY_CAP = 10
```

- [ ] **Step 3: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(viewmodel): add EQ state holders"
```

---

## Task 15: RecorderViewModel — EQ actions

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Add the action functions (continue inside `RecorderViewModel` class)**

```kotlin
// ------------- EQ actions -------------

private fun pushHistory(chain: EQChain) {
    eqHistory.addLast(chain)
    if (eqHistory.size > EQ_HISTORY_CAP) eqHistory.removeFirst()
    eqRedo.clear()
}

fun onEQOpen(file: RecordFile) {
    _eqSourceFile.value = file
    // Load sidecar if present
    val srcPath = file.path
    if (!srcPath.startsWith("content://")) {
        val sidecar = File(srcPath.replace(Regex("\\.wav$", RegexOption.IGNORE_CASE), "_eq.json"))
        if (sidecar.exists()) {
            val restored = EQChainJson.fromJsonString(sidecar.readText())
            _currentEQChain.value = restored ?: EQChain.empty()
        } else {
            _currentEQChain.value = EQChain.empty()
        }
    } else {
        _currentEQChain.value = EQChain.empty()
    }
    eqHistory.clear(); eqRedo.clear()
    _eqOpen.value = true
    // Compute spectrum on IO
    viewModelScope.launch(Dispatchers.IO) {
        if (!srcPath.startsWith("content://")) {
            try {
                val spec = SpectrumAnalyzer.analyzeFile(File(srcPath), bins = 256)
                _eqSourceSpectrum.value = spec
            } catch (e: Exception) {
                Log.e(TAG, "Spectrum compute failed: ${e.message}", e)
                _eqSourceSpectrum.value = null
            }
        } else {
            _eqSourceSpectrum.value = null
        }
    }
}

fun onEQClose() {
    // Autosave sidecar before closing
    _eqSourceFile.value?.let { file ->
        if (!file.path.startsWith("content://")) {
            val sidecar = File(file.path.replace(Regex("\\.wav$", RegexOption.IGNORE_CASE), "_eq.json"))
            try {
                sidecar.writeText(EQChainJson.toJsonString(_currentEQChain.value))
            } catch (e: Exception) {
                Log.w(TAG, "Sidecar autosave failed: ${e.message}")
            }
        }
    }
    _eqOpen.value = false
    _eqSourceSpectrum.value = null
    _eqRenderProgress.value = -1f
}

fun onEQBandChanged(updated: EQBand) {
    pushHistory(_currentEQChain.value)
    _currentEQChain.value = _currentEQChain.value.withBand(updated)
}

fun onEQModeToggle(mode: EQEditMode) { _eqMode.value = mode }

fun onEQViewModeToggle(mode: EQViewMode) { _eqViewMode.value = mode }

fun onEQSelectBand(id: Int?) { _eqSelectedBandId.value = id }

fun onEQUndo() {
    val prev = eqHistory.removeLastOrNull() ?: return
    eqRedo.addLast(_currentEQChain.value)
    _currentEQChain.value = prev
}

fun onEQRedo() {
    val next = eqRedo.removeLastOrNull() ?: return
    eqHistory.addLast(_currentEQChain.value)
    _currentEQChain.value = next
}

fun onEQABToggle() {
    val snap = _eqSnapshot.value
    if (snap == null) {
        _eqSnapshot.value = _currentEQChain.value
    } else {
        val current = _currentEQChain.value
        _currentEQChain.value = snap
        _eqSnapshot.value = current
    }
}

fun onEQResetAll() {
    pushHistory(_currentEQChain.value)
    _currentEQChain.value = EQChain.empty()
}

fun onEQPresetSelected(preset: EQPreset) {
    pushHistory(_currentEQChain.value)
    _currentEQChain.value = EQChain(bands = preset.bands)
}

fun onEQNoiseAutoDetect() {
    val spec = _eqSourceSpectrum.value ?: return
    val suggestions = EQAutoDetect.proposeNotches(spec, maxBands = 4)
    _currentEQChain.value = _currentEQChain.value.copy(noiseCutSuggestions = suggestions)
    if (suggestions.isEmpty()) {
        Toast.makeText(app, "Spectrum is clean — no peaks detected", Toast.LENGTH_SHORT).show()
    }
}

fun onEQAcceptSuggestion(band: EQBand) {
    val current = _currentEQChain.value
    val added = current.withAddedBand(band) ?: run {
        Toast.makeText(app, "8-band limit reached — disable a band first", Toast.LENGTH_SHORT).show()
        return
    }
    pushHistory(current)
    _currentEQChain.value = added.copy(
        noiseCutSuggestions = current.noiseCutSuggestions.filter { it.id != band.id }
    )
}

fun onEQRejectSuggestion(band: EQBand) {
    _currentEQChain.value = _currentEQChain.value.copy(
        noiseCutSuggestions = _currentEQChain.value.noiseCutSuggestions.filter { it.id != band.id }
    )
}

fun onEQDrawCurve(targetDbCurve: FloatArray) {
    val bands = EQCurveFitter.fitToCurve(targetDbCurve, 20f, 20_000f, maxBands = 6)
    if (bands.isEmpty()) return
    pushHistory(_currentEQChain.value)
    // Replace the chain with the fitted bands padded with disabled defaults
    val padded = bands + (bands.size + 1..8).map { EQBand.defaultForSlot(it) }
    _currentEQChain.value = EQChain(bands = padded.take(8))
}

fun onEQTapNotch(frequencyHz: Float) {
    val newBand = EQBand(id = 0, type = EQBandType.NOTCH, frequencyHz = frequencyHz, gainDb = 0f, q = 8f, enabled = true)
    val added = _currentEQChain.value.withAddedBand(newBand) ?: run {
        Toast.makeText(app, "8-band limit reached — disable a band first", Toast.LENGTH_SHORT).show()
        return
    }
    pushHistory(_currentEQChain.value)
    _currentEQChain.value = added
}

fun onEQSaveModeChange(mode: ApplySaveMode) { _eqApplySaveMode.value = mode }

fun onEQApply() {
    val src = _eqSourceFile.value ?: return
    val mode = _eqApplySaveMode.value
    if (mode == ApplySaveMode.ORIGINAL_ONLY) {
        // Discard chain, just close
        _currentEQChain.value = EQChain.empty()
        onEQClose()
        return
    }
    if (src.path.startsWith("content://")) {
        Toast.makeText(app, "SAF (content://) sources not yet supported for Apply — please save to a local folder", Toast.LENGTH_LONG).show()
        return
    }
    val srcFile = File(src.path)
    val eqFile = File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.wav")
    val chain = _currentEQChain.value
    viewModelScope.launch(Dispatchers.IO) {
        _eqRenderProgress.value = 0f
        try {
            EQProcessor.process(srcFile, eqFile, chain) { p ->
                _eqRenderProgress.value = p
            }
            when (mode) {
                ApplySaveMode.BOTH -> {
                    // Sidecar JSON for re-edit
                    File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.json")
                        .writeText(EQChainJson.toJsonString(chain))
                    // Flip hasEQ on the RecordFile
                    _recordFiles.value = _recordFiles.value.map {
                        if (it.id == src.id) it.copy(hasEQ = true) else it
                    }
                }
                ApplySaveMode.EQ_ONLY -> {
                    // Atomic rename eqFile over srcFile
                    val tmpRename = File(srcFile.parentFile, srcFile.name + ".replacing")
                    srcFile.renameTo(tmpRename)
                    if (eqFile.renameTo(srcFile)) {
                        tmpRename.delete()
                    } else {
                        // Rollback
                        tmpRename.renameTo(srcFile)
                        eqFile.delete()
                        throw RuntimeException("Atomic rename failed")
                    }
                    _recordFiles.value = _recordFiles.value.map {
                        if (it.id == src.id) it.copy(hasEQ = true) else it
                    }
                }
                ApplySaveMode.ORIGINAL_ONLY -> Unit // handled above
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "EQ applied", Toast.LENGTH_SHORT).show()
                delay(600)
                _eqRenderProgress.value = -1f
                onEQClose()
            }
        } catch (e: Exception) {
            Log.e(TAG, "EQ render failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "EQ render failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            _eqRenderProgress.value = -1f
            eqFile.delete() // cleanup partial
        }
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(viewmodel): add EQ actions (open/edit/apply/undo/redo/AB/presets/noise-cut)"
```

---

## Task 16: EQCurveView2D — Compose Canvas curve rendering

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/EQCurveView2D.kt`

This component renders the response curve, spectrum hills, draggable handles, and grid in 2D. No tests at this layer — preview-driven dev.

- [ ] **Step 1: Implement `EQCurveView2D.kt`**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.example.recorderproject.audio.BiquadCoeffs
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

private const val MIN_FREQ = 20f
private const val MAX_FREQ = 20_000f
private const val MIN_DB = -18f
private const val MAX_DB = 18f
private const val CURVE_BINS = 256

@Composable
fun EQCurveView2D(
    chain: EQChain,
    spectrum: StaticSpectrum?,
    mode: EQEditMode,
    selectedBandId: Int?,
    sampleRate: Float,
    onHandleDrag: (band: EQBand, newFreqHz: Float, newGainDb: Float) -> Unit,
    onHandleTap: (band: EQBand) -> Unit,
    onTapEmpty: (freqHz: Float) -> Unit,
    onAcceptSuggestion: (band: EQBand) -> Unit,
    onFreeformDraw: (targetDbCurve: FloatArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drawnPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(chain, mode) {
                    if (mode == EQEditMode.NOISE_CUT) {
                        // Freeform draw mode
                        detectDragGestures(
                            onDragStart = { drawnPoints = listOf(it) },
                            onDrag = { change, _ -> drawnPoints = drawnPoints + change.position },
                            onDragEnd = {
                                if (drawnPoints.size >= 3) {
                                    val target = freeformPointsToTargetCurve(drawnPoints, size, CURVE_BINS)
                                    onFreeformDraw(target)
                                }
                                drawnPoints = emptyList()
                            },
                        )
                    } else {
                        // Parametric mode: drag the nearest handle
                        var dragging: EQBand? = null
                        detectDragGestures(
                            onDragStart = { pos ->
                                dragging = nearestHandle(chain, pos, size)
                            },
                            onDrag = { change, _ ->
                                val band = dragging ?: return@detectDragGestures
                                val freq = xToFreq(change.position.x, size.width)
                                val gain = yToDb(change.position.y, size.height)
                                onHandleDrag(band, freq, gain)
                            },
                            onDragEnd = { dragging = null },
                        )
                    }
                }
                .pointerInput(chain, mode) {
                    detectTapGestures(
                        onTap = { pos ->
                            val band = nearestHandle(chain, pos, size)
                            if (band != null) {
                                onHandleTap(band)
                            } else if (mode == EQEditMode.PARAMETRIC) {
                                onTapEmpty(xToFreq(pos.x, size.width))
                            }
                            // Check noise-cut suggestion tap
                            if (mode == EQEditMode.NOISE_CUT) {
                                val sugg = nearestSuggestion(chain, pos, size)
                                if (sugg != null) onAcceptSuggestion(sugg)
                            }
                        },
                    )
                }
        ) {
            drawGrid()
            spectrum?.let { drawSpectrumHills(it) }
            drawResponseCurve(chain, sampleRate)
            drawHandles(chain, selectedBandId)
            if (mode == EQEditMode.NOISE_CUT) {
                drawSuggestions(chain)
                drawFreeformTrail(drawnPoints)
            }
        }
    }
}

// ---------- math + helpers ----------

private fun freqToX(freqHz: Float, width: Float): Float {
    val t = (ln(freqHz.toDouble()) - ln(MIN_FREQ.toDouble())) /
        (ln(MAX_FREQ.toDouble()) - ln(MIN_FREQ.toDouble()))
    return (t.toFloat()).coerceIn(0f, 1f) * width
}

private fun xToFreq(x: Float, width: Float): Float {
    val t = (x / width).coerceIn(0f, 1f).toDouble()
    return exp(ln(MIN_FREQ.toDouble()) + t * (ln(MAX_FREQ.toDouble()) - ln(MIN_FREQ.toDouble()))).toFloat()
}

private fun dbToY(db: Float, height: Float): Float {
    val t = (db.coerceIn(MIN_DB, MAX_DB) - MIN_DB) / (MAX_DB - MIN_DB)
    return (1f - t) * height
}

private fun yToDb(y: Float, height: Float): Float {
    val t = (1f - y / height).coerceIn(0f, 1f)
    return MIN_DB + t * (MAX_DB - MIN_DB)
}

/** Compute combined magnitude (dB) of the chain at log-spaced frequencies. */
private fun computeResponse(chain: EQChain, sampleRate: Float, bins: Int = CURVE_BINS): FloatArray {
    val activeBands = if (chain.bypassed) emptyList() else chain.bands.filter { it.enabled && !it.muted }
    if (activeBands.isEmpty()) return FloatArray(bins) { 0f }

    // Compute |H(e^jw)| analytically per biquad: |H| = sqrt((b0+b1·cos+b2·cos2)^2 + ...)
    val out = FloatArray(bins)
    for (i in 0 until bins) {
        val t = i.toDouble() / (bins - 1)
        val freq = exp(ln(MIN_FREQ.toDouble()) + t * (ln(MAX_FREQ.toDouble()) - ln(MIN_FREQ.toDouble())))
        val w = 2 * Math.PI * freq / sampleRate
        var totalDb = 0.0
        for (band in activeBands) {
            // For TILT, two biquads; for others, one. Use cascadeForBand.
            val biquads = BiquadCoeffs.cascadeForBand(band, sampleRate)
            for (biq in biquads) {
                // We need coefficients — store them as fields you can read. Simplest: re-derive
                // by calling forBand and using a freshly built biquad's coefficient query.
                val coeffs = biquadResponseDb(band, freq.toFloat(), sampleRate)
                totalDb += coeffs
                break // cascadeForBand returns multiple for TILT; we sum dB once per biquad via the helper
            }
        }
        out[i] = totalDb.toFloat().coerceIn(MIN_DB, MAX_DB)
    }
    return out
}

/** Closed-form RBJ biquad magnitude response in dB at frequency f, by band type. */
private fun biquadResponseDb(band: EQBand, f: Float, sr: Float): Double {
    // Use cascadeForBand to handle TILT (2 biquads). We compute |H| by running impulse through
    // the biquad — but that's stateful. For visualization we can use the closed-form coeffs.
    val cascade = BiquadCoeffs.cascadeForBand(band, sr)
    val omega = 2 * Math.PI * f / sr
    var totalDb = 0.0
    for (biq in cascade) {
        // Probe the biquad with a unit impulse, sum response over 256 samples, take DFT bin at f
        // Faster: closed-form |H(e^jw)|^2 = |B(e^jw)|^2 / |A(e^jw)|^2 — but we'd need the coefficients
        // exposed. Add a public getter for testing/visualization purposes.
        val (b0, b1, b2, a1, a2) = biq.coeffs()
        val cosW = kotlin.math.cos(omega)
        val cos2W = kotlin.math.cos(2 * omega)
        val sinW = kotlin.math.sin(omega)
        val sin2W = kotlin.math.sin(2 * omega)
        val numR = b0 + b1 * cosW + b2 * cos2W
        val numI = -b1 * sinW - b2 * sin2W
        val denR = 1.0 + a1 * cosW + a2 * cos2W
        val denI = -a1 * sinW - a2 * sin2W
        val num = sqrt(numR * numR + numI * numI)
        val den = sqrt(denR * denR + denI * denI).coerceAtLeast(1e-12)
        totalDb += 20.0 * log10(num / den)
    }
    return totalDb
}

private fun nearestHandle(chain: EQChain, pos: Offset, size: Size): EQBand? {
    val active = chain.bands.filter { it.enabled }
    var best: EQBand? = null
    var bestDist = Float.MAX_VALUE
    for (b in active) {
        val x = freqToX(b.frequencyHz, size.width)
        val y = dbToY(b.gainDb, size.height)
        val d = (Offset(x, y) - pos).getDistance()
        if (d < 40f && d < bestDist) { bestDist = d; best = b }
    }
    return best
}

private fun nearestSuggestion(chain: EQChain, pos: Offset, size: Size): EQBand? {
    var best: EQBand? = null
    var bestDist = Float.MAX_VALUE
    for (b in chain.noiseCutSuggestions) {
        val x = freqToX(b.frequencyHz, size.width)
        val y = dbToY(-6f, size.height) // suggestions render at -6 dB position
        val d = (Offset(x, y) - pos).getDistance()
        if (d < 40f && d < bestDist) { bestDist = d; best = b }
    }
    return best
}

private fun freeformPointsToTargetCurve(points: List<Offset>, size: Size, bins: Int): FloatArray {
    // Convert touch points to (logFreq → dB) target curve at log-spaced bins
    val sorted = points.sortedBy { it.x }
    val xs = sorted.map { xToFreq(it.x, size.width) }
    val ys = sorted.map { yToDb(it.y, size.height) }
    val out = FloatArray(bins)
    for (i in 0 until bins) {
        val t = i.toDouble() / (bins - 1)
        val freq = exp(ln(MIN_FREQ.toDouble()) + t * (ln(MAX_FREQ.toDouble()) - ln(MIN_FREQ.toDouble()))).toFloat()
        // Find nearest two control points and linear-interp in log freq
        val ix = xs.indexOfFirst { it >= freq }
        out[i] = when {
            ix <= 0 -> ys.firstOrNull() ?: 0f
            ix < 0 -> ys.lastOrNull() ?: 0f
            else -> {
                val xLo = xs[ix - 1]; val xHi = xs[ix]
                val yLo = ys[ix - 1]; val yHi = ys[ix]
                val frac = ((ln(freq.toDouble()) - ln(xLo.toDouble())) / (ln(xHi.toDouble()) - ln(xLo.toDouble()))).toFloat()
                yLo + frac * (yHi - yLo)
            }
        }
    }
    return out
}

// ---------- drawing ----------

private fun DrawScope.drawGrid() {
    val w = size.width; val h = size.height
    val gridColor = RecorderBlueGrey.copy(alpha = 0.3f)
    // 0 dB line
    drawLine(gridColor, Offset(0f, h / 2), Offset(w, h / 2), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
    // ±6, ±12 lines
    for (db in listOf(-12f, -6f, 6f, 12f)) {
        val y = dbToY(db, h)
        drawLine(gridColor.copy(alpha = 0.15f), Offset(0f, y), Offset(w, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 8f)))
    }
    // Vertical decade lines: 100 Hz, 1 kHz, 10 kHz
    for (f in listOf(100f, 1000f, 10_000f)) {
        val x = freqToX(f, w)
        drawLine(gridColor.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, h), 1f)
    }
}

private fun DrawScope.drawSpectrumHills(spectrum: StaticSpectrum) {
    val w = size.width; val h = size.height
    val mag = spectrum.magnitudeDb
    val path = Path()
    path.moveTo(0f, h)
    for (i in mag.indices) {
        val x = i.toFloat() / (mag.size - 1) * w
        // map magnitude dB to height: louder = taller. Range roughly -80..0 dB.
        val normalized = ((mag[i] + 80f) / 80f).coerceIn(0f, 1f)
        val y = h - normalized * (h * 0.55f)
        path.lineTo(x, y)
    }
    path.lineTo(w, h); path.close()
    drawPath(path, color = RecorderBlueGrey.copy(alpha = 0.18f))
}

private fun DrawScope.drawResponseCurve(chain: EQChain, sampleRate: Float) {
    val w = size.width; val h = size.height
    val response = computeResponse(chain, sampleRate)
    val path = Path()
    path.moveTo(0f, dbToY(response[0], h))
    for (i in 1 until response.size) {
        val x = i.toFloat() / (response.size - 1) * w
        val y = dbToY(response[i], h)
        path.lineTo(x, y)
    }
    drawPath(path, color = RecorderOrange, style = Stroke(width = 6f))
    drawPath(path, color = RecorderOrange.copy(alpha = 0.85f), style = Stroke(width = 3f))
}

private fun DrawScope.drawHandles(chain: EQChain, selectedBandId: Int?) {
    val w = size.width; val h = size.height
    for (band in chain.bands.filter { it.enabled }) {
        val x = freqToX(band.frequencyHz, w)
        val y = dbToY(band.gainDb, h)
        val radius = if (band.id == selectedBandId) 14f else 11f
        drawCircle(RecorderYellow, radius, Offset(x, y))
        drawCircle(Color(0xFF0C0C10), radius - 4f, Offset(x, y))
        drawCircle(RecorderYellow, radius - 7f, Offset(x, y))
    }
}

private fun DrawScope.drawSuggestions(chain: EQChain) {
    val w = size.width; val h = size.height
    for (band in chain.noiseCutSuggestions) {
        val x = freqToX(band.frequencyHz, w)
        val y = dbToY(-6f, h)
        drawCircle(RecorderOrange.copy(alpha = 0.5f), 11f, Offset(x, y))
        drawCircle(Color(0xFF0C0C10), 7f, Offset(x, y))
    }
}

private fun DrawScope.drawFreeformTrail(points: List<Offset>) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    drawPath(path, color = RecorderOrange.copy(alpha = 0.7f), style = Stroke(width = 5f))
}

// Helper: destructure Biquad coefficients (requires exposing a getter — added in Step 2 below)
private operator fun com.example.recorderproject.audio.Biquad.coeffs(): DoubleArray = TODO()
```

- [ ] **Step 2: Expose Biquad coefficients (for closed-form magnitude response)**

The 2D curve drawer needs the coefficients. Add a getter to `Biquad.kt`:

Replace the `Biquad` class declaration with:

```kotlin
class Biquad(
    private var b0: Double,
    private var b1: Double,
    private var b2: Double,
    private var a1: Double,
    private var a2: Double,
) {
    private var s1 = 0.0
    private var s2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + s1
        s1 = b1 * x - a1 * y + s2
        s2 = b2 * x - a2 * y
        return y
    }

    fun reset() { s1 = 0.0; s2 = 0.0 }

    fun setCoefficients(b0: Double, b1: Double, b2: Double, a1: Double, a2: Double) {
        this.b0 = b0; this.b1 = b1; this.b2 = b2; this.a1 = a1; this.a2 = a2
    }

    /** Returns [b0, b1, b2, a1, a2] for closed-form frequency response calculations. */
    fun coeffs(): DoubleArray = doubleArrayOf(b0, b1, b2, a1, a2)
}
```

Now the `private operator fun ... coeffs(): DoubleArray = TODO()` line at the bottom of `EQCurveView2D.kt` becomes a regular call to `Biquad.coeffs()`. Replace that line with:

```kotlin
private operator fun com.example.recorderproject.audio.Biquad.component1(): DoubleArray = coeffs()
// destructure into (b0,b1,b2,a1,a2)
```

Actually simpler: change the destructured access in `biquadResponseDb` from `val (b0, b1, b2, a1, a2) = biq.coeffs()` to direct indexing:

```kotlin
val c = biq.coeffs()
val b0 = c[0]; val b1 = c[1]; val b2 = c[2]; val a1 = c[3]; val a2 = c[4]
```

And delete the trailing `private operator fun ... coeffs(): DoubleArray = TODO()` line.

- [ ] **Step 3: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/audio/Biquad.kt \
        app/src/main/java/com/example/recorderproject/ui/components/EQCurveView2D.kt
git commit -m "feat(ui): add EQCurveView2D Compose canvas (curve + spectrum + handles + freeform)"
```

---

## Task 17: EQCurveView3D — 3D spectrogram landscape

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/EQCurveView3D.kt`

A simplified 3D spectrogram: receding parallel slices in blue-grey with the orange EQ response curve on the front edge. Uses manual perspective projection. No new dependency.

- [ ] **Step 1: Implement `EQCurveView3D.kt`**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@Composable
fun EQCurveView3D(
    chain: EQChain,
    /** Snapshots of spectrum bins over recent time (newest last). Phase 1 ships static = single slice repeated. */
    historicalSlices: List<StaticSpectrum>,
    sampleRate: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawLandscape(historicalSlices)
        drawFrontEQCurve(chain, sampleRate)
        drawHandleColumns(chain)
    }
}

private fun DrawScope.drawLandscape(slices: List<StaticSpectrum>) {
    if (slices.isEmpty()) return
    val w = size.width; val h = size.height
    val depthCount = slices.size
    // Perspective: each slice is rendered at a y-offset that shrinks horizontally as depth increases
    for ((depthIdx, slice) in slices.withIndex()) {
        val z = depthIdx.toFloat() / (depthCount - 1).coerceAtLeast(1) // 0 = front, 1 = back
        val shrink = 1f - z * 0.7f
        val yOffset = h * 0.1f + z * h * 0.4f
        val widthAtDepth = w * shrink
        val xPad = (w - widthAtDepth) / 2f
        val opacity = (1f - z * 0.6f) * 0.7f

        val path = Path()
        for (i in slice.magnitudeDb.indices) {
            val t = i.toFloat() / (slice.magnitudeDb.size - 1)
            val x = xPad + t * widthAtDepth
            val normalized = ((slice.magnitudeDb[i] + 80f) / 80f).coerceIn(0f, 1f)
            val maxHeight = (h * 0.4f) * (1f - z * 0.4f)
            val y = yOffset + (1f - normalized) * maxHeight
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = RecorderBlueGrey.copy(alpha = opacity), style = Stroke(width = 1.2f - z * 0.3f))
    }
}

private fun DrawScope.drawFrontEQCurve(chain: EQChain, sampleRate: Float) {
    val w = size.width; val h = size.height
    // Reuse 2D computeResponse but render along the front edge
    val response = computeResponse(chain, sampleRate)
    val path = Path()
    val baseline = h * 0.85f
    val ampPerDb = h * 0.025f
    for (i in response.indices) {
        val x = i.toFloat() / (response.size - 1) * w
        val y = baseline - response[i] * ampPerDb
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = RecorderOrange.copy(alpha = 0.3f), style = Stroke(width = 12f))
    drawPath(path, color = RecorderOrange, style = Stroke(width = 4f))
}

private fun DrawScope.drawHandleColumns(chain: EQChain) {
    val w = size.width; val h = size.height
    val baseline = h * 0.85f
    val ampPerDb = h * 0.025f
    for (band in chain.bands.filter { it.enabled }) {
        val x = freqToX(band.frequencyHz, w)
        val y = baseline - band.gainDb * ampPerDb
        // Yellow column from handle down to landscape baseline
        drawLine(RecorderYellow.copy(alpha = 0.4f), Offset(x, y), Offset(x, h * 0.95f), 3f)
        drawCircle(RecorderYellow, 12f, Offset(x, y))
        drawCircle(Color(0xFF0C0C10), 7f, Offset(x, y))
    }
}

// Local copies of frequency-domain helpers (kept private so the 2D file can be edited independently)
private fun freqToX(freqHz: Float, width: Float): Float {
    val t = (kotlin.math.ln(freqHz.toDouble()) - kotlin.math.ln(20.0)) /
        (kotlin.math.ln(20_000.0) - kotlin.math.ln(20.0))
    return t.toFloat().coerceIn(0f, 1f) * width
}

private fun computeResponse(chain: EQChain, sampleRate: Float, bins: Int = 256): FloatArray {
    val activeBands = if (chain.bypassed) emptyList() else chain.bands.filter { it.enabled && !it.muted }
    if (activeBands.isEmpty()) return FloatArray(bins) { 0f }
    val out = FloatArray(bins)
    for (i in 0 until bins) {
        val t = i.toDouble() / (bins - 1)
        val freq = kotlin.math.exp(kotlin.math.ln(20.0) + t * (kotlin.math.ln(20_000.0) - kotlin.math.ln(20.0)))
        var totalDb = 0.0
        for (band in activeBands) {
            val cascade = com.example.recorderproject.audio.BiquadCoeffs.cascadeForBand(band, sampleRate)
            val omega = 2 * Math.PI * freq / sampleRate
            for (biq in cascade) {
                val c = biq.coeffs()
                val cosW = kotlin.math.cos(omega); val sinW = kotlin.math.sin(omega)
                val cos2W = kotlin.math.cos(2 * omega); val sin2W = kotlin.math.sin(2 * omega)
                val numR = c[0] + c[1] * cosW + c[2] * cos2W
                val numI = -c[1] * sinW - c[2] * sin2W
                val denR = 1.0 + c[3] * cosW + c[4] * cos2W
                val denI = -c[3] * sinW - c[4] * sin2W
                val num = kotlin.math.sqrt(numR * numR + numI * numI)
                val den = kotlin.math.sqrt(denR * denR + denI * denI).coerceAtLeast(1e-12)
                totalDb += 20.0 * kotlin.math.log10(num / den)
            }
        }
        out[i] = totalDb.toFloat().coerceIn(-18f, 18f)
    }
    return out
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/ui/components/EQCurveView3D.kt
git commit -m "feat(ui): add EQCurveView3D spectrogram-landscape mode"
```

---

## Task 18: EQCurveView dispatcher + ViewModeToggle

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/EQCurveView.kt`
- Create: `app/src/main/java/com/example/recorderproject/ui/components/ViewModeToggle.kt`

- [ ] **Step 1: Implement the dispatcher**

```kotlin
// EQCurveView.kt
package com.example.recorderproject.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.model.EQViewMode

@Composable
fun EQCurveView(
    chain: EQChain,
    spectrum: StaticSpectrum?,
    mode: EQEditMode,
    viewMode: EQViewMode,
    selectedBandId: Int?,
    sampleRate: Float,
    onHandleDrag: (EQBand, Float, Float) -> Unit,
    onHandleTap: (EQBand) -> Unit,
    onTapEmpty: (Float) -> Unit,
    onAcceptSuggestion: (EQBand) -> Unit,
    onFreeformDraw: (FloatArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (viewMode) {
        EQViewMode.TWO_D -> EQCurveView2D(
            chain = chain,
            spectrum = spectrum,
            mode = mode,
            selectedBandId = selectedBandId,
            sampleRate = sampleRate,
            onHandleDrag = onHandleDrag,
            onHandleTap = onHandleTap,
            onTapEmpty = onTapEmpty,
            onAcceptSuggestion = onAcceptSuggestion,
            onFreeformDraw = onFreeformDraw,
            modifier = modifier,
        )
        EQViewMode.THREE_D -> EQCurveView3D(
            chain = chain,
            historicalSlices = listOfNotNull(spectrum), // Phase 1: single slice; Phase 5 expands to true waterfall
            sampleRate = sampleRate,
            modifier = modifier,
        )
    }
}
```

- [ ] **Step 2: Implement ViewModeToggle**

```kotlin
// ViewModeToggle.kt
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.EQViewMode
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange

@Composable
fun ViewModeToggle(
    current: EQViewMode,
    onChange: (EQViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C0C10))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mode in EQViewMode.values()) {
            val active = mode == current
            Text(
                text = if (mode == EQViewMode.TWO_D) "2D" else "3D",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .clickable { onChange(mode) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
```

- [ ] **Step 3: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/ui/components/EQCurveView.kt \
        app/src/main/java/com/example/recorderproject/ui/components/ViewModeToggle.kt
git commit -m "feat(ui): add EQCurveView dispatcher + ViewModeToggle (2D/3D)"
```

---

## Task 19: EQBandSheet — per-band sliders

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/EQBandSheet.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.exp
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQBandSheet(
    band: EQBand,
    onChange: (EQBand) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF161618)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Band ${band.id}", color = RecorderYellow, fontWeight = FontWeight.SemiBold)

            // Filter type
            TypePicker(band.type, onPicked = { onChange(band.copy(type = it)) })

            // Frequency (log-mapped slider)
            ParamSlider(
                label = "Frequency",
                value = freqToSliderT(band.frequencyHz),
                onChange = { onChange(band.copy(frequencyHz = sliderTToFreq(it))) },
                readout = formatHz(band.frequencyHz),
            )

            // Gain
            ParamSlider(
                label = "Gain",
                value = (band.gainDb + 24f) / 48f,
                onChange = { onChange(band.copy(gainDb = it * 48f - 24f)) },
                readout = String.format("%+.1f dB", band.gainDb),
            )

            // Q
            ParamSlider(
                label = "Q",
                value = (band.q - 0.1f) / 17.9f,
                onChange = { onChange(band.copy(q = 0.1f + it * 17.9f)) },
                readout = String.format("%.2f", band.q),
            )

            // Toggles
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Enabled", band.enabled) { onChange(band.copy(enabled = !band.enabled)) }
                ToggleChip("Mute", band.muted) { onChange(band.copy(muted = !band.muted)) }
                ToggleChip("Solo", band.soloed) { onChange(band.copy(soloed = !band.soloed)) }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", color = RecorderOrange)
            }
        }
    }
}

@Composable
private fun TypePicker(current: EQBandType, onPicked: (EQBandType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Type", color = RecorderBlueGrey, modifier = Modifier.padding(end = 12.dp))
        TextButton(onClick = { expanded = true }) {
            Text(current.displayName, color = RecorderYellow)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (t in EQBandType.values()) {
                DropdownMenuItem(text = { Text(t.displayName) }, onClick = { onPicked(t); expanded = false })
            }
        }
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, onChange: (Float) -> Unit, readout: String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = RecorderBlueGrey)
            Text(readout, color = RecorderYellow)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = RecorderYellow,
                activeTrackColor = RecorderOrange,
                inactiveTrackColor = RecorderBlueGrey.copy(alpha = 0.4f),
            ),
        )
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onTap: () -> Unit) {
    Text(
        text = label,
        color = if (on) Color.White else RecorderBlueGrey,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) RecorderOrange else Color(0xFF0C0C10))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onTap),
    )
}

private fun freqToSliderT(f: Float): Float {
    val t = (ln(f.toDouble()) - ln(20.0)) / (ln(20_000.0) - ln(20.0))
    return t.toFloat().coerceIn(0f, 1f)
}
private fun sliderTToFreq(t: Float): Float =
    exp(ln(20.0) + t.toDouble() * (ln(20_000.0) - ln(20.0))).toFloat()

private fun formatHz(f: Float): String = when {
    f >= 1000f -> String.format("%.1f kHz", f / 1000f)
    else -> String.format("%.0f Hz", f)
}

// Bring in the Modifier.clickable used above
private fun androidx.compose.ui.Modifier.clickable(onClick: () -> Unit) = this.then(
    androidx.compose.foundation.clickable { onClick() }
)
```

(Tiny fix: replace the bottom `private fun Modifier.clickable` extension with the standard import `import androidx.compose.foundation.clickable` and remove the helper — it's there to make the snippet self-contained but the standard import is cleaner.)

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/ui/components/EQBandSheet.kt
git commit -m "feat(ui): add EQBandSheet per-band sliders bottom sheet"
```

---

## Task 20: NoiseCutPanel — analyze button + suggestion list

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/NoiseCutPanel.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@Composable
fun NoiseCutPanel(
    chain: EQChain,
    onAnalyze: () -> Unit,
    onAccept: (EQBand) -> Unit,
    onReject: (EQBand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAnalyze,
                colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
                modifier = Modifier.weight(1f),
            ) { Text("Analyze") }
            Button(
                onClick = { /* freeform draw is initiated on the curve itself in noise-cut mode */ },
                colors = ButtonDefaults.outlinedButtonColors(),
                modifier = Modifier.weight(1f),
                enabled = false,
            ) { Text("Drawing on curve…", color = RecorderBlueGrey) }
        }

        if (chain.noiseCutSuggestions.isEmpty()) {
            Text("Tap Analyze to scan the spectrum for noise peaks, or draw a curve directly on the graph.",
                color = RecorderBlueGrey)
        } else {
            Text("Suggestions (${chain.noiseCutSuggestions.size}):", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
            for (band in chain.noiseCutSuggestions) {
                SuggestionRow(band, onAccept = { onAccept(band) }, onReject = { onReject(band) })
            }
        }
    }
}

@Composable
private fun SuggestionRow(band: EQBand, onAccept: () -> Unit, onReject: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0C0C10))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Notch @ ${formatHz(band.frequencyHz)} · Q ${String.format("%.1f", band.q)}",
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reject", color = RecorderBlueGrey,
                modifier = Modifier.clickable { onReject() })
            Text("Accept", color = RecorderOrange, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onAccept() })
        }
    }
}

private fun formatHz(f: Float): String = when {
    f >= 1000f -> String.format("%.1f kHz", f / 1000f)
    else -> String.format("%.0f Hz", f)
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/ui/components/NoiseCutPanel.kt
git commit -m "feat(ui): add NoiseCutPanel (analyze + suggestion accept/reject)"
```

---

## Task 21: EQPresetPicker — bottom-sheet preset browser

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/EQPresetPicker.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.EQPreset
import com.example.recorderproject.model.EQPresets
import com.example.recorderproject.model.PresetCategory
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQPresetPicker(onPick: (EQPreset) -> Unit, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf<PresetCategory?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color(0xFF161618)) {
        Column(Modifier.padding(20.dp)) {
            Text("Presets", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                CategoryChip("All", category == null) { category = null }
                for (c in PresetCategory.values()) {
                    CategoryChip(c.name, category == c) { category = c }
                }
            }
            val filtered = EQPresets.ALL.filter { category == null || it.category == category }
            LazyColumn {
                items(filtered) { preset ->
                    PresetRow(preset, onPick = { onPick(preset); onDismiss() })
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) Color.White else RecorderBlueGrey,
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) RecorderOrange else Color(0xFF0C0C10))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun PresetRow(preset: EQPreset, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0C0C10))
            .clickable(onClick = onPick)
            .padding(12.dp),
    ) {
        Text(preset.name, color = RecorderYellow, fontWeight = FontWeight.SemiBold)
        Text(preset.description, color = RecorderBlueGrey)
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/ui/components/EQPresetPicker.kt
git commit -m "feat(ui): add EQPresetPicker (category tabs + 10 presets)"
```

---

## Task 22: SaveModeSelector

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/SaveModeSelector.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange

@Composable
fun SaveModeSelector(
    current: ApplySaveMode,
    onChange: (ApplySaveMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0C0C10))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mode in ApplySaveMode.values()) {
            val active = mode == current
            Text(
                text = when (mode) {
                    ApplySaveMode.BOTH -> "Keep both"
                    ApplySaveMode.EQ_ONLY -> "Replace"
                    ApplySaveMode.ORIGINAL_ONLY -> "Discard EQ"
                },
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .clickable { onChange(mode) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/ui/components/SaveModeSelector.kt
git commit -m "feat(ui): add SaveModeSelector (Keep both / Replace / Discard EQ)"
```

---

## Task 23: ApplyButton — animated press → progress ring → check

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/ApplyButton.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.ui.theme.RecorderOrange

@Composable
fun ApplyButton(
    saveMode: ApplySaveMode,
    /** -1 = idle, 0..1 = rendering, exactly 1f shows checkmark briefly. */
    progress: Float,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRendering = progress in 0f..1f
    val scaleTarget = if (isRendering) 0.96f else 1f
    val scale by animateFloatAsState(scaleTarget, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "applyScale")

    val label = when {
        progress in 0f..0.99f -> "Rendering…"
        progress >= 1f -> "Applied"
        saveMode == ApplySaveMode.BOTH -> "Apply · save copy"
        saveMode == ApplySaveMode.EQ_ONLY -> "Apply · replace original"
        saveMode == ApplySaveMode.ORIGINAL_ONLY -> "Discard EQ"
        else -> "Apply"
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(RecorderOrange)
            .clickable(enabled = !isRendering, onClick = onApply)
            .size(width = 200.dp, height = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isRendering && progress < 1f) {
            Canvas(Modifier.size(28.dp)) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(width = 3f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height),
                )
            }
        } else if (progress >= 1f) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
        } else {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/ui/components/ApplyButton.kt
git commit -m "feat(ui): add animated ApplyButton (press → progress ring → check)"
```

---

## Task 24: EQScreen — assemble everything

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/EQScreen.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.example.recorderproject.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.ui.components.ApplyButton
import com.example.recorderproject.ui.components.EQBandSheet
import com.example.recorderproject.ui.components.EQCurveView
import com.example.recorderproject.ui.components.EQPresetPicker
import com.example.recorderproject.ui.components.NoiseCutPanel
import com.example.recorderproject.ui.components.SaveModeSelector
import com.example.recorderproject.ui.components.ViewModeToggle
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQScreen(viewModel: RecorderViewModel, onBack: () -> Unit) {
    val chain by viewModel.currentEQChain.collectAsStateWithLifecycle()
    val mode by viewModel.eqMode.collectAsStateWithLifecycle()
    val viewMode by viewModel.eqViewMode.collectAsStateWithLifecycle()
    val selectedBandId by viewModel.eqSelectedBandId.collectAsStateWithLifecycle()
    val sourceFile by viewModel.eqSourceFile.collectAsStateWithLifecycle()
    val spectrum by viewModel.eqSourceSpectrum.collectAsStateWithLifecycle()
    val saveMode by viewModel.eqApplySaveMode.collectAsStateWithLifecycle()
    val renderProgress by viewModel.eqRenderProgress.collectAsStateWithLifecycle()
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()

    var bandSheetBand by remember { mutableStateOf<EQBand?>(null) }
    var presetPickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sourceFile?.name ?: "EQ", color = RecorderYellow) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEQClose(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEQUndo() }) { Icon(Icons.Default.Undo, contentDescription = "Undo", tint = RecorderBlueGrey) }
                    IconButton(onClick = { viewModel.onEQRedo() }) { Icon(Icons.Default.Redo, contentDescription = "Redo", tint = RecorderBlueGrey) }
                    IconButton(onClick = { viewModel.onEQABToggle() }) { Icon(Icons.Default.Compare, contentDescription = "A/B", tint = RecorderBlueGrey) }
                    IconButton(onClick = { viewModel.onEQResetAll() }) { Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = RecorderBlueGrey) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Mode + view-mode row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeSegmented(mode, viewModel::onEQModeToggle, Modifier.weight(1f))
                ViewModeToggle(viewMode, viewModel::onEQViewModeToggle)
            }

            // Curve view — ~55% of remaining height
            Box(Modifier.fillMaxWidth().aspectRatio(1.05f)) {
                EQCurveView(
                    chain = chain,
                    spectrum = spectrum,
                    mode = mode,
                    viewMode = viewMode,
                    selectedBandId = selectedBandId,
                    sampleRate = sampleRate.toFloat(),
                    onHandleDrag = { band, freq, gain ->
                        viewModel.onEQBandChanged(band.copy(frequencyHz = freq, gainDb = gain))
                    },
                    onHandleTap = { band ->
                        viewModel.onEQSelectBand(band.id)
                        bandSheetBand = band
                    },
                    onTapEmpty = { freq -> viewModel.onEQTapNotch(freq) },
                    onAcceptSuggestion = viewModel::onEQAcceptSuggestion,
                    onFreeformDraw = viewModel::onEQDrawCurve,
                )
            }

            // Parametric / Noise-Cut content
            AnimatedContent(
                targetState = mode,
                transitionSpec = { (fadeIn() + slideInHorizontally()) togetherWith (fadeOut() + slideOutHorizontally()) },
                label = "modeContent",
            ) { m ->
                when (m) {
                    EQEditMode.PARAMETRIC -> BandChipsStrip(chain.bands, selectedBandId, onChipTap = {
                        viewModel.onEQSelectBand(it.id); bandSheetBand = it
                    })
                    EQEditMode.NOISE_CUT -> NoiseCutPanel(
                        chain = chain,
                        onAnalyze = viewModel::onEQNoiseAutoDetect,
                        onAccept = viewModel::onEQAcceptSuggestion,
                        onReject = viewModel::onEQRejectSuggestion,
                    )
                }
            }

            SaveModeSelector(saveMode, viewModel::onEQSaveModeChange)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161618))
                    .clickable { presetPickerOpen = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("PRESET", color = RecorderBlueGrey, fontWeight = FontWeight.Normal)
                    Text("Browse → ", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
                }
                ApplyButton(saveMode, renderProgress, onApply = viewModel::onEQApply)
            }
        }
    }

    bandSheetBand?.let { band ->
        EQBandSheet(
            band = band,
            onChange = { updated -> viewModel.onEQBandChanged(updated); bandSheetBand = updated },
            onDismiss = { bandSheetBand = null; viewModel.onEQSelectBand(null) },
        )
    }

    if (presetPickerOpen) {
        EQPresetPicker(
            onPick = { preset -> viewModel.onEQPresetSelected(preset); presetPickerOpen = false },
            onDismiss = { presetPickerOpen = false },
        )
    }
}

@Composable
private fun ModeSegmented(current: EQEditMode, onChange: (EQEditMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C0C10))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (m in EQEditMode.values()) {
            val active = m == current
            Text(
                text = if (m == EQEditMode.PARAMETRIC) "Parametric" else "Noise Cut",
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .clickable { onChange(m) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun BandChipsStrip(bands: List<EQBand>, selectedId: Int?, onChipTap: (EQBand) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(bands) { band ->
            val active = band.enabled
            val selected = band.id == selectedId
            Text(
                text = band.id.toString(),
                color = if (active) Color(0xFF0C0C10) else RecorderBlueGrey,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) RecorderYellow else Color(0xFF1F1F23))
                    .clickable { onChipTap(band) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/recorderproject/ui/EQScreen.kt
git commit -m "feat(ui): assemble EQScreen (curve + bands + noise-cut + presets + apply)"
```

---

## Task 25: RecorderApp button + MainActivity navigation

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt`
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt`

- [ ] **Step 1: Add the button to RecorderApp.kt**

Replace the existing debug-shell `Column` with a version that includes the EQ launch button:

```kotlin
package com.example.recorderproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.RecorderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderApp(
    viewModel: RecorderViewModel,
    onStartRecording: () -> Unit,
    onSelectSaveLocation: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenEQOnLast: () -> Unit,
) {
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val files by viewModel.recordFiles.collectAsStateWithLifecycle()
    val fileName by viewModel.fileName.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("MEATrec — recovery build") }) }) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Recording: $fileName")
            Text(if (isRecording) "● REC" else "Idle")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStartRecording) { Text(if (isRecording) "Stop" else "Record") }
            OutlinedButton(onClick = onSelectSaveLocation) { Text("Choose save folder") }
            Spacer(Modifier.height(8.dp))
            Text("Recordings: ${files.size}")
            OutlinedButton(onClick = onOpenEQOnLast, enabled = files.isNotEmpty()) {
                Text("Open EQ on last recording")
            }
        }
    }
}
```

- [ ] **Step 2: Wire navigation in MainActivity.kt**

Inspect MainActivity first:
```bash
cat app/src/main/java/com/example/recorderproject/MainActivity.kt
```

Add an `eqOpen` state observer and conditionally show `EQScreen`. Modify the `setContent { ... }` block (approximate — adapt to actual code):

```kotlin
setContent {
    RecorderProjectTheme {
        val eqOpen by viewModel.eqOpen.collectAsStateWithLifecycle()
        if (eqOpen) {
            EQScreen(viewModel = viewModel, onBack = { /* eqOpen flips false in onEQClose */ })
        } else {
            RecorderApp(
                viewModel = viewModel,
                onStartRecording = ::handleRecordTap,        // or whatever the existing handler is
                onSelectSaveLocation = ::launchDirectoryPicker,
                onRequestPermission = ::requestRecordPermission,
                onOpenEQOnLast = {
                    val last = viewModel.recordFiles.value.lastOrNull()
                    if (last != null) viewModel.onEQOpen(last)
                },
            )
        }
    }
}
```

Adapt the handler names to match what's already there.

- [ ] **Step 3: Build and verify the APK assembles**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL, new APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt \
        app/src/main/java/com/example/recorderproject/MainActivity.kt
git commit -m "feat(app): wire EQ entry button + navigation in debug shell"
```

---

## Task 26: End-to-end smoke test on device

- [ ] **Step 1: Install the APK on a connected device**

```bash
./gradlew installDebug
```

- [ ] **Step 2: Walk through the happy path manually**

1. Open MEATrec on device.
2. Grant audio permission.
3. Choose a save folder (use device internal storage, not SAF for this smoke).
4. Record a 5–10 s sample of voice or music.
5. Tap **Open EQ on last recording**.
6. EQ screen opens, source filename in app bar, spectrum hills should appear within ~1 s.
7. Tap a band chip (1) — band sheet appears with sliders. Set freq ≈ 200 Hz, gain +6 dB.
8. Close the sheet — handle should appear on the curve at (200 Hz, +6 dB).
9. Drag the handle — curve recomputes smoothly with spring animation.
10. Tap **Preset → Browse** → pick **Pultec Smooth** — every band morphs into place.
11. Tap **Noise Cut** mode tab → tap **Analyze** → if peaks exist, suggestions appear; tap one to accept.
12. Switch back to **Parametric**, switch view-mode toggle to **3D** — landscape renders, curve shows on the front edge.
13. Switch back to **2D**.
14. Pick **Keep both** save mode.
15. Tap **Apply · save copy** — button shows progress ring, then check.
16. EQ screen closes back to debug shell.
17. Verify the file `~/your-save-folder/<base>_eq.wav` exists and plays back through your music app.

If any step fails, note the failure and file an issue against the specific task that introduced it.

- [ ] **Step 3: Quick on-device sanity: 8-band limit toast**

In a fresh open of the EQ screen, enable all 8 bands via the band sheets, then in Parametric mode tap an empty area of the curve — a toast should say "8-band limit reached — disable a band first".

- [ ] **Step 4: Run the JVM test suite once more for green-bar confidence**

```bash
./gradlew testDebugUnitTest
```
Expected: 7 test classes, all passing (BiquadTest, BiquadCoeffsTest, EQChainTest, EQChainJsonTest, EQPresetTest, EQProcessorTest, SpectrumAnalyzerTest, EQAutoDetectTest, EQCurveFitterTest, WavIoTest).

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit --allow-empty -m "chore: Phase 1 Live Graph EQ MVP complete"
```

---

## Self-review (post-plan)

Engineer should verify against the spec at `/Users/meatball_mac/RECORDER_PROJECT/docs/superpowers/specs/2026-05-25-live-graph-eq-design.md`:

**Spec coverage check** — every spec section maps to at least one task:
- Brand palette → Task 1 ✓
- 2D / 3D view modes → Tasks 16, 17, 18 ✓
- Motion design → embedded in Tasks 16, 17, 23, 24 ✓
- Architecture (pure-Kotlin, biquad, no JNI) → Tasks 4, 5, 7 ✓
- Data model (EQBand, EQChain, EQPreset, enums) → Tasks 2, 3, 11, 12 ✓
- Save mode → Task 22 + Task 15 (action) ✓
- Audio pipeline (EQProcessor, SpectrumAnalyzer, EQAutoDetect, EQCurveFitter) → Tasks 7, 8, 9, 10 ✓
- UI (EQScreen + components) → Tasks 16–24 ✓
- ViewModel additions → Tasks 14, 15 ✓
- Entry point (debug-shell button) → Task 25 ✓
- Data flow → Task 15 covers actions; Task 24 wires composables ✓
- Error handling table → Task 15 (Apply error catch, toast, rollback) + Task 5 (header parse) ✓
- Persistence (sidecar JSON + DataStore) → Tasks 12, 13, 15 ✓
- Testing → embedded in each math-layer task; manual smoke = Task 26 ✓

**Risks the engineer should know about:**

1. The 3D mode in Phase 1 renders a single "current" spectrum slice as a stand-in for the true waterfall. True historical slices land in Phase 5 (see roadmap). This is intentional simplification — flag if the user requests true real-time waterfall in Phase 1.
2. SAF (`content://`) sources are not supported for Apply in Phase 1 — only local paths. The Toast in `onEQApply` makes this explicit. SAF write support is a follow-up.
3. The biquad coefficient getter (`Biquad.coeffs()`) is used by the closed-form magnitude calculation in `EQCurveView2D` and `EQCurveView3D`. Both files duplicate the math; if a bug is found, fix both.
4. The "freeform draw" gesture conflicts with handle-drag in noise-cut mode by design — in NOISE_CUT mode, drags are freeform; tap is still tap-to-notch / accept-suggestion. Tested manually in Task 26.
5. The plan adds ~15 new files. Build time may grow noticeably on first compile.

**Execution handoff:**

Plan complete and saved to `docs/superpowers/plans/2026-05-25-live-graph-eq.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
