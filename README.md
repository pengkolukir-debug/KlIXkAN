# AutoKlix

Aplikasi automasi klik/gulir/ketik untuk Android, dibuat berbasis **AccessibilityService**
dan **floating overlay controller**. Dibuat untuk mempermudah tugas berulang.

Copyright © cowalskiiw2026

## Fitur utama
- Titik **klik sekali**, **tekan tahan (durasi custom)**, **gulir (swipe fleksibel, titik awal→akhir, ada garis panduan)**, dan **bot ketik huruf acak (autofill 2 huruf, tanpa pengulangan sampai variasi habis)**.
- Titik klik bernomor urut otomatis sesuai urutan pembuatan (bisa diedit ulang urutannya di editor preset).
- Setiap titik punya **delay** (detik/milidetik) sebelum titik berikutnya dieksekusi.
- **Menu melayang (floating controller)**: hanya ikon (tanpa kotak/background), bisa digeser bebas, muncul di atas aplikasi lain, punya tombol Start/Stop, tambah titik (semua jenis), hapus titik, dan pengaturan menu itu sendiri (ukuran, transparansi, tutup).
- **Preset**: simpan/edit/hapus/tambah, atur jumlah pengulangan & durasi maksimum pengulangan (menit/jam), lalu tinggal pilih preset untuk dijalankan.
- Pengaturan ukuran & transparansi **titik klik** dan **menu melayang** bisa diatur terpisah di menu aplikasi.

## Izin yang dibutuhkan
- `BIND_ACCESSIBILITY_SERVICE` — untuk mensimulasikan tap/long-press/swipe & isi teks di aplikasi lain.
- `SYSTEM_ALERT_WINDOW` (Tampil di atas aplikasi lain) — untuk menu melayang & titik-titik overlay.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — agar bot tetap berjalan stabil.
- `POST_NOTIFICATIONS` (Android 13+) — notifikasi status bot berjalan.

## Cara build APK otomatis lewat GitHub (tanpa install apa-apa)
Repo ini sudah dilengkapi `.github/workflows/build.yml`. Setelah kode di-push ke branch **main**:
1. Buka tab **Actions** di halaman repo GitHub kamu.
2. Tunggu workflow **"Build AutoKlix APK"** selesai jalan (bulat hijau centang ✅), biasanya 3-6 menit.
3. Klik hasil run tersebut → di bagian bawah ada **Artifacts** → download `AutoKlix-debug-apk`.
4. Ekstrak zip-nya, kamu akan dapat file `app-debug.apk` → pindahkan ke HP → install (aktifkan dulu "Izinkan sumber tidak dikenal" di HP saat instal).
5. Jika belum ada tab Actions yang jalan otomatis, buka tab Actions → pilih workflow ini → klik **"Run workflow"** untuk memicu manual.

> APK hasil build ini adalah versi **debug** (belum ditandatangani untuk rilis Play Store), tapi sudah bisa langsung diinstal & dipakai di HP kamu sendiri.

## Cara build manual lewat Android Studio (opsional, kalau mau edit kode)
1. Buka folder ini di **Android Studio** (Hedgehog/Iguana ke atas).
2. Biarkan Android Studio men-generate Gradle Wrapper otomatis (atau jalankan `gradle wrapper` bila punya Gradle terinstall) — file `gradlew`/`gradlew.bat` sengaja tidak disertakan karena butuh binari; Android Studio akan membuatnya otomatis saat sync pertama kali.
3. Sync Gradle, lalu jalankan ke perangkat/emulator (`minSdk 24`, `targetSdk 34`).
4. Setelah install, aktifkan izin **Accessibility** dan **Tampil di atas aplikasi lain** melalui tombol yang tersedia di layar utama aplikasi (akan mengarahkan otomatis ke halaman pengaturan sistem).
5. Untuk upload ke GitHub: buat repo baru, lalu push seluruh isi folder ini apa adanya (`git init && git add . && git commit -m "init" && git remote add origin <url> && git push -u origin main`).

## Struktur singkat
```
app/src/main/java/com/cowalskiiw2026/autoklix/
 ├─ model/      -> data class ClickPoint, Preset, PointType, dll
 ├─ data/       -> Room database (PresetEntity, ClickPointEntity, DAO, Repository)
 ├─ service/    -> AutoKlixAccessibilityService (eksekusi gesture), FloatingControllerService, BotEngine
 ├─ overlay/    -> Floating menu, marker titik, garis panduan swipe
 ├─ ui/         -> MainActivity (daftar preset), PresetEditorActivity, SettingsActivity, PointConfigActivity
 └─ util/       -> RandomTextGenerator, PrefsManager, PermissionUtils
```
