package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pro live spectrum analyzer for the recording session.
 * Takes the rolling RMS-history `levels` (newest at end) and renders a Hann-windowed FFT
 * as log-frequency bars with orange-yellow gradient. Lightweight enough for 60fps Compose redraw.
 */
@Composable
fun LiveSpectrumView(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    barCount: Int = 56,
    height: Dp = 140.dp,
) {
    val fftSize = remember(barCount) { nextPow2(barCount * 4).coerceAtLeast(64) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (levels.isEmpty()) return@Canvas
            val w = size.width; val h = size.height

            // Build FFT input from the last fftSize levels (zero-padded if short)
            val input = DoubleArray(fftSize)
            val src = if (levels.size <= fftSize) levels else levels.subList(levels.size - fftSize, levels.size)
            for (i in src.indices) {
                val window = 0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))
                input[fftSize - src.size + i] = src[i].toDouble() * window
            }
            val imag = DoubleArray(fftSize)
            fft(input, imag)

            // Group FFT bins into log-spaced display bars
            val bars = FloatArray(barCount)
            for (b in 0 until barCount) {
                val tLo = b.toDouble() / barCount
                val tHi = (b + 1).toDouble() / barCount
                val fftLo = (Math.pow(2.0, ln((fftSize / 2.0)) / ln(2.0) * tLo)).toInt().coerceAtMost(fftSize / 2 - 1)
                val fftHi = (Math.pow(2.0, ln((fftSize / 2.0)) / ln(2.0) * tHi)).toInt().coerceAtMost(fftSize / 2 - 1)
                var max = 0.0
                for (k in fftLo..fftHi) {
                    val mag = sqrt(input[k] * input[k] + imag[k] * imag[k])
                    if (mag > max) max = mag
                }
                // Compress to dB-ish scale
                val db = 20.0 * log10(max.coerceAtLeast(1e-6))
                bars[b] = ((db + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
            }

            val barW = w / barCount.toFloat()
            for ((i, lvl) in bars.withIndex()) {
                val barH = lvl * h * 0.92f + 2f
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to RecorderYellow,
                        0.55f to RecorderOrange,
                        1f to RecorderOrange.copy(alpha = 0.5f),
                    ),
                    topLeft = Offset(i * barW + 1f, h - barH),
                    size = Size((barW - 2f).coerceAtLeast(1f), barH),
                )
            }
        }
    }
}

/** In-place radix-2 FFT. n must be a power of 2. */
private fun fft(real: DoubleArray, imag: DoubleArray) {
    val n = real.size
    if (n and (n - 1) != 0) return
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
        j = j or bit
        if (i < j) {
            val tr = real[i]; real[i] = real[j]; real[j] = tr
            val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * PI / len
        val wlenR = cos(ang); val wlenI = kotlin.math.sin(ang)
        var i = 0
        while (i < n) {
            var wR = 1.0; var wI = 0.0
            for (k in 0 until len / 2) {
                val uR = real[i + k]; val uI = imag[i + k]
                val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                real[i + k] = uR + vR; imag[i + k] = uI + vI
                real[i + k + len / 2] = uR - vR; imag[i + k + len / 2] = uI - vI
                val nwR = wR * wlenR - wI * wlenI
                val nwI = wR * wlenI + wI * wlenR
                wR = nwR; wI = nwI
            }
            i += len
        }
        len = len shl 1
    }
}

private fun nextPow2(n: Int): Int {
    var v = 1
    while (v < n) v = v shl 1
    return v
}
