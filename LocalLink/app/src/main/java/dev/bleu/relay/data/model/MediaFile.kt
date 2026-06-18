package dev.bleu.relay.data.model

import android.net.Uri
import dev.bleu.relay.util.fileExtension
import dev.bleu.relay.util.formatFileSize

enum class MediaCategory { IMAGES, VIDEOS, FILES }

data class MediaFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val dateModifiedMs: Long,
    val category: MediaCategory
) {
    val sizeLabel: String get() = formatFileSize(sizeBytes)
    val extension: String get() = fileExtension(name)

    fun toPendingFile() = PendingFile(uri = uri, name = name, sizeBytes = sizeBytes, mimeType = mimeType)
}
