package app.raven.browser.ui.screens

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.raven.browser.BuildConfig
import app.raven.browser.Container
import app.raven.browser.CrashLog
import app.raven.browser.data.DnsProvider
import app.raven.browser.data.Isolation
import app.raven.browser.data.LinksInApps
import app.raven.browser.data.Motion
import app.raven.browser.data.Prefs
import app.raven.browser.data.Profile
import app.raven.browser.data.SearchEngine
import app.raven.browser.data.SitePermissions
import app.raven.browser.ui.Screen
import app.raven.browser.ui.Sheet
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.Card
import app.raven.browser.ui.components.Divider
import app.raven.browser.ui.components.ListRow
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.components.ScreenHeader
import app.raven.browser.ui.components.SectionLabel
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.components.Toggle
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/**
 * Settings, laid out like Firefox's: a short main page where each line opens its own page and shows what it's set to,
 * with Raven's explanations inside those pages. A search box at the top finds any setting.
 */
enum class SettingsPage(val title: String) {
    MAIN("Settings"), PROFILES("Profiles"), SEARCH("Search engine"), HOME("Home and wallpapers"), TABS("Tabs"), LOOK("Look"),
    DOWNLOADS("Downloads"), SHIELD("Shield"), TRACKING("Tracking protection"), COOKIES("Cookie popups"), HTTPS("HTTPS-only mode"),
    DNS("Encrypted DNS"), ISOLATION("Site isolation"), PERMISSIONS("Site permissions"), LOCK("Lock"), ERASING("Erasing"),
    PIP("Picture-in-picture"), LINKS("Open links in apps"), BACKUP("Backup and restore"), ABOUT("About Raven"),
}

/** Everything a page needs: the settings, a way to change them, and where to go. */
private class Pages(
    val c: Container,
    val ui: UiState,
    val p: Prefs,
    val go: (SettingsPage) -> Unit,
    val set: ((Prefs) -> Prefs) -> Unit,
)

@Composable
fun SettingsScreen(c: Container, ui: UiState, onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    // Back from a page returns to the main page first.
    BackHandler(page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    val p by c.settings.prefs.collectAsState()
    val pages = Pages(c, ui, p, { page = it }) { t ->
        c.settings.update(t)
        c.engine.applyLiveSettings(c.settings.current)
    }
    Column(Modifier.fillMaxSize().background(Raven.ground).navigationBarsPadding()) {
        ScreenHeader(page.title, { if (page == SettingsPage.MAIN) onBack() else page = SettingsPage.MAIN })
        // Each page starts at its top.
        key(page) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                when (page) {
                    SettingsPage.MAIN -> pages.Main()
                    SettingsPage.PROFILES -> pages.ProfilesPage()
                    SettingsPage.SEARCH -> pages.SearchPage()
                    SettingsPage.HOME -> pages.HomePage()
                    SettingsPage.TABS -> pages.TabsPage()
                    SettingsPage.LOOK -> pages.LookPage()
                    SettingsPage.DOWNLOADS -> pages.DownloadsPage()
                    SettingsPage.SHIELD -> pages.ShieldPage()
                    SettingsPage.TRACKING -> pages.TrackingPage()
                    SettingsPage.COOKIES -> pages.CookiesPage()
                    SettingsPage.HTTPS -> pages.HttpsPage()
                    SettingsPage.DNS -> pages.DnsPage()
                    SettingsPage.ISOLATION -> pages.IsolationPage()
                    SettingsPage.PERMISSIONS -> pages.PermissionsPage()
                    SettingsPage.LOCK -> pages.LockPage()
                    SettingsPage.ERASING -> pages.ErasingPage()
                    SettingsPage.PIP -> pages.PipPage()
                    SettingsPage.LINKS -> pages.LinksPage()
                    SettingsPage.BACKUP -> pages.BackupPage()
                    SettingsPage.ABOUT -> pages.AboutPage()
                }
            }
        }
    }
}

/** Settings Gecko reads only once per start: changed, Raven must restart. */
private val startIsolation = mutableStateOf<Isolation?>(null)

@Composable
private fun Pages.RestartBanner() {
    if (startIsolation.value == null) startIsolation.value = c.settings.current.isolation
    if (p.isolation == startIsolation.value) return
    val context = LocalContext.current
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Raven.accent.copy(alpha = 0.12f)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Restart Raven to apply your changes", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        PillButton("Restart", { restart(context as Activity) }, height = 40.dp)
    }
}

