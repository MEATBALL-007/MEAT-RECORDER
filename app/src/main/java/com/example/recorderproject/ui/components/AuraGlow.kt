package com.example.recorderproject.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Soft radial aura behind the orange record button.
 *
 * Two layers:
 *   1. Outer halo — large, very faint, slow breathing scale
 *   2. Inner glow — smaller, brighter, faster breathing alpha
 *
 * When [recording] is true the aura tints red-hot (more saturated) and breathes
 * faster — gives a "live" sensation. Otherwise it's a subtle orange ambience.
 *
 * Drawn ON A LAYER BEHIND the button (wrap in Box and put aura first, button on top).
 */
@Composable
fun OrangeAura(
    diameter: Dp,
    recording: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "auraBreath")
    val periodMs = if (recording) 1400 else 2800
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val breath: Float = (sin(phase.toDouble()).toFloat() * 0.5f + 0.5f) // 0..1
    // MUCH subtler — aura should hint, not blast across the screen.
    val innerAlpha = if (recording) (0.18f + 0.08f * breath) else (0.05f + 0.03f * breath)
    val outerAlpha = if (recording) (0.08f + 0.04f * breath) else (0.02f + 0.02f * breath)

    val innerColor = Color(0xFFFA4616)
    val outerColor = if (recording) Color(0xFFFA4616) else Color(0xFFFA9112)

    // 1.35x of diameter — just enough to feather around the button, not engulf the screen
    Box(
        modifier = modifier
            .size(diameter * 1.35f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = size.minDimension / 2f

            // Outer feathered ring
            val outerR = baseR * (0.92f + 0.04f * breath)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        outerColor.copy(alpha = outerAlpha * 0.6f),
                        outerColor.copy(alpha = outerAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = outerR,
                ),
                radius = outerR,
                center = Offset(cx, cy),
            )

            // Inner soft glow — falls off fast so it stays close to the button
            val innerR = baseR * (0.68f + 0.04f * breath)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        innerColor.copy(alpha = innerAlpha),
                        innerColor.copy(alpha = innerAlpha * 0.3f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = innerR,
                ),
                radius = innerR,
                center = Offset(cx, cy),
            )
        }
    }
}

/**
 * Soft underglow strip that "leaks" from the bottom of the orange top bar —
 * a 8dp tall gradient that fades from orange@40% to transparent. Gives a
 * subtle depth cue under the bar without needing real shadow.
 */
@Composable
fun OrangeUnderglow(
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    color: Color = Color(0xFFFA4616),
) {
    Box(
        modifier = modifier
            .size(width = androidx.compose.ui.unit.Dp.Unspecified, height = height),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.35f),
                        color.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                ),
            )
        }
    }
}

/**
 * Ring of orange-yellow glow around an active pill / chip / theme card.
 * Use as a sibling layer with negative padding behind the active element.
 */
@Composable
fun GlowRing(
    cornerRadiusDp: Dp = 14.dp,
    intensity: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val r = cornerRadiusDp.toPx()
        // 3-layer glow: outermost largest + faintest
        val color = Color(0xFFFA4616)
        for (layer in 3 downTo 1) {
            val spread = layer * 6f
            drawRoundRect(
                color = color.copy(alpha = (0.16f / layer) * intensity),
                topLeft = Offset(-spread, -spread),
                size = androidx.compose.ui.geometry.Size(
                    width = size.width + spread * 2,
                    height = size.height + spread * 2,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r + spread, r + spread),
            )
        }
    }
}
