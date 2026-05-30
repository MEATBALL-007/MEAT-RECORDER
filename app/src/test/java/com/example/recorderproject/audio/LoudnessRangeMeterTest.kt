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
        lra.process(sine(sr, 1000f, 10f, 0.1f))
        assertEquals(0f, lra.lra, 1.0f)
    }

    @Test fun loud_then_quiet_yields_about_20lu() {
        val sr = 48000
        val lra = LoudnessRangeMeter(sr.toFloat(), channels = 1)
        lra.process(sine(sr, 1000f, 10f, 0.1f))
        lra.process(sine(sr, 1000f, 10f, 0.01f))
        assertEquals(20f, lra.lra, 3.0f)
    }
}
