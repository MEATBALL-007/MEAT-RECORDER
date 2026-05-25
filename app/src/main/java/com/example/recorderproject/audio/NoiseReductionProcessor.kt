package com.example.recorderproject.audio

import com.example.recorderproject.model.RecordFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs

class NoiseReductionProcessor {

    companion object {
        // Noise gate threshold: samples quieter than ~2% of max amplitude are silenced
        private const val NOISE_GATE_THRESHOLD = 0.02f
    }

    fun process(file: RecordFile): RecordFile {
        val sourceFile = File(file.path)
        if (!sourceFile.exists()) return file

        val outputName = sourceFile.nameWithoutExtension + "_nr.wav"
        val outputFile = File(sourceFile.parentFile, outputName)
        val thresholdShort = (NOISE_GATE_THRESHOLD * 32768).toInt()

        FileInputStream(sourceFile).use { inputStream ->
            BufferedOutputStream(FileOutputStream(outputFile)).use { outputStream ->
                // Copy the 44-byte WAV header unchanged
                val header = ByteArray(44)
                inputStream.read(header)
                outputStream.write(header)

                // Process PCM samples with a noise gate
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                    var i = 0
                    while (i + 1 < bytesRead) {
                        val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                        val gated = if (abs(sample.toInt()) < thresholdShort) 0.toShort() else sample
                        buffer[i] = (gated.toInt() and 0xFF).toByte()
                        buffer[i + 1] = ((gated.toInt() shr 8) and 0xFF).toByte()
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
