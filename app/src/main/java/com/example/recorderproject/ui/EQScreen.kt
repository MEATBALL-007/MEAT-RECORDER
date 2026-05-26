package com.example.recorderproject.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import com.example.recorderproject.ui.components.BrandWordmark
import com.example.recorderproject.ui.components.SaveCustomPresetDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.ui.components.ApplyButton
import com.example.recorderproject.ui.components.EQBandSheet
import com.example.recorderproject.ui.components.EQCurveView
import com.example.recorderproject.ui.components.EQPresetPicker
import com.example.recorderproject.ui.components.NoiseCutPanel
import com.example.recorderproject.ui.components.EQBandReadout
import com.example.recorderproject.ui.components.SaveModeSelector
import com.example.recorderproject.ui.components.ViewModeToggle
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQScreen(viewModel: RecorderViewModel, onBack: () -> Unit) {
    val chain by viewModel.currentEQChain.collectAsStateWithLifecycle()
    val mode by viewModel.eqMode.collectAsStateWithLifecycle()
    val viewMode by viewModel.eqViewMode.collectAsStateWithLifecycle()
    val selectedBandId by viewModel.eqSelectedBandId.collectAsStateWithLifecycle()
    val sourceFile by viewModel.eqSourceFile.collectAsStateWithLifecycle()
    val spectrum by viewModel.eqSourceSpectrum.collectAsStateWithLifecycle()
    val saveMode by viewModel.eqApplySaveMode.collectAsStateWithLifecycle()
    val renderProgress by viewModel.eqRenderProgress.collectAsStateWithLifecycle()
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()

    var bandSheetBand by remember { mutableStateOf<EQBand?>(null) }
    var presetPickerOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var saveDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BrandWordmark(style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp,
                            ))
                            Text("EQ", color = RecorderYellow, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        sourceFile?.let {
                            Text(it.name, color = RecorderBlueGrey, fontWeight = FontWeight.Normal,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEQClose(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEQToggleBypass() }) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Bypass",
                            tint = if (chain.bypassed) RecorderOrange else RecorderBlueGrey,
                        )
                    }
                    IconButton(onClick = { viewModel.onEQToggleGainCompensation() }) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = "Gain compensation",
                            tint = if (chain.gainCompensation) RecorderYellow else RecorderBlueGrey,
                        )
                    }
                    IconButton(onClick = { viewModel.onEQUndo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = RecorderBlueGrey)
                    }
                    IconButton(onClick = { viewModel.onEQRedo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = RecorderBlueGrey)
                    }
                    IconButton(onClick = { viewModel.onEQABToggle() }) {
                        Icon(Icons.Default.Compare, contentDescription = "A/B", tint = RecorderBlueGrey)
                    }
                    IconButton(onClick = { viewModel.onEQResetAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = RecorderBlueGrey)
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = RecorderBlueGrey)
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save as preset…") },
                                onClick = { overflowOpen = false; saveDialogOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Random preset") },
                                onClick = { overflowOpen = false; viewModel.onEQRandomPreset() },
                            )
                            DropdownMenuItem(
                                text = { Text("Export curve as PNG") },
                                onClick = { overflowOpen = false; viewModel.onEQExportCurvePng() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Mode segmented + view-mode toggle
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeSegmented(mode, viewModel::onEQModeToggle, Modifier.weight(1f))
                ViewModeToggle(viewMode, viewModel::onEQViewModeToggle)
            }

            // Curve view with floating active-band readout
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                val selectedBand = chain.bands.firstOrNull { it.id == selectedBandId && it.enabled }
                EQBandReadout(
                    band = selectedBand,
                    modifier = Modifier
                        .padding(8.dp)
                        .zIndex(2f)
                )
                EQCurveView(
                    chain = chain,
                    spectrum = spectrum,
                    mode = mode,
                    viewMode = viewMode,
                    selectedBandId = selectedBandId,
                    sampleRate = sampleRate.toFloat(),
                    onHandleDrag = { band, freq, gain ->
                        viewModel.onEQBandChanged(band.copy(frequencyHz = freq, gainDb = gain))
                    },
                    onHandleTap = { band ->
                        viewModel.onEQSelectBand(band.id)
                        bandSheetBand = band
                    },
                    onTapEmpty = { freq -> viewModel.onEQTapNotch(freq) },
                    onAcceptSuggestion = viewModel::onEQAcceptSuggestion,
                    onFreeformDraw = viewModel::onEQDrawCurve,
                )
            }

            // Mode-dependent content
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInHorizontally(tween(240))) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(200)))
                },
                label = "modeContent",
            ) { m ->
                when (m) {
                    EQEditMode.PARAMETRIC -> Column {
                        Text("BANDS", color = RecorderBlueGrey, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        BandChipsStrip(chain.bands, selectedBandId, onChipTap = {
                            viewModel.onEQSelectBand(it.id); bandSheetBand = it
                        })
                    }
                    EQEditMode.NOISE_CUT -> NoiseCutPanel(
                        chain = chain,
                        onAnalyze = viewModel::onEQNoiseAutoDetect,
                        onHumDetect50 = { viewModel.onEQHumDetect(50f) },
                        onHumDetect60 = { viewModel.onEQHumDetect(60f) },
                        onAccept = viewModel::onEQAcceptSuggestion,
                        onReject = viewModel::onEQRejectSuggestion,
                    )
                }
            }

            SaveModeSelector(saveMode, viewModel::onEQSaveModeChange)

            // Preset bar + Apply
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RecorderCharcoalCard)
                        .clickable { presetPickerOpen = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("PRESET", color = RecorderBlueGrey, fontWeight = FontWeight.Normal)
                    Text("Browse →", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
                }
            }
            ApplyButton(saveMode, renderProgress, onApply = viewModel::onEQApply)
        }
    }

    bandSheetBand?.let { band ->
        EQBandSheet(
            band = band,
            onChange = { updated -> viewModel.onEQBandChanged(updated); bandSheetBand = updated },
            onDismiss = { bandSheetBand = null; viewModel.onEQSelectBand(null) },
        )
    }

    if (presetPickerOpen) {
        EQPresetPicker(
            onPick = { preset -> viewModel.onEQPresetSelected(preset); presetPickerOpen = false },
            onDismiss = { presetPickerOpen = false },
        )
    }

    if (saveDialogOpen) {
        SaveCustomPresetDialog(
            onConfirm = { viewModel.onEQSaveAsCustomPreset(it) },
            onDismiss = { saveDialogOpen = false },
        )
    }
}

@Composable
private fun ModeSegmented(current: EQEditMode, onChange: (EQEditMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF0C0C10))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (m in EQEditMode.values()) {
            val active = m == current
            Text(
                text = if (m == EQEditMode.PARAMETRIC) "Parametric" else "Noise Cut",
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .clickable { onChange(m) }
                    .padding(vertical = 4.dp),
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BandChipsStrip(bands: List<EQBand>, selectedId: Int?, onChipTap: (EQBand) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        items(bands) { band ->
            val active = band.enabled
            val selected = band.id == selectedId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) RecorderYellow else Color(0xFF1F1F23))
                    .clickable { onChipTap(band) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = band.id.toString(),
                    color = if (active) Color(0xFF0C0C10) else RecorderBlueGrey,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}
