package com.example.recorderproject.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavTrimmer {

    /**
     * Copies the audio data between [inMs] and [outMs] from [srcPath] into a new
     * `*_trim.wav` file beside the source, preserving its format exactly.
     * Returns null on any IO failure.
     */
    fun trimToFile(
        srcPath: String,
        inMs: Long,
        outMs: Long,
        sampleRate: Int,
        bitDepth: Int,
        channelCount: Int
    ): File? {
        val src = File(srcPath)
        if (!src.exists()) return null
        return try {
            val allBytes: ByteArray
            FileInputStream(src).use { fis -> fis.skip(44); allBytes = fis.readBytes() }

            val bytesPerSample = bitDepth / 8
            val bytesPerFrame  = bytesPerSample * channelCount.coerceAtLeast(1)
            val startByte = (inMs  * sampleRate / 1000L * bytesPerFrame).toInt().coerceIn(0, allBytes.size)
            val endByte   = (outMs * sampleRate / 1000L * bytesPerFrame).toInt().coerceIn(startByte, allBytes.size)
            val trimBytes = allBytes.copyOfRange(startByte, endByte)

            val outFile = File(src.parentFile, "${src.nameWithoutExtension}_trim.wav")
            FileOutputStream(outFile).use { fos ->
                val dataLen  = trimBytes.size
                val riffLen  = dataLen + 36
                val byteRate = sampleRate * bytesPerFrame
                val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                hdr.put("RIFF".toByteArray()); hdr.putInt(riffLen)
                hdr.put("WAVE".toByteArray()); hdr.put("fmt ".toByteArray())
                hdr.putInt(16)
                hdr.putShort(if (bitDepth == 32) 3 else 1)  // PCM=1, IEEE float=3
                hdr.putShort(channelCount.toShort())
                hdr.putInt(sampleRate); hdr.putInt(byteRate)
                hdr.putShort(bytesPerFrame.toShort()); hdr.putShort(bitDepth.toShort())
                hdr.put("data".toByteArray()); hdr.putInt(dataLen)
                fos.write(hdr.array()); fos.write(trimBytes)
            }
            outFile
        } catch (_: Exception) { null }
    }
}
