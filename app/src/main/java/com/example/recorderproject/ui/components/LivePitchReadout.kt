package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pro live pitch readout — computes dominant frequency from the rolling waveform and maps to
 * nearest musical note with cent offset. Designed to update at ~10Hz alongside the other
 * recording session visualizations.
 */
@Composable
fun LivePitchReadout(
    levels: List<Float>,
    sampleRate: Int,
    modifier: Modifier = Modifier,
) {
    val pitchHz = remember(levels) { estimatePitch(levels, sampleRate) }
    val note = pitchHz.takeIf { it > 30f }?.let { freqToNote(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("PITCH", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (pitchHz > 30f) "%.1f Hz".format(pitchHz) else "—",
                color = RecorderYellow,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (note != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${note.name}${note.octave}",
                    color = RecorderOrange,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    (if (note.cents >= 0) "+" else "") + "${note.cents} cents",
                    color = if (kotlin.math.abs(note.cents) < 5) RecorderYellow else RecorderBlueGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private data class Note(val name: String, val octave: Int, val cents: Int)

private fun freqToNote(freqHz: Float): Note {
    val semitones = 12.0 * log2(freqHz / 440.0)
    val midi = (semitones + 69).roundToInt()
    val cents = ((semitones + 69 - midi) * 100).roundToInt()
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val idx = ((midi % 12) + 12) % 12
    return Note(names[idx], (midi / 12) - 1, cents)
}

/** Lightweight pitch estimate from rolling waveform — peak of windowed FFT. */
private fun estimatePitch(levels: List<Float>, sampleRate: Int): Float {
    if (levels.size < 256) return 0f
    val n = 512
    val src = if (levels.size <= n) levels else levels.subList(levels.size - n, levels.size)
    val real = DoubleArray(n)
    val imag = DoubleArray(n)
    for (i in src.indices) {
        val w = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
        real[n - src.size + i] = src[i].toDouble() * w
    }
    // Reuse simple FFT
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
        j = j or bit
        if (i < j) {
            val tr = real[i]; real[i] = real[j]; real[j] = tr
            val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * PI / len
        val wlenR = cos(ang); val wlenI = kotlin.math.sin(ang)
        var i = 0
        while (i < n) {
            var wR = 1.0; var wI = 0.0
            for (k in 0 until len / 2) {
                val uR = real[i + k]; val uI = imag[i + k]
                val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                real[i + k] = uR + vR; imag[i + k] = uI + vI
                real[i + k + len / 2] = uR - vR; imag[i + k + len / 2] = uI - vI
                val nwR = wR * wlenR - wI * wlenI
                val nwI = wR * wlenI + wI * wlenR
                wR = nwR; wI = nwI
            }
            i += len
        }
        len = len shl 1
    }
    var peakBin = 1; var peakMag = 0.0
    // Restrict to musical range 60Hz..2000Hz to ignore DC + super-highs
    val loBin = (60.0 * n / sampleRate).toInt().coerceAtLeast(1)
    val hiBin = (2000.0 * n / sampleRate).toInt().coerceAtMost(n / 2 - 1)
    for (k in loBin..hiBin) {
        val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
        if (mag > peakMag) { peakMag = mag; peakBin = k }
    }
    if (peakMag < 0.05) return 0f
    return (peakBin.toFloat() * sampleRate / n)
}
