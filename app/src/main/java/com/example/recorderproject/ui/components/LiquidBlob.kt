package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.recorderproject.ui.theme.LocalReduceMotion
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Layered organic blob visualization — port-back of old MEATrec LiquidBlobKt.
 *
 * Renders 3 stacked, wobbling blob shapes in brand colors (orange, deep-orange,
 * yellow) drifting on different sine/cosine phases. Used as a splash backdrop
 * or recording-session decoration.
 *
 * Honors [LocalReduceMotion] — when reduce-motion is on, the time-driven wobble
 * freezes (entrance still completes once).
 */
@Composable
fun LiquidBlobCanvas(modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val time = remember { Animatable(0f) }
    val entrance = remember { Animatable(0.2f) }

    // Entrance scale-in (always plays once, even with reduce-motion — feels static otherwise)
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }

    // Continuous time drift for the wobble — skip if reduce-motion is on.
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            // 30s "period" — long enough that the motion feels organic rather than spinning.
            time.animateTo(PI.toFloat() * 4f, tween(durationMillis = 30_000, easing = LinearEasing))
        }
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = min(size.width, size.height) / 2f * 0.85f
        val t = time.value
        val e = entrance.value

        // Three layered blob stacks at differing phases / colors / sizes.
        drawBlobStack(
            center = Offset(cx + base * 0.33f * sin(t * 0.52f), cy + base * 0.28f * cos(t * 0.44f)),
            radius = base * 1.0f * e,
            time = t * 1.0f,
            color = RecorderOrange,
            coreAlpha = 0.82f,
        )
        drawBlobStack(
            center = Offset(cx - base * 0.29f * cos(t * 0.63f + 1.1f), cy + base * 0.23f * sin(t * 0.58f + 0.5f)),
            radius = base * 0.76f * e,
            time = t * 1.25f,
            color = Color(0xFFFA9112), // MeatDeepOrange
            coreAlpha = 0.68f,
        )
        drawBlobStack(
            center = Offset(cx + base * 0.22f * cos(t * 0.87f + 2.0f), cy - base * 0.33f * sin(t * 0.71f + 1.0f)),
            radius = base * 0.49f * e,
            time = t * 1.7f,
            color = RecorderYellow,
            coreAlpha = 0.56f,
        )
    }
}

/**
 * Draw a 3-layer blob: outer faint halo + main fill + bright inner highlight.
 */
private fun DrawScope.drawBlobStack(
    center: Offset,
    radius: Float,
    time: Float,
    color: Color,
    coreAlpha: Float,
) {
    // Outer halo — bigger, very faint
    drawPath(
        path = blobPath(center, radius * 1.35f, time + 0.42f),
        color = color.copy(alpha = coreAlpha * 0.25f),
    )
    // Core fill
    drawPath(
        path = blobPath(center, radius, time),
        color = color.copy(alpha = coreAlpha),
    )
    // Inner highlight — smaller, offset phase, semi-white
    drawPath(
        path = blobPath(center, radius * 0.52f, time * 1.1f + 1f),
        color = Color.White.copy(alpha = 0.30f),
    )
}

/**
 * Generate a wobbling closed Path approximating a blob:
 * N points around a circle, each pushed in/out by a stack of 3 sine wobbles
 * at different angular frequencies (creates organic, non-symmetric motion).
 * Path drawn as quadratic-bezier curves through midpoints for smoothness.
 */
private fun blobPath(center: Offset, radius: Float, time: Float, n: Int = 24): Path {
    val pts = Array(n) { i ->
        val angle = 2f * PI.toFloat() * i / n.toFloat()
        val wobble =
            (sin(1.88f * time + 0.84f * i.toFloat()) * 0.12f +
                sin(3.14f * time + 1.57f * i.toFloat()) * 0.09f +
                sin(5.28f * time + 2.30f * i.toFloat()) * 0.06f) * radius
        Offset(
            center.x + (radius + wobble) * cos(angle),
            center.y + (radius + wobble) * sin(angle),
        )
    }
    return Path().apply {
        moveTo((pts[n - 1].x + pts[0].x) / 2f, (pts[n - 1].y + pts[0].y) / 2f)
        for (i in 0 until n) {
            val curr = pts[i]
            val next = pts[(i + 1) % n]
            val midX = (curr.x + next.x) / 2f
            val midY = (curr.y + next.y) / 2f
            quadraticBezierTo(curr.x, curr.y, midX, midY)
        }
        close()
    }
}
