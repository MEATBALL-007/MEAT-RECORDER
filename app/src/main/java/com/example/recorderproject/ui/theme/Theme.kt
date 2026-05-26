package com.example.recorderproject.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Build a darkColorScheme for the given [AppTheme].
 *
 * Brand accents (primary = Orange, secondary = Yellow, tertiary = BlueGrey) stay
 * constant across all 10 themes — only the dark surface ramp shifts per palette.
 */
private fun darkSchemeFor(theme: AppTheme) = darkColorScheme(
    primary              = RecorderOrange,
    onPrimary            = theme.background,
    primaryContainer     = Color(0xFF7A1F00),
    onPrimaryContainer   = Color(0xFFFFD9CC),
    secondary            = RecorderYellow,
    onSecondary          = theme.background,
    secondaryContainer   = Color(0xFF665000),
    onSecondaryContainer = Color(0xFFFFF1C2),
    tertiary             = RecorderBlueGrey,
    onTertiary           = theme.background,
    background           = theme.background,
    onBackground         = OnSurfaceDark,
    surface              = theme.surface,
    onSurface            = OnSurfaceDark,
    surfaceVariant       = theme.surfaceElevated,
    onSurfaceVariant     = OnSurfaceVariantDark,
    // The 5-tier surface ramp interpolates from background → surfaceElevated.
    surfaceContainerLowest  = theme.background,
    surfaceContainerLow     = theme.surface,
    surfaceContainer        = theme.surfaceElevated,
    surfaceContainerHigh    = theme.surfaceElevated,
    surfaceContainerHighest = theme.surfaceElevated,
    error                = SemanticError,
    onError              = theme.background,
    outline              = RecorderBlueGrey,
    outlineVariant       = Color(0xFF3A3D42),
)

private val RecorderLightColors = lightColorScheme(
    primary   = RecorderOrange,
    secondary = RecorderYellow,
    tertiary  = RecorderBlueGrey,
    error     = SemanticError,
    // Light scheme is functional but not the polish target this round.
)

/**
 * App theme wrapper. Pass an [AppTheme] (default MIDNIGHT) — the background /
 * surface colors are interpolated with animateColorAsState when the user picks
 * a different theme in Settings, giving a smooth ~280ms color fade.
 *
 * Pass [darkTheme] = false to opt into the light scheme; theme picker only
 * applies in dark mode (light mode is a single neutral baseline).
 */
@Composable
fun RecorderProjectTheme(
    appTheme: AppTheme = AppTheme.Default,
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Smoothly fade between palettes when the user switches themes.
    val durationMs = if (reduceMotion) 0 else 280
    val animBackground by animateColorAsState(appTheme.background, tween(durationMs), label = "themeBg")
    val animSurface by animateColorAsState(appTheme.surface, tween(durationMs), label = "themeSurface")
    val animSurfaceElev by animateColorAsState(appTheme.surfaceElevated, tween(durationMs), label = "themeSurfaceElev")

    val darkScheme = darkSchemeFor(appTheme).copy(
        background = animBackground,
        surface = animSurface,
        surfaceVariant = animSurfaceElev,
        surfaceContainerLowest = animBackground,
        surfaceContainerLow = animSurface,
        surfaceContainer = animSurfaceElev,
        surfaceContainerHigh = animSurfaceElev,
        surfaceContainerHighest = animSurfaceElev,
    )

    val colors = if (darkTheme) darkScheme else RecorderLightColors
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalAppTypography provides RecorderAppTypography,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography  = RecorderTypography,
            shapes      = RecorderShapes,
            content     = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Design-system @Preview — render this in Android Studio's preview pane to
// visually verify the swatch grid, type ramp, spacing ruler, and shape samples.
// Not used at runtime.
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF0C0C10, widthDp = 380, heightDp = 1100)
@Composable
private fun DesignSystemPreview() {
    RecorderProjectTheme {
        Column(
            Modifier
                .background(RecorderCharcoal)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Swatch grid
            Text("Swatches", style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    "primary"   to MaterialTheme.colorScheme.primary,
                    "secondary" to MaterialTheme.colorScheme.secondary,
                    "tertiary"  to MaterialTheme.colorScheme.tertiary,
                    "error"     to MaterialTheme.colorScheme.error,
                    "success"   to SemanticSuccess,
                    "info"      to SemanticInfo,
                ).forEach { (label, color) ->
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(color),
                        )
                        Text(label, style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
                    }
                }
            }

            // Surface tier ramp
            Text("Surface tiers", style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    SurfaceContainerLowest, SurfaceContainerLow, SurfaceContainer,
                    SurfaceContainerHigh, SurfaceContainerHighest,
                ).forEach {
                    Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(it))
                }
            }

            // Type ramp
            Text("Type ramp", style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark)
            Text("displayMedium 45/52", style = MaterialTheme.typography.displayMedium, color = OnSurfaceDark)
            Text("headlineSmall 24/32", style = MaterialTheme.typography.headlineSmall, color = OnSurfaceDark)
            Text("titleLarge 22/28",    style = MaterialTheme.typography.titleLarge,    color = OnSurfaceDark)
            Text("bodyLarge 16/24",     style = MaterialTheme.typography.bodyLarge,     color = OnSurfaceDark)
            Text("labelSmall 11/16",    style = MaterialTheme.typography.labelSmall,    color = OnSurfaceDark)
            Text("LABEL TINY (custom)", style = LocalAppTypography.current.labelTiny,   color = OnSurfaceDark)
            Text("88:23:04",            style = LocalAppTypography.current.numericLarge, color = RecorderOrange)
            Text("-12.3 dB",            style = LocalAppTypography.current.numericMedium, color = RecorderYellow)

            // Spacing ruler
            Text("Spacing tokens", style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark)
            listOf(
                "xxs (2)" to Spacing.xxs, "xs (4)" to Spacing.xs, "sm (8)" to Spacing.sm,
                "md (12)" to Spacing.md, "lg (16)" to Spacing.lg, "xl (24)" to Spacing.xl,
                "xxl (32)" to Spacing.xxl, "xxxl (48)" to Spacing.xxxl,
            ).forEach { (label, dp) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(width = dp, height = 12.dp).background(RecorderOrange))
                    Spacer(Modifier.size(Spacing.sm))
                    Text(label, style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
                }
            }

            // Shape samples
            Text("Shapes", style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    "xs" to MaterialTheme.shapes.extraSmall,
                    "sm" to MaterialTheme.shapes.small,
                    "md" to MaterialTheme.shapes.medium,
                    "lg" to MaterialTheme.shapes.large,
                    "xl" to MaterialTheme.shapes.extraLarge,
                ).forEach { (label, shape) ->
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(56.dp).clip(shape)
                                .background(SurfaceContainerHigh)
                                .border(1.dp, RecorderBlueGrey, shape),
                        )
                        Text(label, style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
                    }
                }
            }
        }
    }
}
