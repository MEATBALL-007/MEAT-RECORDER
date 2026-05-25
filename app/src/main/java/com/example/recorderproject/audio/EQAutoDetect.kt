package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType

object EQAutoDetect {

    /** Identify prominent peaks in the spectrum and propose narrow notch filters. */
    fun proposeNotches(spectrum: StaticSpectrum, maxBands: Int = 8): List<EQBand> {
        val mag = spectrum.magnitudeDb
        val n = mag.size
        val window = 10
        val peakWindow = 5     // ±5 bins to check for peak dominance
        val proposals = mutableListOf<Pair<Int, Float>>()
        var i = window
        while (i < n - window) {
            var meanAcc = 0f
            for (j in i - window..i + window) if (j != i) meanAcc += mag[j]
            val mean = meanAcc / (window * 2)
            val prominence = mag[i] - mean
            // Plateau-tolerant peak detection: bin is a peak if nothing in ±peakWindow is STRICTLY larger
            var isPeak = true
            for (off in 1..peakWindow) {
                if (i - off >= 0 && mag[i - off] > mag[i]) { isPeak = false; break }
                if (i + off < n && mag[i + off] > mag[i]) { isPeak = false; break }
            }
            if (prominence > 6f && isPeak) {
                proposals.add(i to prominence)
                i += peakWindow // skip the rest of this plateau
            }
            i++
        }
        val top = proposals.sortedByDescending { it.second }.take(maxBands).sortedBy { it.first }
        return top.mapIndexed { idx, (binIdx, prom) ->
            val freq = spectrum.frequencyForBin(binIdx).toFloat()
            val q = (prom.toDouble().coerceIn(4.0, 24.0) * 0.7 + 4.0).toFloat()
            EQBand(
                id = idx + 1,
                type = EQBandType.NOTCH,
                frequencyHz = freq.coerceIn(20f, 20_000f),
                gainDb = 0f,
                q = q,
                enabled = true,
            )
        }
    }
}