// ------------------------------------------------------------------------------------------------ the main page

/** A line of the main page: what it is, what it's set to, and the page it opens. */
@Composable
private fun Pages.Line(icon: RavenIcon, title: String, value: String?, to: SettingsPage? = null, onClick: (() -> Unit)? = null) {
    ListRow(title, icon = icon, iconTint = Space.Text2, value = value, chevron = true, onClick = onClick ?: { if (to != null) go(to) })
}

@Composable
private fun Pages.Main() {
    var query by rememberSaveable { mutableStateOf("") }
    SearchBox(query) { query = it }
    if (query.isNotBlank()) {
        Results(query)
        return
    }
    RestartBanner()

    // Profiles: who's using Raven, at the top.
    val profiles by c.profiles.all.collectAsState()
    Spacer(Modifier.height(14.dp))
    Card {
        ListRow(
            "Profiles",
            detail = if (profiles.size == 1) "Just you. Add one for a second account or person." else profiles.joinToString(" · ") { it.name },
            leading = { ProfileDots(profiles) },
            chevron = true, onClick = { go(SettingsPage.PROFILES) },
        )
    }
    DefaultBrowserCard()

    val wallpapers = app.raven.browser.ui.sky.Wallpapers.all
    SectionLabel("General")
    Card {
        Line(Icons.Search, "Search engine", p.searchEngine.label, SettingsPage.SEARCH)
        Divider()
        Line(Icons.Moon, "Home and wallpapers", if (p.wallpaperRotate) "${wallpapers.count { it.id !in p.wallpapersOff }} taking turns" else app.raven.browser.ui.sky.Wallpapers.byId(p.wallpaper).name, SettingsPage.HOME)
        Divider()
        Line(Icons.TabsIcon, "Tabs", "Sleep after ${minutes(p.sleepAfterMinutes).lowercase()}", SettingsPage.TABS)
        Divider()
        Line(Icons.Sliders, "Look", Space.accentNames.getOrElse(p.accent) { "" }, SettingsPage.LOOK)
        Divider()
        Line(Icons.Download, "Downloads", "${p.connections} connections", SettingsPage.DOWNLOADS)
    }

    val ubo = c.engine.addons.collectAsState().value.firstOrNull { it.id == app.raven.browser.engine.Engine.UBO_ID }
    val vpnPlaces by c.ravenVpn.places.collectAsState()
    val vpnActive by c.ravenVpn.active.collectAsState()
    SectionLabel("Privacy and security")
    Card {
        Line(Icons.Shield, "Shield", if (ubo == null) "Setting up" else if (ubo.metaData.enabled) "On" else "Off", SettingsPage.SHIELD)
        Divider()
        Line(Icons.Vpn, "VPN", vpnPlaces.firstOrNull { it.id == vpnActive }?.let { "${it.flag} ${it.label}" } ?: "Off", onClick = { ui.sheet = Sheet.Vpn })
        Divider()
        Line(Icons.Eclipse, "Tracking protection", if (p.strictTracking) "Strict" else "Standard", SettingsPage.TRACKING)
        Divider()
        Line(Icons.Close, "Cookie popups", if (p.cookiePopups) "Turned down" else "Off", SettingsPage.COOKIES)
        Divider()
        Line(Icons.Lock, "HTTPS-only mode", if (p.httpsOnly) "On" else "Off", SettingsPage.HTTPS)
        Divider()
        Line(Icons.Globe, "Encrypted DNS", p.dns.label.substringBefore(" ("), SettingsPage.DNS)
        Divider()
        Line(Icons.Split, "Site isolation", p.isolation.label, SettingsPage.ISOLATION)
        Divider()
        Line(Icons.Location, "Site permissions", null, SettingsPage.PERMISSIONS)
        Divider()
        Line(Icons.Fingerprint, "Lock", lockValue(p), SettingsPage.LOCK)
        Divider()
        Line(Icons.Supernova, "Erasing", if (p.eraseOnClose) "When closing" else "Off", SettingsPage.ERASING)
    }

    val addons by c.engine.addons.collectAsState()
    SectionLabel("Advanced")
    Card {
        Line(Icons.Float, "Picture-in-picture", if (p.pictureInPicture) "On" else "Off", SettingsPage.PIP)
        Divider()
        Line(Icons.Puzzle, "Add-ons", "${addons.size}", onClick = { ui.go(Screen.Addons) })
        Divider()
        Line(Icons.Link, "Open links in apps", p.linksInApps.label, SettingsPage.LINKS)
        Divider()
        Line(Icons.Folder, "Backup and restore", null, SettingsPage.BACKUP)
    }
    Spacer(Modifier.height(14.dp))
    Card { Line(Icons.Info, "About Raven", BuildConfig.VERSION_NAME, SettingsPage.ABOUT) }
}

