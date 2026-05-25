package com.example.recorderproject.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val KmuttDarkColors = darkColorScheme(
    primary = KmuttMaroon,
    onPrimary = OnSurfaceDark,
    secondary = KmuttGold,
    onSecondary = SurfaceDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceVariantDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark
)

private val KmuttLightColors = lightColorScheme(
    primary = KmuttMaroon,
    secondary = KmuttGold
)

@Composable
fun RecorderProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) KmuttDarkColors else KmuttLightColors
    MaterialTheme(colorScheme = colors, content = content)
}
