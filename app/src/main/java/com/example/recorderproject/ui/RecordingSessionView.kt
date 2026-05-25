package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.components.CircleRecordButton
import com.example.recorderproject.ui.components.LivePitchReadout
import com.example.recorderproject.ui.components.LiveSpectrumView
import com.example.recorderproject.ui.components.RecordingMeterBar
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Phase 5 — Recording Session view. Replaces the home content while isRecording=true.
 * Shows WAVE (rolling RMS waveform), SPECTRUM (live FFT bars), PITCH (note + cents), big timer,
 * and a circular Stop button.
 */
@Composable
fun RecordingSessionView(
    fileName: String,
    elapsedSeconds: Int,
    levels: List<Float>,
    sampleRate: Int,
    cueCount: Int,
    onDropCue: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: recording label + filename
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RecorderCharcoalCard)
                .padding(14.dp),
        ) {
            Column {
                Text(
                    "● RECORDING",
                    color = RecorderOrange,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    fileName,
                    color = RecorderYellow,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text(
                    "${sampleRate / 1000} kHz · live take",
                    color = RecorderBlueGrey,
                    fontSize = 11.sp,
                )
            }
        }

        // WAVE + TIMER + CUE — existing meter bar
        RecordingMeterBar(
            elapsedSeconds = elapsedSeconds,
            levels = levels,
            cueCount = cueCount,
            onDropCue = onDropCue,
        )

        // SPECTRUM — live FFT bars
        Text(
            "SPECTRUM",
            color = RecorderBlueGrey,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        )
        LiveSpectrumView(levels = levels)

        // PITCH — dominant frequency + nearest note
        LivePitchReadout(levels = levels, sampleRate = sampleRate)

        // Big Stop button at the bottom — circular
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircleRecordButton(
                isRecording = true,
                onTap = onStop,
            )
        }
    }
}
