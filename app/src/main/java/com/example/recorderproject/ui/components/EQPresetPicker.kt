package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import com.example.recorderproject.model.EQPreset
import com.example.recorderproject.model.EQPresets
import com.example.recorderproject.model.PresetCategory
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQPresetPicker(onPick: (EQPreset) -> Unit, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf<PresetCategory?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = RecorderCharcoalCard,
    ) {
        Column(Modifier.padding(20.dp).height(420.dp)) {
            Text("PRESETS", color = RecorderYellow, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp())
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                CategoryChip("All", category == null) { category = null }
                for (c in PresetCategory.values()) {
                    CategoryChip(c.name.lowercase().replaceFirstChar { it.uppercase() }, category == c) { category = c }
                }
            }
            val filtered = EQPresets.ALL.filter { category == null || it.category == category }
            LazyColumn {
                items(filtered) { preset ->
                    PresetRow(preset, onPick = { onPick(preset); onDismiss() })
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) Color.White else RecorderBlueGrey,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) RecorderOrange else Color(0xFF0C0C10))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun PresetRow(preset: EQPreset, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0C0C10))
            .clickable(onClick = onPick)
            .padding(14.dp),
    ) {
        Text(preset.name, color = RecorderYellow, fontWeight = FontWeight.SemiBold)
        Text(preset.description, color = RecorderBlueGrey)
    }
}

// Small helper since "1.5.sp" requires the .sp extension import.
private fun Double.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
