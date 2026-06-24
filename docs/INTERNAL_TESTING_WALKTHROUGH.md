# MEAT REC — Internal Testing Walkthrough

Step-by-step to get the app into **Internal testing** on Google Play (private,
up to 100 testers, no review wait). Do these in order. Check each box.

> Console: https://play.google.com/console — sign in with your developer account
> (one-time US$25 registration required if you haven't paid it).

---

## Your values (copy-paste as needed)

| Field | Value |
|---|---|
| App name | **MEAT REC** |
| Package name | `com.meatball.meatrec` |
| versionCode / versionName | `1` / `1.0` |
| **AAB to upload** | `app/build/outputs/bundle/release/app-release.aab` |
| Privacy policy URL | `https://meatball-007.github.io/meatrec-privacy/` |
| In-app product ID | `meatrec_pro_lifetime` |
| Upload-key SHA-1 | `87:04:6F:BC:B8:42:AF:2D:45:20:B2:86:78:71:06:7F:DF:F4:51:13` |
| Upload-key SHA-256 | `BB:D9:CB:1B:4F:31:71:AE:C4:65:D9:AC:20:48:04:03:30:EF:83:B0:C7:FE:C4:D7:52:96:83:1A:C4:DE:3B:56` |
| Icon 512 | `docs/play-assets/icon-512.png` |
| Feature graphic | `docs/play-assets/feature-1024x500.png` |
| Screenshots (5) | `docs/play-assets/screenshots/` |
| Store listing copy | `docs/PLAY_STORE_SUBMISSION.md` |

---

## Phase 1 — Create the app

- [ ] Play Console → **Create app**
- [ ] App name: `MEAT REC`
- [ ] Default language: English (United States) — or Thai if you prefer
- [ ] App or game: **App**
- [ ] Free or paid: **Free** (the Pro unlock is an in-app product, so the app itself is free)
- [ ] Accept declarations → **Create app**

---

## Phase 2 — Upload to Internal testing (do this early — it kicks off Play App Signing)

- [ ] Left menu → **Testing → Internal testing**
- [ ] **Create new release**
- [ ] When prompted, **enroll in Play App Signing** (recommended/required).
      - Your keystore (`meatrec-release.jks`) becomes the **upload key**.
      - Google holds the real **app signing key**.
      - ⚠️ Keep `meatrec-release.jks` + `keystore.properties` backed up — losing the
        upload key means you must contact Google to reset it.
- [ ] **Upload** `app/build/outputs/bundle/release/app-release.aab`
- [ ] Release name: `1.0 (1)` (auto-fills)
- [ ] Release notes (inside `<en-US>` tags):
      `First internal build — full recorder, EQ, loudness delivery, Pro paywall.`
- [ ] **Next → Save** (don't roll out yet if the dashboard still shows red items — finish Phase 3–4 first, then roll out)

---

## Phase 3 — Testers list

- [ ] Internal testing → **Testers** tab
- [ ] Create an email list → add tester Gmail addresses (your own + friends; up to 100)
- [ ] Save
- [ ] Copy the **“Copy link”** opt-in URL — testers must open it and accept before they
      can install from Play. (They'll get the app on the Play Store as a normal install.)

---

## Phase 4 — Required declarations (the "Set up your app" dashboard)

These gate rollout. Do all of them:

- [ ] **App access** → "All functionality is available without special access"
      (MEAT REC needs no login)
- [ ] **Ads** → "No, my app does not contain ads"
- [ ] **Content rating** → fill questionnaire (utility/productivity app, no violent/sexual
      content) → submit → receive rating
- [ ] **Target audience** → select age groups (suggest 13+ or 18+; not designed for children)
- [ ] **Data safety** → declare:
      - Collects **Audio** (the recordings) — stored **on device**, and **uploaded to the
        user's own Google Drive** only if they enable Cloud Backup
      - **Location** (optional, approximate/precise) — only if user allows iXML tagging;
        stored in the file, **not** sent to you
      - **No data shared with third parties**, **no analytics**, **no ads**
- [ ] **Privacy policy** → paste `https://meatball-007.github.io/meatrec-privacy/`
- [ ] **Government / financial / health** → No

---

## Phase 5 — Store listing (main store listing)

- [ ] **Grow → Store presence → Main store listing**
- [ ] App name: `MEAT REC`
- [ ] Short description (≤80 chars) — from `docs/PLAY_STORE_SUBMISSION.md`
- [ ] Full description — from `docs/PLAY_STORE_SUBMISSION.md`
- [ ] App icon → upload `docs/play-assets/icon-512.png`
- [ ] Feature graphic → upload `docs/play-assets/feature-1024x500.png`
- [ ] Phone screenshots → upload the 5 PNGs in `docs/play-assets/screenshots/`
      (Play needs **at least 2**; you have 5)
- [ ] Save

---

## Phase 6 — In-app product (needed to test the Pro purchase)

The paywall UI already works; this makes the actual purchase complete.

- [ ] **Monetize → Products → In-app products → Create product**
- [ ] Product ID: `meatrec_pro_lifetime`  ← must match exactly (it's hardcoded in the app)
- [ ] Name: `MEAT REC Pro`
- [ ] Description: `One-time unlock — all Pro features, forever.`
- [ ] Price: set per region (app fallback shows ~US$3.99 launch price)
- [ ] **Activate**
- [ ] **Monetize → Setup → License testing** → add your testers' Gmails as **License
      testers** → they can make **test purchases for free** (no real charge)

---

## Phase 7 — Roll out & test

- [ ] Return to **Internal testing** → **Edit release** (or Review release) → **Start rollout
      to Internal testing** → confirm
- [ ] Open the **opt-in URL** on a tester device → accept → "Download on Google Play"
- [ ] Install from Play, then verify on-device:
  - [ ] App launches, records a take, take appears in list
  - [ ] Settings → Appearance → switch theme → background changes + persists
  - [ ] Tap ★ PRO → paywall opens on slide 1/7
  - [ ] Tap "Unlock MEAT REC Pro" → Google purchase sheet appears → complete **test
        purchase** → Pro features unlock, "PRO" shows in the top bar
  - [ ] Settings → Cloud backup → Google sign-in prompt appears

---

## Notes / gotchas

- **Internal testing has no review delay** — builds are usually available to testers
  within minutes. (Production/closed testing can take days for review.)
- **Billing only works through Play** — a sideloaded APK shows the paywall but cannot
  complete a purchase. That's why we test billing via the Internal track.
- **First upload is slow to propagate** to the Play Store listing for testers (can take
  20–60 min the very first time). Subsequent uploads are fast.
- **versionCode must increase** for every new upload. Next build → bump `versionCode` to
  `2` in `app/build.gradle.kts` before rebuilding the AAB.
- **Namespace** is still `com.example.recorderproject` internally; the **applicationId**
  `com.meatball.meatrec` is what Play uses, so this is cosmetic and not a blocker.
- **Google Drive sign-in (Cloud Backup):** the OAuth client in Google Cloud Console must be
  registered for package `com.meatball.meatrec` + the **Play app signing** SHA-1 (shown in
  Play Console after you enroll in Play App Signing). Until that's set, Drive sign-in will
  fail with a developer error — but recording/EQ/everything else works fine.
