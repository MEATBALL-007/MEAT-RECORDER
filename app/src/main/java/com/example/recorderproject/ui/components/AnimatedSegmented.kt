package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Premium segmented control with a sliding orange pill indicator instead of background swap.
 * The pill physics-springs between options for a satisfying tactile feel.
 */
@Composable
fun <T> AnimatedSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
) {
    val selectedIdx = options.indexOf(selected).coerceAtLeast(0)
    var totalWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val pillWidth: Dp = with(density) {
        if (options.isEmpty()) 0.dp else (totalWidth / options.size).toDp()
    }
    val pillOffsetTarget: Dp = with(density) {
        (selectedIdx * (if (options.isEmpty()) 0 else totalWidth / options.size)).toDp()
    }
    val pillOffset by animateDpAsState(
        targetValue = pillOffsetTarget,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "pillSlide",
    )

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color(0xFF0C0C10))
            .padding(3.dp)
            .onSizeChanged { totalWidth = it.width },
    ) {
        // Sliding pill
        if (totalWidth > 0) {
            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(RecorderOrange)
                    // pillWidth derived from total
                    .then(Modifier),
            ) {
                // Empty — the pill fills the option slot, label is rendered above
                Box(modifier = Modifier.fillMaxHeight()) {
                    Text("", modifier = Modifier.padding(start = pillWidth))
                }
            }
        }
        // Labels row above the pill
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            for (opt in options) {
                val active = opt == selected
                Text(
                    text = label(opt),
                    color = if (active) Color.White else RecorderBlueGrey,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(opt) }
                        .padding(vertical = 5.dp),
                )
            }
        }
    }
}
