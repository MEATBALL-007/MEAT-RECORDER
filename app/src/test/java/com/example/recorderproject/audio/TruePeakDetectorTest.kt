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

    /** Sample-aligned vs ISP-aware: at 7350 Hz / 48 kHz the discrete samples land
     *  off the continuous sine maxima, so the naive sample peak undershoots the true
     *  -1 dBFS amplitude. The polyphase detector must report TP at least as high as
     *  the sample max and close to the true -1 dBFS amplitude. */
    @Test fun inter_sample_peak_recovers_true_amplitude() {
        val sr = 48000
        val freq = 7350f
        val amp = 0.891f                              // -1 dBFS
        val sig = sine(sr, freq, 0.5f, amp)

        var sampleMaxLin = 0f
        for (s in sig) { val a = kotlin.math.abs(s); if (a > sampleMaxLin) sampleMaxLin = a }
        val sampleMaxDb = 20f * log10(sampleMaxLin)

        val det = TruePeakDetector(sampleRate = sr.toFloat(), channels = 1)
        det.feed(sig)

        assertTrue(
            "ISP TP (${det.peakDbTP}) should be >= sample max ($sampleMaxDb) " +
            "and within tolerance of the true -1 dBFS amplitude",
            det.peakDbTP >= sampleMaxDb - 0.05f && det.peakDbTP <= 0.5f && det.peakDbTP >= -1.5f,
        )
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
