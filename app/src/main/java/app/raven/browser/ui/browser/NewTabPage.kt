package app.raven.browser.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.raven.browser.Container
import app.raven.browser.engine.BrowserTab
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.Chip
import app.raven.browser.ui.components.FitText
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.components.glass
import app.raven.browser.ui.sky.Wallpapers
import app.raven.browser.ui.sky.nightSky
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PinnedSite(val url: String, val label: String, val pinned: Boolean)

private val defaultSites = listOf("youtube.com", "wikipedia.org", "github.com", "reddit.com", "duckduckgo.com")
private val names = mapOf(
    "youtube" to "YouTube", "github" to "GitHub", "duckduckgo" to "DuckDuckGo", "justwatch" to "JustWatch",
    "wikipedia" to "Wikipedia", "reddit" to "Reddit", "bbc" to "BBC", "cnn" to "CNN",
)

fun siteLabel(hostOrUrl: String): String {
    val host = android.net.Uri.parse(if (hostOrUrl.contains("://")) hostOrUrl else "https://$hostOrUrl").host.orEmpty().removePrefix("www.").removePrefix("m.")
    // An address like 192.168.1.1 (or localhost) is its own name: "1" or "168" would mean nothing.
    if (host.isEmpty() || host == "localhost" || host.contains(':') || host.all { it.isDigit() || it == '.' }) return host.ifEmpty { hostOrUrl }
    val parts = host.split('.')
    val main = if (parts.size >= 2) parts[parts.size - 2].let { if (it.length <= 3 && parts.size >= 3) parts[parts.size - 3] else it } else host
    return names[main] ?: main.replaceFirstChar { it.uppercase() }
}

/** Whether the browser (and so a home screen in it) can be seen right now, rather than a screen or a lock over it. */
val LocalBrowserShown = androidx.compose.runtime.compositionLocalOf { true }

/** The home screen's sky, also drawn behind the address bar so the two read as one. */
@Composable
fun homeSky(c: Container, private: Boolean): Modifier {
    val prefs by c.settings.prefs.collectAsState()
    // A live wallpaper moves only while the home screen can be seen (not under Settings, the Tabs screen or a lock).
    val moving = prefs.movingSky && !Raven.reduceMotion && LocalBrowserShown.current
    return if (private) {
        LaunchedEffect(Unit) { c.sky.loadEclipse() }
        val image by c.sky.eclipse.collectAsState()
        Modifier.nightSky(image, Wallpapers.eclipse, Space.NebulaGround, moving, private = true)
    } else {
        val wallpaper by c.sky.current.collectAsState()
        val image by c.sky.image.collectAsState()
        Modifier.nightSky(image, wallpaper, Raven.ground, moving)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTabPage(c: Container, ui: UiState, tab: BrowserTab) {
    val prefs by c.settings.prefs.collectAsState()
    val blockedToday by c.blockedToday.count.collectAsState()
    val historyVersion by c.db.historyVersion.collectAsState()
    var sites by remember { mutableStateOf<List<PinnedSite>>(emptyList()) }
    LaunchedEffect(prefs.pinnedSites, prefs.hiddenSites, historyVersion) {
        val pinned = prefs.pinnedSites.map { PinnedSite(it, siteLabel(it), true) }
        val pinnedHosts = pinned.map { siteLabel(it.url) }.toSet()
        val top = c.db.topSites(8, prefs.hiddenSites, tab.profile).map { PinnedSite(it.url, siteLabel(it.host), false) }.filter { siteLabel(it.url) !in pinnedHosts }
        val fallback = defaultSites.filter { it !in prefs.hiddenSites }.map { PinnedSite("https://$it/", siteLabel(it), false) }
        sites = (pinned + top + fallback).distinctBy { it.label }.take(5)
    }
    // Continue: the page you were on, as long as its tab is still open. Home pressed on a page leaves that page
    // under this one; otherwise it's the everyday tab you used last. Close it and Continue goes too.
    val openTabs by c.tabs.tabs.collectAsState()
    val back = when {
        !tab.hasNoPage -> tab
        else -> openTabs.filter { it.id != tab.id && !it.private && !it.hasNoPage && it.profile == tab.profile }.maxByOrNull { it.lastActive }
    }
    var menuFor by remember { mutableStateOf<PinnedSite?>(null) }
    var adding by remember { mutableStateOf(false) }
    // The clock, for the date and the part of the day: Raven can stay open in the background for days, so both
    // follow it (each minute while on screen, and at once on coming back).
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val now by produceState(LocalDateTime.now(), started) {
        value = LocalDateTime.now()
        while (started) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = LocalDateTime.now()
        }
    }
    LaunchedEffect(now.hour) { c.greetings.follow(now.hour) }
    val greeting by c.greetings.current.collectAsState()
    val date = now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault())).uppercase()

    val wallpaper by c.sky.current.collectAsState()
    HomeContent(
        sky = homeSky(c, private = false),
        wallpaper = wallpaper.name,
        date = date,
        greeting = greeting.text,
        recent = back?.let { t -> Continue(t.title.value.ifBlank { t.host }, siteLabel(t.url.value)) },
        sites = sites,
        blocked = blockedToday,
        onRecent = {
            back?.let { t ->
                if (t.id != tab.id) c.tabs.select(t.id)
                if (t.ntpOverlay.value) t.leaveHome()
            }
        },
        onSite = { c.tabs.load(tab, it.url) },
        onSiteMenu = { menuFor = it },
        onAdd = { adding = true },
        onShield = { c.engine.openUboPanel() },
        onSitePress = { c.engine.warmUp(it.url) },
    )

    menuFor?.let { site ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(site.label) },
            text = { Text(site.url.removePrefix("https://").trimEnd('/'), color = Space.Text2) },
            confirmButton = {
                TextButton({
                    c.settings.update { p ->
                        if (site.pinned) p.copy(pinnedSites = p.pinnedSites - site.url)
                        else p.copy(hiddenSites = (p.hiddenSites + android.net.Uri.parse(site.url).host.orEmpty().removePrefix("www.")).distinct())
                    }
                    menuFor = null
                }) { Text("Remove", color = Raven.accent) }
            },
            dismissButton = {
                if (!site.pinned) TextButton({
                    c.settings.update { it.copy(pinnedSites = (it.pinnedSites + site.url).distinct()) }
                    menuFor = null
                }) { Text("Keep it here", color = Raven.accent) }
            },
            containerColor = Space.Surface,
        )
    }
    if (adding) AddSiteDialog(onDismiss = { adding = false }) { url ->
        c.settings.update { it.copy(pinnedSites = (listOf(url) + it.pinnedSites).distinct()) }
        adding = false
    }
}

