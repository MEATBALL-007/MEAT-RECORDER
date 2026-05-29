package com.example.recorderproject.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecorderMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mode selector — 2-column grid (rebuilt per user feedback: the fan animation
 * stuttered and overlapped labels). Each row holds 2 cards. Top-right has a
 * [+] action that lets the user save a new custom preset.
 *
 *  ┌─────────────────────────────────────┐
 *  │      MEAT REC                  [+]  │
 *  │      SELECT RECORDING MODE          │
 *  │                                     │
 *  │  ┌─────────┐   ┌─────────┐         │
 *  │  │  FILM   │   │INTERVIEW│         │
 *  │  └─────────┘   └─────────┘         │
 *  │  ┌─────────┐   ┌─────────┐         │
 *  │  │ MUSIC   │   │AMBIENCE │         │
 *  │  └─────────┘   └─────────┘         │
 *  │  ┌─────────┐                       │
 *  │  │ CUSTOM  │                       │
 *  │  └─────────┘                       │
 *  │                                     │
 *  │              SKIP →                 │
 *  └─────────────────────────────────────┘
 */
@Composable
fun MeatRecModeSelector(
    onSelectMode: (RecorderMode) -> Unit,
    onSkip: () -> Unit,
    onCreatePreset: (name: String) -> Unit = {},
) {
    var addOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        // Header row: wordmark on left, [+] on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "MEAT",
                        color = Color(0xFFFA4616),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "REC",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
                Text(
                    "SELECT RECORDING MODE",
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            PlusButton(onClick = { addOpen = true })
        }

        Box(modifier = Modifier.height(28.dp))

        // 2-column grid (no rotation / overlap — just clean cards)
        val modes = RecorderMode.entries
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            modes.chunked(2).forEach { rowModes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowModes.forEach { mode ->
                        ModeGridCard(
                            mode = mode,
                            onClick = { onSelectMode(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowModes.size == 1) Box(Modifier.weight(1f))
                }
            }
        }

        Box(modifier = Modifier.height(24.dp))

        Text(
            "SKIP  →",
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 13.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onSkip)
                .padding(12.dp),
        )
    }

    if (addOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            containerColor = Color(0xFF161616),
            titleContentColor = Color(0xFFFFC72C),
            textContentColor = Color.White,
            title = { Text("New custom preset") },
            text = {
                Column {
                    Text(
                        "Saves a copy of the current Custom mode under this name.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1F1F1F))
                            .border(1.dp, Color(0xFFFA4616).copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color(0xFFFA4616)),
                            decorationBox = { inner ->
                                if (name.isEmpty()) {
                                    Text("e.g. Voice Memo · Field · …",
                                        color = Color.White.copy(alpha = 0.40f), fontSize = 16.sp)
                                }
                                inner()
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreatePreset(name.trim())
                            addOpen = false
                        }
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Save", color = Color(0xFFFA4616), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { addOpen = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
        )
    }
}

@Composable
private fun PlusButton(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "plusPress",
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFA4616), Color(0xFFE13606)),
                ),
            )
            .clickable {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Create custom preset",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(140)
            pressed = false
        }
    }
}

@Composable
private fun ModeGridCard(
    mode: RecorderMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "modePress",
    )

    Column(
        modifier = modifier
            .scale(pressScale)
            .aspectRatio(0.85f) // slightly tall
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        mode.accent.copy(alpha = 0.35f),
                        mode.accent.copy(alpha = 0.08f),
                        Color(0xFF0F0F0F),
                    ),
                ),
            )
            .border(
                1.dp,
                mode.accent.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp),
            )
            .clickable {
                pressed = true
                onClick()
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Center icon
        Box(
            modifier = Modifier
                .padding(top = 20.dp)
                .size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            ModeIcon(mode = mode, size = 56.dp)
        }
        // Mode name
        Text(
            mode.displayName,
            color = mode.accent,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // Short help
        Text(
            mode.shortHelp.split("·").firstOrNull()?.trim() ?: "",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
        )
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(150)
            pressed = false
        }
    }
}

@Composable
private fun ModeIcon(mode: RecorderMode, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val tint = mode.accent
        val sw = 2.5f * density

        when (mode) {
            RecorderMode.FILM -> {
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.50f),
                    size = Size(w * 0.76f, h * 0.40f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.20f),
                    size = Size(w * 0.76f, h * 0.18f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                for (k in 0..2) {
                    val x = w * (0.20f + k * 0.22f)
                    drawLine(
                        tint,
                        Offset(x, h * 0.20f),
                        Offset(x + w * 0.10f, h * 0.38f),
                        strokeWidth = sw, cap = StrokeCap.Round,
                    )
                }
            }
            RecorderMode.INTERVIEW -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.35f, h * 0.15f),
                    size = Size(w * 0.30f, h * 0.45f),
                    cornerRadius = CornerRadius(w * 0.15f, w * 0.15f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                drawArc(
                    color = tint,
                    startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.25f, h * 0.35f),
                    size = Size(w * 0.50f, w * 0.50f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                drawLine(tint, Offset(w * 0.5f, h * 0.70f), Offset(w * 0.5f, h * 0.88f),
                    strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.88f), Offset(w * 0.70f, h * 0.88f),
                    strokeWidth = sw, cap = StrokeCap.Round)
            }
            RecorderMode.MUSIC -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.13f,
                    center = Offset(w * 0.32f, h * 0.72f),
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.45f, h * 0.72f),
                    end = Offset(w * 0.45f, h * 0.18f),
                    strokeWidth = sw * 1.5f, cap = StrokeCap.Round,
                )
                val flag = Path().apply {
                    moveTo(w * 0.45f, h * 0.18f)
                    cubicTo(
                        w * 0.78f, h * 0.18f,
                        w * 0.80f, h * 0.40f,
                        w * 0.62f, h * 0.48f,
                    )
                }
                drawPath(flag, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
            RecorderMode.AMBIENCE -> {
                val leaf = Path().apply {
                    moveTo(w * 0.5f, h * 0.10f)
                    cubicTo(
                        w * 0.95f, h * 0.30f,
                        w * 0.85f, h * 0.85f,
                        w * 0.50f, h * 0.90f,
                    )
                    cubicTo(
                        w * 0.15f, h * 0.85f,
                        w * 0.05f, h * 0.30f,
                        w * 0.50f, h * 0.10f,
                    )
                    close()
                }
                drawPath(leaf, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
                drawLine(tint,
                    start = Offset(w * 0.50f, h * 0.15f),
                    end = Offset(w * 0.50f, h * 0.88f),
                    strokeWidth = sw, cap = StrokeCap.Round)
            }
            RecorderMode.CUSTOM -> {
                val cx = w / 2; val cy = h / 2
                val rOuter = w * 0.32f; val rInner = w * 0.12f
                val teeth = 8
                for (i in 0 until teeth) {
                    val a = i * (2 * Math.PI / teeth)
                    val x1 = cx + cos(a).toFloat() * rOuter * 0.92f
                    val y1 = cy + sin(a).toFloat() * rOuter * 0.92f
                    val x2 = cx + cos(a).toFloat() * rOuter * 1.40f
                    val y2 = cy + sin(a).toFloat() * rOuter * 1.40f
                    drawLine(tint, Offset(x1, y1), Offset(x2, y2),
                        strokeWidth = sw, cap = StrokeCap.Round)
                }
                drawCircle(tint, rOuter, Offset(cx, cy),
                    style = Stroke(width = sw, cap = StrokeCap.Round))
                drawCircle(tint, rInner, Offset(cx, cy),
                    style = Stroke(width = sw, cap = StrokeCap.Round))
            }
        }
    }
}
