package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.ui.theme.RecorderOrange

@Composable
fun ApplyButton(
    saveMode: ApplySaveMode,
    /** -1f = idle, 0..1 = rendering, exactly 1f shows checkmark briefly. */
    progress: Float,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRendering = progress in 0f..0.999f
    val isDone = progress >= 1f
    val scaleTarget = when {
        isRendering -> 0.96f
        else -> 1f
    }
    val scale by animateFloatAsState(
        scaleTarget,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "applyScale",
    )

    val label = when {
        isRendering -> "Rendering…"
        isDone -> "Applied"
        saveMode == ApplySaveMode.BOTH -> "Apply · save copy"
        saveMode == ApplySaveMode.EQ_ONLY -> "Apply · replace"
        saveMode == ApplySaveMode.ORIGINAL_ONLY -> "Discard EQ"
        else -> "Apply"
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(RecorderOrange)
            .clickable(enabled = !isRendering && !isDone, onClick = onApply)
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isRendering -> {
                Canvas(Modifier.size(28.dp)) {
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        style = Stroke(width = 4f),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, size.height),
                    )
                }
            }
            isDone -> {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            }
            else -> {
                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
