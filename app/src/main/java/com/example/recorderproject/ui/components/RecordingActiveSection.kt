package com.example.recorderproject.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.border
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
    cueCount: Int = 0,
    isPaused: Boolean = false,
    onDropCue: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    liveEqOn: Boolean = false,
    onToggleLiveEq: () -> Unit = {},
    onOpenEqEditor: () -> Unit = {},
    liveNoiseGateOn: Boolean = false,
    onToggleLiveNoiseGate: () -> Unit = {},
    liveEqBandGains: FloatArray = FloatArray(6),
    onChangeLiveEqBand: (Int, Float) -> Unit = { _, _ -> },
    preRollOn: Boolean = false,
    onTogglePreRoll: () -> Unit = {},
    vadOn: Boolean = false,
    onToggleVad: () -> Unit = {},
    lufsDb: Float = -70f,
    onSlateTone: () -> Unit = {},
    sceneName: String = "",
    fileName: String = "",
    micSource: String = "",
    phaseCorrelation: Float = 0f,
    channelCount: Int = 1,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SlateHeader(
            sceneName = sceneName,
            fileName = fileName,
            micSource = micSource,
            elapsedSeconds = elapsedSeconds,
        )
        RecBadge(elapsedSeconds = elapsedSeconds, isPaused = isPaused)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InputLevelBar(percent = inputLevelPercent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "LUFS",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (lufsDb > -69f) "%.1f".format(lufsDb) else "—",
                    color = MeatYellow,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // Quick action row: pause/resume + drop cue + slate tone
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickActionChip(
                label = if (isPaused) "Resume" else "Pause",
                icon = if (isPaused) "▶" else "❚❚",
                onClick = onTogglePause,
                modifier = Modifier.weight(1f),
            )
            QuickActionChip(
                label = if (cueCount == 0) "Drop cue" else "Cue · $cueCount",
                icon = "◆",
                onClick = onDropCue,
                modifier = Modifier.weight(1f),
            )
            QuickActionChip(
                label = "Slate",
                icon = "♪",
                onClick = onSlateTone,
                modifier = Modifier.weight(1f),
            )
        }

        // P3: Live EQ + NR Gate + Edit EQ — adjust the recording chain mid-take
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActiveChip(
                label = if (liveEqOn) "Live EQ ON" else "Live EQ",
                active = liveEqOn,
                onClick = onToggleLiveEq,
                modifier = Modifier.weight(1f),
            )
            ActiveChip(
                label = if (liveNoiseGateOn) "NR Gate ON" else "NR Gate",
                active = liveNoiseGateOn,
                onClick = onToggleLiveNoiseGate,
                modifier = Modifier.weight(1f),
            )
            QuickActionChip(
                label = "Edit EQ",
                icon = "→",
                onClick = onOpenEqEditor,
                modifier = Modifier.weight(1f),
            )
        }
        // Pre-roll + VAD chip row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActiveChip(
                label = if (preRollOn) "Pre-roll 5s ON" else "Pre-roll 5s",
                active = preRollOn,
                onClick = onTogglePreRoll,
                modifier = Modifier.weight(1f),
            )
            ActiveChip(
                label = if (vadOn) "VAD ON" else "VAD",
                active = vadOn,
                onClick = onToggleVad,
                modifier = Modifier.weight(1f),
            )
        }
        // K.3: Phase correlation meter — only shown in stereo mode
        if (channelCount == 2) {
            PhaseMeter(correlation = phaseCorrelation)
        }
        LiveWaveformCard(waveform = waveform)
        // Q1: 6-band live EQ — only shown when Live EQ is toggled on
        if (liveEqOn) {
            LiveEqBandStrip(
                bandGainsDb = liveEqBandGains,
                onChangeBand = onChangeLiveEqBand,
            )
        }
        SpectrumCard(spectrumHistory = spectrumHistory, fallbackWaveform = waveform)
        PitchCard(pitchHz = pitchHz)
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1F1F1F))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, color = MeatYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActiveChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) MeatOrange else Color(0xFF1F1F1F))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun RecBadge(elapsedSeconds: Int, isPaused: Boolean = false) {
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
            if (isPaused) "PAUSE" else "REC",
            color = if (isPaused) MeatYellow else RecRed,
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
    val targetFraction = (percent.coerceIn(0, 100)) / 100f
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "inputLevelFill",
    )

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
    // Hold a "displayed" buffer that smoothly eases toward each new target value.
    // Lerp factor 0.35 per 16ms tick → ~60% closure over ~30ms (one or two frames).
    val displayed = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16) // ~60 fps
            // Resize displayed to match latest waveform length without losing existing values
            val target = waveform
            if (target.isEmpty()) {
                if (displayed.isNotEmpty()) {
                    // Decay toward zero when no input
                    for (i in displayed.indices) {
                        displayed[i] = displayed[i] * 0.85f
                    }
                }
                continue
            }
            // Match length
            while (displayed.size < target.size) displayed.add(0f)
            while (displayed.size > target.size) displayed.removeAt(displayed.size - 1)
            // Lerp toward target
            for (i in target.indices) {
                val curr = displayed[i]
                val targ = target[i]
                displayed[i] = curr + (targ - curr) * 0.35f
            }
        }
    }

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
            if (displayed.isEmpty()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 1f,
                )
                return@Canvas
            }
            // Tight vertical bars with vertical gradient (yellow center → orange peak)
            val bars = displayed.toList().takeLast(140)
            val barW = w / bars.size.toFloat()
            val midY = h / 2f
            for ((i, lvl) in bars.withIndex()) {
                val mag = abs(lvl).coerceIn(0f, 1f)
                val barH = max(2f, mag * h * 0.92f)
                val top = (h - barH) / 2f
                val x = i * barW
                val width = max(1f, barW - 0.6f)

                // Glow halo for tall bars (mag > 0.55): wider, lower-alpha block behind
                if (mag > 0.55f) {
                    val glowAlpha = ((mag - 0.55f) / 0.45f).coerceIn(0f, 1f) * 0.30f
                    drawRect(
                        color = MeatOrange.copy(alpha = glowAlpha),
                        topLeft = Offset(x - 0.8f, top - 1f),
                        size = Size(width + 1.6f, barH + 2f),
                    )
                }

                // Main bar with vertical gradient: yellow at middle, orange at peaks
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to MeatOrange.copy(alpha = 0.7f + 0.3f * mag),
                            0.5f to MeatYellow,
                            1.0f to MeatOrange.copy(alpha = 0.7f + 0.3f * mag),
                        ),
                        startY = top,
                        endY = top + barH,
                    ),
                    topLeft = Offset(x, top),
                    size = Size(width, barH),
                )
            }
            // Faint center line for visual reference
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1f,
            )
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

