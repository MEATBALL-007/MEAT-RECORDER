# MEAT REC — Localization (20 languages)

Per-app language switching via `AppCompatDelegate.setApplicationLocales`. Users pick a
language in **Settings → Language**; the app updates instantly and the choice persists.

## Supported languages (20)

English · 中文 · हिन्दी · Español · Français · العربية · বাংলা · Português · Русский ·
Bahasa Indonesia · 日本語 · Deutsch · 한국어 · Türkçe · Tiếng Việt · ไทย · Italiano ·
Polski · Українська · فارسی

## How it works

- `i18n/AppLanguages.kt` — the language list + `setLanguage()` / `currentDisplayName()`.
- `ui/LanguageScreen.kt` — the picker (native names + English subtitle).
- `res/xml/locales_config.xml` + manifest `android:localeConfig` — declares supported locales
  to the system (Android 13+ shows the app under system language settings too).
- Manifest `AppLocalesMetadataHolderService` (autoStoreLocales=true) — persists the choice on
  Android < 13.
- `MainActivity` extends `AppCompatActivity` and the theme is `*.NoActionBar` so locale changes
  recreate the activity without showing a system action bar.

## Translation status — IMPORTANT

The strings in `res/values-XX/strings.xml` are **AI-generated drafts** covering the core,
highest-visibility UI (home screen, language screen, reset dialog, paywall price/CTA).

⚠️ **Have each language reviewed by a native speaker before the public release.** Machine
translations can be subtly wrong in tone or terminology.

Coverage is intentionally a curated core set, not every string in the app. The rest of the
UI stays in English until translated. To expand:

1. Add the new English string to `res/values/strings.xml`.
2. Add its translation to the `T` dict in `docs/i18n/gen_translations.py` (one entry per language).
3. Run `python3 docs/i18n/gen_translations.py` to regenerate all `values-XX/strings.xml`.
4. Replace the hardcoded string in Kotlin with `stringResource(R.string.<key>)`.

## Adding a language

Add it to: `AppLanguages.all`, `res/xml/locales_config.xml`, and the `T` dict in the
generator script. Regenerate and rebuild.
