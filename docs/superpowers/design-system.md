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

Plus Jakarta Sans — SIL Open Font License 1.1 (https://github.com/tokotype/PlusJakartaSans).
JetBrains Mono — SIL Open Font License 1.1 (https://github.com/JetBrains/JetBrainsMono).

Neither requires runtime attribution; license texts live in the upstream repos.
