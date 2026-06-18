package dev.bleu.locallink.data.model

import android.net.Uri
import dev.bleu.locallink.util.fileExtension
import dev.bleu.locallink.util.formatFileSize

data class PendingFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
) {
    val sizeLabel: String get() = formatFileSize(sizeBytes)
    val extension: String get() = fileExtension(name)
}
