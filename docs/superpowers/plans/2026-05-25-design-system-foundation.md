# Design System Foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the design-token foundation (color, typography, shape, spacing, motion) for the MEAT REC app: fix the `Theme.kt` palette mismatch, add tokens that future polish work compounds on, ship the two-color "MEAT REC" wordmark, and migrate one screen as proof of use — without redesigning any layout.

**Architecture:** Pure-Compose theme system. New files under `ui/theme/` (Type, Shape, Spacing, Motion) + new `BrandWordmark.kt` composable under `ui/components/`. `Theme.kt` rewritten to wire Material3 `colorScheme` / `typography` / `shapes` from brand palette and expose `LocalReduceMotion` + `LocalAppTypography`. No new gradle dependencies; Plus Jakarta Sans + JetBrains Mono added as `res/font/` resources.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2024.02.00), Material 3, JVM 17. minSdk 24, compileSdk/targetSdk 34, AGP 9.1.1, Gradle 9.3.1. Project root `/Users/meatball_mac/RECORDER_PROJECT/`.

**Spec:** `/Users/meatball_mac/RECORDER_PROJECT/docs/superpowers/specs/2026-05-25-design-system-foundation-design.md`

---

## Pre-task setup

Run once before starting Task 1.

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
git status
# Expected: working tree clean (or only ignored files). Bail if there are unstaged changes you didn't make.

git log --oneline -3
# Expected: top commit is 937e3c8 "docs(design): MEAT REC two-color wordmark..."

./gradlew assembleDebug
# Expected: BUILD SUCCESSFUL. If this fails, do not start; the rest of the plan assumes a green baseline.

./gradlew test
# Expected: BUILD SUCCESSFUL, all existing unit tests pass.
```

After each task, run `./gradlew assembleDebug` and `git commit`. If a task is large enough to touch tests, also run `./gradlew test`.

---

## Task 1: Add font resources

**Goal:** Place Plus Jakarta Sans (Regular / SemiBold / Bold) and JetBrains Mono (Regular / Medium) TTF files into the Android `res/font/` directory.

**Files:**
- Create: `app/src/main/res/font/plus_jakarta_sans_regular.ttf`
- Create: `app/src/main/res/font/plus_jakarta_sans_semibold.ttf`
- Create: `app/src/main/res/font/plus_jakarta_sans_bold.ttf`
- Create: `app/src/main/res/font/jetbrains_mono_regular.ttf`
- Create: `app/src/main/res/font/jetbrains_mono_medium.ttf`

- [ ] **Step 1: Create the `res/font/` directory**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
mkdir -p app/src/main/res/font
```

- [ ] **Step 2: Download the five TTFs**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT/app/src/main/res/font

curl -fSL -o plus_jakarta_sans_regular.ttf \
  https://raw.githubusercontent.com/itfoundry/Plus-Jakarta-Sans/master/fonts/ttf/PlusJakartaSans-Regular.ttf
curl -fSL -o plus_jakarta_sans_semibold.ttf \
  https://raw.githubusercontent.com/itfoundry/Plus-Jakarta-Sans/master/fonts/ttf/PlusJakartaSans-SemiBold.ttf
curl -fSL -o plus_jakarta_sans_bold.ttf \
  https://raw.githubusercontent.com/itfoundry/Plus-Jakarta-Sans/master/fonts/ttf/PlusJakartaSans-Bold.ttf
curl -fSL -o jetbrains_mono_regular.ttf \
  https://raw.githubusercontent.com/JetBrains/JetBrainsMono/master/fonts/ttf/JetBrainsMono-Regular.ttf
curl -fSL -o jetbrains_mono_medium.ttf \
  https://raw.githubusercontent.com/JetBrains/JetBrainsMono/master/fonts/ttf/JetBrainsMono-Medium.ttf
```

Both fonts ship under the SIL Open Font License (OFL). No attribution required in-app; license notice is added to the docs in Task 14.

- [ ] **Step 3: Verify all five files are valid TTFs and non-empty**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT/app/src/main/res/font
ls -l *.ttf
file *.ttf
```

