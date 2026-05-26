package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.components.BrandWordmark
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenV2(
    theme: String,
    noiseReductionEnabled: Boolean,
    reduceMotion: Boolean,
    onChangeTheme: (String) -> Unit,
    onToggleNR: (Boolean) -> Unit,
    onToggleReduceMotion: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader("APPEARANCE")
            ThemeRow(theme, onChangeTheme)

            SectionHeader("RECORDING")
            ToggleRow(
                label = "Auto noise reduction",
                detail = "Runs noise gate after each take",
                value = noiseReductionEnabled,
                onChange = onToggleNR,
            )

            SectionHeader("ACCESSIBILITY")
            ToggleRow(
                label = "Reduce motion",
                detail = "Disables non-essential animations",
                value = reduceMotion,
                onChange = onToggleReduceMotion,
            )

            SectionHeader("ABOUT")
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RecorderCharcoalCard)
                    .padding(16.dp),
            ) {
                Column {
                    BrandWordmark(style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    ))
                    Text("Audio · field-grade · live EQ", color = RecorderBlueGrey, fontSize = 12.sp)
                    Text("Version 1.0 · Phase 1+", color = RecorderBlueGrey, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = RecorderBlueGrey,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun ToggleRow(label: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = RecorderBlueGrey, fontSize = 11.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RecorderOrange,
                uncheckedThumbColor = RecorderBlueGrey,
                uncheckedTrackColor = Color(0xFF0C0C10),
            ),
        )
    }
}

@Composable
private fun ThemeRow(current: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Theme", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF0C0C10)).padding(3.dp),
        ) {
            for (t in listOf("MEATrec", "Light", "System")) {
                val active = t == current
                Text(
                    t,
                    color = if (active) Color.White else RecorderBlueGrey,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (active) RecorderOrange else Color.Transparent)
                        .clickable { onChange(t) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
