package com.example.recorderproject.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.model.EQViewMode

@Composable
fun EQCurveView(
    chain: EQChain,
    spectrum: StaticSpectrum?,
    mode: EQEditMode,
    viewMode: EQViewMode,
    selectedBandId: Int?,
    sampleRate: Float,
    onHandleDrag: (EQBand, Float, Float) -> Unit,
    onHandleTap: (EQBand) -> Unit,
    onTapEmpty: (Float) -> Unit,
    onAcceptSuggestion: (EQBand) -> Unit,
    onFreeformDraw: (FloatArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = viewMode,
        transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
        label = "viewMode",
        modifier = modifier,
    ) { vm ->
        when (vm) {
            EQViewMode.TWO_D -> EQCurveView2D(
                chain = chain,
                spectrum = spectrum,
                mode = mode,
                selectedBandId = selectedBandId,
                sampleRate = sampleRate,
                onHandleDrag = onHandleDrag,
                onHandleTap = onHandleTap,
                onTapEmpty = onTapEmpty,
                onAcceptSuggestion = onAcceptSuggestion,
                onFreeformDraw = onFreeformDraw,
            )
            EQViewMode.THREE_D -> EQCurveView3D(
                chain = chain,
                historicalSlices = listOfNotNull(spectrum),
                sampleRate = sampleRate,
            )
        }
    }
}