Expected output for `file *.ttf`: every line ends with `TrueType Font data ...` (or similar). Sizes should be roughly 80–250 KB each. If any line says `HTML document` or `empty`, the URL is wrong — fix and re-download before continuing.

- [ ] **Step 4: Build to confirm Android registers them**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. The aapt2 step will fail if any font file name contains uppercase, hyphens, or invalid characters — the names above are already lowercase/underscore.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/font
git commit -m "feat(design): add Plus Jakarta Sans + JetBrains Mono TTF resources"
```

---

## Task 2: Color tokens — semantic, surface tier, spectrum

**Goal:** Add semantic colors (success/warning/error/info), Material3 surface elevation tiers, and spectrum gradient stops to `Color.kt`. Keep all existing constants.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/theme/Color.kt`

- [ ] **Step 1: Append the new tokens at the end of `Color.kt`, before the `AppColors` data class**

Open the file. After the line `val MeatRed = Color(0xFFC0392B)` and before `val TextPrimary = OnSurfaceDark` (around line 30), insert:

```kotlin
// Semantic — for status indicators, badges, and toast accents
val SemanticSuccess = Color(0xFF3DDC97)   // mint — recording saved, render complete
val SemanticWarning = Color(0xFFFFC72C)   // = RecorderYellow — clip, caution
val SemanticError   = Color(0xFFFF5A5F)   // recording failed, permission denied
val SemanticInfo    = Color(0xFF7AB7FF)   // tips, neutral notifications

// Surface elevation tiers (Material3 surfaceContainer pattern, charcoal-tinted)
val SurfaceContainerLowest  = Color(0xFF09090C)
val SurfaceContainerLow     = Color(0xFF111114)
val SurfaceContainer        = Color(0xFF161618)   // matches RecorderCharcoalCard
val SurfaceContainerHigh    = Color(0xFF1C1C20)
val SurfaceContainerHighest = Color(0xFF222227)

// Spectrum gradient stops — for future data viz (meters, spectrograms, gain readouts)
val SpectrumLow  = Color(0xFF1E3A5F)   // deep blue
val SpectrumMid  = RecorderYellow
val SpectrumHigh = RecorderOrange
val SpectrumClip = SemanticError
```

- [ ] **Step 2: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/theme/Color.kt
git commit -m "feat(design): add semantic + surface-tier + spectrum color tokens"
```

---

## Task 3: Spacing tokens

**Goal:** Create `Spacing.kt` with a 4-pt-grid object of `Dp` constants. Used directly (no theme indirection).

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/theme/Spacing.kt`

- [ ] **Step 1: Create `Spacing.kt`**

Create the file with this content:

```kotlin
package com.example.recorderproject.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens — 4-pt grid (Material default), with one 2-dp sub-grid token for tight gaps.
 *
 * Import directly: `import com.example.recorderproject.ui.theme.Spacing`
 * Use: `Modifier.padding(Spacing.md)`, `Arrangement.spacedBy(Spacing.sm)`, etc.
 *
 * No theme indirection — these are constants, not theme-dependent.
 */
object Spacing {
    val xxs = 2.dp     // sub-grid (used sparingly; e.g., 2-dp gap between paired icons)
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp    // default card padding
    val lg  = 16.dp    // screen edge padding
    val xl  = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}
```

- [ ] **Step 2: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/theme/Spacing.kt
git commit -m "feat(design): add Spacing token object (4-pt grid)"
```

---

## Task 4: Shape tokens

**Goal:** Create `Shape.kt` exporting a `Shapes` instance that overrides Material3 defaults with tighter radii (instrument feel).

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/theme/Shape.kt`

- [ ] **Step 1: Create `Shape.kt`**

```kotlin
package com.example.recorderproject.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape tokens — tighter than consumer Material default (16+ dp) to give
 * an instrument / pro-tool feel. Wired into MaterialTheme.shapes by Theme.kt.
 *
 * Existing screens that hardcode `RoundedCornerShape(12.dp)` keep working;
 * new and migrated screens read from `MaterialTheme.shapes.*`.
 */
val RecorderShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(6.dp),    // chips, small buttons
    medium     = RoundedCornerShape(8.dp),    // cards, panels
    large      = RoundedCornerShape(12.dp),   // dialogs, sheets
    extraLarge = RoundedCornerShape(20.dp),   // full-screen sheets, splash card
)
```

