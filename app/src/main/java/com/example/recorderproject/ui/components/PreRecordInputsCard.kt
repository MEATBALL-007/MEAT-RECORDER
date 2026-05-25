package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Pre-record inputs card. File name + Scene + Notes — all editable before recording starts.
 * Once you tap Record, these values get baked into the take's filename and metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreRecordInputsCard(
    fileName: String,
    sceneName: String,
    notes: String,
    enabled: Boolean,
    onFileNameChange: (String) -> Unit,
    onSceneChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "NEW TAKE",
            color = RecorderBlueGrey,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(
                label = "File name",
                value = fileName,
                onChange = onFileNameChange,
                enabled = enabled,
                modifier = Modifier.weight(1.4f),
            )
            Field(
                label = "Scene",
                value = sceneName,
                onChange = onSceneChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        Field(
            label = "Notes (optional)",
            value = notes,
            onChange = onNotesChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = RecorderBlueGrey) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = RecorderBlueGrey,
            focusedBorderColor = RecorderOrange,
            unfocusedBorderColor = RecorderBlueGrey.copy(alpha = 0.5f),
            disabledBorderColor = RecorderBlueGrey.copy(alpha = 0.25f),
            cursorColor = RecorderYellow,
            focusedLabelColor = RecorderYellow,
        ),
    )
}
