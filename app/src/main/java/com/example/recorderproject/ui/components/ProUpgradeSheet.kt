package com.example.recorderproject.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.billing.BillingManager
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.PI
import kotlin.math.sin

private data class ProSlide(
    val title: String,
    val blurb: String,
    val accentColor: Color,
    val draw: DrawScope.() -> Unit,
)

private val SlideBlue   = Color(0xFF1A6BFF)
private val SlideCyan   = Color(0xFF00CFFF)
private val SlideGreen  = Color(0xFF3DDC97)
private val SlidePurple = Color(0xFFA78BFA)
private val SlideTeal   = Color(0xFF2DD4BF)
private val SlidePink   = Color(0xFFF472B6)

private val proSlides = listOf(
    ProSlide(
        title = "Loudness Delivery",
        blurb = "Auto-render to Podcast, Broadcast, or custom LUFS target",
        accentColor = RecorderYellow,
        draw = { drawLoudnessVisual() },
    ),
    ProSlide(
        title = "Parametric EQ + Live DSP",
        blurb = "Visual EQ with noise gate, compressor & stereo widener",
        accentColor = SlideCyan,
        draw = { drawEqVisual() },
    ),
    ProSlide(
        title = "Studio Quality",
        blurb = "96 kHz / 32-bit float recording with any external mic",
        accentColor = SlideGreen,
        draw = { drawHiResVisual() },
    ),
    ProSlide(
        title = "Pitch Shift + Smart Capture",
        blurb = "Re-pitch takes, pre-roll buffer & voice-activated record",
        accentColor = SlidePurple,
        draw = { drawPitchVisual() },
    ),
    ProSlide(
        title = "Transcribe + Cloud Backup",
        blurb = "On-device text from audio + auto-upload to Google Drive",
        accentColor = SlideTeal,
        draw = { drawTranscribeVisual() },
    ),
    ProSlide(
        title = "All Themes + Analysis Suite",
        blurb = "10 appearance themes, spectrogram, room profiler, A/B compare",
        accentColor = SlidePink,
        draw = { drawThemesVisual() },
    ),
)

private fun ProFeature.initialSlide(): Int = when (this) {
    ProFeature.LOUDNESS_DELIVERY             -> 0
    ProFeature.FULL_EQ, ProFeature.LIVE_DSP  -> 1
    ProFeature.HIGH_RES_AUDIO,
    ProFeature.EXTERNAL_MIC                  -> 2
    ProFeature.PITCH_SHIFT,
    ProFeature.PRE_ROLL_VAD                  -> 3
    ProFeature.TRANSCRIPTION,
    ProFeature.CLOUD_BACKUP                  -> 4
    ProFeature.ANALYSIS_TOOLS,
    ProFeature.ALL_THEMES                    -> 5
    ProFeature.RECORDING_LIMIT               -> 0
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProUpgradeSheet(
    highlight: ProFeature?,
    priceText: String?,
    originalPriceText: String? = null,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val promoPrice = priceText ?: BillingManager.FALLBACK_PROMO_PRICE
    val wasPrice   = originalPriceText ?: BillingManager.FALLBACK_ORIGINAL_PRICE
    val discount   = BillingManager.LAUNCH_DISCOUNT_PERCENT

    val initialPage = highlight?.initialSlide() ?: 0
    val pagerState  = rememberPagerState(initialPage = initialPage) { proSlides.size }
    val sheet       = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = RecorderCharcoal,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "MEAT REC PRO",
                    color = RecorderYellow,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    "LAUNCH –$discount%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RecorderOrange)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }

            // ── Feature pager ────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val slide = proSlides[page]
                Column(modifier = Modifier.fillMaxSize()) {
                    // Visual — 80%
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.8f)
                            .background(RecorderCharcoal),
                    ) {
                        // Radial glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    slide.accentColor.copy(alpha = 0.18f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension * 0.65f,
                            ),
                        )
                        slide.draw(this)
                    }
                    // Caption — 20%
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.2f)
                            .background(RecorderCharcoalCard)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(slide.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(slide.blurb, color = RecorderBlueGrey, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }

            // ── Dot indicators ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                proSlides.indices.forEach { i ->
                    val active = pagerState.currentPage == i
                    val dotWidth by animateFloatAsState(
                        targetValue = if (active) 18f else 6f,
                        animationSpec = tween(200),
                        label = "dot",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = dotWidth.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (active) RecorderYellow else Color(0xFF3D3D4A)),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${pagerState.currentPage + 1} / ${proSlides.size}",
                    color = RecorderBlueGrey,
                    fontSize = 11.sp,
                )
            }

            // ── Price row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    wasPrice,
                    color = RecorderBlueGrey,
                    fontSize = 15.sp,
                    textDecoration = TextDecoration.LineThrough,
                )
                Text(
                    promoPrice,
                    color = RecorderYellow,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "lifetime",
                    color = RecorderBlueGrey,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // ── CTA ──────────────────────────────────────────────────────
            Button(
                onClick = onUpgrade,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    "Unlock MEAT REC Pro · $promoPrice",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Restore purchase", color = RecorderBlueGrey, fontSize = 13.sp)
            }
        }
    }
}

