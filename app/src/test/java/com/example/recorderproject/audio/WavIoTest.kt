package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.PI
import kotlin.math.sin

class WavIoTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `write then read 16-bit mono round trip preserves samples`() {
        val sr = 48_000
        val n = 4800
        val samples = FloatArray(n) { i -> 0.5f * sin(2.0 * PI * 1000.0 * i / sr).toFloat() }
        val file = tmp.newFile("test16.wav")

        WavIo.write(file, samples, channels = 1, sampleRate = sr, bitDepth = 16)

        val header = WavIo.readHeader(file)
        assertEquals(1, header.channels)
        assertEquals(sr, header.sampleRate)
        assertEquals(16, header.bitDepth)
        assertEquals(n.toLong(), header.totalFrames)

        val readBack = WavIo.readAllSamples(file)
        assertEquals(n, readBack.size)
        for (i in samples.indices) {
            assertEquals("sample $i", samples[i], readBack[i], 1e-4f)
        }
    }

    @Test fun `write then read stereo interleaves channels correctly`() {
        val sr = 44_100
        val frames = 1000
        val interleaved = FloatArray(frames * 2)
        for (i in 0 until frames) {
            interleaved[2 * i] = 0.3f
            interleaved[2 * i + 1] = -0.3f
        }
        val file = tmp.newFile("stereo.wav")
        WavIo.write(file, interleaved, channels = 2, sampleRate = sr, bitDepth = 16)

        val header = WavIo.readHeader(file)
        assertEquals(2, header.channels)
        assertEquals(frames.toLong(), header.totalFrames)

        val readBack = WavIo.readAllSamples(file)
        assertEquals(frames * 2, readBack.size)
        for (i in 0 until frames) {
            assertEquals(0.3f, readBack[2 * i], 1e-4f)
            assertEquals(-0.3f, readBack[2 * i + 1], 1e-4f)
        }
    }

    @Test fun `streaming read returns blocks until EOF`() {
        val sr = 48_000
        val n = 10_000
        val samples = FloatArray(n) { it.toFloat() / n - 0.5f }
        val file = tmp.newFile("stream.wav")
        WavIo.write(file, samples, channels = 1, sampleRate = sr, bitDepth = 16)

        val reader = WavIo.openReader(file)
        val buf = FloatArray(4096)
        var total = 0
        while (true) {
            val read = reader.readBlock(buf)
            if (read <= 0) break
            total += read
        }
        reader.close()
        assertEquals(n, total)
    }

    @Test fun stream_writer_round_trips_24bit_stereo() {
        val tmp = java.io.File.createTempFile("stream-wav-", ".wav")
        tmp.deleteOnExit()
        val sr = 48000; val ch = 2; val bits = 24

        val chunkA = FloatArray(8192) { (it / 8192f) - 0.5f }
        val chunkB = FloatArray(8192) { 0.5f - (it / 8192f) }

        WavIo.openWriter(tmp, channels = ch, sampleRate = sr, bitDepth = bits).use { w ->
            w.writeBlock(chunkA, chunkA.size)
            w.writeBlock(chunkB, chunkB.size)
        }

        val readBack = WavIo.readAllSamples(tmp)
        org.junit.Assert.assertEquals(chunkA.size + chunkB.size, readBack.size)
        org.junit.Assert.assertEquals(chunkA[100], readBack[100], 1e-4f)
        org.junit.Assert.assertEquals(chunkB[100], readBack[chunkA.size + 100], 1e-4f)

        val hdr = WavIo.readHeader(tmp)
        org.junit.Assert.assertEquals(ch, hdr.channels)
        org.junit.Assert.assertEquals(sr, hdr.sampleRate)
        org.junit.Assert.assertEquals(bits, hdr.bitDepth)
    }
}
