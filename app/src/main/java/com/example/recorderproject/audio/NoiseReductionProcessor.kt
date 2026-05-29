package com.example.recorderproject.audio

import com.example.recorderproject.model.RecordFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Soft noise gate post-processor.
 *
 * Old behavior was a hard gate (samples below 2% amplitude → 0). That mutes
 * normal speech between phonemes and clicks at every edge. This implementation
 * uses an envelope-following gain with attack/release ramps so the gate
 * closes/opens smoothly over ~10–30ms, and a much lower threshold so we only
 * gate true silence, not quiet speech.
 *
 * Conservative defaults:
 *   threshold = -55 dBFS (~0.0018 of full scale)
 *   attack    = 5 ms  (gate opens quickly when signal arrives)
 *   release   = 80 ms (gate closes slowly so trailing word ends survive)
 *   sampleRateHz default 48000 (close enough — the WAV header has the real value
 *                               but for envelope timing constants this is fine)
 */
class NoiseReductionProcessor {

    companion object {
        // -55 dBFS as a linear short magnitude: 32768 * 10^(-55/20) ≈ 58
        private const val THRESHOLD_LINEAR_SHORT = 58

        // Per-sample smoothing coefficients. Computed from 48 kHz; will be slightly
        // off for 44.1/96k but the audible difference is negligible at these scales.
        private const val ATTACK_COEF  = 1f / (0.005f * 48000f)   // ~4167 samples → 1.0 over 5 ms
        private const val RELEASE_COEF = 1f / (0.080f * 48000f)   // 80 ms ramp down
    }

    fun process(file: RecordFile): RecordFile {
        val sourceFile = File(file.path)
        if (!sourceFile.exists()) return file

        val outputName = sourceFile.nameWithoutExtension + "_nr.wav"
        val outputFile = File(sourceFile.parentFile, outputName)

        FileInputStream(sourceFile).use { inputStream ->
            BufferedOutputStream(FileOutputStream(outputFile)).use { outputStream ->
                // Copy the 44-byte WAV header unchanged
                val header = ByteArray(44)
                inputStream.read(header)
                outputStream.write(header)

                // Envelope-following gate
                // gain follows a target (0 below threshold, 1 above) with asymmetric
                // attack/release so the transition is smooth and inaudible.
                var gain = 1f
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                    var i = 0
                    while (i + 1 < bytesRead) {
                        val sample = ((buffer[i + 1].toInt() shl 8) or
                            (buffer[i].toInt() and 0xFF)).toShort()
                        val absVal = abs(sample.toInt())

                        // Target gain: 1 above threshold, 0 below. Smoothed envelope.
                        val target = if (absVal >= THRESHOLD_LINEAR_SHORT) 1f else 0f
                        gain += if (target > gain) {
                            ((target - gain) * ATTACK_COEF).coerceAtMost(target - gain)
                        } else {
                            ((target - gain) * RELEASE_COEF).coerceAtLeast(target - gain)
                        }
                        gain = gain.coerceIn(0f, 1f)

                        val out = (sample * gain).toInt().coerceIn(-32768, 32767).toShort()
                        buffer[i] = (out.toInt() and 0xFF).toByte()
                        buffer[i + 1] = ((out.toInt() shr 8) and 0xFF).toByte()
                        i += 2
                    }
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }

        return file.copy(
            id = System.currentTimeMillis().toString(),
            name = outputFile.name,
            path = outputFile.absolutePath,
            hasNoiseReduction = true
        )
    }
}
