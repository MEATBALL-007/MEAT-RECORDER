package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.recorderproject.ui.theme.RecorderCharcoalCard

/**
 * G27: "Glass" card — translucent gradient + subtle inner border that mimics
 * iOS glass-blur material without needing a real BlurEffect (which requires
 * RenderEffect on API 31+ and ranks the surface as opaque-over-content anyway).
 *
 * Pair with Box(...) backgrounds that have texture (LiquidBlob, hero gradients)
 * for the glassy feel.
 */
@Composable
fun GlassCard(
    cornerRadius: Dp = 14.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.02f),
                    ),
                ),
            )
            .background(RecorderCharcoalCard.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(cornerRadius)),
    ) {
        content()
    }
}
