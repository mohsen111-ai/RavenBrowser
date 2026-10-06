package app.raven.browser.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import org.mozilla.geckoview.GeckoView

enum class Screen { Browser, Tabs, Downloads, History, Settings, Addons, Bookmarks }

sealed interface Sheet {
    data object Menu : Sheet
    data object SiteInfo : Sheet
    data object Supernova : Sheet
    data object Translate : Sheet
    data object Vpn : Sheet
    /** Pick the tab to share the screen with. */
    data object Split : Sheet
    class LongPress(val tabId: String, val element: ContextElement) : Sheet
}

/** UI-only state: which screen and sheet are showing, address editing, find in page. */
class UiState {
    var screen by mutableStateOf(Screen.Browser)
    var sheet by mutableStateOf<Sheet?>(null)
    var editing by mutableStateOf(false)
    var editText by mutableStateOf("")
    /** Goes up when something outside the field (the pencil) puts [editText] in it, cursor at the end. */
    var editFill by mutableStateOf(0)
    var findOpen by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    /** The tab whose page is fullscreen (the one on screen, a half of split screen, or the floating tab). */
    var fullscreenTabId by mutableStateOf<String?>(null)
    /** The video is playing in a small picture-in-picture window over other apps. */
    var pip by mutableStateOf(false)
    /** The Tabs screen shows its Private side (kept out of screenshots and recent apps when the lock is on). */
    var privateSideShown by mutableStateOf(false)
    /** A bookmark just saved, waiting for its folder (from "Saved to bookmarks · Folder"). */
    var folderFor by mutableStateOf<String?>(null)
    var geckoView: GeckoView? = null

    // The floating tab's window: its top-left corner in pixels (NaN until it first shows), how much bigger or
    // smaller than its usual size, and whether it shows only the page's video (wide, at the video's shape).
    var floatX by mutableStateOf(Float.NaN)
    var floatY by mutableStateOf(Float.NaN)
    var floatScale by mutableStateOf(1f)
    var floatVideoOnly by mutableStateOf(false)
    var floatAspect by mutableStateOf(16f / 9f)
    /** Parked on the edge: which side, and how far down (pixels). */
    var floatParkLeft by mutableStateOf(false)
    var floatParkY by mutableStateOf(Float.NaN)
    /** Split screen: the share of the screen the top (or left) half takes. */
    var splitRatio by mutableStateOf(0.5f)
    val snackbar = SnackbarHostState()

    fun go(screen: Screen) {
        sheet = null
        editing = false
        this.screen = screen
    }
}
