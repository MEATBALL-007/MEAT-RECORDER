package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/** Phase 4 — crop start/end via RangeSlider. Confirm writes _trim.wav using existing WavTrimmer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(
    file: RecordFile,
    onConfirm: (inMs: Long, outMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val durationMs = (file.durationSeconds * 1000L).coerceAtLeast(1L)
    var range by remember { mutableStateOf(0f..durationMs.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trim", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
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
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RecorderCharcoalCard)
                    .padding(16.dp),
            ) {
                Column {
                    Text(file.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Duration: ${file.durationSeconds}s · ${file.sampleRate / 1000} kHz · ${file.bitDepth}-bit",
                        color = RecorderBlueGrey, fontSize = 11.sp)
                }
            }

            Box(
                Modifier.fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C0C10)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[ waveform stub — phase 4 wires real samples ]", color = RecorderBlueGrey, fontSize = 11.sp)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start: ${(range.start / 1000).toInt()}s", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
                Text("End: ${(range.endInclusive / 1000).toInt()}s", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
            }
            RangeSlider(
                value = range,
                onValueChange = { range = it },
                valueRange = 0f..durationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = RecorderYellow,
                    activeTrackColor = RecorderOrange,
                    inactiveTrackColor = RecorderBlueGrey.copy(alpha = 0.4f),
                ),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel", color = RecorderBlueGrey) }
                Button(
                    onClick = { onConfirm(range.start.toLong(), range.endInclusive.toLong()) },
                    colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
                    modifier = Modifier.weight(1f),
                ) { Text("Trim", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
