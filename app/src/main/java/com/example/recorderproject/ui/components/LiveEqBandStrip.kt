package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MeatOrange = Color(0xFFFA4616)
private val MeatYellow = Color(0xFFFFC72C)

/**
 * 6-band live EQ strip — Q1 (simplified).
 *
 *  Each column: [+] button on top, gain readout, [-] button on bottom, Hz label.
 *  Each tap adjusts the band ±1 dB within ±12 dB range.
 *  Long-press the readout to reset to 0 dB.
 */
@Composable
fun LiveEqBandStrip(
    bandGainsDb: FloatArray,
    bandFreqsHz: IntArray = intArrayOf(60, 200, 500, 1000, 3000, 10000),
    onChangeBand: (band: Int, gainDb: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161616))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "LIVE EQ",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "tap +/− per band · ±12 dB",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in bandGainsDb.indices) {
                EqBand(
                    gainDb = bandGainsDb[i],
                    label = formatHz(bandFreqsHz.getOrElse(i) { 1000 }),
                    onUp = { onChangeBand(i, (bandGainsDb[i] + 1f).coerceAtMost(12f)) },
                    onDown = { onChangeBand(i, (bandGainsDb[i] - 1f).coerceAtLeast(-12f)) },
                    onReset = { onChangeBand(i, 0f) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EqBand(
    gainDb: Float,
    label: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // +
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF222222))
                .clickable(onClick = onUp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = MeatOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        // dB readout — tap to reset to 0
        Text(
            text = if (gainDb == 0f) "0" else "%+.0f".format(gainDb),
            color = when {
                gainDb == 0f -> Color.White.copy(alpha = 0.45f)
                gainDb > 0f -> MeatYellow
                else -> Color(0xFF7FB6F2)
            },
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(onClick = onReset)
                .padding(vertical = 4.dp),
        )
        // -
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF222222))
                .clickable(onClick = onDown),
            contentAlignment = Alignment.Center,
        ) {
            Text("−", color = MeatOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatHz(hz: Int): String = when {
    hz >= 1000 -> "${hz / 1000}k"
    else -> hz.toString()
}
