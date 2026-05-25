package com.example.recorderproject.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.audio.SpectrumAnalyzer
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase 5 — full-screen spectrogram view for a RecordFile. Computes the spectrum once,
 * paints it as a heatmap: x = frequency (log), y = magnitude (height), color = magnitude (orange→yellow).
 *
 * This is the static "single-slice" version; full time-x-freq waterfall comes later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectrogramScreen(file: RecordFile, onBack: () -> Unit) {
    var spectrum by remember { mutableStateOf<StaticSpectrum?>(null) }
    LaunchedEffect(file) {
        spectrum = withContext(Dispatchers.IO) {
            runCatching { SpectrumAnalyzer.analyzeFile(File(file.path), bins = 512) }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spectrogram", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(Color(0xFF0C0C10)),
        ) {
            spectrum?.let { Spectrogram(it, modifier = Modifier.fillMaxSize()) }
                ?: Text(
                    "Computing spectrum…",
                    color = RecorderBlueGrey,
                    modifier = Modifier.padding(20.dp),
                )
        }
    }
}

@Composable
private fun Spectrogram(spec: StaticSpectrum, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val mag = spec.magnitudeDb
        // Vertical stripes for each bin colored by magnitude
        for (i in mag.indices) {
            val x = i.toFloat() / (mag.size - 1) * w
            val nextX = (i + 1).toFloat() / (mag.size - 1) * w
            val normalized = ((mag[i] + 80f) / 80f).coerceIn(0f, 1f)
            // Color ramp: dark gray → blue-grey → orange → yellow
            val color = ramp(normalized)
            val barH = normalized * h * 0.85f
            drawRect(
                color = color,
                topLeft = Offset(x, h - barH),
                size = Size(nextX - x + 0.6f, barH),
            )
        }
    }
}

private fun ramp(t: Float): Color {
    // Smooth gradient: charcoal (low) → blue-grey → orange → yellow (high)
    return when {
        t < 0.25f -> lerp(Color(0xFF1F1F23), Color(0xFF7B8189), t / 0.25f)
        t < 0.6f -> lerp(Color(0xFF7B8189), Color(0xFFFA4616), (t - 0.25f) / 0.35f)
        else -> lerp(Color(0xFFFA4616), Color(0xFFFFC72C), (t - 0.6f) / 0.4f)
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
