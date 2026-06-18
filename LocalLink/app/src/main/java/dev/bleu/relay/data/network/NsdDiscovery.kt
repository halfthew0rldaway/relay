package dev.bleu.relay.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import dev.bleu.relay.data.model.DiscoveredDevice
import dev.bleu.relay.data.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private const val SERVICE_TYPE = "_relay._tcp."
private const val SERVICE_NAME_PREFIX = "Relay-"
private const val TXT_KEY_IP = "ip"
private const val DISCOVERY_SCAN_INTERVAL_MS = 8000L
private const val DISCOVERY_SCAN_TIMEOUT_MS = 500
private const val DISCOVERY_SCAN_BATCH_SIZE = 48

/**
 * Pencarian device dual-strategi: Android NSD (mDNS) + HTTP probe subnet sebagai fallback.
 *
 * ## Strategi 1 — NSD / mDNS
 * Meregister service `_relay._tcp.` dengan IPv4 device sebagai TXT record
 * (workaround untuk host resolution yang rusak di Android 11 / ColorOS).
 * Mendeskoversi service peer dan resolve secara sequential agar tidak terjadi `NSD_ERROR_INTERNAL`.
 *
 * ## Strategi 2 — HTTP probe subnet
 * Setiap [DISCOVERY_SCAN_INTERVAL_MS], memindai subnet /24 lokal dengan mengirim
 * HTTP GET `/health` ke setiap IP. Device yang menjalankan server Relay
 * merespons dengan metadata JSON. Hasil digabung dengan hasil NSD (NSD lebih prioritas).
 *
 * Mengembalikan [Flow] berisi map device yang sudah digabung, deduplicate berdasarkan IP.
 */
class NsdDiscovery(private val context: Context) {

    /** Mendapatkan alamat IPv4 WiFi device saat ini */
    private fun getLocalIp(wifiManager: WifiManager): String? {
        val ip = wifiManager.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }

    fun observeDevices(): Flow<Map<String, DiscoveredDevice>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Paksa chip WiFi agar menerima paket multicast/mDNS (penting di Realme/ColorOS Android 11)
        val multicastLock = wifiManager.createMulticastLock("relay_mcast_lock")
        multicastLock.setReferenceCounted(true)
        try { multicastLock.acquire() } catch (e: Exception) {}

        val nsdDevices = ConcurrentHashMap<String, DiscoveredDevice>()
        val probedDevices = ConcurrentHashMap<String, DiscoveredDevice>()

        fun emit() {
            val merged = LinkedHashMap<String, DiscoveredDevice>()
            nsdDevices.forEach { (key, value) -> merged[key] = value }
            probedDevices.forEach { (key, value) -> if (!merged.containsKey(key)) merged[key] = value }
            trySend(merged)
        }

        val resolveQueue = ArrayDeque<NsdServiceInfo>()
        val serviceNameToIp = ConcurrentHashMap<String, String>()
        var isResolving = false

