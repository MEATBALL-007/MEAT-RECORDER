package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Spectrum-splash overlay. Triggered when Record is hit: erupts spectrum bars radially from a
 * center point, plus three expanding concentric rings, plus particles. Fades out over ~800ms.
 *
 * Place this in a Box that overlays the screen, pass `triggerKey` that changes on each press,
 * and the splash will replay every time the key bumps.
 */
@Composable
fun SpectrumSplash(
    triggerKey: Int,
    centerXFraction: Float = 0.5f,
    centerYFraction: Float = 0.5f,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val barHeights = remember(triggerKey) {
        FloatArray(32) { 0.3f + Random.nextFloat() * 0.7f }
    }

    LaunchedEffect(triggerKey) {
        if (triggerKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = LinearEasing))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (progress.value <= 0f || progress.value >= 1f) return@Canvas
            val p = progress.value
            val w = size.width; val h = size.height
            val cx = w * centerXFraction
            val cy = h * centerYFraction
            val maxR = kotlin.math.hypot(w.toDouble(), h.toDouble()).toFloat() * 0.7f

            // Three expanding concentric rings (staggered)
            for (i in 0..2) {
                val ringP = ((p - i * 0.12f).coerceIn(0f, 1f))
                if (ringP <= 0f) continue
                val r = ringP * maxR * 0.55f
                val alpha = (1f - ringP) * 0.55f
                drawCircle(
                    color = RecorderOrange.copy(alpha = alpha),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f),
                )
            }

            // Radial spectrum bars erupting outward
            val barCount = barHeights.size
            val rNear = maxR * 0.15f
            val rFar = maxR * (0.25f + p * 0.6f)
            for (i in 0 until barCount) {
                val angle = (i.toFloat() / barCount) * 2f * PI.toFloat()
                val bh = barHeights[i]
                val rOuter = rNear + (rFar - rNear) * bh
                val sx = cx + cos(angle.toDouble()).toFloat() * rNear
                val sy = cy + sin(angle.toDouble()).toFloat() * rNear
                val ex = cx + cos(angle.toDouble()).toFloat() * rOuter
                val ey = cy + sin(angle.toDouble()).toFloat() * rOuter
                val barAlpha = (1f - p) * 0.95f
                val color = if (i % 3 == 0) RecorderYellow else RecorderOrange
                drawLine(
                    color = color.copy(alpha = barAlpha),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = 4f,
                )
            }

            // Particle dust at the bar tips (sprinkles)
            for (i in 0 until barCount) {
                val angle = (i.toFloat() / barCount) * 2f * PI.toFloat()
                val rTip = maxR * (0.25f + p * 0.75f) * barHeights[i]
                val px = cx + cos(angle.toDouble()).toFloat() * rTip
                val py = cy + sin(angle.toDouble()).toFloat() * rTip
                drawCircle(
                    color = RecorderYellow.copy(alpha = (1f - p) * 0.9f),
                    radius = 3f * (1f - p),
                    center = Offset(px, py),
                )
            }

            // Bright burst at center fades quickly
            val coreAlpha = ((1f - p * 4f).coerceAtLeast(0f)) * 0.85f
            drawCircle(
                color = RecorderYellow.copy(alpha = coreAlpha),
                radius = 30f,
                center = Offset(cx, cy),
            )
        }
    }
}
