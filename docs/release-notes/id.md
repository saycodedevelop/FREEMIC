Mikrofon nirkabel dan pemutar karaoke untuk ponsel Android pada Wi-Fi yang sama.

### Perubahan

{changes}

### Unduh dan pasang

- Unduh `KaraokeLink-{version}.apk` dari berkas di bawah. Memerlukan Android 9 atau lebih baru.
- Pasang APK yang sama pada pemutar dan semua mikrofon. Satu pemutar mendukung dua mikrofon sekaligus.
- Versi pratinjau lama memakai kunci tanda tangan berbeda sehingga perlu dihapus sekali; rilis resmi berikutnya dapat diperbarui langsung.

### Validasi

Alur penerbitan memeriksa build/Lint Android, antarmuka pemutar, layanan mikrofon latar depan, pencarian perangkat dan dua mikrofon, serta pengujian C++ dengan sanitizer dan simulasi jitter/pergeseran clock.
Kualitas Bluetooth, pengelolaan baterai, dan audio bersamaan masih perlu diuji pada perangkat nyata.

Gunakan `SHA256SUMS.txt` untuk memverifikasi integritas APK.
