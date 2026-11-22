# KaraokeLink

[ไทย](README.md) · [English](README.en.md) · [简体中文](README.zh-CN.md) · [Tiếng Việt](README.vi.md) · [Bahasa Indonesia](README.id.md)

Jadikan ponsel mikrofon nirkabel dan putar karaoke di ponsel lain melalui Wi-Fi yang sama. Satu pemutar mendukung **dua mikrofon sekaligus**. Pasang APK yang sama pada semua ponsel. Memerlukan Android 9 atau lebih baru.

**[Unduh rilis terbaru](https://github.com/saycodedevelop/FREEMIC/releases/latest)** · [Catatan perubahan multibahasa](CHANGELOG.md)

## Tampilan

![Pilih pemutar atau mikrofon](docs/screenshots/home.png)

[Mikrofon](docs/screenshots/microphone.png) · [Mixer](docs/screenshots/mixer.png) · [Panduan](docs/screenshots/getting-started.png)

Pratinjau dirender di Android dengan status simulasi dan teks Thai. [Panduan pengguna (Thai)](docs/USER_GUIDE.md).

## Mulai

1. Pasang APK pada pemutar dan setiap mikrofon; hubungkan semuanya ke Wi-Fi yang sama.
2. Pada ponsel yang tersambung ke speaker, pilih **Buka pemutar musik**, lalu cari lagu atau tempel tautan HTTPS.
3. Pada setiap ponsel untuk bernyanyi, pilih **Gunakan ponsel ini sebagai mikrofon**, izinkan akses mikrofon/notifikasi, lalu pilih pemutar yang sama.

Panduan awal dapat dilewati dan dibuka lagi melalui tombol **?** di setiap layar.

## Temukan pemutar dan hubungkan dua mikrofon

Pencarian memakai Android NSD/mDNS bersama siaran UDP dan mengonfirmasi pemutar lewat probe unicast. Hasil menampilkan merek/model dan IP. Dua mikrofon yang terhubung ditampilkan sebagai **2/2**; menghentikan satu tidak memutus yang lain.

Jika pemutar tidak ditemukan, ketuk status pemutar untuk melihat IP, lalu pilih **Hubungkan lewat IP** pada mikrofon. Wi-Fi tamu, isolasi AP/klien, beberapa hotspot, dan VPN dapat memblokir komunikasi. Jaringan harus mengizinkan perangkat saling mengakses.

## Bernyanyi saat layar mikrofon mati

Mikrofon yang terhubung tetap mengirim audio saat layar mati atau aplikasi lain dibuka. Hentikan lewat notifikasi atau tombol **Hentikan mikrofon** di aplikasi. Perekaman tidak dimulai ulang otomatis setelah ponsel dimulai ulang atau proses dihentikan.

Pemutar menjeda audio saat berada di latar belakang atau layar mati; biarkan layar pemutar terbuka.

## Suara dan jaringan

- Setiap mikrofon memiliki gain dan efek. Noise gate mati secara bawaan dan dapat diaktifkan di pengaturan lanjutan.
- Ada pengurutan ulang paket, buffer adaptif, pelemahan suara saat paket hilang sesaat, dan kompensasi pergeseran clock.
- Gunakan speaker kabel atau USB untuk bernyanyi langsung. Bluetooth menambah jeda sesuai perangkat dan dapat tersendat.
- Volume musik HTML5 hanya dapat diatur pada situs yang mendukungnya. Android mencampur musik dan audio mikrofon.
- RTT dan antrean tidak mengukur jeda dari mulut ke speaker. Penambahan jeda tidak menghilangkan jeda Bluetooth.
- Audio mikrofon di LAN saat ini belum dienkripsi; gunakan jaringan tepercaya.

## Bahasa

Tersedia bahasa Thai, Inggris, Mandarin Sederhana, Vietnam, dan Indonesia. Aplikasi mengikuti bahasa ponsel dengan bahasa Inggris sebagai cadangan. Pilih **Bahasa** di beranda atau gunakan pengaturan bahasa aplikasi Android 13 ke atas. Ubah bahasa sebelum memulai sesi. Catatan rilis mencakup kelima bahasa.

## Instalasi dan pengembangan

Berkas resmi adalah `KaraokeLink-<version>.apk` yang ditandatangani dengan kunci rilis tetap, beserta `SHA256SUMS.txt`. Versi pratinjau lama memakai kunci berbeda sehingga perlu dihapus sekali; rilis resmi berikutnya dapat diperbarui langsung.

Gunakan JDK 17 atau 21, Android SDK 35, NDK 27.0.12077973, dan CMake 3.22.1. Atur `JAVA_HOME` dan `ANDROID_HOME`, lalu jalankan:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

[Arsitektur (Thai/Inggris)](docs/ARCHITECTURE.md) · [Protokol LAN (Thai)](docs/PROTOCOL.md) · [Pengujian perangkat (Thai/Inggris)](docs/TESTING.md) · [Proses rilis (Inggris)](docs/RELEASING.md)

## Hotspot pemutar dan latensi rendah

Pada pemutar, buka **Pengaturan suara → Hotspot Wi-Fi pemutar → Buat Wi-Fi dari pemutar**. Berikan izin lalu hubungkan setiap mikrofon ke nama Wi-Fi dan kata sandi yang ditampilkan. Kembali ke aplikasi mikrofon dan pilih pemutar. Dua mikrofon tetap didukung. Aplikasi melepas hotspot buatannya saat keluar dari peran pemutar atau Activity dihancurkan.

Hotspot lokal tidak berbagi Internet; musik daring mungkin memerlukan data seluler dan beberapa ponsel memutus Wi-Fi sebelumnya. Gunakan pengaturan hotspot sistem untuk berbagi Internet atau memilih 5 GHz.

**Mode latensi rendah** aktif secara bawaan. Buffer penerima awal berkurang dari 30 menjadi 15 ms dan buffer keluaran dari empat menjadi dua burst; buffer tetap menyesuaikan jitter dan ukuran callback. Angka ini bukan hasil pengukuran jeda mulut ke speaker. Matikan mode jika suara berderak atau terputus. Saat membandingkan, atur tambahan jeda ke 0 dan matikan echo/reverb mikrofon. Hotspot dapat mengurangi variasi jaringan tetapi tidak menghilangkan jeda perangkat keras audio.

[Panduan pengujian jeda (Thai)](docs/LATENCY.md)
