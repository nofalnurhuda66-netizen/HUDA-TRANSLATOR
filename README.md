# HUDA TRANSLATOR — starter project

Aplikasi Android Kotlin dengan komponen antarmuka HTML, MediaProjection, ML Kit Text Recognition, dan ML Kit Translation (English → Indonesian).

## Persyaratan
- Android Studio versi stabil terbaru
- JDK 17
- Android SDK 35
- Perangkat Android 8.0+ untuk pengujian

## Membuka proyek
1. Ekstrak ZIP.
2. Android Studio → Open → pilih folder `HUDA_TRANSLATOR`.
3. Tunggu Gradle Sync selesai dan unduh dependensi.
4. Hubungkan perangkat Android dengan USB debugging aktif.
5. Tekan Run.

## Cara menggunakan
1. Buka HUDA TRANSLATOR.
2. Tekan izin tampil di atas aplikasi dan izinkan.
3. Tekan mulai penerjemahan layar.
4. Setujui dialog Android untuk berbagi/menangkap layar.
5. Overlay akan muncul. Teks Inggris yang terdeteksi akan diterjemahkan ke Bahasa Indonesia.
6. Tekan Tutup penerjemah atau hentikan layanan untuk mengakhiri sesi.

## Catatan penting
- Ini adalah starter project yang perlu diuji pada perangkat target; bukan APK siap instal.
- Implementasi awal menerjemahkan English → Indonesian. Pemilihan bahasa tambahan belum dibuat.
- OCR memproses gambar layar secara berkala; aplikasi sensitif atau layar aman (FLAG_SECURE) mungkin tidak dapat ditangkap.
- Model terjemahan ML Kit perlu diunduh saat pertama kali digunakan dan membutuhkan koneksi internet.
- Android 13+ mungkin meminta izin notifikasi. Android 14+ menerapkan pembatasan MediaProjection; mulai sesi baru melalui dialog sistem setiap kali.
- Untuk distribusi publik, tambahkan alur izin notifikasi runtime, tombol pause/resume, pengaturan bahasa, penanganan rotasi/resolusi, dan uji kompatibilitas perangkat.
- Jangan gunakan aplikasi untuk membaca informasi pribadi orang lain. Pastikan pengguna perangkat menyetujui sesi perekaman layar.
