package com.example.recorderproject.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private val MeatOrange = Color(0xFFFA4616)
private val MeatYellow = Color(0xFFFFC72C)
private val RecRed = Color(0xFFEA4F30)
private val CardBg = Color(0xFF161616)

/**
 * Full recording-active section — port-back of the 22 May APK recording screen.
 *
 * Reference: recovery/screenshots/16-recording-3s.png
 *
 * Stack of:
 *   1. RecBadge — "● REC HH:MM:SS" red, just below the record button
 *   2. InputLevelBar — yellow gradient bar with % readout
 *   3. LiveWaveformCard — Canvas of vertical yellow bars from current waveform
 *   4. SpectrumCard — rolling FFT-style heatmap (purple→cyan→yellow→orange)
 *   5. PitchCard — note grid (E2..C6) with current pitch indicator
 *
 * All inputs are float lists / scalars so the section is pure and testable.
 * Real DSP wiring lives in the caller (ViewModel feeds [waveform], etc.).
 */
@Composable
fun RecordingActiveSection(
    elapsedSeconds: Int,
    waveform: List<Float>,
    inputLevelPercent: Int,
    spectrumHistory: List<FloatArray> = emptyList(),
    pitchHz: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        RecBadge(elapsedSeconds = elapsedSeconds)
        InputLevelBar(percent = inputLevelPercent)
        LiveWaveformCard(waveform = waveform)
        SpectrumCard(spectrumHistory = spectrumHistory, fallbackWaveform = waveform)
        PitchCard(pitchHz = pitchHz)
    }
}

@Composable
private fun RecBadge(elapsedSeconds: Int) {
    val pulse by rememberInfiniteTransition(label = "recPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween<Float>(700, easing = LinearEasing)),
        label = "pulse",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(RecRed.copy(alpha = pulse)),
        )
        Box(modifier = Modifier.size(width = 8.dp, height = 0.dp))
        Text(
            "REC",
            color = RecRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Box(modifier = Modifier.size(width = 12.dp, height = 0.dp))
        Text(
            text = formatElapsedFull(elapsedSeconds),
            color = RecRed,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun InputLevelBar(percent: Int) {
    val fraction = (percent.coerceIn(0, 100)) / 100f

    // M3: peak hold — climbs instantly with level, decays slowly
    var peakHold by remember { mutableStateOf(0f) }
    LaunchedEffect(percent) {
        if (fraction > peakHold) {
            peakHold = fraction
        }
    }
    LaunchedEffect(Unit) {
        // Decay every 80ms by 1.5% — peak slowly slides down toward 0
        while (true) {
            kotlinx.coroutines.delay(80)
            if (peakHold > fraction) {
                peakHold = (peakHold - 0.015f).coerceAtLeast(fraction)
            }
        }
    }

    val clipping = percent >= 95
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "INPUT LEVEL",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (clipping) {
                    // Small red CLIP indicator dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(RecRed),
                    )
                    Text(
                        "CLIP",
                        color = RecRed,
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                "${percent}%",
                color = if (clipping) RecRed else Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF222222)),
        ) {
            // Main fill bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MeatYellow,
                                MeatYellow.copy(alpha = 0.85f),
                                Color(0xFFFAA616),
                            ),
                        ),
                    ),
            )
            // Peak hold tick — 2dp white bar at peakHold position
            if (peakHold > 0f) {
                Canvas(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                ) {
                    val x = (size.width * peakHold).coerceIn(0f, size.width - 3f)
                    drawRect(
                        color = if (clipping) RecRed else Color.White,
                        topLeft = Offset(x, 0f),
                        size = Size(3f, size.height),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveWaveformCard(waveform: List<Float>) {
    CardWithLabel(label = "Live Waveform", dot = true) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0A0A0A)),
        ) {
            val w = size.width
            val h = size.height
            if (waveform.isEmpty()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 1f,
                )
                return@Canvas
            }
            // Tight vertical bars in yellow — feel of a digital VU strip
            val bars = waveform.takeLast(140)
            val barW = w / bars.size.toFloat()
            for ((i, lvl) in bars.withIndex()) {
                val mag = abs(lvl).coerceIn(0f, 1f)
                val barH = max(2f, mag * h * 0.92f)
                drawRect(
                    color = MeatYellow,
                    topLeft = Offset(i * barW, (h - barH) / 2f),
                    size = Size(max(1f, barW - 0.6f), barH),
                )
            }
        }
    }
}

