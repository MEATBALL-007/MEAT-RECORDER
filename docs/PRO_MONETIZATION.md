# MEAT REC Pro — Monetization Setup

ระบบขาย Pro แบบ **จ่ายครั้งเดียว ปลดล็อคถาวร** (ไม่มี subscription) สร้างเสร็จในโค้ดแล้ว
เหลือแค่ตั้งค่า in-app product ใน Play Console + ใส่ราคา

---

## โมเดล

- **Free**: อัดไม่จำกัด, 48kHz/16-bit, mono+stereo, ไมค์ในตัว, เล่น/trim พื้นฐาน, 3 ธีม
- **Pro (จ่ายครั้งเดียว)**: ปลดล็อกทุกอย่าง — รายละเอียดด้านล่าง

ราคาตั้งใน **Play Console** ไม่ใช่ในโค้ด → แอพดึงราคามาแสดงเอง เปลี่ยนได้ทุกเมื่อ
แนะนำ: **฿199** lifetime (โปรเปิดตัว ฿129)

---

## Feature ที่ถูกล็อกไว้หลัง Pro (gating อยู่ใน `RecorderViewModel`)

| Feature | จุด gate |
|---|---|
| 96kHz + 24/32-bit float | `updateSampleRate` / `updateBitDepth` (free cap 48k/16) |
| ไมค์ภายนอก USB/BT/wired | `selectInputDevice` |
| EQ เต็มระบบ | `onEQOpen` |
| Pitch shift | `openPitchShift` |
| Transcription | `requestTranscribe` |
| Cloud backup (Drive) | `toggleCloudBackup` |
| Analysis: A/B, portrait, multi-take, room profiler | `openAbCompareFromSelection` / `openPortrait` / `openMultiTake` / `openRoomProfiler` |

แตะ feature ที่ล็อก → เปิด Paywall (`ProUpgradeSheet`) อัตโนมัติ

---

## ⚙️ ตั้งค่าใน Play Console (ทำครั้งเดียว)

ต้องมี Play Console account + อัปโหลด AAB ขึ้น Internal testing ก่อน (in-app product ต้องการให้แอพมีอยู่ในระบบ)

### 1. เปิด Merchant account
```
Play Console → Setup → Payments profile → สร้าง (กรอกข้อมูลธนาคาร/ภาษี)
```
ต้องมีอันนี้ก่อนถึงจะขายได้

### 2. สร้าง In-app product
```
Play Console → เลือกแอพ → Monetize → Products → In-app products → Create product
```
- **Product ID**: `meatrec_pro_lifetime`  ← **ต้องตรงเป๊ะ** (โค้ดอ้างชื่อนี้ใน BillingManager.PRODUCT_PRO_LIFETIME)
- **Name**: MEAT REC Pro
- **Description**: Unlock all professional features — lifetime, one payment
- **Price**: ฿199 (หรือที่ต้องการ — ตั้ง default price แล้ว Play แปลงทุกสกุลเงินให้)
- **Status**: Active → Save

### 3. License testing (ทดสอบโดยไม่เสียเงินจริง)
```
Play Console → Setup → License testing → ใส่อีเมล Gmail ของผู้ทดสอบ
```
อีเมลในลิสต์นี้จะซื้อแบบ test (ไม่ตัดเงินจริง) — กดซื้อแล้วปลดล็อก Pro ได้เลย

---

## 🧪 ทดสอบก่อนตั้ง Play Console

Debug build มี toggle ลับ: **Settings → MEAT REC PRO → "DEBUG: simulate Pro"**
(เห็นเฉพาะ debug build — release ไม่มี) เปิดเพื่อทดสอบว่า feature ปลดล็อกถูกต้อง

---

## โครงสร้างโค้ด

| ไฟล์ | หน้าที่ |
|---|---|
| `billing/BillingManager.kt` | เชื่อม Play Billing 7.x — query product, purchase, acknowledge, restore |
| `billing/EntitlementStore.kt` | เก็บสถานะ Pro (SharedPreferences แยก — รอด factory reset) |
| `billing/ProFeature.kt` | รายการ feature + free caps |
| `ui/components/ProUpgradeSheet.kt` | Paywall UI (ราคา dynamic จาก Billing) |
| `RecorderViewModel.requirePro()` | helper gate — เปิด paywall ถ้ายังไม่ Pro |

---

## ⚠️ ข้อควรรู้

- **Source of truth คือ Play** — แอพ query `queryPurchasesAsync` ทุกครั้งที่เปิด ถ้าผู้ใช้ refund Play จะแจ้ง แอพก็ปลด Pro ออกอัตโนมัติรอบถัดไป
- **Restore purchase** — ผู้ใช้เปลี่ยนเครื่อง/ลงแอพใหม่ กด Restore ใน paywall หรือ settings → ดึงสิทธิ์กลับจาก Google account เดิม
- ราคาในแอพจะ**ว่าง**จนกว่าจะตั้ง product ใน Play Console เสร็จ (ปุ่มจะขึ้น "See Pro features" แทนราคา) — เป็นเรื่องปกติ
