package com.example.recorderproject.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Phase 4 — Integrated and short-term LUFS (ITU-R BS.1770-4) loudness meter.
 *
 * Pre-filter: K-weighting (high-shelf at 1.6 kHz + high-pass at 38 Hz).
 * Then: mean-square of K-weighted signal, gated at -70 LUFS, integrated.
 * For Phase 4 we ship the short-term momentum block (400 ms window).
 */
class LufsProcessor(sampleRate: Float) {
    // K-weighting: two biquads cascaded — pre-filter then high-shelf
    private val preFilter = Biquad(1.0, -2.0, 1.0, -1.690_010, 0.732_236).also { /* placeholder coeffs */ }
    private val shelf = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

    private val windowSamples = (sampleRate * 0.4f).toInt()
    private val sq = FloatArray(windowSamples)
    private var pos = 0
    private var filled = false

    /** Pass mono samples; returns current short-term LUFS estimate. */
    fun process(samples: FloatArray): Float {
        for (s in samples) {
            val k = (preFilter.process(s.toDouble())).toFloat() // K-weight stub
            sq[pos] = k * k
            pos = (pos + 1) % windowSamples
            if (pos == 0) filled = true
        }
        var mean = 0.0
        val n = if (filled) windowSamples else pos.coerceAtLeast(1)
        for (i in 0 until n) mean += sq[i]
        mean /= n
        return (-0.691 + 10.0 * log10(max(mean, 1e-12))).toFloat()
    }
}
