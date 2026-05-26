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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Hero section — the dominant visual moment of the home screen.
 *
 * Composition:
 *  - Radial gradient backdrop with a slow orange halo behind the record button
 *  - Status pill (REC red / Idle blue-grey) at the top
 *  - Big timecode (numericLarge typography, tabular figures)
 *  - File name underneath in tiny tracked caps
 *  - Record button (caller-provided as content slot) sits centered with a
 *    soft glow halo while recording
 *
 * Heights are loose so the gradient feels airy rather than crammed.
 */
@Composable
fun HeroRecordSection(
    isRecording: Boolean,
    elapsedSeconds: Int,
    fileName: String,
    sampleRateLabel: String,
    bitDepthLabel: String,
    modifier: Modifier = Modifier,
    recordButton: @Composable () -> Unit,
) {
    // Slow breathing halo when recording
    val infinite = rememberInfiniteTransition(label = "heroBreath")
    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween<Float>(3600, easing = LinearEasing)),
        label = "breath",
    )
    val haloAlpha: Float = if (isRecording) (0.18f + 0.08f * sin(breath.toDouble()).toFloat()).coerceIn(0.1f, 0.32f) else 0.06f
    val haloRadius by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0.7f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "haloRadius",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Atmospheric backdrop — radial gradient that gets warmer when recording
        Canvas(Modifier.fillMaxWidth().height(320.dp)) {
            val cx = size.width / 2f
            val cy = size.height * 0.55f
            val r = size.minDimension * 0.7f * haloRadius
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RecorderOrange.copy(alpha = haloAlpha),
                        RecorderOrange.copy(alpha = haloAlpha * 0.4f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            // Status pill — REC blinks softly during recording
            StatusPill(isRecording = isRecording)

            // Big timecode
            AnimatedContent(
                targetState = elapsedSeconds,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "elapsed",
            ) { secs ->
                Text(
                    text = formatBigTime(secs),
                    color = if (isRecording) RecorderOrange else Color.White,
                    fontWeight = FontWeight.Bold,
                    style = LocalAppTypography.current.numericLarge,
                    fontSize = 56.sp,
                    letterSpacing = 1.5.sp,
                )
            }

            // File + format label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    fileName.uppercase(),
                    color = RecorderBlueGrey,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("·", color = RecorderBlueGrey.copy(alpha = 0.5f))
                Text(
                    "$sampleRateLabel · $bitDepthLabel",
                    color = RecorderYellow.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Record button slot
            Box(modifier = Modifier.padding(top = 8.dp)) {
                recordButton()
            }
        }
    }
}

@Composable
private fun StatusPill(isRecording: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pillPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween<Float>(800, easing = LinearEasing)),
        label = "pulse",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (isRecording)
                        listOf(RecorderOrange.copy(alpha = 0.25f), RecorderOrange.copy(alpha = 0.10f))
                    else
                        listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.02f)),
                ),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(pulse)
                    .clip(RoundedCornerShape(4.dp))
                    .background(RecorderOrange),
            )
            Text(
                "RECORDING",
                color = RecorderOrange,
                fontSize = 10.sp,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(RecorderBlueGrey),
            )
            Text(
                "READY",
                color = RecorderBlueGrey,
                fontSize = 10.sp,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Format seconds as HH:MM:SS or MM:SS, monospace-friendly. */
private fun formatBigTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
