package com.example.recorderproject.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Pitch-shift dialog — port-back of old MEATrec pitch shift UI.
 *
 * Slider lets the user pick a shift in semitones [-12..+12]. On Apply, the
 * recording is run through [com.example.recorderproject.audio.PitchShifter] and
 * a new file is saved alongside the original.
 */
@Composable
fun PitchShiftDialog(
    file: RecordFile,
    onApply: (semitones: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var semitones by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RecorderCharcoalCard,
        titleContentColor = RecorderYellow,
        textContentColor = Color.White,
        title = { Text("Pitch shift") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, color = RecorderBlueGrey, fontSize = 11.sp)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${if (semitones >= 0) "+" else ""}${"%.1f".format(semitones)} semitones",
                        style = LocalAppTypography.current.numericMedium,
                        color = RecorderOrange,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = describeShift(semitones),
                        color = RecorderBlueGrey,
                        fontSize = 11.sp,
                    )
                }
                Slider(
                    value = semitones,
                    onValueChange = { semitones = it },
                    valueRange = -12f..12f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = RecorderOrange,
                        activeTrackColor = RecorderOrange,
                        inactiveTrackColor = RecorderBlueGrey.copy(alpha = 0.4f),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(semitones); onDismiss() },
                enabled = semitones != 0f,
            ) {
                Text("Apply", color = RecorderOrange, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RecorderBlueGrey)
            }
        },
    )
}

private fun describeShift(semitones: Float): String {
    val s = semitones.toInt()
    return when {
        s == 0 -> "Unchanged"
        s == 12 -> "+ Octave"
        s == -12 -> "− Octave"
        s > 0 -> "+ ${s} semitone${if (s == 1) "" else "s"}"
        else -> "− ${-s} semitone${if (s == -1) "" else "s"}"
    }
}

