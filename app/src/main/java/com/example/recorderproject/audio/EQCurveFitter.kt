package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import kotlin.math.exp
import kotlin.math.ln

object EQCurveFitter {

    /**
     * Convert a freeform target curve (dB values at log-spaced frequencies) into up to
     * maxBands Bell EQBands. Finds local extrema of the curve and assigns each to a band.
     */
    fun fitToCurve(
        targetDb: FloatArray,
        minFreqHz: Float,
        maxFreqHz: Float,
        maxBands: Int = 8,
    ): List<EQBand> {
        val n = targetDb.size
        if (n < 5) return emptyList()
        val extrema = mutableListOf<Pair<Int, Float>>()
        for (i in 2 until n - 2) {
            val v = targetDb[i]
            val isMax = v > targetDb[i - 1] && v > targetDb[i + 1] && v > 1f
            val isMin = v < targetDb[i - 1] && v < targetDb[i + 1] && v < -1f
            if (isMax || isMin) extrema.add(i to kotlin.math.abs(v))
        }
        val top = extrema.sortedByDescending { it.second }.take(maxBands).sortedBy { it.first }
        return top.mapIndexed { slot, (idx, _) ->
            val gain = targetDb[idx]
            val t = idx.toDouble() / (n - 1)
            val freq = exp(ln(minFreqHz.toDouble()) + t * (ln(maxFreqHz.toDouble()) - ln(minFreqHz.toDouble())))

            var halfWidthBins = 0
            for (off in 1 until 12) {
                val l = (idx - off).coerceAtLeast(0)
                val r = (idx + off).coerceAtMost(n - 1)
                if (kotlin.math.abs(targetDb[l]) < kotlin.math.abs(gain) / 2 ||
                    kotlin.math.abs(targetDb[r]) < kotlin.math.abs(gain) / 2
                ) {
                    halfWidthBins = off
                    break
                }
            }
            val halfWidthOctaves = (halfWidthBins.coerceAtLeast(1).toFloat() / n) * (kotlin.math.ln(maxFreqHz / minFreqHz) / kotlin.math.ln(2f))
            val q = (1.0 / (2.0 * halfWidthOctaves + 0.1)).coerceIn(0.4, 8.0)

            EQBand(
                id = slot + 1,
                type = EQBandType.BELL,
                frequencyHz = freq.toFloat().coerceIn(20f, 20_000f),
                gainDb = gain.coerceIn(-24f, 24f),
                q = q.toFloat(),
                enabled = true,
            )
        }
    }
}
