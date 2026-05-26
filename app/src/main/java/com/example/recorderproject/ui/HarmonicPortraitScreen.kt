package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.audio.FFTAnalyzer
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.components.HarmonicPortraitView
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ln
import kotlin.math.max

/**
 * Full-screen harmonic portrait for one recording.
 *
 * Computes a time-averaged log-band spectrum from the WAV file (off the main
 * thread) then renders it through [HarmonicPortraitView]. Provides a sense of
 * the recording's spectral "personality" — bright takes bloom outward in the
 * higher bands, dark/muddy takes stay tight in the lower bands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarmonicPortraitScreen(
    file: RecordFile,
    onBack: () -> Unit,
) {
    var bands by remember { mutableStateOf<FloatArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.id) {
        try {
            val computed = withContext(Dispatchers.IO) {
                if (file.path.startsWith("content://")) {
                    // SAF file — skip for now (would require ContentResolver read path).
                    FloatArray(FFTAnalyzer.LOG_BANDS) { 0f }
                } else {
                    val f = File(file.path)
                    if (!f.exists()) return@withContext FloatArray(FFTAnalyzer.LOG_BANDS) { 0f }
                    val samples = FFTAnalyzer.readWavSamples(f)
                    timeAveragedSpectrum(samples, sampleRate = 48_000)
                }
            }
            bands = computed
        } catch (e: Exception) {
            error = e.message ?: "Failed to compute spectrum"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Harmonic portrait",
                        color = RecorderYellow,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(file.name, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "${file.durationSeconds} s · log-spectrum average",
                color = RecorderBlueGrey,
                fontSize = 11.sp,
            )
            Spacer(Modifier.padding(top = 24.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(androidx.compose.ui.graphics.Color(0xFF0C0C10)),
                contentAlignment = Alignment.Center,
            ) {
                val current = bands
                when {
                    error != null -> Text(error ?: "", color = RecorderOrange)
                    current == null -> CircularProgressIndicator(color = RecorderOrange)
                    current.all { it == 0f } -> Text("No audio data", color = RecorderBlueGrey)
                    else -> HarmonicPortraitView(
                        bands = current,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.padding(top = 12.dp))
            Text(
                "Brighter petals = stronger band energy. Petals fan from low (right) clockwise to high frequencies.",
                color = RecorderBlueGrey,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Average the per-frame log spectrum across the file's duration to produce one
 * representative band vector. Normalizes the result to [0..1] by dividing by
 * max, then applies a mild log compression so quiet bands still register.
 */
private fun timeAveragedSpectrum(samples: ShortArray, sampleRate: Int): FloatArray {
    val frameSize = FFTAnalyzer.FFT_SIZE
    if (samples.size < frameSize) return FloatArray(FFTAnalyzer.LOG_BANDS) { 0f }
    val hop = frameSize / 2 // 50% overlap
    val accumulator = FloatArray(FFTAnalyzer.LOG_BANDS)
    var frames = 0
    var offset = 0
    while (offset + frameSize <= samples.size) {
        val spectrum = FFTAnalyzer.frameSpectrum(samples, offset, frameSize, sampleRate)
        for (i in 0 until FFTAnalyzer.LOG_BANDS) {
            accumulator[i] += spectrum[i]
        }
        frames++
        offset += hop
        if (frames >= 800) break // cap so we don't process 30-minute files for the visual
    }
    if (frames == 0) return FloatArray(FFTAnalyzer.LOG_BANDS) { 0f }
    for (i in accumulator.indices) accumulator[i] /= frames.toFloat()
    // Log compression + normalize
    var peak = 0f
    for (i in accumulator.indices) {
        accumulator[i] = ln(1f + accumulator[i] * 9f)
        if (accumulator[i] > peak) peak = accumulator[i]
    }
    val norm = max(peak, 0.0001f)
    for (i in accumulator.indices) accumulator[i] = (accumulator[i] / norm).coerceIn(0f, 1f)
    return accumulator
}