- [ ] **Step 2: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/theme/Shape.kt
git commit -m "feat(design): add Shape tokens (RecorderShapes)"
```

---

## Task 5: Typography tokens

**Goal:** Create `Type.kt` exposing the FontFamily declarations, a Material3 `Typography` instance, a custom `AppTypography` data class for non-Material styles (labelTiny, numericLarge/Medium/Small), and `LocalAppTypography` composition local.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/theme/Type.kt`

- [ ] **Step 1: Create `Type.kt`**

```kotlin
package com.example.recorderproject.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.recorderproject.R

/**
 * Typography tokens for MEAT REC.
 *
 * Two font families:
 *   - Plus Jakarta Sans (display / body) — geometric sans with Thai support
 *   - JetBrains Mono (numerics) — tabular figures for level/timecode readouts
 *
 * Use Material3 styles via `MaterialTheme.typography.*` and custom styles
 * via `LocalAppTypography.current.*`.
 */

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular,  FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold,     FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium,  FontWeight.Medium),
)

private fun jakarta(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private fun mono(
    weight: FontWeight,
    size: Int,
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = letterSpacing.sp,
)

val RecorderTypography = Typography(
    displayLarge   = jakarta(FontWeight.Bold,     57, 64, -0.5f),
    displayMedium  = jakarta(FontWeight.Bold,     45, 52,  0f),
    displaySmall   = jakarta(FontWeight.SemiBold, 36, 44,  0f),
    headlineLarge  = jakarta(FontWeight.SemiBold, 32, 40,  0f),
    headlineMedium = jakarta(FontWeight.SemiBold, 28, 36,  0f),
    headlineSmall  = jakarta(FontWeight.SemiBold, 24, 32,  0f),
    titleLarge     = jakarta(FontWeight.SemiBold, 22, 28,  0f),
    titleMedium    = jakarta(FontWeight.SemiBold, 16, 24,  0.15f),
    titleSmall     = jakarta(FontWeight.SemiBold, 14, 20,  0.1f),
    bodyLarge      = jakarta(FontWeight.Normal,   16, 24,  0.5f),
    bodyMedium     = jakarta(FontWeight.Normal,   14, 20,  0.25f),
    bodySmall      = jakarta(FontWeight.Normal,   12, 16,  0.4f),
    labelLarge     = jakarta(FontWeight.SemiBold, 14, 20,  0.1f),
    labelMedium    = jakarta(FontWeight.SemiBold, 12, 16,  0.5f),
    labelSmall     = jakarta(FontWeight.SemiBold, 11, 16,  0.5f),
)

/**
 * Custom typography styles outside the Material3 scale.
 * Access via `LocalAppTypography.current.*`.
 */
@Immutable
data class AppTypography(
    /** Caps section labels — "FILE", "RECORDINGS", "BIT DEPTH" */
    val labelTiny: TextStyle = jakarta(FontWeight.SemiBold, 10, 14, 1.5f),
    /** Hero numeric — timer, large take counter */
    val numericLarge: TextStyle = mono(FontWeight.Medium, 48, 0f),
    /** Level readout — dB, gain */
    val numericMedium: TextStyle = mono(FontWeight.Medium, 24, 0f),
    /** Inline numbers — Hz, ms, sample rate */
    val numericSmall: TextStyle = mono(FontWeight.Normal, 14, 0.5f),
)

val RecorderAppTypography = AppTypography()

val LocalAppTypography = staticCompositionLocalOf { AppTypography() }
```