@Composable
private fun ProfileDots(profiles: List<Profile>) {
    Box(Modifier.size(20.dp)) {
        profiles.take(3).forEachIndexed { i, pr ->
            Box(Modifier.padding(start = (i * 5).dp, top = (i * 3).dp).size(11.dp).clip(CircleShape).background(profileColor(pr)).border(1.dp, Space.Surface, CircleShape))
        }
    }
}

/** "Make Raven your default browser", shown only while it isn't. */
@Composable
private fun DefaultBrowserCard() {
    val context = LocalContext.current
    fun isDefault(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_BROWSER)
        else context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/")), 0)?.activityInfo?.packageName == context.packageName
    }.getOrDefault(true)
    var default by remember { mutableStateOf(isDefault()) }
    // Asked again each time Settings comes back, in case it was changed in the phone's settings meanwhile.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) default = isDefault() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { default = isDefault() }
    if (default) return
    Spacer(Modifier.height(10.dp))
    Card {
        ListRow("Make Raven your default browser", detail = "Links from other apps open in Raven", icon = Icons.Globe, iconTint = Raven.accent, chevron = true, onClick = {
            val roles = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
            if (roles != null && Build.VERSION.SDK_INT >= 29 && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                runCatching { ask.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)) }
            } else {
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            }
        })
    }
}

// ------------------------------------------------------------------------------------------------ search

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Space.Surface)
            .border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Search, null, size = 18.dp, tint = Space.Text2)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search settings", style = MaterialTheme.typography.bodyMedium, color = Space.Text2)
            BasicTextField(
                query, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Space.Text), cursorBrush = SolidColor(Raven.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search settings" },
            )
        }
        if (query.isNotEmpty()) app.raven.browser.ui.components.IconButton(Icons.Close, "Clear", { onChange("") }, size = 40.dp, iconSize = 16.dp, tint = Space.Text2)
    }
}

/** One setting the search box can find: its name, other words for it, and the page it's on. */
private class Entry(val title: String, val page: SettingsPage?, val words: String, val action: (() -> Unit)? = null)

