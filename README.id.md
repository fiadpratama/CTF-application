# CTF Application

[English](README.md) | Bahasa Indonesia

---

**CTF Application** adalah tantangan asesmen keamanan aplikasi Android *end-to-end*. Proyek ini dirancang untuk menguji kemampuan praktis dalam *reverse engineering* aplikasi seluler, analisis biner native, inspeksi kriptografi payload, serta rekonstruksi protokol klien-server.

## Gambaran Sistem

- **Klien Seluler:** Aplikasi Android (`Android 8.0+ / API 26+`, menargetkan `API 36` dengan dukungan *16 KB page-size*) yang memadukan antarmuka Java dengan modul kriptografi native C++ (JNI).
- **Backend Verifikasi:** Layanan REST Node.js modular yang menegakkan kompatibilitas versi klien, validasi sesi berbasis *state*, serta mitigasi penyalahgunaan otomatis.
- **Keamanan Transportasi:** Enkripsi simetris terautentikasi yang melindungi payload komunikasi klien-server melalui HTTPS.

## Prasyarat & Perangkat

- **Lingkungan Target:** Emulator atau perangkat fisik Android 8.0+ (API 26+).
- **Analisis Statis:** Dekompilator dan *disassembler* untuk memeriksa *bytecode* Java serta pustaka native (misalnya JADX, Apktool, Ghidra).
- **Analisis Dinamis:** Kerangka kerja instrumentasi *runtime* untuk mengamati alur eksekusi dan memori (misalnya Frida, Objection).
- **Inspeksi Jaringan & Protokol:** Proksi intersepsi HTTP atau lingkungan pembuatan skrip (misalnya Python, Burp Suite, OWASP ZAP) untuk memeriksa header, kode status, dan payload terenkripsi.

## Memulai

