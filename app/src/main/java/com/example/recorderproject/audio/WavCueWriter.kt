package com.example.recorderproject.audio

import com.example.recorderproject.model.CuePoint
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase 4 — write WAV "cue " + "LIST adtl labl" chunks for tap-during-recording markers.
 * Adds the cue chunks after the existing data chunk and rewrites the RIFF size.
 */
object WavCueWriter {

    /** Append cue points to an existing WAV. Non-destructive of existing data. */
    fun appendCues(file: File, cues: List<CuePoint>) {
        if (cues.isEmpty()) return
        val header = WavIo.readHeader(file)
        RandomAccessFile(file, "rw").use { raf ->
            // Seek to end
            raf.seek(raf.length())

            // Build cue chunk
            val cueChunk = ByteBuffer.allocate(12 + 24 * cues.size).order(ByteOrder.LITTLE_ENDIAN)
            cueChunk.put("cue ".toByteArray(Charsets.US_ASCII))
            cueChunk.putInt(4 + 24 * cues.size)   // chunk size
            cueChunk.putInt(cues.size)            // numCuePoints
            for ((i, cue) in cues.withIndex()) {
                val sampleOffset = (cue.timeMs.toLong() * header.sampleRate / 1000L).toInt()
                cueChunk.putInt(i + 1)                                 // cuePointID
                cueChunk.putInt(sampleOffset)                          // position
                cueChunk.put("data".toByteArray(Charsets.US_ASCII))    // chunkID
                cueChunk.putInt(0)                                     // chunkStart
                cueChunk.putInt(0)                                     // blockStart
                cueChunk.putInt(sampleOffset)                          // sampleOffset
            }
            raf.write(cueChunk.array())

            // Build LIST adtl labl chunk
            val labelEntries = cues.mapIndexed { idx, cue ->
                val label = cue.label.ifBlank { "Cue ${idx + 1}" }
                val nameBytes = label.toByteArray(Charsets.UTF_8) + 0  // null-terminated
                val entry = ByteBuffer.allocate(4 + 4 + 4 + nameBytes.size + (nameBytes.size and 1))
                    .order(ByteOrder.LITTLE_ENDIAN)
                entry.put("labl".toByteArray(Charsets.US_ASCII))
                entry.putInt(4 + nameBytes.size)
                entry.putInt(idx + 1)
                entry.put(nameBytes)
                if (nameBytes.size and 1 != 0) entry.put(0.toByte())
                entry.array()
            }
            val adtlSize = 4 + labelEntries.sumOf { it.size }
            val listChunk = ByteBuffer.allocate(8 + adtlSize).order(ByteOrder.LITTLE_ENDIAN)
            listChunk.put("LIST".toByteArray(Charsets.US_ASCII))
            listChunk.putInt(adtlSize)
            listChunk.put("adtl".toByteArray(Charsets.US_ASCII))
            for (entry in labelEntries) listChunk.put(entry)
            raf.write(listChunk.array())

            // Rewrite RIFF size
            val newRiffSize = (raf.length() - 8).toInt()
            raf.seek(4)
            val sizeBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newRiffSize).array()
            raf.write(sizeBytes)
        }
    }
}
