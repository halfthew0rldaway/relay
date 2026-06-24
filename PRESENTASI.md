# Relay - Local File Transfer

## Apa itu Relay?
Relay adalah platform transfer file lokal secara *peer-to-peer* yang berjalan antar ekosistem (Android & Web). Memungkinkan pengiriman file berukuran besar dengan cepat tanpa mengandalkan koneksi internet, cukup berada di jaringan Wi-Fi/LAN yang sama.

---

## 🔍 Deep Dive: Bagaimana Perangkat Saling Menemukan (Discovery)?
Perangkat **tidak** membutuhkan input IP address manual untuk saling "melihat". Mereka mendeteksi satu sama lain menggunakan protokol **mDNS (Multicast DNS) / Zeroconf (Bonjour)**:
1. **Multicast Broadcast:** Saat aplikasi (Android) atau server (Node.js) menyala, mereka memancarkan (*broadcast*) paket UDP ke alamat IP khusus `224.0.0.251` di port `5353` ke seluruh jaringan lokal.
2. **Service Advertising:** Sistem ini mengiklankan ke jaringan: *"Halo, ada layanan file transfer `_http._tcp` dengan nama `Relay-Android-Bleu` yang aktif di Port 8080."*
3. **Service Resolution:** Perangkat penerima yang juga terhubung di Wi-Fi yang sama secara otomatis "mendengar" (listening) paket UDP ini. Perangkat akan menerjemahkan nama layanan tersebut menjadi alamat IP lokal aslinya (contoh: `192.168.1.15`), sehingga mereka bisa langsung terhubung tanpa bantuan DNS server eksternal/internet.

---

## ⚙️ Deep Dive: Bagaimana Alur Transfer Filenya?
Proses transfer dirancang menggunakan konsep *Streaming/Piping* untuk mencegah perangkat *crash* kehabisan RAM saat mengirim file berukuran ratusan Gigabyte:
1. **HTTP Handshake:** Klien melakukan HTTP POST request ke target IP, mengirimkan *metadata* file terlebih dahulu (nama file, ekstensi, ukuran total byte).
2. **Chunking & Streaming:** Daripada memuat (load) seluruh file video 5GB ke RAM, file dibaca dari Disk secara kecil-kecil (*chunks*).
3. **Piping / Buffered Streaming:** Data yang dibaca langsung dialirkan (*streamed/piped*) ke *network socket* (TCP/IP). Pada praktiknya, aplikasi membaca file dalam bentuk *chunk* buffer kecil (sekitar 64KB di Android) secara terus-menerus dan langsung mendorongnya ke jaringan.
4. Aliran langsung dari Disk 👉 Jaringan 👉 Disk ini memastikan penggunaan RAM yang sangat minim. *(Catatan Teknis: Secara arsitektur OS/Kernel, metode yang digunakan adalah **Buffered Userspace Streaming**, bukan true 'zero-copy' seperti syscall `sendfile`. Namun di lingkup aplikasi web, ini sering disebut 'zero-copy' karena kita tidak menyalin keseluruhan isi file utuh ke dalam RAM memori).*

---

## ⚖️ Perbandingan Relay vs Metode Transfer Lain (Pros & Cons)

**1. Relay vs Cloud Storage (WhatsApp / Google Drive / Telegram)**
- ✅ **Kelebihan Relay:** Kecepatan transfer murni secepat batas router Anda (bisa mencapai >100 MB/s), 100% gratis tanpa kuota internet, tidak ada kompresi/penurunan kualitas, dan privasi maksimal karena file tidak diunggah ke server pihak ketiga.
- ❌ **Kekurangan Relay:** Pengirim dan penerima harus terhubung di Wi-Fi/Hotspot yang sama saat itu juga. Tidak bisa mengirim file ke teman di beda kota.

