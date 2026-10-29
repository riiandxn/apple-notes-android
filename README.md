# Apple Notes untuk Android

Aplikasi Android sederhana untuk membuka **Apple Notes** (Catatan) dengan login **Apple ID**.
Catatan yang dibuat di iPhone/Mac muncul di sini, dan sebaliknya — sinkron lewat iCloud.

## Cara kerja

Apple tidak menyediakan API publik untuk Notes, jadi aplikasi ini menampilkan
**iCloud Notes resmi** (`https://www.icloud.com/notes/`) di dalam WebView yang
sudah disesuaikan:

- Langsung membuka Notes saja (tanpa Mail, Photos, dll.).
- Login Apple ID + verifikasi dua langkah ditangani langsung oleh Apple.
  Aplikasi tidak pernah membaca atau menyimpan kata sandi.
- Sesi login disimpan, jadi tidak perlu login ulang setiap membuka aplikasi.
- Link di luar Apple/iCloud dibuka di browser.
- Upload lampiran (gambar/file) dan download lampiran.
- Mengikuti mode gelap/terang sistem.
- Menu (tombol ⋮ di pojok kanan bawah): **Muat ulang**, **Mode desktop**
  (tampilan dengan sidebar folder, cocok untuk tablet), **Keluar dari Apple ID**.
- Tampilan "Tidak ada koneksi" dengan tombol coba lagi.

## Syarat

- Android 8.0 (API 26) ke atas.
- Di iPhone/Mac: **Settings → [nama] → iCloud → Notes** harus aktif.
  Catatan yang hanya tersimpan "On My iPhone" tidak ikut ke iCloud.
- Catatan yang dikunci (locked) bisa dibuka dengan kata sandi catatan,
  sesuai dukungan iCloud web.

## Build APK

### Opsi 1 — Android Studio
1. Buka folder ini di Android Studio.
2. Tunggu Gradle sync, lalu **Build → Build APK(s)**.
3. APK ada di `app/build/outputs/apk/`.

### Opsi 2 — Command line (butuh Android SDK + JDK 17)
```bash
./gradlew assembleRelease
```
Hasil: `app/build/outputs/apk/release/app-release.apk`.

### Opsi 3 — GitHub Actions (tanpa install apa pun)
Push folder ini ke repository GitHub. Workflow `.github/workflows/build.yml`
akan membuat APK otomatis; unduh dari tab **Actions → Build APK → Artifacts**.

> APK release ditandatangani dengan debug key agar langsung bisa dipasang.
> Ganti dengan keystore sendiri sebelum dipublikasikan.

## Catatan

Aplikasi ini tidak berafiliasi dengan Apple. "Apple", "iCloud", dan "Notes"
adalah merek dagang Apple Inc. Jika suatu saat Apple mengubah situs iCloud,
tampilan bisa berubah karena kontennya langsung dari Apple.
