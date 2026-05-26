package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange

/**
 * G28: iOS-style segmented control — sliding pill that follows the selected
 * index with a spring animation. Looks Apple-y next to chip rows.
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val targetFrac = selectedIndex.toFloat() / options.size.toFloat()
    val animatedFrac by animateFloatAsState(
        targetValue = targetFrac,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "segPos",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xFF0C0C10))
            .padding(3.dp),
    ) {
        val pillWidth = maxWidth / options.size
        // Sliding pill behind text — driven by animatedFrac
        Box(
            modifier = Modifier
                .padding(start = (pillWidth * animatedFrac))
                .size(width = pillWidth, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RecorderOrange),
        )
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            options.forEach { (value, label) ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (value == selected) Color.White else RecorderBlueGrey,
                        fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
