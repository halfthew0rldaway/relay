package dev.bleu.relay.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.bleu.relay.data.db.AppDatabase
import dev.bleu.relay.data.db.PinnedDevice
import dev.bleu.relay.data.db.TransferDirection
import dev.bleu.relay.data.db.TransferRecord
import dev.bleu.relay.data.db.TransferStatus
import dev.bleu.relay.data.media.MediaRepository
import dev.bleu.relay.data.model.DiscoveredDevice
import dev.bleu.relay.data.model.MediaCategory
import dev.bleu.relay.data.model.MediaFile
import dev.bleu.relay.data.model.PendingFile
import dev.bleu.relay.data.model.TransferProgress
import dev.bleu.relay.data.network.FileTransferManager
import dev.bleu.relay.data.network.NsdDiscovery
import dev.bleu.relay.data.network.TRANSFER_PORT
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel utama yang mengoordinasikan seluruh siklus transfer file.
 *
 * ## Lapisan arsitektur
 *  - **Discovery**: [NsdDiscovery] (mDNS + HTTP probe subnet) memberi data ke [devices].
 *  - **Transfer**: [FileTransferManager] menjalankan server Ktor CIO di port 49152,
 *    menangani handshake HTTP, serving file (pengirim), dan download file (penerima).
 *  - **Penyimpanan**: Room database ([TransferDao], [PinnedDeviceDao]) untuk riwayat dan pin.
 *  - **Media**: [MediaRepository] mengakses storage device untuk file manager bawaan.
 *
 * ## Alur transfer (pengirim)
 *  1. User memilih file → [setPendingFiles].
 *  2. User mengetuk device → [selectDevice], lalu [startSend].
 *  3. [startSend] memanggil [FileTransferManager.hostFiles], lalu mengirim POST `/handshake` ke peer.
 *  4. Penerima mengambil file via GET `/file/{idx}`; progres dilacak di [transferProgress].
 *  5. Selesai, [TransferRecord] disimpan ke Room.
 *
 * ## Alur transfer (penerima)
 *  1. POST `/handshake` masuk → [FileTransferManager.onIncomingHandshake].
 *  2. Jika auto-accept mati, [KonfirmasiPenerimaanScreen] ditampilkan; jika hidup, auto-start.
 *  3. [startReceive] mengambil file dari pengirim via HTTP GET, disimpan ke Downloads/Relay.
 *  4. Setiap file selesai dicatat ke Room via [TransferDao].
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    // Inisialisasi komponen utama
    private val db          = AppDatabase.getInstance(app)
    private val dao         = db.transferDao()
    private val pinnedDao   = db.pinnedDeviceDao()
    private val nsd         = NsdDiscovery(app)
    private val mediaRepo   = MediaRepository(app)

    private val ftm = FileTransferManager(
        context = app,
        getDeviceName = { deviceName.value }
    ) { senderName, senderIp, files ->
        val dev = devices.value.find { it.ipAddress == senderIp || it.name == senderName }
            ?: DiscoveredDevice(senderIp.ifBlank { senderName }, senderName, senderIp, TRANSFER_PORT)
        if (autoAccept.value) {
            setIncomingTransfer(dev, files)
            startReceive()
        } else {
            setIncomingTransfer(dev, files)
        }
    }

    // ── Pencarian device ─────────────────────────────────────────────────
    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val devices: StateFlow<List<DiscoveredDevice>> = refreshTrigger
        .flatMapLatest { nsd.observeDevices() }
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refreshScan() { refreshTrigger.value++ }

    // ── Device yang di-pin ───────────────────────────────────────────────
    val pinnedDevices: StateFlow<List<PinnedDevice>> = pinnedDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePin(device: DiscoveredDevice) {
        viewModelScope.launch {
            if (pinnedDao.isPinned(device.id) > 0) {
                pinnedDao.deleteById(device.id)
            } else {
                pinnedDao.insert(
                    PinnedDevice(
                        id = device.id,
                        name = device.name,
                        ipAddress = device.ipAddress,
                        port = device.port,
                        type = device.type
                    )
                )
            }
        }
    }

    fun isPinned(device: DiscoveredDevice): Boolean =
        pinnedDevices.value.any { it.id == device.id }

    /** Target terbaik: pinned device pertama yang online */
    val quickSendTarget: StateFlow<DiscoveredDevice?> = combine(devices, pinnedDevices) { live, pinned ->
        pinned.firstOrNull { p -> live.any { it.id == p.id } }
            ?.let { p -> live.first { it.id == p.id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Device yang dipilih ──────────────────────────────────────────────
    private val _selectedDevice = MutableStateFlow<DiscoveredDevice?>(null)
    val selectedDevice: StateFlow<DiscoveredDevice?> = _selectedDevice.asStateFlow()

    fun selectDevice(device: DiscoveredDevice) { _selectedDevice.value = device }

    // ── File yang belum dikirim ──────────────────────────────────────────
    private val _pendingFiles = MutableStateFlow<List<PendingFile>>(emptyList())
    val pendingFiles: StateFlow<List<PendingFile>> = _pendingFiles.asStateFlow()

    fun setPendingFiles(files: List<PendingFile>) { _pendingFiles.value = files }
    fun clearPendingFiles() { _pendingFiles.value = emptyList() }

    // ── Transfer masuk ───────────────────────────────────────────────────
    private val _incomingFiles  = MutableStateFlow<List<PendingFile>>(emptyList())
    val incomingFiles: StateFlow<List<PendingFile>> = _incomingFiles.asStateFlow()
    private val _incomingSender = MutableStateFlow<DiscoveredDevice?>(null)
    val incomingSender: StateFlow<DiscoveredDevice?> = _incomingSender.asStateFlow()

    fun setIncomingTransfer(sender: DiscoveredDevice, files: List<PendingFile>) {
        _incomingSender.value = sender
        _incomingFiles.value  = files
    }

    fun clearIncomingTransfer() {
        _incomingSender.value = null
        _incomingFiles.value  = emptyList()
    }

    // ── Progres transfer ─────────────────────────────────────────────────
    val transferProgress: StateFlow<TransferProgress> = ftm.progress

    // ── Riwayat transfer ─────────────────────────────────────────────────
    val transferHistory: StateFlow<List<TransferRecord>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Pengaturan ───────────────────────────────────────────────────────
    private val _deviceName = MutableStateFlow(
        android.os.Build.MODEL.take(24).ifBlank { "Relay Device" }
    )
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _downloadPath = MutableStateFlow(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    )
    val downloadPath: StateFlow<String> = _downloadPath.asStateFlow()

    private val prefs = app.getSharedPreferences("relay_prefs", Context.MODE_PRIVATE)

    private val _hasSeenWelcome = MutableStateFlow(prefs.getBoolean("hasSeenWelcome", false))
    val hasSeenWelcome: StateFlow<Boolean> = _hasSeenWelcome.asStateFlow()

    fun dismissWelcome() {
        prefs.edit().putBoolean("hasSeenWelcome", true).apply()
        _hasSeenWelcome.value = true
    }

    private val _autoAccept = MutableStateFlow(false)
    val autoAccept: StateFlow<Boolean> = _autoAccept.asStateFlow()

    fun updateDeviceName(name: String) { _deviceName.value = name }
    fun toggleAutoAccept() { _autoAccept.value = !_autoAccept.value }

    init { ftm.startPersistentServer() }

    // ── Media (file manager bawaan) ──────────────────────────────────────
    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val _activeCategory = MutableStateFlow(MediaCategory.IMAGES)
    val activeCategory: StateFlow<MediaCategory> = _activeCategory.asStateFlow()

    private val _mediaLoading = MutableStateFlow(false)
    val mediaLoading: StateFlow<Boolean> = _mediaLoading.asStateFlow()

    fun loadMedia(category: MediaCategory) {
        _activeCategory.value = category
        viewModelScope.launch {
            _mediaLoading.value = true
            _mediaFiles.value = when (category) {
                MediaCategory.IMAGES -> mediaRepo.queryImages()
                MediaCategory.VIDEOS -> mediaRepo.queryVideos()
                MediaCategory.FILES  -> mediaRepo.queryFiles()
            }
            _mediaLoading.value = false
        }
    }

    // ── Kirim ────────────────────────────────────────────────────────────
    fun startSend() {
        val device = _selectedDevice.value ?: return
        val files  = _pendingFiles.value.ifEmpty { return }
        ftm.hostFiles(files)
        viewModelScope.launch {
            val success = ftm.sendHandshake(deviceName.value, files, device.ipAddress)
            if (!success) {
                dao.insert(TransferRecord(
                    fileName  = files.firstOrNull()?.name ?: "Unknown",
                    sizeBytes = files.sumOf { it.sizeBytes },
                    direction = TransferDirection.SENT,
                    status    = TransferStatus.FAILED,
                    peerName  = device.name
                ))
                return@launch
            }
            ftm.setProgress(TransferProgress("Waiting for ${device.name} to accept...", totalBytes = files.sumOf { it.sizeBytes }))
            ftm.progress.first { it.isDone || it.isFailed || it.isCancelled }
            val final = ftm.progress.value
            files.forEach { pf ->
                dao.insert(TransferRecord(
                    fileName  = pf.name,
                    sizeBytes = pf.sizeBytes,
                    direction = TransferDirection.SENT,
                    status    = if (final.isFailed || final.isCancelled) TransferStatus.FAILED else TransferStatus.SUCCESS,
                    peerName  = device.name
                ))
            }
        }
    }

    // ── Terima ───────────────────────────────────────────────────────────
    fun startReceive() {
        val sender = _incomingSender.value ?: return
        val files  = _incomingFiles.value.ifEmpty { return }
        if (sender.ipAddress.isBlank()) {
            ftm.setProgress(TransferProgress(fileName = "Unable to identify sender", totalBytes = files.sumOf { it.sizeBytes }, isFailed = true))
            return
        }
        viewModelScope.launch {
            files.forEachIndexed { idx, pf ->
                val destUri = createDownloadUri(pf.name, pf.mimeType) ?: return@forEachIndexed
                ftm.receiveFile(sender.ipAddress, sender.port, idx, pf.name, pf.sizeBytes, destUri)
                val progress = ftm.progress.value
                dao.insert(TransferRecord(
                    fileName  = pf.name,
                    sizeBytes = pf.sizeBytes,
                    direction = TransferDirection.RECEIVED,
                    status    = if (progress.isFailed || progress.isCancelled) TransferStatus.FAILED else TransferStatus.SUCCESS,
                    peerName  = sender.name
                ))
            }
        }
    }

    fun cancelTransfer() { ftm.cancelTransfer() }

    private fun createDownloadUri(fileName: String, mimeType: String): Uri? {
        val resolver = getApplication<Application>().contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Relay")
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else {
            val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(dir, "Relay/$fileName")
            file.parentFile?.mkdirs()
            Uri.fromFile(file)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ftm.stopServer()
    }
}
