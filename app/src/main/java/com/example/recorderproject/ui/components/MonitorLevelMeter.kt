package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.MonitorLevel
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.SemanticError
import com.example.recorderproject.ui.theme.Spacing
import com.example.recorderproject.ui.theme.SpectrumHigh
import com.example.recorderproject.ui.theme.SpectrumLow
import com.example.recorderproject.ui.theme.SpectrumMid
import com.example.recorderproject.ui.theme.SurfaceContainerHigh

/**
 * Pre-record input level meter.
 *
 *   INPUT LEVEL                              -12.3 dB
 *   ▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░│░░  •
 *                                         ↑
 *                                       peak hold
 *
 * Visible only when AudioMonitor is on and we are not recording — caller decides.
 *
 * Layout precedent: old MEATrec RecorderApp's INPUT LEVEL meter used `labelSmall`
 * + percentage. We upgrade to dB readout via `numericSmall` (tabular figures, no
 * digit jitter) and add peak-hold + clip indicator.
 */
@Composable
fun MonitorLevelMeter(
    level: MonitorLevel,
    modifier: Modifier = Modifier,
) {
    // Spring-animate the fill fractions so the bar eases instead of snapping.
    val targetRmsFrac = fractionFromDb(level.rmsDb)
    val targetPeakFrac = fractionFromDb(level.peakDb)
    val rmsFrac by animateFloatAsState(
        targetValue = targetRmsFrac,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "monitorRmsFill",
    )
    val peakFrac by animateFloatAsState(
        targetValue = targetPeakFrac,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "monitorPeakFill",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Top row: caps label + dB readout + (optional) clip dot
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "INPUT LEVEL",
                style = LocalAppTypography.current.labelTiny,
                color = RecorderBlueGrey,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = formatDb(level.rmsDb),
                    style = LocalAppTypography.current.numericSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (level.clipped) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(SemanticError),
                    )
                }
            }
        }

        // The bar itself
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(MaterialTheme.shapes.small)
                .background(SurfaceContainerHigh),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                val w = size.width
                val h = size.height

                // Fill — gradient stops positioned so red appears only near 0 dB
                val gradient = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f  to SpectrumLow,
                        0.80f to SpectrumMid,
                        0.95f to SpectrumHigh,
                        1.0f  to SemanticError,
                    ),
                    startX = 0f,
                    endX = w,
                )
                drawRect(brush = gradient, topLeft = Offset.Zero, size = Size(width = w * rmsFrac, height = h))

                // Peak-hold marker: thin vertical bar (spring-eased)
                if (peakFrac > 0f) {
                    val x = (w * peakFrac).coerceIn(0f, w - 2f)
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(x, 0f),
                        size = Size(width = 2f, height = h),
                    )
                }
            }
        }
    }
}

/** Map dB [-60..0] → fraction [0..1] linearly, clamped. */
internal fun fractionFromDb(db: Float): Float {
    val f = (db + 60f) / 60f
    return f.coerceIn(0f, 1f)
}

/** Render dB with one decimal place. Forces Locale.US so the decimal separator is a dot, never a comma. */
internal fun formatDb(db: Float): String {
    val rounded = (db * 10f).toInt() / 10f
    return String.format(java.util.Locale.US, "%.1f dB", rounded)
}
