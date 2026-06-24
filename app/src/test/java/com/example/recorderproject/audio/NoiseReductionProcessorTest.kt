package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class NoiseReductionProcessorTest {

    @get:Rule val tmp = TemporaryFolder()

    private val nr = NoiseReductionProcessor()

    private fun writeSine(file: File, fHz: Float, sr: Int, durationSec: Float, amp: Float) {
        val frames = (sr * durationSec).toInt()
        val samples = FloatArray(frames) { i -> amp * sin(2.0 * PI * fHz * i / sr).toFloat() }
        WavIo.write(file, samples, channels = 1, sampleRate = sr, bitDepth = 16)
    }

    private fun rms(file: File): Float {
        val s = WavIo.readAllSamples(file)
        var acc = 0.0
        for (v in s) acc += v.toDouble() * v
        return sqrt(acc / s.size).toFloat()
    }

    @Test fun `processFile preserves frame count and produces a valid WAV`() {
        val src = tmp.newFile("in.wav")
        val dst = tmp.newFile("out.wav")
        writeSine(src, fHz = 440f, sr = 48_000, durationSec = 0.2f, amp = 0.5f)

        nr.processFile(src, dst)

        val inFrames = WavIo.readHeader(src).totalFrames
        val outFrames = WavIo.readHeader(dst).totalFrames
        assertEquals(inFrames, outFrames)
    }

    @Test fun `processFile passes a loud signal through with the gate open`() {
        val src = tmp.newFile("loud_in.wav")
        val dst = tmp.newFile("loud_out.wav")
        writeSine(src, fHz = 440f, sr = 48_000, durationSec = 0.3f, amp = 0.5f)

        nr.processFile(src, dst)

        val ratio = rms(dst) / rms(src)
        assertTrue("loud signal should pass through (ratio=$ratio)", ratio > 0.9f)
    }

    @Test fun `processFile gates a sub-threshold signal toward silence`() {
        val src = tmp.newFile("quiet_in.wav")
        val dst = tmp.newFile("quiet_out.wav")
        // ~0.001 full-scale ≈ 33 in 16-bit, below the -55 dBFS (~58) gate threshold.
        writeSine(src, fHz = 440f, sr = 48_000, durationSec = 0.5f, amp = 0.001f)

        nr.processFile(src, dst)

        val ratio = rms(dst) / rms(src)
        assertTrue("sub-threshold signal should be strongly attenuated (ratio=$ratio)", ratio < 0.5f)
    }
}
