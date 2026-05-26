package com.example.recorderproject.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.components.IconLineDisc
import com.example.recorderproject.ui.components.IconLineEq
import com.example.recorderproject.ui.components.IconLineHeadphones
import com.example.recorderproject.ui.components.IconLineMenu
import com.example.recorderproject.ui.components.IconLineMic
import com.example.recorderproject.ui.components.IconLineSettings
import com.example.recorderproject.ui.components.IconLineSpectrum
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Design Picker screen — swipe through 4 design directions side-by-side.
 *
 * Each page is a static mock of the home screen in that visual language so the
 * user can FEEL the difference before we commit to one and rebuild the rest of
 * the app to match.
 *
 *   A. STUDIO HARDWARE  — vintage analog console, brushed-metal panels, LED display
 *   B. BRUTALIST MONO   — pure black + one accent, huge type, hairline borders
 *   C. DAW PRO          — flat dark grey, dense data, monospace tabular numerics
 *   D. APPLE CLEAN      — glass cards, soft gradients, large titles, generous space
 *
 * All mocks use the line-icon library (no emojis) so the no-emoji direction is
 * visible up front.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DesignPickerScreen(
    onBack: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 1) { 4 } // start at B per user pick

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DESIGN STUDIO  ·  ${pageLabel(pagerState.currentPage)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
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
            Modifier.fillMaxSize().padding(padding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().weight(1f),
            ) { page ->
                when (page) {
                    0 -> MockA_StudioHardware()
                    1 -> MockB_BrutalistMono()
                    2 -> MockC_DawPro()
                    3 -> MockD_AppleClean()
                }
            }

            // Page dots
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                for (i in 0 until 4) {
                    val active = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) RecorderOrange else RecorderBlueGrey.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

private fun pageLabel(i: Int) = when (i) {
    0 -> "A · STUDIO HARDWARE"
    1 -> "B · BRUTALIST MONO"
    2 -> "C · DAW PRO"
    3 -> "D · APPLE CLEAN"
    else -> ""
}

// ============================================================================
// A. STUDIO HARDWARE — brushed metal, LED display, VU meter, knob
// ============================================================================
@Composable
private fun MockA_StudioHardware() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1A18)) // warm dark
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top brand bar with brushed-metal feel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF3A3733), Color(0xFF2A2724), Color(0xFF1F1C19))
                    )
                )
                .border(1.dp, Color(0xFF4A4744), RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("MEAT REC", color = Color(0xFFE8E0D0),
                fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 3.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF6BCB77))) // power LED
                Text("MIC-1", color = Color(0xFF8E847A), fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }

        // Big LED-style readout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0A0E08))
                .border(2.dp, Color(0xFF2A2724), RoundedCornerShape(4.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "00:00:00",
                color = Color(0xFF45D87B), // CRT green
                style = LocalAppTypography.current.numericLarge,
                fontSize = 56.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
            )
        }

        // VU meter
        VuMeterMock()

        // Big metal record button
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(44.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFC8362A), Color(0xFF8E1A12)),
                        )
                    )
                    .border(3.dp, Color(0xFF3A3733), RoundedCornerShape(44.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            }
        }
        Text(
            "TAP TO RECORD",
            color = Color(0xFF8E847A),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun VuMeterMock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE8E0CC))
            .padding(14.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val w = this.size.width; val h = this.size.height
            // Arc scale
            val cx = w / 2; val cy = h * 1.4f
            val r = h * 1.05f
            drawArc(
                color = Color(0xFF3A2010),
                startAngle = 210f, sweepAngle = 120f, useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 2f),
            )
            // Tick marks
            for (i in 0..10) {
                val a = Math.toRadians((210 + i * 12).toDouble())
                val r1 = r * 0.92f; val r2 = r * 1.0f
                drawLine(
                    Color(0xFF3A2010),
                    Offset((cx + r1 * Math.cos(a)).toFloat(), (cy + r1 * Math.sin(a)).toFloat()),
                    Offset((cx + r2 * Math.cos(a)).toFloat(), (cy + r2 * Math.sin(a)).toFloat()),
                    strokeWidth = if (i % 5 == 0) 2.5f else 1.5f,
                )
            }
            // Needle pointing slightly right of center
            val needleA = Math.toRadians(258.0)
            drawLine(
                Color(0xFFC8362A),
                Offset(cx, cy),
                Offset((cx + r * 0.95f * Math.cos(needleA)).toFloat(),
                       (cy + r * 0.95f * Math.sin(needleA)).toFloat()),
                strokeWidth = 2f, cap = StrokeCap.Round,
            )
            drawCircle(Color(0xFF3A2010), 6f, Offset(cx, cy))
        }
    }
}

