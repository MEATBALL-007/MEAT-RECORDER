package com.example.recorderproject.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Pro circular Record button — large centered circle with morphing icon (filled dot → rounded
 * square), press scale, ripple, concentric breathing rings while recording.
 */
@Composable
fun CircleRecordButton(
    isRecording: Boolean,
    onTap: () -> Unit,
    size: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    var pressing by remember { mutableStateOf(false) }
    var rippleId by remember { mutableStateOf(0) }

    val infinite = rememberInfiniteTransition(label = "circleRecPulse")
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween<Float>(1600, easing = LinearEasing)),
        label = "breath",
    )

    val pressScale by animateFloatAsState(
        if (pressing) 0.9f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "pressScale",
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .size(size)
            .pointerInput(isRecording) {
                detectTapGestures(
                    onPress = {
                        pressing = true
                        rippleId++
                        tryAwaitRelease()
                        pressing = false
                        onTap()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Outer breathing ring (only while recording)
        if (isRecording) {
            val ringAlpha = (0.15f + 0.25f * kotlin.math.sin(breath * Math.PI.toFloat() * 2f)).coerceIn(0.1f, 0.45f)
            val ringScale = 1f + 0.06f * kotlin.math.sin(breath * Math.PI.toFloat() * 2f)
            Box(
                modifier = Modifier
                    .scale(ringScale)
                    .size(size)
                    .clip(CircleShape)
                    .background(RecorderOrange.copy(alpha = ringAlpha))
            )
        }

        // Subtle pulse rings expanding outward
        if (isRecording) {
            Canvas(modifier = Modifier.size(size)) {
                val maxR = this.size.maxDimension / 2f
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                for (i in 0..2) {
                    val phase = ((breath + i * 0.33f) % 1f)
                    val r = maxR * (0.7f + phase * 0.6f)
                    val alpha = (1f - phase) * 0.4f
                    drawCircle(
                        color = RecorderYellow.copy(alpha = alpha),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f),
                    )
                }
            }
        }

        // Main button surface — radial gradient orange
        Box(
            modifier = Modifier
                .size(size * 0.82f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0f to RecorderOrange,
                        0.7f to RecorderOrange,
                        1f to Color(0xFFCC380F),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Morphing icon: dot (Record) ↔ rounded square (Stop)
            AnimatedContent(
                targetState = isRecording,
                transitionSpec = {
                    (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(tween(180))) togetherWith
                        (scaleOut(spring(Spring.DampingRatioMediumBouncy)) + fadeOut(tween(120)))
                },
                label = "iconMorph",
            ) { rec ->
                if (rec) {
                    Box(
                        modifier = Modifier
                            .size(size * 0.32f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(size * 0.44f)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}
