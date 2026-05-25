package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderOrange
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * Big primary record/stop button with rich animation:
 *   - When idle (Record): press scale-down 1.0 → 0.93 → 1.0 with spring
 *   - When pressed: an expanding ripple ring radiates from the button center (~600ms)
 *   - When recording: the orange surface emits a slow breathing pulse (1.6s loop)
 *   - On stop: brief celebratory scale 1.06 → 1.0
 */
@Composable
fun RecordPulseButton(
    isRecording: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressing by remember { mutableStateOf(false) }
    var ripplePhase by remember { mutableStateOf(-1f) }
    var celebrate by remember { mutableStateOf(false) }

    // Breathing pulse while recording
    val infinite = rememberInfiniteTransition(label = "recPulse")
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween<Float>(1600, easing = LinearEasing)),
        label = "breath",
    )

    val pressScaleTarget = when {
        celebrate -> 1.06f
        pressing -> 0.93f
        else -> 1f
    }
    val pressScale by animateFloatAsState(
        pressScaleTarget,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        finishedListener = { if (celebrate) celebrate = false },
        label = "pressScale",
    )

    // Ripple animation: when triggered, animate radius 0→1 over 600ms
    val rippleRadius by animateFloatAsState(
        targetValue = if (ripplePhase >= 0f) 1f else 0f,
        animationSpec = tween(600, easing = LinearEasing),
        finishedListener = { ripplePhase = -1f },
        label = "ripple",
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(RecorderOrange)
            .pointerInput(isRecording) {
                detectTapGestures(
                    onPress = {
                        pressing = true
                        ripplePhase = 0f
                        tryAwaitRelease()
                        pressing = false
                        celebrate = isRecording // celebrate on stop press
                        onTap()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Breathing pulse overlay
        if (isRecording) {
            val pulseAlpha = (0.3f + 0.4f * kotlin.math.sin(breath * Math.PI.toFloat() * 2f)).coerceIn(0.15f, 0.6f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.radialGradient(
                            0f to Color.White.copy(alpha = pulseAlpha * 0.7f),
                            0.5f to Color.White.copy(alpha = pulseAlpha * 0.2f),
                            1f to Color.Transparent,
                        )
                    ),
            )
        }

        // Ripple
        if (rippleRadius > 0f && rippleRadius < 1f) {
            Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                val r = rippleRadius * size.maxDimension * 0.7f
                val alpha = (1f - rippleRadius) * 0.6f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = r,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 3f),
                )
            }
        }

        Text(
            text = if (isRecording) "■ Stop" else "● Record",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
    }
}
