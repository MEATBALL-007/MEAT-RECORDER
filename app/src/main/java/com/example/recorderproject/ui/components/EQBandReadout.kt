package com.example.recorderproject.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/** Pro polish — small monospace readout that appears over the curve when a band is selected. */
@Composable
fun EQBandReadout(
    band: EQBand?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = band != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier,
    ) {
        band ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC0C0C10))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "#${band.id}",
                    color = RecorderYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    band.type.displayName,
                    color = RecorderBlueGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("·", color = RecorderBlueGrey, fontSize = 11.sp)
                Text(
                    formatHz(band.frequencyHz),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("·", color = RecorderBlueGrey, fontSize = 11.sp)
                Text(
                    String.format("%+.1f dB", band.gainDb),
                    color = RecorderOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("·", color = RecorderBlueGrey, fontSize = 11.sp)
                Text(
                    "Q ${String.format("%.2f", band.q)}",
                    color = RecorderYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun formatHz(f: Float): String = when {
    f >= 1000f -> String.format("%.2f kHz", f / 1000f)
    else -> String.format("%.0f Hz", f)
}