        suspend fun probeHealth(ip: String, localIp: String): DiscoveredDevice? = withContext(Dispatchers.IO) {
            if (ip == localIp) return@withContext null
            try {
                val conn = (URL("http://$ip:$TRANSFER_PORT/health").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = DISCOVERY_SCAN_TIMEOUT_MS
                    readTimeout = DISCOVERY_SCAN_TIMEOUT_MS
                    useCaches = false
                }
                try {
                    if (conn.responseCode != 200) return@withContext null
                    val body = conn.inputStream.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                    val json = JSONObject(body)
                    if (!json.optBoolean("ok", false)) return@withContext null
                    val service = json.optString("service", "relay").lowercase()
                    if (service != "relay") return@withContext null

                    val name = json.optString("deviceName", json.optString("name", "")).trim()
                    if (name.isBlank()) return@withContext null

                    val platform = json.optString("platform", "")
                    val type = when {
                        platform.equals("desktop", ignoreCase = true) -> DeviceType.DESKTOP
                        name.contains("Mac", true) || name.contains("Laptop", true) -> DeviceType.LAPTOP
                        name.contains("Tablet", true) || name.contains("Pad", true) -> DeviceType.TABLET
                        name.contains("Desktop", true) || name.contains("PC", true)
                            || name.contains("Linux", true) || name.contains("Windows", true) -> DeviceType.DESKTOP
                        else -> DeviceType.PHONE
                    }

                    DiscoveredDevice(
                        id = ip,
                        name = name,
                        ipAddress = ip,
                        port = json.optInt("port", TRANSFER_PORT),
                        type = type
                    )
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }

        suspend fun scanSubnet(localIp: String): Map<String, DiscoveredDevice> = withContext(Dispatchers.IO) {
            val prefix = localIp.substringBeforeLast('.', "")
            if (prefix.isBlank()) return@withContext emptyMap()

            val candidates = (1..254)
                .map { "$prefix.$it" }
                .filter { it != localIp }

            val results = LinkedHashMap<String, DiscoveredDevice>()
            for (chunk in candidates.chunked(DISCOVERY_SCAN_BATCH_SIZE)) {
                val resolved = chunk.map { ip -> async { probeHealth(ip, localIp) } }.awaitAll()
                resolved.filterNotNull().forEach { results[it.ipAddress] = it }
            }
            results
        }

        fun resolveNext() {
            if (isResolving || resolveQueue.isEmpty()) return
            isResolving = true
            val info = resolveQueue.removeFirst()
            try {
                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        isResolving = false  // Always reset FIRST

                        val rawName = si.serviceName.removePrefix(SERVICE_NAME_PREFIX)
                        val devName = rawName.ifBlank { si.serviceName }
                        val mySubstring = android.os.Build.MODEL.take(14)

                        if (!devName.contains(mySubstring, ignoreCase = true)) {
                            val parsedType = when {
                                devName.contains("Mac", true) || devName.contains("Laptop", true) -> DeviceType.LAPTOP
                                devName.contains("Tablet", true) || devName.contains("Pad", true) -> DeviceType.TABLET
                                devName.contains("Desktop", true) || devName.contains("PC", true)
                                    || devName.contains("Linux", true) || devName.contains("Windows", true) -> DeviceType.DESKTOP
                                else -> DeviceType.PHONE
                            }

                            // Prioritas 1: atribut "ip" dari TXT record (paling andal di Android 11)
                            //   — di-set oleh app saat registrasi, bypass host resolution yang rusak
                            // Prioritas 2: si.host (bekerja di Android 12+, kadang Android 11)
                            // Prioritas 3: resolve hostname via DNS (upaya terakhir)
                            val txtIp = si.attributes[TXT_KEY_IP]
                                ?.let { bytes -> if (bytes.isNotEmpty()) String(bytes, Charsets.UTF_8) else null }
                                ?.takeIf { it.contains('.') }  // must be IPv4

                            val ip = txtIp
                                ?: si.host?.hostAddress?.takeIf { it.contains('.') }
                                ?: try {
                                    InetAddress.getByName("${si.serviceName}.local").hostAddress
                                        ?.takeIf { it.contains('.') }
                                } catch (e: Exception) { null }

                            if (ip != null) {
                                serviceNameToIp[si.serviceName] = ip
                                nsdDevices[ip] = DiscoveredDevice(
                                    id = ip,
                                    name = devName,
                                    ipAddress = ip,
                                    port = si.port,
                                    type = parsedType
                                )
                                emit()
                            }
                        }
                        resolveNext()
                    }

                    override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
                        isResolving = false
                        resolveNext()
                    }
                })
            } catch (e: Exception) {
                isResolving = false
                resolveNext()
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                // Harus mengandung '"relay"' dan 'tcp'
                val t = info.serviceType ?: ""
                val matchesType = t.contains("relay", ignoreCase = true) && t.contains("tcp", ignoreCase = true)
                
                if (matchesType) {
                    resolveQueue.addLast(info)
                    resolveNext()
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                serviceNameToIp.remove(info.serviceName)?.let { ip ->
                    nsdDevices.remove(ip)
                }
                emit()
            }
        }

        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        // Sisipkan IP kita sebagai TXT record agar peer di Android 11 bisa menemukan kita
        // tanpa perlu host record lookup (yang gagal diam-diam di banyak OEM)
        val localIp = getLocalIp(wifiManager) ?: ""
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX${android.os.Build.MODEL.take(14)}"
            serviceType = SERVICE_TYPE
            port = 49152
            setAttribute(TXT_KEY_IP, localIp)
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {}

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {}

        val probeJob = launch {
            while (isActive) {
                val localIp = getLocalIp(wifiManager)
                val scanned = if (localIp.isNullOrBlank()) emptyMap() else scanSubnet(localIp)
                probedDevices.clear()
                probedDevices.putAll(scanned.filterKeys { !nsdDevices.containsKey(it) })
                emit()
                delay(DISCOVERY_SCAN_INTERVAL_MS)
            }
        }

        emit()

        awaitClose {
            probeJob.cancel()
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
            try { nsdManager.unregisterService(registrationListener) } catch (e: Exception) {}
            try { if (multicastLock.isHeld) multicastLock.release() } catch (e: Exception) {}
        }
    }
}
