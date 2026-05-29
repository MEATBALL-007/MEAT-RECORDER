package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * RECORDINGS list — port-back of old MEATrec FileCard 13-action surface.
 *
 * Action callbacks (mirrors the 13 the old app exposed):
 *  1. [onTapFile]              — select / play
 *  2. [onTapEQ]                — open EQ on this file
 *  3. [onShare]                — share / export
 *  4. [onDelete]               — delete (confirmed via dialog)
 *  5. [onToggleLock]           — toggle lock flag
 *  6. [onToggleStar]           — toggle star flag
 *  7. [onOpenSpectrogram]      — open spectrogram viewer
 *  8. [onRename]               — rename file (dialog handled inline)
 *  9. [onOpenInfo]             — show metadata dialog (handled inline)
 * 10. [onOpenPortrait]         — open harmonic portrait (stub until Phase C)
 * 11. [onSliceScenes]          — open scene slicer (stub until Phase E)
 * 12. [onSetGhostTake]         — set as ghost playback ref (stub until Phase D)
 * 13. [onDetectSync]           — detect sync markers (stub until Phase E)
 * 14. [onPitchShift]           — open pitch shift editor (stub until Phase E)
 * 15. [onApplyNR]              — run noise reduction on this take
 *
 * Empty defaults so call sites that haven't been wired yet still compile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingFileList(
    files: List<RecordFile>,
    onTapFile: (RecordFile) -> Unit,
    onTapEQ: (RecordFile) -> Unit,
    onShare: (RecordFile) -> Unit = {},
    onDelete: (RecordFile) -> Unit = {},
    onToggleLock: (RecordFile) -> Unit = {},
    onToggleStar: (RecordFile) -> Unit = {},
    onOpenSpectrogram: (RecordFile) -> Unit = {},
    onRename: (RecordFile, String) -> Unit = { _, _ -> },
    onOpenPortrait: (RecordFile) -> Unit = {},
    onSliceScenes: (RecordFile) -> Unit = {},
    onSetGhostTake: (RecordFile) -> Unit = {},
    onDetectSync: (RecordFile) -> Unit = {},
    onPitchShift: (RecordFile) -> Unit = {},
    onApplyNR: (RecordFile) -> Unit = {},
    selectedIds: Set<String> = emptySet(),
    onToggleSelect: (RecordFile) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selectionMode = selectedIds.isNotEmpty()
    if (files.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("No recordings yet — tap Record to start", color = RecorderBlueGrey)
        }
        return
    }
    var deleteCandidate by remember { mutableStateOf<RecordFile?>(null) }
    var renameCandidate by remember { mutableStateOf<RecordFile?>(null) }
    var infoCandidate by remember { mutableStateOf<RecordFile?>(null) }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(files, key = { it.id }) { file ->
            val selected = file.id in selectedIds
            Box(modifier = Modifier.animateItemPlacement(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            )) {
                FileRow(
                    file = file,
                    selected = selected,
                    onTap = {
                        if (selectionMode) onToggleSelect(file) else onTapFile(file)
                    },
                    onLongPress = {
                        // Long-press still opens the dropdown menu via FileRow's internal state.
                        // (Multi-select is entered via the "Select" menu item below.)
                    },
                    onSelect = { onToggleSelect(file) },
                    onTapEQ = { onTapEQ(file) },
                    onShare = { onShare(file) },
                    onRequestDelete = { deleteCandidate = file },
                    onToggleLock = { onToggleLock(file) },
                    onToggleStar = { onToggleStar(file) },
                    onOpenSpectrogram = { onOpenSpectrogram(file) },
                    onRequestRename = { renameCandidate = file },
                    onRequestInfo = { infoCandidate = file },
                    onOpenPortrait = { onOpenPortrait(file) },
                    onSliceScenes = { onSliceScenes(file) },
                    onSetGhostTake = { onSetGhostTake(file) },
                    onDetectSync = { onDetectSync(file) },
                    onPitchShift = { onPitchShift(file) },
                    onApplyNR = { onApplyNR(file) },
                )
            }
        }
    }

    deleteCandidate?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = RecorderCharcoalCard,
            titleContentColor = RecorderYellow,
            textContentColor = Color.White,
            title = { Text("Delete recording?") },
            text = { Text(f.name, color = RecorderBlueGrey) },
            confirmButton = {
                TextButton(onClick = { onDelete(f); deleteCandidate = null }) {
                    Text("Delete", color = RecorderOrange, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel", color = RecorderBlueGrey)
                }
            },
        )
    }

    renameCandidate?.let { f ->
        var newName by remember(f.id) { mutableStateOf(f.name.substringBeforeLast(".")) }
        AlertDialog(
            onDismissRequest = { renameCandidate = null },
            containerColor = RecorderCharcoalCard,
            titleContentColor = RecorderYellow,
            textContentColor = Color.White,
            title = { Text("Rename recording") },
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
                        focusedIndicatorColor = RecorderOrange,
                        unfocusedIndicatorColor = RecorderBlueGrey,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onRename(f, newName.trim()); renameCandidate = null },
                    enabled = newName.isNotBlank(),
                ) {
                    Text("Rename", color = RecorderOrange, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameCandidate = null }) {
                    Text("Cancel", color = RecorderBlueGrey)
                }
            },
        )
    }

    infoCandidate?.let { f ->
        AlertDialog(
            onDismissRequest = { infoCandidate = null },
            containerColor = RecorderCharcoalCard,
            titleContentColor = RecorderYellow,
            textContentColor = Color.White,
            title = { Text(f.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoLine("Duration", "${f.durationSeconds} s")
                    InfoLine("Path", f.path, mono = true)
                    InfoLine("Starred", if (f.starred) "Yes" else "No")
                    InfoLine("Locked", if (f.isLocked) "Yes" else "No")
                    InfoLine("Noise reduced", if (f.hasNoiseReduction) "Yes" else "No")
                    InfoLine("EQ applied", if (f.hasEQ) "Yes" else "No")
                }
            },
            confirmButton = {
                TextButton(onClick = { infoCandidate = null }) {
                    Text("Close", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = RecorderBlueGrey, fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
        Text(value, color = Color.White, fontSize = if (mono) 10.sp else 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: RecordFile,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelect: () -> Unit,
    onTapEQ: () -> Unit,
    onShare: () -> Unit,
    onRequestDelete: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenSpectrogram: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestInfo: () -> Unit,
    onOpenPortrait: () -> Unit,
    onSliceScenes: () -> Unit,
    onSetGhostTake: () -> Unit,
    onDetectSync: () -> Unit,
    onPitchShift: () -> Unit,
    onApplyNR: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val rowBg = if (selected) Color(0xFF3A1F0A) else RecorderCharcoalCard

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .combinedClickable(
                onClick = onTap,
                onLongClick = { menuOpen = true },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatElapsed(file.durationSeconds), color = RecorderBlueGrey, fontSize = 11.sp)
                if (file.starred) Badge("★")
                if (file.hasNoiseReduction) Badge("NR")
                if (file.hasEQ) Badge("EQ")
                if (file.isLocked) Badge("🔒")
            }
        }
        // F9: mini waveform thumbnail
        MiniWaveform(path = file.path)
        Text(
            "EQ →",
            color = RecorderOrange,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0C0C10))
                .combinedClickable(onClick = onTapEQ)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            // F8 entry point
            DropdownMenuItem(
                text = { Text(if (selected) "Deselect" else "Select") },
                onClick = { menuOpen = false; onSelect() },
            )
            HorizontalDivider()
            // Group 1 — quick file operations
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = { menuOpen = false; onRequestRename() },
            )
            DropdownMenuItem(
                text = { Text("Info") },
                onClick = { menuOpen = false; onRequestInfo() },
            )
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

            HorizontalDivider()

            // Group 2 — visualization
            DropdownMenuItem(
                text = { Text("Spectrogram") },
                onClick = { menuOpen = false; onOpenSpectrogram() },
            )
            DropdownMenuItem(
                text = { Text("Harmonic portrait") },
                onClick = { menuOpen = false; onOpenPortrait() },
            )

            HorizontalDivider()

            // Group 3 — processing
            DropdownMenuItem(
                text = { Text("Noise reduce") },
                onClick = { menuOpen = false; onApplyNR() },
            )
            DropdownMenuItem(
                text = { Text("Pitch shift") },
                onClick = { menuOpen = false; onPitchShift() },
            )
            DropdownMenuItem(
                text = { Text("Slice scenes") },
                onClick = { menuOpen = false; onSliceScenes() },
            )
            DropdownMenuItem(
                text = { Text("Detect sync") },
                onClick = { menuOpen = false; onDetectSync() },
            )
            DropdownMenuItem(
                text = { Text("Set as ghost take") },
                onClick = { menuOpen = false; onSetGhostTake() },
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Delete", color = RecorderOrange) },
                onClick = { menuOpen = false; onRequestDelete() },
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        color = RecorderYellow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1F1F23))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