@Composable
private fun SpectrumCard(
    spectrumHistory: List<FloatArray>,
    fallbackWaveform: List<Float>,
) {
    // Prefer real FFT history from the recorder. If that's still empty (first
    // ~80ms of recording or perms not granted), fall back to a waveform-derived
    // animated approximation so the card never looks completely dead.
    val useReal = spectrumHistory.isNotEmpty()
    val fallbackFrames = remember { mutableStateListOf<FloatArray>() }
    val fallbackBandCount = 24
    LaunchedEffect(fallbackWaveform.size, useReal) {
        if (!useReal && fallbackWaveform.isNotEmpty()) {
            val avgEnergy = fallbackWaveform.takeLast(64).map { abs(it) }.average().toFloat()
            val rms = sqrt(fallbackWaveform.takeLast(128).map { it * it }.average().toFloat())
            val frame = FloatArray(fallbackBandCount) { i ->
                val phase = (System.currentTimeMillis() / 80.0).toFloat()
                val mix = sin((i * 0.32f + phase * 0.1f).toDouble()).toFloat() * 0.5f + 0.5f
                val falloff = 1f - (i / fallbackBandCount.toFloat()) * 0.3f
                ((avgEnergy * 4f + rms * 2f) * mix * falloff).coerceIn(0f, 1f)
            }
            fallbackFrames.add(frame)
            while (fallbackFrames.size > 80) fallbackFrames.removeAt(0)
        }
    }

    val frames: List<FloatArray> = if (useReal) spectrumHistory else fallbackFrames
    val bandCount: Int = frames.firstOrNull()?.size ?: fallbackBandCount

    CardWithLabel(label = "SPECTRUM") {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0A0A0A)),
        ) {
            val w = size.width
            val h = size.height
            if (frames.isEmpty() || bandCount == 0) return@Canvas

            // Find global max so we can normalize each frame to 0..1
            val gmax = frames.maxOf { f -> f.maxOrNull() ?: 1f }.coerceAtLeast(1e-6f)

            val colCount = frames.size
            val colW = w / colCount.toFloat()
            val bandH = h / bandCount.toFloat()

            for (col in 0 until colCount) {
                val frame = frames[col]
                for (band in 0 until bandCount.coerceAtMost(frame.size)) {
                    // Normalize + log-compress to keep low energies visible
                    val raw = (frame[band] / gmax).coerceIn(0f, 1f)
                    val mag = kotlin.math.ln(1f + raw * 9f) / kotlin.math.ln(10f)
                    val color = magnitudeToColor(mag.coerceIn(0f, 1f))
                    drawRect(
                        color = color,
                        topLeft = Offset(col * colW, (bandCount - 1 - band) * bandH),
                        size = Size(colW + 0.5f, bandH + 0.5f),
                    )
                }
            }
        }
    }
}

private fun magnitudeToColor(mag: Float): Color {
    // Classic spectrogram colormap: dark purple → cyan → yellow → orange → white-hot
    val m = mag.coerceIn(0f, 1f)
    return when {
        m < 0.2f -> lerpColor(Color(0xFF1B0840), Color(0xFF3A1C70), m / 0.2f)
        m < 0.4f -> lerpColor(Color(0xFF3A1C70), Color(0xFF1F84C7), (m - 0.2f) / 0.2f)
        m < 0.6f -> lerpColor(Color(0xFF1F84C7), Color(0xFFE9C04F), (m - 0.4f) / 0.2f)
        m < 0.85f -> lerpColor(Color(0xFFE9C04F), Color(0xFFFA4616), (m - 0.6f) / 0.25f)
        else -> lerpColor(Color(0xFFFA4616), Color(0xFFFFE3C3), (m - 0.85f) / 0.15f)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val c = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * c,
        green = a.green + (b.green - a.green) * c,
        blue = a.blue + (b.blue - a.blue) * c,
        alpha = 1f,
    )
}

@Composable
private fun PitchCard(pitchHz: Float) {
    CardWithLabel(label = "PITCH", trailing = {
        Text(
            text = if (pitchHz > 0f) "${pitchHz.toInt()} Hz" else "—",
            color = MeatYellow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0A0A0A)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(start = 32.dp, top = 6.dp, end = 12.dp, bottom = 6.dp)) {
                val w = size.width
                val h = size.height
                // Note grid lines (8 rows ~ C6 G5 D5 A4 E4 B3 F#3 C3 → roughly log spaced)
                val rows = 8
                for (i in 0 until rows) {
                    val y = h * (i + 0.5f) / rows
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                }

                // Map pitchHz to a 0..1 vertical position (log scale)
                val minHz = 65f
                val maxHz = 1000f
                val frac = if (pitchHz > 0f) {
                    (ln(pitchHz.toDouble()) - ln(minHz.toDouble())) /
                        (ln(maxHz.toDouble()) - ln(minHz.toDouble()))
                } else 0.0
                val y = (h * (1f - frac.toFloat())).coerceIn(4f, h - 4f)

                // Orange tracking line + end dot
                drawLine(
                    color = MeatYellow,
                    start = Offset(0f, y),
                    end = Offset(w - 12f, y),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = MeatOrange,
                    radius = 10f,
                    center = Offset(w - 12f, y),
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(w - 12f, y),
                )
            }
            // Note labels on the left side
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("C6", "G5", "D5", "A4", "E4", "B3", "F3", "C3").forEach {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.30f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardWithLabel(
    label: String,
    dot: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (dot) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RecRed),
                    )
                }
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = if (label.uppercase() == label) 1.5.sp else 0.sp,
                )
            }
            trailing?.invoke()
        }
        content()
    }
}

// Helper — same formatter as MeatRecHome, kept private here so the component is self-contained
private fun formatElapsedFull(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

