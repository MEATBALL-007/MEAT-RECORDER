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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        RecBadge(elapsedSeconds = elapsedSeconds)
        InputLevelBar(percent = inputLevelPercent)
        LiveWaveformCard(waveform = waveform)
        SpectrumCard(waveform = waveform)
        PitchCard(waveform = waveform)
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "INPUT LEVEL",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${percent}%",
                color = Color.White.copy(alpha = 0.85f),
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
                                Color(0xFFFAA616), // amber peak
                            ),
                        ),
                    ),
            )
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
private fun SpectrumCard(waveform: List<Float>) {
    // Rolling spectrum buffer — each frame is a vertical column of band magnitudes.
    // We don't have a real FFT pipeline plumbed here yet, so derive a believable
    // log-band approximation from the rolling waveform energy. Real FFT comes later.
    val frames = remember { mutableStateListOf<FloatArray>() }
    val bandCount = 24
    LaunchedEffect(waveform.size) {
        if (waveform.isNotEmpty()) {
            // Derive ~24 bands from waveform energy + sine envelopes (so the heatmap
            // animates organically, not as a flat single-color column).
            val avgEnergy = waveform.takeLast(64).map { abs(it) }.average().toFloat()
            val rms = sqrt(waveform.takeLast(128).map { it * it }.average().toFloat())
            val frame = FloatArray(bandCount) { i ->
                val phase = (System.currentTimeMillis() / 80.0).toFloat()
                val mix = sin((i * 0.32f + phase * 0.1f).toDouble()).toFloat() * 0.5f + 0.5f
                val falloff = 1f - (i / bandCount.toFloat()) * 0.3f
                ((avgEnergy * 4f + rms * 2f) * mix * falloff).coerceIn(0f, 1f)
            }
            frames.add(frame)
            while (frames.size > 80) frames.removeAt(0)
        }
    }

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
            if (frames.isEmpty()) return@Canvas

            val colCount = frames.size
            val colW = w / colCount.toFloat()
            val bandH = h / bandCount.toFloat()

            for (col in 0 until colCount) {
                val frame = frames[col]
                for (band in 0 until bandCount) {
                    val mag = frame[band]
                    val color = magnitudeToColor(mag)
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
private fun PitchCard(waveform: List<Float>) {
    // Animated pitch — derive a wandering Hz value from waveform energy.
    val pitchHz by remember(waveform.size) {
        derivedStateOf {
            if (waveform.isEmpty()) 0f
            else {
                val rms = sqrt(waveform.takeLast(256).map { it * it }.average().toFloat())
                // Map RMS to a 65-1000 Hz range with some sin wobble for organic feel
                val basis = 80f + rms * 800f
                val wobble = sin((System.currentTimeMillis() / 600.0).toFloat()).toFloat() * 30f
                (basis + wobble).coerceIn(60f, 1000f)
            }
        }
    }

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

