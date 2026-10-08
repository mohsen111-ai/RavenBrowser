package app.raven.browser.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.raven.browser.data.Database
import app.raven.browser.data.Profile
import app.raven.browser.data.Profiles
import app.raven.browser.data.Settings
import app.raven.browser.data.LinksInApps
import app.raven.browser.data.SitePermissions
import app.raven.browser.downloads.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.util.UUID

sealed interface TabEvent {
    class Message(val text: String) : TabEvent
    class OpenExternal(val intent: Intent, val fallbackUrl: String? = null, val tabId: String? = null) : TabEvent
    class ContextMenu(val tabId: String, val element: ContentDelegate.ContextElement) : TabEvent
    /** A link that has its own app on the phone: "Open in <app>" is offered while the page loads in Raven. */
    class OfferApp(val intent: Intent, val app: String) : TabEvent
    /** Tabs you closed: "Tab closed · Undo" shows for a few seconds. */
    class Closed(val batch: Long, val count: Int) : TabEvent
}

/** All open tabs: creating, switching, sleeping, crash recovery, and restoring them after a restart. */
class TabManager(
    private val context: Context,
    private val engine: Engine,
    private val db: Database,
    private val settings: Settings,
    private val downloads: DownloadManager,
    private val media: MediaControls,
    val profiles: Profiles,
    private val sitePermissions: SitePermissions,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()
    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()
    val selected: BrowserTab? get() = _tabs.value.firstOrNull { it.id == _selectedId.value }

    private val _profile = MutableStateFlow("")
    /** The profile you're in: the one the tab on screen belongs to, and where new tabs open. */
    val profile: StateFlow<String> = _profile.asStateFlow()

    val events = MutableSharedFlow<TabEvent>(extraBufferCapacity = 16)
    val prompts = MutableStateFlow<List<UiPrompt>>(emptyList())

    /**
     * Set by the app (VPN per site): for a site about to load on screen, a wait while the VPN switches to where that
     * site goes, or null when nothing needs to change.
     */
    var vpnGate: ((String?) -> GeckoResult<AllowOrDeny>?)? = null
    /** Whether a page of this site would have to wait for the VPN to change countries. */
    var vpnWaits: ((String?) -> Boolean)? = null
    /** Set by the app: another tab came on screen (the VPN follows its site). */
    var onShown: ((BrowserTab) -> Unit)? = null

    /** Called with how many more requests uBlock Origin blocked (feeds "blocked today"). */
    var onBlocked: ((Int) -> Unit)? = null

    /** Set by the activity: text-selection toolbar (copy, paste, share). */
    var selectionDelegateFactory: (() -> GeckoSession.SelectionActionDelegate)? = null

    /**
     * Raven is on screen (set by the activity). While it's away, pages the phone stopped wait to be reopened until it
     * comes back, and no page reopens as active, so nothing loads in the background.
     */
    var inFront = false
        private set

    /** Raven came back on screen: the pages on it whose engine was stopped meanwhile load again where they were. */
    fun onAppShown() {
        inFront = true
        val kept = keptAwake()
        _tabs.value.filter { it.id in kept && it.asleep.value }.forEach { tab ->
            Log.i("Raven", "back on screen: reopening ${tab.id.take(6)}")
            wake(tab)
        }
    }

    fun onAppHidden() {
        inFront = false
        saveNow()
    }

    private val stateFile = File(context.filesDir, "tabs.json")
    /** How far a page must scroll one way before its bar hides or comes back (the engine counts in CSS pixels). */
    private val scrollThreshold = 48

    init {
        engine.tabs = this
        media.onTabPlays = ::pauseOthers
    }

    // ------------------------------------------------------------------ lifecycle

    fun restore() {
        val saved = runCatching { JSONObject(String(android.util.AtomicFile(stateFile).readFully())) }.getOrNull()
        val list = saved?.optJSONArray("tabs")
        if (list != null && list.length() > 0 && !settings.current.eraseOnClose) {
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                // A tab of a profile that's since been removed goes to the first profile.
                val profile = o.optString("profile").takeIf { id -> profiles.all.value.any { it.id == id } } ?: ""
                val tab = createTab(private = false, id = o.optString("id", UUID.randomUUID().toString()), profile = profile)
                tab.url.value = o.optString("url")
                tab.title.value = o.optString("title")
                tab.startedFromNewTab = o.optBoolean("fromNewTab")
                tab.openerId = o.optString("opener").ifBlank { null }
                tab.flock.value = o.optString("flock").ifBlank { null }
                tab.state = o.optString("state").takeIf { it.isNotBlank() }?.let { runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull() }
                tab.asleep.value = true
                _tabs.value = _tabs.value + tab
            }
            val sel = saved.optString("selected")
            select(_tabs.value.firstOrNull { it.id == sel }?.id ?: _tabs.value.last().id)
        } else {
            newTab(select = true)
        }
        main.postDelayed(sleeper, 60_000)
    }

    private val sleeper = object : Runnable {
        override fun run() {
            sleepIdleTabs(settings.current.sleepAfterMinutes * 60_000L)
            closeOldTabs()
            main.postDelayed(this, 60_000)
        }
    }

    // ------------------------------------------------------------------ tabs

    fun newTab(
        url: String? = null,
        private: Boolean = false,
        select: Boolean = true,
        open: Boolean = true,
        fromApp: Boolean = false,
        openerId: String? = null,
        flock: String? = null,
        profile: String = _profile.value,
    ): BrowserTab {
        val tab = createTab(private, profile = profile)
        tab.openedFromApp = fromApp
        tab.openerId = openerId
        if (!private) tab.flock.value = flock
        // VPN per site: a page opened behind the one on screen whose site has a country of its own waits, unloaded,
        // until you open it (it then loads once the VPN is there), rather than loading now by the wrong way out.
        val waits = url != null && open && !select && vpnWaits?.invoke(Uri.parse(url).host) == true
        if (open && url != null && !waits) openSession(tab)
        if (!open) adoptWhenOpen(tab)
        val list = _tabs.value.toMutableList()
        val at = list.indexOfFirst { it.id == _selectedId.value }
        if (at >= 0 && url != null) list.add(at + 1, tab) else list.add(tab)
        _tabs.value = list
        // On screen first, then loaded: a page in front waits for its site's VPN country, one behind doesn't.
        if (url != null) tab.url.value = url
        if (select) select(tab.id)
        if (url != null) { if (waits) tab.asleep.value = true else load(tab, url, fromNewTab = false) }
        persist()
        return tab
    }

    private fun createTab(private: Boolean, id: String = UUID.randomUUID().toString(), profile: String = ""): BrowserTab {
        // Each profile keeps its own sign-ins and site data (an engine context); private tabs keep nothing anyway.
        val owner = if (private) "" else profile
        val session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(private)
                .contextId(Profile.contextOf(owner))
                .suspendMediaWhenInactive(false)
                .build(),
        )
        val tab = BrowserTab(id, private, session, owner)
        wire(tab)
        return tab
    }

    private fun openSession(tab: BrowserTab) {
        if (tab.session.isOpen) return
        tab.session.open(engine.runtime)
        onOpened(tab)
    }

    /** Add-on hooks, text selection and focus for a session that just opened. */
    private fun onOpened(tab: BrowserTab) {
        engine.attachSession(tab.session)
        engine.helper.attach(tab.session)
        selectionDelegateFactory?.let { tab.session.selectionActionDelegate = it() }
        // In the background (a page reopened after the phone stopped it) it waits inactive until Raven is back.
        val active = tab.id in keptAwake() && inFront
        tab.session.setActive(active)
        engine.runtime.webExtensionController.setTabActive(tab.session, active)
        tab.opened.value++
        enforceAwakeLimit()
    }

    /**
     * How many tabs may stay loaded besides the one on screen. Android 14+ no longer warns apps when
     * memory runs low, so Raven sets its own ceiling from the phone's memory.
     */
    private val awakeLimit: Int = run {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        val gb = info.totalMem / (1024.0 * 1024 * 1024)
        when {
            gb < 3.5 -> 2
            gb < 5.5 -> 3
            gb < 7.5 -> 5
            gb < 11.5 -> 7
            else -> 9
        }
    }

    /** Puts the least recently used background tabs to sleep once more than [awakeLimit] are loaded. */
    private fun enforceAwakeLimit() {
        // Private tabs stay loaded: sleeping one would end its private session and sign it out.
        val kept = keptAwake()
        val awake = _tabs.value.filter { it.id !in kept && !it.private && !it.asleep.value && it.session.isOpen && !it.playing.value }
        if (awake.size <= awakeLimit) return
        awake.sortedBy { it.lastActive }.take(awake.size - awakeLimit).forEach { sleep(it) }
    }

    /** Pop-ups and add-on tabs: Gecko opens the session we hand it, so pick it up once it has. */
    private fun adoptWhenOpen(tab: BrowserTab) {
        main.post(object : Runnable {
            var tries = 0
            override fun run() {
                if (tab !in _tabs.value) return
                if (tab.session.isOpen) onOpened(tab) else if (++tries < 100) main.postDelayed(this, 50)
            }
        })
    }

    /** Called when the activity (re)creates its text-selection toolbar. */
    fun refreshSelectionDelegates() {
        val f = selectionDelegateFactory ?: return
        _tabs.value.forEach { if (it.session.isOpen) it.session.selectionActionDelegate = f() }
    }

    fun attachExtension(ext: WebExtension) {
        _tabs.value.forEach { if (it.session.isOpen) engine.attachSession(it.session, ext) }
    }

    /** Raven's helper is ready: it listens in every open tab (new ones get it when they open). */
    fun attachHelper() {
        _tabs.value.forEach { if (it.session.isOpen) engine.helper.attach(it.session) }
    }

    /** [fromNewTab]: typed or picked on the new tab page, so Back on this page leads to the new tab page. */
    fun load(tab: BrowserTab, url: String, fromNewTab: Boolean = tab.isNewTabPage) {
        openSession(tab)
        // Leaving the new tab page Back led to: like Chrome, the pages that were ahead of it are gone.
        // Purging keeps the page under the new tab page, so the new page takes its place in the history.
        // From the page Home showed, the history stays: Back returns to the page you were on before.
        val fromHome = tab.ntpOverlay.value && tab.overlayFromHome
        val fresh = tab.ntpOverlay.value && !fromHome
        if (tab.ntpOverlay.value) {
            tab.ntpOverlay.value = false
            tab.overlayFromHome = false
        }
        if (fresh) tab.session.purgeHistory()
        if (fromNewTab) tab.startedFromNewTab = true
        tab.asleep.value = false
        tab.url.value = url
        tab.expectingLoad = true
        tab.directLoad = true
        if (fresh) tab.session.load(GeckoSession.Loader().uri(url).flags(GeckoSession.LOAD_FLAGS_REPLACE_HISTORY))
        else tab.session.loadUri(url)
    }

    fun select(id: String) {
        val target = _tabs.value.firstOrNull { it.id == id } ?: return
        val now = SystemClock.elapsedRealtime()
        // The floating tab, picked: it comes back full size.
        if (_floatingId.value == id) dropFloat(target)
        // A tab outside the split: the split ends, and the half that isn't picked rests like any other tab.
        _split.value?.let { s -> if (id != s.top && id != s.bottom) dropSplit(except = null) }
        selected?.let { prev ->
            if (prev.id != id) {
                prev.lastActive = now
                if (prev.session.isOpen && prev.id !in keptAwake()) {
                    prev.session.setActive(false)
                    engine.runtime.webExtensionController.setTabActive(prev.session, false)
                }
            }
        }
        // In front before it wakes, so its page loads as the one on screen (VPN per site waits for its country).
        _selectedId.value = id
        if (target.asleep.value) wake(target)
        if (target.session.isOpen) {
            target.session.setActive(true)
            engine.runtime.webExtensionController.setTabActive(target.session, true)
        }
        target.lastActive = now
        if (!target.private) _profile.value = target.profile
        onShown?.invoke(target)
        enforceAwakeLimit()
        persist()
    }

    /**
     * The tab [step] places after (+1) or before (-1) [tab] among tabs of the same kind (everyday or
     * private), in the order the Tabs screen shows them; null at either end.
     */
    fun neighbour(tab: BrowserTab, step: Int): BrowserTab? {
        val same = _tabs.value.filter { it.private == tab.private && it.profile == tab.profile }
        val i = same.indexOfFirst { it.id == tab.id }
        return if (i < 0) null else same.getOrNull(i + step)
    }

    /** The tab [tab] was opened from, while it is still open. */
    fun opener(tab: BrowserTab): BrowserTab? = tab.openerId?.let { id -> _tabs.value.firstOrNull { it.id == id } }

    /** Back on the first page of a tab a link opened: like Chrome, the tab closes and the one it came from shows. */
    fun returnToOpener(tab: BrowserTab) {
        val opener = opener(tab) ?: return
        select(opener.id)
        close(tab.id)
    }

    fun selectBySession(session: GeckoSession) {
        _tabs.value.firstOrNull { it.session == session }?.let { select(it.id) }
    }

    /** [undoable]: you closed it yourself, so "Tab closed · Undo" shows for a few seconds. */
    fun close(id: String, undoable: Boolean = false) {
        val list = _tabs.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = list[index]
        val wasSelected = _selectedId.value == id
        if (_floatingId.value == id) { _floatingId.value = null; floatParked.value = false }
        val otherHalf = _split.value?.let { s -> when (id) { s.top -> s.bottom; s.bottom -> s.top; else -> null } }
        if (otherHalf != null) { _split.value = null; find(otherHalf)?.let { setMuted(it, false) } }
        dropPrompts(setOf(tab.id))
        shut(tab, undoable)
        val rest = list - tab
        _tabs.value = rest
        var standIn: String? = null
        if (wasSelected) {
            // The next tab of the same kind and profile; after the last private tab, back to everyday tabs; after a
            // profile's last tab, a fresh one in that profile.
            val sameKind = rest.filter { it.private == tab.private && it.profile == tab.profile && it.id != _floatingId.value }
            val next = otherHalf?.let(::find) ?: sameKind.getOrNull(index.coerceAtMost(sameKind.lastIndex))
                ?: if (tab.private) rest.lastOrNull { !it.private && it.profile == _profile.value && it.id != _floatingId.value } else null
            if (next != null) select(next.id) else standIn = newTab(select = true, profile = if (tab.private) _profile.value else tab.profile).id
        }
        if (undoable) keepForUndo(listOf(index to tab), if (wasSelected) id else null, standIn)
        persist()
    }

    fun closeBySession(session: GeckoSession) {
        _tabs.value.firstOrNull { it.session == session }?.let { close(it.id) }
    }

    /** Closes every tab (Clean slate, erase on close), or every private one. */
    fun closeAll(private: Boolean? = null, undoable: Boolean = false) =
        closeGroup(_tabs.value.filter { private == null || it.private == private }.map { it.id }.toSet(), undoable)

    /** Closes these tabs together (a flock, the tabs on one side of the Tabs screen), with one Undo for all of them. */
    fun closeTabs(ids: Set<String>) {
        if (ids.size == 1) close(ids.first(), undoable = true) else closeGroup(ids, undoable = true)
    }

    private fun closeGroup(ids: Set<String>, undoable: Boolean) {
        // Clean slate or a profile going: tabs closed a moment ago go for good too, so their Undo can't bring them back.
        if (!undoable) closed?.let { finishClosed(it.batch) }
        val all = _tabs.value
        val (gone, keep) = all.partition { it.id in ids }
        if (gone.isEmpty()) return
        val selectedGone = _selectedId.value?.takeIf { it in ids }
        val was = gone.firstOrNull { it.id == selectedGone }
        if (_floatingId.value in ids) { _floatingId.value = null; floatParked.value = false }
        _split.value?.let { s -> if (s.top in ids || s.bottom in ids) { _split.value = null; keep.forEach { setMuted(it, false) } } }
        dropPrompts(ids)
        gone.forEach { shut(it, undoable) }
        _tabs.value = keep
        var standIn: String? = null
        if (keep.none { it.id == _selectedId.value }) {
            // Closing everyday tabs never drops you into a private one, nor into another profile: a fresh new tab
            // instead. After the private tabs, back to the everyday ones.
            val profile = (if (was == null || was.private) _profile.value else was.profile).takeIf { p -> profiles.all.value.any { it.id == p } } ?: ""
            val next = keep.lastOrNull { !it.private && it.profile == profile }
            if (next != null) select(next.id) else standIn = newTab(select = true, profile = profile).id
        }
        if (undoable) keepForUndo(all.withIndex().filter { it.value.id in ids }.map { it.index to it.value }, selectedGone, standIn)
        persist()
    }

    // ------------------------------------------------------------------ Undo

    /**
     * Tabs you just closed, set aside for a few seconds: paused and out of sight, but not shut, so Undo brings each one
     * back exactly as it was (the place on the page, what was typed, the sign-ins of a private tab). [standIn]: the
     * new tab that took the screen because nothing was left; Undo takes it away again if it's still untouched.
     */
    private class Closed(val batch: Long, val tabs: List<Pair<Int, BrowserTab>>, val selected: String?, val standIn: String?)
    private var closed: Closed? = null
    private var batches = 0L

    /** A tab leaves the list: shut at once, or (for Undo) quietened and kept until [finishClosed]. */
    private fun shut(tab: BrowserTab, keep: Boolean) {
        media.forget(tab)
        Displays.release(tab.session)
        if (!keep) {
            if (tab.session.isOpen) tab.session.close()
            return
        }
        engine.helper.pause(tab.session)
        tab.media?.pause()
        tab.playing.value = false
        if (tab.session.isOpen) {
            tab.session.setActive(false)
            engine.runtime.webExtensionController.setTabActive(tab.session, false)
        }
    }

    private fun keepForUndo(tabs: List<Pair<Int, BrowserTab>>, selected: String?, standIn: String?) {
        closed?.let { finishClosed(it.batch) }
        val batch = ++batches
        closed = Closed(batch, tabs, selected, standIn)
        events.tryEmit(TabEvent.Closed(batch, tabs.size))
        // However the bar ends, the tabs are shut for good within half a minute.
        main.postDelayed({ finishClosed(batch) }, 30_000)
    }

    /** The Undo bar went away: the tabs it offered are shut for good. */
    fun finishClosed(batch: Long) {
        val c = closed?.takeIf { it.batch == batch } ?: return
        closed = null
        c.tabs.forEach { (_, t) -> if (t.session.isOpen) t.session.close() }
    }

    /** Undo: the tabs come back where they were, and the one you were on is on screen again. */
    fun undoClose(batch: Long) {
        val c = closed?.takeIf { it.batch == batch } ?: return
        closed = null
        // A tab of a profile removed meanwhile stays gone.
        val gone = c.tabs.filter { (_, t) -> !t.private && t.profile.isNotEmpty() && profiles.all.value.none { it.id == t.profile } }
        gone.forEach { (_, t) -> if (t.session.isOpen) t.session.close() }
        if (gone.size == c.tabs.size) return
        if (gone.isNotEmpty()) return undoKept(Closed(c.batch, c.tabs - gone.toSet(), c.selected?.takeIf { id -> gone.none { it.second.id == id } }, c.standIn))
        undoKept(c)
    }

    private fun undoKept(c: Closed) {
        val list = _tabs.value.toMutableList()
        // The fresh new tab that stood in, if you haven't used it.
        c.standIn?.let { id -> list.firstOrNull { it.id == id }?.takeIf { it.hasNoPage && !it.loading.value } }?.let { t ->
            list.remove(t)
            Displays.release(t.session)
            if (t.session.isOpen) t.session.close()
        }
        c.tabs.sortedBy { it.first }.forEach { (i, t) -> list.add(i.coerceIn(0, list.size), t) }
        _tabs.value = list
        if (c.tabs.any { it.second.flock.value != null }) _flocksVersion.value++
        c.selected?.let { select(it) } ?: run { if (list.none { it.id == _selectedId.value }) select(c.tabs.first().second.id) }
        persist()
    }

    /** Puts tabs you haven't looked at for a while to sleep; they reload where they were when opened. */
    fun sleepIdleTabs(idleMs: Long, includePrivate: Boolean = false): Int {
        if (idleMs <= 0) return 0
        val now = SystemClock.elapsedRealtime()
        val kept = keptAwake()
        var n = 0
        _tabs.value.forEach { tab ->
            if (tab.id in kept || tab.asleep.value || !tab.session.isOpen) return@forEach
            if (tab.private && !includePrivate) return@forEach
            if (tab.playing.value) return@forEach
            if (now - tab.lastActive < idleMs) return@forEach
            sleep(tab)
            n++
        }
        return n
    }

    /** The system is short of memory: everything but the tab on screen sleeps, private ones too. */
    fun sleepAllBackground() = sleepIdleTabs(1, includePrivate = true)

    private fun sleep(tab: BrowserTab) {
        dropPrompts(setOf(tab.id))
        forgetPage(tab)
        tab.asleep.value = true
        Displays.release(tab.session)
        tab.session.close()
    }

    /** What a page that's gone can no longer report: it isn't playing, and its video isn't fullscreen any more. */
    private fun forgetPage(tab: BrowserTab) {
        tab.playing.value = false
        media.forget(tab)
        tab.media = null
        tab.fullscreen.value = false
        tab.wideVideo.value = null
        tab.videoSize.value = null
    }

    private fun wake(tab: BrowserTab) {
        openSession(tab)
        tab.asleep.value = false
        val state = tab.state
        when {
            state != null -> { tab.expectingLoad = true; tab.session.restoreState(state) }
            tab.url.value.isNotBlank() && tab.url.value != "about:blank" -> { tab.expectingLoad = true; tab.session.loadUri(tab.url.value) }
        }
    }

    private fun closeOldTabs() {
        val days = settings.current.closeAfterDays
        if (days <= 0) return
        val cutoff = SystemClock.elapsedRealtime() - days * 86_400_000L
        val kept = keptAwake()
        _tabs.value.filter { it.id !in kept && it.lastActive < cutoff }.forEach { close(it.id) }
    }

    // ------------------------------------------------------------------ floating tab and split screen

    private fun find(id: String): BrowserTab? = _tabs.value.firstOrNull { it.id == id }

    private val _floatingId = MutableStateFlow<String?>(null)
    /** The tab floating in a small window over the others (one at a time), or null. */
    val floatingId: StateFlow<String?> = _floatingId.asStateFlow()
    /** The floating window waits on the edge of the screen as a small icon, its video paused. */
    val floatParked = MutableStateFlow(false)

    /** Two tabs sharing the screen: [top] above (or left), [bottom] below (or right). */
    class Split(val top: String, val bottom: String)
    private val _split = MutableStateFlow<Split?>(null)
    val split: StateFlow<Split?> = _split.asStateFlow()

    /** Whose sound you hear in split screen. Both halves keep playing either way. */
    enum class SplitSound { BOTH, TOP, BOTTOM }
    private val _splitSound = MutableStateFlow(SplitSound.BOTH)
    val splitSound: StateFlow<SplitSound> = _splitSound.asStateFlow()

    /** Tabs on screen now: they may play while another tab plays. */
    fun onScreenIds(): Set<String> = buildSet {
        _selectedId.value?.let(::add)
        _split.value?.let { add(it.top); add(it.bottom) }
        if (!floatParked.value) _floatingId.value?.let(::add)
    }

    /** Tabs that stay loaded and are never put to sleep: those on screen, and the floating tab even when parked. */
    private fun keptAwake(): Set<String> = onScreenIds() + listOfNotNull(_floatingId.value)

    /** One tab plays at a time, like one speaker in a room, except tabs on screen together. */
    private fun pauseOthers(playing: BrowserTab) {
        val onScreen = onScreenIds()
        _tabs.value.filter { it !== playing && it.id !in onScreen && it.session.isOpen }.forEach {
            if (it.playing.value || it.media != null) Log.i("Raven", "media: pausing ${it.id.take(6)} (helper ${if (engine.helper.reaches(it.session)) "reaches it" else "doesn't reach it"})")
            engine.helper.pause(it.session)
            it.media?.pause()
        }
    }

    fun setMuted(tab: BrowserTab, on: Boolean) {
        tab.muted.value = on
        engine.helper.mute(tab.session, on)
    }

    /** Floats [id] in a small window; the screen under it shows the tab you were on before (or a new one). */
    fun float(id: String): Boolean {
        val tab = find(id) ?: return false
        if (tab.private) {
            events.tryEmit(TabEvent.Message("Private tabs can't float"))
            return false
        }
        _floatingId.value?.takeIf { it != id }?.let { unfloat() }
        _split.value?.let { s -> if (id == s.top || id == s.bottom) dropSplit(except = if (id == s.top) s.bottom else s.top) }
        floatParked.value = false
        _floatingId.value = id
        if (tab.asleep.value) wake(tab)
        if (tab.session.isOpen) tab.session.setActive(true)
        if (_selectedId.value == id) {
            val under = _tabs.value.filter { it.id != id && !it.private }.maxByOrNull { it.lastActive }
            if (under != null) select(under.id) else newTab(select = true)
        }
        return true
    }

    /** The floating window closes. [fullSize]: the tab comes back on screen; otherwise it pauses and rests. */
    fun unfloat(fullSize: Boolean = false) {
        val tab = _floatingId.value?.let(::find) ?: run { _floatingId.value = null; return }
        if (fullSize) { select(tab.id); return }
        dropFloat(tab)
        engine.helper.pause(tab.session)
        tab.media?.pause()
        if (tab.session.isOpen && tab.id != _selectedId.value) tab.session.setActive(false)
    }

    /** The floating state goes; the tab itself stays as it is. */
    private fun dropFloat(tab: BrowserTab) {
        _floatingId.value = null
        floatParked.value = false
        setMuted(tab, false)
        engine.helper.videoOnly(tab.session, false)
        if (tab.fullscreen.value) tab.session.exitFullScreen()
    }

    /** Parks the floating window on the edge as an icon (pausing its video), or brings it back. */
    fun parkFloat(on: Boolean) {
        val tab = _floatingId.value?.let(::find) ?: return
        floatParked.value = on
        if (on) {
            engine.helper.pause(tab.session)
            tab.media?.pause()
        }
        if (tab.session.isOpen) tab.session.setActive(!on || tab.playing.value)
    }

    /** The tab on screen and [otherId] share the screen: the one on screen on top, [otherId] below. */
    fun split(otherId: String): Boolean {
        val a = selected ?: return false
        val b = find(otherId) ?: return false
        if (a.id == b.id) return false
        if (a.private != b.private) {
            events.tryEmit(TabEvent.Message("A private tab can only share the screen with another private tab"))
            return false
        }
        _floatingId.value?.let { f -> if (f == a.id || f == b.id) find(f)?.let(::dropFloat) }
        _splitSound.value = SplitSound.BOTH
        _split.value = Split(a.id, b.id)
        if (b.asleep.value) wake(b)
        if (b.session.isOpen) {
            b.session.setActive(true)
            engine.runtime.webExtensionController.setTabActive(b.session, false)
        }
        b.lastActive = SystemClock.elapsedRealtime()
        return true
    }

    /** Split screen ends; [keep] (the half on screen by default) stays, the other half pauses and rests. */
    fun endSplit(keep: String? = _selectedId.value) {
        val s = _split.value ?: return
        dropSplit(except = keep)
        if (keep != null && keep != _selectedId.value) select(keep)
    }

    private fun dropSplit(except: String?) {
        val s = _split.value ?: return
        _split.value = null
        listOf(s.top, s.bottom).mapNotNull(::find).forEach { half ->
            setMuted(half, false)
            if (half.id != except && half.id != _selectedId.value && half.id != _floatingId.value) {
                engine.helper.pause(half.session)
                half.media?.pause()
                if (half.session.isOpen) half.session.setActive(false)
            }
        }
    }

    fun setSplitSound(sound: SplitSound) {
        val s = _split.value ?: return
        _splitSound.value = sound
        find(s.top)?.let { setMuted(it, sound == SplitSound.BOTTOM) }
        find(s.bottom)?.let { setMuted(it, sound == SplitSound.TOP) }
    }

    // ------------------------------------------------------------------ profiles

    /** Switches to a profile (on the Tabs screen): new tabs open in it from now on. */
    fun useProfile(id: String) {
        if (profiles.all.value.any { it.id == id }) _profile.value = id
    }

    /** A profile goes with everything it kept: its tabs close, and its sign-ins, site data and history are erased. */
    fun removeProfile(id: String) {
        if (id.isEmpty()) return
        profiles.remove(id)
        if (_profile.value == id) _profile.value = ""
        closeGroup(_tabs.value.filter { !it.private && it.profile == id }.map { it.id }.toSet(), undoable = false)
        Profile.contextOf(id)?.let { runCatching { engine.runtime.storageController.clearDataForSessionContext(it) } }
        scope.launch { db.clearHistory(profile = id) }
    }

    // ------------------------------------------------------------------ flocks (tab groups)

    private val _flocksVersion = MutableStateFlow(0)
    /** Goes up whenever a tab joins or leaves a flock, or a flock is renamed. */
    val flocksVersion: StateFlow<Int> = _flocksVersion.asStateFlow()

    /** Everyday tabs only: private tabs never join a flock. */
    fun setFlock(ids: Collection<String>, flock: String?) {
        val name = flock?.trim()?.ifEmpty { null }
        _tabs.value.filter { it.id in ids && !it.private }.forEach { it.flock.value = name }
        _flocksVersion.value++
        persist()
    }

    /** A flock's tabs: flocks belong to a profile, and two profiles may each have one with the same name. */
    private fun flockTabs(flock: String, profile: String) = _tabs.value.filter { !it.private && it.profile == profile && it.flock.value == flock }

    fun renameFlock(old: String, new: String, profile: String = _profile.value) {
        val name = new.trim().ifEmpty { return }
        flockTabs(old, profile).forEach { it.flock.value = name }
        _flocksVersion.value++
        persist()
    }

    /** The tabs stay; they just stop flying together. */
    fun ungroup(flock: String, profile: String = _profile.value) = setFlock(flockTabs(flock, profile).map { it.id }, null)

    fun closeFlock(flock: String, profile: String = _profile.value) {
        closeTabs(flockTabs(flock, profile).map { it.id }.toSet())
        _flocksVersion.value++
    }


    // ------------------------------------------------------------------ persistence

    private val persistRunnable = Runnable { writeState() }

    fun persist() {
        main.removeCallbacks(persistRunnable)
        main.postDelayed(persistRunnable, 1500)
    }

    /** A backup's tabs were put in place: nothing more is saved over them before Raven restarts. */
    @Volatile private var frozen = false

    /** One write at a time, in order. */
    private val writer = Dispatchers.IO.limitedParallelism(1)

    private fun writeState() {
        if (frozen) return
        if (settings.current.eraseOnClose) { stateFile.delete(); return }
        // Made here, written there; written whole or not at all, so a phone closing Raven halfway through a write
        // can't leave a broken file that loses every tab.
        val text = savedState().toString()
        scope.launch(writer) {
            val file = android.util.AtomicFile(stateFile)
            var out: java.io.FileOutputStream? = null
            try {
                out = file.startWrite()
                out.write(text.toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                out?.let { file.failWrite(it) }
                Log.w("Raven", "couldn't save the tabs", e)
            }
        }
    }

    /** Saves the tabs now (Raven is leaving the screen, and the phone may close it any time). */
    fun saveNow() {
        main.removeCallbacks(persistRunnable)
        writeState()
    }

    /** The everyday tabs as Raven keeps them between starts (also what a backup holds). */
    fun savedState(): JSONObject {
        val arr = JSONArray()
        _tabs.value.filter { !it.private && !it.hasNoPage }.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("url", t.url.value)
                put("title", t.title.value)
                put("fromNewTab", t.startedFromNewTab)
                t.openerId?.let { put("opener", it) }
                t.flock.value?.let { put("flock", it) }
                if (t.profile.isNotEmpty()) put("profile", t.profile)
                t.state?.let { put("state", it.toString()) }
            })
        }
        val sel = selected?.takeIf { !it.private }?.id
        return JSONObject().put("tabs", arr).put("selected", sel ?: "")
    }

    /** Puts a backup's tabs where Raven finds them when it starts; Raven restarts right after. */
    fun replaceSavedState(json: JSONObject) {
        frozen = true
        main.removeCallbacks(persistRunnable)
        val file = android.util.AtomicFile(stateFile)
        val out = file.startWrite()
        try {
            out.write(json.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    // ------------------------------------------------------------------ add-on actions

    fun onTabAction(session: GeckoSession, ext: WebExtension, action: WebExtension.Action) {
        if (ext.id != Engine.UBO_ID) return
        val tab = _tabs.value.firstOrNull { it.session == session } ?: return
        val n = action.badgeText?.trim()?.toIntOrNull() ?: 0
        val before = tab.blocked.value
        tab.blocked.value = n
        if (n > before) onBlocked?.invoke(n - before)
    }

    // ------------------------------------------------------------------ prompts

    private fun enqueue(p: UiPrompt) {
        prompts.value = prompts.value + p
    }

    fun finish(p: UiPrompt) {
        prompts.value = prompts.value - p
    }

    /** Turns down what these tabs were still asking, before their pages go away. */
    private fun dropPrompts(ids: Set<String>) {
        val (gone, keep) = prompts.value.partition { it.tabId in ids }
        if (gone.isEmpty()) return
        gone.forEach { it.decline() }
        prompts.value = keep
    }

    // ------------------------------------------------------------------ delegates

    private fun wire(tab: BrowserTab) {
        val s = tab.session
        // Translation happens on the phone (Firefox's own engine); Raven only hears what the page is in and how
        // the translation is going. (A speed-test copy of Raven runs without it, to measure what it costs.)
        if (app.raven.browser.BuildConfig.SPEED_VARIANT != "notranslate") s.translationsSessionDelegate = object : org.mozilla.geckoview.TranslationsController.SessionTranslation.Delegate {
            override fun onOfferTranslate(session: GeckoSession) { tab.offerTranslate.value = true }
            override fun onExpectedTranslate(session: GeckoSession) { tab.offerTranslate.value = true }
            override fun onTranslationStateChange(session: GeckoSession, state: org.mozilla.geckoview.TranslationsController.SessionTranslation.TranslationState?) {
                tab.translation.value = state
            }
        }
        s.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.resetScroll()
                tab.loading.value = true
                tab.progress.value = 5
                tab.secure.value = null
                tab.blocked.value = 0
                tab.offerTranslate.value = false
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading.value = false
                tab.progress.value = 100
                if (tab.insecureAllowed) {
                    tab.insecureAllowed = false
                    engine.restoreHttpsOnly()
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress.value = progress.coerceIn(5, 100)
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: ProgressDelegate.SecurityInformation) {
                tab.secure.value = securityInfo.isSecure
            }

            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                tab.state = sessionState
                persist()
            }
        }

        s.navigationDelegate = object : NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                val u = url ?: return
                if (u.startsWith(ErrorPages.PREFIX)) return
                if (u == "about:blank" && tab.expectingLoad) return
                tab.expectingLoad = false
                tab.directLoad = false
                tab.url.value = u
                tab.committedUrl = u
                if (!tab.private && u.startsWith("http")) scope.launch { db.recordVisit(u, tab.title.value, tab.profile) }
                persist()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack.value = canGoBack }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canGoForward.value = canGoForward }

            override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? =
                handleLoad(tab, request.uri, request)

            override fun onSubframeLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val scheme = Uri.parse(request.uri).scheme?.lowercase()
                return if (scheme == "intent" || scheme == "market") GeckoResult.fromValue(AllowOrDeny.DENY) else null
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val t = newTab(private = tab.private, select = true, open = false, profile = tab.profile)
                t.url.value = uri
                t.openedByPage = true
                t.openerId = tab.id
                t.expectingLoad = true
                return GeckoResult.fromValue(t.session)
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String> =
                GeckoResult.fromValue(ErrorPages.dataUri(uri, error))
        }

        s.contentDelegate = object : ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val t = title.orEmpty()
                if (t.startsWith("data:")) return
                tab.title.value = t
                val u = tab.url.value
                if (!tab.private && u.startsWith("http")) scope.launch { db.updateTitle(u, t, tab.profile) }
            }

            override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: ContentDelegate.ContextElement) {
                events.tryEmit(TabEvent.ContextMenu(tab.id, element))
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val isAddon = response.uri.substringBefore('?').endsWith(".xpi") ||
                    response.headers.entries.any { it.key.equals("Content-Type", true) && it.value.startsWith("application/x-xpinstall") }
                if (isAddon) {
                    response.body?.close()
                    engine.install(response.uri)
                } else {
                    downloads.start(response, tab.private)?.let { events.tryEmit(TabEvent.Message("Downloading ${it.name}")) }
                }
                // A download doesn't replace the page, so show the page's own address again.
                if (tab.committedUrl.isBlank() && tab.openedByPage && _tabs.value.size > 1) {
                    close(tab.id)
                } else {
                    tab.url.value = tab.committedUrl
                    tab.loading.value = false
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                tab.fullscreen.value = fullScreen
                if (!fullScreen) { tab.wideVideo.value = null; tab.videoSize.value = null }
            }

            override fun onCrash(session: GeckoSession) = recover(tab, crashed = true)
            override fun onKill(session: GeckoSession) = recover(tab, crashed = false)
            override fun onCloseRequest(session: GeckoSession) = close(tab.id)
        }

        // Also feeds Android's media controls (notification, lock screen, headphone buttons).
        s.mediaSessionDelegate = media.delegate(tab)

        // Scrolling down a page tucks its bar away in split screen; scrolling back up brings it out again.
        s.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) = tab.onScrolled(scrollY, scrollThreshold)
        }

        s.permissionDelegate = object : PermissionDelegate {
            override fun onContentPermissionRequest(session: GeckoSession, perm: PermissionDelegate.ContentPermission): GeckoResult<Int> {
                val result = GeckoResult<Int>()
                enqueue(UiPrompt.Permission(tab.id, hostOf(perm.uri), perm, result))
                return result
            }

            override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: PermissionDelegate.Callback) {
                enqueue(UiPrompt.AndroidPermissions(tab.id, permissions.orEmpty().toList(), callback))
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out PermissionDelegate.MediaSource>?,
                audio: Array<out PermissionDelegate.MediaSource>?,
                callback: PermissionDelegate.MediaCallback,
            ) {
                val host = hostOf(uri)
                val v = video.orEmpty().toList()
                val a = audio.orEmpty().toList()
                // What you told Raven to remember for this site (everyday tabs only): no question, the same answer.
                if (!tab.private) {
                    val kinds = listOfNotNull(SitePermissions.Kind.CAMERA.takeIf { v.isNotEmpty() }, SitePermissions.Kind.MICROPHONE.takeIf { a.isNotEmpty() })
                    val answers = kinds.map { sitePermissions.get(host, it) }
                    if (answers.isNotEmpty() && answers.all { it != null }) {
                        runCatching { if (answers.all { it == true }) callback.grant(v.firstOrNull(), a.firstOrNull()) else callback.reject() }
                        return
                    }
                }
                enqueue(UiPrompt.Media(tab.id, host, v, a, callback))
            }
        }

        s.promptDelegate = object : PromptDelegate {
            private fun ask(prompt: PromptDelegate.BasePrompt): GeckoResult<PromptDelegate.PromptResponse> {
                val result = GeckoResult<PromptDelegate.PromptResponse>()
                val ui = UiPrompt.Page(tab.id, tab.host, prompt, result)
                prompt.setDelegate(object : PromptDelegate.PromptInstanceDelegate {
                    override fun onPromptDismiss(prompt: PromptDelegate.BasePrompt) {
                        ui.withdraw()
                        finish(ui)
                    }
                })
                enqueue(ui)
                return result
            }

            override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) = ask(prompt)
            override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) = ask(prompt)
            override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) = ask(prompt)
            override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt) = ask(prompt)
            override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) = ask(prompt)
            override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt) = ask(prompt)
            override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt) = ask(prompt)
            override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt) = ask(prompt)
            override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) = ask(prompt)
            override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt) = ask(prompt)

            override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptDelegate.PromptResponse> {
                events.tryEmit(TabEvent.Message("Pop-up blocked"))
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
            }

            override fun onSharePrompt(session: GeckoSession, prompt: PromptDelegate.SharePrompt): GeckoResult<PromptDelegate.PromptResponse> {
                val text = listOfNotNull(prompt.title, prompt.text, prompt.uri).joinToString("\n")
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                events.tryEmit(TabEvent.OpenExternal(Intent.createChooser(intent, null)))
                return GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.SUCCESS))
            }
        }
    }

    private fun handleLoad(tab: BrowserTab, uri: String, request: NavigationDelegate.LoadRequest? = null): GeckoResult<AllowOrDeny>? {
        if (uri.startsWith(ErrorPages.ALLOW_HTTP)) {
            val target = Uri.parse(uri).getQueryParameter("u") ?: return GeckoResult.fromValue(AllowOrDeny.DENY)
            tab.insecureAllowed = true
            engine.allowInsecureOnce()
            main.post { tab.session.loadUri(target) }
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        val parsed = Uri.parse(uri)
        if (parsed.path.orEmpty().endsWith(".xpi") && (parsed.scheme == "https" || parsed.scheme == "file")) {
            engine.install(uri)
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        when (parsed.scheme?.lowercase()) {
            "http", "https" -> {
                request?.let { appLink(tab, parsed, it) }?.let { return it }
                // VPN per site: the page on screen waits a moment while the VPN goes where its site goes.
                return if (request != null && tab.id == _selectedId.value) vpnGate?.invoke(parsed.host) else null
            }
            null, "about", "data", "blob", "moz-extension", "resource", "file", "view-source", "javascript" -> return null
            "intent" -> {
                val intent = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
                if (intent != null) {
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    intent.component = null
                    intent.selector = null
                    val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    events.tryEmit(TabEvent.OpenExternal(intent, fallback, tab.id))
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            else -> {
                val intent = Intent(Intent.ACTION_VIEW, parsed).addCategory(Intent.CATEGORY_BROWSABLE)
                events.tryEmit(TabEvent.OpenExternal(intent))
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }
    }

    /**
     * Open links in apps (Settings): a link you follow to another site that has its own app on the phone opens there
     * (Always), or the app is offered while the page loads in Raven (Ask first). Never from a private tab, never for an
     * address you typed, and never within the same site.
     */
    private fun appLink(tab: BrowserTab, uri: Uri, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
        if (request.hasUserGesture) tab.directLoad = false
        val mode = settings.current.linksInApps
        if (mode == LinksInApps.NEVER || tab.private || request.isDirectNavigation) return null
        if (!request.hasUserGesture && !request.isRedirect) return null
        // An address you typed or picked that redirects to another site (twitter.com to x.com) stays in Raven.
        if (request.isRedirect && tab.directLoad) return null
        if (request.target != NavigationDelegate.TARGET_WINDOW_CURRENT && request.target != NavigationDelegate.TARGET_WINDOW_NEW) return null
        if (siteOf(uri.host) == siteOf(Uri.parse(tab.url.value).host)) return null
        val (intent, name) = appFor(uri) ?: return null
        return if (mode == LinksInApps.ALWAYS) {
            events.tryEmit(TabEvent.OpenExternal(intent))
            GeckoResult.fromValue(AllowOrDeny.DENY)
        } else {
            events.tryEmit(TabEvent.OfferApp(intent, name))
            null
        }
    }

    /** The app on the phone (not a browser, not Raven) that opens this link, and its name. */
    private fun appFor(uri: Uri): Pair<Intent, String>? {
        val pm = context.packageManager
        val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val handlers = runCatching { pm.queryIntentActivities(view, 0) }.getOrDefault(emptyList())
        if (handlers.isEmpty()) return null
        // Browsers open every web address; a site's own app opens its own. An address no site has finds the browsers.
        val browsers = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://raven.invalid/")).addCategory(Intent.CATEGORY_BROWSABLE), 0)
        }.getOrDefault(emptyList()).map { it.activityInfo.packageName }.toSet()
        val app = handlers.firstOrNull { it.activityInfo.packageName !in browsers && it.activityInfo.packageName != context.packageName } ?: return null
        val name = runCatching { pm.getApplicationLabel(app.activityInfo.applicationInfo).toString() }.getOrDefault("the app")
        return view.setClassName(app.activityInfo.packageName, app.activityInfo.name) to name
    }

    /** A site, without its subdomains (m.youtube.com and www.youtube.com are both youtube.com; bbc.co.uk stays whole). */
    private fun siteOf(host: String?): String = RavenVpn.siteOf(host.orEmpty())

    /**
     * The engine's process for this page crashed ([crashed]) or the phone stopped it for memory (often while Raven is
     * in another app; one process runs all pages, so every tab gets this at once). Every tab on screen (the one in
     * front, both halves of split screen, the floating tab) reopens where it was; the others sleep until opened. While
     * Raven is away they wait for it to come back ([onAppShown]), so nothing loads in the background.
     */
    private fun recover(tab: BrowserTab, crashed: Boolean) {
        main.post {
            if (_tabs.value.none { it === tab }) return@post  // closed meanwhile
            if (tab.asleep.value && !tab.session.isOpen) return@post  // already handled
            Log.i("Raven", "page engine ${if (crashed) "crashed" else "stopped"}: ${tab.id.take(6)}, Raven ${if (inFront) "on screen" else "away"}")
            dropPrompts(setOf(tab.id))
            forgetPage(tab)
            // Let the page view go first, so the reopened session can be shown again.
            Displays.release(tab.session)
            if (tab.session.isOpen) tab.session.close()
            tab.asleep.value = true
            if (inFront && tab.id in keptAwake()) {
                wake(tab)
                if (crashed && tab.id == _selectedId.value) events.tryEmit(TabEvent.Message("The page stopped working and was reloaded"))
            }
        }
    }

    private fun hostOf(uri: String) = Uri.parse(uri).host?.removePrefix("www.") ?: uri
}
