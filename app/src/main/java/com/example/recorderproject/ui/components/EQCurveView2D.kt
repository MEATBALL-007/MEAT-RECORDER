package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.recorderproject.audio.BiquadCoeffs
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

private const val MIN_FREQ = 20f
private const val MAX_FREQ = 20_000f
private const val MIN_DB = -18f
private const val MAX_DB = 18f
private const val CURVE_BINS = 256

@Composable
fun EQCurveView2D(
    chain: EQChain,
    spectrum: StaticSpectrum?,
    mode: EQEditMode,
    selectedBandId: Int?,
    sampleRate: Float,
    onHandleDrag: (band: EQBand, newFreqHz: Float, newGainDb: Float) -> Unit,
    onHandleTap: (band: EQBand) -> Unit,
    onTapEmpty: (freqHz: Float) -> Unit,
    onAcceptSuggestion: (band: EQBand) -> Unit,
    onFreeformDraw: (targetDbCurve: FloatArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drawnPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Spectrum entry animation 0→1 over 360ms when a new spectrum arrives
    var spectrumIntro by remember { mutableStateOf(0f) }
    LaunchedEffect(spectrum) {
        spectrumIntro = 0f
        if (spectrum != null) {
            val steps = 24
            for (i in 1..steps) {
                spectrumIntro = i.toFloat() / steps
                kotlinx.coroutines.delay(15)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(chain, mode) {
                    val sizeF = Size(size.width.toFloat(), size.height.toFloat())
                    if (mode == EQEditMode.NOISE_CUT) {
                        detectDragGestures(
                            onDragStart = { drawnPoints = listOf(it) },
                            onDrag = { change, _ -> drawnPoints = drawnPoints + change.position },
                            onDragEnd = {
                                if (drawnPoints.size >= 3) {
                                    val target = freeformPointsToTargetCurve(drawnPoints, sizeF, CURVE_BINS)
                                    onFreeformDraw(target)
                                }
                                drawnPoints = emptyList()
                            },
                        )
                    } else {
                        var dragging: EQBand? = null
                        detectDragGestures(
                            onDragStart = { pos -> dragging = nearestHandle(chain, pos, sizeF) },
                            onDrag = { change, _ ->
                                val band = dragging ?: return@detectDragGestures
                                val freq = xToFreq(change.position.x, sizeF.width)
                                val gain = yToDb(change.position.y, sizeF.height)
                                onHandleDrag(band, freq, gain)
                            },
                            onDragEnd = { dragging = null },
                        )
                    }
                }
                .pointerInput(chain, mode) {
                    val sizeF = Size(size.width.toFloat(), size.height.toFloat())
                    detectTapGestures(
                        onTap = { pos ->
                            val band = nearestHandle(chain, pos, sizeF)
                            when {
                                band != null -> onHandleTap(band)
                                mode == EQEditMode.NOISE_CUT -> {
                                    val sugg = nearestSuggestion(chain, pos, sizeF)
                                    if (sugg != null) onAcceptSuggestion(sugg)
                                    else onTapEmpty(xToFreq(pos.x, sizeF.width))
                                }
                                else -> onTapEmpty(xToFreq(pos.x, sizeF.width))
                            }
                        },
                    )
                }
        ) {
            drawBackground()
            drawGrid()
            spectrum?.let { drawSpectrumHills(it, spectrumIntro) }
            drawResponseFill(chain, sampleRate)
            drawResponseCurve(chain, sampleRate)
            drawHandles(chain, selectedBandId)
            if (mode == EQEditMode.NOISE_CUT) {
                drawSuggestions(chain)
                drawFreeformTrail(drawnPoints)
            }
            drawAxisLabels()
            drawActiveBandReadout(chain, selectedBandId)
        }
    }
}

// ------------- math helpers -------------

private fun freqToX(freqHz: Float, width: Float): Float {
    val t = (ln(freqHz.toDouble()) - ln(MIN_FREQ.toDouble())) /
        (ln(MAX_FREQ.toDouble()) - ln(MIN_FREQ.toDouble()))
    return t.toFloat().coerceIn(0f, 1f) * width
}

private fun xToFreq(x: Float, width: Float): Float {
    val t = (x / width).coerceIn(0f, 1f).toDouble()
    return exp(ln(MIN_FREQ.toDouble()) + t * (ln(MAX_FREQ.toDouble()) - ln(MIN_FREQ.toDouble()))).toFloat()
}

private fun dbToY(db: Float, height: Float): Float {
    val t = (db.coerceIn(MIN_DB, MAX_DB) - MIN_DB) / (MAX_DB - MIN_DB)
    return (1f - t) * height
}

private fun yToDb(y: Float, height: Float): Float {
    val t = (1f - y / height).coerceIn(0f, 1f)
    return MIN_DB + t * (MAX_DB - MIN_DB)
}

/** Combined magnitude (dB) of the chain at log-spaced frequencies via closed-form |H(e^jw)|. */
private fun computeResponse(chain: EQChain, sampleRate: Float, bins: Int = CURVE_BINS): FloatArray {
    val activeBands = when {
        chain.bypassed -> emptyList()
        chain.bands.any { it.soloed && !it.muted } -> chain.bands.filter { it.soloed && !it.muted }
        else -> chain.bands.filter { it.enabled && !it.muted }
    }
    if (activeBands.isEmpty()) return FloatArray(bins) { 0f }
    val out = FloatArray(bins)
    for (i in 0 until bins) {
        val t = i.toDouble() / (bins - 1)
        val freq = exp(ln(MIN_FREQ.toDouble()) + t * (ln(MAX_FREQ.toDouble()) - ln(MIN_FREQ.toDouble())))
        var totalDb = 0.0
        for (band in activeBands) {
            for (biq in BiquadCoeffs.cascadeForBand(band, sampleRate)) {
                val c = biq.coeffs()
                val b0 = c[0]; val b1 = c[1]; val b2 = c[2]; val a1 = c[3]; val a2 = c[4]
                val omega = 2 * Math.PI * freq / sampleRate
                val cosW = cos(omega); val sinW = sin(omega)
                val cos2W = cos(2 * omega); val sin2W = sin(2 * omega)
                val numR = b0 + b1 * cosW + b2 * cos2W
                val numI = -b1 * sinW - b2 * sin2W
                val denR = 1.0 + a1 * cosW + a2 * cos2W
                val denI = -a1 * sinW - a2 * sin2W
                val num = sqrt(numR * numR + numI * numI)
                val den = sqrt(denR * denR + denI * denI).coerceAtLeast(1e-12)
                totalDb += 20.0 * log10(num / den)
            }
        }
        out[i] = totalDb.toFloat().coerceIn(MIN_DB, MAX_DB)
    }
    return out
}

private fun nearestHandle(chain: EQChain, pos: Offset, size: Size): EQBand? {
    var best: EQBand? = null
    var bestDist = Float.MAX_VALUE
    for (b in chain.bands.filter { it.enabled }) {
        val x = freqToX(b.frequencyHz, size.width)
        val y = dbToY(b.gainDb, size.height)
        val d = (Offset(x, y) - pos).getDistance()
        if (d < 48f && d < bestDist) { bestDist = d; best = b }
    }
    return best
}

private fun nearestSuggestion(chain: EQChain, pos: Offset, size: Size): EQBand? {
    var best: EQBand? = null
    var bestDist = Float.MAX_VALUE
    for (b in chain.noiseCutSuggestions) {
        val x = freqToX(b.frequencyHz, size.width)
        val y = dbToY(-6f, size.height)
        val d = (Offset(x, y) - pos).getDistance()
        if (d < 48f && d < bestDist) { bestDist = d; best = b }
    }
    return best
}

private fun freeformPointsToTargetCurve(points: List<Offset>, size: Size, bins: Int): FloatArray {
    val sorted = points.sortedBy { it.x }
    val xs = sorted.map { xToFreq(it.x, size.width) }
    val ys = sorted.map { yToDb(it.y, size.height) }
    val out = FloatArray(bins)
    for (i in 0 until bins) {
        val t = i.toDouble() / (bins - 1)
        val freq = exp(ln(MIN_FREQ.toDouble()) + t * (ln(MAX_FREQ.toDouble()) - ln(MIN_FREQ.toDouble()))).toFloat()
        var ix = xs.indexOfFirst { it >= freq }
        out[i] = when {
            ix <= 0 -> ys.firstOrNull() ?: 0f
            ix >= xs.size -> ys.lastOrNull() ?: 0f
            else -> {
                val xLo = xs[ix - 1]; val xHi = xs[ix]
                val yLo = ys[ix - 1]; val yHi = ys[ix]
                val frac = ((ln(freq.toDouble()) - ln(xLo.toDouble())) /
                    (ln(xHi.toDouble()) - ln(xLo.toDouble()))).toFloat()
                yLo + frac * (yHi - yLo)
            }
        }
    }
    return out
}

// ------------- drawing -------------

private fun DrawScope.drawBackground() {
    // Subtle vertical gradient from charcoal to slightly lighter at the top
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF111114),
            1f to Color(0xFF09090C),
        )
    )
}

