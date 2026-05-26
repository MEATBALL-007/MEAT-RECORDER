package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Circular FFT-band visualization — port-back of old MEATrec HarmonicPortraitView.
 *
 * Renders each of the N input bands as a petal-shaped wedge radiating from the
 * center, length proportional to that band's magnitude (0..1).
 *
 * Each petal is a cubic-bezier wedge — control points pulled toward the center
 * at 0.55*r make the wedge sides curve in slightly, giving petals organic shape
 * rather than pointy triangles.
 *
 * Backdrop: 2 faint concentric reference circles + N radial guide lines, plus a
 * central yellow pip for visual anchor.
 *
 * @param bands FFT band magnitudes (0..1). Recommended 24–48 bands for a clean
 *   visual. More bands → finer petals; fewer → chunkier.
 */
@Composable
fun HarmonicPortraitView(
    bands: FloatArray,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (bands.isEmpty()) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = min(size.width, size.height) / 2f * 0.92f
        val n = bands.size

        // Two faint reference circles — orient the eye.
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = maxR,
            center = Offset(cx, cy),
            style = Stroke(width = 1f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.04f),
            radius = maxR * 0.5f,
            center = Offset(cx, cy),
            style = Stroke(width = 1f),
        )

        // Radial guide lines (faint, every band)
        for (k in 0 until n) {
            val angle = 2f * PI.toFloat() * k / n.toFloat() - PI.toFloat() / 2f
            drawLine(
                color = Color.White.copy(alpha = 0.04f),
                start = Offset(cx, cy),
                end = Offset(cx + cos(angle) * maxR, cy + sin(angle) * maxR),
                strokeWidth = 0.5f,
            )
        }

        // Each band → one petal wedge.
        val petalWidthFraction = 0.78f / n.toFloat() // share of full sweep per band's wedge
        for (k in 0 until n) {
            val centerAngle = 2f * PI.toFloat() * k / n.toFloat() - PI.toFloat() / 2f
            val halfSpan = PI.toFloat() * petalWidthFraction
            val angleA = centerAngle - halfSpan
            val angleB = centerAngle + halfSpan

            val magnitude = bands[k].coerceIn(0f, 1f)
            val r = maxR * magnitude
            if (r <= 0f) continue

            // Color interpolation: orange near center → yellow at peak.
            val hue = lerpColor(RecorderOrange, RecorderYellow, magnitude)

            // Cubic-bezier petal path
            val cp1 = Offset(cx + cos(angleA) * r * 0.55f, cy + sin(angleA) * r * 0.55f)
            val cp2 = Offset(cx + cos(angleB) * r * 0.55f, cy + sin(angleB) * r * 0.55f)
            val tip = Offset(cx + cos(centerAngle) * r, cy + sin(centerAngle) * r)
            val petal = Path().apply {
                moveTo(cx, cy)
                cubicTo(cp1.x, cp1.y, cp1.x, cp1.y, tip.x, tip.y)
                cubicTo(cp2.x, cp2.y, cp2.x, cp2.y, cx, cy)
                close()
            }
            drawPath(petal, hue.copy(alpha = 0.85f), style = Fill)
        }

        // Centre pip — yellow with white halo
        drawCircle(
            color = RecorderYellow,
            radius = 6.dp.toPx(),
            center = Offset(cx, cy),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = 2.dp.toPx(),
            center = Offset(cx, cy),
        )
    }
}

/** Simple RGB lerp between two colors (alpha taken from [b]). */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * clamped,
        green = a.green + (b.green - a.green) * clamped,
        blue = a.blue + (b.blue - a.blue) * clamped,
        alpha = b.alpha,
    )
}
