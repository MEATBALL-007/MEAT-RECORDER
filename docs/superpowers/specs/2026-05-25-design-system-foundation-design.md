# Design System Foundation — Design

**Date:** 2026-05-25
**Sibling to:** `2026-05-25-recorder-roadmap.md` (cross-cutting; precedes Phases 2–7 polish work)
**Status:** Approved direction (dark pro-tool / data-viz aesthetic), spec drafted, awaiting user review
**Target:** Android, Kotlin + Jetpack Compose, minSdk 24, Compose BOM 2024.02.00

---

## Goal

Establish the foundational design tokens (color, typography, shape, spacing, motion, elevation) so every existing and future screen draws from a single source of truth. Fix the immediate `Theme.kt` palette mismatch. Set the aesthetic direction (dark, sophisticated, instrument/data-viz vibe — iOS-tier polish on Android) that all Phase 2–7 polish work compounds on.

This is **infrastructure**. It does not redesign any screen. It establishes the vocabulary that later UI passes (Main screen polish, EQ polish, etc.) will use.

## Why now (the problem)

Two concrete failures today:

1. **`Theme.kt` ships the wrong palette.** `MaterialTheme.colorScheme.primary` is `KmuttMaroon (#A31F34)` — a legacy color the roadmap says "is not used in new UI". Material3 components that read from theme (`Switch`, `Slider`, `TextField`, `OutlinedButton` defaults, `IconButton` ripples) will render in maroon, while screens like `RecorderApp.kt` hardcode `RecorderOrange` directly. The two streams diverge visibly.
2. **Tokens don't exist.** `RecorderApp.kt:164` writes `fontSize = 10.sp, letterSpacing = 1.5.sp` inline; the same triplet repeats at `:186`, `:281`. Padding is sprinkled (`14.dp`, `12.dp`, `8.dp`, `6.dp`) with no rule. Every future polish PR multiplies this drift.

