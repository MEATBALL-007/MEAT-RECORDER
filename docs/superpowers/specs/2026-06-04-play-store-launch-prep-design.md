# MEAT REC — Play Store Launch Preparation

**Date:** 2026-06-04
**Target:** Google Play launch July 2026
**Context:** App is feature-complete on `feat/loudness-delivery` branch. Signing, ProGuard, and privacy policy are already in place. Play Console account will be created by user's dad next month.

---

## Goal

Ship MEAT REC to Google Play at "Apple grade" quality: no crashes, polished UI, every feature working end-to-end on a real device, and a redesigned Pro upgrade page that is visually compelling.

---

## Approach

Six sequential phases. Phases 1–5 are code/testing work. Phase 6 is a handoff document for dad to execute in Play Console.

| Phase | What | Exit condition |
|---|---|---|
| 1 | Verify loudness delivery on real device | Full flow works end-to-end, no crashes |
| 2 | Merge to main + full QA sweep | Every screen tested and logged |
| 3 | Fix + polish (incl. Pro page redesign) | Zero known crashes, UI feels tight |
| 4 | Release build | Signed AAB builds clean, ProGuard verified |
| 5 | Store assets | ≥6 screenshots taken from polished build |
| 6 | Play Console handoff doc | Dad has a clear checklist to follow |

---

## Phase 1 — Verify Loudness Delivery on Real Device

The loudness delivery code is already written and compiles (`DeliveryRenderer`, `LufsProcessor`, `TruePeakDetector`, `LoudnessRangeMeter`, `LoudnessMeterBar`, `LoudnessTargetChip` all exist). It has not been tested on device yet.

**Test steps:**
1. Build debug APK and install on real device
2. Tap `LoudnessTargetChip` → select Podcast –16 → record 30 seconds → stop
3. Confirm delivery file appears in file list with `–16` badge
4. Confirm `name_delivery.wav` exists alongside `name.wav` (original untouched)
5. Test each preset: Streaming –14, Broadcast –23, Custom (set manual LUFS + TP), Off
6. Verify `LoudnessMeterBar` animates during recording with M / S / I readouts
7. Verify TP indicator lights when signal is hot
8. Confirm "Re-render with different target…" appears on long-press of delivery file

Fix any crashes or wrong behavior before moving to Phase 2.

---

## Phase 2 — Merge to Main + Full QA Sweep

After Phase 1 passes: merge `feat/loudness-delivery` → `main`, install on real device, test every feature.

### Recording core
- Record with built-in mic, play back — audio sounds clean
- Pre-roll buffer: start recording, clap before tapping record → clap is captured
- VAD auto-start: enable VAD in settings, speak → recording starts automatically
- Scene/take naming: change scene name, confirm new take shows correct label
- Foreground service: lock screen during recording → notification persists, audio continues

### Processing
- EQ: tap EQ (free user → paywall; Pro user → EQ opens, bands adjust, sound changes)
- Pitch shift: open pitch shift, change pitch → no crash, audio re-pitches correctly
- Trim: open trim, waveform loads, drag handles, export → shorter file saved correctly
- Loudness delivery: full flow verified in Phase 1

### Files
- File list: recordings appear, tap to play
- Long-press: rename, delete, share all work
- Delivery file: groups with original as expandable row

### Pro paywall
- Free user tapping any Pro feature → paywall opens (new pager design after Phase 3)
- Paywall shows correct localized price
- Pro badge and –35% strikethrough visible
- DEBUG simulate Pro toggle (debug build only): unlocks all features

### Settings & language
- Language selector: change to each of 20 languages → UI updates
- Factory reset: clears all settings back to defaults
- Google Drive toggle: opens Google Sign-In

### Edge cases
- Deny microphone permission → graceful error shown, no crash
- Deny notification permission → recording still works, no crash
- Rotate screen during recording → recording continues uninterrupted

---

## Phase 3 — Fix + Polish

### 3a — Fix all issues found in Phase 2
Log every issue during QA, fix in priority order: crash > wrong behavior > visual glitch.

### 3b — Pro Page Redesign (Option A: Feature Pager)

Replace the current `ProUpgradeSheet` scroll list with a full-screen horizontal pager.

**File:** `app/src/main/java/com/example/recorderproject/ui/components/ProUpgradeSheet.kt` (rewrite)

**Layout:**
- Full-screen `ModalBottomSheet` (`skipPartiallyExpanded = true`)
- Top 80% of sheet: `HorizontalPager` — one slide per feature group
- Bottom 20%: dot indicators + slide counter + price row + CTA button

**6 slides:**

| Slide | Title | Blurb | Visual accent color |
|---|---|---|---|
| 1 | Loudness Delivery | Render to Podcast, Broadcast, or custom LUFS target | Orange / Yellow |
| 2 | Parametric EQ + Live DSP | Visual EQ with noise gate, compressor, stereo widener | Blue / Cyan |
| 3 | Studio Quality | 96 kHz / 32-bit float + any external mic | Green |
| 4 | Pitch Shift + Smart Capture | Re-pitch takes, pre-roll buffer, voice-activated start | Purple |
| 5 | Transcription + Cloud Backup | On-device STT + auto-upload to Google Drive | Teal |
| 6 | All Themes + Analysis Suite | 10 themes, spectrogram, room profiler, A/B compare | Pink / Amber |

