package com.example.recorderproject.audio

/**
 * Phase 4 — circular pre-roll buffer. Always-on capture in a ring; on record-start, the
 * AudioRecorderManager prepends the buffer contents so the take catches the few seconds before.
 */
class PreRollBuffer(
    sampleRate: Int = 48_000,
    seconds: Int = 5,
    channels: Int = 1,
) {
    private val capacity = sampleRate * seconds * channels
    private val ring = FloatArray(capacity)
    private var writeIdx = 0
    private var filled = false

    fun write(samples: FloatArray) {
        for (s in samples) {
            ring[writeIdx] = s
            writeIdx = (writeIdx + 1) % capacity
            if (writeIdx == 0) filled = true
        }
    }

    /** Drain the buffer in time order (oldest → newest) into a fresh array. */
    fun drainOrdered(): FloatArray {
        val n = if (filled) capacity else writeIdx
        val out = FloatArray(n)
        if (!filled) {
            System.arraycopy(ring, 0, out, 0, writeIdx)
        } else {
            val tail = capacity - writeIdx
            System.arraycopy(ring, writeIdx, out, 0, tail)
            System.arraycopy(ring, 0, out, tail, writeIdx)
        }
        return out
    }

    fun clear() {
        writeIdx = 0
        filled = false
    }
}