**2. Relay vs Bluetooth**
- ✅ **Kelebihan Relay:** Kecepatan eksponensial lebih tinggi. Bandwidth Wi-Fi bisa ratusan Mbps, sedangkan Bluetooth mentok di ~2-3 Mbps. Sangat ideal untuk file raksasa/film.
- ❌ **Kekurangan Relay:** Membutuhkan router/hotspot lokal sebagai perantara jaringan. Sedangkan Bluetooth bisa koneksi fisik *ad-hoc* tanpa butuh jaringan Wi-Fi.

**3. Relay vs AirDrop / Nearby Share / Quick Share**
- ✅ **Kelebihan Relay:** *Cross-Platform* sejati! Anda bisa mengirim dari Android ke iPhone, ke Mac, atau ke PC Windows tanpa menginstall aplikasi di pihak target (cukup buka Web Browser karena adanya Node.js web interface).
- ❌ **Kekurangan Relay:** AirDrop/Nearby Share memiliki teknologi *Wi-Fi Direct* yang memancarkan sinyalnya sendiri, sedangkan Relay bergantung pada *Local Area Network* (Wi-Fi kantor/rumah/hotspot HP) agar bisa berjalan.

---

## Detail Teknologi & Framework (Tech Stack)
Arsitektur Relay terpisah antara *Mobile Client* (Android) dan *Web/Backend Node.js*:

**1. Aplikasi Android (Mobile Client):**
- **UI Framework:** Jetpack Compose & Material 3 (100% Native, Declarative UI).
- **Local Server Engine:** Ktor Server (CIO & Core) digunakan untuk menghosting *HTTP Server* asinkron kecil langsung di dalam HP Android.
- **Media & Animation:** Coil (Image Loading) & Lottie Compose (Offline-first vector animations).
- **Architecture:** MVVM (Model-View-ViewModel) dengan Kotlin Coroutines & Flow.

**2. Web & Backend (Node.js):**
- **Server:** Node.js dengan framework **Express.js**.
- **File Handling:** **Multer** (digunakan untuk memproses aliran data *multipart/form-data* yang masuk).
- **Service Discovery:** **bonjour-service** (mengelola mDNS broadcasting).
- **Frontend Web UI:** Vanilla HTML5, CSS3, dan JavaScript (DOM manipulation).

---

## Penggunaan Database
Relay menggunakan database **HANYA pada sisi aplikasi Android**, yaitu menggunakan **Room Database** (berbasis SQLite).

**Apa saja yang disimpan di Room DB?**
1. **Riwayat Transfer (`TransferRecord`):** Menyimpan log file. Hanya **metadata** yang direkam (nama, ukuran, status selesai/gagal, waktu). *File aslinya tidak ditampung di database.*
2. **Perangkat Tersimpan (`PinnedDevice`):** Riwayat IP/nama perangkat favorit agar pengguna bisa langsung *konek* secara instan.

**Sisi Web/Node.js (Stateless):**
Server Web Node.js murni **stateless** dan tidak memakai database sama sekali. Seluruh proses *state* dijaga dalam RAM dan dihapus begitu transfer usai. Tujuannya agar aplikasi Web tetap ringan (lightweight) dan menjaga privasi (*zero footprint*).

---

## FAQ (Tanya Jawab)
**Q: Apakah aman jika menggunakan Wi-Fi umum (Cafe/Kampus)?**
A: Karena aplikasi mentransfer lewat jalur lokal tanpa enkripsi canggih (*non-HTTPS local stream*), secara teknis data bisa di-*sniff* di jaringan Wi-Fi publik yang rawan peretasan. Disarankan memakai Router Rumah atau Hotspot HP pribadi jika mentransfer data pekerjaan/rahasia tingkat tinggi.

**Q: Mengapa server Node.js tidak memakai DB?**
A: Untuk kemudahan *deployment* lokal. Tujuannya adalah membuat jembatan (*relay server*) yang instan tanpa *setup* database yang merepotkan.
