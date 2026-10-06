package app.raven.browser.engine

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

/** One place Raven can browse from: a WireGuard file from your Proton VPN account. */
class VpnPlace(val id: String, val name: String, val country: String?) {
    /** The country's name in your language, or the file's name when it doesn't say. */
    val label: String get() = country?.let { Locale("", it).displayCountry.ifBlank { null } } ?: name
    /** The country's flag (an emoji from its two-letter code), or a globe. */
    val flag: String get() = country?.let { c -> c.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) } } ?: "🌐"
}

/**
 * Raven's own VPN, for Raven alone: WireGuard (the protocol Proton VPN uses) with the files you download from your
 * Proton account, one per country. The files hold your private key; they stay in Raven's private storage, which
 * Android never backs up (allowBackup is off), and are never shown or sent anywhere.
 */
class RavenVpn(private val app: Application) {
    private val dir = File(app.filesDir, "vpn").apply { mkdirs() }
    private val index = File(dir, "places.json")
    private val backend by lazy { GoBackend(app) }
    private val tunnel = object : Tunnel {
        override fun getName() = "raven"
        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.DOWN) _active.value = null
        }
    }

    private val _places = MutableStateFlow(read())
    val places: StateFlow<List<VpnPlace>> = _places.asStateFlow()

    private val _active = MutableStateFlow<String?>(null)
    /** The place Raven is browsing from right now, or null when its VPN is off. */
    val active: StateFlow<String?> = _active.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The last place used, for the switch. */
    var last: String?
        get() = app.getSharedPreferences("vpn", 0).getString("last", null)
        private set(v) { app.getSharedPreferences("vpn", 0).edit().putString("last", v).apply() }

    /** Adds a WireGuard file; null when it isn't one. */
    suspend fun add(uri: Uri): VpnPlace? = withContext(Dispatchers.IO) {
        val text = runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull() ?: return@withContext null
        // Checked now, so a broken file is refused here rather than when you switch it on.
        runCatching { Config.parse(text.byteInputStream()) }.getOrElse { return@withContext null }
        val file = displayName(uri)?.substringBeforeLast('.') ?: "Location"
        val place = VpnPlace(UUID.randomUUID().toString(), file, countryOf(file))
        File(dir, "${place.id}.conf").writeText(text)
        _places.value = _places.value + place
        write()
        place
    }

    fun rename(id: String, name: String) {
        _places.value = _places.value.map { if (it.id == id) VpnPlace(it.id, name.trim().ifEmpty { it.name }, countryOf(name) ?: it.country) else it }
        write()
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        if (_active.value == id) turnOff()
        File(dir, "$id.conf").delete()
        _places.value = _places.value.filter { it.id != id }
        write()
    }

    /**
     * Browse from [id]. Android must have allowed Raven to run a VPN first (VpnService.prepare); otherwise this
     * fails and says so. Only Raven's traffic goes through it.
     */
    suspend fun turnOn(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            val text = File(dir, "$id.conf").readText()
            val config = Config.parse(onlyRaven(text).byteInputStream())
            backend.setState(tunnel, Tunnel.State.UP, config)
            _active.value = id
            last = id
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("Raven", "vpn on failed: ${e.javaClass.simpleName}")
            _active.value = null
            Result.failure(e)
        } finally {
            _busy.value = false
        }
    }

    suspend fun turnOff() = withContext(Dispatchers.IO) {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        _active.value = null
    }

    /** The file as downloaded, told to carry Raven's traffic only (and nothing else on the phone). */
    private fun onlyRaven(text: String): String {
        val lines = text.lines().filterNot { it.trim().startsWith("IncludedApplications", true) || it.trim().startsWith("ExcludedApplications", true) }
        val at = lines.indexOfFirst { it.trim().equals("[Interface]", true) }
        if (at < 0) return text
        return (lines.take(at + 1) + "IncludedApplications = ${app.packageName}" + lines.drop(at + 1)).joinToString("\n")
    }

    private fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun read(): List<VpnPlace> = runCatching {
        val arr = JSONArray(index.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            VpnPlace(o.getString("id"), o.getString("name"), o.optString("country").ifBlank { null })
        }.filter { File(dir, "${it.id}.conf").exists() }
    }.getOrDefault(emptyList())

    private fun write() {
        val arr = JSONArray()
        _places.value.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("country", it.country ?: "")) }
        runCatching { index.writeText(arr.toString()) }
    }

    companion object {
        private val countries = Locale.getISOCountries().toSet()

        /**
         * The country a Proton file is for, from its name: "phone-NL-87", "wg-US-FREE-28", "CH-NL#1" (Secure Core:
         * the last one is where you come out). Two-letter codes only; "UK" means Great Britain.
         */
        fun countryOf(name: String): String? = name.split('-', '_', ' ', '#', '.', '(', ')')
            .map { if (it == "UK") "GB" else it }
            .lastOrNull { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } && it in countries }
    }
}
