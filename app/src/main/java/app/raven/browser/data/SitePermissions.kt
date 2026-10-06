package app.raven.browser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What you answered a site about its camera and microphone, when you said to remember it (the engine remembers
 * location, notifications and the rest itself, but asks for the camera and microphone every time). Per site name.
 */
class SitePermissions(context: Context) {
    enum class Kind(val label: String) { CAMERA("Camera"), MICROPHONE("Microphone") }

    private val sp = context.getSharedPreferences("site-permissions", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    /** Site → what each kind is set to (true allowed, false blocked); a kind not there is asked each time. */
    val all: StateFlow<Map<String, Map<Kind, Boolean>>> = _all.asStateFlow()

    fun get(host: String, kind: Kind): Boolean? = _all.value[host]?.get(kind)

    fun set(host: String, kind: Kind, allowed: Boolean?) {
        val key = "$host|${kind.name}"
        sp.edit().apply { if (allowed == null) remove(key) else putBoolean(key, allowed) }.apply()
        _all.value = load()
    }

    fun forget(host: String) {
        sp.edit().apply { Kind.entries.forEach { remove("$host|${it.name}") } }.apply()
        _all.value = load()
    }

    private fun load(): Map<String, Map<Kind, Boolean>> {
        val out = mutableMapOf<String, MutableMap<Kind, Boolean>>()
        sp.all.forEach { (k, v) ->
            val host = k.substringBeforeLast('|')
            val kind = Kind.entries.firstOrNull { it.name == k.substringAfterLast('|') } ?: return@forEach
            if (v is Boolean) out.getOrPut(host) { mutableMapOf() }[kind] = v
        }
        return out
    }
}
