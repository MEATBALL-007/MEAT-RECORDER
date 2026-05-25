package com.example.recorderproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.RecorderViewModel

// TODO: original RecorderApp Compose UI lost 2026-05-25 iCloud eviction.
// Placeholder so the project compiles + smoke-tests on device.
// Symbol inventory confirmed Composables: EQScreen, EQCurveView, EQSlider, HarmonicPortraitScreen,
// HarmonicPortraitView, ModeSelectorScreen, MultiTakeScreen, PitchView, SlateDialog, SpectrogramScreen,
// SpectrogramView, ThemeCard, TranscriptScreen, TrimScreen, WaterfallView, WaveformPreview, WaveformView,
// FanModeCard, LiquidSplashEffect, RecorderAppWithIntro.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderApp(
    viewModel: RecorderViewModel,
    onStartRecording: () -> Unit,
    onSelectSaveLocation: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val files by viewModel.recordFiles.collectAsStateWithLifecycle()
    val fileName by viewModel.fileName.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("KMUTT Recorder — recovery build") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Recording: $fileName")
            Text(if (isRecording) "● REC" else "Idle")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStartRecording) {
                Text(if (isRecording) "Stop" else "Record")
            }
            OutlinedButton(onClick = onSelectSaveLocation) {
                Text("Choose save folder")
            }
            Spacer(Modifier.height(8.dp))
            Text("Recordings: ${files.size}")
        }
    }
}
