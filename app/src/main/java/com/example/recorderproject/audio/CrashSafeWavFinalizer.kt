package com.example.recorderproject.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase 7 — Crash-safe WAV finalizer.
 *
 * A long take that's killed mid-record normally leaves an unplayable WAV because the RIFF size
 * and data-chunk size fields point past the actual on-disk bytes. This utility re-finalizes
 * those header fields based on the file's current length, so even a force-stopped file plays.
 *
 * Usage: call finalize(file) periodically (e.g. every 1s) from the recording loop, or at any
 * checkpoint. Safe to call repeatedly; idempotent if the file is already valid.
 */
object CrashSafeWavFinalizer {

    /**
     * Rewrite RIFF size (offset 4) and "data" chunk size (offset 40 in canonical 44-byte header)
     * to match the file's current length on disk. Returns true if it successfully wrote.
     */
    fun finalize(file: File): Boolean {
        if (!file.exists() || file.length() < 44) return false
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                val totalLen = raf.length()
                // RIFF chunk: total file size - 8
                val riffSize = (totalLen - 8).toInt()
                raf.seek(4)
                raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(riffSize).array())

                // Walk chunks to find "data" and refinalize its size
                var pos = 12L
                while (pos < totalLen - 8) {
                    raf.seek(pos)
                    val idBytes = ByteArray(4); raf.read(idBytes)
                    val sizeBytes = ByteArray(4); raf.read(sizeBytes)
                    val id = idBytes.toString(Charsets.US_ASCII)
                    val declaredSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    if (id == "data") {
                        // The data chunk runs to end-of-file; refinalize its size.
                        val actualDataSize = (totalLen - (pos + 8)).toInt()
                        raf.seek(pos + 4)
                        raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(actualDataSize).array())
                        return@use true
                    }
                    pos += 8 + declaredSize + (declaredSize and 1)
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
