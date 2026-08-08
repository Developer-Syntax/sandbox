# COC Auto Farming Bot 🏰⚔️

Bot auto farming Clash of Clans untuk Android tanpa root.
Stack: Kotlin + MediaProjection + AccessibilityService + OpenCV

---

## Cara Setup

### 1. Persiapan Template Images
Buat folder `app/src/main/assets/templates/` dan isi dengan screenshot tombol-tombol COC:

| File | Deskripsi |
|------|-----------|
| `btn_attack.png` | Tombol Attack di home |
| `btn_find_match.png` | Tombol Find a Match |
| `btn_next.png` | Tombol Next (skip base) |
| `btn_end_battle.png` | Tombol End Battle |
| `btn_return_home.png` | Tombol Return Home |
| `btn_okay.png` | Tombol OK generic |
| `battle_result.png` | Header layar Battle Result |
| `troops_ready.png` | Indikator troops siap |
| `builder_available.png` | Ikon builder tersedia |
| `logo_up.png` | Ikon upgrade wall |
| `home_screen.png` | Elemen unik home screen |
| `searching.png` | Layar sedang searching |

**Tips ambil template:**
- Screenshot COC di resolusi 720x1612
- Crop hanya bagian tombol/elemen yang dicari
- Pastikan background minimal (crop ketat)
- Simpan sebagai PNG

### 2. Build & Install
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Aktifkan Accessibility Service
- Buka Settings → Accessibility → Installed Services
- Cari "COC Bot Accessibility" → Aktifkan

### 4. Jalankan
- Buka app COC Bot
- Tap START
- Grant permission Screen Capture
- Bot akan mulai otomatis

---

## Konfigurasi (BotConfig.kt)

```kotlin
BotConfig.maxNextTaps = 5          // Max skip base
BotConfig.troopsPerSide = 5        // Troops per sisi
BotConfig.waitBattleSeconds = 180  // Timeout battle
BotConfig.minGoldToFarm = 100_000  // Min gold untuk farm
BotConfig.autoUpgradeWall = true   // Auto upgrade wall
BotConfig.delayMinMs = 800L        // Min delay anti-bot
BotConfig.delayMaxMs = 2500L       // Max delay anti-bot
```

---

## Arsitektur

```
MainActivity
    ├── BotService (state machine utama)
    │   ├── ScreenCaptureService (MediaProjection screenshot)
    │   ├── AccessibilityBot (gesture tap)
    │   ├── TemplateManager (OpenCV matching)
    │   └── BotLogger (log system)
    └── BotConfig (koordinat & settings)
```

## State Machine

```
IDLE → CHECKING_HOME → CHECKING_RESOURCES
     → WAITING_TROOPS (jika belum siap)
     → UPGRADING_WALL (jika gold cukup)
     → OPENING_ATTACK → FINDING_MATCH
     → SEARCHING → SCOUTING
     → DEPLOYING_TROOPS → WAITING_BATTLE
     → READING_RESULT → RETURNING_HOME
     → (loop kembali ke CHECKING_HOME)
```

---

## ⚠️ Disclaimer

Bot ini dibuat untuk **akun alt/dummy saja**. 
Supercell melarang penggunaan bot — akun utama berisiko di-ban.
Gunakan dengan bijak dan risiko sendiri.
