package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.recorderproject.audio.AudioSegment
import com.example.recorderproject.audio.SceneSlicer
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scene slicer screen — detect silence-bounded sub-sections of a recording.
 *
 * Phase E port-back. Calls [SceneSlicer.sliceOnSilence] off the main thread and
 * lists each detected scene as a tappable row. Currently shows the slice
 * metadata only; tap-to-export and waveform thumbnails are follow-up polish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneSlicerScreen(
    file: RecordFile,
    onBack: () -> Unit,
) {
    var segments by remember { mutableStateOf<List<AudioSegment>?>(null) }
    var analyzing by remember { mutableStateOf(false) }

    LaunchedEffect(file.id) {
        if (file.path.startsWith("content://")) {
            segments = emptyList()
            return@LaunchedEffect
        }
        analyzing = true
        segments = withContext(Dispatchers.IO) {
            try {
                SceneSlicer.sliceOnSilence(file.path)
            } catch (e: Exception) {
                emptyList()
            }
        }
        analyzing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scene slicer", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(file.name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Splits recording on silence ≥ 400 ms below −40 dB", color = RecorderBlueGrey, fontSize = 11.sp)

            val s = segments
            when {
                analyzing -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RecorderOrange)
                }
                s == null -> {}
                s.isEmpty() -> Text(
                    "No scenes detected — recording may be too short, too quiet, or use content:// storage we can't read yet.",
                    color = RecorderBlueGrey,
                    fontSize = 12.sp,
                )
                else -> {
                    Text(
                        "${s.size} scene${if (s.size == 1) "" else "s"} detected",
                        color = RecorderYellow,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s) { seg -> SceneRow(seg) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneRow(seg: AudioSegment) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RecorderCharcoalCard)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(seg.label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "${formatMs(seg.startMs)} → ${formatMs(seg.endMs)}",
                color = RecorderBlueGrey,
                fontSize = 11.sp,
            )
        }
        Text(
            "%.1f dB".format(seg.avgLevelDb),
            style = LocalAppTypography.current.numericMedium,
            color = RecorderOrange,
        )
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    return "%d:%02d.%03d".format(m, s % 60, ms % 1000)
}
