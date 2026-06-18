#!/usr/bin/env bash
# Boot emulator (if needed), build the debug APK, (re)install it cleanly, launch it.
# Verified flow for MEATrec on macOS. Safe to re-run. Prints the adb path it used.
set -uo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
AVD="${MEATREC_AVD:-Medium_Phone}"
PKG="com.meatball.meatrec.debug"
ACT="com.example.recorderproject.MainActivity"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"  # repo root

echo "adb=$ADB  avd=$AVD  project=$PROJECT_DIR"

# 1. Emulator — boot only if nothing is attached.
if ! "$ADB" devices | grep -qE "emulator-[0-9]+\s+device"; then
  echo "Booting emulator $AVD (swiftshader)…"
  nohup "$EMU" -avd "$AVD" -no-snapshot-save -gpu swiftshader_indirect >/tmp/meatrec-emu.log 2>&1 &
fi
echo "Waiting for device + boot_completed…"
"$ADB" wait-for-device
for _ in $(seq 1 60); do
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 3
done
"$ADB" devices -l

# 2. Build.
echo "Building debug APK…"
( cd "$PROJECT_DIR" && ./gradlew assembleDebug ) || { echo "BUILD FAILED"; exit 1; }
# Internal copy → app/build/; external exFAT copy redirects to ~/.meatrec-build/
APK="$(find "$PROJECT_DIR/app/build/outputs/apk/debug" "$HOME/.meatrec-build" -name 'app-debug.apk' 2>/dev/null | head -1)"
echo "APK=$APK"

# 3. Install clean — drop BOTH the current and the legacy stale package first.
"$ADB" uninstall com.meatrec.recorder.debug >/dev/null 2>&1
"$ADB" uninstall "$PKG" >/dev/null 2>&1
"$ADB" install -r "$APK" || { echo "INSTALL FAILED"; exit 1; }

# 4. Pre-grant the runtime perms we can (first launch still prompts for the BT one).
"$ADB" shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null
"$ADB" shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null

# 5. Launch.
"$ADB" shell am start -n "$PKG/$ACT" >/dev/null 2>&1
echo "Launched $PKG. NOTE: first cold start JIT-compiles the Compose UI for ~10s on"
echo "swiftshader and may show an ANR dialog — tap Wait, give it ~12s. Not a crash."