@Composable
private fun Pages.Results(query: String) {
    val entries = listOf(
        Entry("Profiles", SettingsPage.PROFILES, "people person accounts second account work containers"),
        Entry("Search engine", SettingsPage.SEARCH, "duckduckgo google startpage brave qwant bing"),
        Entry("Wallpapers", SettingsPage.HOME, "wallpaper sky background picture new each time"),
        Entry("Live wallpapers", SettingsPage.HOME, "moving sky animation stars twinkle"),
        Entry("Put unused tabs to sleep", SettingsPage.TABS, "memory sleep resting"),
        Entry("Close tabs not used for", SettingsPage.TABS, "old tabs"),
        // The pages themselves, so their names find them too.
        Entry("Look", SettingsPage.LOOK, "appearance theme colours look"),
        Entry("Home and wallpapers", SettingsPage.HOME, "home page start greeting"),
        Entry("Tabs", SettingsPage.TABS, "tab"),
        Entry("Downloads", SettingsPage.DOWNLOADS, "files download folder"),
        Entry("Erasing", SettingsPage.ERASING, "erase clear delete"),
        Entry("Moonlight colour", SettingsPage.LOOK, "accent color colour theme look appearance"),
        Entry("Pure black", SettingsPage.LOOK, "oled true black dark battery"),
        Entry("Dark websites", SettingsPage.LOOK, "dark mode night"),
        Entry("Reduce motion", SettingsPage.LOOK, "animation battery saver"),
        Entry("Text size", SettingsPage.LOOK, "font zoom bigger smaller"),
        Entry("Full screen", SettingsPage.LOOK, "hide bars immersive"),
        Entry("Connections per download", SettingsPage.DOWNLOADS, "download speed"),
        Entry("Shield", SettingsPage.SHIELD, "ublock origin ad blocker ads trackers"),
        Entry("VPN", null, "proton wireguard country location") { ui.sheet = Sheet.Vpn },
        Entry("Tracking protection", SettingsPage.TRACKING, "trackers strict standard"),
        Entry("Cookie popups", SettingsPage.COOKIES, "cookie banner consent gdpr reject"),
        Entry("HTTPS-only mode", SettingsPage.HTTPS, "secure http unsecure"),
        Entry("Encrypted DNS", SettingsPage.DNS, "doh dns cloudflare quad9 mullvad"),
        Entry("Site isolation", SettingsPage.ISOLATION, "fission process memory"),
        Entry("Site permissions", SettingsPage.PERMISSIONS, "camera microphone location notifications autoplay"),
        Entry("Lock Raven", SettingsPage.LOCK, "fingerprint password pin biometric app lock"),
        Entry("Lock private tabs", SettingsPage.LOCK, "fingerprint private"),
        Entry("Erase data when closing", SettingsPage.ERASING, "clear history cookies delete"),
        Entry("Clean slate", SettingsPage.ERASING, "erase everything supernova"),
        Entry("Picture-in-picture", SettingsPage.PIP, "video small window pip"),
        Entry("Add-ons", null, "extensions addons dark reader") { ui.go(Screen.Addons) },
        Entry("Open links in apps", SettingsPage.LINKS, "youtube app links"),
        Entry("Backup and restore", SettingsPage.BACKUP, "backup restore export import file proton drive"),
        Entry("About Raven", SettingsPage.ABOUT, "version crash report welcome"),
    )
    val q = query.trim().lowercase()
    val found = entries.filter { e -> q.split(' ').all { w -> e.title.lowercase().contains(w) || e.words.contains(w) } }
    Spacer(Modifier.height(14.dp))
    if (found.isEmpty()) {
        Text("Nothing in Settings matches \"${query.trim()}\".", style = MaterialTheme.typography.bodyMedium, color = Space.Text2, modifier = Modifier.padding(8.dp))
        return
    }
    Card {
        found.forEachIndexed { i, e ->
            if (i > 0) Divider()
            ListRow(e.title, detail = e.page?.takeIf { it.title != e.title }?.title, chevron = true, onClick = {
                val action = e.action
                if (action != null) action() else e.page?.let { go(it) }
            })
        }
    }
}

// ------------------------------------------------------------------------------------------------ the pages

/** Raven's explanation at the top of a page. */
@Composable
private fun Explain(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Space.Text2, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp))
}

