package com.example.recorderproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.ui.components.BrandWordmark
import com.example.recorderproject.ui.components.LiquidBlobCanvas
import com.example.recorderproject.ui.components.MeatrecMark
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Menu screen redesigned (H4): hero with LiquidBlob backdrop + parallax,
 * 2-col grid of glass cards. Each card has its own accent gradient — visual
 * variety instead of every card being the same orange.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    viewModel: RecorderViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEQOnLast: () -> Unit,
    onOpenRoomProfiler: () -> Unit,
    onOpenMultiTake: () -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenDesignPicker: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scrollFrac = (scrollState.value.toFloat() / 480f).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MENU",
                        color = RecorderYellow.copy(alpha = scrollFrac),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal.copy(alpha = scrollFrac)),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
        ) {
            HeroHeader(scrollFrac = scrollFrac, showPro = viewModel.isPro.collectAsStateWithLifecycle().value)

            // Accent palette — each card gets its own visual tint
            val items = listOf(
                MenuItem("⚡", "Live EQ", "30-band biquad on last take", AccentSpec(Color(0xFFFA4616), Color(0xFFFA9112)), onOpenEQOnLast),
                MenuItem("🌈", "Spectrum", "Harmonic portrait viewer", AccentSpec(Color(0xFFFFC72C), Color(0xFFFFE3C3))) {
                    val last = viewModel.recordFiles.value.lastOrNull()
                    if (last != null) viewModel.openPortrait(last)
                },
                MenuItem("🎬", "Multi-take", "Ghost-take comparison", AccentSpec(Color(0xFF3DC399), Color(0xFF7FE5C8)), onOpenMultiTake),
                MenuItem("📝", "Transcript", "Speech-to-text view", AccentSpec(Color(0xFF7B8189), Color(0xFFB0B7BF))) {
                    val last = viewModel.recordFiles.value.lastOrNull()
                    if (last != null) viewModel.openTranscript(last)
                },
                MenuItem("🏠", "Room profile", "Acoustic noise floor · RT60", AccentSpec(Color(0xFFE65F39), Color(0xFFFB8D6B)), onOpenRoomProfiler),
                MenuItem("✂️", "Scene slicer", "Detect silence-bounded scenes", AccentSpec(Color(0xFFFA4616), Color(0xFFFFC72C))) {
                    val last = viewModel.recordFiles.value.lastOrNull()
                    if (last != null) viewModel.openSceneSlicer(last)
                },
                MenuItem("⚙️", "Settings", "Theme · sample rate · etc.", AccentSpec(Color(0xFF7B8189), Color(0xFFAAB1BA)), onOpenSettings),
                MenuItem("📊", "Statistics", "Total time · file count", AccentSpec(Color(0xFFFFC72C), Color(0xFFFA9112)), onOpenStats),
                MenuItem("◐", "Design Studio", "Preview 4 design directions", AccentSpec(Color(0xFFFA4616), Color(0xFFFFC72C)), onOpenDesignPicker),
                MenuItem("📋", "Sound Report", "Export CSV of all takes", AccentSpec(Color(0xFF3DC399), Color(0xFF7FE5C8))) {
                    viewModel.closeMenu()
                    viewModel.exportSoundReport()
                },
            )

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.chunked(2).forEachIndexed { rowIdx, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowItems.forEachIndexed { colIdx, item ->
                            val idx = rowIdx * 2 + colIdx
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(40L * idx)
                                visible = true
                            }
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(tween(280)) + slideInVertically(initialOffsetY = { it / 6 }, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)),
                                modifier = Modifier.weight(1f),
                            ) {
                                MenuCard(item, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        if (rowItems.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(scrollFrac: Float, showPro: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(
                brush = Brush.verticalGradient(
                    0f to RecorderCharcoal,
                    1f to Color(0xFF15151B),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Subtle LiquidBlob behind the wordmark (≈20% strength so it doesn't overpower)
        Box(modifier = Modifier.fillMaxWidth().height(260.dp).alpha(0.22f * (1f - scrollFrac))) {
            LiquidBlobCanvas(modifier = Modifier.fillMaxWidth().height(260.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .alpha(1f - scrollFrac)
                .scale(1f - scrollFrac * 0.1f),
        ) {
            MeatrecMark(size = 84.dp)
            BrandWordmark(
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                ),
                showPro = showPro,
            )
            Text(
                "MENU",
                color = RecorderBlueGrey,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 6.sp,
            )
        }
    }
}

private data class AccentSpec(val from: Color, val to: Color)

private data class MenuItem(
    val icon: String,
    val title: String,
    val subtitle: String,
    val accent: AccentSpec,
    val onClick: () -> Unit,
)

@Composable
private fun MenuCard(item: MenuItem, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "menuCardScale",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A20),
                        Color(0xFF111116),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        item.accent.from.copy(alpha = 0.35f),
                        item.accent.to.copy(alpha = 0.05f),
                    ),
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable {
                pressed = true
                item.onClick()
            }
            .padding(16.dp),
    ) {
        // Halo glow behind the icon — uses the card's accent
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            item.accent.from.copy(alpha = 0.30f),
                            item.accent.to.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.icon, fontSize = 30.sp)
        }
        Column(
            Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(item.subtitle, color = RecorderBlueGrey, fontSize = 10.sp, maxLines = 2, letterSpacing = 0.4.sp)
        }
        // Accent line at the bottom edge — varies card to card
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(width = 32.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(item.accent.from, item.accent.to),
                    ),
                ),
        )
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(160)
            pressed = false
        }
    }
}
