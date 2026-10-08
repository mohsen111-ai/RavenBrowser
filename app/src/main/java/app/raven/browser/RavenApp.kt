package app.raven.browser

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ComponentCallbacks2
import android.os.Build
import app.raven.browser.data.Database
import app.raven.browser.data.Profiles
import app.raven.browser.data.SitePermissions
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
    /** Camera and microphone answers you asked Raven to remember, per site. */
    val sitePermissions = SitePermissions(app)
    val tabs = TabManager(app, engine, db, settings, downloads, media, profiles, sitePermissions)
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
        // VPN per site: a site with its own country loads only once the VPN is there (or after a few seconds anyway).
        tabs.vpnGate = { host ->
            if (!ravenVpn.needsSwitch(host)) null
            else org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>().also { result ->
                scope.launch {
                    val ok = kotlinx.coroutines.withTimeoutOrNull(6000) { ravenVpn.applyFor(host) }
                    if (ok == false) engine.messages.tryEmit("Couldn't switch the VPN for this site")
                    result.complete(org.mozilla.geckoview.AllowOrDeny.ALLOW)
                }
            }
        }
        tabs.vpnWaits = { host -> ravenVpn.needsSwitch(host) }
        tabs.onShown = { tab ->
            val host = android.net.Uri.parse(tab.url.value).host
            // In split screen one VPN serves both halves: tapping from one half to the other doesn't move it (that
            // cut off what played in the first); a new page in a half still goes where its site goes.
            val split = tabs.split.value
            val half = split != null && (tab.id == split.top || tab.id == split.bottom)
            if (!half && ravenVpn.needsSwitch(host)) scope.launch { ravenVpn.applyFor(host) }
        }
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
        if (processName() == packageName) {
            container = Container(this)
            Thread({ ExitLog.collect(this) }, "raven-exits").start()
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::container.isInitialized) return
        // Low memory while in use, or Android about to close Raven in the background (older Android only).
        // Not on merely leaving Raven: from Android 14 "background" comes on every trip away, and putting every
        // other tab to sleep then made each one reload, and held up the screen just as Raven left. The limit on
        // loaded tabs keeps memory in check instead.
        val low = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        if (low || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) container.tabs.sleepAllBackground()
    }

    private fun processName(): String? =
        if (Build.VERSION.SDK_INT >= 28) getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000') }.getOrNull()
}

/** Keeps what went wrong lately (newest first) so it can be copied from Settings, About Raven, and sent for a fix. */
object CrashLog {
    private const val MAX = 200_000

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { add(app, "Raven ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}, ${thread.name}\n${e.stackTraceToString()}") }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Puts [text] at the top of the report; the oldest part goes once the report gets long. */
    @Synchronized
    fun add(app: Application, text: String) {
        val file = File(app.filesDir, "last-crash.txt")
        val before = if (file.exists()) file.readText() else ""
        file.writeText((if (before.isEmpty()) text else "$text\n\n----------------\n\n$before").take(MAX))
    }

    fun read(app: Application): String? = File(app.filesDir, "last-crash.txt").takeIf { it.exists() }?.readText()
    fun clear(app: Application) = File(app.filesDir, "last-crash.txt").delete()
}

/**
 * Why Raven's processes stopped lately, as Android itself remembers it (Android 11 and later): Raven crashing, the
 * engine crashing, Raven freezing ("isn't responding"), or the phone closing it or one of the engine's helpers for
 * memory. Read once each time Raven starts. When something went wrong, the crash report gets Android's own account:
 * every recent stop, and for a freeze what Raven's main threads were doing at that moment.
 */
object ExitLog {
    fun collect(app: Application) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val sp = app.getSharedPreferences("exits", 0)
            val seen = sp.getLong("seen", 0)
            val exits = app.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(app.packageName, 0, 32)
                .filter { it.timestamp > seen }
                .sortedByDescending { it.timestamp }
            if (exits.isEmpty()) return
            sp.edit().putLong("seen", exits.first().timestamp).apply()
            val bad = exits.filter { it.reason in BAD }
            if (bad.isEmpty()) return
            val time = java.text.SimpleDateFormat("d MMM HH:mm:ss", java.util.Locale.US)
            val text = buildString {
                append("Raven ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("How Raven's processes stopped lately (newest first), as Android remembers it:\n")
                exits.forEach { e ->
                    append("- ${time.format(java.util.Date(e.timestamp))}  ${part(app, e.processName)}: ${reason(e.reason)}")
                    append(" (status ${e.status}, was ${importance(e.importance)}, memory ${e.pss / 1024} MB)")
                    e.description?.takeIf { it.isNotBlank() }?.let { append(": $it") }
                    append('\n')
                }
                // A freeze comes with what every thread was doing; the threads that matter are kept.
                bad.firstOrNull { it.reason == ApplicationExitInfo.REASON_ANR }?.let { anr ->
                    runCatching { anr.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()?.let {
                        append("\nWhat Raven was doing when it froze (${time.format(java.util.Date(anr.timestamp))}):\n")
                        append(threads(it))
                    }
                }
            }
            CrashLog.add(app, text)
        }
    }

    private val BAD = setOf(
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    )

    /** "Raven" for the app itself, else which of the engine's helpers: the one that draws, a page's, ... */
    private fun part(app: Application, name: String): String {
        val helper = name.removePrefix(app.packageName).removePrefix(":")
        return when {
            helper.isEmpty() -> "Raven"
            helper.startsWith("gpu") -> "engine (drawing)"
            helper.startsWith("tab") -> "engine (pages)"
            helper.startsWith("media") -> "engine (media)"
            helper.startsWith("socket") -> "engine (network)"
            else -> "engine ($helper)"
        }
    }

    private fun reason(r: Int) = when (r) {
        ApplicationExitInfo.REASON_ANR -> "FROZE (isn't responding)"
        ApplicationExitInfo.REASON_CRASH -> "CRASHED"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASHED (engine code)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "FAILED TO START"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "STOPPED for using too much"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "closed by the phone for memory"
        ApplicationExitInfo.REASON_SIGNALED -> "stopped by the phone"
        ApplicationExitInfo.REASON_EXIT_SELF -> "closed itself"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by you"
        ApplicationExitInfo.REASON_USER_STOPPED -> "force-stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "stopped because a part it needs stopped"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "stopped after a permission changed"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "updated"
        ApplicationExitInfo.REASON_FREEZER -> "stopped while frozen in the background"
        ApplicationExitInfo.REASON_OTHER -> "stopped by the phone (other)"
        else -> "stopped (reason $r)"
    }

    private fun importance(i: Int) = when {
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "on screen"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "working in the background"
        else -> "in the background"
    }

    /** From Android's freeze report: its header, the main thread, and Gecko's and the drawing threads, briefly. */
    private fun threads(trace: String): String {
        val blocks = trace.split(Regex("\n[ \t]*\n"))
        val keep = blocks.filterIndexed { i, b ->
            i == 0 || b.startsWith("\"main\"") || Regex("^\"(Gecko|Compositor|Renderer|CanvasRenderer|RenderBackend|ImageBridge|IPC I/O|Socket|Raven)", RegexOption.IGNORE_CASE).containsMatchIn(b)
        }
        return keep.joinToString("\n\n") { it.lines().take(60).joinToString("\n") }.take(60_000)
    }
}
