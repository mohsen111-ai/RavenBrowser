package app.raven.browser

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import app.raven.browser.data.Database
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
    val tabs = TabManager(app, engine, db, settings, downloads, media)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Private tabs are locked (the lock is on and you left Raven); a fingerprint or the screen lock opens them. */
    val privateLocked = MutableStateFlow(false)
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
            CrashLog.noteLastExit(this)
            container = Container(this)
        }
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

    /**
     * Android remembers why Raven's last process ended. A crash inside the engine, being killed for memory or a
     * frozen screen leave no Kotlin stack trace, so write down what Android says, for the same crash report.
     */
    fun noteLastExit(app: Application) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val am = app.getSystemService(android.app.ActivityManager::class.java)
            val exits = am.getHistoricalProcessExitReasons(app.packageName, 0, 8)
                .filter { it.processName == app.packageName }
            val seen = app.getSharedPreferences("exits", 0)
            val last = exits.firstOrNull { it.timestamp > seen.getLong("seen", 0) && it.reason !in IGNORED_EXITS }
            seen.edit().putLong("seen", exits.maxOfOrNull { it.timestamp } ?: 0).apply()
            if (last == null) return
            val reason = when (last.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "crash"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash in native code"
                android.app.ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
                android.app.ApplicationExitInfo.REASON_SIGNALED -> "killed by signal ${last.status}"
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "used too many resources"
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
                android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a service it needs died"
                else -> "reason ${last.reason}"
            }
            val trace = if (last.reason == android.app.ApplicationExitInfo.REASON_ANR || last.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE)
                runCatching { last.traceInputStream?.bufferedReader()?.use { it.readText().take(6000) } }.getOrNull().orEmpty()
            else ""
            File(app.filesDir, "last-exit.txt").writeText(
                "Raven ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}\n" +
                    "Last time Raven ended: $reason\nAt: ${java.util.Date(last.timestamp)}\n" +
                    "Android's note: ${last.description}\nMemory (RSS): ${last.rss / 1024} MB, PSS: ${last.pss / 1024} MB\n" +
                    "It was ${if (last.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) "on screen" else "in the background"}\n$trace",
            )
        }
    }

    private val IGNORED_EXITS = setOf(
        android.app.ApplicationExitInfo.REASON_EXIT_SELF,
        android.app.ApplicationExitInfo.REASON_USER_REQUESTED,
        android.app.ApplicationExitInfo.REASON_USER_STOPPED,
        android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE,
    )

    fun read(app: Application): String? =
        listOf("last-crash.txt", "last-exit.txt").mapNotNull { n -> File(app.filesDir, n).takeIf { it.exists() }?.readText() }
            .takeIf { it.isNotEmpty() }?.joinToString("\n\n")

    fun clear(app: Application) {
        File(app.filesDir, "last-crash.txt").delete()
        File(app.filesDir, "last-exit.txt").delete()
    }
}
