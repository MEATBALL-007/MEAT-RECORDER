package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import java.io.File
import kotlin.math.exp
import kotlin.math.ln

/**
 * Phase 2 — "Match EQ" — analyze a reference file's spectrum, compute the difference vs the
 * source's spectrum, and fit a chain of bell bands to approximate the difference curve.
 */
object EQMatchFitter {

    fun fitMatch(source: File, reference: File, maxBands: Int = 6): List<EQBand> {
        val srcSpec = SpectrumAnalyzer.analyzeFile(source, bins = 256)
        val refSpec = SpectrumAnalyzer.analyzeFile(reference, bins = 256)
        if (srcSpec.magnitudeDb.size != refSpec.magnitudeDb.size) return emptyList()

        // Difference (target gain curve) = ref - src, smoothed
        val diff = FloatArray(srcSpec.magnitudeDb.size) { i ->
            refSpec.magnitudeDb[i] - srcSpec.magnitudeDb[i]
        }
        // 5-point moving average smoothing
        val smoothed = FloatArray(diff.size)
        for (i in diff.indices) {
            var acc = 0f; var count = 0
            for (j in i - 2..i + 2) if (j in diff.indices) { acc += diff[j]; count++ }
            smoothed[i] = acc / count
        }
        return EQCurveFitter.fitToCurve(smoothed, srcSpec.minFreqHz, srcSpec.maxFreqHz, maxBands)
    }
}
