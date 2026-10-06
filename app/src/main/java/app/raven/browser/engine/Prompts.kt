package app.raven.browser.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import java.util.concurrent.atomic.AtomicBoolean

/** Everything a web page can ask the user, queued for the UI to answer. */
sealed class UiPrompt(val tabId: String) {
    class Page(tabId: String, val host: String, val prompt: PromptDelegate.BasePrompt, val result: GeckoResult<PromptDelegate.PromptResponse>) : UiPrompt(tabId)
    class Permission(tabId: String, val host: String, val permission: PermissionDelegate.ContentPermission, val result: GeckoResult<Int>) : UiPrompt(tabId)
    class Media(
        tabId: String,
        val host: String,
        val video: List<PermissionDelegate.MediaSource>,
        val audio: List<PermissionDelegate.MediaSource>,
        val callback: PermissionDelegate.MediaCallback,
    ) : UiPrompt(tabId)
    class AndroidPermissions(tabId: String, val permissions: List<String>, val callback: PermissionDelegate.Callback) : UiPrompt(tabId)

    private val answered = AtomicBoolean(false)

    /** Answers at most once: the engine rejects a second answer, which a quick double tap would send. */
    fun answer(block: () -> Unit) {
        if (answered.compareAndSet(false, true)) runCatching(block)
    }

    /** Says no; used when the tab closes, sleeps or crashes before the user answered. */
    fun decline() = answer {
        when (this) {
            is Page -> result.complete(prompt.dismiss())
            is Permission -> result.complete(PermissionDelegate.ContentPermission.VALUE_DENY)
            is Media -> callback.reject()
            is AndroidPermissions -> callback.reject()
        }
    }

    /** The page took the question back (it navigated away, for example), so it needs no answer. */
    fun withdraw() = answered.set(true)
}
