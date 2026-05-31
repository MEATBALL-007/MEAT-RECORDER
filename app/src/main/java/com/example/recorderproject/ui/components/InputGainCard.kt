package com.example.recorderproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.round

private val Orange   = Color(0xFFFA4616)
private val Yellow   = Color(0xFFFFC72C)
private val BlueGrey = Color(0xFF7B8189)
private val Red      = Color(0xFFC0392B)
private val CardBg   = Color(0xFF161616)
private val MutedTxt = Color(0xFFB0B6BB)

/**
 * Input level + gain control.
 *
 * Top half: horizontal raw-peak meter with zones:
 *   - green/orange (<= -18 dBFS): healthy
 *   - yellow      (-18..-3 dBFS): hot but safe
 *   - red         (>-3 dBFS):     about to clip
 * Plus the current peak-dBFS readout in mono-numbers on the right.
 *
 * Bottom half: input-gain slider, range -12..+24 dB, snapped to 0.5 dB steps.
 *
 * `peakDbfs` is expected to be the held-and-decaying peak (the recorder handles
 * the decay) — this component just renders, doesn't animate the value itself.
 */
@Composable
fun InputGainCard(
    peakDbfs: Float,
    inputGainDb: Float,
    onChangeInputGain: (Float) -> Unit,
    modifier: Modifier = Modifier,
    gainMinDb: Float = -12f,
    gainMaxDb: Float = 24f,
    gainStepDb: Float = 0.5f,
) {
    // Scale: bar runs from -60 dBFS (left, silent) to 0 dBFS (right, clip).
    val scaleMin = -60f
    val scaleMax = 0f
    fun norm(db: Float): Float {
        if (!db.isFinite()) return 0f
        return ((db - scaleMin) / (scaleMax - scaleMin)).coerceIn(0f, 1f)
    }
    val fill = norm(peakDbfs)

    val fillColor = when {
        !peakDbfs.isFinite()    -> BlueGrey
        peakDbfs >= -3f          -> Red
        peakDbfs >= -18f         -> Yellow
        else                     -> Orange
    }
    val readoutText = if (peakDbfs.isFinite()) "%.1f dBFS".format(peakDbfs) else "—"
    val readoutColor = when {
        peakDbfs >= -0.5f -> Red
        peakDbfs >= -6f   -> Yellow
        else              -> Color.White
    }

    Column(
        modifier = modifier
            .background(CardBg, shape = RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "INPUT",
                color = MutedTxt,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = readoutText,
                color = readoutColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Meter bar with zones drawn as background gradient ticks.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        ) {
            // Background track
            drawRect(BlueGrey.copy(alpha = 0.15f), size = size)
            // Yellow zone marker at -18 dBFS
            val xY = size.width * norm(-18f)
            drawRect(
                color = Yellow.copy(alpha = 0.08f),
                topLeft = Offset(xY, 0f),
                size = Size(size.width - xY, size.height),
            )
            // Red zone marker at -3 dBFS
            val xR = size.width * norm(-3f)
            drawRect(
                color = Red.copy(alpha = 0.12f),
                topLeft = Offset(xR, 0f),
                size = Size(size.width - xR, size.height),
            )
            // Actual fill
            drawRect(
                color = fillColor.copy(alpha = 0.95f),
                size = Size(size.width * fill, size.height),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "GAIN",
                color = MutedTxt,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "%+.1f dB".format(inputGainDb),
                color = Orange,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val steps = (((gainMaxDb - gainMinDb) / gainStepDb) - 1).toInt().coerceAtLeast(0)
        Slider(
            value = inputGainDb,
            onValueChange = { raw ->
                val snapped = (round(raw / gainStepDb) * gainStepDb).coerceIn(gainMinDb, gainMaxDb)
                onChangeInputGain(snapped)
            },
            valueRange = gainMinDb..gainMaxDb,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Orange,
                activeTrackColor = Orange,
                inactiveTrackColor = BlueGrey.copy(alpha = 0.3f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
