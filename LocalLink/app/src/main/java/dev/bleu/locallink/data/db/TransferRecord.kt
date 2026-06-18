package dev.bleu.locallink.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.bleu.locallink.util.fileExtension
import dev.bleu.locallink.util.formatFileSize

enum class TransferDirection { SENT, RECEIVED }
enum class TransferStatus    { SUCCESS, FAILED, CANCELLED }

@Entity(tableName = "transfer_records")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val sizeBytes: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val peerName: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val extension: String get() = fileExtension(fileName)
    val sizeLabel: String get() = formatFileSize(sizeBytes)
}