/** Choices, one of which is picked: a row each, with a line saying what it means. */
@Composable
private fun <T> Choices(options: List<Triple<T, String, String?>>, current: T, onPick: (T) -> Unit) {
    Spacer(Modifier.height(10.dp))
    Card {
        options.forEachIndexed { i, (value, label, detail) ->
            if (i > 0) Divider()
            val on = value == current
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onPick(value) }.semantics { selected = on }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(on, null, modifier = Modifier.padding(horizontal = 6.dp))
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun Switch(title: String, detail: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title, detail = detail, trailing = { Toggle(on, onChange, title) })
}

@Composable
private fun Pages.ProfilesPage() {
    Explain("Each profile is a person (or a second account) with their own sign-ins, cookies, history and tabs. Two Instagram accounts can be open at once, even side by side in split screen. Settings, bookmarks, home sites, Shield, the VPN and downloads are shared. Switch between profiles on the Tabs screen; a long-pressed link can open in another profile.")
    Spacer(Modifier.height(10.dp))
    Card { ProfilesList(c) }
}

@Composable
private fun Pages.SearchPage() {
    Explain("Where your searches from the address bar go.")
    Choices(SearchEngine.entries.map { Triple(it, it.label, null) }, p.searchEngine) { v -> set { it.copy(searchEngine = v) } }
}

@Composable
private fun Pages.HomePage() {
    val context = LocalContext.current
    Explain("Raven's home shows a night wallpaper, the greeting, what you were reading, your sites and Shield's count.")
    Spacer(Modifier.height(10.dp))
    Card {
        val thumbs by androidx.compose.runtime.produceState(emptyMap<String, androidx.compose.ui.graphics.ImageBitmap?>()) {
            value = app.raven.browser.ui.sky.Wallpapers.all.associate { it.id to c.sky.thumbnail(it) }
        }
        WallpaperPicker(
            rotate = p.wallpaperRotate, chosen = p.wallpaper, off = p.wallpapersOff, thumbs = thumbs,
            onRotate = { v ->
                // Turning it off keeps the wallpaper you're seeing now.
                set { it.copy(wallpaperRotate = v, wallpaper = c.sky.current.value.id) }
                c.sky.settingsChanged()
            },
            onTap = { w ->
                if (p.wallpaperRotate) {
                    val included = w.id !in p.wallpapersOff
                    val taking = app.raven.browser.ui.sky.Wallpapers.all.count { it.id !in p.wallpapersOff }
                    if (included && taking <= 2) {
                        android.widget.Toast.makeText(context, "Keep at least two taking turns", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        set { it.copy(wallpapersOff = if (included) it.wallpapersOff + w.id else it.wallpapersOff - w.id) }
                    }
                } else {
                    set { it.copy(wallpaper = w.id) }
                    c.sky.show(w)
                }
                c.sky.settingsChanged()
            },
        )
        Divider()
        Switch("Live wallpapers", "The live ones move, stars twinkle and the moon breathes, only while the home screen shows. Still with Reduce motion or Battery Saver.", p.movingSky) { v -> set { it.copy(movingSky = v) } }
    }
}

@Composable
private fun Pages.TabsPage() {
    Explain("Tabs you haven't looked at for a while go to sleep: they free their memory and reload when you open them. Closing a tab shows Undo for a few seconds.")
    SectionLabel("Put unused tabs to sleep after")
    Choices(listOf(5, 10, 30, 60, 0).map { Triple(it, minutes(it), null) }, p.sleepAfterMinutes) { v -> set { it.copy(sleepAfterMinutes = v) } }
    SectionLabel("Close tabs not used for")
    Choices(listOf(0, 1, 7, 30).map { Triple(it, days(it), null) }, p.closeAfterDays) { v -> set { it.copy(closeAfterDays = v) } }
}

@Composable
private fun Pages.LookPage() {
    Spacer(Modifier.height(10.dp))
    Card {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Moonlight colour", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Space.accents.forEachIndexed { i, color ->
                val on = p.accent == i
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { set { it.copy(accent = i) } }
                        .semantics { contentDescription = "${Space.accentNames[i]} accent"; selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(color).then(if (on) Modifier.border(2.dp, Space.Text, CircleShape) else Modifier))
                }
            }
        }
        Divider()
        Switch("Pure black", "Darker than midnight behind web pages, saves battery on OLED screens", p.trueBlack) { v -> set { it.copy(trueBlack = v) } }
        Divider()
        Switch("Dark websites", "Ask sites for their dark version when they have one", p.darkWebsites) { v -> set { it.copy(darkWebsites = v) } }
        Divider()
        Switch("Full screen", "Every bar hidden on every page; swipe down from the top to see them. Also in the menu.", p.fullPage) { v -> set { it.copy(fullPage = v) } }
    }
    SectionLabel("Reduce motion")
    Choices(Motion.entries.map { Triple(it, it.label, null) }, p.reduceMotion) { v -> set { it.copy(reduceMotion = v) } }
    SectionLabel("Text size on websites")
    Choices(listOf(80, 90, 100, 110, 125, 150).map { Triple(it, "$it%", null) }, p.textScale) { v -> set { it.copy(textScale = v) } }
}

@Composable
private fun Pages.DownloadsPage() {
    Explain("A big download goes faster over several connections at once, when the server allows it. Servers that limit connections are waited out, so a download always finishes.")
    SectionLabel("Connections per download")
    Choices(
        listOf(1, 2, 4, 6, 8, 12, 16).map { Triple(it, if (it == 1) "1 (off)" else "$it", if (it > 8) "Some servers refuse this many" else null) },
        p.connections,
    ) { v -> set { it.copy(connections = v) } }
}

