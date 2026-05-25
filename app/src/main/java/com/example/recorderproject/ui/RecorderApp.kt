package com.example.recorderproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MEATrec", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBardefaultColors(),
            )
        },
        containerColor = RecorderCharcoal,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Recording: $fileName", color = RecorderBlueGrey)
            Text(
                text = if (isRecording) "● REC" else "Idle",
                color = if (isRecording) RecorderOrange else RecorderBlueGrey,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStartRecording,
                colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
            ) {
                Text(if (isRecording) "Stop" else "Record", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onSelectSaveLocation) {
                Text("Choose save folder", color = RecorderYellow)
            }
            Spacer(Modifier.height(8.dp))
            Text("Recordings: ${files.size}", color = RecorderBlueGrey)
            OutlinedButton(
                onClick = onOpenEQOnLast,
                enabled = files.isNotEmpty(),
            ) {
                Text("Open EQ on last recording", color = if (files.isNotEmpty()) RecorderYellow else RecorderBlueGrey)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBardefaultColors() = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal)
