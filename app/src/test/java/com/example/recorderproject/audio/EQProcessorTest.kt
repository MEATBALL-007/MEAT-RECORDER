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
        assertTrue("Expected >15 dB attenuation but got $ratioDb dB", ratioDb < -15.0)
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