class Continue(val title: String, val label: String)

/** Home A: the greeting under the bar, and at the bottom what you were reading, your sites, and Shield's count. */
@Composable
fun HomeContent(
    sky: Modifier,
    wallpaper: String,
    date: String,
    greeting: String,
    recent: Continue?,
    sites: List<PinnedSite>,
    blocked: Int,
    onRecent: () -> Unit,
    onSite: (PinnedSite) -> Unit,
    onSiteMenu: (PinnedSite) -> Unit,
    onAdd: () -> Unit,
    onShield: () -> Unit,
    onSitePress: (PinnedSite) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().then(sky)) {
        val tall = maxHeight
        // Tonight's wallpaper's name is on the page itself (for screen readers, and the emulator tests): a tiny
        // label on its own would be hidden behind the page, and Android leaves out what is covered.
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).semantics { contentDescription = "Wallpaper: $wallpaper" },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(min = tall).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.padding(start = 8.dp, top = 22.dp, bottom = 28.dp)) {
                    Text(date, style = MaterialTheme.typography.labelSmall, color = Space.Text2)
                    FitText(
                        greeting,
                        TextStyle(
                            fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 41.sp, letterSpacing = (-1).sp,
                            shadow = Shadow(Color(0x8C05070D), blurRadius = 18f),
                        ),
                        Modifier.padding(top = 8.dp),
                    )
                }

                Column(Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    recent?.let { v ->
                        Row(
                            Modifier.fillMaxWidth().height(62.dp).glass(CircleShape)
                                .clickable(onClickLabel = "Continue", onClick = onRecent)
                                .padding(start = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SitePlanet(v.label, 46.dp)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text("CONTINUE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = Space.Text2)
                                Text(v.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Forward, null, size = 18.dp, tint = Raven.accent)
                        }
                    }

                    // The dock: your sites as round stones of glass, and a place to add one.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val slots = sites.size + 1
                        // On a narrow phone, when they don't all fit, the dock scrolls sideways instead of squeezing.
                        val fits = (maxWidth - 24.dp) / slots >= 48.dp
                        val circle = if (fits) ((maxWidth - 24.dp) / slots - 8.dp).coerceIn(40.dp, 52.dp) else 42.dp
                        Row(
                            Modifier.fillMaxWidth().glass(RoundedCornerShape(36.dp))
                                .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = if (fits) Arrangement.SpaceEvenly else Arrangement.spacedBy(4.dp),
                        ) {
                            sites.forEach { site ->
                                DockSite(site.label.take(1).uppercase(), site.label, circle, onClick = { onSite(site) }, onLongClick = { onSiteMenu(site) }, pinned = site.pinned, onPress = { onSitePress(site) })
                            }
                            DockSite("+", "Add", circle, onClick = onAdd, onLongClick = null, description = "Add a site")
                        }
                    }

                    Text(
                        when {
                            blocked == 1 -> "1 tracker turned away today"
                            blocked > 0 -> "$blocked trackers turned away today"
                            else -> "Shield is turning trackers away"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Space.Text2, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable(onClickLabel = "Open Shield", onClick = onShield).padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockSite(letter: String, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, onLongClick: (() -> Unit)?, pinned: Boolean = false, description: String = label, onPress: () -> Unit = {}) {
    val press by androidx.compose.runtime.rememberUpdatedState(onPress)
    Column(
        Modifier.width(size + 8.dp).clip(RoundedCornerShape(16.dp))
            // The finger coming down (before it lifts and the tap counts) already starts connecting to the site.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    press()
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(size).clip(CircleShape).background(Color(0x14E9ECF3))
                .border(1.dp, if (pinned) Raven.accent.copy(alpha = 0.6f) else Color(0x38C7CCD8), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp, color = Space.Text)
        }
        Text(label, fontSize = 11.sp, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun AddSiteDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin a site") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text("example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = {
            TextButton({
                val t = text.trim()
                if (t.isNotEmpty()) onAdd(if (t.contains("://")) t else "https://$t/")
            }) { Text("Pin", color = Raven.accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}

@Composable
fun PrivateNewTabPage(c: Container) {
    val prefs by c.settings.prefs.collectAsState()
    val ubo = c.engine.addons.collectAsState().value.firstOrNull { it.id == app.raven.browser.engine.Engine.UBO_ID }
    PrivateHomeContent(homeSky(c, private = true), shieldOn = ubo?.metaData?.allowedInPrivateBrowsing == true, locks = prefs.lockPrivateTabs)
}

/** The private home: the moon in eclipse, and what private tabs keep (nothing). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PrivateHomeContent(sky: Modifier, shieldOn: Boolean, locks: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().then(sky)) {
        val tall = maxHeight
        // A short screen (a small phone): the eclipse sits lower, so the words below it take less room.
        val short = tall < 600.dp
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(min = tall).padding(horizontal = 24.dp, vertical = if (short) 14.dp else 22.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!short) Text("PRIVATE · ECLIPSE", style = MaterialTheme.typography.labelSmall, color = Space.Nebula)
                FitText(
                    "The moon looks away.",
                    TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = if (short) 26.sp else 30.sp, lineHeight = if (short) 29.sp else 34.sp, letterSpacing = (-0.5).sp),
                    Modifier.padding(top = 10.dp), textAlign = TextAlign.Center,
                )
                Text(
                    "No history, cookies or site data are kept. Close the last private tab and it's all gone. Websites and your internet provider can still see your visits.",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA9A2D6), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                )
                if (!short) FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shieldOn) Chip("Shield on", dot = Space.Nebula, background = Space.Nebula.copy(alpha = 0.12f), color = Space.NebulaText)
                    Chip("Strict tracking protection", dot = Space.Nebula, background = Space.Nebula.copy(alpha = 0.12f), color = Space.NebulaText)
                }
                if (locks) {
                    Row(
                        Modifier.padding(top = 14.dp).fillMaxWidth().height(58.dp)
                            .glass(CircleShape, fill = Color(0xA60E0A1C), edge = Space.Nebula.copy(alpha = 0.24f), top = Space.Nebula.copy(alpha = 0.4f))
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Fingerprint, null, size = 22.dp, tint = Space.Nebula)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Locked when you leave", style = MaterialTheme.typography.titleSmall)
                            Text("Your fingerprint opens them again", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA9A2D6))
                        }
                    }
                }
            }
        }
    }
}
