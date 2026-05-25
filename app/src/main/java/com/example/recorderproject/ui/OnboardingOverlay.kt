package com.example.recorderproject.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.components.MeatrecMark
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

private data class OnboardingPage(
    val title: String,
    val body: String,
)

private val pages = listOf(
    OnboardingPage("Record at field-grade quality", "44.1 / 48 / 96 kHz · 16/24/32-bit · auto noise reduction · live waveform meter."),
    OnboardingPage("Sculpt with the live graph EQ", "8-band parametric · 10 plugin-emulation presets · 2D and 3D spectrogram views · noise-cut tap/draw/auto."),
    OnboardingPage("Keep your originals safe", "Apply with Keep both · Replace original · Discard EQ. Sidecar JSON remembers your chain."),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingOverlay(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { pages.size })

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .clickable(enabled = false) { },
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MeatrecMark(size = 72.dp)
            Box(modifier = Modifier.padding(top = 8.dp)) {
                Text("MEATrec", color = RecorderYellow, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp)
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) { idx ->
                val p = pages[idx]
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(RecorderCharcoalCard)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        p.title,
                        color = RecorderYellow,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        p.body,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // Page indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in pages.indices) {
                    val active = i == pager.currentPage
                    val scale by animateFloatAsState(if (active) 1f else 0.7f, spring(Spring.DampingRatioMediumBouncy), label = "dot$i")
                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (active) RecorderOrange else RecorderBlueGrey.copy(alpha = 0.5f)),
                    )
                }
            }

            // Footer buttons
            Row(
                Modifier.fillMaxWidth().padding(top = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDone) {
                    Text("Skip", color = RecorderBlueGrey)
                }
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
                ) {
                    Text("Get started", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
