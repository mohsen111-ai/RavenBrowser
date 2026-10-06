package app.raven.browser.engine

import android.util.Log
import android.view.Choreographer
import androidx.core.view.doOnLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Which page view shows each tab. A session can be shown by only one view at a time, and Gecko stops the app
 * if a second view takes a session the first still holds. That happens when Android throws the screen away
 * while you're in another app and builds a new one when you come back: the old view still holds the page.
 * So a view always takes a session through here, and a session is let go before it's closed.
 */
object Displays {
    private val holders = WeakHashMap<GeckoSession, WeakReference<GeckoView>>()

    /** Shows [session] in [view], first taking it back from any other view that still holds it. */
    fun show(session: GeckoSession, view: GeckoView) {
        if (view.session === session) return
        holders[session]?.get()?.takeIf { it !== view && it.session === session }?.releaseSession()
        view.session?.let { holders.remove(it) }
        view.releaseSession()
        try {
            view.setSession(session)
            holders[session] = WeakReference(view)
        } catch (e: IllegalStateException) {
            // The session's display is still taken by a view we don't know about: leave the page blank
            // rather than stopping the app. The next change of tab or page tries again.
            Log.w("Raven", "couldn't show the page", e)
        }
    }

    private val settled = WeakHashMap<GeckoView, Boolean>()

    /**
     * Like [show], but a view that was just created waits until its size has settled: a rebuilt screen is laid
     * out first without the status bar and a frame later with it, and the engine starting to draw at the first
     * size and getting the second straight away could stall the screen for seconds (an ANR in smoke-6). Views
     * that have shown a page before (switching tabs, changing pages) show the new one at once.
     */
    fun showWhenSettled(session: GeckoSession, view: GeckoView) {
        when (settled[view]) {
            true -> { show(session, view); return }
            false -> { pending[view] = session; return }  // already waiting: show this one when settled
            null -> settled[view] = false
        }
        view.doOnLayout {
            val frames = Choreographer.getInstance()
            frames.postFrameCallback {
                frames.postFrameCallback {
                    // Gone meanwhile (its screen closed): it must not take the page from the view showing it now.
                    if (!settled.containsKey(view) || !view.isAttachedToWindow) return@postFrameCallback
                    settled[view] = true
                    // Whatever the view is asked to show by now (the tab may have changed meanwhile).
                    (pending.remove(view) ?: session).takeIf { it.isOpen }?.let { show(it, view) }
                }
            }
        }
        pending[view] = session
    }
    private val pending = WeakHashMap<GeckoView, GeckoSession>()

    /** Before a session closes or sleeps: the view showing it lets it go. */
    fun release(session: GeckoSession) {
        holders.remove(session)?.get()?.takeIf { it.session === session }?.releaseSession()
    }

    /** The view is going away (its screen was closed): it lets go of whatever it shows. */
    fun releaseView(view: GeckoView) {
        settled.remove(view)
        pending.remove(view)
        view.session?.let { holders.remove(it) }
        view.releaseSession()
    }
}
