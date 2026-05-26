package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compact swatch dot showing a theme's 3-color triple — used in the theme picker.
 *
 * Layout: a circle split into a top-half wedge of [background] and a bottom-half
 * wedge of [surface], with a centered inner circle of [surfaceElevated]. White-faint
 * border lifts it off any background.
 */
@Composable
fun ColorDot(
    background: Color,
    surface: Color,
    surfaceElevated: Color,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        // Top half — background
        drawArc(
            color = background,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset.Zero,
            size = Size(this.size.width, this.size.height),
        )
        // Bottom half — surface
        drawArc(
            color = surface,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset.Zero,
            size = Size(this.size.width, this.size.height),
        )
        // Center pip — surfaceElevated
        drawCircle(
            color = surfaceElevated,
            radius = r * 0.42f,
            center = Offset(r, r),
        )
        // Outline so the swatch reads on either dark or light card surfaces
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = r,
            center = Offset(r, r),
            style = Stroke(width = 1.5f),
        )
    }
}
