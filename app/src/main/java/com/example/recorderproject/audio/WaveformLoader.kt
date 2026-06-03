package com.example.recorderproject.audio

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WaveformLoader {
    fun load(path: String, targetSamples: Int = 300): FloatArray {
        if (targetSamples <= 0) return FloatArray(0)
        val file = File(path)
        if (!file.exists()) return FloatArray(targetSamples)
        return try {
            val headerBuf = ByteArray(44)
            FileInputStream(file).use { if (it.read(headerBuf) < 44) return FloatArray(targetSamples) }
            val hdr = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
            hdr.position(20)
            val audioFormat = hdr.short.toInt() and 0xFFFF
            val channels = hdr.short.toInt().coerceAtLeast(1)
            hdr.position(34)
            val bitDepth = hdr.short.toInt().coerceAtLeast(8)
            val bytesPerSample = bitDepth / 8
            val bytesPerFrame = bytesPerSample * channels

            val rawBytes = FileInputStream(file).use { fis -> fis.skip(44); fis.readBytes() }
            val totalFrames = rawBytes.size / bytesPerFrame
            if (totalFrames == 0) return FloatArray(targetSamples)

            val framesPerBucket = (totalFrames / targetSamples).coerceAtLeast(1)
            val result = FloatArray(targetSamples)
            val pcm = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)

            for (bucket in 0 until targetSamples) {
                var peakAbs = 0f
                val startFrame = bucket * framesPerBucket
                val endFrame = ((bucket + 1) * framesPerBucket).coerceAtMost(totalFrames)
                pcm.position(startFrame * bytesPerFrame)
                for (frame in startFrame until endFrame) {
                    for (ch in 0 until channels) {
                        val sample = when (bitDepth) {
                            16 -> pcm.short / 32768f
                            24 -> {
                                val b0 = pcm.get().toInt() and 0xFF
                                val b1 = pcm.get().toInt() and 0xFF
                                val b2 = pcm.get().toInt()
                                ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                            }
                            32 -> if (audioFormat == 3) pcm.float else pcm.int / 2147483648f
                            else -> pcm.short / 32768f
                        }
                        if (kotlin.math.abs(sample) > peakAbs) peakAbs = kotlin.math.abs(sample)
                    }
                }
                result[bucket] = peakAbs
            }
            result
        } catch (_: Exception) { FloatArray(targetSamples) }
    }
}
