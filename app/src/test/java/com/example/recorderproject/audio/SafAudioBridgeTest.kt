package com.example.recorderproject.audio

import com.example.recorderproject.model.EQChain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class SafAudioBridgeTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `drains input to a temp file and publishes processor output to the sink`() {
        val inputBytes = "the original recording bytes".toByteArray()
        val sink = ByteArrayOutputStream()
        var srcSeenByProcessor: ByteArray? = null

        SafAudioBridge.processViaTemp(
            cacheDir = tmp.root,
            openInput = { ByteArrayInputStream(inputBytes) },
            openOutput = { sink },
            process = { src, dst ->
                srcSeenByProcessor = src.readBytes()
                dst.writeBytes(src.readBytes().reversedArray())
            },
        )

        // The processor saw the full input materialized as a real local file.
        assertArrayEquals(inputBytes, srcSeenByProcessor)
        // Whatever the processor wrote to dst was published back to the sink.
        assertArrayEquals(inputBytes.reversedArray(), sink.toByteArray())
    }

    @Test fun `deletes both temp files on success`() {
        SafAudioBridge.processViaTemp(
            cacheDir = tmp.root,
            openInput = { ByteArrayInputStream(ByteArray(8)) },
            openOutput = { ByteArrayOutputStream() },
            process = { _, dst -> dst.writeBytes(ByteArray(4)) },
        )
        assertEquals("temp files should be cleaned up", 0, tmp.root.listFiles()?.size ?: 0)
    }

    @Test fun `deletes temp files and rethrows when the processor fails`() {
        try {
            SafAudioBridge.processViaTemp(
                cacheDir = tmp.root,
                openInput = { ByteArrayInputStream(ByteArray(8)) },
                openOutput = { ByteArrayOutputStream() },
                process = { _, _ -> throw IllegalStateException("render boom") },
            )
            fail("expected the processor exception to propagate")
        } catch (e: IllegalStateException) {
            assertEquals("render boom", e.message)
        }
        assertEquals("temp files should be cleaned up even on failure", 0, tmp.root.listFiles()?.size ?: 0)
    }

    @Test fun `round-trips a real WAV through EQProcessor preserving frame count`() {
        val sr = 48_000
        val frames = (sr * 0.1f).toInt()
        val samples = FloatArray(frames) { 0.5f * sin(2.0 * PI * 1000.0 * it / sr).toFloat() }
        val seed = tmp.newFile("seed.wav")
        WavIo.write(seed, samples, channels = 1, sampleRate = sr, bitDepth = 16)
        val inputBytes = seed.readBytes()
        val sink = ByteArrayOutputStream()

        SafAudioBridge.processViaTemp(
            cacheDir = tmp.root,
            openInput = { ByteArrayInputStream(inputBytes) },
            openOutput = { sink },
            process = { src, dst -> EQProcessor.process(src, dst, EQChain.empty(), progress = {}) },
        )

        val out = File(tmp.root, "out.wav").apply { writeBytes(sink.toByteArray()) }
        val header = WavIo.readHeader(out)
        assertEquals(frames.toLong(), header.totalFrames)
        assertTrue("output should contain audio", header.dataSize > 0)
    }
}
