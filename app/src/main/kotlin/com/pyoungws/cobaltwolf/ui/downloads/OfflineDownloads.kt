package com.pyoungws.cobaltwolf.ui.downloads

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class DownloadedItem(val itemId: String, val name: String, val path: String, val sizeBytes: Long)

data class DownloadJob(val itemId: String, val name: String, val received: Long = 0, val total: Long = -1, val failed: Boolean = false, val waitingForWifi: Boolean = false)

/**
 * Saves server media to app-private storage for offline playback. The index lives in
 * SharedPreferences; files land in filesDir/offline/<itemId>.bin.
 */
class OfflineDownloads(private val context: Context) {
    private val prefs = context.getSharedPreferences("offline_downloads", Context.MODE_PRIVATE)

    /** Download only on unmetered (Wi-Fi) connections; off also allows mobile data. */
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(value) = prefs.edit().putBoolean("wifi_only", value).apply()

    /** Store downloads on external app storage (usually an SD card) when one is mounted. */
    var useExternalStorage: Boolean
        get() = prefs.getBoolean("external_storage", false)
        set(value) = prefs.edit().putBoolean("external_storage", value).apply()

    private fun storageDir(): File {
        val base = if (useExternalStorage) context.getExternalFilesDir(null) ?: context.filesDir else context.filesDir
        return File(base, "offline")
    }

    /** True when a download may start on the current network under [wifiOnly]. */
    fun networkAllowed(): Boolean {
        if (!wifiOnly) return true
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private val _items = MutableStateFlow(loadIndex())
    val items: StateFlow<List<DownloadedItem>> = _items.asStateFlow()

    private val _jobs = MutableStateFlow<Map<String, DownloadJob>>(emptyMap())
    val jobs: StateFlow<Map<String, DownloadJob>> = _jobs.asStateFlow()

    fun isDownloaded(itemId: String) = _items.value.any { it.itemId == itemId && File(it.path).exists() }
    fun isDownloading(itemId: String) = itemId in _jobs.value

    fun fileFor(itemId: String): File? =
        _items.value.firstOrNull { it.itemId == itemId }?.let { File(it.path).takeIf(File::exists) }

    fun delete(itemId: String) {
        _items.value.firstOrNull { it.itemId == itemId }?.let { File(it.path).delete() }
        _items.update { list -> list.filterNot { it.itemId == itemId } }
        saveIndex(_items.value)
    }

    /** Streams /Items/{id}/Download to disk. Reports progress through [jobs]. */
    suspend fun download(serverAddress: String, accessToken: String, itemId: String, name: String) = withContext(Dispatchers.IO) {
        if (isDownloading(itemId) || isDownloaded(itemId)) return@withContext
        if (!networkAllowed()) {
            _jobs.update { it + (itemId to DownloadJob(itemId, name, failed = true, waitingForWifi = true)) }
            return@withContext
        }
        _jobs.update { it + (itemId to DownloadJob(itemId, name)) }
        val dir = storageDir()
        dir.mkdirs()
        val out = File(dir, "$itemId.bin")
        try {
            val conn = URL("$serverAddress/Items/$itemId/Download").openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "MediaBrowser Client=\"Cobalt Wolf\", Device=\"Android\", DeviceId=\"offline\", Version=\"1\", Token=\"$accessToken\"")
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.connect()
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024)
                    var received = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        received += n
                        _jobs.update { it + (itemId to DownloadJob(itemId, name, received, total)) }
                    }
                }
            }
            val item = DownloadedItem(itemId, name, out.absolutePath, out.length())
            _items.update { it.filterNot { e -> e.itemId == itemId } + item }
            saveIndex(_items.value)
            _jobs.update { it - itemId }
        } catch (e: Exception) {
            android.util.Log.w("CobaltWolfOffline", "download $itemId failed", e)
            out.delete()
            _jobs.update { it + (itemId to DownloadJob(itemId, name, failed = true)) }
        }
    }

    private fun saveIndex(list: List<DownloadedItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.itemId).put("name", it.name).put("path", it.path).put("size", it.sizeBytes))
        }
        prefs.edit().putString("index", arr.toString()).apply()
    }

    private fun loadIndex(): List<DownloadedItem> {
        val raw = prefs.getString("index", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DownloadedItem(o.getString("id"), o.getString("name"), o.getString("path"), o.getLong("size"))
            }.filter { File(it.path).exists() }
        }.getOrDefault(emptyList())
    }
}
