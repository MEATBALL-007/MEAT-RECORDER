package com.example.recorderproject.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile

private val MeatOrange = Color(0xFFFA4616)
private val MeatYellow = Color(0xFFFFC72C)

/**
 * Bottom mini-player — matches 22 May dark card aesthetic, no glass blur.
 *
 * Hidden by default. AnimatedVisibility slides up from the bottom when a file
 * is selected. Layout:
 *
 *  ┌──────────────────────────────────────────────────┐
 *  │ [▶/❚❚]  scene1_take1 · 2:43           [×]        │
 *  │ ════════●════════════════ 1:24 / 2:43            │
 *  └──────────────────────────────────────────────────┘
 *
 * - 1dp orange top border for separator effect
 * - Background slight gradient (#1A1A1A → #131313)
 * - Play/pause = 40dp orange circle with line icon
 * - Scrubber = orange thumb + orange filled, white-alpha track
 * - Close = small × icon, white@45%
 */
@Composable
fun MeatRecMiniPlayer(
    file: RecordFile?,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = file != null,
        enter = fadeIn() + expandVertically(animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically(animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)),
        modifier = modifier,
    ) {
        if (file == null) return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A1A1A), Color(0xFF131313)),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MeatOrange.copy(alpha = 0.55f),
                            MeatOrange.copy(alpha = 0.20f),
                            MeatYellow.copy(alpha = 0.25f),
                        ),
                    ),
                    shape = RoundedCornerShape(0.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Play/pause button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(MeatOrange)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        IconLinePause(tint = Color.White, size = 16.dp)
                    } else {
                        IconLinePlay(tint = Color.White, size = 16.dp)
                    }
                }

                // Title + duration meta
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name.substringBeforeLast(".").ifBlank { file.name },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Close
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    IconLineX(tint = Color.White.copy(alpha = 0.45f), size = 14.dp)
                }
            }

            // Scrubber
            Slider(
                value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { onSeek((it * durationMs).toInt().coerceAtLeast(0)) },
                colors = SliderDefaults.colors(
                    thumbColor = MeatOrange,
                    activeTrackColor = MeatOrange,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                ),
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )
        }
    }
}

private fun formatMs(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val m = s / 60
    return "%d:%02d".format(m, s % 60)
}
