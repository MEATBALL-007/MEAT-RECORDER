package com.example.recorderproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.ui.components.BrandWordmark
import com.example.recorderproject.ui.components.MeatrecMark
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Menu screen — Apple-style hub of all secondary destinations.
 *
 * Large hero header (parallaxes on scroll), then a 2-col grid of action cards
 * for: EQ, Spectrogram (Harmonic Portrait), Multi-take, Transcript, Room
 * profiler, Scene slicer, Settings.
 *
 * Each card has spring-bounce on tap and emoji+title+subtitle layout that
 * reads as a clear menu of features rather than a wall of buttons.
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
) {
    val scrollState = rememberScrollState()
    val scrollFrac = (scrollState.value.toFloat() / 600f).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Title fades in once the user scrolls past the hero
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
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
            // Hero — large title with parallax fade-out as you scroll
            HeroHeader(scrollFrac = scrollFrac)

            // 2-column grid of action cards
            val items = listOf(
                MenuItem("⚡", "Live EQ", "30-band biquad on last take", onOpenEQOnLast),
                MenuItem("🌈", "Spectrum", "Harmonic portrait viewer") {
                    val last = viewModel.recordFiles.value.lastOrNull()
                    if (last != null) viewModel.openPortrait(last)
                },
                MenuItem("🎬", "Multi-take", "Ghost-take comparison", onOpenMultiTake),
                MenuItem("📝", "Transcript", "Speech-to-text view") {
                    val last = viewModel.recordFiles.value.lastOrNull()
                    if (last != null) viewModel.openTranscript(last)
                },
                MenuItem("🏠", "Room profile", "Acoustic noise floor / RT60", onOpenRoomProfiler),
                MenuItem("✂️", "Scene slicer", "Detect silence-bounded scenes") {
                    val last = viewModel.recordFiles.value.lastOrNull()
                    if (last != null) viewModel.openSceneSlicer(last)
                },
                MenuItem("⚙️", "Settings", "Theme · sample rate · etc.", onOpenSettings),
                MenuItem("📊", "Statistics", "Total time · file count") {},
            )

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.chunked(2).forEachIndexed { rowIdx, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEachIndexed { colIdx, item ->
                            val idx = rowIdx * 2 + colIdx
                            // Stagger entrance for each card
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(40L * idx)
                                visible = true
                            }
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 6 }, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)),
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
private fun HeroHeader(scrollFrac: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                brush = Brush.verticalGradient(
                    0f to RecorderCharcoal,
                    1f to Color(0xFF15151B),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .alpha(1f - scrollFrac)
                .scale(1f - scrollFrac * 0.1f),
        ) {
            MeatrecMark(size = 72.dp)
            BrandWordmark(style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            ))
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

private data class MenuItem(
    val icon: String,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun MenuCard(item: MenuItem, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "menuCardScale",
    )
    Column(
        modifier = modifier
            .scale(scale)
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RecorderCharcoalCard)
            .clickable {
                pressed = true
                item.onClick()
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(item.icon, fontSize = 30.sp)
        Column {
            Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(item.subtitle, color = RecorderBlueGrey, fontSize = 10.sp, maxLines = 2)
        }
    }
    // Release the press scale after a frame so it bounces back
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(140)
            pressed = false
        }
    }
}
