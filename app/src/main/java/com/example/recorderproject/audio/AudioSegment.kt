package com.example.recorderproject.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class AudioSegment(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val avgLevelDb: Float
) {
    val durationMs: Long get() = endMs - startMs
    val label: String get() = "Scene ${index + 1}  (${durationMs / 1000}s)"
}

object SceneSlicer {

    fun sliceOnSilence(
        path: String,
        sampleRate: Int = 48000,
        bitDepth: Int = 16,
        channelCount: Int = 1,
        silenceThresholdDb: Float = -40f,
        minSilenceDurationMs: Long = 400L
    ): List<AudioSegment> {
        val file = File(path)
        if (!file.exists()) return emptyList()
        val allBytes: ByteArray
        try {
            FileInputStream(file).use { fis -> fis.skip(44); allBytes = fis.readBytes() }
        } catch (_: Exception) { return emptyList() }

        val bytesPerSample = when (bitDepth) { 32 -> 4; 24 -> 3; else -> 2 }
        val bytesPerFrame  = bytesPerSample * channelCount.coerceAtLeast(1)
        val totalFrames    = allBytes.size / bytesPerFrame
        if (totalFrames == 0) return emptyList()

        val silenceLinear = 10f.pow(silenceThresholdDb / 20f)
        val windowFrames  = (sampleRate * 0.02).toInt().coerceAtLeast(1)
        val minSilenceWindows = (minSilenceDurationMs / 20).toInt().coerceAtLeast(1)

        val rmsValues = mutableListOf<Float>()
        var pos = 0
        while (pos + windowFrames <= totalFrames) {
            var sumSq = 0.0
            for (i in pos until pos + windowFrames) {
                val byteOffset = i * bytesPerFrame
                // Read first channel only for RMS (mix-down not needed for silence detection)
                val s: Double = when (bitDepth) {
                    32 -> {
                        val bits = ((allBytes[byteOffset + 3].toInt() and 0xFF) shl 24) or
                                   ((allBytes[byteOffset + 2].toInt() and 0xFF) shl 16) or
                                   ((allBytes[byteOffset + 1].toInt() and 0xFF) shl 8)  or
                                   (allBytes[byteOffset].toInt() and 0xFF)
                        java.lang.Float.intBitsToFloat(bits).toDouble()
                    }
                    24 -> {
                        val lo  = allBytes[byteOffset].toInt() and 0xFF
                        val mid = allBytes[byteOffset + 1].toInt() and 0xFF
                        val hi  = allBytes[byteOffset + 2].toInt()  // sign-extended
                        ((hi shl 16) or (mid shl 8) or lo).toDouble() / 8388608.0
                    }
                    else -> ((allBytes[byteOffset + 1].toInt() shl 8) or
                             (allBytes[byteOffset].toInt() and 0xFF)).toShort().toDouble() / 32768.0
                }
                sumSq += s * s
            }
            rmsValues.add(sqrt(sumSq / windowFrames).toFloat())
            pos += windowFrames
        }
        if (rmsValues.isEmpty()) return emptyList()

        val segments = mutableListOf<AudioSegment>()
        var segStart = 0
        var silenceCount = 0
        var idx = 0

        for (i in rmsValues.indices) {
            val isSilent = rmsValues[i] < silenceLinear
            if (isSilent) {
                silenceCount++
                if (silenceCount == minSilenceWindows && (i - silenceCount) > segStart) {
                    val startMs = segStart * 20L
                    val endMs = (i - silenceCount + 1) * 20L
                    val sub = rmsValues.subList(segStart, (i - silenceCount + 1).coerceAtMost(rmsValues.size))
                    val avg = sub.average().toFloat()
                    segments.add(AudioSegment(idx++, startMs, endMs,
                        if (avg > 1e-10f) 20f * log10(avg) else -96f))
                }
            } else {
                if (silenceCount >= minSilenceWindows) segStart = i
                silenceCount = 0
            }
        }
        // Capture trailing segment
        if (silenceCount < minSilenceWindows && segStart < rmsValues.size) {
            val startMs = segStart * 20L
            val endMs = rmsValues.size * 20L
            val sub = rmsValues.subList(segStart, rmsValues.size)
            val avg = sub.average().toFloat()
            segments.add(AudioSegment(idx, startMs, endMs,
                if (avg > 1e-10f) 20f * log10(avg) else -96f))
        }
        return segments
    }

    fun exportSegment(
        srcPath: String,
        segment: AudioSegment,
        sampleRate: Int = 48000,
        bitDepth: Int = 16,
        channelCount: Int = 1
    ): File? {
        val src = File(srcPath)
        if (!src.exists()) return null
        return try {
            val allBytes: ByteArray
            FileInputStream(src).use { fis -> fis.skip(44); allBytes = fis.readBytes() }
            val bytesPerSample = bitDepth / 8
            val bytesPerFrame  = bytesPerSample * channelCount.coerceAtLeast(1)
            val startByte = (segment.startMs * sampleRate / 1000 * bytesPerFrame).toInt().coerceIn(0, allBytes.size)
            val endByte   = (segment.endMs   * sampleRate / 1000 * bytesPerFrame).toInt().coerceIn(startByte, allBytes.size)
            val segBytes  = allBytes.copyOfRange(startByte, endByte)
            val outFile   = File(src.parentFile, "${src.nameWithoutExtension}_scene${segment.index + 1}.wav")
            FileOutputStream(outFile).use { fos ->
                val dataLen  = segBytes.size
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
                fos.write(hdr.array()); fos.write(segBytes)
            }
            outFile
        } catch (_: Exception) { null }
    }
}
