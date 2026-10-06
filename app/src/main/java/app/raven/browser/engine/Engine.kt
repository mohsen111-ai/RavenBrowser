package app.raven.browser.engine

import android.content.Context
import android.util.Log
import app.raven.browser.data.DnsProvider
import app.raven.browser.data.Isolation
import app.raven.browser.data.Prefs
import app.raven.browser.data.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.OrientationController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class InstallRequest(
    val extension: WebExtension,
    val permissions: List<String>,
    val origins: List<String>,
    private val result: GeckoResult<WebExtension.PermissionPromptResponse>,
) {
    private val answered = AtomicBoolean(false)

    /** Answers once; the engine rejects a second answer, which a quick double tap would send. */
    fun respond(allow: Boolean, inPrivate: Boolean) {
        if (answered.compareAndSet(false, true)) {
            runCatching { result.complete(WebExtension.PermissionPromptResponse(allow, allow && inPrivate, false)) }
        }
    }
}

class PopupRequest(val extension: WebExtension, val session: GeckoSession)

/** Owns the single Gecko engine instance and the installed add-ons. */
class Engine(private val context: Context, private val settings: Settings) {
    val runtime: GeckoRuntime
    /** Raven's built-in helper: mutes, pauses or shows only the video in a tab (split screen, floating tab). */
    val helper: Helper
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val _addons = MutableStateFlow<List<WebExtension>>(emptyList())
    val addons: StateFlow<List<WebExtension>> = _addons.asStateFlow()
    val ubo: WebExtension? get() = _addons.value.firstOrNull { it.id == UBO_ID }

    val installRequest = MutableStateFlow<InstallRequest?>(null)
    val popup = MutableStateFlow<PopupRequest?>(null)

    /** Latest browser-action state per add-on (the uBlock Origin badge shows blocked requests). */
    val actions = MutableStateFlow<Map<String, WebExtension.Action>>(emptyMap())

    lateinit var tabs: TabManager

    init {
        val prefs = settings.current
        val builder = GeckoRuntimeSettings.Builder()
            .contentBlocking(contentBlocking(prefs))
            .allowInsecureConnections(if (prefs.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL)
            .globalPrivacyControlEnabled(true)
            .preferredColorScheme(if (prefs.darkWebsites) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
            .fontSizeFactor(prefs.textScale / 100f)
            .extensionsProcessEnabled(true)
            .lowMemoryDetection(true)
            .loginAutofillEnabled(false)
            .consoleOutput(false)
            .remoteDebuggingEnabled(false)
            .aboutConfigEnabled(false)
        when (prefs.dns.uri) {
            null -> builder.trustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF)
            else -> builder.trustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_FIRST).trustedRecursiveResolverUri(prefs.dns.uri)
        }
        builder.fissionEnabled(prefs.isolation != Isolation.OFF)
        builder.configFilePath(writeEnginePrefs(prefs))
        runtime = GeckoRuntime.create(context, builder.build())
        helper = Helper(runtime)
        helper.consent = prefs.cookiePopups
        setUpExtensions()
        runtime.orientationController.delegate = object : OrientationController.OrientationDelegate {
            override fun onOrientationLock(aOrientation: Int): GeckoResult<AllowOrDeny> {
                android.util.Log.i("Raven", "screen: page locks the orientation ($aOrientation)")
                orientationLock.value = aOrientation
                return GeckoResult.allow()
            }
            override fun onOrientationUnlock() {
                android.util.Log.i("Raven", "screen: page unlocks the orientation")
                orientationLock.value = null
            }
        }
    }

    /**
     * The way a page asked to turn the screen (an ActivityInfo orientation), or null. Gecko lets go of it when
     * the page is hidden, e.g. while you're in another app; the screen itself is turned in RavenRoot.
     */
    val orientationLock = MutableStateFlow<Int?>(null)

