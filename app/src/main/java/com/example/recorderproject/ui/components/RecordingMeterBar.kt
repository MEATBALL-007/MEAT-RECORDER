package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

private fun Modifier.androidx_clickable(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

/**
 * Recording in-progress card: shows mm:ss elapsed timer + rolling rms-history meter.
 * `levels` is the rolling RMS history (newest at end, 0..1 range).
 */
@Composable
fun RecordingMeterBar(
    elapsedSeconds: Int,
    levels: List<Float>,
    cueCount: Int = 0,
    onDropCue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
    ) {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text("REC", color = RecorderOrange, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "📍 $cueCount",
                        color = RecorderYellow,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C0C10))
                            .androidx_clickable(onDropCue)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                        color = RecorderYellow,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 8.dp),
            ) {
                val w = size.width; val h = size.height
                if (levels.isEmpty()) {
                    // Quiet baseline
                    drawLine(
                        color = RecorderBlueGrey.copy(alpha = 0.3f),
                        start = Offset(0f, h / 2),
                        end = Offset(w, h / 2),
                        strokeWidth = 1.5f,
                    )
                    return@Canvas
                }
                val barWidth = w / levels.size.toFloat()
                for ((i, lvl) in levels.withIndex()) {
                    val barH = (lvl.coerceIn(0f, 1f) * h * 0.9f) + 2f
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to RecorderYellow,
                            0.7f to RecorderOrange,
                            1f to RecorderOrange.copy(alpha = 0.6f),
                        ),
                        topLeft = Offset(i * barWidth, (h - barH) / 2f),
                        size = Size(barWidth - 1f, barH),
                    )
                }
            }
        }
    }
}
