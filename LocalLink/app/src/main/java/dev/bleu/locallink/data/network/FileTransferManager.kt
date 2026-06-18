package dev.bleu.locallink.data.network

import android.content.Context
import android.net.Uri
import dev.bleu.locallink.data.model.PendingFile
import dev.bleu.locallink.data.model.TransferProgress
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

const val TRANSFER_PORT = 49152

/**
 * Mesin transfer utama — menjalankan server HTTP Ktor CIO di [TRANSFER_PORT].
 *
 * ## Endpoint server (sisi penerima)
 * - `GET /health` — health check; mengembalikan metadata device sebagai JSON.
 * - `POST /handshake` — pengirim memulai transfer dengan payload `"NamaPengirim|||file:nama;;;file:nama"`.
 * - `GET /file/{idx}` — menyajikan file berdasarkan index dengan streaming progress.
 *
 * ## Operasi client (sisi pengirim)
 * - [sendHandshake] — mengirim payload handshake ke peer via POST.
 * - [receiveFile] — mengunduh satu file dari pengirim remote via HTTP GET.
 *
 * Progress untuk kedua arah di-expose via [progress] (StateFlow<TransferProgress>).
 * Mendukung pembatalan via [cancelTransfer] (set flag yang dicek di loop baca).
 */
class FileTransferManager(
    private val context: Context,
    private val getDeviceName: () -> String,
    private val onIncomingHandshake: (senderName: String, senderIp: String, files: List<PendingFile>) -> Unit
) {
    private var server: ApplicationEngine? = null
    private val _progress = MutableStateFlow(TransferProgress())
    val progress: StateFlow<TransferProgress> = _progress

    private var cancelFlag = false
    private var hostedFiles: List<PendingFile> = emptyList()

    /** Menjalankan server HTTP persisten untuk menerima koneksi transfer */
    fun startPersistentServer() {
        if (server != null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(CIO, port = TRANSFER_PORT) {
                    install(PartialContent)
                    routing {
                        get("/health") {
                            call.respond(
                                mapOf(
                                    "ok" to true,
                                    "service" to "locallink",
                                    "platform" to "android",
                                    "port" to TRANSFER_PORT,
                                    "deviceName" to getDeviceName()
                                )
                            )
                        }
                        // Handshake dari pengirim
                        post("/handshake") {
                            try {
                                val body = call.receiveText()
                                // Format: "NamaPengirim|||file1:size1;;;file2:size2"
                                val parts = body.split("|||")
                                val senderName = parts.getOrNull(0) ?: "Unknown"
                                val filesPart = parts.getOrNull(1) ?: ""
                                val senderIp = call.request.local.remoteHost
                                    .removePrefix("::ffff:")
                                    .substringBefore('%')
                                val parsedFiles = filesPart.split(";;;").filter { it.isNotBlank() }.map {
                                    val f = it.split(":")
                                    PendingFile(Uri.EMPTY, f.getOrElse(0) { "file" }, f.getOrElse(1) { "0" }.toLong(), "")
                                }
                                withContext(Dispatchers.Main) {
                                    onIncomingHandshake(senderName, senderIp, parsedFiles)
                                }
                                call.respond(HttpStatusCode.OK)
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                        }
                        // Menyajikan file ke penerima
                        get("/file/{idx}") {
                            val idx = call.parameters["idx"]?.toIntOrNull() ?: return@get
                            val pf = hostedFiles.getOrNull(idx)
                                ?: run { call.respond(HttpStatusCode.NotFound); return@get }
                            val stream: InputStream = this@FileTransferManager.context.contentResolver.openInputStream(pf.uri)
                                ?: run { call.respond(HttpStatusCode.NotFound); return@get }
                            call.response.header("Content-Disposition", "attachment; filename=\"${pf.name}\"")
                            try {
                                call.respondOutputStream(contentLength = pf.sizeBytes) {
                                    try {
                                        stream.use { ins ->
                                            val buf = ByteArray(65536)
                                            var transferred = 0L
                                            var read: Int
                                            var startMs = System.currentTimeMillis()
                                            _progress.value = TransferProgress(fileName = pf.name, totalBytes = pf.sizeBytes)
                                            TransferService.start(this@FileTransferManager.context, pf.name)
                                            
                                            while (ins.read(buf).also { read = it } != -1) {
                                                if (cancelFlag) {
                                                    _progress.value = _progress.value.copy(isCancelled = true)
                                                    TransferService.stop(this@FileTransferManager.context)
                                                    return@use
                                                }
                                                this.write(buf, 0, read)
                                                
                                                transferred += read
                                                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                                                val speed = transferred * 1000L / elapsed
                                                _progress.value = _progress.value.copy(
                                                    transferredBytes = transferred,
                                                    speedBytesPerSec = speed
                                                )
                                                val pct = if (pf.sizeBytes > 0) (transferred * 100 / pf.sizeBytes).toInt() else 0
                                                TransferService.updateProgress(this@FileTransferManager.context, pf.name, pct, false)
                                            }
                                            // Jika file terakhir, tandai selesai
                                            if (!cancelFlag && idx == hostedFiles.lastIndex) {
                                                _progress.value = _progress.value.copy(isDone = true)
                                                TransferService.updateProgress(this@FileTransferManager.context, pf.name, 100, true)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        _progress.value = _progress.value.copy(isFailed = true)
                                        TransferService.stop(this@FileTransferManager.context)
                                        throw e
                                    }
                                }
                            } catch (e: Exception) {
                                _progress.value = _progress.value.copy(isFailed = true)
                                TransferService.stop(this@FileTransferManager.context)
                            }
                        }
                    }
                }.start(wait = false)
            } catch (e: Exception) {
                // Port kemungkinan sudah terpakai
            }
        }
    }

    fun stopServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try { server?.stop(0, 0) } catch (e: Exception) {}
            server = null
        }
    }

    fun hostFiles(files: List<PendingFile>) {
        hostedFiles = files
    }

    fun cancelTransfer() { 
        cancelFlag = true 
        _progress.value = _progress.value.copy(isCancelled = true)
    }

    // Pengirim memberitahu penerima
    suspend fun sendHandshake(
        senderName: String,
        files: List<PendingFile>,
        peerIp: String
    ): Boolean = withContext(Dispatchers.IO) {
        cancelFlag = false
        _progress.value = TransferProgress(fileName = "Handshake", totalBytes = 1)
        try {
            val url = URL("http://$peerIp:$TRANSFER_PORT/handshake")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            val filesStr = files.joinToString(";;;") { "${it.name}:${it.sizeBytes}" }
            val payload = "$senderName|||$filesStr".toByteArray()
            conn.setRequestProperty("Content-Length", payload.size.toString())
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(isFailed = true)
            false
        }
    }

    // Pengirim tetap sebagai server, UI update saat file di-pull
    fun setProgress(progress: TransferProgress) {
        _progress.value = progress
    }

    // Penerima mengunduh dari pengirim
    suspend fun receiveFile(
        sourceIp: String,
        sourcePort: Int,
        fileIndex: Int,
        fileName: String,
        totalBytes: Long,
        destUri: Uri
    ) = withContext(Dispatchers.IO) {
        cancelFlag = false
        _progress.value = TransferProgress(fileName = fileName, totalBytes = totalBytes)
        TransferService.start(context, fileName)
        try {
            val url = URL("http://$sourceIp:$sourcePort/file/$fileIndex")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            val inputStream: InputStream = conn.inputStream
            val out: OutputStream = when (destUri.scheme) {
                "file" -> FileOutputStream(destUri.path ?: return@withContext)
                else -> context.contentResolver.openOutputStream(destUri)
            } ?: return@withContext
            inputStream.use { ins ->
                out.use { os ->
                    val buf = ByteArray(65536)
                    var transferred = 0L
                    var read: Int
                    val startMs = System.currentTimeMillis()
                    while (ins.read(buf).also { read = it } != -1) {
                        if (cancelFlag) {
                            _progress.value = _progress.value.copy(isCancelled = true)
                            TransferService.stop(context)
                            return@withContext
                        }
                        os.write(buf, 0, read)
                        transferred += read
                        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                        val speed = transferred * 1000L / elapsed
                        _progress.value = _progress.value.copy(
                            transferredBytes = transferred,
                            speedBytesPerSec = speed
                        )
                        val pct = if (totalBytes > 0) (transferred * 100 / totalBytes).toInt() else 0
                        TransferService.updateProgress(context, fileName, pct, false)
                    }
                }
            }
            conn.disconnect()
            _progress.value = _progress.value.copy(isDone = true)
            TransferService.updateProgress(context, fileName, 100, true)
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(isFailed = true)
            TransferService.stop(context)
        }
    }
}
