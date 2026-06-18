package dev.bleu.relay.util

/**
 * Utilitas untuk format ukuran file dan ekstensi file.
 *
 * Digunakan di seluruh UI untuk menampilkan info file
 * secara konsisten tanpa duplikasi kode.
 */

/** Format ukuran bytes menjadi string yang mudah dibaca (contoh: "1.5 MB") */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.0f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}

/** Ambil ekstensi file dari nama, maksimal 5 karakter, huruf besar */
fun fileExtension(name: String): String =
    name.substringAfterLast('.', "").uppercase().take(5).ifBlank { "FILE" }