@Composable
private fun Pages.ShieldPage() {
    val ubo = c.engine.addons.collectAsState().value.firstOrNull { it.id == app.raven.browser.engine.Engine.UBO_ID }
    val today by c.blockedToday.count.collectAsState()
    Explain("Shield is uBlock Origin, by Raymond Hill: it blocks ads and trackers on every page, and Raven keeps it up to date. Tracking protection and cookie popups work alongside it.")
    Spacer(Modifier.height(10.dp))
    Card {
        ListRow("Shield is ${if (ubo == null) "being set up" else if (ubo.metaData.enabled) "on" else "off"}", detail = if (today == 1) "1 tracker turned away today" else "$today trackers turned away today", icon = Icons.Shield, iconTint = Color(0xFF8FD6B4))
        Divider()
        ListRow("Open Shield for this page", detail = "Turn it off for a site, or pick an element to hide", chevron = true, onClick = {
            if (!c.engine.openUboPanel()) c.engine.messages.tryEmit("Shield is still being set up")
        })
        Divider()
        ListRow("uBlock Origin's own settings", detail = "Filter lists, cookie notices, your own rules", chevron = true, onClick = {
            ubo?.metaData?.optionsPageUrl?.let { c.tabs.newTab(url = it); ui.go(Screen.Browser) } ?: c.engine.messages.tryEmit("Shield is still being set up")
        })
    }
}

@Composable
private fun Pages.TrackingPage() {
    Explain("Blocks trackers even without Shield, and keeps each site's cookies apart so they can't follow you from site to site.")
    Choices(
        listOf(Triple(true, "Strict", "Recommended. Blocks more, very rarely breaks a site."), Triple(false, "Standard", "Fewer broken sites, a little less blocked.")),
        p.strictTracking,
    ) { v -> set { it.copy(strictTracking = v) } }
}

@Composable
private fun Pages.CookiesPage() {
    Explain("Most sites ask about cookies when you arrive. Raven says no for you: it presses the site's own \"Reject all\", and hides popups that only let you accept. It works on the common popup systems and on popups with a plain Reject or Decline button, in many languages.")
    Spacer(Modifier.height(10.dp))
    Card { Switch("Turn down cookie popups", null, p.cookiePopups) { v -> set { it.copy(cookiePopups = v) } } }
}

@Composable
private fun Pages.HttpsPage() {
    Explain("Raven opens every site over a secure connection. When a site has none, it warns you first, and you can open it anyway once.")
    Spacer(Modifier.height(10.dp))
    Card { Switch("HTTPS-only mode", null, p.httpsOnly) { v -> set { it.copy(httpsOnly = v) } } }
}

@Composable
private fun Pages.DnsPage() {
    Explain("Looking up a site's address normally tells your network (and its owner) every site you visit. Encrypted DNS hides that.")
    Choices(DnsProvider.entries.map { Triple(it, it.label, null) }, p.dns) { v -> set { it.copy(dns = v) } }
}

@Composable
private fun Pages.IsolationPage() {
    RestartBanner()
    Explain("Keeps sites apart in their own processes on the phone, so one site can't reach into another. More isolation uses more memory. A change takes effect when Raven restarts.")
    Choices(Isolation.entries.map { Triple(it, it.label, it.detail) }, p.isolation) { v -> set { it.copy(isolation = v) } }
}

@Composable
private fun Pages.LockPage() {
    val context = LocalContext.current
    val main = context as? app.raven.browser.MainActivity
    fun guarded(on: Boolean, title: String, apply: () -> Unit) {
        when {
            main == null -> Unit
            on && !main.canLock() -> android.widget.Toast.makeText(context, "Set a screen lock on your phone first (Settings, Security)", android.widget.Toast.LENGTH_LONG).show()
            else -> main.authenticate(title) { apply() }
        }
    }
    Explain("Your fingerprint or screen lock opens Raven when you come back to it. Private tabs keep their own lock, which locks the moment you leave; when both are locked, one fingerprint opens both.")
    Spacer(Modifier.height(10.dp))
    Card {
        Switch("Lock Raven", "Off until you turn it on", p.appLock) { v ->
            guarded(v, if (v) "Lock Raven" else "Stop locking Raven") {
                set { it.copy(appLock = v) }
                if (!v) c.appLocked.value = false
            }
        }
        Divider()
        Switch("Lock private tabs", "They're also kept out of screenshots and recent apps", p.lockPrivateTabs) { v ->
            guarded(v, if (v) "Lock private tabs" else "Stop locking private tabs") {
                set { it.copy(lockPrivateTabs = v) }
                if (!v) c.privateLocked.value = false
            }
        }
    }
    if (p.appLock) {
        SectionLabel("Lock Raven after")
        Choices(listOf(0, 1, 5, 30).map { Triple(it, lockAfter(it), null) }, p.lockAfterMinutes) { v -> set { it.copy(lockAfterMinutes = v) } }
    }
}

