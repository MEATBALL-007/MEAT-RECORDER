package com.example.recorderproject.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.LoudnessTarget
import kotlin.math.abs

private val Orange   = Color(0xFFFA4616)
private val Yellow   = Color(0xFFFFC72C)
private val BlueGrey = Color(0xFF7B8189)
private val Red      = Color(0xFFC0392B)

/**
 * Target-aware loudness meter. Tracks short-term LUFS against the target line.
 * Zones: green ≤ ±1 LU, yellow ≤ ±3 LU, red beyond.
 * Bottom row shows M/S/I numeric readouts plus TP dBTP.
 */
@Composable
fun LoudnessMeterBar(
    momentaryLufs: Float,
    shortTermLufs: Float,
    integratedLufs: Float,
    truePeakDbtp: Float,
    target: LoudnessTarget,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val targetLufs = target.targetLufs

    // Scale: target ±12 LU. When target is Off, anchor scale to -16 ±12.
    val scaleCenter = targetLufs ?: -16f
    val scaleSpan = 12f
    fun normalize(l: Float): Float {
        val clamped = l.coerceIn(scaleCenter - scaleSpan, scaleCenter + scaleSpan)
        return ((clamped - (scaleCenter - scaleSpan)) / (scaleSpan * 2f)).coerceIn(0f, 1f)
    }

    val barFill by if (reduceMotion) {
        animateFloatAsState(normalize(shortTermLufs), tween(0), label = "loudness-fill")
    } else {
        animateFloatAsState(
            normalize(shortTermLufs),
            spring(stiffness = Spring.StiffnessMediumLow),
            label = "loudness-fill",
        )
    }

    val zoneColor = when {
        targetLufs == null -> BlueGrey
        abs(shortTermLufs - targetLufs) <= 1f -> Orange
        abs(shortTermLufs - targetLufs) <= 3f -> Yellow
        else -> Red
    }
    val animatedColor by animateColorAsState(
        zoneColor,
        if (reduceMotion) tween(0) else tween(150),
        label = "loudness-zone",
    )

    Column(
        modifier = modifier
            .height(120.dp)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            drawRect(BlueGrey.copy(alpha = 0.15f), size = size)
            val fillH = size.height * barFill
            drawRect(
                color = animatedColor.copy(alpha = 0.85f),
                topLeft = Offset(0f, size.height - fillH),
                size = Size(size.width, fillH),
            )
            if (targetLufs != null) {
                // Target line sits at scale center (50% from top).
                val y = size.height * 0.5f
                drawLine(
                    color = Orange,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 4f,
                )
                // Integrated tick.
                val ti = size.height * (1f - normalize(integratedLufs))
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(size.width * 0.1f, ti),
                    end = Offset(size.width * 0.9f, ti),
                    strokeWidth = 2f,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Readout("M", momentaryLufs)
            Readout("S", shortTermLufs)
            Readout("I", integratedLufs)
        }
        val tpColor = when {
            truePeakDbtp >= -0.1f -> Red
            truePeakDbtp >= -3f   -> Yellow
            else                  -> Color.White
        }
        Text(
            text = if (truePeakDbtp.isFinite()) "TP %.1f dBTP".format(truePeakDbtp) else "TP —",
            color = tpColor,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Readout(label: String, lufs: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = BlueGrey)
        Text(
            text = if (lufs > -69f) "%.1f".format(lufs) else "—",
            fontSize = 13.sp,
            color = Color.White,
        )
    }
}
