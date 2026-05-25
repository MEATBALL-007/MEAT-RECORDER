package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/** Phase 5 — full-screen scrolling 3D spectrogram from a history of slices. */
@Composable
fun WaterfallView(
    slices: List<StaticSpectrum>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (slices.isEmpty()) return@Canvas
        val w = size.width; val h = size.height
        val depthCount = slices.size

        // Render from back (oldest) to front (newest)
        for ((depthIdx, slice) in slices.withIndex()) {
            val z = 1f - depthIdx.toFloat() / (depthCount - 1).coerceAtLeast(1)
            val shrink = 1f - z * 0.65f
            val yOffset = h * 0.08f + z * h * 0.55f
            val widthAtDepth = w * shrink
            val xPad = (w - widthAtDepth) / 2f
            val opacity = (1f - z * 0.7f) * 0.85f
            val tone = if (depthIdx == depthCount - 1) RecorderOrange else RecorderBlueGrey

            val path = Path()
            for (i in slice.magnitudeDb.indices) {
                val t = i.toFloat() / (slice.magnitudeDb.size - 1)
                val x = xPad + t * widthAtDepth
                val normalized = ((slice.magnitudeDb[i] + 80f) / 80f).coerceIn(0f, 1f)
                val maxHeight = (h * 0.4f) * (1f - z * 0.5f)
                val y = yOffset + (1f - normalized) * maxHeight
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = tone.copy(alpha = opacity), style = Stroke(width = 1.3f - z * 0.5f))
        }

        // Front highlight glow
        slices.lastOrNull()?.let { last ->
            val path = Path()
            for (i in last.magnitudeDb.indices) {
                val t = i.toFloat() / (last.magnitudeDb.size - 1)
                val x = t * w
                val normalized = ((last.magnitudeDb[i] + 80f) / 80f).coerceIn(0f, 1f)
                val y = h * 0.6f + (1f - normalized) * (h * 0.3f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = RecorderOrange.copy(alpha = 0.6f), style = Stroke(width = 6f))
            drawPath(path, color = RecorderYellow, style = Stroke(width = 2f))
        }
    }
}
