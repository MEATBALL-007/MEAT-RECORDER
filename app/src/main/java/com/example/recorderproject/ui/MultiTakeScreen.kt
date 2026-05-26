package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Multi-take comparison + ghost-take picker.
 *
 * Phase D port-back. Lets the user browse existing recordings and pick one as
 * the "ghost take" — that file's waveform is overlaid (semi-transparent) on the
 * next recording session so the user can align timing or phrasing against it.
 *
 * The actual ghost overlay rendering during recording is a follow-up (this screen
 * is the picker; the overlay hook is just `viewModel.setGhostTake(file)`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiTakeScreen(
    viewModel: RecorderViewModel,
    onBack: () -> Unit,
) {
    val files by viewModel.sortedRecordFiles.collectAsStateWithLifecycle()
    val ghost by viewModel.ghostTakeFile.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multi-take", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                actions = {
                    if (ghost != null) {
                        IconButton(onClick = { viewModel.setGhostTake(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear ghost", tint = RecorderOrange)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (ghost == null)
                    "Pick a take to overlay on your next recording."
                else
                    "Ghost active: ${ghost?.name}",
                color = RecorderBlueGrey,
                fontSize = 12.sp,
            )

            if (files.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text("No recordings yet — record something first", color = RecorderBlueGrey)
                }
                return@Column
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files) { file ->
                    TakeRow(
                        file = file,
                        active = ghost?.id == file.id,
                        onSelect = { viewModel.setGhostTake(file) },
                    )
                }
            }

            if (ghost != null) {
                OutlinedButton(
                    onClick = { viewModel.setGhostTake(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear ghost take", color = RecorderOrange, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TakeRow(
    file: RecordFile,
    active: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = if (active) RecorderOrange else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RecorderCharcoalCard)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                color = if (active) RecorderYellow else Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text("${file.durationSeconds} s", color = RecorderBlueGrey, fontSize = 11.sp)
        }
        // Selection indicator
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) RecorderOrange else Color(0xFF0C0C10))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (active) "Ghost ●" else "Set ghost",
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
        }
    }
}
