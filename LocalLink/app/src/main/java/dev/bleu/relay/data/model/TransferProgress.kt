package dev.bleu.relay.data.model

data class TransferProgress(
    val fileName: String = "",
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val isDone: Boolean = false,
    val isFailed: Boolean = false,
    val isCancelled: Boolean = false,
    val errorMessage: String? = null
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val percentLabel: String
        get() = "${(fraction * 100).toInt()}%"

    val speedLabel: String
        get() = when {
            speedBytesPerSec >= 1_048_576L -> "%.1f MB/s".format(speedBytesPerSec / 1_048_576.0)
            speedBytesPerSec >= 1_024L     -> "%.0f KB/s".format(speedBytesPerSec / 1_024.0)
            else                           -> "$speedBytesPerSec B/s"
        }

    val etaLabel: String
        get() {
            val remaining = totalBytes - transferredBytes
            if (speedBytesPerSec <= 0L || isDone) return "--"
            val secs = remaining / speedBytesPerSec
            return when {
                secs < 60   -> "${secs}s"
                secs < 3600 -> "${secs / 60}m ${secs % 60}s"
                else        -> "${secs / 3600}h ${(secs % 3600) / 60}m"
            }
        }
}
