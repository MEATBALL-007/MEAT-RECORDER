package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.recorderproject.audio.FFTAnalyzer
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * F9: Mini waveform thumbnail (~32 dp tall) for a FileCard row.
 *
 * Reads the WAV file once on first composition and caches the downsampled peak
 * envelope. Renders [bins] vertical bars with brand gradient. Falls back to a
 * faint baseline when the file is content://-backed (can't read directly).
 */
@Composable
fun MiniWaveform(
    path: String,
    bins: Int = 48,
    width: androidx.compose.ui.unit.Dp = 88.dp,
    height: androidx.compose.ui.unit.Dp = 28.dp,
) {
    // Cache the downsampled envelope so we don't re-read the file every frame.
    var envelope by remember(path, bins) { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(path, bins) {
        envelope = withContext(Dispatchers.IO) {
            if (path.startsWith("content://")) null
            else try {
                val f = File(path)
                if (!f.exists()) null else downsamplePeaks(FFTAnalyzer.readWavSamples(f), bins)
            } catch (_: Exception) {
                null
            }
        }
    }

    Canvas(modifier = Modifier.width(width).height(height)) {
        val w = size.width
        val h = size.height
        val env = envelope
        if (env == null || env.isEmpty()) {
            // Faint baseline placeholder
            drawLine(
                color = RecorderBlueGrey.copy(alpha = 0.4f),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1f,
            )
            return@Canvas
        }
        val barWidth = w / env.size.toFloat()
        for ((i, lvl) in env.withIndex()) {
            val mag = lvl.coerceIn(0f, 1f)
            val barH = max(2f, mag * h * 0.9f)
            val color = when {
                mag > 0.85f -> RecorderOrange
                mag > 0.55f -> RecorderYellow
                else -> RecorderBlueGrey
            }
            drawRect(
                color = color,
                topLeft = Offset(i * barWidth, (h - barH) / 2f),
                size = Size(max(1f, barWidth - 0.8f), barH),
            )
        }
    }
}

/**
 * Downsample [samples] to a [bins]-length peak envelope (0..1 magnitude).
 * Each bin = max absolute amplitude of the samples falling within its window.
 */
private fun downsamplePeaks(samples: ShortArray, bins: Int): FloatArray {
    if (samples.isEmpty()) return FloatArray(bins)
    val perBin = max(1, samples.size / bins)
    val out = FloatArray(bins)
    for (b in 0 until bins) {
        val start = b * perBin
        val end = min(samples.size, start + perBin)
        var peak = 0
        for (i in start until end) {
            val a = abs(samples[i].toInt())
            if (a > peak) peak = a
        }
        out[b] = peak / 32_768f
    }
    // Normalize against the max so quiet files still show shape
    val maxV = out.maxOrNull() ?: 1f
    if (maxV > 0f) for (i in out.indices) out[i] = (out[i] / maxV).coerceIn(0f, 1f)
    return out
}
