package app.raven.browser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A person using Raven: their own sign-ins, cookies, history and tabs. [id] "" is the first one, whose tabs keep the
 * engine's ordinary storage; every other profile keeps its own, apart (a Gecko context). [color]: an index into
 * [Profiles.colors].
 */
class Profile(val id: String, val name: String, val color: Int) {
    /** The engine's storage this profile's tabs use (null: the ordinary one). */
    val contextId: String? get() = contextOf(id)

    companion object {
        fun contextOf(id: String): String? = if (id.isEmpty()) null else "raven-profile-$id"
    }
}

/**
 * The profiles, and which one new tabs open in. Settings, home sites, bookmarks, Shield, the VPN and downloads are
 * shared by all of them.
 */
class Profiles(context: Context) {
    private val sp = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Profile>> = _all.asStateFlow()

    fun byId(id: String): Profile = _all.value.firstOrNull { it.id == id } ?: _all.value.first()

    /** Adds a profile with the next colour nobody has yet. */
    fun add(name: String): Profile {
        val used = _all.value.map { it.color }.toSet()
        val color = colors.indices.firstOrNull { it !in used } ?: (_all.value.size % colors.size)
        val p = Profile(UUID.randomUUID().toString().take(8), name.trim().ifEmpty { "Profile ${_all.value.size + 1}" }, color)
        save(_all.value + p)
        return p
    }

    fun edit(id: String, name: String? = null, color: Int? = null) {
        save(_all.value.map { p -> if (p.id == id) Profile(p.id, name?.trim()?.ifEmpty { null } ?: p.name, color ?: p.color) else p })
    }

    /** The first profile can't be removed: it's where Raven's own storage lives. */
    fun remove(id: String) {
        if (id.isEmpty()) return
        save(_all.value.filter { it.id != id })
    }

    fun export(): JSONArray = JSONArray().also { arr -> _all.value.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) } }

    /** A backup's profiles in place of these, written at once (Raven restarts right after). */
    fun import(arr: JSONArray) {
        val list = List(arr.length()) { i -> arr.getJSONObject(i).let { Profile(it.getString("id"), it.getString("name"), it.optInt("color")) } }
        if (list.any { it.id.isEmpty() }) save(list, now = true)
    }

    private fun save(list: List<Profile>, now: Boolean = false) {
        _all.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        val edit = sp.edit().putString("list", arr.toString())
        if (now) edit.commit() else edit.apply()
    }

    private fun load(): List<Profile> {
        val list = runCatching {
            val arr = JSONArray(sp.getString("list", "[]"))
            List(arr.length()) { i -> arr.getJSONObject(i).let { Profile(it.getString("id"), it.getString("name"), it.optInt("color")) } }
        }.getOrDefault(emptyList())
        return if (list.any { it.id.isEmpty() }) list else listOf(Profile("", "Personal", 0)) + list
    }

    companion object {
        /** Moonlight, ember, moss, dusk blue, rose, sand: none of them the private tabs' violet. */
        val colors = listOf(0xFFC7CCD8, 0xFFE8A86A, 0xFF8FD6B4, 0xFF7FB2E5, 0xFFE58FA8, 0xFFD9C9A3)
        val colorNames = listOf("Moonlight", "Ember", "Moss", "Dusk blue", "Rose", "Sand")
    }
}
