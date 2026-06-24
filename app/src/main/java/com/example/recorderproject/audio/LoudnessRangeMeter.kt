package com.example.recorderproject.audio

import kotlin.math.max

/**
 * BS.1770 Loudness Range (LRA).
 *
 *  - Compute short-term (3 s) loudness every 1 s (a "short-term frame").
 *  - Apply absolute gate at -70 LUFS.
 *  - Then apply relative gate at (ungated mean - 20 LU).
 *  - LRA = p95 - p10 of the gated short-term values.
 *
 * Reuses LufsProcessor internally so the K-weighting is identical.
 */
class LoudnessRangeMeter(sampleRate: Float, channels: Int = 1) {

    private val lufs = LufsProcessor(sampleRate, channels)
    private val frameSamples = (sampleRate * 1f).toInt()     // 1 s frames
    private var samplesInFrame = 0
    private val shortTerm = ArrayList<Float>()

    var lra: Float = 0f; private set

    fun process(interleaved: FloatArray) {
        val ch = lufs.channels
        val frames = interleaved.size / ch
        var consumed = 0
        while (consumed < frames) {
            val take = minOf(frames - consumed, frameSamples - samplesInFrame)
            lufs.process(interleaved.copyOfRange(consumed * ch, (consumed + take) * ch))
            samplesInFrame += take
            consumed += take
            if (samplesInFrame >= frameSamples) {
                shortTerm.add(lufs.shortTermLufs)
                samplesInFrame = 0
                recomputeLra()
            }
        }
    }

    private fun recomputeLra() {
        val absGated = shortTerm.filter { it >= -70f }
        if (absGated.size < 2) { lra = 0f; return }
        val ungated = absGated.average().toFloat()
        val relGated = absGated.filter { it >= ungated - 20f }.sorted()
        if (relGated.size < 2) { lra = 0f; return }
        val p10 = percentile(relGated, 10f)
        val p95 = percentile(relGated, 95f)
        lra = max(0f, p95 - p10)
    }

    private fun percentile(sorted: List<Float>, p: Float): Float {
        val idx = ((p / 100f) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}
