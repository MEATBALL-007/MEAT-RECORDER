package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Recording in-progress card with elapsed timer + rolling RMS meter.
 *
 * Phase F polish:
 *  - F2: pause/resume mini-button (yellow when paused)
 *  - F4: long-press the cue chip → opens label dialog (short tap = blank cue)
 *  - F5: time formatter switches to HH:MM:SS once recording crosses 1 hour
 *
 * @param levels Rolling RMS history (newest at end, 0..1 range).
 * @param onDropCue Called for short-tap on cue chip (no label).
 * @param onDropCueWithLabel Called for long-press → label dialog confirm.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingMeterBar(
    elapsedSeconds: Int,
    levels: List<Float>,
    cueCount: Int = 0,
    isPaused: Boolean = false,
    onDropCue: () -> Unit = {},
    onDropCueWithLabel: (String) -> Unit = {},
    onTogglePause: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var cueDialogOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isPaused) "PAUSED" else "REC",
                    color = if (isPaused) RecorderYellow else RecorderOrange,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Pause / resume mini-button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isPaused) RecorderYellow else Color(0xFF0C0C10))
                            .clickable(onClick = onTogglePause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isPaused) "▶" else "⏸",
                            color = if (isPaused) Color.Black else RecorderYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                    // Cue chip — short tap = drop blank cue, long press = label dialog
                    Text(
                        "📍 $cueCount",
                        color = RecorderYellow,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C0C10))
                            .combinedClickable(
                                onClick = onDropCue,
                                onLongClick = { cueDialogOpen = true },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        text = formatElapsed(elapsedSeconds),
                        color = RecorderYellow,
                        fontWeight = FontWeight.SemiBold,
                        style = LocalAppTypography.current.numericLarge,
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

    if (cueDialogOpen) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cueDialogOpen = false },
            containerColor = RecorderCharcoalCard,
            titleContentColor = RecorderYellow,
            textContentColor = Color.White,
            title = { Text("Drop cue with label") },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    placeholder = { Text("e.g. take 2 start", color = RecorderBlueGrey) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = RecorderOrange,
                        unfocusedIndicatorColor = RecorderBlueGrey,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDropCueWithLabel(label.trim())
                    cueDialogOpen = false
                }) {
                    Text("Drop", color = RecorderOrange, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cueDialogOpen = false }) {
                    Text("Cancel", color = RecorderBlueGrey)
                }
            },
        )
    }
}

/** F5: HH:MM:SS once recording crosses 1 hour, else MM:SS. */
internal fun formatElapsed(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
