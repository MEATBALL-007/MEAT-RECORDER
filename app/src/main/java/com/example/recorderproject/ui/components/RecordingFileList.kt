package com.example.recorderproject.ui.components

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("No recordings yet — tap Record to start", color = RecorderBlueGrey)
        }
        return
    }
    var deleteCandidate by remember { mutableStateOf<RecordFile?>(null) }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(files) { file ->
            FileRow(
                file = file,
                onTap = { onTapFile(file) },
                onTapEQ = { onTapEQ(file) },
                onShare = { onShare(file) },
                onRequestDelete = { deleteCandidate = file },
                onToggleLock = { onToggleLock(file) },
                onToggleStar = { onToggleStar(file) },
                onOpenSpectrogram = { onOpenSpectrogram(file) },
            )
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: RecordFile,
    onTap: () -> Unit,
    onTapEQ: () -> Unit,
    onShare: () -> Unit,
    onRequestDelete: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenSpectrogram: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RecorderCharcoalCard)
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
                Text("${file.durationSeconds}s", color = RecorderBlueGrey, fontSize = 11.sp)
                if (file.starred) Badge("★")
                if (file.hasNoiseReduction) Badge("NR")
                if (file.hasEQ) Badge("EQ")
                if (file.isLocked) Badge("🔒")
            }
        }
        Text(
            "EQ →",
            color = RecorderOrange,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0C0C10))
                .combinedClickable(onClick = onTapEQ)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (file.starred) "Unstar" else "Star") },
                onClick = { menuOpen = false; onToggleStar() },
            )
            DropdownMenuItem(
                text = { Text("Spectrogram") },
                onClick = { menuOpen = false; onOpenSpectrogram() },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = { menuOpen = false; onShare() },
            )
            DropdownMenuItem(
                text = { Text(if (file.isLocked) "Unlock" else "Lock") },
                onClick = { menuOpen = false; onToggleLock() },
            )
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
