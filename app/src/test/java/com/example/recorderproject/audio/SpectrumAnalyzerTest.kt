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
        var peakIdx = 0
        for (i in spectrum.magnitudeDb.indices) if (spectrum.magnitudeDb[i] > spectrum.magnitudeDb[peakIdx]) peakIdx = i
        val peakFreq = spectrum.frequencyForBin(peakIdx)
        assertTrue("Peak should be near 1000 Hz, was $peakFreq", kotlin.math.abs(peakFreq - 1000.0) < 200.0)
    }
}
