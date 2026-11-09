<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Fork yang disempurnakan dari <a href="https://github.com/dead8309/Kizzy">Kizzy</a> — manajer Rich Presence Discord untuk Android, ditulis dalam Kotlin.</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README dalam Bahasa Indonesia](https://img.shields.io/badge/README-dalam%20Bahasa%20Indonesia-blue?style=for-the-badge)](README_ID.md)

</div>
</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** adalah fork pribadi dari [Kizzy](https://github.com/dead8309/Kizzy) oleh dead8309. Penambahan khusus fork ditandai dengan ⭐ di bawah ini.

## Fitur

- [x] Tombol yang dapat diklik
- [x] Mendeteksi aplikasi yang sedang berjalan
- [x] Mendeteksi media yang sedang diputar
- [x] Timestamp opsional
- [x] Status kustom
- [x] Simpan/muat konfigurasi kehadiran
- [x] Tema Material You
- [x] Terjemahan
- [x] Pengaturan mudah
- [x] Spoof Konsol RPC
- [x] Buat konfigurasi kustom dengan gambar dan tautan Anda sendiri
- [x] Pratinjau RPC di dalam aplikasi itu sendiri
- [x] Berjalan di latar belakang bahkan saat layar mati
- [x] Dukungan GIF
- [x] Dukungan URL eksternal (artinya Anda dapat memberikan URL yang mengarah ke gambar di web dan Discord akan menampilkannya!)
- [x] Gunakan Gambar dari Galeri
- [x] ⭐ Spoofer Platform (Desktop / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ ID Aplikasi Kustom — gunakan ikon dan nama aplikasi Discord Anda sendiri
- [x] ⭐ Placeholder templat tambahan (`{{media_album}}`, `{{battery}}`, `{{playback}}` dan lainnya)
- [x] ⭐ Pemilih mode timestamp (Bawaan Media/App · Waktu saat ini · ms kustom)
- [x] ⭐ Desain ulang layar beranda — kartu lebar penuh dengan tombol aktif/nonaktif
- [x] ⭐ Gateway sambung ulang otomatis dengan backoff eksponensial (tidak mati lagi setelah 1-2 jam)
- [x] ⭐ Perulangan deteksi tak hancur — galat jaringan tidak lagi menghentikan deteksi aplikasi/game
- [x] ⭐ Polling perpindahan aplikasi 2 detik (sebelumnya 5 dtk) untuk deteksi game yang lebih cepat

## Apa yang baru di fork ini

Semua penambahan ada di layar **Experimental RPC** dan layar beranda.

### ⭐ Spoofer Platform
- Pilih platform tempat kehadiran Anda terhubung — **Desktop, Web, Android, iOS, Xbox, Playstation, VR** (di-porting dari platform yang digunakan platformSpoofer Equicord).
- Bekerja pada tingkat `IDENTIFY` gateway (status klien), sehingga diambil dengan benar oleh plugin seperti **platformindicators**.
- Diterapkan secara **langsung** — layanan memulai ulang sendiri, tidak perlu sakelar manual.

### ⭐ ID Aplikasi Kustom (ikon sendiri)
- Pemilih **Built-in / Custom**. Built-in mempertahankan ikon default; Custom menampilkan kolom tempat Anda menempelkan **Application ID** Discord Anda sendiri, sehingga aktivitas menampilkan ikon dan nama **Anda**.

### ⭐ Placeholder templat tambahan
Selain yang asli, dengan pelengkapan otomatis:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — persentase baterai
- `{{playback}}` — status pemutaran (▶ / ⏸)

### ⭐ Pemilih mode timestamp
- **Media / App (default)** — timestamp dari trek/aplikasi saat ini.
- **Sama seperti waktu saat ini** — Discord menampilkan waktu saat ini sebagai waktu berlalu (mis. pada 15:30 → menampilkan "15:30 berlalu"). Logika yang sama dengan customRPC Vencord.
- **Kustom** — ketik **Timestamp Mulai / Berakhir (dalam milidetik)** Anda sendiri.

### ⭐ Desain ulang layar beranda
- Kartu fitur kini berupa tumpukan vertikal kartu lebar penuh.
- **Tombol sakelar aktif/nonaktif pada setiap kartu**, sehingga Media / Custom / Experimental RPC dapat dimulai dan dihentikan langsung dari layar beranda.

### ⭐ Ketahanan Gateway (di-porting dari [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- Sambung ulang otomatis dengan backoff eksponensial pada pemutusan tak terduga.
- Melanjutkan sesi bila memungkinkan; kembali ke identifikasi baru setelah 3 kali percobaan gagal.
- Kode penutupan fatal (4004, 4010–4014) tetap berhenti secara permanen.
- Batas 60 detik pada waktu tunggu pembaruan kehadiran (sebelumnya perulangan tak terbatas).
- Perulangan deteksi kini dibungkus dalam try/catch — satu galat jaringan tidak lagi menghentikan deteksi perpindahan aplikasi hingga dimulai ulang secara manual.
- Polling perpindahan aplikasi: **2 dtk** (sebelumnya 5 dtk).
- Perbaikan: beralih antara dua aplikasi yang diaktifkan kini memperbarui kehadiran segera.

### Perbaikan
- Memperbaiki kris saat **"Bagikan log"** (Otoritas FileProvider kini mengikuti ID paket).
- Memperbaiki kris saat beralih fitur RPC dari layar beranda.

## Persyaratan Sistem
- OS: Android 8.1 hingga 16
- RAM: Minimal 3 GB

## Unduh
Dapatkan `app-release.apk` bertandatangan terbaru dari halaman [**Releases**](../../releases/latest).

**Kizzy Enhanced** menggunakan ID paket **`com.my.kizzy.enhanced`**, sehingga dapat diinstal berdampingan dengan Kizzy asli jika Anda memilikinya.

> [!WARNING]
> Aplikasi ini menggunakan koneksi Discord Gateway. Gunakan dengan risiko Anda sendiri.

## Build
> Prasyarat: Android Studio, serta pemahaman tentang Gradle, Kotlin, dan Jetpack Compose.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Buka proyek di Android Studio, impor, lalu Build dan Run. Build rilis dihasilkan secara otomatis oleh alur kerja GitHub Actions **Build Release Apk**.

## Kredit
✨ [Kizzy](https://github.com/dead8309/Kizzy) untuk proyek Kizzy asli.

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) untuk perbaikan ketahanan gateway.

✨ [Read You](https://github.com/Ashinch/ReadYou) dan [Seal](https://github.com/JunkFood02/Seal) untuk Komponen UI.

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) untuk data game Nintendo dan Wii U.

✨ [Logra](https://github.com/wingio/Logra) untuk UI log.

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) untuk data game Xbox.

✨ [Monet](https://github.com/Kyant0/Monet) untuk palet Material3.

## Lisensi
**Kizzy** adalah proyek sumber terbuka di bawah Lisensi Sumber Terbuka GNU GPL 3.0, yang memungkinkan Anda menggunakan, mereferensikan, dan memodifikasi kode sumber secara gratis, tetapi tidak mengizinkan kode yang dimodifikasi dan diturunkan untuk didistribusikan dan dijual sebagai perangkat lunak komersial sumber tertutup.