1. **Unduh APK Rilis:** Unduh berkas `.apk` resmi terbaru dari halaman [GitHub Releases](https://github.com/fiadpratama/CTF-application/releases). Versi klien yang usang akan ditolak oleh server dengan respons `HTTP 426 Upgrade Required`.
2. **Verifikasi Integritas Paket:** Pastikan keaslian sertifikat penandatangan dan *checksum* berkas menggunakan langkah pada bagian [Verifikasi Keaslian Rilis](#verifikasi-keaslian-rilis).
3. **Mulai Investigasi:** Pasang APK pada lingkungan uji Anda dan telusuri alur verifikasi mulai dari *handshake* awal hingga klaim flag akhir.

> **Mode Asesmen:** Meskipun repositori ini bersifat *open-source* sebagai referensi arsitektur, kunci kriptografi produksi dan kode otorisasi tidak disimpan di dalam kontrol versi. Untuk pengalaman investigasi yang utuh, perlakukan APK rilis sebagai target *black-box*.

<details>
<summary>Membangun dari Kode Sumber (Opsional untuk Pengembangan Lokal)</summary>

**Prasyarat:** JDK 17, Python 3 (dieksekusi otomatis oleh task Gradle `preBuild` via `generateNativeKeys`), Android SDK (`compileSdk 36`, `buildToolsVersion 36.0.0`, `NDK 26.1.10909125`, `CMake 3.22.1`), serta Gradle Wrapper bawaan (`gradlew` / `gradlew.bat`).

Untuk mengompilasi klien Android secara lokal menggunakan placeholder kunci *dummy* 32-byte:

```bash
# 1. Buat berkas rahasia native lokal dari templat dummy 32-byte (POSIX / PowerShell)
cp android_client/app/src/main/cpp/native-secrets.properties.example android_client/app/src/main/cpp/native-secrets.properties
# Windows CMD: copy android_client\app\src\main\cpp\native-secrets.properties.example android_client\app\src\main\cpp\native-secrets.properties

# 2. Kompilasi APK rilis melalui Gradle Wrapper
cd android_client
./gradlew assembleRelease      # Linux / macOS
gradlew.bat assembleRelease    # Windows
```

- **Penandatanganan Lokal (*Local Signing*):** Apabila berkas `android_client/keystore.properties` tidak ditemukan, Gradle otomatis menggunakan konfigurasi *debug signing* bawaan Android sehingga kompilasi lokal langsung berhasil. Untuk menandatangani dengan *keystore* kustom, buat `android_client/keystore.properties` berisi `storeFile`, `storePassword`, `keyAlias`, dan `keyPassword`.
- **Memasangkan dengan Server Lokal:** APK hasil build lokal menanamkan kunci dummy dari `native-secrets.properties` dan menargetkan `https://server-umber-eta.vercel.app/api/vault` di `VaultApiClient.java`. APK ini tidak akan terautentikasi ke server produksi kecuali Anda mengubah `BASE_URL` dan menjalankan `server/` (`Node.js >= 20.0.0`) dengan environment variable yang selaras (`E2EE_KEY`, `BACKDOOR_CODE`, `SECRET_MULTIPLIER`, `JWT_SECRET`, serta kredensial Upstash Redis).
- **Lokasi Keluaran:** `android_client/app/build/outputs/apk/release/app-release.apk`
</details>

## Tahapan Asesmen & Panduan Bertingkat

Tantangan ini terdiri atas tiga tahap berurutan yang saling terhubung dalam satu rantai bukti. Buka bagian petunjuk di bawah ini hanya jika Anda membutuhkan arahan investigasi.

### Tahap 1: Handshake & Ekstraksi Kriptografi

Klien menyusun dan mengenkripsi payload *handshake* awal, namun ditolak oleh server saat dijalankan melalui tombol bawaan antarmuka. Temukan perbedaan antara payload default yang dikirim klien dan kontrak validasi yang diminta backend.

<details>
<summary>Petunjuk untuk Tahap 1</summary>

- **Orientasi:** Telusuri alur eksekusi mulai dari *handler* tombol utama di Java, fungsi kriptografi pembantu, hingga batas pemanggilan JNI C++. Bandingkan daftar fungsi yang diekspor oleh pustaka native dengan fungsi yang benar-benar dipanggil oleh lapisan Java.
- **Metode:** Jangan hanya mengandalkan teks polos yang terlihat pada *dump* statis biner. Bandingkan hasil dekompilasi statis dengan nilai kembalian saat *runtime*, serta perhatikan pemeriksaan lingkungan yang dapat mengubah hasil apabila proses *tracing* aktif terdeteksi.
- **Relasi Bukti:** Amati kode status HTTP dan pesan penolakan dari endpoint tahap pertama. Server memberikan respons yang berbeda antara parameter payload yang belum disertakan dan nilai otorisasi yang salah.
</details>

### Tahap 2: Rekonstruksi Sesi & Protokol

Lolos dari *handshake* tahap pertama akan menghasilkan nilai tantangan (*challenge*) beserta metadata sesi. Rekonstruksi struktur request tahap kedua untuk mengirimkan hasil perhitungan dan memperoleh flag.

<details>
<summary>Petunjuk untuk Tahap 2</summary>

- **Orientasi:** Antarmuka aplikasi Android tidak menyediakan tombol untuk tahap kedua. Anda perlu membangun dan mengirimkan request berikutnya secara mandiri menggunakan data dari respons Tahap 1.
- **Metode:** Periksa *body* JSON sekaligus seluruh HTTP *response header* yang diterima dari Tahap 1. Dekode parameter yang diberikan, tentukan hubungan perhitungannya dengan nilai *challenge*, lalu enkripsi jawabannya menggunakan struktur *envelope* yang sama.
- **Relasi Bukti:** Endpoint tahap kedua mewajibkan header token sesi yang aktif beserta jawaban terenkripsi. Pengiriman jawaban yang valid sebelum masa berlaku sesi habis akan menerbitkan satu flag unik.
</details>

### Tahap 3: Verifikasi Flag & Penyelesaian

Kirimkan flag yang telah diterbitkan untuk memverifikasi penyelesaian tantangan dan membuka layar debriefing Hall of Fame.

<details>
<summary>Petunjuk untuk Tahap 3</summary>

- **Orientasi:** Flag dibuat secara dinamis untuk setiap sesi yang sah dan divalidasi terhadap status di server, bukan disimpan sebagai teks statis di dalam berkas APK.
- **Metode:** Masukkan flag yang diperoleh ke kolom input verifikasi pada aplikasi Android atau kirimkan langsung ke endpoint verifikasi selama masa registrasi flag masih aktif.
- **Relasi Bukti:** Pastikan setiap request menyertakan header versi aplikasi yang sesuai. Klaim flag yang terverifikasi akan membuka sekuens debriefing Hall of Fame.
</details>

## Cakupan, Privasi, & Pencegahan Penyalahgunaan

- **Aturan Pengujian:** Pengujian dibatasi pada analisis biner, inspeksi protokol, dan pengiriman request terukur. Serangan *brute-force* otomatis, *fuzzing* berintensitas tinggi, atau pengujian *Denial of Service* (DoS) terhadap infrastruktur aktif dilarang keras.
- **Privasi Data:** Aplikasi ini tidak mengumpulkan atau menyimpan data pribadi. Jangan mengirimkan kredensial pribadi atau informasi sensitif di dalam payload.
- **Pembatasan Laju & Telemetri:** Alamat IP klien diproses secara sementara (*ephemeral*) pada penyimpanan state terdistribusi Redis dengan masa kedaluwarsa (TTL) otomatis semata-mata untuk penegakan *rate limit* dan mitigasi penyalahgunaan otomatis. Antarmuka seluler juga menerapkan jeda permintaan lokal untuk membantu mencegah pemblokiran server yang tidak disengaja saat penggunaan normal, sementara penyelesaian tantangan hanya dicatat melalui penghitung global anonim.

## Verifikasi Keaslian Rilis

Sebelum memasang APK, Anda dapat memverifikasi **tanda tangan sertifikat pengembang** (keaslian) maupun **checksum berkas** (keutuhan) secara mandiri:

1. **Tanda Tangan Sertifikat Pengembang (Keaslian):**  
   Seluruh APK rilis resmi ditandatangani dengan *keystore* pengembang yang sama (`CN=Fiad Pratama, OU=Software Engineering, O=Fiad Pratama, L=Palu, ST=Central Sulawesi, C=ID`). Verifikasi *digest* SHA-256 sertifikat menggunakan `apksigner` dari Android SDK:
   ```bash
   apksigner verify --print-certs app-release.apk
   ```
   - **SHA-256 Sertifikat Penandatangan Resmi:**
     ```text
     11919c358daf9c9f56da77617284fb99cc402fc789242fe8c75c674a3c551a62
     ```

2. **Checksum Berkas APK & Perataan 16 KB (Keutuhan):**  
   Hitung hash SHA-256 dari berkas `.apk` yang diunduh, lalu cocokkan hasilnya dengan *checksum* berkas yang tertera pada halaman [GitHub Releases](https://github.com/fiadpratama/CTF-application/releases) (dan secara opsional periksa perataan *16 KB page-size* untuk perangkat Android 15/16+):
   ```bash
   sha256sum app-release.apk                    # Linux / macOS
   certutil -hashfile app-release.apk SHA256    # Windows
   zipalign -c -P 16 4 app-release.apk          # Cek opsional perataan 16 KB
   ```

## Lisensi & Atribusi Aset

- **Kode Sumber:** Dirilis di bawah [Lisensi MIT](LICENSE). Lisensi MIT berlaku untuk kode sumber proyek dan tidak mencakup aset media pihak ketiga.
- **Aset Audio:** Musik latar pada layar debriefing Hall of Fame (`android_client/app/src/main/res/raw/bgm.ogg`) menggunakan potongan yang telah diedit (2:55–4:55, dengan *fade*) dari *"Signal to Noise"* karya **Scott Buckley** ([www.scottbuckley.com.au](https://www.scottbuckley.com.au)), dilisensikan di bawah [Creative Commons Attribution 4.0 International (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).

Untuk riwayat perubahan versi dan arsitektur, lihat [CHANGELOG.md](CHANGELOG.md). Untuk batasan keamanan infrastruktur dan kebijakan pelaporan kerentanan, lihat [SECURITY.md](SECURITY.md).
