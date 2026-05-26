package com.example.recorderproject.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecorderMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mode selector — pixel-faithful rebuild of the 22 May APK fan-of-cards intro.
 *
 * Reference: recovery/screenshots/03-home.png
 *
 * Layout:
 *  - Black background
 *  - Top: "MEAT" orange + "REC" lighter grey, "SELECT RECORDING MODE" tracked caps
 *  - Bottom: 5 fanned cards (FILM / INTERVIEW / MUSIC / AMBIENCE / CUSTOM)
 *    spread in a slight arc, each rotated and offset. Each card shows:
 *      · large central icon (custom line drawing per mode)
 *      · mode name in tracked caps
 *      · accent color tint per mode
 *      · small pencil edit icon in the top-right
 *  - SKIP → text at the bottom, taps go to home with INTERVIEW default
 */
@Composable
fun MeatRecModeSelector(
    onSelectMode: (RecorderMode) -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top heading
        Row {
            Text(
                "MEAT",
                color = Color(0xFFFA4616),
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Text(
                "REC",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 14.dp, start = 4.dp),
            )
        }
        Text(
            "SELECT RECORDING MODE",
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 12.sp,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            // Fan-of-cards: 5 cards spread in an arc near the bottom
            FanOfModeCards(
                onSelectMode = onSelectMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            )
        }

        // SKIP →
        Text(
            "SKIP  →",
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 13.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onSkip)
                .padding(8.dp),
        )
    }
}

@Composable
private fun FanOfModeCards(
    onSelectMode: (RecorderMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = RecorderMode.entries
    Box(modifier = modifier.fillMaxWidth().height(280.dp)) {
        // Place cards from back to front (CUSTOM rightmost goes on top of FILM leftmost).
        // Offsets calibrated so the visible "spread" reads like the screenshot.
        modes.forEachIndexed { i, mode ->
            val center = (modes.size - 1) / 2f
            val offsetIndex = i - center
            val rotation = offsetIndex * 12f          // -24°, -12°, 0°, 12°, 24°
            val xOffset = (offsetIndex * 52f).dp       // horizontal spread
            val yOffset = (kotlin.math.abs(offsetIndex) * 14f).dp // arc dip
            FanCard(
                mode = mode,
                rotation = rotation,
                xOffset = xOffset,
                yOffset = yOffset,
                onClick = { onSelectMode(mode) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun FanCard(
    mode: RecorderMode,
    rotation: Float,
    xOffset: androidx.compose.ui.unit.Dp,
    yOffset: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "modePress",
    )

    Box(
        modifier = modifier
            .padding(start = xOffset.let { if (it < 0.dp) -it * 2 else 0.dp })
            .scale(pressScale)
            .width(108.dp)
            .height(168.dp)
            .rotate(rotation)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        mode.accent.copy(alpha = 0.45f),
                        mode.accent.copy(alpha = 0.10f),
                        Color(0xFF111111),
                    ),
                ),
            )
            .border(
                1.dp,
                Color.White.copy(alpha = 0.18f),
                RoundedCornerShape(14.dp),
            )
            .clickable {
                pressed = true
                onClick()
            }
            .padding(10.dp),
    ) {
        // Pencil edit icon top-right
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(16.dp),
        ) {
            val w = size.width; val h = size.height
            // Simple pencil outline — diagonal stroke
            val sw = 1.5f * density
            drawLine(
                color = mode.accent.copy(alpha = 0.85f),
                start = Offset(w * 0.18f, h * 0.78f),
                end = Offset(w * 0.78f, h * 0.18f),
                strokeWidth = sw * 2.5f, cap = StrokeCap.Round,
            )
            // Pencil tip — small triangle at start
            drawCircle(
                color = mode.accent,
                radius = w * 0.10f,
                center = Offset(w * 0.18f, h * 0.78f),
            )
            // Eraser cap — square at end
            drawRect(
                color = mode.accent,
                topLeft = Offset(w * 0.68f, h * 0.08f),
                size = Size(w * 0.20f, h * 0.20f),
            )
        }

        // Center icon
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            ModeIcon(mode = mode, size = 48.dp)
        }

        // Label at bottom
        Text(
            mode.displayName,
            color = mode.accent,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(160)
            pressed = false
        }
    }
}

