package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun WaveformView(
    waveform: List<Float>,
    ghostWaveform: List<Float>? = null,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxWidth().height(72.dp)
    ) {
        drawRect(color = Color(0xFF110508))
        val centerY = size.height / 2f

        // Ghost take overlay (grey, slightly transparent)
        if (!ghostWaveform.isNullOrEmpty()) {
            val gCount = ghostWaveform.size.coerceAtLeast(1)
            val gStep = size.width / gCount
            ghostWaveform.forEachIndexed { index, sample ->
                val x = index * gStep + gStep / 2f
                val amplitude = abs(sample) * centerY * 0.88f
                drawLine(
                    color = Color(0x44AAAAAA),
                    start = Offset(x, centerY - amplitude),
                    end   = Offset(x, centerY + amplitude),
                    strokeWidth = maxOf(gStep * 0.6f, 1.5f)
                )
            }
        }

        // Live waveform
        if (waveform.isNotEmpty()) {
            val count = waveform.size.coerceAtLeast(1)
            val step  = size.width / count
            waveform.forEachIndexed { index, sample ->
                val x = index * step + step / 2f
                val amplitude = abs(sample) * centerY * 0.88f
                val t = (abs(sample) * 2.5f).coerceIn(0f, 1f)
                val barColor = Color(
                    red   = (0.639f + t * 0.192f).coerceIn(0f, 1f),
                    green = (0.122f + t * 0.564f).coerceIn(0f, 1f),
                    blue  = (0.204f - t * 0.190f).coerceIn(0f, 1f)
                )
                drawLine(
                    color = barColor,
                    start = Offset(x, centerY - amplitude),
                    end   = Offset(x, centerY + amplitude),
                    strokeWidth = maxOf(step * 0.6f, 1.5f)
                )
            }
        }

        drawLine(
            color = Color(0x55A31F34),
            start = Offset(0f, centerY),
            end   = Offset(size.width, centerY),
            strokeWidth = 1f
        )
    }
}