- [ ] **Step 2: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. (The `R.font.*` references resolve after Task 1's font files are in place.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/theme/Type.kt
git commit -m "feat(design): add Typography (Plus Jakarta Sans + JetBrains Mono) + AppTypography"
```

---

## Task 6: Motion tokens

**Goal:** Create `Motion.kt` with `Duration` (ms constants), `Easing` (cubic-bezier curves), and a `LocalReduceMotion` composition local + `ReducedMotionProvider` helper.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/theme/Motion.kt`

- [ ] **Step 1: Create `Motion.kt`**

```kotlin
package com.example.recorderproject.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens for MEAT REC.
 *
 * Duration constants are in milliseconds — pass directly to `tween(Duration.medium)`,
 * `spring(...)`, `delay(Duration.fast.toLong())`, etc.
 *
 * Easing curves follow Material Motion guidance; emphasized curves are for
 * hero animations, standard curves for default UI motion.
 *
 * Reduce-motion: read `LocalReduceMotion.current` and substitute a snap or
 * shortened tween. The roadmap commits to honoring this on every animation
 * in a later motion-polish phase; this file just exposes the hook.
 */
object Duration {
    const val instant = 80     // press feedback, micro-touches
    const val fast    = 160    // toggles, small fades
    const val medium  = 280    // standard transitions
    const val slow    = 480    // hero animations, splash
    const val verySlow = 800   // ambient (breathing rings, pulse)
}

object Easing {
    val standard        = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val emphasized      = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val emphasizedAccel = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val emphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
}

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ReducedMotionProvider(reduce: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}
```

- [ ] **Step 2: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/theme/Motion.kt
git commit -m "feat(design): add Motion tokens (Duration, Easing) + LocalReduceMotion hook"
```

---

## Task 7: Rewrite `Theme.kt` to wire brand palette + tokens

**Goal:** Replace the legacy `KmuttDarkColors` / `KmuttLightColors` with brand-aligned schemes; wire `Typography`, `Shapes`, `LocalReduceMotion`, `LocalAppTypography`. Public `RecorderProjectTheme` signature gains a `reduceMotion: Boolean = false` parameter (default keeps existing callers compiling). Also add a `@Preview` swatch grid for visual verification.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/theme/Theme.kt` (full rewrite)

- [ ] **Step 1: Replace the entire contents of `Theme.kt`**

```kotlin
package com.example.recorderproject.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val RecorderDarkColors = darkColorScheme(
    primary              = RecorderOrange,
    onPrimary            = RecorderCharcoal,
    primaryContainer     = Color(0xFF7A1F00),
    onPrimaryContainer   = Color(0xFFFFD9CC),
    secondary            = RecorderYellow,
    onSecondary          = RecorderCharcoal,
    secondaryContainer   = Color(0xFF665000),
    onSecondaryContainer = Color(0xFFFFF1C2),
    tertiary             = RecorderBlueGrey,
    onTertiary           = RecorderCharcoal,
    background           = RecorderCharcoal,
    onBackground         = OnSurfaceDark,
    surface              = RecorderCharcoal,
    onSurface            = OnSurfaceDark,
    surfaceVariant       = SurfaceContainerHigh,
    onSurfaceVariant     = OnSurfaceVariantDark,
    surfaceContainerLowest  = SurfaceContainerLowest,
    surfaceContainerLow     = SurfaceContainerLow,
    surfaceContainer        = SurfaceContainer,
    surfaceContainerHigh    = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    error                = SemanticError,
    onError              = RecorderCharcoal,
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

@Composable
fun RecorderProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,   // caller (MainActivity) wires from SettingsDataStore once that toggle exists; default false until then
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) RecorderDarkColors else RecorderLightColors
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
```

- [ ] **Step 2: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. The legacy `KmuttDarkColors` / `KmuttLightColors` no longer exist as private vals; any external reference would fail compilation. Confirm nothing else imported them:

```bash
grep -rn "KmuttDarkColors\|KmuttLightColors" app/src
```

Expected: no output (they were private to `Theme.kt`).

- [ ] **Step 3: Run existing tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. The theme rewrite does not touch any audio/model code, so all existing tests should still pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/theme/Theme.kt
git commit -m "feat(design): rewrite Theme.kt to brand palette + wire typography/shapes/motion + @Preview swatches"
```

---

## Task 8: `BrandWordmark` composable

**Goal:** Create the two-color "MEAT REC" wordmark as a single composable, so every surface renders it identically.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/BrandWordmark.kt`

- [ ] **Step 1: Create `BrandWordmark.kt`**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import com.example.recorderproject.ui.theme.Spacing

/**
 * MEAT REC brand wordmark — two-color split.
 *
 *   MEAT (RecorderOrange)  REC (RecorderYellow)
 *
 * Bold, all-caps, 2.5sp letter-spacing, 8dp gap between words.
 * `style` defaults to titleLarge; pass `displayMedium` (or similar) for
 * splash / hero surfaces.
 *
 * Examples:
 *   BrandWordmark()                                              // TopBar size
 *   BrandWordmark(style = MaterialTheme.typography.displayMedium) // Splash size
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = brandWordmarkDefaultStyle(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("MEAT", style = style, color = RecorderOrange)
        Text("REC",  style = style, color = RecorderYellow)
    }
}

@Composable
@ReadOnlyComposable
private fun brandWordmarkDefaultStyle(): TextStyle =
    MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.5.sp,
    )
```

- [ ] **Step 2: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/BrandWordmark.kt
git commit -m "feat(design): BrandWordmark composable (MEAT=orange, REC=yellow)"
```

---

## Task 9: Rename `app_name` to `MEAT REC`

**Goal:** Change the launcher label / system-shown app name.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Replace `strings.xml` contents**

```xml
<resources>
    <string name="app_name">MEAT REC</string>
</resources>
```

- [ ] **Step 2: Build + install + visually confirm launcher shows "MEAT REC"**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. (Manual install/launch verification deferred to Task 15 final walk.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(design): rename app_name to MEAT REC"
```

---

## Task 10: Migrate `RecorderApp.kt` — TopBar wordmark + labelTiny + Spacing.md

**Goal:** Replace the inline `Text("MEATrec", ...)` in the TopBar with `BrandWordmark()`. Replace the three hardcoded `fontSize = 10.sp, letterSpacing = 1.5.sp` triplets with `LocalAppTypography.current.labelTiny`. Replace one `padding(14.dp)` magic value with `padding(Spacing.md)`. This is the proof-of-use migration the spec calls for.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt`

- [ ] **Step 1: Add the new imports**

Open `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt`. After the existing imports for `RecorderOrange` / `RecorderYellow` (around line 65), add:

```kotlin
import com.example.recorderproject.ui.components.BrandWordmark
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.Spacing
```

- [ ] **Step 2: Replace the TopBar title to use `BrandWordmark`**

Find lines 107–112 (the `title = { Row(...) { MeatrecMark(...); Text("MEATrec", ...) } }` block) and replace with:

```kotlin
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MeatrecMark(size = 32.dp)
                        BrandWordmark()
                    }
                },
```

- [ ] **Step 3: Replace the three "FILE" / "BIT DEPTH" / "RECORDINGS" caps labels with `labelTiny`**

Find line 164 (`Text("FILE", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp)`) and change to:

```kotlin
                    Text("FILE", style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
```

Find line 186 (`Text("BIT DEPTH", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp)`) and change to:

```kotlin
                        Text("BIT DEPTH", style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
```

Find line 281 (`Text("RECORDINGS", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)`) and change to (note: the existing `fontWeight = FontWeight.SemiBold` is already part of `labelTiny`):

```kotlin
                Text("RECORDINGS", style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
```

- [ ] **Step 4: Replace one magic `padding(14.dp)` with `padding(Spacing.md)` (= 12.dp)**

Find line 161 (`.padding(14.dp)` inside the status card `Box` modifier chain) and change to:

```kotlin
                    .padding(Spacing.md)
```

This is the intentional 2-dp tighten flagged in the spec.

- [ ] **Step 5: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt
git commit -m "feat(design): migrate RecorderApp TopBar to BrandWordmark + labelTiny + Spacing.md (proof-of-use)"
```

---

## Task 11: Migrate other display-wordmark sites

**Goal:** Replace the inline `Text("MEATrec", ...)` calls in `SplashScreen.kt`, `OnboardingOverlay.kt`, `SettingsScreenV2.kt`, and `EQScreen.kt` with `BrandWordmark`. The MeatrecMark icon-badge file (`MeatrecMark.kt`) is intentionally left unchanged per spec.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/SplashScreen.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/OnboardingOverlay.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/SettingsScreenV2.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/EQScreen.kt`

- [ ] **Step 1: `SplashScreen.kt` — replace the hero wordmark Text**

Find lines 90–98 in `SplashScreen.kt` (the `Box(modifier = Modifier.alpha(titleAlpha.value).padding(top = 4.dp)) { Text("MEATrec", ...) }`) and replace the inner `Text` with a `BrandWordmark`:

```kotlin
            Box(modifier = Modifier.alpha(titleAlpha.value).padding(top = 4.dp)) {
                BrandWordmark(style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                ))
            }
```

Add the needed imports near the top of the file (alongside the existing `RecorderYellow` import):

```kotlin
import com.example.recorderproject.ui.components.BrandWordmark
import androidx.compose.material3.MaterialTheme
```

(If `MaterialTheme` is already imported, skip its import line.)

- [ ] **Step 2: `OnboardingOverlay.kt` — replace wordmark Text**

Find line 72 (`Text("MEATrec", color = RecorderYellow, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp)`) and replace with:

```kotlin
                BrandWordmark(style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ))
```

Add imports:

```kotlin
import com.example.recorderproject.ui.components.BrandWordmark
import androidx.compose.material3.MaterialTheme
```

- [ ] **Step 3: `SettingsScreenV2.kt` — replace About-card wordmark**

Find line 94 (`Text("MEATrec", color = RecorderYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)`) and replace with:

```kotlin
                    BrandWordmark(style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    ))
```

Add imports:

```kotlin
import com.example.recorderproject.ui.components.BrandWordmark
import androidx.compose.material3.MaterialTheme
```

**Important:** Do NOT change the theme picker option string on line 155 (`for (t in listOf("MEATrec", "Light", "System"))`) — that is an internal theme identifier, not a display wordmark.

- [ ] **Step 4: `EQScreen.kt` — split "MEATrec EQ" into wordmark + suffix**

Find lines 93–98 (`title = { Column { Text("MEATrec EQ", ...) sourceFile?.let { Text(it.name, ...) } } }`) and replace with:

```kotlin
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
```

Add imports if missing:

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.recorderproject.ui.components.BrandWordmark
```

(The file already imports `MaterialTheme`; line 97 uses `androidx.compose.material3.MaterialTheme` fully qualified — your edit can switch to the imported short name, but doesn't have to.)

- [ ] **Step 5: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Confirm no remaining display-wordmark Text uses `"MEATrec"`**

```bash
grep -rn '"MEATrec"' app/src/main/java/com/example/recorderproject/ui/
```

Expected: only the line in `MeatrecMark.kt` (a docstring) and the theme-picker option in `SettingsScreenV2.kt:155` should remain. If any other display `Text("MEATrec", ...)` shows up, migrate it the same way.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/SplashScreen.kt \
        app/src/main/java/com/example/recorderproject/ui/OnboardingOverlay.kt \
        app/src/main/java/com/example/recorderproject/ui/SettingsScreenV2.kt \
        app/src/main/java/com/example/recorderproject/ui/EQScreen.kt
git commit -m "feat(design): migrate splash/onboarding/settings/EQ wordmarks to BrandWordmark"
```

---

## Task 12: Wire `reduceMotion` from `MainActivity` into `RecorderProjectTheme`

**Goal:** The `reduceMotion` state already exists in `MainActivity.onCreate` (line 63). Pass it to `RecorderProjectTheme` so `LocalReduceMotion` is populated app-wide.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt`

- [ ] **Step 1: Pass `reduceMotion` to `RecorderProjectTheme`**

Find line 58 (`RecorderProjectTheme {`) and change to:

```kotlin
            RecorderProjectTheme(reduceMotion = reduceMotion) {
```

The `reduceMotion` `var` declared on line 63 must be moved above the `RecorderProjectTheme` call so it's in scope. Reorder the block so the four `remember { ... }` declarations come **before** `RecorderProjectTheme`:

```kotlin
        setContent {
            var splashDone by remember { mutableStateOf(false) }
            var onboardingDone by remember { mutableStateOf(getPreferences(MODE_PRIVATE).getBoolean("onboarding_done", false)) }
            var settingsOpen by remember { mutableStateOf(false) }
            var theme by remember { mutableStateOf("MEATrec") }
            var reduceMotion by remember { mutableStateOf(false) }
            RecorderProjectTheme(reduceMotion = reduceMotion) {
                val noiseReductionEnabled by viewModel.noiseReductionEnabled.collectAsStateWithLifecycle()
                val eqOpen by viewModel.eqOpen.collectAsStateWithLifecycle()
                when {
                    // ... existing when-branches unchanged ...
                }
            }
        }
```

Leave the `when { ... }` block contents (lines 66–101 of the original) exactly as they were. Only the `remember` declarations move out, and `RecorderProjectTheme` gains the `reduceMotion =` parameter.

- [ ] **Step 2: Build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/MainActivity.kt
git commit -m "feat(design): wire reduceMotion state from MainActivity into RecorderProjectTheme"
```

---

## Task 13: Write `docs/superpowers/design-system.md`

**Goal:** Short usage reference for future PRs — token names, when to use which, one code example.

**Files:**
- Create: `docs/superpowers/design-system.md`

- [ ] **Step 1: Create `design-system.md`**

```markdown
# MEAT REC — Design System Reference

Quick lookup for design tokens. Spec lives in `specs/2026-05-25-design-system-foundation-design.md`; this file is the day-to-day cheatsheet.

## Brand

- **App name (display):** `MEAT REC` — never `MEATrec` in user-visible text. Use the `BrandWordmark` composable, not raw `Text`.
- **Palette:** Orange `#FA4616` · Yellow `#FFC72C` · Blue-Grey `#7B8189` · Charcoal `#0C0C10` (canvas) / `#161618` (cards).
- **Internal identifiers** keep `MEATrec` for back-compat (theme keys, IxmlWriter PROJECT tag, CurveBitmapExport file/folder names).

## Colors

| Where you'd reach for a raw hex | Use this token instead |
|---|---|
| `RecorderOrange` for buttons | `MaterialTheme.colorScheme.primary` |
| `RecorderYellow` for accents | `MaterialTheme.colorScheme.secondary` |
| `RecorderBlueGrey` for grid / muted text | `MaterialTheme.colorScheme.tertiary` or `outline` |
| Card background `#161618` | `MaterialTheme.colorScheme.surfaceContainer` |
| Higher card | `surfaceContainerHigh` / `surfaceContainerHighest` |
| "Recording saved" green | `SemanticSuccess` |
| Error toast / red dot | `SemanticError` (= `colorScheme.error`) |

Only reach for the raw brand constants (`RecorderOrange`, etc.) when drawing on a `Canvas` (gradients, paths, spectrum) where `MaterialTheme` isn't readable — or in `BrandWordmark` itself.

## Typography

Use `MaterialTheme.typography.*` for normal text. Use `LocalAppTypography.current.*` for the four custom styles:

- `labelTiny` — caps section labels ("FILE", "RECORDINGS")
- `numericLarge` — timer / hero numbers
- `numericMedium` — dB / gain readouts
- `numericSmall` — inline Hz / ms numbers

JetBrains Mono (numericX) has **tabular figures** by default — digits don't jitter as values change. Use it for any number that ticks.

## Spacing

`import com.example.recorderproject.ui.theme.Spacing`

- `Spacing.xxs` (2dp) — sub-grid, paired-icon gaps only
- `Spacing.xs` (4) · `sm` (8) · `md` (12, default card pad) · `lg` (16, screen edge) · `xl` (24) · `xxl` (32) · `xxxl` (48)

Examples:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) { ... }
Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
```

## Shapes

`MaterialTheme.shapes.medium` (8dp) is the new default for cards, replacing scattered `RoundedCornerShape(12.dp)`. Use `.small` for chips, `.large` for sheets/dialogs.

## Motion

Durations are integers in ms — pass to `tween(...)`, `delay(...)`, etc.

```kotlin
animateFloatAsState(targetValue, tween(Duration.medium, easing = Easing.standard))
```

Honor reduce-motion when you add a new animation:

```kotlin
val reduce = LocalReduceMotion.current
val spec = if (reduce) snap() else tween(Duration.medium)
```

## Wordmark

```kotlin
import com.example.recorderproject.ui.components.BrandWordmark

BrandWordmark()                                                   // titleLarge (TopBar size)
BrandWordmark(style = MaterialTheme.typography.displayMedium)     // splash size
BrandWordmark(style = MaterialTheme.typography.headlineMedium)    // onboarding size
```

## Adding a new screen — example

```kotlin
@Composable
fun MyScreen() {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("SECTION", style = LocalAppTypography.current.labelTiny,
             color = MaterialTheme.colorScheme.outline)
        Card(shape = MaterialTheme.shapes.medium) {
            Text("body copy", style = MaterialTheme.typography.bodyLarge,
                 color = MaterialTheme.colorScheme.onSurface,
                 modifier = Modifier.padding(Spacing.md))
        }
        Text("-12.3 dB", style = LocalAppTypography.current.numericMedium,
             color = MaterialTheme.colorScheme.primary)
    }
}
```

## Font licenses

Plus Jakarta Sans — SIL Open Font License 1.1 (https://github.com/itfoundry/Plus-Jakarta-Sans).
JetBrains Mono — SIL Open Font License 1.1 (https://github.com/JetBrains/JetBrainsMono).

Neither requires runtime attribution; license texts live in the upstream repos.
```

- [ ] **Step 2: Commit**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
git add docs/superpowers/design-system.md
git commit -m "docs(design): design-system.md reference for future PRs"
```

---

## Task 14: Final verification

**Goal:** Build + tests + manual smoke walk to confirm the foundation is in place and nothing regressed.

**Files:** No file changes; verification only.

- [ ] **Step 1: Clean build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew clean assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: All unit tests pass**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, no failures. Theme/typography/shape/spacing changes are non-behavioral so existing audio/model tests should continue to pass unchanged.

- [ ] **Step 3: Confirm no stray Kmutt references in Theme.kt**

```bash
grep -n "Kmutt" app/src/main/java/com/example/recorderproject/ui/theme/Theme.kt
```

Expected: no output. The constants `KmuttMaroon` / `KmuttGold` may still appear in `Color.kt` (as legacy aliases for recovered files) — that is intentional per the spec.

- [ ] **Step 4: Confirm no leftover hardcoded `fontSize = 10.sp, letterSpacing = 1.5.sp` triplet in `RecorderApp.kt`**

```bash
grep -n "fontSize = 10.sp" app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt
```

Expected: no output.

- [ ] **Step 5: Install on a device or emulator and walk every screen**

```bash
./gradlew installDebug
adb shell am start -n com.example.recorderproject/.MainActivity
```

Walk:
1. **Launcher** — app icon label reads "MEAT REC" (not "MEATrec")
2. **Splash** — wordmark renders as "MEAT" orange + "REC" yellow, large
3. **Onboarding** (first run only — delete app data to re-trigger) — wordmark renders two-color
4. **Recorder home** — TopBar shows MeatrecMark badge + "MEAT REC" wordmark; "FILE" / "BIT DEPTH" / "RECORDINGS" labels render in caps + 1.5sp letter-spacing (visually identical to before, now sourced from `labelTiny`); the status card padding is 12dp (was 14dp — 2dp tighter, intentional)
5. **Settings** — About card shows "MEAT REC" wordmark; theme picker still works (the "MEATrec" theme key is internal, not user-facing in the label)
6. **EQ screen** — TopBar shows "MEAT REC EQ" with the two-color wordmark plus separate "EQ" suffix
7. **General theming sanity** — any Material3 component that pulls from theme (the OutlinedButtons on the home screen, the slate dialog, the source picker) should now reflect orange/yellow accents, never maroon

If anything renders incorrectly (e.g., wrong color, font not loading, layout shift > 2dp), file as a fix-forward task and re-run.

- [ ] **Step 6: Confirm clean git log**

```bash
git log --oneline -16
```

Expected: roughly 14 task commits since the spec commit `937e3c8`, each with a clear `feat(design): ...` or `docs(design): ...` subject.

- [ ] **Step 7: No commit**

This task is verification, not implementation. Nothing to commit.

---

## Done

After Task 14 passes, the design system foundation is shipped. Future polish phases (app icon redesign, main-screen polish, EQ polish, component pass, motion overhaul) each become their own spec → plan cycle and consume these tokens.
