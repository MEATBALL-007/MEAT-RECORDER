package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.components.HomeFileRow
import com.example.recorderproject.ui.components.IconLineMenu
import com.example.recorderproject.ui.components.IconLineSearch
import com.example.recorderproject.ui.components.IconLineSettings
import com.example.recorderproject.ui.components.IconLineX
import com.example.recorderproject.ui.components.RecordPill
import com.example.recorderproject.ui.theme.LocalAppTypography

/**
 * Apple-style library-first home screen.
 *
 * Vertical stack, top to bottom:
 *  1. Compact top action bar: menu (left) · settings (right). No big wordmark.
 *  2. Large title "Studio" + subtitle stats line ("N recordings · Xh Ym total")
 *  3. Search field (filled, capsule, faint)
 *  4. LazyColumn of HomeFileRow with hairline separators between rows
 *  5. Floating RecordPill pinned to the bottom with a glass backdrop
 *
 * Color rules:
 *  - Background: true black (#000) for OLED contrast
 *  - Primary label: white
 *  - Secondary label: white @ 60%
 *  - Tertiary label: white @ 30%
 *  - Separators: white @ 8%
 *  - Brand orange used only on the record pill + active row tint
 *
 * Typography rules:
 *  - Large title: 34sp Bold (tight tracking)
 *  - Subtitle: 14sp Regular (secondary)
 *  - File row title: 16sp Medium · meta 13sp
 */
@Composable
fun AppleHomeScreen(
    files: List<RecordFile>,
    isRecording: Boolean,
    elapsedSeconds: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFile: RecordFile?,
    isPlaying: Boolean,
    onTapFile: (RecordFile) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onExpandRecording: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val totalSeconds = files.sumOf { it.durationSeconds.toLong() }
    val statsLine = "${files.size} recording${if (files.size == 1) "" else "s"} · ${formatTotalDuration(totalSeconds)}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top action bar — compact, no big brand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable(onClick = onOpenMenu),
                    contentAlignment = Alignment.Center,
                ) {
                    IconLineMenu(tint = Color.White, size = 20.dp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    IconLineSettings(tint = Color.White, size = 20.dp)
                }
            }

            // 2. Large title + subtitle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Studio",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = statsLine,
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 14.sp,
                )
            }

            // 3. Search field — capsule, filled, faint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconLineSearch(tint = Color.White.copy(alpha = 0.45f), size = 16.dp)
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Color(0xFFFA4616)),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                                }
                                inner()
                            },
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Box(modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable { onSearchChange("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            IconLineX(tint = Color.White.copy(alpha = 0.7f), size = 12.dp)
                        }
                    }
                }
            }

            // 4. Section label "Recent" — tiny tracked caps for hierarchy
            if (files.isNotEmpty()) {
                Text(
                    text = "RECENT",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            // 5. Library list — hairline separators between rows
            if (files.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                ) {
                    items(files, key = { it.id }) { file ->
                        HomeFileRow(
                            file = file,
                            isPlaying = isPlaying && selectedFile?.id == file.id,
                            isSelected = selectedFile?.id == file.id,
                            onTap = { onTapFile(file) },
                        )
                        // Hairline separator inset from waveform thumb
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 90.dp)
                                .height(0.5.dp)
                                .background(Color.White.copy(alpha = 0.08f)),
                        )
                    }
                    // Bottom spacer so floating pill doesn't cover last row
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }
            }
        }

        // 6. Floating record pill — pinned bottom with a tall fade to draw eye
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            RecordPill(
                isRecording = isRecording,
                elapsedSeconds = elapsedSeconds,
                onStart = onStartRecording,
                onStop = onStopRecording,
                onExpand = onExpandRecording,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No recordings yet",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap Record to capture your first take.",
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 14.sp,
        )
    }
}

/** "4h 23m" / "23m" / "42s" — shortest sensible form. */
private fun formatTotalDuration(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val mins = seconds / 60
    if (mins < 60) return "${mins}m"
    val h = mins / 60
    val m = mins % 60
    return if (m == 0L) "${h}h" else "${h}h ${m}m"
}
