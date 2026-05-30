package com.example.recorderproject.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile

private val MeatOrange = Color(0xFFFA4616)
private val MeatYellow = Color(0xFFFFC72C)
private val CardBg = Color(0xFF161616)
private val RowBg = Color(0xFF1F1F1F)

/**
 * Recordings list card — matches 22 May visual language.
 *
 * Header: orange vertical bar | "Recordings · N" + chevron (expandable)
 * Body: list of HomeFileRow items with hairline separators
 *
 * Empty state: small italic "No recordings yet" line.
 *
 * Long-press a row → 7-item dropdown (Star · Lock · Share · Rename · Trim ·
 * Open EQ · Delete).
 *
 * Selected file (currently playing) gets an orange-left-edge accent.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsListCard(
    files: List<RecordFile>,
    selectedId: String?,
    isPlaying: Boolean,
    onTap: (RecordFile) -> Unit,
    onShare: (RecordFile) -> Unit,
    onRename: (RecordFile, String) -> Unit,
    onToggleStar: (RecordFile) -> Unit,
    onToggleLock: (RecordFile) -> Unit,
    onDelete: (RecordFile) -> Unit,
    onOpenTrim: (RecordFile) -> Unit,
    onOpenEQ: (RecordFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else 180f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "recChevron",
    )

    var deleteCandidate by remember { mutableStateOf<RecordFile?>(null) }
    var renameCandidate by remember { mutableStateOf<RecordFile?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1B1B1B), Color(0xFF131313)),
                ),
            )
            .animateContentSize(
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MeatOrange),
                )
                Text(
                    "Recordings",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (files.isNotEmpty()) {
                    Text(
                        "${files.size}",
                        color = MeatYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFC72C))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                "▲",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp,
                modifier = Modifier.rotate(chevronRotation),
            )
        }

        if (expanded) {
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No recordings yet — tap the orange button to start.",
                        color = Color.White.copy(alpha = 0.40f),
                        fontSize = 13.sp,
                    )
                }
            } else {
                Column {
                    files.forEachIndexed { idx, file ->
                        RecordingRow(
                            file = file,
                            selected = file.id == selectedId,
                            playing = isPlaying && file.id == selectedId,
                            onTap = { onTap(file) },
                            onShare = { onShare(file) },
                            onRequestRename = { renameCandidate = file },
                            onToggleStar = { onToggleStar(file) },
                            onToggleLock = { onToggleLock(file) },
                            onRequestDelete = { deleteCandidate = file },
                            onOpenTrim = { onOpenTrim(file) },
                            onOpenEQ = { onOpenEQ(file) },
                        )
                        if (idx < files.lastIndex) {
                            // Hairline separator inset from waveform
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 64.dp)
                                    .height(0.5.dp)
                                    .background(Color.White.copy(alpha = 0.08f)),
                            )
                        }
                    }
                }
            }
        }
    }

    deleteCandidate?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = CardBg,
            titleContentColor = MeatYellow,
            textContentColor = Color.White,
            title = { Text("Delete recording?") },
            text = { Text(f.name, color = Color.White.copy(alpha = 0.6f)) },
            confirmButton = {
                TextButton(onClick = { onDelete(f); deleteCandidate = null }) {
                    Text("Delete", color = MeatOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
        )
    }

    renameCandidate?.let { f ->
        var newName by remember(f.id) { mutableStateOf(f.name.substringBeforeLast(".")) }
        AlertDialog(
            onDismissRequest = { renameCandidate = null },
            containerColor = CardBg,
            titleContentColor = MeatYellow,
            textContentColor = Color.White,
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MeatOrange,
                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = MeatOrange,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onRename(f, newName.trim()); renameCandidate = null },
                    enabled = newName.isNotBlank(),
                ) {
                    Text("Rename", color = MeatOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameCandidate = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    file: RecordFile,
    selected: Boolean,
    playing: Boolean,
    onTap: () -> Unit,
    onShare: () -> Unit,
    onRequestRename: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleLock: () -> Unit,
    onRequestDelete: () -> Unit,
    onOpenTrim: () -> Unit,
    onOpenEQ: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val rowAccentAlpha = if (selected) 1f else 0f

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Orange left-edge accent when selected
        Box(
            modifier = Modifier
                .padding(start = 0.dp)
                .size(width = 3.dp, height = 56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MeatOrange.copy(alpha = rowAccentAlpha)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = { menuOpen = true },
                )
                .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Mini waveform thumbnail
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0E0E0E)),
                contentAlignment = Alignment.Center,
            ) {
                MiniWaveform(path = file.path, bins = 24, width = 48.dp, height = 32.dp)
            }

            // Name + meta
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        file.name.substringBeforeLast(".").ifBlank { file.name },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (file.starred) {
                        Text("★", color = MeatYellow, fontSize = 12.sp)
                    }
                    if (file.isLocked) {
                        Text("●", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        formatDur(file.durationSeconds),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                    )
                    if (file.hasEQ) {
                        Text(
                            "EQ",
                            color = MeatOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33FA4616))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    if (file.hasNoiseReduction) {
                        Text(
                            "NR",
                            color = MeatYellow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33FFC72C))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    val dr = file.deliveryResult
                    if (dr != null && dr.targetLufs != null) {
                        val passed = dr.passed
                        val badgeColor = if (passed) MeatOrange else Color(0xFF7B8189)
                        val badgeBg = if (passed) Color(0x33FA4616) else Color(0x337B8189)
                        Text(
                            text = "%d".format(dr.targetLufs.toInt()),
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBg)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                if (file.deliveryPath != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    ) {
                        Text(
                            text = "↳ " + java.io.File(file.deliveryPath).name,
                            fontSize = 11.sp,
                            color = Color(0xFF7B8189),
                        )
                    }
                }
            }

            // Right-side affordance: play/pause when selected, else simple chevron
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) MeatOrange else Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                if (playing) {
                    IconLinePause(tint = Color.White, size = 14.dp)
                } else {
                    IconLinePlay(tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f), size = 14.dp)
                }
            }

            // Dropdown menu anchored to row
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (file.starred) "Unstar" else "Star") },
                    onClick = { menuOpen = false; onToggleStar() },
                )
                DropdownMenuItem(
                    text = { Text(if (file.isLocked) "Unlock" else "Lock") },
                    onClick = { menuOpen = false; onToggleLock() },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = { menuOpen = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuOpen = false; onRequestRename() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Trim") },
                    onClick = { menuOpen = false; onOpenTrim() },
                )
                DropdownMenuItem(
                    text = { Text("Open in EQ") },
                    onClick = { menuOpen = false; onOpenEQ() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = MeatOrange) },
                    onClick = { menuOpen = false; onRequestDelete() },
                )
            }
        }
    }
}

private fun formatDur(s: Int): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
