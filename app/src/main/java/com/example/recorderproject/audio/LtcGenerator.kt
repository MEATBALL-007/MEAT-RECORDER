package com.example.recorderproject.audio

/**
 * Phase 4 — SMPTE Linear Timecode (LTC) generator.
 * Produces a biphase-encoded audio waveform that carries timecode at standard frame rates.
 *
 * Each frame is 80 bits at framesPerSec; this encodes HH:MM:SS:FF + sync word.
 */
class LtcGenerator(
    private val sampleRate: Int = 48_000,
    private val framesPerSec: Int = 30,
) {
    /** Render bits for a single timecode frame. */
    private fun frameBits(hours: Int, minutes: Int, seconds: Int, frame: Int): BooleanArray {
        val bits = BooleanArray(80)
        // Bits 0-3: frame units
        writeBcd(bits, 0, frame % 10, 4)
        // Bits 8-9: frame tens
        writeBcd(bits, 8, frame / 10, 2)
        // Bits 16-19: seconds units
        writeBcd(bits, 16, seconds % 10, 4)
        // Bits 24-26: seconds tens
        writeBcd(bits, 24, seconds / 10, 3)
        // Bits 32-35: minutes units
        writeBcd(bits, 32, minutes % 10, 4)
        // Bits 40-42: minutes tens
        writeBcd(bits, 40, minutes / 10, 3)
        // Bits 48-51: hours units
        writeBcd(bits, 48, hours % 10, 4)
        // Bits 56-57: hours tens
        writeBcd(bits, 56, hours / 10, 2)
        // Bits 64-79: sync word 0x3FFD (LE in bits)
        val sync = 0x3FFD
        for (i in 0 until 16) bits[64 + i] = (sync shr i) and 1 == 1
        return bits
    }

    /** Encode a sequence of frames (e.g., timecode for a recording's length) as biphase audio. */
    fun encode(startSec: Int, durationSec: Int): FloatArray {
        val totalSamples = durationSec * sampleRate
        val samplesPerBit = sampleRate / (framesPerSec * 80)
        val out = FloatArray(totalSamples)
        var writeIdx = 0
        var t = startSec
        var phase = 1f
        val totalFrames = durationSec * framesPerSec
        for (frameIdx in 0 until totalFrames) {
            val h = t / 3600; val m = (t / 60) % 60; val s = t % 60
            val frameInSec = frameIdx % framesPerSec
            if (frameInSec == 0 && frameIdx > 0) t++
            val bits = frameBits(h, m, s, frameInSec)
            for (b in bits) {
                // Biphase: 0 = one transition per bit, 1 = two
                val halfSamples = samplesPerBit / 2
                for (i in 0 until samplesPerBit) {
                    if (writeIdx >= totalSamples) break
                    if (b && i == halfSamples) phase = -phase
                    if (!b && i == samplesPerBit - 1) phase = -phase
                    if (b && i == 0) phase = -phase
                    if (!b && i == 0) phase = -phase
                    out[writeIdx++] = phase * 0.6f
                }
                if (writeIdx >= totalSamples) break
            }
        }
        return out
    }

    private fun writeBcd(bits: BooleanArray, start: Int, value: Int, width: Int) {
        for (i in 0 until width) {
            bits[start + i] = (value shr i) and 1 == 1
        }
    }
}
