package com.example.recorderproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.RecorderViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.recorderproject.ui.components.BitDepthSelector
import com.example.recorderproject.ui.components.MeatrecMark
import com.example.recorderproject.ui.components.RecordPulseButton
import com.example.recorderproject.ui.components.RecordingFileList
import com.example.recorderproject.ui.components.RecordingMeterBar
import com.example.recorderproject.ui.components.SampleRateSelector
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderApp(
    viewModel: RecorderViewModel,
    onStartRecording: () -> Unit,
    onSelectSaveLocation: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenEQOnLast: () -> Unit,
) {
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val files by viewModel.recordFiles.collectAsStateWithLifecycle()
    val fileName by viewModel.fileName.collectAsStateWithLifecycle()
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()
    val bitDepth by viewModel.bitDepth.collectAsStateWithLifecycle()
    val waveform by viewModel.currentWaveform.collectAsStateWithLifecycle()

    // Elapsed seconds tracker — increments while recording
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(isRecording) {
        elapsed = 0
        while (isRecording) {
            kotlinx.coroutines.delay(1000)
            elapsed++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MeatrecMark(size = 32.dp)
                        Text("MEATrec", color = RecorderYellow, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RecorderCharcoalCard)
                    .padding(14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("FILE", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Text(fileName, color = Color_white(), fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (isRecording) "● REC" else "Idle",
                            color = if (isRecording) RecorderOrange else RecorderBlueGrey,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SampleRateSelector(
                            current = sampleRate,
                            onChange = { viewModel.updateSampleRate(it) },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("BIT DEPTH", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp)
                        BitDepthSelector(
                            current = bitDepth,
                            onChange = { viewModel.updateBitDepth(it) },
                        )
                    }
                }
            }

            // Recording meter: appears with a fade+expand when recording starts
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                RecordingMeterBar(elapsedSeconds = elapsed, levels = waveform)
            }

            // Animated Record / Stop button — press scale, ripple, breathing pulse while recording
            RecordPulseButton(
                isRecording = isRecording,
                onTap = { if (isRecording) viewModel.stopRecording() else onStartRecording() },
            )

            // Secondary actions row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelectSaveLocation, modifier = Modifier.weight(1f)) {
                    Text("Save folder", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenEQOnLast,
                    enabled = files.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "Open EQ on last",
                        color = if (files.isNotEmpty()) RecorderYellow else RecorderBlueGrey,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // File list section header
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("RECORDINGS", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${files.size}", color = RecorderYellow, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }

            RecordingFileList(
                files = files,
                onTapFile = { viewModel.selectFile(it) },
                onTapEQ = { viewModel.onEQOpen(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Small helper because using Color.White directly in many places adds verbosity
@Composable
private fun Color_white(): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White
