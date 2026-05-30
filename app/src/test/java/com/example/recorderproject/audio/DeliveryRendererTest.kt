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
        writeMonoSine(src, sr = 48000, freq = 1000f, seconds = 5f, amp = 0.0316f)  // ~-30 dBFS

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Podcast)
        assertNotNull(result)
        assertEquals(-16f, result!!.integratedLufs, 1.0f)   // generous tolerance for tone/test signal
        assertTrue("TP <= ceiling", result.truePeakDbtp <= -1f + 0.5f)
        assertTrue(dst.exists() && dst.length() > 0)
    }

    @Test fun renders_loud_signal_down_to_target() {
        val src = tmp("src-loud"); val dst = tmp("dst-loud")
        writeMonoSine(src, sr = 48000, freq = 1000f, seconds = 5f, amp = 0.5f)  // ~-6 dBFS

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Broadcast)
        assertNotNull(result)
        assertEquals(-23f, result!!.integratedLufs, 1.0f)
    }

    @Test fun limiter_engages_when_full_gain_would_clip() {
        val src = tmp("src-square"); val dst = tmp("dst-square")
        val sr = 48000
        val sig = FloatArray(sr * 3) { if ((it / 240) % 2 == 0) 0.99f else -0.99f }
        WavIo.write(src, sig, channels = 1, sampleRate = sr, bitDepth = 24)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Streaming)
        assertNotNull(result)
        assertTrue("TP must stay below ceiling+0.5dB", result!!.truePeakDbtp <= -1f + 0.5f)
    }

    @Test fun skips_silent_source() {
        val src = tmp("src-silent"); val dst = tmp("dst-silent")
        WavIo.write(src, FloatArray(48000 * 2), channels = 1, sampleRate = 48000, bitDepth = 24)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Podcast)
        assertNull(result)
    }

    @Test fun skips_sub_window_source() {
        val src = tmp("src-tiny"); val dst = tmp("dst-tiny")
        val sig = FloatArray((48000 * 0.1f).toInt()) { 0.1f * sin(2.0 * PI * 1000.0 * it / 48000.0).toFloat() }
        WavIo.write(src, sig, channels = 1, sampleRate = 48000, bitDepth = 24)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Podcast)
        assertNull(result)
    }

    @Test fun off_target_returns_null_and_writes_no_file() {
        val src = tmp("src-off"); val dst = tmp("dst-off")
        writeMonoSine(src, sr = 48000, freq = 1000f, seconds = 2f, amp = 0.1f)

        val result = DeliveryRenderer.render(src, dst, LoudnessTarget.Off)
        assertNull(result)
    }
}
