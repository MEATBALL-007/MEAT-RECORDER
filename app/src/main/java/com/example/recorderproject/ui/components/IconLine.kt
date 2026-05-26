package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Line-icon library — replaces emojis everywhere.
 *
 * Each icon is a Composable that draws into a square Canvas. Default stroke is
 * ~1.4 dp (modulated by density). Caps are Round so the lines feel precise but
 * never sharp-edged. Colors default to the caller-provided [tint].
 *
 * Conventions:
 *  - `tint` is the stroke color (required)
 *  - `size` is the bounding square — default 18 dp
 *  - All icons are designed at a logical 24×24 grid then rendered to [size]
 */

private val DefaultSize = 18.dp
private const val StrokeBase = 1.4f

@Composable
fun IconLineMic(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        // Capsule body
        val p = Path()
        p.addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = w * 0.36f, top = h * 0.10f, right = w * 0.64f, bottom = h * 0.58f,
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            )
        )
        drawPath(p, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
        // Cradle arc
        drawArc(
            color = tint,
            startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.34f),
            size = Size(w * 0.52f, w * 0.52f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        // Stand
        drawLine(tint, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.85f),
            strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.85f), Offset(w * 0.66f, h * 0.85f),
            strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun IconLineDisc(tint: Color, fill: Color? = null, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        val r = w * 0.42f
        if (fill != null) drawCircle(fill, r, Offset(w / 2, h / 2))
        drawCircle(tint, r, Offset(w / 2, h / 2),
            style = Stroke(width = sw, cap = StrokeCap.Round))
        drawCircle(tint, w * 0.09f, Offset(w / 2, h / 2))
    }
}

@Composable
fun IconLinePlay(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        val p = Path().apply {
            moveTo(w * 0.32f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.5f)
            lineTo(w * 0.32f, h * 0.78f)
            close()
        }
        drawPath(p, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

@Composable
fun IconLinePause(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density * 1.4f
        drawLine(tint, Offset(w * 0.36f, h * 0.22f), Offset(w * 0.36f, h * 0.78f),
            strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.64f, h * 0.22f), Offset(w * 0.64f, h * 0.78f),
            strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun IconLineStop(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        drawRect(
            color = tint,
            topLeft = Offset(w * 0.28f, h * 0.28f),
            size = Size(w * 0.44f, h * 0.44f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun IconLineEq(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density * 1.3f
        drawLine(tint, Offset(w * 0.25f, h * 0.32f), Offset(w * 0.25f, h * 0.85f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.85f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.75f, h * 0.45f), Offset(w * 0.75f, h * 0.85f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun IconLineSpectrum(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density * 1.3f
        val heights = floatArrayOf(0.30f, 0.55f, 0.42f, 0.75f, 0.50f, 0.28f, 0.62f)
        val barW = w / heights.size.toFloat()
        for (i in heights.indices) {
            drawLine(
                tint,
                Offset(i * barW + barW / 2, h * 0.85f),
                Offset(i * barW + barW / 2, h * (0.85f - heights[i] * 0.7f)),
                strokeWidth = sw, cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun IconLineWaveform(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        val cy = h / 2
        val path = Path().apply {
            moveTo(0f, cy)
            val n = 32
            for (i in 0..n) {
                val x = w * i / n.toFloat()
                val y = cy + sin(i * 0.7f).toFloat() * h * 0.30f
                lineTo(x, y)
            }
        }
        drawPath(path, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

@Composable
fun IconLineSettings(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        val cx = w / 2; val cy = h / 2
        val rOuter = w * 0.32f; val rInner = w * 0.14f
        val teeth = 8
        for (i in 0 until teeth) {
            val a = i * (2 * Math.PI / teeth)
            val x1 = cx + cos(a).toFloat() * rOuter * 0.92f
            val y1 = cy + sin(a).toFloat() * rOuter * 0.92f
            val x2 = cx + cos(a).toFloat() * rOuter * 1.45f
            val y2 = cy + sin(a).toFloat() * rOuter * 1.45f
            drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        }
        drawCircle(tint, rOuter, Offset(cx, cy), style = Stroke(width = sw, cap = StrokeCap.Round))
        drawCircle(tint, rInner, Offset(cx, cy), style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

@Composable
fun IconLineMenu(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density * 1.4f
        drawLine(tint, Offset(w * 0.18f, h * 0.30f), Offset(w * 0.82f, h * 0.30f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.18f, h * 0.70f), Offset(w * 0.82f, h * 0.70f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun IconLineLock(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        drawArc(
            color = tint,
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.30f, h * 0.18f),
            size = Size(w * 0.40f, w * 0.40f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.45f),
            size = Size(w * 0.56f, h * 0.40f),
            cornerRadius = CornerRadius(w * 0.04f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun IconLineStar(tint: Color, filled: Boolean = false, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        val cx = w / 2; val cy = h * 0.55f
        val rOuter = w * 0.42f; val rInner = rOuter * 0.42f
        val path = Path()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * (Math.PI / 5)
            val r = if (i % 2 == 0) rOuter else rInner
            val x = (cx + cos(a) * r).toFloat()
            val y = (cy + sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (filled) drawPath(path, tint)
        drawPath(path, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

@Composable
fun IconLineX(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density * 1.3f
        drawLine(tint, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun IconLineSearch(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        drawCircle(tint, w * 0.27f, Offset(w * 0.42f, h * 0.42f),
            style = Stroke(width = sw, cap = StrokeCap.Round))
        drawLine(tint, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.85f, h * 0.85f),
            strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
    }
}

@Composable
fun IconLineHeadphones(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        // Top arc (band)
        drawArc(
            color = tint,
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.25f),
            size = Size(w * 0.70f, w * 0.70f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        // Left cup
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.55f),
            size = Size(w * 0.18f, h * 0.28f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        // Right cup
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.67f, h * 0.55f),
            size = Size(w * 0.18f, h * 0.28f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun IconLineFilter(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.20f)
            lineTo(w * 0.85f, h * 0.20f)
            lineTo(w * 0.55f, h * 0.50f)
            lineTo(w * 0.55f, h * 0.80f)
            lineTo(w * 0.45f, h * 0.85f)
            lineTo(w * 0.45f, h * 0.50f)
            close()
        }
        drawPath(path, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

@Composable
fun IconLineArrowRight(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density * 1.3f
        drawLine(tint, Offset(w * 0.25f, h * 0.5f), Offset(w * 0.75f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.55f, h * 0.30f), Offset(w * 0.75f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.55f, h * 0.70f), Offset(w * 0.75f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}
