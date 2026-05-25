package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBandType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
        val near60 = suggestions.any { kotlin.math.abs(it.frequencyHz - 60f) < 20f }
        assertTrue("Expected a notch near 60 Hz, got: ${suggestions.map { it.frequencyHz }}", near60)
        assertTrue("All suggestions should be NOTCH type", suggestions.all { it.type == EQBandType.NOTCH })
    }
}
