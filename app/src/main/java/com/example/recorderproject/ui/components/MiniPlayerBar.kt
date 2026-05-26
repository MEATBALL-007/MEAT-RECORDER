package com.example.recorderproject.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Persistent mini player bar — pinned bottom of RecorderApp when a file is selected.
 *
 * Apple-style: glass-card surface, Play / Pause morph with spring scale, scrubbing
 * slider, expandable controls on tap. Visible state controlled by [file] != null.
 *
 * Tap row to expand (loop / speed / volume / close). Mini view shows only essentials.
 */
@Composable
fun MiniPlayerBar(
    file: RecordFile?,
    isPlaying: Boolean,
    isReady: Boolean,
    positionMs: Int,
    durationMs: Int,
    speed: Float,
    loop: Boolean,
    volume: Float,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onClose: () -> Unit,
    onToggleLoop: () -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onChangeVolume: (Float) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    AnimatedVisibility(
        visible = file != null,
        enter = fadeIn() + expandVertically(animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)),
        exit = fadeOut() + shrinkVertically(animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)),
    ) {
        if (file == null) return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RecorderCharcoalCard)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Top row: play / file name / close
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlayPauseButton(isPlaying = isPlaying, enabled = isReady, onClick = onPlayPause)
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    Text(
                        "${formatMs(positionMs)} / ${formatMs(durationMs)}" +
                            (if (speed != 1f) "  · ${"%.1f".format(speed)}x" else "") +
                            (if (loop) "  · loop" else ""),
                        color = RecorderBlueGrey,
                        style = LocalAppTypography.current.numericSmall,
                        fontSize = 11.sp,
                    )
                }
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close player",
                    tint = RecorderBlueGrey,
                    modifier = Modifier.size(20.dp).clickable(onClick = onClose),
                )
            }
            // Scrubber
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { onSeek((it * durationMs).toInt().coerceAtLeast(0)) },
                colors = SliderDefaults.colors(
                    thumbColor = RecorderOrange,
                    activeTrackColor = RecorderOrange,
                    inactiveTrackColor = RecorderBlueGrey.copy(alpha = 0.4f),
                ),
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )

            // Expanded controls
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Speed chips
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Speed", color = RecorderBlueGrey, fontSize = 11.sp)
                        listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
                            val active = s == speed
                            Text(
                                "${if (s == 1f) "1.0" else "%.1f".format(s)}x",
                                color = if (active) Color.White else RecorderBlueGrey,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) RecorderOrange else Color(0xFF0C0C10))
                                    .clickable { onChangeSpeed(s) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            if (loop) "🔁 loop" else "↪ once",
                            color = if (loop) RecorderYellow else RecorderBlueGrey,
                            fontWeight = if (loop) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0C0C10))
                                .clickable(onClick = onToggleLoop)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    // Volume slider
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔊", color = RecorderBlueGrey)
                        Slider(
                            value = volume,
                            onValueChange = onChangeVolume,
                            modifier = Modifier.weight(1f).height(20.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = RecorderYellow,
                                activeTrackColor = RecorderYellow,
                                inactiveTrackColor = RecorderBlueGrey.copy(alpha = 0.4f),
                            ),
                        )
                        Text("${(volume * 100).toInt()}", color = RecorderBlueGrey, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // Spring-bounced scale when state flips — feels Apple-y.
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "playScale",
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) RecorderOrange else RecorderBlueGrey)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isPlaying) "❚❚" else "▶",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatMs(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val m = s / 60
    return "%d:%02d".format(m, s % 60)
}
