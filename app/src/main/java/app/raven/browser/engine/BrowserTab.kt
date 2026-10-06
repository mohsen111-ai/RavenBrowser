package app.raven.browser.engine

import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/** One tab: its Gecko session plus the observable state the UI shows. [profile]: whose tab it is ("" the first profile). */
class BrowserTab(
    val id: String,
    val private: Boolean,
    val session: GeckoSession,
    val profile: String = "",
) {
    val url = MutableStateFlow("")
    val title = MutableStateFlow("")
    val progress = MutableStateFlow(100)
    val loading = MutableStateFlow(false)
    val canGoBack = MutableStateFlow(false)
    val canGoForward = MutableStateFlow(false)
    val secure = MutableStateFlow<Boolean?>(null)
    val blocked = MutableStateFlow(0)
    val asleep = MutableStateFlow(false)
    /** A video (or the page) is fullscreen. Kept here, not on the screen, so a rebuilt screen still knows. */
    val fullscreen = MutableStateFlow(false)
    /** The video that went fullscreen is wider than tall (null: no fullscreen video, or its shape is unknown). */
    val wideVideo = MutableStateFlow<Boolean?>(null)
    /** Width and height of that video, for the shape of the picture-in-picture window. */
    val videoSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val desktop = MutableStateFlow(false)
    val thumbnail = MutableStateFlow<ImageBitmap?>(null)
    /** Goes up each time the session opens, so the page view picks it up. */
    val opened = MutableStateFlow(0)
    /** Audio or video is playing: the tab keeps running in the background and is never put to sleep. */
    val playing = MutableStateFlow(false)
    /** Back went past the tab's first page: the new tab page shows over it, as a step in the history. */
    val ntpOverlay = MutableStateFlow(false)
    /** The flock (tab group) this tab flies with, if any. */
    val flock = MutableStateFlow<String?>(null)
    /** The page's language and its translation, as the engine last told it (null until a page reports). */
    val translation = MutableStateFlow<org.mozilla.geckoview.TranslationsController.SessionTranslation.TranslationState?>(null)
    /** The page is in a language other than yours and can be translated. */
    val offerTranslate = MutableStateFlow(false)
    /** Raven keeps this tab quiet (its half of split screen, or the floating tab's sound button). It may still play. */
    val muted = MutableStateFlow(false)
    /** The page was scrolled down (its half's bar in split screen steps aside); scrolling up brings it back. */
    val scrolledAway = MutableStateFlow(false)

    @Volatile var state: GeckoSession.SessionState? = null
    @Volatile var lastActive: Long = SystemClock.elapsedRealtime()
    /** The page's audio or video, to pause it when the page goes out of sight. */
    @Volatile var media: MediaSession? = null
    /** Opened by a link from another app: Back closes the tab and returns to that app. */
    var openedFromApp = false
    /** Opened by a page (target=_blank or window.open) rather than by the user. */
    var openedByPage = false
    /** The tab this one was opened from: Back on its first page closes it and returns there. */
    var openerId: String? = null
    /** The first page was opened from the new tab page, so Back on it goes to the new tab page. */
    var startedFromNewTab = false
    /** The address the page on screen actually has; [url] can run ahead while a load starts. */
    var committedUrl = ""
    /** A page is on its way: the blank document a new engine session starts with isn't the address. */
    var expectingLoad = false
    var insecureAllowed = false

    // Where the page was last scrolled to, and how far it has gone since the direction last changed.
    private var lastScrollY = 0
    private var scrollRun = 0
    private var scrollTurnedAt = 0L

    /**
     * The page scrolled to [y] (pixels). A clear move down hides the bar, a clear move up (or the top of the page)
     * brings it back. Changes right after one are ignored: the bar going away resizes the page, which can move it.
     */
    fun onScrolled(y: Int, threshold: Int) {
        val dy = y - lastScrollY
        lastScrollY = y
        val now = SystemClock.elapsedRealtime()
        if (y <= threshold / 3) {
            scrollRun = 0
            scrolledAway.value = false
            return
        }
        if (dy == 0 || now - scrollTurnedAt < 300) return
        scrollRun = if ((dy > 0) == (scrollRun > 0)) scrollRun + dy else dy
        val away = when {
            scrollRun > threshold -> true
            scrollRun < -threshold -> false
            else -> return
        }
        if (away != scrolledAway.value) {
            scrolledAway.value = away
            scrollTurnedAt = now
        }
        scrollRun = 0
    }

    /** A new page: its bar shows again. */
    fun resetScroll() {
        lastScrollY = 0
        scrollRun = 0
        scrolledAway.value = false
    }

    /** No page has been opened in this tab. */
    val hasNoPage: Boolean get() = url.value.isBlank() || url.value == "about:blank"
    /** The new tab page is on screen, either because there is no page or because Back led to it. */
    val isNewTabPage: Boolean get() = hasNoPage || ntpOverlay.value

    /** The new tab page is showing because Home was pressed: Back returns to the page, and the history stays. */
    var overlayFromHome = false

    /** Home: the new tab page over the page you were on, like any other step forward. */
    fun goHome() {
        if (isNewTabPage) return
        overlayFromHome = true
        backToNewTabPage()
    }

    /** Leaves the new tab page Home showed, back to the page under it. */
    fun leaveHome() {
        overlayFromHome = false
        ntpOverlay.value = false
    }

    /** Back on the first page: show the new tab page over it, pausing anything playing. */
    fun backToNewTabPage() {
        media?.pause()
        ntpOverlay.value = true
    }
    val displayTitle: String get() = if (ntpOverlay.value) "New tab" else title.value.ifBlank { host.ifBlank { "New tab" } }
    val host: String get() = runCatching { android.net.Uri.parse(url.value).host.orEmpty().removePrefix("www.") }.getOrDefault("")
}
