package com.example.recorderproject.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import com.example.recorderproject.ui.theme.Spacing

/**
 * MEAT REC brand wordmark — two-color split.
 *
 *   MEAT (RecorderOrange)  REC (RecorderYellow)
 *
 * Bold, all-caps, 2.5sp letter-spacing, 8dp gap between words.
 * `style` defaults to titleLarge; pass `displayMedium` (or similar) for
 * splash / hero surfaces.
 *
 * Examples:
 *   BrandWordmark()                                              // TopBar size
 *   BrandWordmark(style = MaterialTheme.typography.displayMedium) // Splash size
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = brandWordmarkDefaultStyle(),
    showPro: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("MEAT", style = style, color = RecorderOrange)
        Text("REC",  style = style, color = RecorderYellow)
        // Appended once the user owns Pro.
        if (showPro) {
            Text("PRO", style = style.copy(fontWeight = FontWeight.Black), color = RecorderYellow)
        }
    }
}

@Composable
@ReadOnlyComposable
private fun brandWordmarkDefaultStyle(): TextStyle =
    MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.5.sp,
    )