// ============================================================================
// B. BRUTALIST MONO — pure black, one accent, hairline borders, huge type
// ============================================================================
@Composable
private fun MockB_BrutalistMono() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header strip — hairline divider only, no card
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconLineMenu(tint = Color.White, size = 20.dp)
                Text("MEAT/REC", color = Color.White,
                    fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 4.sp)
                IconLineSettings(tint = Color.White, size = 20.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
        }

        // Status label
        Text(
            "STATUS / READY ━━━━━━━",
            color = Color.White,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
        )

        // GIANT timecode
        Text(
            "00:00",
            color = Color.White,
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-3).sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "48 kHz · 16-BIT · MONO",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontFamily = FontFamily.Monospace,
        )

        // Square record button with hairline border
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(0.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(RecorderOrange),
                )
            }
        }
        Text(
            "[ TAP TO RECORD ]",
            color = Color.White,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        // Tool row — text-only, separated by pipes
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MON ◯", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
            Text("EQ  ◯", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
            Text("GATE◯", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
            Text("MIC ●", color = RecorderOrange, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
        }
    }
}

// ============================================================================
// C. DAW PRO — flat dark grey, dense data, monospace
// ============================================================================
@Composable
private fun MockC_DawPro() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1B1D))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Tool bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF252528))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconLineMenu(tint = Color(0xFFB8B8BC), size = 16.dp)
            Text("MEAT.REC", color = Color(0xFFB8B8BC),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold)
            Box(Modifier.height(14.dp).size(1.dp, 14.dp).background(Color(0xFF3A3A3D)))
            Text("EDIT", color = Color(0xFF8C8C90), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("MIX", color = Color(0xFF8C8C90), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("EXPORT", color = Color(0xFF8C8C90), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        // Transport row with monospace timecode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF252528))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Disc record indicator
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFC8362A)),
                contentAlignment = Alignment.Center,
            ) {
                IconLineDisc(tint = Color.White, fill = null, size = 18.dp)
            }
            Column {
                Text("REC", color = Color(0xFFC8362A), fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp)
                Text("00:00:00.000", color = Color(0xFFE8E8EA),
                    fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        // 6-band EQ strip — like a mixer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF252528))
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconLineEq(tint = Color(0xFFB8B8BC), size = 14.dp)
                    Text("EQ / 6 BANDS", color = Color(0xFFB8B8BC),
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Row(
                    Modifier.fillMaxWidth().height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val gains = floatArrayOf(0.25f, 0.6f, 0.85f, 0.45f, 0.30f, 0.55f)
                    val labels = arrayOf("60", "200", "500", "1k", "3k", "10k")
                    val colors = arrayOf(
                        Color(0xFFEC5D4F), Color(0xFFEC8B4F), Color(0xFFE9C04F),
                        Color(0xFF4FB8EC), Color(0xFF8C4FEC), Color(0xFFE94F9D),
                    )
                    for (i in gains.indices) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color(0xFF111114)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(gains[i])
                                        .align(Alignment.BottomCenter)
                                        .background(colors[i]),
                                )
                            }
                            Text(labels[i], color = Color(0xFF8C8C90),
                                fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // Levels meter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF252528))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("L", color = Color(0xFF8C8C90), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            LevelBarMock(0.62f, modifier = Modifier.weight(1f))
            Text("-12.4dB", color = Color(0xFFE8E8EA),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF252528))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("R", color = Color(0xFF8C8C90), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            LevelBarMock(0.58f, modifier = Modifier.weight(1f))
            Text("-13.1dB", color = Color(0xFFE8E8EA),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LevelBarMock(level: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(12.dp).background(Color(0xFF111114))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(level)
                .background(
                    brush = Brush.horizontalGradient(
                        0f to Color(0xFF4FEC5D), 0.7f to Color(0xFFE9C04F), 1f to Color(0xFFEC5D4F),
                    )
                ),
        )
    }
}

// ============================================================================
// D. APPLE CLEAN — glass cards, soft gradients, large title, generous spacing
// ============================================================================
@Composable
private fun MockD_AppleClean() {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    0f to Color(0xFF0E0E12),
                    1f to Color(0xFF14141A),
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Large iOS-style title
        Column {
            Text("Recordings", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, letterSpacing = 0.5.sp)
            Text("Studio", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        }

        // Hero glass card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f),
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                .padding(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("READY", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
                Text("00:00", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                Text("Untitled · 48 kHz · 16-bit", color = Color.White.copy(alpha = 0.40f), fontSize = 12.sp)

                Box(modifier = Modifier.padding(top = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(RecorderOrange, Color(0xFFB02E0F))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White))
                    }
                }
            }
        }

        // Glass tool row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolCardClean("Monitor", iconColor = Color(0xFF7FB6F2)) { IconLineHeadphones(tint = Color(0xFF7FB6F2), size = 18.dp) }
            ToolCardClean("Live EQ", iconColor = RecorderYellow) { IconLineEq(tint = RecorderYellow, size = 18.dp) }
            ToolCardClean("Mic", iconColor = Color.White) { IconLineMic(tint = Color.White, size = 18.dp) }
        }

        // Library hint
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            IconLineSpectrum(tint = Color.White.copy(alpha = 0.6f), size = 18.dp)
        }
    }
}

@Composable
private fun ToolCardClean(label: String, iconColor: Color, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.02f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

