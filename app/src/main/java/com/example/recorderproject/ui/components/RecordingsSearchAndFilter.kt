package com.example.recorderproject.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * F6: Search bar for the RECORDINGS list — compact pill with magnifier + clear-x.
 */
@Composable
fun RecordingsSearchBar(
    query: String,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RecorderCharcoalCard)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = RecorderBlueGrey, modifier = Modifier.padding(end = 8.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
            singleLine = true,
            cursorBrush = SolidColor(RecorderOrange),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { /* keyboard hides on its own */ }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search recordings…", color = RecorderBlueGrey, fontSize = 14.sp)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear search",
                tint = RecorderYellow,
                modifier = Modifier.clickable { onChange("") }.padding(4.dp),
            )
        }
    }
}

/**
 * F7: Filter chips above the RECORDINGS list — All / Starred / Locked / NR / EQ.
 */
@Composable
fun RecordingsFilterChips(
    current: RecorderViewModel.FileFilter,
    onChange: (RecorderViewModel.FileFilter) -> Unit,
) {
    val items = listOf(
        RecorderViewModel.FileFilter.ALL to "All",
        RecorderViewModel.FileFilter.STARRED to "★ Starred",
        RecorderViewModel.FileFilter.LOCKED to "🔒 Locked",
        RecorderViewModel.FileFilter.NR to "NR",
        RecorderViewModel.FileFilter.EQ to "EQ",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (value, label) ->
            val active = value == current
            var pressed by remember { mutableStateOf(false) }

            val chipBg by animateColorAsState(
                targetValue = if (active) RecorderOrange else RecorderCharcoalCard,
                animationSpec = tween(durationMillis = 150),
                label = "chipBg_$label",
            )
            val textColor by animateColorAsState(
                targetValue = if (active) Color.White else RecorderBlueGrey,
                animationSpec = tween(durationMillis = 150),
                label = "chipText_$label",
            )
            val pressScale by animateFloatAsState(
                targetValue = if (pressed) 0.96f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "chipScale_$label",
            )

            Text(
                label,
                color = textColor,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                modifier = Modifier
                    .scale(pressScale)
                    .clip(RoundedCornerShape(14.dp))
                    .background(chipBg)
                    .pointerInput(value) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onChange(value) },
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
