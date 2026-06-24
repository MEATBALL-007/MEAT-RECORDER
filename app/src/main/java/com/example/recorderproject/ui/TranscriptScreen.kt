package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
 * Transcript view for a recording.
 *
 * Phase D port-back. The actual speech-to-text engine isn't wired in this phase
 * (would require ML Kit's speech recognition or Android's SpeechRecognizer
 * service). This screen is the UI shell ready to display transcript text once
 * the engine is hooked up — for now it shows a friendly placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    viewModel: RecorderViewModel,
    file: RecordFile,
    onBack: () -> Unit,
) {
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val text = transcripts[file.id]
    val progress by viewModel.transcribeProgress.collectAsStateWithLifecycle()
    val isTranscribing = progress[file.id] != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcript", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(file.name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("${file.durationSeconds} s", color = RecorderBlueGrey, fontSize = 11.sp)

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RecorderCharcoalCard)
                    .padding(16.dp),
            ) {
                if (text != null) {
                    Text(text, color = Color.White, fontSize = 14.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "No transcript yet",
                            color = RecorderYellow,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            "Tap Transcribe below to convert this recording to text using on-device speech recognition.",
                            color = RecorderBlueGrey,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            if (isTranscribing) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = RecorderOrange,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(progress[file.id] ?: "Transcribing…", color = RecorderBlueGrey, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.requestTranscribe(file) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (text != null) "Re-transcribe" else "Transcribe",
                        color = RecorderOrange,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
