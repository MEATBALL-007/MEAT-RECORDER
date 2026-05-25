package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CrashSafeWavFinalizerTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `finalize refinalizes RIFF + data sizes to actual file length`() {
        val sr = 48_000
        val n = 4800
        val samples = FloatArray(n) { it.toFloat() / n - 0.5f }
        val file = tmp.newFile("partial.wav")
        WavIo.write(file, samples, channels = 1, sampleRate = sr, bitDepth = 16)

        // Simulate a crash mid-write: corrupt the RIFF size and data size to point past EOF
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(99_999_999).array())
            raf.seek(40)
            raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(99_999_999).array())
        }

        // Should now report a corrupt header on parse — refinalize fixes it
        val ok = CrashSafeWavFinalizer.finalize(file)
        assertTrue(ok)

        // Reading the header should now succeed and match the actual frame count
        val header = WavIo.readHeader(file)
        assertEquals(n.toLong(), header.totalFrames)
    }
}
