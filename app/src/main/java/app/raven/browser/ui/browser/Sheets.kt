package app.raven.browser.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.raven.browser.BuildConfig
import app.raven.browser.Container
import app.raven.browser.engine.BrowserTab
import app.raven.browser.engine.InstallRequest
import app.raven.browser.engine.PopupRequest
import app.raven.browser.ui.Screen
import app.raven.browser.ui.Sheet
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.AmberPlanet
import app.raven.browser.ui.components.FitText
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.components.SheetHandle
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.components.Tile
import app.raven.browser.ui.components.Toggle
import app.raven.browser.ui.components.chocolate
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RavenSheet(onDismiss: () -> Unit, scrollable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Space.Surface,
        contentColor = Space.Text,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetHandle() },
    ) {
        // Scrolls when it doesn't fit, as in landscape.
        Column(
            Modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .navigationBarsPadding().padding(bottom = 16.dp),
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------------- menu

@Composable
fun MenuSheet(c: Container, ui: UiState, tab: BrowserTab) {
    val context = LocalContext.current
    val canBack by tab.canGoBack.collectAsState()
    val canForward by tab.canGoForward.collectAsState()
    val overNewTab by tab.ntpOverlay.collectAsState()
    val desktop by tab.desktop.collectAsState()
    val downloads by c.downloads.items.collectAsState()
    val prefs by c.settings.prefs.collectAsState()
    val active = downloads.count { it.status == app.raven.browser.downloads.DownloadStatus.RUNNING }
    val hasPage = !tab.isNewTabPage
    fun close() { ui.sheet = null }

    val opener = c.tabs.opener(tab)
    val backOut = !canBack && (opener != null || (tab.startedFromNewTab && !tab.hasNoPage))
    val blocked = tab.blocked.collectAsState().value
    val saved = c.db.bookmarked.collectAsState().value.contains(tab.url.value)
    val vpnOn by c.vpn.on.collectAsState()
    val vpnPlaces by c.ravenVpn.places.collectAsState()
    val vpnActive by c.ravenVpn.active.collectAsState()
    val place = vpnPlaces.firstOrNull { it.id == vpnActive }
    val translation by tab.translation.collectAsState()
    val translated = translation?.requestedTranslationPair != null && translation?.hasVisibleChange == true
    val split by c.tabs.split.collectAsState()
    val homeUrl = "https://" + android.net.Uri.parse(tab.url.value).host + "/"
    val onHome = homeUrl in prefs.pinnedSites
    // What isn't one touch away elsewhere: tabs and private tabs live behind the tabs button (hold it for a new one).
    val tiles = buildList {
        add(MenuTileSpec(Icons.Plus, "New tab", "New tab") { close(); c.tabs.newTab(select = true) })
        add(MenuTileSpec(Icons.Bookmark, "Bookmarks", "Bookmarks") { ui.go(Screen.Bookmarks) })
        add(MenuTileSpec(Icons.Download, "Downloads", "Downloads", badge = if (active > 0) "$active" else null) { ui.go(Screen.Downloads) })
        add(MenuTileSpec(Icons.History, "History", "History") { ui.go(Screen.History) })
        // The tab in a small window over the others; two tabs sharing the screen.
        add(MenuTileSpec(Icons.Float, "Float", "Float this tab", enabled = hasPage && !tab.private) { close(); c.tabs.float(tab.id) })
        if (split != null) add(MenuTileSpec(Icons.Split, "End split", "End split screen", on = true) { close(); c.tabs.endSplit() })
        else add(MenuTileSpec(Icons.Split, "Split screen", "Split screen") { ui.sheet = Sheet.Split })
        add(MenuTileSpec(Icons.Find, "Find in page", "Find in page", enabled = hasPage) { ui.findOpen = true; close() })
        add(MenuTileSpec(Icons.Translate, if (translated) "Translated" else "Translate", "Translate page", enabled = hasPage, on = translated) { ui.sheet = Sheet.Translate })
        // On: the tile lights up with the accent.
        add(MenuTileSpec(Icons.Desktop, "Desktop site", "Desktop site", enabled = hasPage, on = desktop) { setDesktop(tab, !desktop); close() })
        // Every bar hidden on every page, until it's turned off here again; a swipe down from the top shows them.
        add(MenuTileSpec(Icons.FullPage, "Full screen", "Full screen for pages", on = prefs.fullPage) {
            val on = !prefs.fullPage
            c.settings.update { it.copy(fullPage = on) }
            ui.barsPeek = false
            close()
            c.engine.messages.tryEmit(if (on) "Full screen. Swipe down from the top to see the bar." else "Full screen off")
        })
        add(MenuTileSpec(Icons.Sliders, "Settings", "Settings") { ui.go(Screen.Settings) })
        add(MenuTileSpec(Icons.Puzzle, "Add-ons", "Add-ons") { ui.go(Screen.Addons) })
        add(MenuTileSpec(Icons.Pdf, "Save as PDF", "Save as PDF", enabled = hasPage) { close(); savePdf(c, tab) })
        // The VPN: on and off and the country, inside Raven. Lit while Raven's VPN (or your VPN app) carries the traffic.
        val green = Color(0xFF8FD6B4)
        val label = place?.let { "${it.flag} ${it.label}" } ?: if (vpnOn) "VPN on" else "VPN off"
        add(MenuTileSpec(Icons.Vpn, label, "VPN: " + (place?.let { "browsing from ${it.label}" } ?: if (vpnOn) "on" else "off"), tint = if (vpnOn || place != null) green else Space.Text2, on = vpnOn || place != null, onColor = green) {
            ui.sheet = Sheet.Vpn
        })
        if (prefs.supernovaButton) add(MenuTileSpec(Icons.Supernova, "Clean slate", "Clean slate", tint = Space.Solar) { ui.sheet = Sheet.Supernova })
    }
    RavenSheet(::close) {
        MenuContent(
            back = RoundAction(Icons.Back, "Back", !overNewTab && (canBack || backOut)) {
                when {
                    canBack -> tab.session.goBack()
                    opener != null -> c.tabs.returnToOpener(tab)
                    else -> tab.backToNewTabPage()
                }
                close()
            },
            // On the new tab page Back led to, Forward returns to the page underneath.
            forward = RoundAction(Icons.Forward, "Forward", canForward || overNewTab) { if (overNewTab) tab.leaveHome() else tab.session.goForward(); close() },
            bookmark = RoundAction(if (saved) Icons.BookmarkFilled else Icons.Bookmark, if (saved) "Remove bookmark" else "Bookmark this page", hasPage, on = saved) {
                val url = tab.url.value
                val title = tab.title.value
                close()
                // The menu is gone by then, so this runs on the app's own scope.
                c.scope.launch {
                    if (saved) {
                        c.db.removeBookmark(url)
                        c.engine.messages.tryEmit("Removed from bookmarks")
                    } else {
                        c.db.addBookmark(url, title)
                        ui.snackbar.currentSnackbarData?.dismiss()
                        val r = ui.snackbar.showSnackbar("Saved to bookmarks", actionLabel = "Folder", duration = androidx.compose.material3.SnackbarDuration.Short)
                        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) ui.folderFor = url
                    }
                }
            },
            share = RoundAction(Icons.Share, "Share", hasPage) {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url.value).putExtra(Intent.EXTRA_SUBJECT, tab.title.value)
                context.startActivity(Intent.createChooser(i, null))
                close()
            },
            // Puts the site in (or takes it out of) the dock on Raven's home screen.
            home = RoundAction(Icons.AddHome, if (onHome) "Take off the home screen" else "Add to home screen", hasPage, on = onHome) {
                c.settings.update { if (onHome) it.copy(pinnedSites = it.pinnedSites - homeUrl) else it.copy(pinnedSites = (listOf(homeUrl) + it.pinnedSites).distinct()) }
                c.engine.messages.tryEmit(if (onHome) "Taken off your home screen" else "Added to your home screen")
                close()
            },
            tiles = tiles,
            shieldDetail = if (hasPage && blocked > 0) "$blocked ${if (blocked == 1) "tracker" else "trackers"} turned away on this page" else "Blocks ads and trackers on every page",
            onShield = {
                close()
                if (!c.engine.openUboPanel()) c.engine.messages.tryEmit("Shield is still being set up")
            },
        )
        // Other add-ons' toolbar buttons (uBlock Origin has its own row above).
        val actions by c.engine.actions.collectAsState()
        val addons by c.engine.addons.collectAsState()
        val others = addons.filter { it.id != app.raven.browser.engine.Engine.UBO_ID && it.metaData.enabled && actions[it.id] != null }
        if (others.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                others.forEach { ext ->
                    val name = ext.metaData.name ?: "Add-on"
                    Row(
                        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Space.Surface2)
                            .clickable(onClickLabel = "Open $name") { actions[ext.id]?.click(); close() }
                            .padding(start = 6.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SitePlanet(name, 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
        }
        Text(
            "Raven ${BuildConfig.VERSION_NAME} · Gecko engine ${BuildConfig.GECKO_VERSION.substringBefore('.')}",
            style = MaterialTheme.typography.bodySmall, color = Space.Text3, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

class RoundAction(val icon: RavenIcon, val label: String, val enabled: Boolean, val on: Boolean = false, val onClick: () -> Unit)

class MenuTileSpec(
    val icon: RavenIcon,
    val label: String,
    val description: String,
    val tint: Color = Space.Text,
    val badge: String? = null,
    val enabled: Boolean = true,
    val on: Boolean = false,
    val onColor: Color? = null,
    val onClick: () -> Unit,
)

/** The menu: four round buttons for the page, a grid of tiles, and Shield with today's count for this page. */
@Composable
fun MenuContent(back: RoundAction, forward: RoundAction, bookmark: RoundAction, share: RoundAction, home: RoundAction, tiles: List<MenuTileSpec>, shieldDetail: String, onShield: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(back, forward, bookmark, share, home).forEach { a ->
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(if (a.on) Raven.accent.copy(alpha = 0.16f) else Space.Surface2)
                        .border(1.dp, if (a.on) Raven.accent.copy(alpha = 0.5f) else Space.Hairline, CircleShape)
                        .clickable(enabled = a.enabled, onClickLabel = a.label, onClick = a.onClick),
                    contentAlignment = Alignment.Center,
                ) { Icon(a.icon, a.label, size = 21.dp, tint = if (!a.enabled) Space.Text3 else if (a.on) Raven.accent else Space.Text) }
            }
        }
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { MenuTile(it, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(60.dp).clip(CircleShape).background(Space.Surface2).border(1.dp, Space.Hairline, CircleShape)
                .clickable(onClickLabel = "Open Shield", onClick = onShield).padding(start = 18.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Shield, null, size = 21.dp, tint = Color(0xFF8FD6B4))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Shield is on", style = MaterialTheme.typography.titleSmall)
                Text(shieldDetail, style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Forward, null, size = 16.dp, tint = Space.Text2)
        }
    }
}

@Composable
private fun MenuTile(t: MenuTileSpec, modifier: Modifier) {
    val accent = t.onColor ?: Raven.accent
    val tint = when {
        !t.enabled -> Space.Text3
        t.on -> accent
        else -> t.tint
    }
    Column(
        modifier
            .height(86.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (t.on) accent.copy(alpha = 0.14f) else Color(0x0DE9ECF3))
            .border(1.dp, if (t.on) accent.copy(alpha = 0.5f) else Color(0x14C7CCD8), RoundedCornerShape(24.dp))
            .clickable(enabled = t.enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = t.onClick)
            .semantics { contentDescription = t.description + if (t.on) ", on" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(t.icon, null, size = 22.dp, tint = tint)
            if (t.badge != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(start = 18.dp).size(16.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) { Text(t.badge, color = Space.OnAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
        FitText(
            t.label, MaterialTheme.typography.labelMedium.copy(lineHeight = 14.sp), Modifier.padding(top = 7.dp, start = 4.dp, end = 4.dp),
            color = if (t.enabled) Space.Text else Space.Text3, textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}

private fun setDesktop(tab: BrowserTab, on: Boolean) {
    tab.desktop.value = on
    tab.session.settings.setUserAgentMode(if (on) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
    tab.session.settings.setViewportMode(if (on) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
    tab.session.reload()
}

private fun savePdf(c: Container, tab: BrowserTab) {
    c.engine.messages.tryEmit("Saving as PDF…")
    tab.session.saveAsPdf().accept({ stream ->
        if (stream == null) return@accept
        val name = (tab.title.value.ifBlank { tab.host }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)) + ".pdf"
        c.downloads.saveStream(name, "application/pdf", stream, tab.private) { ok ->
            c.engine.messages.tryEmit(if (ok) "Saved $name to Downloads" else "Couldn't save the PDF")
        }
    }, { c.engine.messages.tryEmit("This page can't be saved as PDF") })
}

// ---------------------------------------------------------------------------------- uBlock Origin panel

@Composable
fun UboSheet(c: Container, popup: PopupRequest, tab: BrowserTab?) {
    val blocked = tab?.blocked?.collectAsState()?.value ?: 0
    fun close() {
        c.engine.popup.value = null
        popup.session.close()
    }
    val isUbo = popup.extension.id == app.raven.browser.engine.Engine.UBO_ID
    // The panel is a web page that scrolls itself; size it to the screen instead of scrolling the sheet.
    val panelHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp.coerceAtMost(470.dp)
    RavenSheet(::close, scrollable = false) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isUbo) {
                Box(Modifier.size(72.dp, 56.dp), contentAlignment = Alignment.Center) { AmberPlanet(40.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("$blocked", fontFamily = Display, fontSize = 30.sp, color = Space.Text)
                    Text(
                        if (tab != null && !tab.isNewTabPage) "blocked on ${tab.host}" else "uBlock Origin",
                        style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                val name = popup.extension.metaData.name ?: "Add-on"
                SitePlanet(name, 44.dp, highlighted = true)
                Spacer(Modifier.width(14.dp))
                Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        // The add-on's own panel (for uBlock Origin: power switch, element picker, zapper, logger, dashboard).
        AndroidView(
            factory = { ctx -> GeckoView(ctx).apply { setSession(popup.session) } },
            onRelease = { it.releaseSession() },
            modifier = Modifier.fillMaxWidth().height(panelHeight).padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp)),
        )
    }
}

// ---------------------------------------------------------------------------------- long press

@Composable
fun LongPressSheet(c: Container, ui: UiState, sheet: Sheet.LongPress) {
    val context = LocalContext.current
    val tab = c.tabs.tabs.collectAsState().value.firstOrNull { it.id == sheet.tabId }
    val e: ContextElement = sheet.element
    val link = e.linkUri
    val src = e.srcUri
    fun close() { ui.sheet = null }
    RavenSheet(::close) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SitePlanet(siteLabel(link ?: src ?: "?"), 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title ?: e.linkText ?: e.altText ?: siteLabel(link ?: src ?: ""), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text((link ?: src).orEmpty().removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        val private = tab?.private == true
        if (link != null) {
            ActionRow(Icons.NewTab, "Open in new tab") {
                c.tabs.newTab(url = link, private = private, select = false, openerId = tab?.id)
                c.engine.messages.tryEmit("Opened in a new tab")
                close()
            }
            if (!private) ActionRow(Icons.Eclipse, "Open in private tab", tint = Space.Nebula) { c.tabs.newTab(url = link, private = true); close() }
            ActionRow(Icons.Link, "Copy link") { copy(context, link); close() }
            ActionRow(Icons.Share, "Share link") { share(context, link); close() }
            if (e.type == ContextElement.TYPE_NONE) ActionRow(Icons.Download, "Download link") { c.downloads.downloadUrl(link, private, tab?.url?.value); close() }
        }
        if (src != null && e.type != ContextElement.TYPE_NONE) {
            Text(
                when (e.type) { ContextElement.TYPE_IMAGE -> "IMAGE"; ContextElement.TYPE_VIDEO -> "VIDEO"; else -> "AUDIO" },
                style = MaterialTheme.typography.labelSmall, color = Space.Text2, modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 4.dp),
            )
            ActionRow(Icons.NewTab, if (e.type == ContextElement.TYPE_IMAGE) "Open image in new tab" else "Open in new tab") {
                c.tabs.newTab(url = src, private = private, openerId = tab?.id); close()
            }
            ActionRow(Icons.Download, if (e.type == ContextElement.TYPE_IMAGE) "Save image" else "Save file") {
                c.downloads.downloadUrl(src, private, tab?.url?.value); close()
            }
            ActionRow(Icons.Copy, "Copy address") { copy(context, src); close() }
        }
    }
}

@Composable
private fun ActionRow(icon: RavenIcon, text: String, tint: Color = Space.Text, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, size = 20.dp, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Link", text))
}

fun share(context: Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

// ---------------------------------------------------------------------------------- site info

private val permissionNames = mapOf(
    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION to ("Location" to Icons.Location),
    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION to ("Notifications" to Icons.Bell),
    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE to ("Autoplay with sound" to Icons.Mute),
    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE to ("Store data permanently" to Icons.File),
    GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS to ("Protected content (DRM)" to Icons.Play),
    GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS to ("Cookies on other sites" to Icons.Globe),
)

@Composable
fun SiteInfoSheet(c: Container, ui: UiState, tab: BrowserTab) {
    val secure by tab.secure.collectAsState()
    val blocked by tab.blocked.collectAsState()
    var perms by remember { mutableStateOf<List<GeckoSession.PermissionDelegate.ContentPermission>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(tab.url.value, version) {
        c.engine.runtime.storageController.getPermissions(tab.url.value, tab.private).accept({ list ->
            perms = list.orEmpty().filter { it.permission in permissionNames.keys }
        }, { })
    }
    fun close() { ui.sheet = null }
    RavenSheet(::close) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SitePlanet(siteLabel(tab.url.value), 48.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(tab.host, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                    Icon(if (secure == false) Icons.LockOpen else Icons.Lock, null, size = 14.dp, tint = if (secure == false) Space.Solar else Raven.accent, stroke = 2f)
                    Spacer(Modifier.width(6.dp))
                    Text(if (secure == false) "Connection is not secure" else "Connection is secure", style = MaterialTheme.typography.bodySmall, color = if (secure == false) Space.SolarText else Space.Text2)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Space.Surface2).padding(start = 14.dp, end = 6.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Space.Solar))
                Spacer(Modifier.width(12.dp))
                Text("uBlock Origin · $blocked blocked", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                PillButton("Manage", { close(); c.engine.openUboPanel() }, style = PillStyle.Soft, height = 40.dp)
            }
            Text("PERMISSIONS", style = MaterialTheme.typography.labelSmall, color = Space.Text2, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Space.Surface2)) {
                if (perms.isEmpty()) {
                    Text("This site hasn't asked for any permissions.", style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(16.dp))
                }
                perms.forEach { p ->
                    val (name, icon) = permissionNames.getValue(p.permission)
                    val allowed = p.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, size = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Toggle(allowed, { on ->
                            c.engine.runtime.storageController.setPermission(p, if (on) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                            version++
                        }, "$name allowed")
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Space.Surface2).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cookies and site data", style = MaterialTheme.typography.bodyMedium)
                    Text("Signs you out of this site", style = MaterialTheme.typography.bodySmall, color = Space.Text2)
                }
                PillButton("Clear", {
                    c.engine.runtime.storageController.clearDataFromBaseDomain(tab.host, StorageController.ClearFlags.SITE_DATA)
                    c.engine.messages.tryEmit("Cookies and data for ${tab.host} cleared")
                    close()
                }, style = PillStyle.Outline, height = 40.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------- supernova

@Composable
fun SupernovaSheet(c: Container, ui: UiState) {
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var downloadList by remember { mutableStateOf(false) }
    val count = c.tabs.tabs.collectAsState().value.count { !it.isNewTabPage }
    fun close() { ui.sheet = null }
    RavenSheet(::close) {
        CleanSlateMark(Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 14.dp))
        Text("Clean slate", fontFamily = Display, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            when (count) { 0 -> "Close all tabs and erase:"; 1 -> "Close your 1 tab and erase:"; else -> "Close all $count tabs and erase:" },
            style = MaterialTheme.typography.bodyMedium, color = Space.Text2,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 12.dp),
        )
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Space.Surface2).padding(horizontal = 6.dp)) {
            CheckRow("Browsing history", history) { history = it }
            CheckRow("Cookies and site data", cookies) { cookies = it }
            CheckRow("Cached images and files", cache) { cache = it }
            CheckRow("Download list (your files stay)", downloadList) { downloadList = it }
        }
        Text("Add-ons, settings and downloaded files are kept.", style = MaterialTheme.typography.bodySmall, color = Space.Text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", ::close, Modifier.weight(1f), style = PillStyle.Outline, height = 52.dp)
            PillButton("Erase everything", {
                c.tabs.closeAll()
                c.eraseBrowsingData(history, cookies, cache)
                if (downloadList) c.downloads.clearList()
                ui.sheet = null
                ui.screen = Screen.Browser
                c.engine.messages.tryEmit("Clean slate. Everything is gone.")
            }, Modifier.weight(1.8f), style = PillStyle.Amber, height = 52.dp)
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = Space.Solar, checkmarkColor = Space.OnAccent, uncheckedColor = Space.Text2))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The bin in a dusk circle, in a faint warm glow. */
@Composable
private fun CleanSlateMark(modifier: Modifier) {
    Box(modifier.size(120.dp, 96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Space.Solar.copy(alpha = 0.22f), Color.Transparent)), radius = size.minDimension * 0.6f)
        }
        Box(Modifier.size(72.dp).chocolate(CircleShape, Space.Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Supernova, null, size = 30.dp, tint = Space.Solar)
        }
    }
}

// ---------------------------------------------------------------------------------- add-on install

private val permissionText = mapOf(
    "tabs" to "See your open tabs",
    "activeTab" to "Access the page you're on when you use it",
    "downloads" to "Download files and manage downloads",
    "downloads.open" to "Open downloaded files",
    "webRequest" to "Watch network requests",
    "webRequestBlocking" to "Block or change network requests",
    "privacy" to "Change privacy settings",
    "dns" to "Look up website addresses",
    "history" to "Read and change your browsing history",
    "bookmarks" to "Read and change bookmarks",
    "cookies" to "Read and change cookies",
    "notifications" to "Show notifications",
    "clipboardRead" to "Read the clipboard",
    "clipboardWrite" to "Change the clipboard",
    "nativeMessaging" to "Talk to other apps",
    "geolocation" to "Use your location",
    "webNavigation" to "See which pages you visit",
    "management" to "Manage other add-ons",
    "proxy" to "Send your traffic through a proxy",
    "browsingData" to "Clear browsing data",
)

@Composable
fun InstallSheet(c: Container, req: InstallRequest) {
    var private by remember { mutableStateOf(false) }
    fun answer(ok: Boolean) {
        req.respond(ok, private)
        if (c.engine.installRequest.value === req) c.engine.installRequest.value = null
    }
    val meta = req.extension.metaData
    val allSites = req.origins.any { it == "<all_urls>" || it.startsWith("*://*/") || it == "http://*/*" || it == "https://*/*" }
    val lines = buildList {
        if (allSites) add(true to "Read and change content on all websites")
        else if (req.origins.isNotEmpty()) add(true to "Read and change content on ${req.origins.size} site${if (req.origins.size > 1) "s" else ""}")
        req.permissions.mapNotNull { permissionText[it] }.distinct().forEach { add((it.contains("network") || it.contains("history") || it.contains("cookies")) to it) }
    }
    RavenSheet({ answer(false) }) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SitePlanet(meta.name ?: "?", 52.dp, highlighted = true)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Add ${meta.name ?: "this add-on"}?", fontFamily = Display, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(meta.creatorName?.let { "By $it" }, if (meta.signedState > 0) "signed by Mozilla" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Space.Surface2).padding(16.dp)) {
            Text(if (lines.isEmpty()) "It doesn't need any special permissions." else "It will be able to:", style = MaterialTheme.typography.bodySmall, color = Space.Text2)
            lines.forEach { (risky, text) ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (risky) Icons.Globe else Icons.Check, null, size = 18.dp, tint = if (risky) Space.Solar else Space.Text)
                    Spacer(Modifier.width(10.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { private = !private }.padding(horizontal = 8.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(private, { private = it })
            Text("Also allow in private tabs", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Only add extensions you trust. You can turn it off or remove it any time in Add-ons.", style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", { answer(false) }, Modifier.weight(1f), style = PillStyle.Outline, height = 52.dp)
            PillButton("Add", { answer(true) }, Modifier.weight(1f), height = 52.dp)
        }
    }
}

@Composable
fun SmallIconButton(icon: RavenIcon, description: String, onClick: () -> Unit) =
    IconButton(icon, description, onClick, size = 44.dp, iconSize = 18.dp, background = Space.Surface2)