    private fun contentBlocking(p: Prefs) = ContentBlocking.Settings.Builder()
        .antiTracking(if (p.strictTracking) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT)
        .enhancedTrackingProtectionLevel(if (p.strictTracking) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
        .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
        .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
        .queryParameterStrippingEnabled(true)
        .queryParameterStrippingPrivateBrowsingEnabled(true)
        .build()

    /**
     * Process and memory tuning measured on a real phone: one shared web process, no spare
     * processes started in advance, fewer cached pages. With site isolation for logged-in sites,
     * only sites you sign in to get their own process.
     */
    private fun writeEnginePrefs(p: Prefs): String {
        val prefs = mutableMapOf<String, Any>(
            "dom.ipc.processCount" to 1,
            "dom.ipc.processPrelaunch.enabled" to false,
            "dom.ipc.processPrelaunch.fission.number" to 0,
            "browser.sessionhistory.max_total_viewers" to 3,
            // A wide video going fullscreen asks for landscape (MainActivity turns the screen).
            "media.videocontrols.lock-video-orientation" to true,
            // The engine's own "one tab plays at a time" paused the other half of split screen when one half
            // started (emulator run 23). Raven does it itself (TabManager.pauseOthers), with tabs on screen together
            // (split screen, the floating tab) allowed to play at once.
            "media.audioFocus.management" to false,
        )
        when (p.isolation) {
            Isolation.LOGGED_IN -> prefs["fission.webContentIsolationStrategy"] = 2
            Isolation.ALL -> prefs["fission.webContentIsolationStrategy"] = 1
            Isolation.OFF -> Unit
        }
        val file = File(context.filesDir, "gecko-config.yaml")
        file.writeText(buildString {
            appendLine("prefs:")
            prefs.forEach { (k, v) -> appendLine("  $k: $v") }
        })
        return file.absolutePath
    }

    /** Settings Gecko can change while running. The rest need an app restart. */
    fun applyLiveSettings(p: Prefs) {
        val s = runtime.settings
        s.contentBlocking.setAntiTracking(if (p.strictTracking) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT)
        s.contentBlocking.setEnhancedTrackingProtectionLevel(if (p.strictTracking) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
        s.setAllowInsecureConnections(if (p.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL)
        s.setPreferredColorScheme(if (p.darkWebsites) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
        s.setFontSizeFactor(p.textScale / 100f)
        helper.consent = p.cookiePopups
        when (val uri = p.dns.uri) {
            null -> s.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF)
            else -> {
                s.setTrustedRecursiveResolverUri(uri)
                s.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_FIRST)
            }
        }
    }

    // ---------------------------------------------------------------- warming up connections

    private val web by lazy { org.mozilla.geckoview.GeckoWebExecutor(runtime) }
    /** Sites connected to ahead of time, and when: each one at most every 30 seconds. */
    private val warmed = LinkedHashMap<String, Long>()

    /**
     * Starts connecting to [url]'s site before it's opened (looking up its address and opening the secure
     * connection), as Firefox does while you type: on a slow line that's often a second saved when you press Go.
     * Everyday tabs only; the caller leaves private tabs out.
     */
    fun warmUp(url: String) {
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return
        if (uri.scheme != "https" && uri.scheme != "http") return
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val last = warmed[host]
        if (last != null && now - last < 30_000) return
        warmed.remove(host)
        warmed[host] = now
        if (warmed.size > 64) warmed.remove(warmed.keys.first())
        Log.i("Raven", "warm up: $host")
        runCatching { web.speculativeConnect("${uri.scheme}://$host/") }
    }

    /** Lets one page load over plain HTTP after the user chose "open anyway"; HTTPS-only comes back after it loads. */
    fun allowInsecureOnce() {
        runtime.settings.setAllowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
    }

    fun restoreHttpsOnly() {
        if (settings.current.httpsOnly) runtime.settings.setAllowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
    }

    // ---------------------------------------------------------------- add-ons

    private val autoAccept = mutableSetOf<String>()

    private fun setUpExtensions() {
        val controller = runtime.webExtensionController
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                if (extension.id in autoAccept) {
                    autoAccept.remove(extension.id)
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, false))
                }
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                // One question at a time: an unanswered earlier install is cancelled.
                installRequest.value?.respond(allow = false, inPrivate = false)
                installRequest.value = InstallRequest(extension, permissions.toList(), origins.toList(), result)
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<String>,
                newOrigins: Array<String>,
                newDataCollectionPermissions: Array<String>,
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(AllowOrDeny.ALLOW)

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(AllowOrDeny.ALLOW)
        })
        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) { attach(extension); refresh() }
            override fun onReady(extension: WebExtension) { attach(extension); refresh() }
            override fun onUninstalled(extension: WebExtension) = refresh()
            override fun onEnabled(extension: WebExtension) = refresh()
            override fun onDisabled(extension: WebExtension) = refresh()
            override fun onInstallationFailed(extension: WebExtension?, installException: WebExtension.InstallException) {
                Log.w(TAG, "install failed ${extension?.id} ${installException.code}")
            }
        })
    }

    /** Called once tabs exist: attaches add-ons, installs uBlock Origin on first run, checks for updates daily. */
    fun start() {
        // (A speed-test copy of Raven runs without it, to measure what it costs.)
        if (app.raven.browser.BuildConfig.SPEED_VARIANT != "nohelper") helper.install { tabs.attachHelper() }
        runtime.webExtensionController.list().accept({ list ->
            list.orEmpty().forEach { attach(it) }
            _addons.value = list.orEmpty().filter { !it.isBuiltIn }
            if (list.orEmpty().none { it.id == UBO_ID }) installUbo()
            val now = System.currentTimeMillis()
            if (now - settings.current.addonsLastChecked > 24 * 3_600_000L) updateAll()
        }, { e -> Log.w(TAG, "list failed", e) })
    }

    fun installUbo() {
        autoAccept += UBO_ID
        install(UBO_XPI, quiet = true)
    }

    fun install(url: String, quiet: Boolean = false) {
        if (!quiet) messages.tryEmit("Getting the add-on…")
        runtime.webExtensionController.install(url).accept({ ext ->
            ext?.let { attach(it) }
            refresh()
            if (!quiet && ext != null) messages.tryEmit("${ext.metaData.name ?: "Add-on"} added")
        }, { e ->
            val cancelled = (e as? WebExtension.InstallException)?.code == WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED
            if (!cancelled) messages.tryEmit(if (quiet) "Couldn't install uBlock Origin. It will retry next time." else "Couldn't add the add-on")
        })
    }

    fun setEnabled(ext: WebExtension, enabled: Boolean) {
        val c = runtime.webExtensionController
        val r = if (enabled) c.enable(ext, WebExtensionController.EnableSource.USER) else c.disable(ext, WebExtensionController.EnableSource.USER)
        r.accept({ refresh() }, { refresh() })
    }

    fun setAllowedInPrivate(ext: WebExtension, allowed: Boolean) {
        runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, allowed).accept({ refresh() }, { refresh() })
    }

    fun uninstall(ext: WebExtension) {
        runtime.webExtensionController.uninstall(ext).accept({ refresh() }, { refresh() })
    }

    fun updateAll(onDone: ((Int) -> Unit)? = null) {
        val list = _addons.value
        var pending = list.size
        var updated = 0
        if (pending == 0) onDone?.invoke(0)
        list.forEach { ext ->
            runtime.webExtensionController.update(ext).accept({ newer ->
                if (newer != null) updated++
                if (--pending == 0) { refresh(); onDone?.invoke(updated) }
            }, { if (--pending == 0) { refresh(); onDone?.invoke(updated) } })
        }
        settings.update { it.copy(addonsLastChecked = System.currentTimeMillis()) }
    }

    fun refresh() {
        runtime.webExtensionController.list().accept({ list ->
            _addons.value = list.orEmpty().filter { !it.isBuiltIn }
        }, { })
    }

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            if (session == null) {
                actions.value = actions.value + (extension.id to action)
            } else {
                tabs.onTabAction(session, extension, action)
            }
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> = openPopup(extension)
        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> = openPopup(extension)
    }

    private fun openPopup(extension: WebExtension): GeckoResult<GeckoSession> {
        val session = GeckoSession(GeckoSessionSettings.Builder().build())
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                popup.value = null
                session.close()
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                popup.value = null
                return GeckoResult.fromValue(tabs.newTab(select = true, open = false).session)
            }
        }
        session.open(runtime)
        popup.value?.session?.close()
        popup.value = PopupRequest(extension, session)
        return GeckoResult.fromValue(session)
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(source: WebExtension, details: WebExtension.CreateTabDetails): GeckoResult<GeckoSession> {
            popup.value = null
            val tab = tabs.newTab(select = details.active != false, open = false)
            return GeckoResult.fromValue(tab.session)
        }

        override fun onOpenOptionsPage(source: WebExtension) {
            source.metaData.optionsPageUrl?.let { tabs.newTab(url = it, select = true) }
        }
    }

    val sessionTabDelegate = object : WebExtension.SessionTabDelegate {
        override fun onCloseTab(source: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> {
            tabs.closeBySession(session)
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        override fun onUpdateTab(extension: WebExtension, session: GeckoSession, details: WebExtension.UpdateTabDetails): GeckoResult<AllowOrDeny> {
            if (details.active == true) tabs.selectBySession(session)
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }
    }

    private val attached = mutableSetOf<String>()

    private fun attach(ext: WebExtension) {
        ext.setActionDelegate(actionDelegate)
        ext.setTabDelegate(tabDelegate)
        if (attached.add(ext.id) && ::tabs.isInitialized) tabs.attachExtension(ext)
    }

    fun attachSession(session: GeckoSession) {
        runtime.webExtensionController.list().accept({ list ->
            list.orEmpty().forEach { ext ->
                session.webExtensionController.setActionDelegate(ext, actionDelegate)
                session.webExtensionController.setTabDelegate(ext, sessionTabDelegate)
            }
        }, { })
    }

    fun attachSession(session: GeckoSession, ext: WebExtension) {
        session.webExtensionController.setActionDelegate(ext, actionDelegate)
        session.webExtensionController.setTabDelegate(ext, sessionTabDelegate)
    }

    /** Opens uBlock Origin's own panel for the active tab. */
    fun openUboPanel(): Boolean {
        val action = actions.value[UBO_ID] ?: return false
        action.click()
        return true
    }

    companion object {
        const val TAG = "Raven"
        const val UBO_ID = "uBlock0@raymondhill.net"
        const val UBO_XPI = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    }
}