Fixing screens (#3, #4, #5 from the polish backlog) before fixing the foundation guarantees rework.

## Design direction

Confirmed: **dark, sophisticated, instrument/data-viz vibe** — references skew toward fintech dashboards, pro audio interfaces, iOS-quality polish. Not consumer-playful. The brand palette (orange / yellow / blue-grey on charcoal) already supports this. The system formalizes it.

Brand stance also takes a **KMUTT-style institutional wordmark** approach: bold caps, two-tone treatment, paired short words. See "Brand wordmark" section below.

Concretely:
- Numbers are first-class citizens — tabular monospace for timecodes, dB, Hz, sample rates
- Hierarchy through restraint — fewer weights, more spacing
- Motion is subtle and physics-driven, not flashy
- 4-pt spacing grid (matches Material default; predictable rhythm)
- Tighter corner radii than consumer Material (6–10 dp default, not 16–24) — instrument feel
- Semantic colors (success / warning / error / info) so component code never reaches for raw hex

## Brand wordmark — "MEAT REC"

The app name renders as **two words, two colors, all caps**:

```
MEAT REC
████ ███
 ↑    ↑
orange yellow
(#FA4616) (#FFC72C)
```

| Property | Value |
|---|---|
| App display name | `MEAT REC` (was `MEATrec`) |
| Word 1 | `MEAT` in `RecorderOrange` — primary brand mass |
| Word 2 | `REC` in `RecorderYellow` — accent / recording-indicator semantic |
| Font | Plus Jakarta Sans, Bold (700) |
| Case | ALL CAPS |
| Letter spacing | 2.5 sp |
| Inter-word spacing | 8 dp (between MEAT and REC) |
| Background | `RecorderCharcoal` (or transparent over charcoal surfaces) |

**Why this split:** parallels institutional wordmark patterns (KMUTT-style two-tone), gives the recording-indicator semantic to the "REC" word (industry convention — REC is always the eye-catching word), keeps "MEAT" as the brand mass. The two-color split also makes the wordmark recognizable at thumbnail / launcher size.

**Affected surfaces:**
- `res/values/strings.xml` → `<string name="app_name">MEAT REC</string>`
- `RecorderApp.kt:110` → replace single `Text("MEATrec", color = RecorderYellow, ...)` with a `Row` of two `Text` calls per the spec above
- `SplashScreen.kt` → wordmark uses the same two-color split (file inspection pending; same treatment)
- `MeatrecMark.kt` icon badge stays unchanged (the orange-yellow sweep gradient already harmonizes); rename of file/symbol to `MeatRecMark` is **out of scope** to keep churn small — comment will note the new spelling.

A `BrandWordmark` composable lives in `ui/components/BrandWordmark.kt` (new) so the split rendering exists in one place and every surface calls it:

```kotlin
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.5.sp,
    ),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), // 8.dp
    ) {
        Text("MEAT", style = style, color = RecorderOrange)
        Text("REC",  style = style, color = RecorderYellow)
    }
}
```

Callers pass `style = MaterialTheme.typography.displayMedium...` for splash, `titleLarge` for TopBar — single component, scales by typography token.

---

## Scope

### In scope

- `Color.kt` — add semantic tokens; keep existing brand constants
- `Theme.kt` — rewrite to use brand palette for both dark and light schemes
- `Type.kt` (**new**) — full Material3 typography scale + custom `labelTiny` + `numeric*` (mono) styles
- `Shape.kt` (**new**) — `MaterialTheme.shapes` overrides
- `Spacing.kt` (**new**) — `object Spacing` with 4-pt grid tokens
- `Motion.kt` (**new**) — duration + easing tokens, `LocalReduceMotion` composition local
- One proof-of-use migration: replace the three hardcoded `fontSize = 10.sp, letterSpacing = 1.5.sp` triplets in `RecorderApp.kt` with the new `labelTiny` style, and one magic-dp value with `Spacing.md`
- Brand wordmark rename: `app_name` → `MEAT REC`; new `BrandWordmark` composable; `RecorderApp` TopBar adopts it
- Font files added to `app/src/main/res/font/`
- `docs/superpowers/design-system.md` — usage reference for future PRs

### Out of scope

- ❌ Redesigning any screen layout (that's Main screen polish / EQ polish, separate specs)
- ❌ Bulk migration of every screen to tokens (later polish passes pick this up as they touch each file)
- ❌ App icon / launcher / splash redesign (separate spec: branding)
- ❌ Component-level redesign (file rows, dialogs — later spec)
- ❌ Light theme polish (dark is primary; light scheme exists but isn't the polish target this round)
- ❌ Brand palette changes (orange / yellow / blue-grey / charcoal stay as-is)

---

## Token specifications

### 1. Color (`Color.kt` additions)

Keep all existing constants. Add:

```kotlin
// Semantic
val SemanticSuccess = Color(0xFF3DDC97)   // mint — recording saved, render complete
val SemanticWarning = Color(0xFFFFC72C)   // = RecorderYellow — clip, caution
val SemanticError   = Color(0xFFFF5A5F)   // recording failed, permission denied
val SemanticInfo    = Color(0xFF7AB7FF)   // tips, neutral notifications

// Surface elevation tiers (Material3 surfaceContainer pattern, charcoal-tinted)
val SurfaceContainerLowest  = Color(0xFF09090C)
val SurfaceContainerLow     = Color(0xFF111114)
val SurfaceContainer        = Color(0xFF161618)   // = RecorderCharcoalCard
val SurfaceContainerHigh    = Color(0xFF1C1C20)
val SurfaceContainerHighest = Color(0xFF222227)

// Spectrum gradient stops (for future data viz — meters, spectrograms)
val SpectrumLow  = Color(0xFF1E3A5F)   // deep blue
val SpectrumMid  = RecorderYellow
val SpectrumHigh = RecorderOrange
val SpectrumClip = SemanticError
```

WCAG: contrast of `OnSurfaceDark (#E6E6E6)` against `RecorderCharcoal (#0C0C10)` = 15.2:1 (AAA). Against `SurfaceContainerHighest` = 11.8:1 (AAA). All semantic colors against charcoal ≥ 4.5:1 (AA normal). Verified by hand; will spot-check with Compose preview at implementation time.

### 2. `Theme.kt` rewrite

Replace `KmuttDarkColors` / `KmuttLightColors` with brand-aligned schemes. Keep the function name `RecorderProjectTheme` (callers don't change).

```kotlin
private val RecorderDarkColors = darkColorScheme(
    primary             = RecorderOrange,
    onPrimary           = RecorderCharcoal,
    primaryContainer    = Color(0xFF7A1F00),
    onPrimaryContainer  = Color(0xFFFFD9CC),
    secondary           = RecorderYellow,
    onSecondary         = RecorderCharcoal,
    secondaryContainer  = Color(0xFF665000),
    onSecondaryContainer= Color(0xFFFFF1C2),
    tertiary            = RecorderBlueGrey,
    onTertiary          = RecorderCharcoal,
    background          = RecorderCharcoal,
    onBackground        = OnSurfaceDark,
    surface             = RecorderCharcoal,
    onSurface           = OnSurfaceDark,
    surfaceVariant      = SurfaceContainerHigh,
    onSurfaceVariant    = OnSurfaceVariantDark,
    surfaceContainerLowest  = SurfaceContainerLowest,
    surfaceContainerLow     = SurfaceContainerLow,
    surfaceContainer        = SurfaceContainer,
    surfaceContainerHigh    = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    error               = SemanticError,
    onError             = RecorderCharcoal,
    outline             = RecorderBlueGrey,
    outlineVariant      = Color(0xFF3A3D42),
)

private val RecorderLightColors = lightColorScheme(
    primary    = RecorderOrange,
    secondary  = RecorderYellow,
    tertiary   = RecorderBlueGrey,
    // Light scheme is functional but not the polish target this round.
)
```

`RecorderProjectTheme` now wires in `typography = RecorderTypography` and `shapes = RecorderShapes` as well.

Legacy `KmuttDarkColors` / `KmuttLightColors` / `KmuttMaroon` / `KmuttGold` references in `Theme.kt` are deleted. The constants stay in `Color.kt` only because recovered files still reference them at compile time (per Phase 6 removal note).

### 3. Typography (`Type.kt` — new)

Font choices:
- **Display / body:** `Plus Jakarta Sans` (free, OFL, modern geometric, broad Latin + Thai support, multiple weights). Sits between Inter (too neutral) and Space Grotesk (no Thai).
- **Numerics:** `JetBrains Mono` (free, OFL, tabular figures by default — critical for level/timecode readouts that must align vertically).

Both shipped as `.ttf` files in `app/src/main/res/font/` (no Google Fonts runtime fetch — keeps the app offline-resilient, lesson from the eviction event in the roadmap).

Scale (Material3 names + custom additions):

| Style | Font | Weight | Size | Line height | Letter spacing | Use |
|---|---|---|---|---|---|---|
| displayLarge | Jakarta | 700 | 57 sp | 64 sp | -0.5 | Hero numbers (timer, big take #) |
| displayMedium | Jakarta | 700 | 45 sp | 52 sp | 0 | Splash, mode selector titles |
| displaySmall | Jakarta | 600 | 36 sp | 44 sp | 0 | Section heroes |
| headlineLarge | Jakarta | 600 | 32 sp | 40 sp | 0 | Screen titles |
| headlineMedium | Jakarta | 600 | 28 sp | 36 sp | 0 | Dialog titles |
| headlineSmall | Jakarta | 600 | 24 sp | 32 sp | 0 | Sheet titles |
| titleLarge | Jakarta | 600 | 22 sp | 28 sp | 0 | TopAppBar |
| titleMedium | Jakarta | 600 | 16 sp | 24 sp | 0.15 | Card titles |
| titleSmall | Jakarta | 600 | 14 sp | 20 sp | 0.1 | List item titles |
| bodyLarge | Jakarta | 400 | 16 sp | 24 sp | 0.5 | Default body |
| bodyMedium | Jakarta | 400 | 14 sp | 20 sp | 0.25 | Secondary body |
| bodySmall | Jakarta | 400 | 12 sp | 16 sp | 0.4 | Captions |
| labelLarge | Jakarta | 600 | 14 sp | 20 sp | 0.1 | Button label |
| labelMedium | Jakarta | 600 | 12 sp | 16 sp | 0.5 | Chip label |
| labelSmall | Jakarta | 600 | 11 sp | 16 sp | 0.5 | Smallest label |

Plus **custom extensions** (not part of Material3 typography — accessed via `LocalAppTypography`):

| Style | Font | Weight | Size | Letter spacing | Use |
|---|---|---|---|---|---|
| `labelTiny` | Jakarta | 600 | 10 sp | 1.5 sp | Caps section labels ("FILE", "RECORDINGS", "BIT DEPTH") |
| `numericLarge` | JetBrains Mono | 500 | 48 sp | 0 | Timer / countdown / hero number |
| `numericMedium` | JetBrains Mono | 500 | 24 sp | 0 | Level readout, gain dB |
| `numericSmall` | JetBrains Mono | 400 | 14 sp | 0.5 | Inline numbers (Hz, ms) |

The tabular-figures property of JetBrains Mono is what stops the timer from jittering as digits change — non-negotiable for a recorder.

### 4. Shapes (`Shape.kt` — new)

```kotlin
val RecorderShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(6.dp),    // chips, small buttons
    medium     = RoundedCornerShape(8.dp),    // cards (was 12 — tightened for instrument feel)
    large      = RoundedCornerShape(12.dp),   // dialogs, sheets
    extraLarge = RoundedCornerShape(20.dp),   // full-screen sheets, splash card
)
```

Existing screens that hardcode `RoundedCornerShape(12.dp)` keep working (Material3 still respects literal shapes); only new/migrated screens read from `MaterialTheme.shapes.medium`.

### 5. Spacing (`Spacing.kt` — new)

```kotlin
object Spacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}
```

4-pt grid. `12.dp` (`md`) chosen as default card padding because it matches the dominant existing value. Future screens import `Spacing` directly; no theme indirection (kept simple — these are constants, not theme-dependent).

### 6. Motion (`Motion.kt` — new)

```kotlin
object Duration {
    const val instant = 80    // press feedback, micro
    const val fast    = 160   // toggles, small fades
    const val medium  = 280   // standard transitions
    const val slow    = 480   // hero animations, splash
    const val verySlow = 800  // ambient (breathing rings)
}

object Easing {
    val standard         = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val emphasized       = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val emphasizedAccel  = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val emphasizedDecel  = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
}

val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun ReducedMotionProvider(reduce: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}
```

`LocalReduceMotion` is the cross-cutting hook the roadmap promised in Phase 1 (it was deferred). Existing animations don't change behavior in this spec; they get an audit pass in a later motion-polish spec where each animation gains an `if (LocalReduceMotion.current) snap() else spring()` branch. Reading it: animations that already exist keep working; this just wires the local so future code can opt in.

### 7. Wiring it together (`Theme.kt`)

```kotlin
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
```

`LocalAppTypography` exposes the custom styles (`labelTiny`, `numericLarge`, etc.) that don't fit Material3's `Typography` shape.

### 8. Proof-of-use migration in `RecorderApp.kt`

Three sites changed (no behavior change):

```kotlin
// Before (lines 164, 186, 281):
Text("FILE", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp)

// After:
Text("FILE", style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
```

And one spacing change as smoke test:

```kotlin
// Before line 161: .padding(14.dp)
// After:           .padding(Spacing.md)   // 12.dp — intentional 2dp tighten as part of token alignment
```

This proves the tokens work end-to-end without committing to bulk migration. The remaining magic-dp values in `RecorderApp.kt` are left for the main-screen polish spec to pick up.

---

## Architecture summary

```
ui/theme/
├── Color.kt          — brand + semantic + surface tier + spectrum tokens
├── Type.kt           — NEW · RecorderTypography + RecorderAppTypography + LocalAppTypography
├── Shape.kt          — NEW · RecorderShapes
├── Spacing.kt        — NEW · object Spacing (4-pt grid)
├── Motion.kt         — NEW · Duration / Easing / LocalReduceMotion
├── Theme.kt          — REWRITTEN · RecorderDarkColors / RecorderLightColors / RecorderProjectTheme wiring
└── AppTheme.kt       — unchanged (theme enum stub, addressed in later phase)

res/font/
├── plus_jakarta_sans_regular.ttf
├── plus_jakarta_sans_semibold.ttf
├── plus_jakarta_sans_bold.ttf
├── jetbrains_mono_regular.ttf
└── jetbrains_mono_medium.ttf
```

No new dependencies. No NDK. No native fonts at runtime.

## Testing & verification

This is design infrastructure, so verification is visual + structural, not behavioral:

1. **Build pass** — `./gradlew assembleDebug` clean
2. **Lint pass** — no new warnings introduced
3. **Compose previews** — add a `@Preview` to `Theme.kt` rendering a swatch grid + type ramp + spacing ruler + shape samples. Manual visual check.
4. **Launch app** — sanity-walk through every existing screen. Confirm: (a) no Material component renders maroon, (b) `RecorderApp` looks identical to before except the 2dp padding tighten and unchanged labels, (c) Material `TextField` / `Switch` / `Slider` now show orange/yellow accent.
5. **Existing tests** — `./gradlew test` continues green (these tests are audio-side; theme changes shouldn't touch them).

No new tests added — typography/color/shape constants are declarative, not logic. A snapshot test would be valuable later but adds dependencies (Paparazzi or Roborazzi) — deferred to a later infra spec.

## Migration policy for later polish work

Once this lands, every subsequent UI-touching PR follows two rules:
1. **New code** uses tokens (`MaterialTheme.colorScheme.*`, `LocalAppTypography.current.*`, `Spacing.*`, `MaterialTheme.shapes.*`, `Duration.*`, `Easing.*`).
2. **Touched code** migrates inline — if a polish PR opens `EQScreen.kt` to adjust layout, the hardcoded colors/dp values in that file get swapped for tokens as part of that PR. No standalone "token migration" sweep.

This contains scope without leaving permanent two-system drift.

## Definition of done

- [ ] `Theme.kt` wires brand palette through `MaterialTheme.colorScheme` (dark + light)
- [ ] `Type.kt`, `Shape.kt`, `Spacing.kt`, `Motion.kt` exist with the tokens above
- [ ] Plus Jakarta Sans + JetBrains Mono `.ttf` files in `res/font/` and registered via `FontFamily`
- [ ] `RecorderProjectTheme` accepts `reduceMotion` parameter (default `false`) and provides `LocalReduceMotion`
- [ ] `RecorderApp.kt` uses `labelTiny` for the three "FILE" / "BIT DEPTH" / "RECORDINGS" labels and `Spacing.md` for one card padding (proof of use)
- [ ] `strings.xml` app_name = `MEAT REC`; `BrandWordmark` composable exists; `RecorderApp` TopBar renders it instead of the inline `Text("MEATrec", ...)`
- [ ] No remaining `KmuttMaroon` / `KmuttGold` references in `Theme.kt`
- [ ] `./gradlew assembleDebug` green
- [ ] `./gradlew test` green (unchanged behavior)
- [ ] `docs/superpowers/design-system.md` committed — short usage reference for future PRs (token names, when to use which, a code example for typical card)
- [ ] Manual visual check: walk every existing screen, no surprise color shifts beyond intentional Material-default accents (now orange/yellow)
- [ ] Spec committed to `docs/superpowers/specs/`

## Out of scope (future specs)

These intentionally fall to later specs, listed here so reviewers know what is NOT being addressed:

- **App icon + branding** — launcher icon redesign, splash polish, store assets
- **Main screen redesign** — `RecorderApp.kt` layout / hierarchy / spacing overhaul
- **EQ screen polish** — curve view, sliders sheet, top bar
- **Component consistency pass** — file list rows, dialogs, chips
- **Motion overhaul** — auditing each animation to honor `LocalReduceMotion`, refining timing
- **Light theme polish** — beyond functional minimum
- **Snapshot testing infra** — Paparazzi/Roborazzi setup

## Resolved judgment calls

These were flagged for review and confirmed:

1. **Brand wordmark "MEAT REC", MEAT = Orange / REC = Yellow** — confirmed 2026-05-25; parallels KMUTT institutional two-tone treatment; recording-indicator semantic on the REC word.
2. **Plus Jakarta Sans (display + body)** — confirmed; Thai support + geometric character; over the more neutral Inter alternative.
3. **Tightened card radius (12 → 8 dp) as `shapes.medium`** — confirmed; instrument vibe over consumer Material default; existing screens that hardcode `RoundedCornerShape(12.dp)` keep working until they migrate.

No open questions remain.
