package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBandType
import org.junit.Assert.assertTrue
import org.junit.Test

class EQCurveFitterTest {

    @Test fun `fits a single bump near 1kHz to a bell band there`() {
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