@Composable
private fun Pages.ErasingPage() {
    Explain("Clean slate closes every tab and erases what you choose, from the menu. Close all on the Tabs screen only closes tabs.")
    Spacer(Modifier.height(10.dp))
    Card {
        Switch("Erase data when closing", "History, cookies and site data, each time Raven is closed", p.eraseOnClose) { v -> set { it.copy(eraseOnClose = v) } }
        Divider()
        Switch("Clean slate in the menu", null, p.supernovaButton) { v -> set { it.copy(supernovaButton = v) } }
        Divider()
        ListRow("Clean slate now…", icon = Icons.Supernova, iconTint = Space.Solar, chevron = true, onClick = { ui.sheet = Sheet.Supernova })
    }
}

@Composable
private fun Pages.PipPage() {
    Explain("A fullscreen video keeps playing in a small window when you leave Raven, with play and pause on it.")
    Spacer(Modifier.height(10.dp))
    Card { Switch("Picture-in-picture", null, p.pictureInPicture) { v -> set { it.copy(pictureInPicture = v) } } }
}

@Composable
private fun Pages.LinksPage() {
    Explain("A link you follow to another site that has its own app on your phone, like YouTube, can open in that app. Never from a private tab, and never for an address you type.")
    Choices(LinksInApps.entries.map { Triple(it, it.label, it.detail) }, p.linksInApps) { v -> set { it.copy(linksInApps = v) } }
}

@Composable
private fun Pages.BackupPage() {
    Explain("One file with your tabs, history, bookmarks, home sites, settings and profiles, locked with a password you choose. Keep it anywhere, for example in Proton Drive. Sign-ins to websites and the VPN's location files are never in it.")
    Spacer(Modifier.height(10.dp))
    Card { BackupRows(c) }
}

@Composable
private fun Pages.AboutPage() {
    val context = LocalContext.current
    Spacer(Modifier.height(10.dp))
    Card {
        ListRow("Raven ${BuildConfig.VERSION_NAME}", detail = "Gecko engine ${BuildConfig.GECKO_VERSION} · Shield is uBlock Origin by Raymond Hill")
        Divider()
        ListRow("Show the welcome screens again", chevron = true, onClick = { c.settings.update { it.copy(onboardingDone = false) } })
        // After a crash: a copy of what went wrong, to paste into a message.
        var crash by remember { mutableStateOf(CrashLog.read(context.applicationContext as android.app.Application)) }
        crash?.let { report ->
            Divider()
            ListRow("Copy crash report", detail = "Raven stopped unexpectedly. Paste this to whoever helps you fix it.", chevron = true, onClick = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Raven crash report", report))
                android.widget.Toast.makeText(context, "Crash report copied", android.widget.Toast.LENGTH_SHORT).show()
            })
            Divider()
            ListRow("Delete crash report", chevron = true, onClick = {
                CrashLog.clear(context.applicationContext as android.app.Application)
                crash = null
            })
        }
    }
}

// ------------------------------------------------------------------------------------------------ site permissions

/** What each kind of permission is called here, for the ones a site can ask for. */
private val permissionNames = mapOf(
    PermissionDelegate.PERMISSION_GEOLOCATION to ("Location" to Icons.Location),
    PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION to ("Notifications" to Icons.Bell),
    PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE to ("Autoplay with sound" to Icons.Sound),
    PermissionDelegate.PERMISSION_PERSISTENT_STORAGE to ("Store data permanently" to Icons.File),
    PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS to ("Protected content (DRM)" to Icons.Play),
    PermissionDelegate.PERMISSION_STORAGE_ACCESS to ("Cookies on other sites" to Icons.Globe),
    PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS to ("Devices on your network" to Icons.Globe),
)

/** One site's answers: the engine's own (location, notifications...) and Raven's camera and microphone. */
private class SiteAnswers(val host: String, val profile: String?, val gecko: List<ContentPermission>, val raven: Map<SitePermissions.Kind, Boolean>)