@Composable
private fun SlateHeader(
    sceneName: String,
    fileName: String,
    micSource: String,
    elapsedSeconds: Int,
) {
    // Extract take number from file name (e.g., "Scene_1_T05.wav" → "05")
    val takeNum = Regex("_T(\\d+)").find(fileName)?.groupValues?.get(1) ?: "—"
    val now = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A1A), Color(0xFF0A0A0A)),
                )
            )
            .border(1.dp, MeatYellow.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "SCENE",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    sceneName.ifBlank { "—" },
                    color = MeatYellow,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "TAKE",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    takeNum,
                    color = MeatOrange,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "MIC: ${micSource.ifBlank { "—" }}",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                now,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun PhaseMeter(correlation: Float) {
    val target = correlation.coerceIn(-1f, 1f)
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "phaseCorr",
    )
    CardWithLabel(label = "PHASE", trailing = {
        Text(
            text = "%.2f".format(animated),
            color = if (animated < 0f) Color(0xFFEA4F30) else MeatYellow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0A0A0A)),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = Color.White.copy(alpha = 0.18f),
                    start = Offset(w / 2f, 0f),
                    end = Offset(w / 2f, h),
                    strokeWidth = 1f,
                )
                val xFrac = (animated + 1f) / 2f
                val x = (w * xFrac).coerceIn(2f, w - 4f)
                val color = if (animated < 0f) Color(0xFFEA4F30) else MeatYellow
                drawRect(
                    color = color,
                    topLeft = Offset(x - 2f, 0f),
                    size = Size(4f, h),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("−1 out of phase", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
            Text("+1 in phase", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
        }
    }
}

// Helper — same formatter as MeatRecHome, kept private here so the component is self-contained
private fun formatElapsedFull(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

