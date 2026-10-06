package app.raven.browser.engine

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.util.WeakHashMap

/**
 * Raven's own built-in helper (assets/helper): a small script in every page that mutes, unmutes or pauses that
 * page's audio and video, or shows only its video, when Raven asks. The engine itself only listens to the tab that
 * played sound last (a pause sent to an earlier tab is ignored), and split screen needs one half quiet while both
 * play; the helper reaches each tab directly.
 */
class Helper(private val runtime: GeckoRuntime) {
    private var extension: WebExtension? = null
    /** Each page and frame of a tab talks to Raven over its own port. */
    private val ports = WeakHashMap<GeckoSession, MutableList<WebExtension.Port>>()
    /** Tabs Raven keeps quiet: a page loaded later in the tab is muted as soon as its script connects. */
    private val muted = WeakHashMap<GeckoSession, Boolean>()

    /** The floating tab's page answered "Video only": whether it found a video, and its width and height. */
    var onVideoOnly: ((GeckoSession, Boolean, Int, Int) -> Unit)? = null

    fun install(onReady: () -> Unit) {
        runtime.webExtensionController.ensureBuiltIn(URL, ID).accept({ ext ->
            if (ext == null) return@accept
            extension = ext
            // Private tabs too: mute and pause work the same there.
            runCatching { runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true) }
            onReady()
        }, { e -> Log.w("Raven", "helper didn't load", e) })
    }

    /** Listens for the helper in this tab's pages. Called for each session that opens (and all open ones once ready). */
    fun attach(session: GeckoSession) {
        val ext = extension ?: return
        session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                ports.getOrPut(session) { mutableListOf() }.add(port)
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        val m = message as? JSONObject ?: return
                        when (m.optString("type")) {
                            "videoOnly" -> onVideoOnly?.invoke(session, m.optBoolean("ok"), m.optInt("w"), m.optInt("h"))
                            // The page's answer to pause or mute: how many players it reached (for the emulator tests).
                            "done" -> Log.i("Raven", "helper: page did ${m.optString("cmd")} on ${m.optInt("players")} player(s), ${m.optInt("playing")} still playing")
                        }
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        ports[session]?.remove(port)
                    }
                })
                if (muted[session] == true) send(port, JSONObject().put("cmd", "mute").put("on", true))
            }
        }, "raven")
    }

    fun mute(session: GeckoSession, on: Boolean) {
        if (on) muted[session] = true else muted.remove(session)
        sendAll(session, JSONObject().put("cmd", "mute").put("on", on))
    }

    fun pause(session: GeckoSession) = sendAll(session, JSONObject().put("cmd", "pause"))

    /** Plays the page's video if nothing plays, otherwise pauses what does. */
    fun toggle(session: GeckoSession) = sendAll(session, JSONObject().put("cmd", "toggle"))

    fun videoOnly(session: GeckoSession, on: Boolean) = sendAll(session, JSONObject().put("cmd", "videoOnly").put("on", on))

    /** Whether the helper is talking to this tab's page (it isn't on Raven's own pages, or before the page starts). */
    fun reaches(session: GeckoSession): Boolean = ports[session]?.isNotEmpty() == true

    private fun sendAll(session: GeckoSession, m: JSONObject) {
        val to = ports[session]?.toList().orEmpty()
        if (m.optString("cmd") != "videoOnly") Log.i("Raven", "helper: ${m.optString("cmd")} to ${to.size} port(s)")
        to.forEach { send(it, m) }
    }

    private fun send(port: WebExtension.Port, m: JSONObject) {
        runCatching { port.postMessage(m) }.onFailure { Log.w("Raven", "helper message lost", it) }
    }

    companion object {
        const val ID = "helper@raven.browser"
        const val URL = "resource://android/assets/helper/"
    }
}
