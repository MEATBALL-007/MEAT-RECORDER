package com.example.recorderproject.audio

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object PitchShifter {
    private const val TAG = "PitchShifter"
    private const val FRAME_SIZE = 2048
    private const val HOP_SIZE = 512

    /**
     * Shift pitch of [inputPath] WAV by [semitones] and write result to [outputPath].
     * Uses WSOLA: time-stretch by 1/speedFactor, then resample back to original length.
     * Positive semitones = higher pitch; negative = lower pitch.
     */
    fun shift(inputPath: String, outputPath: String, semitones: Float) {
        Log.d(TAG, "shift() semitones=$semitones")
        if (abs(semitones) < 0.01f) {
            File(inputPath).copyTo(File(outputPath), overwrite = true)
            return
        }
        val info = readWavInfo(inputPath)
        val inputSamples = readPcmFloat(inputPath, info)
        val speedFactor = 2.0.pow(semitones / 12.0).toFloat()
        val stretched = wsolaStretch(inputSamples, 1f / speedFactor)
        val resampled = linearResample(stretched, inputSamples.size)
        writePcmFloat(outputPath, resampled, info)
        Log.d(TAG, "shift() done → $outputPath")
    }

    private fun wsolaStretch(input: FloatArray, stretchFactor: Float): FloatArray {
        val window = hannWindow(FRAME_SIZE)
        val outLen = (input.size * stretchFactor).toInt().coerceAtLeast(FRAME_SIZE)
        val out = FloatArray(outLen)
        val norm = FloatArray(outLen)

        var outPos = 0
        var idealInPos = 0.0

        while (outPos + FRAME_SIZE <= outLen) {
            val target = idealInPos.toInt().coerceIn(0, input.size - FRAME_SIZE)
            val searchStart = (target - HOP_SIZE).coerceAtLeast(0)
            val searchEnd = (target + HOP_SIZE).coerceAtMost(input.size - FRAME_SIZE)

            val bestPos = if (outPos >= HOP_SIZE) {
                findBestCorrelation(input, searchStart, searchEnd, out, outPos - HOP_SIZE)
            } else target

            for (i in 0 until FRAME_SIZE) {
                val outIdx = outPos + i
                val inIdx = bestPos + i
                if (outIdx < outLen && inIdx < input.size) {
                    out[outIdx] += input[inIdx] * window[i]
                    norm[outIdx] += window[i]
                }
            }

            outPos += HOP_SIZE
            idealInPos += HOP_SIZE / stretchFactor
        }

        for (i in out.indices) {
            if (norm[i] > 1e-4f) out[i] /= norm[i]
        }
        return out
    }

    private fun findBestCorrelation(
        input: FloatArray,
        searchStart: Int,
        searchEnd: Int,
        reference: FloatArray,
        refStart: Int,
    ): Int {
        var bestPos = searchStart
        var bestCorr = Float.NEGATIVE_INFINITY
        val refLen = (HOP_SIZE).coerceAtMost(reference.size - refStart)
        if (refLen <= 0) return searchStart

        var candidate = searchStart
        while (candidate <= searchEnd) {
            var corr = 0f
            for (i in 0 until refLen) {
                val inputIdx = candidate + i
                val refIdx = refStart + i
                if (inputIdx < input.size && refIdx < reference.size) {
                    corr += input[inputIdx] * reference[refIdx]
                }
            }
            if (corr > bestCorr) { bestCorr = corr; bestPos = candidate }
            candidate += 4
        }
        return bestPos
    }

    private fun hannWindow(size: Int): FloatArray =
        FloatArray(size) { i -> 0.5f * (1f - cos(2.0 * PI * i / (size - 1)).toFloat()) }

    private fun linearResample(input: FloatArray, targetSize: Int): FloatArray {
        if (input.size == targetSize) return input
        val out = FloatArray(targetSize)
        val ratio = (input.size - 1).toDouble() / (targetSize - 1).coerceAtLeast(1)
        for (i in 0 until targetSize) {
            val pos = i * ratio
            val lo = pos.toInt().coerceIn(0, input.size - 2)
            val frac = (pos - lo).toFloat()
            out[i] = input[lo] * (1f - frac) + input[lo + 1] * frac
        }
        return out
    }

    private data class WavInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val bitDepth: Int,
        val audioFormat: Int,
        val dataOffset: Int,
    )

    private fun readWavInfo(path: String): WavInfo {
        FileInputStream(path).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(20)
            val fmt = buf.short.toInt() and 0xFFFF
            val ch = buf.short.toInt() and 0xFFFF
            val sr = buf.int
            buf.int; buf.short // byteRate, blockAlign
            val bd = buf.short.toInt() and 0xFFFF
            return WavInfo(sr, ch, bd, fmt, 44)
        }
    }

    private fun readPcmFloat(path: String, info: WavInfo): FloatArray {
        val raw = FileInputStream(path).use { fis -> fis.skip(info.dataOffset.toLong()); fis.readBytes() }
        val bytesPerSample = info.bitDepth / 8
        val totalSamples = raw.size / bytesPerSample
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(totalSamples) {
            when (info.bitDepth) {
                16 -> buf.short / 32768f
                24 -> {
                    val b0 = buf.get().toInt() and 0xFF
                    val b1 = buf.get().toInt() and 0xFF
                    val b2 = buf.get().toInt()
                    ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                }
                32 -> if (info.audioFormat == 3) buf.float else buf.int / 2147483648f
                else -> buf.short / 32768f
            }
        }
    }

    private fun writePcmFloat(path: String, samples: FloatArray, info: WavInfo) {
        val bytesPerSample = info.bitDepth / 8
        val dataLen = samples.size * bytesPerSample
        FileOutputStream(path).use { fos ->
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            hdr.put("RIFF".toByteArray()); hdr.putInt(dataLen + 36)
            hdr.put("WAVE".toByteArray()); hdr.put("fmt ".toByteArray())
            hdr.putInt(16)
            hdr.putShort(info.audioFormat.toShort())
            hdr.putShort(info.channelCount.toShort())
            hdr.putInt(info.sampleRate)
            hdr.putInt(info.sampleRate * info.channelCount * bytesPerSample)
            hdr.putShort((info.channelCount * bytesPerSample).toShort())
            hdr.putShort(info.bitDepth.toShort())
            hdr.put("data".toByteArray()); hdr.putInt(dataLen)
            fos.write(hdr.array())

            val pcm = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val c = s.coerceIn(-1f, 1f)
                when (info.bitDepth) {
                    16 -> pcm.putShort((c * 32767f).toInt().toShort())
                    24 -> {
                        val v = (c * 8388607f).toInt()
                        pcm.put((v and 0xFF).toByte())
                        pcm.put(((v shr 8) and 0xFF).toByte())
                        pcm.put(((v shr 16) and 0xFF).toByte())
                    }
                    32 -> if (info.audioFormat == 3) pcm.putFloat(c)
                          else pcm.putInt((c * 2147483647f).toInt())
                    else -> pcm.putShort((c * 32767f).toInt().toShort())
                }
            }
            fos.write(pcm.array())
        }
    }
}
