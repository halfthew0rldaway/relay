package dev.bleu.relay.data.model

import android.net.Uri
import dev.bleu.relay.util.fileExtension
import dev.bleu.relay.util.formatFileSize

data class PendingFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
) {
    val sizeLabel: String get() = formatFileSize(sizeBytes)
    val extension: String get() = fileExtension(name)
}
