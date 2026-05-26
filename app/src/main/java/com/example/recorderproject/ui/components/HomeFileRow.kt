package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile

/**
 * Rich file row — Apple-style: waveform thumbnail | title + meta | play button.
 *
 * Each row breathes: 72 dp tall, generous internal padding, hairline separator
 * baked into the parent column rather than rendered here.
 *
 * Visual rules:
 *  - Title: 16sp · medium · primary label color
 *  - Subtitle: 13sp · regular · secondary label color (~60% alpha)
 *  - Waveform thumbnail: 60×32 · rendered by MiniWaveform
 *  - Active row: subtle bg tint (RecorderOrange at 8% alpha)
 *  - Press: spring scale to 0.985 (very subtle — Apple-restrained)
 */
@Composable
fun HomeFileRow(
    file: RecordFile,
    isPlaying: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "rowScale",
    )

    val bg = if (isSelected) Color(0xFFFA4616).copy(alpha = 0.08f) else Color.Transparent

    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .background(bg)
            .clickable {
                pressed = true
                onTap()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Waveform thumbnail
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center,
        ) {
            MiniWaveform(path = file.path, bins = 28, width = 56.dp, height = 32.dp)
        }

        // Title + meta
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = file.name.substringBeforeLast(".").ifBlank { file.name },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatDuration(file.durationSeconds),
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 13.sp,
                )
                if (file.sceneName.isNotBlank() && file.sceneName != "Scene 1") {
                    Text("·", color = Color.White.copy(alpha = 0.30f), fontSize = 13.sp)
                    Text(
                        text = file.sceneName,
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                // Flag badges as small dots/letters, monochrome
                if (file.starred) {
                    Text("·", color = Color.White.copy(alpha = 0.30f), fontSize = 13.sp)
                    Text("starred", color = Color(0xFFFFC72C), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                if (file.hasEQ) {
                    Text("·", color = Color.White.copy(alpha = 0.30f), fontSize = 13.sp)
                    Text("EQ", color = Color(0xFFFA4616), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Right affordance: play indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isPlaying) Color(0xFFFA4616)
                    else Color.White.copy(alpha = 0.06f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) {
                IconLinePause(tint = Color.White, size = 14.dp)
            } else {
                IconLinePlay(tint = Color.White.copy(alpha = 0.85f), size = 14.dp)
            }
        }
    }

    // Release press after a frame for spring rebound
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}

/** Apple-friendly duration "0:42" or "3:21" or "1:02:33" */
private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
