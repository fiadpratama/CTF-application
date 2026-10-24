# CTF Application

[English](README.md) | Bahasa Indonesia

---

Tantangan keamanan seluler praktis yang menyimulasikan asesmen keamanan aplikasi Android. Uji keahlian Anda dalam *reverse engineering*, inspeksi *runtime* dinamis, analisis kriptografi, dan manipulasi protokol jaringan.

## Arsitektur

- **Klien (Android):** Java dengan *engine* native C++ (JNI) (Android 8.0+ / API 26+, menargetkan API 34), diperkuat dengan obfuskasi simbol *compile-time*, *timing noise* eksekusi, serta deteksi *tracing* pasif.
- **Backend (Serverless):** REST API Node.js modular di Vercel, didukung Upstash Redis untuk *state* terdistribusi, *pipeline* verifikasi Lua atomik, token sesi JWT *stateless*, dan *version gating*.
- **Kriptografi:** Enkripsi *payload* AES-256-GCM yang melindungi komunikasi klien-server.

## Alat & Prasyarat

- **Analisis Statis:** JADX, Apktool, atau Ghidra.
- **Instrumentasi Dinamis:** Frida atau Objection.
- **Intersepsi Trafik:** Burp Suite atau OWASP ZAP *(catatan: sertifikat CA pengguna memerlukan konfigurasi keamanan jaringan atau bypass runtime pada Android modern)*.

## Spesifikasi Tantangan

Tujuannya adalah menembus *pipeline* verifikasi dan mengklaim flag dari server jarak jauh melalui tiga tahap berurutan.

### Tahap 1: Ekstraksi Kriptografi

Klien mengirimkan *envelope* terenkripsi ke backend. Tentukan apa yang diharapkan server untuk memvalidasi *handshake* awal ini.

<details>
<summary>💡 Petunjuk untuk Tahap 1</summary>

- **Orientasi:** Lihatlah ke balik lapisan *runtime* aplikasi. Jantung sesungguhnya berkomunikasi dalam bahasa tingkat rendah di mana batas logika berjalan.
- **Metode:** Apa yang tertulis di permukaan biner tidak selalu menjadi apa yang dieksekusi mesin. Memori saat *runtime* mengenali apa yang salah dibaca oleh analisis statis.
- **Bukti:** Penjaga gerbang tidak sekadar menolak atau menerima; dengarkan bagaimana ia menolak Anda. Saku yang kosong memantulkan gema yang berbeda dari kunci palsu.
</details>

### Tahap 2: Manipulasi Protokol

Lolos dari gerbang pertama memberikan akses ke evaluasi sisi server yang membutuhkan data di luar alur default.

<details>
<summary>💡 Petunjuk untuk Tahap 2</summary>

- **Orientasi:** Lolos dari ujian pertama bukan hanya membuka jalan, tetapi membawa pesan. Jangan buang amplop yang mengantarkan Anda masuk.
- **Metode:** Otoritas memberikan stempel bertanda tangan bersamaan dengan teka-teki terselubung. Amati metadata di sekitar pemberian tersebut—jawabannya menjembatani apa yang diberikan dan apa yang diminta.
- **Bukti:** Waktu tidak berhenti, dan keseimbangan menuntut perhitungan. Selesaikan persamaan tersebut sebelum kesabaran sang otoritas habis.
</details>

### Tahap 3: Verifikasi Akhir

Kirimkan flag yang berhasil diklaim untuk menyelesaikan asesmen dan membuka Hall of Fame.

<details>
<summary>💡 Petunjuk untuk Tahap 3</summary>

- **Orientasi:** Tanda kemenangan ini tidak dapat dipindahtangankan; ia hanya mengenali sesi yang memperjuangkannya.
- **Metode:** Anda dapat mempersembahkan bukti melalui terminal antarmuka, atau berbicara langsung ke pintu verifikasi terakhir.
- **Bukti:** Penjaga gerbang hanya menerima penjelajah dengan identitas terkini. Pastikan transmisi Anda mencerminkan era yang sedang berjalan.
</details>

## Memulai

1. Unduh `.apk` terbaru dari [Rilis](../../releases). Build yang usang akan ditolak dengan respons `HTTP 426`.
2. Pasang pada emulator atau perangkat uji fisik (Android 8.0+).
3. Siapkan proksi intersepsi Anda dan mulai *reverse engineering*.

Untuk catatan rilis dan riwayat pembaruan lengkap, lihat [CHANGELOG.md](CHANGELOG.md).

### Membangun dari Kode Sumber (Opsional)

```bash
# 1. Siapkan konfigurasi rahasia native lokal
cp android_client/app/src/main/cpp/native-secrets.properties.example android_client/app/src/main/cpp/native-secrets.properties

# 2. Bangun APK rilis menggunakan Gradle Wrapper
cd android_client
./gradlew assembleRelease      # Linux / macOS
gradlew.bat assembleRelease    # Windows
```
File APK hasil kompilasi akan berada di `android_client/app/build/outputs/apk/release/app-release.apk`.

## Privasi & Pencegahan Penyalahgunaan

Tantangan ini tidak mengumpulkan data pribadi.
- **Rate Limiting:** Alamat IP klien disimpan sementara di Redis dengan kedaluwarsa otomatis (hingga 30 menit bagi klien yang melanggar batas) semata-mata untuk mitigasi *brute-force*.
- **Penghitung Penyelesaian:** Penghitung anonim global mencatat jumlah tantangan yang selesai tanpa menyimpan identitas pengguna.
- **Trafik:** Hanya payload tantangan yang dievaluasi. Jangan mengirimkan kredensial pribadi.

## Verifikasi Keaslian Rilis

APK rilis resmi ditandatangani menggunakan *keystore* pengembang khusus:

- **Sidik Jari SHA-256:** `11919c358daf9c9f56da77617284fb99cc402fc789242fe8c75c674a3c551a62`
- Verifikasi menggunakan `apksigner`:
  ```bash
  apksigner verify --print-certs app-release.apk
  ```
- Cocokkan *digest* SHA-256 berkas dengan *checksum* yang dipublikasikan pada halaman [GitHub Releases](../../releases).

---

*Disclaimer: Repositori ini ditujukan semata-mata untuk riset keamanan edukatif dan latihan peretasan etis. Serangan DoS otomatis atau brute-force terhadap infrastruktur live dilarang keras.*
