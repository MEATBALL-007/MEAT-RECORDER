package com.example.recorderproject.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.example.recorderproject.ui.components.BrandWordmark
import com.example.recorderproject.ui.components.LiquidBlobCanvas
import com.example.recorderproject.ui.components.MeatrecMark
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal

@Composable
fun SplashScreen(onDone: () -> Unit) {
    // Mark entrance
    val markScale = remember { Animatable(0.4f) }
    val markAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(20f) }

    // Continuous phase sweep on the EQ curve inside the mark
    val infinite = rememberInfiniteTransition(label = "splashPhase")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween<Float>(2400, easing = LinearEasing)),
        label = "phase",
    )

    LaunchedEffect(Unit) {
        markScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        markAlpha.animateTo(1f, tween(360))
        titleAlpha.animateTo(1f, tween(420))
        titleOffset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        kotlinx.coroutines.delay(900)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    0f to Color(0xFF15151B),
                    1f to RecorderCharcoal,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Liquid-blob backdrop — port-back of old splash visual
        LiquidBlobCanvas(modifier = Modifier.fillMaxSize().alpha(0.6f * markAlpha.value))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .scale(markScale.value)
                    .alpha(markAlpha.value),
                contentAlignment = Alignment.Center,
            ) {
                MeatrecMark(size = 112.dp, animatedPhase = phase)
            }

            Box(modifier = Modifier.alpha(titleAlpha.value).padding(top = 4.dp)) {
                BrandWordmark(style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                ))
            }
            Box(modifier = Modifier.alpha(titleAlpha.value * 0.9f)) {
                Text(
                    text = "Audio · field-grade · live EQ",
                    color = RecorderBlueGrey,
                    fontSize = 13.sp,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
