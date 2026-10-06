package app.raven.browser.engine

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.SelectionActionDelegate.Selection

/**
 * The copy/select toolbar for page text, minus one annoyance of Gecko browsers: a double tap (to skip
 * ahead in a video, say) selects text like a desktop double-click, and the toolbar pops up. Selections
 * that follow a double tap are dropped; long-press selection and text fields work as usual.
 */
class RavenSelectionDelegate(activity: Activity) : BasicSelectionActionDelegate(activity) {
    override fun onShowActionRequest(session: GeckoSession, selection: Selection) {
        val editable = selection.flags and GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE != 0
        if (!editable && Taps.justDoubleTapped()) {
            Log.i("Raven", "selection after a double tap dropped")
            selection.unselect()
            return
        }
        super.onShowActionRequest(session, selection)
    }
}

/** Watches touches on the page to tell a double tap from a long press. */
object Taps {
    @Volatile private var lastDown = 0L
    @Volatile private var doubleTapAt = 0L

    fun onTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastDown < ViewConfiguration.getDoubleTapTimeout()) {
            doubleTapAt = now
            Log.d("Raven", "double tap")
        }
        lastDown = now
    }

    /** A double (or triple...) tap in the last second: a selection now came from it, not a long press. */
    fun justDoubleTapped() = SystemClock.uptimeMillis() - doubleTapAt < 1_000
}