**Per-slide layout:**
- 80% height: `Canvas`-drawn visual (waveform bars, meter bars, or typographic) on dark background with color-gradient glow
- 20% height: bold white title + one-line grey blurb

**Footer (always visible, below pager):**
- Row of dot indicators, active dot = wider pill in `RecorderYellow`
- Slide counter text: "2 / 6" in grey
- Price row: struck-through original → promo price in `RecorderYellow` → "lifetime" label
- Full-width orange `Button`: "Unlock MEAT REC Pro ฿199"
- `TextButton`: "Restore purchase"

**Behavior:**
- `highlight: ProFeature?` parameter: the slide matching the tapped feature opens first (e.g. tapping EQ → slide 2 opens)
- Auto-advance not needed (user swipes manually)
- Existing `onUpgrade`, `onRestore`, `onDismiss` callbacks unchanged — no ViewModel changes required

---

## Phase 4 — Release Build

```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

**Verify:**
- AAB size is reasonable (target < 20 MB)
- Install via sideload (`./gradlew installRelease`) and test one full recording flow
- ProGuard: confirm no crashes caused by obfuscation (check `app/build/outputs/mapping/release/mapping.txt` exists)
- Confirm `versionCode = 1`, `versionName = "1.0"` in `app/build.gradle.kts`
- Confirm `applicationId = "com.meatrec.recorder"` (not `com.example`)

---

## Phase 5 — Store Assets

Take screenshots from the **final polished release build** on real device. Use English UI (already default for launch).

| # | Screen | Notes |
|---|---|---|
| 1 | Recording in progress — waveform + LUFS meter live | Main feature, first impression |
| 2 | Pro upgrade pager — slide 1 (Loudness Delivery visual) | Shows new paywall design |
| 3 | EQ screen with bands active | Visual, impressive |
| 4 | File list with delivery file (–16 badge) | Shows workflow output |
| 5 | Language selector (20 languages visible) | Shows localization |
| 6 | Trim screen with waveform | Shows editing capability |

Alternatively: use Android Studio emulator (Pixel 7 Pro, API 35) for pixel-perfect clean screenshots without physical device clutter.

Assets already done:
- `docs/play-assets/icon-512.png` ✅
- `docs/play-assets/feature-1024x500.png` ✅
- Privacy policy live at `https://meatball-007.github.io/meatrec-privacy/` ✅

---

## Phase 6 — Play Console Handoff (for dad)

To be written as `docs/PLAY_CONSOLE_HANDOFF.md` — a clean bilingual (Thai + English) step-by-step checklist.

**Contents:**

1. **Create Developer Account** — play.google.com/console → $25 one-time fee → use Gmail
2. **Create Merchant Profile** — Setup → Payments profile → bank details (required to receive Pro payments)
3. **Create App** — Create app → name: MEAT REC → Free → English → accept declarations
4. **Store Listing** — copy from `docs/PLAY_STORE_SUBMISSION.md` (short desc, full desc, category, privacy URL)
5. **Upload Assets** — icon 512×512, feature graphic 1024×500, ≥6 screenshots from Phase 5
6. **Data Safety** — fill form per table in `docs/PLAY_STORE_SUBMISSION.md`
7. **App Content** — privacy policy URL, content rating questionnaire (Everyone), target audience 13+
8. **Upload AAB** — Testing → Internal testing → Create release → upload `app-release.aab`
9. **Fix Play App Signing SHA-1** — Play Console → Setup → App signing → copy SHA-1 → add to Google Cloud OAuth credentials (required for Drive backup to work for real users)
10. **Create In-app Product** — Monetize → Products → In-app products → Create:
    - Product ID: `meatrec_pro_lifetime` (must match exactly)
    - Name: MEAT REC Pro
    - Price: ฿199 (Play auto-converts to all currencies)
    - Status: Active
11. **License Testing** — Setup → License testing → add Gmail for free test purchases
12. **Test Pro purchase** — install from internal testing link → tap locked feature → buy → confirm unlock
13. **Promote to Production** — after all tests pass → Production → Create release → submit for Google review (1–7 days)

---

## What is NOT in scope for launch

- Google OAuth consent screen publishing (Drive backup limited to test users at launch — acceptable, flag in release notes)
- On-device STT improvement for Samsung/Xiaomi (known limitation, document in Play listing)
- Any new features beyond loudness delivery

---

## File summary

**Rewritten:**
- `app/src/main/java/com/example/recorderproject/ui/components/ProUpgradeSheet.kt`

**New:**
- `docs/PLAY_CONSOLE_HANDOFF.md`

**Unchanged:** all other files — no ViewModel, routing, or billing changes needed for the Pro page redesign.
