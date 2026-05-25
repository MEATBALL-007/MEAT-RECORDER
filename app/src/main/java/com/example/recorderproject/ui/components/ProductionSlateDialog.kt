package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/** Phase 4 — Production slate. Captures scene, take, roll, camera, notes; saved with the next take. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionSlateDialog(
    initialScene: String,
    initialTake: String,
    initialRoll: String,
    initialCamera: String,
    initialNotes: String,
    onConfirm: (scene: String, take: String, roll: String, camera: String, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var scene by remember { mutableStateOf(initialScene) }
    var take by remember { mutableStateOf(initialTake) }
    var roll by remember { mutableStateOf(initialRoll) }
    var camera by remember { mutableStateOf(initialCamera) }
    var notes by remember { mutableStateOf(initialNotes) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RecorderCharcoalCard,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PRODUCTION SLATE", color = RecorderYellow, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Text("Will be attached as iXML metadata + included in the filename.",
                color = RecorderBlueGrey, fontSize = 11.sp,)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SlateField("Scene", scene, { scene = it }, modifier = Modifier.weight(1f))
                SlateField("Take", take, { take = it }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SlateField("Roll", roll, { roll = it }, modifier = Modifier.weight(1f))
                SlateField("Camera", camera, { camera = it }, modifier = Modifier.weight(1f))
            }
            SlateField("Notes", notes, { notes = it }, modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel", color = RecorderBlueGrey) }
                Button(
                    onClick = { onConfirm(scene, take, roll, camera, notes); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
                    modifier = Modifier.weight(1f),
                ) { Text("Set slate", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = RecorderBlueGrey) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = RecorderOrange,
            unfocusedBorderColor = RecorderBlueGrey.copy(alpha = 0.5f),
            cursorColor = RecorderYellow,
        ),
    )
}
