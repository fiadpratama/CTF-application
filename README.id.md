# CTF Application

[English](README.md) | Bahasa Indonesia

---

Tantangan praktis *reverse engineering* dan keamanan jaringan Android yang mencakup analisis statis, instrumentasi dinamis, serta intersepsi protokol.

## Gambaran Proyek

CTF ini menyimulasikan skenario penilaian keamanan aplikasi seluler (*mobile application security assessment*), menguji keterampilan dalam analisis statis, instrumentasi dinamis, dan intersepsi protokol jaringan. Aplikasi ini menerapkan arsitektur verifikasi sisi server (*server-side verification*) di mana flag hanya akan dirilis setelah seluruh rangkaian eksploitasi (*exploit chain*) diselesaikan dengan benar.

## Arsitektur

- **Klien (Android):** Java dengan komponen native C++ (JNI) (Android 8.0+ / API 26+, menargetkan API 34), diperkuat dengan *timing obfuscation*, enkoding kunci sekunder, serta deteksi *anti-hooking* pasif.
- **Security Engine:** Konstanta native diobfuskasi saat waktu kompilasi (*compile-time*) untuk menahan analisis string statis sederhana.
- **Backend (Serverless):** REST API Node.js yang di-deploy pada Vercel Edge Network. Menegakkan versi minimum aplikasi klien dan menolak build yang usang.
- **Kriptografi:** Enkripsi *end-to-end* AES-256-GCM untuk transmisi payload antara klien dan server.

## Alat & Prasyarat

Alat yang direkomendasikan untuk tantangan ini:

- **Dekomplikasi & Analisis Statis:** JADX, Apktool, atau Ghidra.
- **Instrumentasi Dinamis:** Frida atau Objection.
- **Intersepsi & Proksi Trafik:** Burp Suite atau OWASP ZAP. Perlu diperhatikan bahwa Android modern tidak mempercayai sertifikat CA yang dipasang pengguna secara default, sehingga intersepsi HTTPS memerlukan konfigurasi tambahan.

## Spesifikasi Tantangan

Tujuannya adalah mengekstrak flag yang valid dari server jarak jauh. Penilaian terdiri dari tiga tahap berurutan.

### Tahap 1: Ekstraksi Kriptografi

Klien Android mengirimkan payload terenkripsi ke backend. Anda perlu mengidentifikasi apa yang dibutuhkan klien agar pertukaran data ini berhasil.

*Catatan: Investigasi terhadap layer native terkompilasi (`libnative-crypto.so`) diperlukan. Tidak semua yang Anda temukan di sana adalah apa yang terlihat.*

### Tahap 2: Manipulasi Protokol

Menyelesaikan Tahap 1 akan membuka langkah validasi di sisi server yang melibatkan nilai yang belum Anda miliki.

### Tahap 3: Verifikasi Akhir

Kirimkan flag yang berhasil diekstrak melalui aplikasi Android untuk menyelesaikan asesmen dan membuka layar Hall of Fame.

## Memulai

1. Unduh file `.apk` terbaru dari rilis repositori. Build yang usang dapat ditolak oleh server.
2. Pasang pada emulator Android atau perangkat uji fisik.
3. Konfigurasikan lingkungan proksi Anda jika menggunakan Burp Suite/ZAP (lihat catatan di atas mengenai intersepsi HTTPS).
4. Mulai analisis Anda.

### Membangun dari Kode Sumber (Opsional)

Jika Anda ingin meng-host atau memodifikasi tantangan ini secara mandiri:
```bash
# 1. Siapkan konfigurasi rahasia native lokal
cp android_client/app/src/main/cpp/native-secrets.properties.example android_client/app/src/main/cpp/native-secrets.properties

# 2. Bangun APK rilis menggunakan Gradle Wrapper
cd android_client
./gradlew assembleRelease      # Linux / macOS
gradlew.bat assembleRelease    # Windows
```
Task pembuat kunci native otomatis berjalan sebelum kompilasi. APK yang dihasilkan akan tersedia di `android_client/app/build/outputs/apk/release/app-release.apk`.

## Privasi & Pengelolaan Data

Proyek ini adalah tantangan keamanan mandiri dan tidak mengumpulkan informasi pribadi selain penanganan alamat IP yang dijelaskan di bawah. Sebuah penghitung anonim jumlah penyelesaian juga disimpan; data ini tidak memuat informasi pribadi apa pun.

- **Alamat IP:** Backend memproses alamat IP klien sementara secara eksklusif untuk *rate limiting* dan pencegahan penyalahgunaan. Data disimpan dalam cache Redis sementara dan kedaluwarsa secara otomatis (maksimal 30 menit bagi klien yang terkena pembatasan).
- **Trafik Jaringan:** Seluruh komunikasi ditransmisikan melalui HTTPS. Hanya token tantangan dan flag yang dievaluasi; peserta tidak boleh memasukkan kredensial pribadi yang sensitif.
- **Log Infrastruktur:** API dihosting di Vercel Edge Network, yang mungkin menyimpan log transit HTTP standar sesuai dengan kebijakan infrastruktur Vercel.

## Verifikasi Keaslian Rilis

APK rilis resmi (v1.4.0 dan seterusnya) ditandatangani menggunakan kunci penandatanganan pengembang khusus. Anda dapat memverifikasi keaslian dan integritas berkas secara independen sebelum memasangnya:

### 1. Sidik Jari Sertifikat Penandatangan (Keaslian / Authenticity)
Pastikan bahwa APK benar-benar dibuat dan ditandatangani oleh pembuat repositori:
```
SHA-256: 11919c358daf9c9f56da77617284fb99cc402fc789242fe8c75c674a3c551a62
```
Untuk memverifikasi menggunakan build-tools Android SDK:
```bash
apksigner verify --print-certs app-release.apk
```

### 2. Checksum Berkas (Integritas / Integrity)
Untuk memastikan file APK yang diunduh tidak rusak saat proses transmisi, cocokkan hash SHA-256 berkas dengan *asset digest* yang dipublikasikan pada halaman [GitHub Releases](../../releases).

---

*Disclaimer: Repositori ini ditujukan secara ketat untuk tujuan edukasi, riset keamanan, dan praktik peretasan etis. Serangan otomatis yang tidak sah, termasuk Denial of Service (DoS) atau brute-forcing terhadap infrastruktur backend yang aktif, dilarang keras.*