@Composable
private fun Pages.PermissionsPage() {
    var version by remember { mutableIntStateOf(0) }
    var gecko by remember { mutableStateOf<List<ContentPermission>>(emptyList()) }
    LaunchedEffect(version) {
        c.engine.runtime.storageController.getAllPermissions().accept({ list ->
            gecko = list.orEmpty().filter { !it.privateMode && it.permission in permissionNames && it.value != ContentPermission.VALUE_PROMPT }
        }, { })
    }
    val raven by c.sitePermissions.all.collectAsState()
    val profiles by c.profiles.all.collectAsState()
    fun profileName(contextId: String?): String? = contextId?.let { id -> profiles.firstOrNull { it.contextId == id }?.name }
    val sites = run {
        val byKey = gecko.groupBy { (Uri.parse(it.uri).host?.removePrefix("www.") ?: it.uri) to profileName(it.contextId) }
        val keys = (byKey.keys + raven.keys.map { it to null }).distinct().sortedBy { it.first }
        keys.map { k -> SiteAnswers(k.first, k.second, byKey[k].orEmpty(), if (k.second == null) raven[k.first].orEmpty() else emptyMap()) }
    }
    Explain("What you've let each site do, or kept it from doing. Switch one off to block it, or forget a site so it asks again. Sites ask for the camera and microphone each time, unless you said to remember.")
    if (sites.isEmpty()) {
        Spacer(Modifier.height(10.dp))
        Card { ListRow("No site has asked for anything yet", detail = "When a site asks for your location, notifications, camera or microphone, your answer shows here.", icon = Icons.Info, iconTint = Space.Text2) }
        return
    }
    sites.forEach { site ->
        Spacer(Modifier.height(12.dp))
        Card {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SitePlanet(app.raven.browser.ui.browser.siteLabel(site.host), 34.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(site.host, style = MaterialTheme.typography.titleSmall)
                    if (site.profile != null) Text("In ${site.profile}", style = MaterialTheme.typography.bodySmall, color = Space.Text2)
                }
                PillButton("Forget", {
                    site.gecko.forEach { c.engine.runtime.storageController.setPermission(it, ContentPermission.VALUE_PROMPT) }
                    if (site.profile == null) c.sitePermissions.forget(site.host)
                    version++
                }, style = PillStyle.Outline, height = 36.dp)
            }
            site.gecko.forEach { perm ->
                val (name, icon) = permissionNames.getValue(perm.permission)
                Divider()
                ListRow(name, icon = icon, iconTint = Space.Text2, trailing = {
                    Toggle(perm.value == ContentPermission.VALUE_ALLOW, { on ->
                        c.engine.runtime.storageController.setPermission(perm, if (on) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
                        version++
                    }, "$name for ${site.host}")
                })
            }
            site.raven.forEach { (kind, allowed) ->
                Divider()
                ListRow(kind.label, icon = if (kind == SitePermissions.Kind.CAMERA) Icons.Camera else Icons.Mic, iconTint = Space.Text2, trailing = {
                    Toggle(allowed, { on -> c.sitePermissions.set(site.host, kind, on) }, "${kind.label} for ${site.host}")
                })
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ words

private fun lockValue(p: Prefs) = when {
    p.appLock -> lockAfter(p.lockAfterMinutes).let { if (it == "Immediately") "At once" else "After $it" }
    p.lockPrivateTabs -> "Private tabs"
    else -> "Off"
}
private fun lockAfter(m: Int) = when (m) { 0 -> "Immediately"; 1 -> "1 minute"; else -> "$m minutes" }
private fun minutes(m: Int) = when (m) { 0 -> "Never"; 60 -> "1 hour"; else -> "$m min" }
private fun days(d: Int) = when (d) { 0 -> "Never"; 1 -> "1 day"; 7 -> "1 week"; 30 -> "1 month"; else -> "$d days" }

/** Gecko reads some settings once per process, so those need a fresh one. */
fun restart(activity: Activity) {
    activity.startActivity(
        Intent(activity, app.raven.browser.RestartActivity::class.java)
            .putExtra("pid", android.os.Process.myPid())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    activity.finishAffinity()
    android.os.Process.killProcess(android.os.Process.myPid())
}