private fun DrawScope.drawGrid() {
    val w = size.width; val h = size.height
    val grid = RecorderBlueGrey
    // 0 dB line (more prominent)
    drawLine(grid.copy(alpha = 0.35f), Offset(0f, h / 2), Offset(w, h / 2), 1.2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
    // ±6 and ±12 dB
    for (db in listOf(-12f, -6f, 6f, 12f)) {
        val y = dbToY(db, h)
        drawLine(grid.copy(alpha = 0.12f), Offset(0f, y), Offset(w, y), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 10f)))
    }
    // Decade verticals
    for (f in listOf(100f, 1000f, 10_000f)) {
        val x = freqToX(f, w)
        drawLine(grid.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, h), 1f)
    }
    // Octave marks (lighter)
    val octaves = listOf(50f, 200f, 500f, 2000f, 5000f, 15_000f)
    for (f in octaves) {
        val x = freqToX(f, w)
        drawLine(grid.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, h), 1f)
    }
}

private fun DrawScope.drawSpectrumHills(spectrum: StaticSpectrum, intro: Float) {
    val w = size.width; val h = size.height
    val mag = spectrum.magnitudeDb
    val path = Path()
    path.moveTo(0f, h)
    for (i in mag.indices) {
        val x = i.toFloat() / (mag.size - 1) * w
        val normalized = ((mag[i] + 80f) / 80f).coerceIn(0f, 1f)
        val targetY = h - normalized * (h * 0.55f)
        val y = h - (h - targetY) * intro // animate upward from baseline
        path.lineTo(x, y)
    }
    path.lineTo(w, h); path.close()
    drawPath(
        path,
        brush = Brush.verticalGradient(
            0f to RecorderBlueGrey.copy(alpha = 0.22f),
            1f to RecorderBlueGrey.copy(alpha = 0.02f),
        )
    )
}

