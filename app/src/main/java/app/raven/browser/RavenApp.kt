package app.raven.browser

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import app.raven.browser.data.Database
import app.raven.browser.data.Profiles
import app.raven.browser.data.Settings
import app.raven.browser.downloads.DownloadManager
import app.raven.browser.engine.Engine
import app.raven.browser.engine.MediaControls
import app.raven.browser.engine.RavenVpn
import app.raven.browser.engine.TabManager
import app.raven.browser.ui.browser.Greetings
import app.raven.browser.ui.sky.Sky
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.StorageController
import java.io.File
import java.time.LocalDate

class Container(app: Application) {
    val settings = Settings(app)
    val db = Database(app)
    val downloads = DownloadManager(app, db, settings)
    val engine = Engine(app, settings)
    val blockedToday = BlockedCounter(app)
    val media = MediaControls(app)
    /** The people using Raven, each with their own sign-ins, history and tabs. */
    val profiles = Profiles(app)
    val tabs = TabManager(app, engine, db, settings, downloads, media, profiles)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Private tabs are locked (the lock is on and you left Raven); a fingerprint or the screen lock opens them. */
    val privateLocked = MutableStateFlow(false)
    /** All of Raven is locked (its lock is on, and Raven was just started or away long enough). */
    val appLocked = MutableStateFlow(settings.current.appLock)
    /** When Raven last went out of sight (elapsed time), or 0 while it's on screen. */
    var leftAt = 0L
    /** The home screen's wallpaper, a new one each time Raven opens. */
    val sky = Sky(app, settings)
    /** The home page's greeting, also a new one each time Raven opens. */
    val greetings = Greetings(app)
    /** Raven went into the background; the next time it shows, the sky and the greeting change. */
    var wasAway = false
    /** Whether a VPN (your Proton VPN, or any other) carries the phone's traffic right now. */
    val vpn = VpnWatch(app)
    /** Raven's own VPN (WireGuard with your Proton VPN locations), for Raven's traffic only. */
    val ravenVpn = RavenVpn(app)

    init {
        downloads.runtime = engine.runtime
        tabs.onBlocked = { blockedToday.add(it) }
        if (settings.current.eraseOnClose) eraseBrowsingData(history = true, cookies = true, cache = true)
        tabs.restore()
        engine.start()
        scope.launch { db.loadBookmarked() }
    }

    /**
     * Everything a backup file holds: settings (home sites among them), profiles, the everyday tabs, history and
     * bookmarks. Never sign-ins to websites, and never the VPN's location files.
     */
    suspend fun backupContents(): org.json.JSONObject {
        val open = tabs.savedState()
        return org.json.JSONObject()
            .put("raven", BuildConfig.VERSION_NAME)
            .put("made", System.currentTimeMillis())
            .put("settings", settings.export())
            .put("profiles", profiles.export())
            .put("tabs", open)
            .put("history", db.exportHistory())
            .put("bookmarks", db.exportBookmarks())
    }

    /**
     * Puts a backup back. History and bookmarks are added to what's here; tabs, settings and profiles take the place
     * of today's. Raven must restart afterwards, so every part starts from them.
     */
    suspend fun restoreBackup(o: org.json.JSONObject) {
        o.optJSONArray("history")?.let { db.importHistory(it) }
        o.optJSONArray("bookmarks")?.let { db.importBookmarks(it) }
        o.optJSONArray("profiles")?.let { profiles.import(it) }
        o.optJSONObject("settings")?.let { settings.import(it) }
        o.optJSONObject("tabs")?.let { tabs.replaceSavedState(it) }
    }

    fun eraseBrowsingData(history: Boolean, cookies: Boolean, cache: Boolean) {
        var flags = 0L
        if (cookies) flags = flags or StorageController.ClearFlags.SITE_DATA
        if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
        if (flags != 0L) engine.runtime.storageController.clearData(flags)
        if (history) scope.launch { db.clearHistory() }
    }
}

/** Follows Android's own answer to "is a VPN on?", for the VPN square in the menu. */
class VpnWatch(app: Application) {
    private val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
    val on = MutableStateFlow(check())

    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    on.value = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                }
                override fun onLost(network: android.net.Network) { on.value = check() }
            })
        }
    }

    private fun check(): Boolean = runCatching {
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    companion object {
        const val PROTON = "ch.protonvpn.android"

        /** Proton VPN if it's on the phone; otherwise Android's VPN settings. */
        fun open(context: android.content.Context) {
            val launch = context.packageManager.getLaunchIntentForPackage(PROTON)
            runCatching { context.startActivity(launch ?: android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS)) }
        }
    }
}

/** "Blocked today" on the new tab page: sums uBlock Origin's per-page counts for the current day. */
class BlockedCounter(app: Application) {
    private val sp = app.getSharedPreferences("blocked", 0)
    val count = MutableStateFlow(read())

    private fun read(): Int = if (sp.getString("day", "") == LocalDate.now().toString()) sp.getInt("n", 0) else 0

    fun add(n: Int) {
        if (n <= 0) return
        val today = LocalDate.now().toString()
        val base = if (sp.getString("day", "") == today) sp.getInt("n", 0) else 0
        val next = base + n
        sp.edit().putString("day", today).putInt("n", next).apply()
        count.value = next
    }
}

class RavenApp : Application() {
    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // Gecko's helper processes also start this class; only the main process runs the browser.
        if (processName() == packageName) container = Container(this)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::container.isInitialized) return
        // Low memory while in use (older Android), or Raven is in the background and the system needs
        // memory. Not merely on leaving the app, so a quick switch away doesn't reload every tab.
        val low = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        if (low || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) container.tabs.sleepAllBackground()
    }

    private fun processName(): String? =
        if (Build.VERSION.SDK_INT >= 28) getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000') }.getOrNull()
}

/** Keeps the last crash's stack trace so a test build can report it. */
object CrashLog {
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { File(app.filesDir, "last-crash.txt").writeText("Raven ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}, ${thread.name}\n${e.stackTraceToString()}") }
            previous?.uncaughtException(thread, e)
        }
    }

    fun read(app: Application): String? = File(app.filesDir, "last-crash.txt").takeIf { it.exists() }?.readText()
    fun clear(app: Application) = File(app.filesDir, "last-crash.txt").delete()
}
