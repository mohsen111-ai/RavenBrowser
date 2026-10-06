package app.raven.browser.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

enum class SearchEngine(val label: String, val template: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    QWANT("Qwant", "https://www.qwant.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
}

enum class DnsProvider(val label: String, val uri: String?) {
    CLOUDFLARE("Cloudflare", "https://mozilla.cloudflare-dns.com/dns-query"),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query"),
    MULLVAD("Mullvad", "https://dns.mullvad.net/dns-query"),
    OFF("Off (use your network's DNS)", null),
}

enum class Isolation(val label: String, val detail: String) {
    LOGGED_IN("Sites you log into", "Sites where you sign in get their own process. Recommended."),
    OFF("Off", "Least memory. All sites share one process."),
    ALL("Every site", "Strongest separation, uses the most memory."),
}

enum class Motion(val label: String) { AUTO("With Battery Saver"), ALWAYS("Always"), NEVER("Never") }

data class Prefs(
    val onboardingDone: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val searchSuggestions: Boolean = false,
    val strictTracking: Boolean = true,
    /** Cookie popups: the site's "Reject all" is pressed for you; popups without one are hidden. */
    val cookiePopups: Boolean = true,
    val httpsOnly: Boolean = true,
    val dns: DnsProvider = DnsProvider.CLOUDFLARE,
    val isolation: Isolation = Isolation.LOGGED_IN,
    val eraseOnClose: Boolean = false,
    val supernovaButton: Boolean = true,
    val lockPrivateTabs: Boolean = false,
    val accent: Int = 0,
    val trueBlack: Boolean = false,
    val reduceMotion: Motion = Motion.AUTO,
    val addressBarTop: Boolean = true,
    val pictureInPicture: Boolean = true,
    /** A new wallpaper each time Raven opens (or always [wallpaper] when off); [wallpapersOff] sit the rotation out. */
    val wallpaperRotate: Boolean = true,
    val wallpaper: String = "moonrise",
    val wallpapersOff: List<String> = listOf("none"),
    /** Stars twinkle and the moon breathes on the home screen. */
    val movingSky: Boolean = true,
    val textScale: Int = 100,
    val darkWebsites: Boolean = true,
    val sleepAfterMinutes: Int = 10,
    val closeAfterDays: Int = 0,
    val connections: Int = 4,
    val askWhereToSave: Boolean = false,
    val pinnedSites: List<String> = emptyList(),
    val hiddenSites: List<String> = emptyList(),
    val addonsLastChecked: Long = 0,
)

/** App settings, stored in SharedPreferences and exposed as one observable value. */
class Settings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()
    val current: Prefs get() = _prefs.value

    fun update(transform: (Prefs) -> Prefs) {
        val next = transform(_prefs.value)
        _prefs.value = next
        save(next)
    }

    private fun load(): Prefs {
        val d = Prefs()
        return Prefs(
            onboardingDone = sp.getBoolean("onboardingDone", d.onboardingDone),
            searchEngine = enumOr(sp.getString("searchEngine", null), d.searchEngine),
            searchSuggestions = sp.getBoolean("searchSuggestions", d.searchSuggestions),
            strictTracking = sp.getBoolean("strictTracking", d.strictTracking),
            cookiePopups = sp.getBoolean("cookiePopups", d.cookiePopups),
            httpsOnly = sp.getBoolean("httpsOnly", d.httpsOnly),
            dns = enumOr(sp.getString("dns", null), d.dns),
            isolation = enumOr(sp.getString("isolation", null), d.isolation),
            eraseOnClose = sp.getBoolean("eraseOnClose", d.eraseOnClose),
            supernovaButton = sp.getBoolean("supernovaButton", d.supernovaButton),
            lockPrivateTabs = sp.getBoolean("lockPrivateTabs", d.lockPrivateTabs),
            accent = sp.getInt("accent", d.accent),
            trueBlack = sp.getBoolean("trueBlack", d.trueBlack),
            reduceMotion = enumOr(sp.getString("reduceMotion", null), d.reduceMotion),
            addressBarTop = sp.getBoolean("addressBarTop", d.addressBarTop),
            pictureInPicture = sp.getBoolean("pictureInPicture", d.pictureInPicture),
            wallpaperRotate = sp.getBoolean("wallpaperRotate", d.wallpaperRotate),
            wallpaper = sp.getString("wallpaper", null) ?: d.wallpaper,
            wallpapersOff = sp.getString("wallpapersOff", null)?.split('\n')?.filter { it.isNotBlank() } ?: d.wallpapersOff,
            movingSky = sp.getBoolean("movingSky", d.movingSky),
            textScale = sp.getInt("textScale", d.textScale),
            darkWebsites = sp.getBoolean("darkWebsites", d.darkWebsites),
            sleepAfterMinutes = sp.getInt("sleepAfterMinutes", d.sleepAfterMinutes),
            closeAfterDays = sp.getInt("closeAfterDays", d.closeAfterDays),
            connections = sp.getInt("connections", d.connections),
            askWhereToSave = sp.getBoolean("askWhereToSave", d.askWhereToSave),
            pinnedSites = sp.getString("pinnedSites", "")!!.split('\n').filter { it.isNotBlank() },
            hiddenSites = sp.getString("hiddenSites", "")!!.split('\n').filter { it.isNotBlank() },
            addonsLastChecked = sp.getLong("addonsLastChecked", d.addonsLastChecked),
        )
    }

    private fun save(p: Prefs) = sp.edit {
        putBoolean("onboardingDone", p.onboardingDone)
        putString("searchEngine", p.searchEngine.name)
        putBoolean("searchSuggestions", p.searchSuggestions)
        putBoolean("strictTracking", p.strictTracking)
        putBoolean("cookiePopups", p.cookiePopups)
        putBoolean("httpsOnly", p.httpsOnly)
        putString("dns", p.dns.name)
        putString("isolation", p.isolation.name)
        putBoolean("eraseOnClose", p.eraseOnClose)
        putBoolean("supernovaButton", p.supernovaButton)
        putBoolean("lockPrivateTabs", p.lockPrivateTabs)
        putInt("accent", p.accent)
        putBoolean("trueBlack", p.trueBlack)
        putString("reduceMotion", p.reduceMotion.name)
        putBoolean("addressBarTop", p.addressBarTop)
        putBoolean("pictureInPicture", p.pictureInPicture)
        putBoolean("wallpaperRotate", p.wallpaperRotate)
        putString("wallpaper", p.wallpaper)
        putString("wallpapersOff", p.wallpapersOff.joinToString("\n"))
        putBoolean("movingSky", p.movingSky)
        putInt("textScale", p.textScale)
        putBoolean("darkWebsites", p.darkWebsites)
        putInt("sleepAfterMinutes", p.sleepAfterMinutes)
        putInt("closeAfterDays", p.closeAfterDays)
        putInt("connections", p.connections)
        putBoolean("askWhereToSave", p.askWhereToSave)
        putString("pinnedSites", p.pinnedSites.joinToString("\n"))
        putString("hiddenSites", p.hiddenSites.joinToString("\n"))
        putLong("addonsLastChecked", p.addonsLastChecked)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