// ── Canvas visuals ────────────────────────────────────────────────────────────

private fun DrawScope.drawLoudnessVisual() {
    val barW = size.width * 0.08f
    val gap  = size.width * 0.06f
    val totalW = 3 * barW + 2 * gap
    var x = (size.width - totalW) / 2f
    val heights = listOf(size.height * 0.55f, size.height * 0.42f, size.height * 0.65f)

    heights.forEach { h ->
        val top = size.height * 0.15f + (size.height * 0.65f - h)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(RecorderYellow, RecorderOrange),
                startY = top, endY = top + h,
            ),
            topLeft = Offset(x, top),
            size = Size(barW, h),
        )
        x += barW + gap
    }
    // Dashed target line
    drawLine(
        color = RecorderYellow.copy(alpha = 0.55f),
        start = Offset(size.width * 0.18f, size.height * 0.22f),
        end   = Offset(size.width * 0.82f, size.height * 0.22f),
        strokeWidth = 2.dp.toPx(),
        pathEffect  = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
    )
}

private fun DrawScope.drawEqVisual() {
    val heights = listOf(0.35f, 0.55f, 0.70f, 0.60f, 0.80f, 0.65f, 0.45f, 0.30f)
    val barW = size.width * 0.065f
    val spacing = (size.width * 0.70f - heights.size * barW) / (heights.size - 1)
    var x = size.width * 0.15f
    val path = Path()

    heights.forEachIndexed { i, hRatio ->
        val h   = size.height * hRatio * 0.65f
        val top = (size.height - h) / 2f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(SlideCyan, SlideBlue),
                startY = top, endY = top + h,
            ),
            topLeft = Offset(x, top),
            size = Size(barW, h),
        )
        val cy = top
        if (i == 0) path.moveTo(x + barW / 2f, cy) else path.lineTo(x + barW / 2f, cy)
        x += barW + spacing
    }
    drawPath(path, color = SlideCyan.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawHiResVisual() {
    val midY = size.height * 0.6f
    val path = Path()
    val pts  = 120
    for (i in 0..pts) {
        val fx       = i / pts.toFloat()
        val envelope = sin(fx * PI.toFloat()) * size.height * 0.22f
        val y        = midY + sin(fx * 18f * PI.toFloat()).toFloat() * envelope
        val px       = fx * size.width
        if (i == 0) path.moveTo(px, y) else path.lineTo(px, y)
    }
    drawPath(path, color = SlideGreen.copy(alpha = 0.65f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawPitchVisual() {
    fun waveAt(midY: Float, alpha: Float, strokeW: Float) {
        val path = Path()
        for (i in 0..100) {
            val fx = i / 100f
            val y  = midY + sin(fx * 12f * PI.toFloat()).toFloat() * size.height * 0.1f
            val px = fx * size.width
            if (i == 0) path.moveTo(px, y) else path.lineTo(px, y)
        }
        drawPath(path, color = SlidePurple.copy(alpha = alpha), style = Stroke(width = strokeW))
    }

    waveAt(size.height * 0.68f, 0.4f, 2.dp.toPx())
    waveAt(size.height * 0.36f, 1.0f, 2.5f.dp.toPx())

    // Arrow
    val ax = size.width * 0.5f
    drawLine(
        color = SlidePurple.copy(alpha = 0.8f),
        start = Offset(ax, size.height * 0.62f),
        end   = Offset(ax, size.height * 0.42f),
        strokeWidth = 2.dp.toPx(),
    )
    val tip = Offset(ax, size.height * 0.42f)
    val aw  = 10.dp.toPx()
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - aw / 2, tip.y + aw)
            lineTo(tip.x + aw / 2, tip.y + aw)
            close()
        },
        color = SlidePurple,
    )
}

private fun DrawScope.drawTranscribeVisual() {
    // Waveform left
    val midY = size.height * 0.5f
    val wavePath = Path()
    for (i in 0..60) {
        val fx  = i / 60f
        val env = sin(fx * PI.toFloat()) * size.height * 0.18f
        val y   = midY + sin(fx * 14f * PI.toFloat()).toFloat() * env
        val px  = fx * size.width * 0.38f + size.width * 0.06f
        if (i == 0) wavePath.moveTo(px, y) else wavePath.lineTo(px, y)
    }
    drawPath(wavePath, color = SlideTeal.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

    // Arrow
    val arrowEndX = size.width * 0.56f
    drawLine(SlideTeal.copy(alpha = 0.6f), Offset(size.width * 0.48f, midY), Offset(arrowEndX, midY), 2.dp.toPx())
    drawPath(
        Path().apply {
            moveTo(arrowEndX, midY)
            lineTo(arrowEndX - 8.dp.toPx(), midY - 5.dp.toPx())
            lineTo(arrowEndX - 8.dp.toPx(), midY + 5.dp.toPx())
            close()
        },
        color = SlideTeal.copy(alpha = 0.6f),
    )

    // Text lines right
    val lx = size.width * 0.60f
    val ly = midY - size.height * 0.15f
    listOf(0.85f, 0.65f, 0.75f, 0.50f).forEachIndexed { i, w ->
        drawLine(
            color = SlideTeal.copy(alpha = 0.45f + i * 0.1f),
            start = Offset(lx, ly + i * size.height * 0.1f),
            end   = Offset(lx + size.width * 0.28f * w, ly + i * size.height * 0.1f),
            strokeWidth = 3.dp.toPx(),
            cap   = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawThemesVisual() {
    val swatchColors = listOf(RecorderOrange, RecorderYellow, SlideGreen, SlideBlue, SlidePurple, SlidePink)
    val r = size.width * 0.05f
    val total = swatchColors.size * r * 2 + (swatchColors.size - 1) * 8.dp.toPx()
    var sx = (size.width - total) / 2f + r
    swatchColors.forEach { c ->
        drawCircle(c, radius = r, center = Offset(sx, size.height * 0.30f))
        sx += r * 2 + 8.dp.toPx()
    }

    // Mini spectrum
    val bars    = 24
    val barW    = size.width * 0.025f
    val spacing = (size.width * 0.70f - bars * barW) / (bars - 1)
    var bx      = size.width * 0.15f
    val baseY   = size.height * 0.78f
    for (i in 0 until bars) {
        val frac = i / bars.toFloat()
        val h    = (sin(frac * PI.toFloat() * 1.5f + 0.5f) * size.height * 0.25f).coerceAtLeast(4.dp.toPx())
        val c    = when {
            frac < 0.33f -> SlidePink
            frac < 0.66f -> RecorderYellow
            else         -> RecorderOrange
        }
        drawRect(c.copy(alpha = 0.7f), topLeft = Offset(bx, baseY - h), size = Size(barW, h))
        bx += barW + spacing
    }
}
