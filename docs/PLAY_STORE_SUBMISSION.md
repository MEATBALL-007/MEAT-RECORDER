# MEAT REC — Play Store Submission Guide

ทุกอย่างที่ต้องทำเพื่อเผยแพร่แอพ รวมไว้ที่เดียว เช็คทีละข้อ

---

## ข้อมูลแอพ (ค่าที่ต้องใช้บ่อย)

| รายการ | ค่า |
|---|---|
| App name | **MEAT REC** |
| Package / applicationId | `com.meatrec.recorder` |
| versionCode / versionName | `1` / `1.0` |
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` (6.7 MB) |
| Keystore SHA-1 | `87:04:6F:BC:B8:42:AF:2D:45:20:B2:86:78:71:06:7F:DF:F4:51:13` |
| Keystore SHA-256 | `BB:D9:CB:1B:4F:31:71:AE:C4:65:D9:AC:20:48:04:03:30:EF:83:B0:C7:FE:C4:D7:52:96:83:1A:C4:DE:3B:56` |
| Keystore backup | `~/Desktop/MEATREC-KEYSTORE-BACKUP/` |

---

## Assets (พร้อมใช้แล้ว)

| Asset | ไฟล์ | สถานะ |
|---|---|---|
| Hi-res icon 512×512 | `docs/play-assets/icon-512.png` | ✅ พร้อม |
| Feature graphic 1024×500 | `docs/play-assets/feature-1024x500.png` | ✅ พร้อม |
| Privacy policy URL (live) | **https://meatball-007.github.io/meatrec-privacy/** | ✅ LIVE สาธารณะแล้ว |
| Screenshots (≥2) | — | ⏳ ถ่ายจาก emulator/มือถือ |

### วิธีถ่าย screenshots
1. Android Studio → Device Manager → สร้าง Pixel emulator (API 34/35)
2. รันแอพ → เปิดหน้าสวยๆ (record, EQ, waveform, settings)
3. กดปุ่มกล้องด้านข้าง emulator เพื่อ capture
4. ต้องการอย่างน้อย **2 รูป**, กว้าง ≥1080px, อัตราส่วน 16:9 หรือ 9:16

---

## Store listing copy (พร้อม copy-paste)

**Short description (80 อักษร):**
```
Professional field recorder with EQ, trim, pitch shift, transcribe & backup
```

**Full description:**
```
MEAT REC is a professional audio field recorder built for musicians, podcasters,
filmmakers, and field-recording enthusiasts.

RECORDING
• High-resolution capture: 44.1 / 48 / 96 kHz, 16 / 24 / 32-bit float
• Works with any microphone — built-in, USB-C, Bluetooth, or wired headset
• Unprocessed audio source bypasses system DSP for clean, true sound
• Pre-roll buffer captures the 5 seconds before you hit record
• Voice Activity Detection auto-starts recording when it hears your voice

EDITING & PROCESSING
• Real-time EQ with a visual band editor
• Trim recordings with a live waveform view
• Pitch shift without changing duration
• Loudness delivery: render to a target LUFS with true-peak limiting
• Live noise gate, AGC, hi-pass, compressor, and stereo widener

WORKFLOW
• On-device transcription — convert recordings to text
• Google Drive backup — auto-upload your takes
• Scene & take naming, cue points, and production slate
• 1 kHz slate tone for syncing with camera audio

