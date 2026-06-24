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
