package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@Composable
fun NoiseCutPanel(
    chain: EQChain,
    onAnalyze: () -> Unit,
    onHumDetect50: () -> Unit,
    onHumDetect60: () -> Unit,
    onAccept: (EQBand) -> Unit,
    onReject: (EQBand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAnalyze,
                colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
                modifier = Modifier.weight(1.2f),
            ) { Text("Analyze", fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onHumDetect50,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0C0C10)),
                modifier = Modifier.weight(1f),
            ) { Text("Hum 50", color = RecorderYellow) }
            Button(
                onClick = onHumDetect60,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0C0C10)),
                modifier = Modifier.weight(1f),
            ) { Text("Hum 60", color = RecorderYellow) }
        }
        Text(
            text = "Drag on the curve to draw a noise-cut shape.",
            color = RecorderBlueGrey,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (chain.noiseCutSuggestions.isEmpty()) {
            Text(
                "Tap Analyze to scan for noise peaks, or drag a curve directly on the graph.",
                color = RecorderBlueGrey,
            )
        } else {
            Text(
                "Suggestions (${chain.noiseCutSuggestions.size}):",
                color = RecorderYellow,
                fontWeight = FontWeight.SemiBold,
            )
            for (band in chain.noiseCutSuggestions) {
                SuggestionRow(band, onAccept = { onAccept(band) }, onReject = { onReject(band) })
            }
        }
    }
}

@Composable
private fun SuggestionRow(band: EQBand, onAccept: () -> Unit, onReject: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0C0C10))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Notch @ ${formatHz(band.frequencyHz)}  ·  Q ${String.format("%.1f", band.q)}",
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Reject",
                color = RecorderBlueGrey,
                modifier = Modifier.clickable { onReject() },
            )
            Text(
                "Accept",
                color = RecorderOrange,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onAccept() },
            )
        }
    }
}

private fun formatHz(f: Float): String = when {
    f >= 1000f -> String.format("%.1f kHz", f / 1000f)
    else -> String.format("%.0f Hz", f)
}