No ads. No analytics. No accounts required. Your recordings stay on your device
unless you choose to back them up.
```

**Category:** Music & Audio
**Tags:** recorder, audio, field recording, podcast, music
**Content rating:** Everyone (no objectionable content)

---

## Data Safety form (Play Console → App content → Data safety)

| คำถาม | คำตอบ |
|---|---|
| Does your app collect or share user data? | **Yes** |
| **Audio** — Voice or sound recordings | Collected: **Yes** · Shared: **No** · Purpose: App functionality · เก็บในเครื่อง / Drive ของผู้ใช้ |
| **Location** — Approximate & Precise | Collected: **Yes (optional)** · Shared: **No** · Purpose: App functionality (file metadata) |
| **Files and docs** | Collected: **Yes** · Shared: **No** · ผู้ใช้ backup ไป Drive ตัวเอง |
| Is all data encrypted in transit? | **Yes** (Google Drive ใช้ HTTPS) |
| Do you provide a way to request data deletion? | **Yes** (ผู้ใช้ลบไฟล์ในเครื่อง/Drive ได้เอง) |
| Analytics? | **No** |
| Crash logs? | **No** |
| Ads / Advertising ID? | **No** |

---

## ลำดับขั้นตอนการส่ง

### ☑ 1. Privacy policy URL — ✅ เสร็จแล้ว (LIVE)
- Host บน public repo แยกต่างหาก: https://github.com/MEATBALL-007/meatrec-privacy
- URL สาธารณะพร้อมใช้: **https://meatball-007.github.io/meatrec-privacy/**
- เอา URL นี้ไปวางใน Play Console → App content → Privacy policy ได้เลย
- (โค้ดหลักยังเป็น private repo — repo นี้มีแค่ไฟล์ privacy policy)
- อยากแก้เนื้อหา: แก้ `index.html` ใน repo meatrec-privacy แล้ว push → Pages อัปเดตอัตโนมัติ

### ☐ 2. Google Cloud OAuth — ✅ เสร็จแล้ว
- OAuth client (Android) ผูกกับ package + SHA-1 แล้ว
- ⚠️ หลังอัปโหลด AAB → เพิ่ม SHA-1 ของ **Play App Signing** อีกตัว
  (Play Console → Setup → App signing → copy SHA-1 → เพิ่มใน Google Cloud Credentials)

### ☐ 3. สร้างแอพใน Play Console
1. https://play.google.com/console → Create app
2. App name = MEAT REC · ภาษา = English · App / Free
3. กรอก declarations (Developer Program Policies, US export laws)

### ☐ 4. กรอก Store listing
- Short + Full description (copy ด้านบน)
- Icon 512×512, Feature graphic 1024×500, Screenshots ≥2
- Category = Music & Audio
- Privacy policy URL (จากข้อ 1)

### ☐ 5. App content (เมนูซ้าย)
- Privacy policy → ใส่ URL
- Data safety → กรอกตามตารางด้านบน
- Content rating → ทำ questionnaire (Everyone)
- Target audience → 13+
- Government apps / Ads = No ads

### ☐ 6. อัปโหลด AAB → Internal testing
1. Testing → Internal testing → Create new release
2. Upload `app-release.aab`
3. เพิ่ม tester email (ตัวเอง) → Save → Review → Rollout
4. ทดสอบบนเครื่องจริงผ่านลิงก์ tester

### ☐ 7. ทดสอบฟีเจอร์บนเครื่องจริง
- ☐ อัดเสียง (ไมค์ในตัว + ไมค์ภายนอก USB/BT)
- ☐ Pitch shift ไม่ crash
- ☐ Trim ตัดไฟล์ได้จริง
- ☐ VAD auto-start
- ☐ Transcription (ดูหมายเหตุด้านล่าง)
- ☐ Google Drive backup (ต้อง login + เป็น test user)

### ☐ 8. เลื่อนขึ้น Production
- หลังเทสต์ผ่าน → Production → Create release → ใช้ AAB เดิม → Review → ส่ง Google review (รอ 1-7 วัน)

---

## หมายเหตุ / ข้อจำกัดที่ควรรู้

- **Transcription** ใช้เทคนิค audio loopback ของ Android SpeechRecognizer ทำงานได้ดีบนหลายเครื่อง
  แต่บางยี่ห้อ (Samsung/Xiaomi รุ่นใหม่) อาจ block → ขึ้น "Audio error"
  ถ้าต้องการความแม่นยำ 100% ทุกเครื่อง อนาคตควรเปลี่ยนเป็น on-device STT library
- **OAuth consent screen** ยังอยู่โหมด Testing → Drive ใช้ได้เฉพาะ test users
  จะเปิดสาธารณะต้อง Publish consent screen (drive.file เป็น sensitive scope อาจถูกขอ verification)
- **versionCode** ต้องเพิ่มขึ้นทุกครั้งที่อัปเดต (1 → 2 → 3...) ไม่งั้น Play reject

---

## คำสั่ง build ที่ใช้บ่อย

```bash
# Build signed release bundle (อัปโหลดอันนี้)
./gradlew bundleRelease
# ผลลัพธ์: app/build/outputs/bundle/release/app-release.aab

# Build signed APK (ทดสอบ sideload)
./gradlew assembleRelease

# ดู SHA-1 ของ keystore อีกครั้ง (รหัสผ่านอยู่ในไฟล์ backup บน Desktop — อย่าใส่ในที่ที่ commit ได้)
keytool -list -v -keystore meatrec-release.jks -storepass '<YOUR_STORE_PASSWORD>' -alias meatrec | grep SHA
```