/** Filled glow region under the response curve (down to the 0dB line). */
private fun DrawScope.drawResponseFill(chain: EQChain, sampleRate: Float) {
    val w = size.width; val h = size.height
    val response = computeResponse(chain, sampleRate)
    val baseline = dbToY(0f, h)
    val path = Path()
    path.moveTo(0f, baseline)
    for (i in response.indices) {
        val x = i.toFloat() / (response.size - 1) * w
        val y = dbToY(response[i], h)
        path.lineTo(x, y)
    }
    path.lineTo(w, baseline)
    path.close()
    drawPath(
        path,
        brush = Brush.verticalGradient(
            0f to RecorderOrange.copy(alpha = 0.18f),
            1f to RecorderOrange.copy(alpha = 0.02f),
        )
    )
}

private fun DrawScope.drawResponseCurve(chain: EQChain, sampleRate: Float) {
    val w = size.width; val h = size.height
    val response = computeResponse(chain, sampleRate)
    val path = Path()
    path.moveTo(0f, dbToY(response[0], h))
    for (i in 1 until response.size) {
        val x = i.toFloat() / (response.size - 1) * w
        path.lineTo(x, dbToY(response[i], h))
    }
    // Glow underlay
    drawPath(path, color = RecorderOrange.copy(alpha = 0.25f), style = Stroke(width = 10f))
    drawPath(path, color = RecorderOrange.copy(alpha = 0.5f), style = Stroke(width = 5f))
    // Crisp top line
    drawPath(path, color = RecorderOrange, style = Stroke(width = 2.5f))
}

