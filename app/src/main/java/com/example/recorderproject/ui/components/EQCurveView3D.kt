package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.recorderproject.audio.BiquadCoeffs
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun EQCurveView3D(
    chain: EQChain,
    /** Snapshots of spectrum bins over recent time (newest last). Phase 1 ships static = single slice. */
    historicalSlices: List<StaticSpectrum>,
    sampleRate: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(0f to Color(0xFF111114), 1f to Color(0xFF09090C)))
        drawLandscape(historicalSlices)
        drawFrontEQCurve(chain, sampleRate)
        drawHandleColumns(chain)
    }
}

private fun DrawScope.drawLandscape(slices: List<StaticSpectrum>) {
    val w = size.width; val h = size.height
    // Synthesize multiple receding slices from the single static slice for Phase 1 — gives the
    // "waterfall feeling" without true historical data (Phase 5 swaps to real history).
    if (slices.isEmpty()) return
    val source = slices.first().magnitudeDb
    val depthCount = 8
    for (depthIdx in depthCount - 1 downTo 0) {
        val z = depthIdx.toFloat() / (depthCount - 1)
        val shrink = 1f - z * 0.55f
        val yOffset = h * 0.1f + z * h * 0.35f
        val widthAtDepth = w * shrink
        val xPad = (w - widthAtDepth) / 2f
        val opacity = (1f - z * 0.7f) * 0.7f

        // Slightly modulate each slice so it doesn't look identical
        val phase = depthIdx * 0.13
        val path = Path()
        for (i in source.indices) {
            val t = i.toFloat() / (source.size - 1)
            val x = xPad + t * widthAtDepth
            val wobble = (sin(phase + i * 0.04) * 1.2).toFloat()
            val normalized = ((source[i] + wobble + 80f) / 80f).coerceIn(0f, 1f)
            val maxHeight = (h * 0.4f) * (1f - z * 0.4f)
            val y = yOffset + (1f - normalized) * maxHeight
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = RecorderBlueGrey.copy(alpha = opacity), style = Stroke(width = 1.2f - z * 0.3f))
    }
}

private fun DrawScope.drawFrontEQCurve(chain: EQChain, sampleRate: Float) {
    val w = size.width; val h = size.height
    val response = computeResponse(chain, sampleRate)
    val baseline = h * 0.85f
    val ampPerDb = h * 0.025f
    val path = Path()
    for (i in response.indices) {
        val x = i.toFloat() / (response.size - 1) * w
        val y = baseline - response[i] * ampPerDb
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    // Multi-pass glow
    drawPath(path, color = RecorderOrange.copy(alpha = 0.18f), style = Stroke(width = 16f))
    drawPath(path, color = RecorderOrange.copy(alpha = 0.4f), style = Stroke(width = 8f))
    drawPath(path, color = RecorderOrange, style = Stroke(width = 3f))
}

private fun DrawScope.drawHandleColumns(chain: EQChain) {
    val w = size.width; val h = size.height
    val baseline = h * 0.85f
    val ampPerDb = h * 0.025f
    for (band in chain.bands.filter { it.enabled }) {
        val x = freqToX(band.frequencyHz, w)
        val y = baseline - band.gainDb * ampPerDb
        // Yellow column down to bottom
        drawLine(RecorderYellow.copy(alpha = 0.35f), Offset(x, y), Offset(x, h * 0.96f), 3f)
        drawCircle(RecorderYellow.copy(alpha = 0.3f), 16f, Offset(x, y))
        drawCircle(RecorderYellow, 12f, Offset(x, y))
        drawCircle(Color(0xFF0C0C10), 7f, Offset(x, y))
    }
}

// ----- helpers -----

private fun freqToX(freqHz: Float, width: Float): Float {
    val t = (ln(freqHz.toDouble()) - ln(20.0)) / (ln(20_000.0) - ln(20.0))
    return t.toFloat().coerceIn(0f, 1f) * width
}

private fun computeResponse(chain: EQChain, sampleRate: Float, bins: Int = 256): FloatArray {
    val activeBands = when {
        chain.bypassed -> emptyList()
        chain.bands.any { it.soloed && !it.muted } -> chain.bands.filter { it.soloed && !it.muted }
        else -> chain.bands.filter { it.enabled && !it.muted }
    }
    if (activeBands.isEmpty()) return FloatArray(bins) { 0f }
    val out = FloatArray(bins)
    for (i in 0 until bins) {
        val t = i.toDouble() / (bins - 1)
        val freq = exp(ln(20.0) + t * (ln(20_000.0) - ln(20.0)))
        var totalDb = 0.0
        for (band in activeBands) {
            for (biq in BiquadCoeffs.cascadeForBand(band, sampleRate)) {
                val c = biq.coeffs()
                val omega = 2 * Math.PI * freq / sampleRate
                val cosW = cos(omega); val sinW = sin(omega)
                val cos2W = cos(2 * omega); val sin2W = sin(2 * omega)
                val numR = c[0] + c[1] * cosW + c[2] * cos2W
                val numI = -c[1] * sinW - c[2] * sin2W
                val denR = 1.0 + c[3] * cosW + c[4] * cos2W
                val denI = -c[3] * sinW - c[4] * sin2W
                val num = sqrt(numR * numR + numI * numI)
                val den = sqrt(denR * denR + denI * denI).coerceAtLeast(1e-12)
                totalDb += 20.0 * log10(num / den)
            }
        }
        out[i] = totalDb.toFloat().coerceIn(-18f, 18f)
    }
    return out
}
