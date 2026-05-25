package com.example.recorderproject.audio

import kotlin.math.*

object PitchDetector {
    private const val FRAME_SIZE = 2048
    private const val THRESHOLD  = 0.20f

    data class PitchResult(val frequencyHz: Float, val confidence: Float)

    fun detect(samples: ShortArray, offset: Int, count: Int, sampleRate: Int): PitchResult {
        val n = minOf(FRAME_SIZE, count)
        val minPeriod = (sampleRate / 2000).coerceAtLeast(2)   // ~2000 Hz max
        val maxPeriod = (sampleRate / 50).coerceAtMost(n / 2)  // ~50 Hz min
        if (n < minPeriod * 2 || maxPeriod <= minPeriod) return PitchResult(0f, 0f)

        // Normalized Square Difference Function (NSDF / MPM)
        val nsdf = FloatArray(maxPeriod + 1)
        for (tau in minPeriod..maxPeriod) {
            var acorr = 0.0; var norm = 0.0
            for (i in 0 until n - tau) {
                val s1 = samples[offset + i] / 32768.0
                val s2 = samples[offset + i + tau] / 32768.0
                acorr += s1 * s2
                norm  += s1 * s1 + s2 * s2
            }
            nsdf[tau] = if (norm > 1e-12) (2.0 * acorr / norm).toFloat() else 0f
        }

        // Find first peak above threshold after first zero crossing
        var bestTau = -1; var bestVal = 0f; var crossedZero = false
        for (tau in minPeriod + 1 until maxPeriod) {
            if (!crossedZero && nsdf[tau - 1] < 0f) crossedZero = true
            if (crossedZero && nsdf[tau] > THRESHOLD
                && nsdf[tau] >= nsdf[tau - 1] && nsdf[tau] >= nsdf[tau + 1]) {
                if (nsdf[tau] > bestVal) { bestVal = nsdf[tau]; bestTau = tau }
                break
            }
        }

        if (bestTau < 0) return PitchResult(0f, 0f)
        // Parabolic interpolation for sub-sample accuracy
        val prev = nsdf[bestTau - 1]; val next = nsdf[(bestTau + 1).coerceAtMost(maxPeriod)]
        val delta = 0.5f * (next - prev) / (2f * nsdf[bestTau] - prev - next + 1e-10f)
        val refinedTau = bestTau + delta
        val freq = sampleRate.toFloat() / refinedTau
        return PitchResult(freq.coerceIn(50f, 2000f), bestVal.coerceIn(0f, 1f))
    }
}
