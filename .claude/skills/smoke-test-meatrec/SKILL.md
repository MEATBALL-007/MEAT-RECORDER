---
name: smoke-test-meatrec
description: >
  Launch and smoke-test the MEATrec Android recorder app on an emulator and verify core
  behavior on-device. Use this WHENEVER the user wants to run, launch, build-and-install,
  or smoke-test the MEATrec / recorder app, confirm a change works on a device or emulator,
  verify recording/playback after a refactor, or "see it actually work" rather than just
  passing unit tests. Triggers on phrasings like "run the app", "smoke test", "does
  recording still work", "test on the emulator", "verify on device", even if MEATrec isn't
  named explicitly — this is an Android app whose lifecycle the JVM unit suite does NOT cover,
  so on-device verification is the only way to catch recording-pipeline regressions.
---

# Smoke-test MEATrec on the emulator

MEATrec is a Jetpack-Compose audio recorder. Its recording lifecycle is `AudioRecord` /
`MediaPlayer` / foreground-service coupled, so the JVM unit tests can't exercise it — the
only real check is launching the app and driving it. This skill is the verified runbook.

## Environment facts (don't rediscover these)

- **adb is NOT on PATH.** Use `$HOME/Library/Android/sdk/platform-tools/adb` (export
  `ANDROID_SDK_ROOT` to override). Same for `emulator/`.
- **AVD:** `Medium_Phone`. Boot with `-gpu swiftshader_indirect` (no host GPU).
- **Debug package:** `com.meatball.meatrec.debug` · activity `com.example.recorderproject.MainActivity`.
  A legacy `com.meatrec.recorder.debug` may linger on the emulator — uninstall it too.
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` on the internal repo copy; the external
  exFAT copy redirects build output to `~/.meatrec-build/`.

## One-command launch

```bash
bash .claude/skills/smoke-test-meatrec/scripts/launch.sh
```

This boots the emulator if needed, builds, uninstalls stale packages, installs, pre-grants
RECORD_AUDIO + ACCESS_FINE_LOCATION, and launches. Then drive the UI with `scripts/ui.py`.

## Two gotchas that look like failures but aren't

1. **Black screencap.** swiftshader returns an all-black frame — screenshots are useless.
   **Always inspect the UI with `uiautomator dump`, never `screencap`.** `scripts/ui.py`
   wraps this: `ui.py labels`, `ui.py clickable`, `ui.py tap-text "<label>"`, `ui.py tap X Y`.
2. **Cold-start ANR.** First launch shows "MEAT REC isn't responding" because the `RecorderApp`
   composable JIT-compiles (~10 s / ~17 MB) on the software renderer. Tap **Wait**
   (`ui.py tap-text Wait`) and give it ~12 s. Confirm it's only jank, not a crash:
   `adb logcat -d | grep -iE 'FATAL|AndroidRuntime'` (expect empty).

## First-run dialogs (expected, in order)

The first time you tap record on a clean install:
1. **"Where should recordings be saved?"** → `ui.py tap-text "USE APP STORAGE"`.
2. **Bluetooth "nearby devices" permission** → `ui.py tap-text "Allow"`.

## Core smoke flow (record → stop → library → playback)

This is the high-value path — it drives the whole recording lifecycle. Verify each step from
logcat (`adb logcat`), not the black screen.

1. **Main screen renders** — `ui.py labels` shows `MeatRec`, `Record`, `Library`, `Scene 1`.
2. **Open controls** — tap the `Record` area, then `ui.py clickable`. The large clickable node
   (a tall, wide bounds box, e.g. `[251,787][829,1365]`) is the record circle.
3. **Start** — tap that circle's centre. Confirm: logcat streams
   `AudioRecorderManager: Read: 512 floats, total written: …`.
4. **Stop** — tap the circle again. Confirm: logcat shows
   `RecorderViewModel: stopRecording() called` → `Recorder stopped, file: Scene_1_T01.wav`
   → `Recording stopped successfully`.
5. **Library** — tap `Library`; `ui.py labels` lists the take with a duration (e.g. `0:24`).
   The WAV is under
   `/storage/emulated/0/Android/data/com.meatball.meatrec.debug/files/Music/Recordings/`
   (the app smart-auto-renames, e.g. `scene_1_auto_quiet_…wav`).
6. **Playback** — tap a take; the player label advances `m:ss / m:ss` (e.g. `0:04 / 0:24`).

If a take logs "stopped successfully" but never appears in the library, that's the real
regression to chase (the post-production chain dropping the file) — not the ANR.

## Known hard-to-test bits

The **Monitor / live-EQ / VAD / pre-roll / countdown** toggles are unlabelled icon chips
(no text or content-desc), so `tap-text` can't reach them — you'd be tapping `clickable`
bounds blind. The first top-row chip opens the **mode selector** (FILM/INTERVIEW/MUSIC…),
which is a good, labelled proxy that exercises `selectRecorderMode`. Monitor without
headphones connected is expected to refuse with a "plug in headphones" toast.

## Final crash sweep

```bash
adb logcat -d | grep -iE "FATAL EXCEPTION|AndroidRuntime" | grep -i meatrec   # expect empty
adb shell pidof com.meatball.meatrec.debug                                     # expect a pid
```