@Composable
private fun ModeIcon(mode: RecorderMode, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val tint = mode.accent
        val sw = 2.5f * density

        when (mode) {
            RecorderMode.FILM -> {
                // Clapperboard: bottom rectangle + tilted top bar with diagonal stripes
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.50f),
                    size = Size(w * 0.76f, h * 0.40f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                // Top hinge bar
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.20f),
                    size = Size(w * 0.76f, h * 0.18f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                // Diagonal stripes inside the top bar
                for (k in 0..2) {
                    val x = w * (0.20f + k * 0.22f)
                    drawLine(
                        tint,
                        Offset(x, h * 0.20f),
                        Offset(x + w * 0.10f, h * 0.38f),
                        strokeWidth = sw, cap = StrokeCap.Round,
                    )
                }
            }
            RecorderMode.INTERVIEW -> {
                // Mic capsule + base
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.35f, h * 0.15f),
                    size = Size(w * 0.30f, h * 0.45f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.15f, w * 0.15f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                drawArc(
                    color = tint,
                    startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.25f, h * 0.35f),
                    size = Size(w * 0.50f, w * 0.50f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                drawLine(tint, Offset(w * 0.5f, h * 0.70f), Offset(w * 0.5f, h * 0.88f),
                    strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.88f), Offset(w * 0.70f, h * 0.88f),
                    strokeWidth = sw, cap = StrokeCap.Round)
            }
            RecorderMode.MUSIC -> {
                // 8th note: filled head + flag stem
                drawCircle(
                    color = tint,
                    radius = w * 0.13f,
                    center = Offset(w * 0.32f, h * 0.72f),
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.45f, h * 0.72f),
                    end = Offset(w * 0.45f, h * 0.18f),
                    strokeWidth = sw * 1.5f, cap = StrokeCap.Round,
                )
                // Flag — curve from top of stem
                val flag = Path().apply {
                    moveTo(w * 0.45f, h * 0.18f)
                    cubicTo(
                        w * 0.78f, h * 0.18f,
                        w * 0.80f, h * 0.40f,
                        w * 0.62f, h * 0.48f,
                    )
                }
                drawPath(flag, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
            RecorderMode.AMBIENCE -> {
                // Leaf: pointed oval with central vein
                val leaf = Path().apply {
                    moveTo(w * 0.5f, h * 0.10f)
                    cubicTo(
                        w * 0.95f, h * 0.30f,
                        w * 0.85f, h * 0.85f,
                        w * 0.50f, h * 0.90f,
                    )
                    cubicTo(
                        w * 0.15f, h * 0.85f,
                        w * 0.05f, h * 0.30f,
                        w * 0.50f, h * 0.10f,
                    )
                    close()
                }
                drawPath(leaf, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
                // Central vein
                drawLine(tint,
                    start = Offset(w * 0.50f, h * 0.15f),
                    end = Offset(w * 0.50f, h * 0.88f),
                    strokeWidth = sw, cap = StrokeCap.Round)
            }
            RecorderMode.CUSTOM -> {
                // Gear: 8-tooth ring
                val cx = w / 2; val cy = h / 2
                val rOuter = w * 0.32f; val rInner = w * 0.12f
                val teeth = 8
                for (i in 0 until teeth) {
                    val a = i * (2 * Math.PI / teeth)
                    val x1 = cx + cos(a).toFloat() * rOuter * 0.92f
                    val y1 = cy + sin(a).toFloat() * rOuter * 0.92f
                    val x2 = cx + cos(a).toFloat() * rOuter * 1.40f
                    val y2 = cy + sin(a).toFloat() * rOuter * 1.40f
                    drawLine(tint, Offset(x1, y1), Offset(x2, y2),
                        strokeWidth = sw, cap = StrokeCap.Round)
                }
                drawCircle(tint, rOuter, Offset(cx, cy),
                    style = Stroke(width = sw, cap = StrokeCap.Round))
                drawCircle(tint, rInner, Offset(cx, cy),
                    style = Stroke(width = sw, cap = StrokeCap.Round))
            }
        }
    }
}
