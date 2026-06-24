# Working on MEAT REC from the external drive (Mac + Windows)

This project lives on an **exFAT** external drive so it can be opened from both
macOS and Windows. exFAT is the only filesystem both OSes read/write natively,
but it has limits the Android build has to work around. This is already set up —
here's what to know.

## TL;DR — it just works, with one rule

- **Source + git stay on the drive** (portable — edit on either machine).
- **Build output goes to the local machine**, not the drive. `app/build.gradle.kts`
  auto-detects a portable drive and redirects the build dir to
  `~/.meatrec-build/` (macOS) / `C:\Users\<you>\.meatrec-build\` (Windows).
  You don't have to do anything; it's automatic.
- Each machine needs its **own `local.properties`** (it points to that machine's
  Android SDK and is gitignored — never copy it between machines).

## Why the build can't write to the drive

exFAT can't store the extended attributes the Android dexer (D8) sets on its
outputs. On macOS the kernel then creates `._` "AppleDouble" sidecar files for
every output, and D8 + Gradle's incremental cleanup choke on them
(`'._drawable' is not a directory`, `Unable to delete directory … ._xxxx.jar`).
Redirecting build output to a native filesystem (APFS / NTFS) avoids this
entirely. Build output is regenerable, so nothing important leaves the drive.

## First time on a NEW machine

1. Install **Android Studio** (gives you the SDK + a bundled JDK).
2. Open the project from the drive, OR create `local.properties` in the project
   root pointing at that machine's SDK:
   - macOS:   `sdk.dir=/Users/<you>/Library/Android/sdk`
   - Windows: `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`
     (note the escaped backslashes)
3. Build:
   - macOS:   `./gradlew assembleDebug`
   - Windows: `gradlew.bat assembleDebug`

The first build on each machine re-downloads dependencies into that machine's
global Gradle cache (`~/.gradle`) and is slower; later builds are fast.

## Things to keep in mind

- **Don't put the build output back on the drive.** If you ever see `._*` files
  or dex/"unable to delete" errors, the redirect was bypassed — check that
  `app/build.gradle.kts` still has the "Portable-drive build redirect" block.
- **Eject safely** before unplugging (exFAT has no journaling — yanking the drive
  mid-write can corrupt files).
- **Secrets travel with the folder**, not git: `keystore.properties` and
  `meatrec-release.jks` are gitignored, so they only exist because they're
  physically in this folder. Keep a separate backup of them.
- **`recovery/` salvage is NOT on the drive** — it stayed on the original Mac.
- `.gitattributes` keeps line endings correct across OSes (LF for `gradlew`,
  CRLF for `*.bat`). `._*` and `.DS_Store` are gitignored.
