package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.PI
import kotlin.math.sin

/**
 * MEATrec mark: stylized EQ curve sweeping across a circular badge.
 * Used in the splash and top app bar to brand the app distinctly.
 */
@Composable
fun MeatrecMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    animatedPhase: Float = 0f,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f - 4f

        // Outer ring — orange-to-yellow sweep gradient
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(RecorderOrange, RecorderYellow, RecorderOrange),
                center = Offset(cx, cy)
            ),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 4f),
        )

        // Inner curve: a small EQ response shape (peak + dip)
        val path = Path()
        val steps = 80
        val span = r * 1.6f
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = cx - span / 2f + t * span
            // Wavy EQ-like curve
            val phase = animatedPhase + t * 2 * PI.toFloat()
            val y = cy +
                (sin(phase) * r * 0.25f).toFloat() -
                (sin(phase * 2f + 1f) * r * 0.15f).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // Glow underlay + crisp top
        drawPath(path, color = RecorderOrange.copy(alpha = 0.25f), style = Stroke(width = 9f))
        drawPath(path, color = RecorderOrange, style = Stroke(width = 3f))

        // Center accent dot
        drawCircle(RecorderYellow, radius = r * 0.08f, center = Offset(cx, cy))
        drawCircle(Color(0xFF0C0C10), radius = r * 0.04f, center = Offset(cx, cy))
    }
}
