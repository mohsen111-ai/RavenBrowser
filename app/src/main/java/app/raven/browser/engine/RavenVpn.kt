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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Raven itself is turning its VPN on or off (any other change came from outside Raven). */
    @Volatile private var ourChange = false
    /**
     * Another VPN took over (the Proton VPN app, say), or Android withdrew Raven's permission: sites with their own
     * country stop switching Raven's VPN (that would take the VPN back from the other app) until you pick something
     * in Raven's VPN sheet again.
     */
    @Volatile var outsideTookOver = false
        private set
    private val tunnel = object : Tunnel {
        override fun getName() = "raven"
        override fun onStateChange(newState: Tunnel.State) {
            if (newState != Tunnel.State.DOWN) return
            _active.value = null
            // Android took the VPN away (another VPN app started, or its permission was withdrawn): the switch is off
            // now, and no site waits for a country Raven can't reach without asking again.
            if (!ourChange) {
                Log.i("Raven", "vpn: turned off from outside Raven")
                chosen = null
                _routedFor.value = null
                outsideTookOver = true
            }
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

    // ------------------------------------------------------------------ a country per site

    private val sp = app.getSharedPreferences("vpn", 0)
    private val _rules = MutableStateFlow(readRules())
    /** Sites with their own country: site → the place's id, or [DIRECT] for no VPN. */
    val rules: StateFlow<Map<String, String>> = _rules.asStateFlow()

    /** What the switch says (a place, or null for off). A site with its own country doesn't change it. */
    @Volatile var chosen: String? = null
        private set

    private val _routedFor = MutableStateFlow<String?>(null)
    /** The site whose own country (or no VPN) is in use right now, or null when the switch decides. */
    val routedFor: StateFlow<String?> = _routedFor.asStateFlow()

    private val switching = Mutex()

    /** Gives [host]'s site its own place ([DIRECT]: no VPN), or back to following the switch (null). */
    fun setRule(host: String, place: String?) {
        outsideTookOver = false  // you chose in Raven's VPN sheet: Raven's VPN is wanted again
        val site = siteOf(host)
        _rules.value = if (place == null) _rules.value - site else _rules.value + (site to place)
        sp.edit().putString("rules", JSONObject(_rules.value as Map<*, *>).toString()).apply()
    }

    fun ruleFor(host: String?): String? = host?.takeIf { it.isNotBlank() }?.let { _rules.value[siteOf(it)] }

    /** The switch: on at [id], or off (null). Takes effect at once, whatever site is on screen. */
    suspend fun choose(id: String?): Result<Unit> = switching.withLock {
        outsideTookOver = false
        _routedFor.value = null
        if (id == null) { chosen = null; turnOff(); return@withLock Result.success(Unit) }
        // Only a place that worked becomes the switch's; one that failed leaves the switch off.
        turnOn(id).also { chosen = if (it.isSuccess) id else null }
    }

    /** Where [host] goes: its own place, no VPN, or what the switch says. */
    private fun targetFor(host: String?): String? = when (val r = ruleFor(host)) {
        null -> chosen
        DIRECT -> null
        else -> r.takeIf { id -> _places.value.any { it.id == id } } ?: chosen
    }

    /**
     * Whether the VPN must change before [host] loads. Only while some site has its own country (or one did a moment
     * ago): otherwise the switch alone decides, as it always has.
     */
    fun needsSwitch(host: String?): Boolean =
        !outsideTookOver && (_rules.value.isNotEmpty() || _routedFor.value != null) && targetFor(host) != _active.value

    /**
     * The page on screen is now on [host]: the VPN goes where that site goes (its own country, none, or what the
     * switch says). One switch at a time; false when it couldn't (Android hasn't allowed Raven's VPN yet).
     */
    suspend fun applyFor(host: String?): Boolean = switching.withLock {
        val target = targetFor(host)
        _routedFor.value = if (ruleFor(host) != null) host?.let(::siteOf) else null
        if (target == _active.value) return@withLock true
        Log.i("Raven", "vpn: ${if (target == null) "off" else "on"} for ${if (ruleFor(host) != null) "a site with its own country" else "the switch"}")
        if (target == null) { turnOff(); true } else turnOn(target, remember = false).isSuccess
    }

    private fun readRules(): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString("rules", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

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
        if (chosen == id) chosen = null
        // Sites that went through it follow the switch again.
        _rules.value.filterValues { it == id }.keys.forEach { setRule(it, null) }
        File(dir, "$id.conf").delete()
        _places.value = _places.value.filter { it.id != id }
        write()
    }

    /**
     * Browse from [id]. Android must have allowed Raven to run a VPN first (VpnService.prepare); otherwise this
     * fails and says so. Only Raven's traffic goes through it.
     */
    suspend fun turnOn(id: String, remember: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            val text = File(dir, "$id.conf").readText()
            val config = Config.parse(onlyRaven(text).byteInputStream())
            ourChange = true
            try { backend.setState(tunnel, Tunnel.State.UP, config) } finally { ourChange = false }
            _active.value = id
            if (remember) last = id
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("Raven", "vpn on failed: ${e.javaClass.simpleName}")
            // Android no longer lets Raven run a VPN (another app has it): stop trying on every page.
            if (e is com.wireguard.android.backend.BackendException && e.reason == com.wireguard.android.backend.BackendException.Reason.VPN_NOT_AUTHORIZED) outsideTookOver = true
            _active.value = null
            Result.failure(e)
        } finally {
            _busy.value = false
        }
    }

    suspend fun turnOff() = withContext(Dispatchers.IO) {
        ourChange = true
        try { runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) } } finally { ourChange = false }
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
        /** A site's rule saying: no VPN for it. */
        const val DIRECT = "direct"

        /** A site, without "www." or the like: m.youtube.com and youtube.com are one; bbc.co.uk stays bbc.co.uk. */
        fun siteOf(host: String): String {
            val parts = host.lowercase().removePrefix("www.").split('.')
            if (parts.size <= 2) return parts.joinToString(".")
            val keep = if (parts[parts.size - 2].length <= 3 && parts.last().length == 2) 3 else 2
            return parts.takeLast(keep).joinToString(".")
        }

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