private fun DrawScope.drawHandles(chain: EQChain, selectedBandId: Int?) {
    val w = size.width; val h = size.height
    for (band in chain.bands.filter { it.enabled }) {
        val x = freqToX(band.frequencyHz, w)
        val y = dbToY(band.gainDb, h)
        val isSelected = band.id == selectedBandId
        val outerR = if (isSelected) 16f else 12f
        // Outer halo
        drawCircle(RecorderYellow.copy(alpha = 0.3f), outerR + 4f, Offset(x, y))
        // Yellow ring
        drawCircle(RecorderYellow, outerR, Offset(x, y))
        // Dark hole + number marker
        drawCircle(Color(0xFF0C0C10), outerR - 5f, Offset(x, y))
        drawCircle(RecorderYellow, outerR - 9f, Offset(x, y))
        // Selected ring accent
        if (isSelected) {
            drawCircle(RecorderOrange, outerR + 2f, Offset(x, y), style = Stroke(width = 1.5f))
        }
    }
}

private fun DrawScope.drawSuggestions(chain: EQChain) {
    val w = size.width; val h = size.height
    val y = dbToY(-6f, h)
    for (band in chain.noiseCutSuggestions) {
        val x = freqToX(band.frequencyHz, w)
        // Dashed ghost ring
        drawCircle(RecorderOrange.copy(alpha = 0.6f), 13f, Offset(x, y), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
        drawCircle(RecorderOrange.copy(alpha = 0.25f), 8f, Offset(x, y))
    }
}

private fun DrawScope.drawFreeformTrail(points: List<Offset>) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    drawPath(path, color = RecorderOrange.copy(alpha = 0.8f), style = Stroke(width = 6f))
    drawPath(path, color = RecorderYellow.copy(alpha = 0.6f), style = Stroke(width = 2f))
}

private fun DrawScope.drawAxisLabels() {
    val w = size.width; val h = size.height
    // Note: Compose Canvas can't draw text via drawText without a TextMeasurer.
    // For now, we render tick marks instead; the EQScreen draws static axis labels via Text overlays.
    // Tick marks: at 100, 1k, 10k bottom; at +12, 0, -12 left
    val tickCol = RecorderBlueGrey.copy(alpha = 0.6f)
    for (f in listOf(100f, 1000f, 10_000f)) {
        val x = freqToX(f, w)
        drawLine(tickCol, Offset(x, h - 8f), Offset(x, h - 1f), 1.5f)
    }
    for (db in listOf(-12f, 0f, 12f)) {
        val y = dbToY(db, h)
        drawLine(tickCol, Offset(0f, y), Offset(8f, y), 1.5f)
    }
}

/** A small highlight near the selected band's handle — soft pulse target. */
private fun DrawScope.drawActiveBandReadout(chain: EQChain, selectedBandId: Int?) {
    val sel = chain.bands.firstOrNull { it.id == selectedBandId && it.enabled } ?: return
    val x = freqToX(sel.frequencyHz, size.width)
    val y = dbToY(sel.gainDb, size.height)
    // Faint vertical/horizontal guide lines crossing through the selected handle
    val guide = RecorderYellow.copy(alpha = 0.18f)
    drawLine(guide, Offset(x, 0f), Offset(x, size.height), 1f)
    drawLine(guide, Offset(0f, y), Offset(size.width, y), 1f)
}
