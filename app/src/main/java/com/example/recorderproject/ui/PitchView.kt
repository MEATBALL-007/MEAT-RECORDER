package com.example.recorderproject.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.log2
import kotlin.math.roundToInt

/** Phase 5 — live pitch view. Pass freqHz=0 when no pitch detected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitchView(freqHz: Float, onBack: () -> Unit) {
    val noteInfo = if (freqHz > 20f) freqToNote(freqHz) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pitch", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Big note
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RecorderCharcoalCard)
                    .padding(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        noteInfo?.let { "${it.note}${it.octave}" } ?: "—",
                        color = RecorderYellow,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (freqHz > 20f) "%.1f Hz".format(freqHz) else "Silent",
                        color = RecorderBlueGrey,
                        fontSize = 18.sp,
                    )
                    noteInfo?.let {
                        Text(
                            (if (it.cents >= 0) "+" else "") + "${it.cents} cents",
                            color = if (kotlin.math.abs(it.cents) < 5) RecorderOrange else RecorderBlueGrey,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Cent-deviation meter
            CentsMeter(noteInfo?.cents ?: 0)
        }
    }
}

@Composable
private fun CentsMeter(cents: Int) {
    Box(
        Modifier.fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RecorderCharcoalCard),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            val w = size.width; val h = size.height
            // Gradient bar
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to RecorderOrange.copy(alpha = 0.5f),
                    0.5f to RecorderYellow,
                    1f to RecorderOrange.copy(alpha = 0.5f),
                )
            )
            // Center mark
            drawLine(
                color = Color.White,
                start = Offset(w / 2f, 0f),
                end = Offset(w / 2f, h),
                strokeWidth = 2f,
            )
            // Pointer at the cents value (-50..50)
            val t = (cents.coerceIn(-50, 50) + 50) / 100f
            drawCircle(
                color = Color.White,
                radius = 8f,
                center = Offset(t * w, h / 2f),
            )
        }
    }
}

private data class NoteInfo(val note: String, val octave: Int, val cents: Int)

private fun freqToNote(freqHz: Float): NoteInfo {
    // A4 = 440 Hz, semitones from A4 = 12 * log2(f / 440)
    val semitones = 12.0 * log2(freqHz / 440.0)
    val midi = (semitones + 69).roundToInt()
    val cents = ((semitones + 69 - midi) * 100).roundToInt()
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val noteIdx = ((midi % 12) + 12) % 12
    val octave = (midi / 12) - 1
    return NoteInfo(noteNames[noteIdx], octave, cents)
}
