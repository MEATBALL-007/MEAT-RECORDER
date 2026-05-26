package com.example.recorderproject.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.ui.components.OrangeUnderglow
import com.example.recorderproject.ui.theme.AppTheme

/**
 * MeatRec Settings — pixel-faithful rebuild of the 22 May APK settings screen.
 *
 * Reference: recovery/screenshots/10-settings.png
 *
 * Layout top → bottom:
 *   1. Orange top bar with back arrow + "Settings" white + "Audio, recording &
 *      appearance" yellow subtitle
 *   2. APPEARANCE section header (tracked caps, dim grey)
 *   3. 2-column theme grid (10 themes: Midnight / Navy / Forest / Maroon /
 *      Carbon / Violet / Steel / Amber / Slate / Obsidian)
 *      - Each card = theme's bg color · 3 dots top-left (theme + orange + yellow)
 *        · theme name centered · two small level bars at the bottom corners
 *      - Selected = thick orange border + orange checkmark dot top-right
 *   4. AUDIO QUALITY section header
 *   5. Sample Rate label + 3 pills (44.1 / 48 / 96 kHz)
 */
@Composable
fun MeatRecSettings(
    viewModel: RecorderViewModel,
    currentTheme: AppTheme,
    onChangeTheme: (AppTheme) -> Unit,
    onBack: () -> Unit,
    onChangeMode: () -> Unit = {},
) {
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Orange top bar (same style as home but with back arrow + 2-line title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFA4616),
                            Color(0xFFE13606),
                        ),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Settings",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Audio, recording & appearance",
                    color = MeatYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                )
            }
        }

        // Underglow strip — consistent with home
        OrangeUnderglow(modifier = Modifier.fillMaxWidth())

        // 2-5. Scrollable body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // P4: Mode reset row — back to mode selector
            val currentMode = viewModel.recorderMode.collectAsStateWithLifecycle().value
            SectionHeader("RECORDING MODE")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161616))
                    .clickable(onClick = onChangeMode)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(currentMode.accent),
                    )
                    Column {
                        Text(currentMode.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(currentMode.shortHelp, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                    }
                }
                Text("Change →", color = MeatOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Q3: Storage indicator + remaining time estimate
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val freeMb = freeBytes / (1024L * 1024L)
            val sampleRateVal = viewModel.sampleRate.collectAsStateWithLifecycle().value
            val bitDepthVal = viewModel.bitDepth.collectAsStateWithLifecycle().value
            val channelsVal = viewModel.channelCount.collectAsStateWithLifecycle().value
            val bytesPerSec = sampleRateVal.toLong() * (bitDepthVal / 8) * channelsVal
            val remainingSec = if (bytesPerSec > 0) freeBytes / bytesPerSec else 0L
            val remainingH = remainingSec / 3600
            val remainingM = (remainingSec % 3600) / 60
            SectionHeader("STORAGE")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161616))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Free space", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${if (freeMb < 1024) "$freeMb MB" else "%.1f GB".format(freeMb / 1024f)} · " +
                            "~${remainingH}h ${remainingM}m at current settings",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                    )
                }
                // Tiny visual bar — % used (out of total). StatFs gives availableBlocks, not total
                val totalBytes = stat.blockCountLong * stat.blockSizeLong
                val usedFrac = if (totalBytes > 0) 1f - (freeBytes.toFloat() / totalBytes.toFloat()) else 0f
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF2A2A2A)),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = (60 * usedFrac).dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (usedFrac > 0.85f) MeatOrange else MeatYellow),
                    )
                }
            }

            SectionHeader("APPEARANCE")
            ThemeGrid(currentTheme = currentTheme, onChange = onChangeTheme)

            // Faint divider
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            )

            SectionHeader("AUDIO QUALITY")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Sample Rate",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                SampleRatePillRow(
                    current = sampleRate,
                    onChange = { viewModel.updateSampleRate(it) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 13.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ThemeGrid(currentTheme: AppTheme, onChange: (AppTheme) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTheme.entries.chunked(2).forEach { rowThemes ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowThemes.forEach { theme ->
                    ThemeCard(
                        theme = theme,
                        selected = theme == currentTheme,
                        onClick = { onChange(theme) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowThemes.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val cardBg = theme.surface
    // Spring everything when selection state changes — feels tactile.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "themePress",
    )
    val borderColorAnim by animateColorAsState(
        targetValue = if (selected) MeatOrange else Color.White.copy(alpha = 0.08f),
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        label = "themeBorder",
    )
    val borderWidthAnim by animateDpAsState(
        targetValue = if (selected) 2.5.dp else 1.dp,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "themeBorderWidth",
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .height(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(borderWidthAnim, borderColorAnim, RoundedCornerShape(14.dp))
            .clickable {
                pressed = true
                onClick()
            }
            .padding(14.dp),
    ) {
        // Glow halo behind the card when selected — subtle aura
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MeatOrange.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        // 3 dots at top-left
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(color = theme.background, ringed = true)
            Dot(color = MeatOrange)
            Dot(color = MeatYellow)
        }

        // Selected checkmark — orange circle with white check at top-right
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeatOrange),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✓",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Theme name centered (slightly left-aligned in old)
        Text(
            theme.displayName,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        // Two small level bars at the bottom corners
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(width = 32.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.20f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(width = 32.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        )
    }

    // Release press scale after a frame so it bounces back
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(140)
            pressed = false
        }
    }
}

@Composable
private fun Dot(color: Color, ringed: Boolean = false) {
    if (ringed) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .border(1.dp, MeatYellow.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                .background(color),
        )
    } else {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color),
        )
    }
}

@Composable
private fun SampleRatePillRow(current: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(44100 to "44.1 kHz", 48000 to "48 kHz", 96000 to "96 kHz").forEach { (value, label) ->
            val active = value == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) MeatOrange else Color(0xFF222222))
                    .clickable { onChange(value) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Color.White.copy(alpha = 0.50f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
