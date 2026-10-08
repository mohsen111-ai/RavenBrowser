package app.raven.browser.ui.browser

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.Container
import app.raven.browser.VpnWatch
import app.raven.browser.engine.VpnPlace
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.Toggle
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.launch

/** Raven's VPN: on and off, and the country to browse from, without leaving Raven. */
@Composable
fun VpnSheet(c: Container, ui: UiState) {
    val context = LocalContext.current
    val places by c.ravenVpn.places.collectAsState()
    val active by c.ravenVpn.active.collectAsState()
    val busy by c.ravenVpn.busy.collectAsState()
    val phoneVpn by c.vpn.on.collectAsState()
    var waiting by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<VpnPlace?>(null) }

    fun say(text: String) { c.engine.messages.tryEmit(text) }
    fun start(id: String) = c.scope.launch {
        c.ravenVpn.choose(id).onFailure { say("Couldn't turn the VPN on. Check the file, or try another location.") }
    }
    // The first time, Android asks whether Raven may run a VPN; then what was waiting for it goes ahead.
    var then by remember { mutableStateOf<(() -> Unit)?>(null) }
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val next = then
        then = null
        waiting = null
        if (r.resultCode == Activity.RESULT_OK) next?.invoke() else if (next != null) say("Android didn't allow Raven's VPN")
    }
    fun allowed(next: () -> Unit) {
        val ask = VpnService.prepare(context)
        if (ask != null) { then = next; allow.launch(ask) } else next()
    }
    fun connect(id: String) {
        waiting = id
        allowed { start(id) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.scope.launch {
            val place = c.ravenVpn.add(uri)
            say(if (place == null) "That isn't a WireGuard file from Proton VPN" else "${place.flag} ${place.label} added")
        }
    }
    val on = active != null
    val current = places.firstOrNull { it.id == active }

    RavenSheet({ ui.sheet = null }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Vpn, null, size = 26.dp, tint = if (on) Green else Space.Text2)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("VPN", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text(
                        when {
                            busy -> "Connecting…"
                            current != null -> "Raven is browsing from ${current.label}"
                            phoneVpn -> "Your Proton VPN app is on for the whole phone"
                            else -> "Off"
                        },
                        style = MaterialTheme.typography.bodySmall, color = if (on) Green else Space.Text2,
                    )
                }
                Toggle(on, { want ->
                    when {
                        !want -> c.scope.launch { c.ravenVpn.choose(null) }
                        places.isEmpty() -> pick.launch(arrayOf("*/*"))
                        else -> connect(c.ravenVpn.last?.takeIf { id -> places.any { it.id == id } } ?: places.first().id)
                    }
                }, "Raven's VPN", enabled = !busy)
            }

            places.forEach { p ->
                PlaceRow(p, p.id == active, enabled = !busy, onTap = { if (p.id == active) c.scope.launch { c.ravenVpn.choose(null) } else connect(p.id) }, onMore = { editing = p })
            }

            // VPN per site: the site on screen can have its own country (or no VPN); the others follow the switch.
            val tab = c.tabs.selected
            val host = tab?.takeIf { !it.isNewTabPage && it.url.value.startsWith("http") }?.host?.takeIf { it.isNotBlank() }
            val rules by c.ravenVpn.rules.collectAsState()
            if (places.isNotEmpty() && host != null) {
                SiteRow(app.raven.browser.engine.RavenVpn.siteOf(host), c.ravenVpn.ruleFor(host), places) { place ->
                    c.ravenVpn.setRule(host, place)
                    val apply = { c.scope.launch { if (!c.ravenVpn.applyFor(host)) say("Couldn't switch the VPN for this site") }; Unit }
                    if (place != null && place != app.raven.browser.engine.RavenVpn.DIRECT) allowed(apply) else apply()
                }
            }
            if (rules.isNotEmpty()) {
                Text("SITES WITH THEIR OWN COUNTRY", style = MaterialTheme.typography.labelSmall, color = Space.Text2, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Space.Surface2)) {
                    rules.entries.sortedBy { it.key }.forEach { (site, place) ->
                        val p = places.firstOrNull { it.id == place }
                        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(site, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (place == app.raven.browser.engine.RavenVpn.DIRECT) "No VPN" else p?.let { "${it.flag} ${it.label}" } ?: "Gone", style = MaterialTheme.typography.bodySmall, color = Space.Text2)
                            IconButton(Icons.Close, "Stop giving $site its own country", { c.ravenVpn.setRule(site, null) }, size = 44.dp, iconSize = 15.dp, tint = Space.Text2)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).border(1.dp, Color(0x33C7CCD8), CircleShape)
                    .clickable(onClickLabel = "Add a location") { pick.launch(arrayOf("*/*")) }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Plus, null, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (places.isEmpty()) "Add your first location" else "Add a location", style = MaterialTheme.typography.labelLarge)
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Space.Surface2).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("A location from your Proton account", style = MaterialTheme.typography.titleSmall)
                Text(
                    "On account.protonvpn.com: Downloads → WireGuard configuration → Android → choose a country → Create → Download. " +
                        "Then add the file here, once per country.",
                    style = MaterialTheme.typography.bodySmall, color = Space.Text2,
                )
                Text(
                    "Only Raven goes through this VPN; other apps keep your normal connection. Android runs one VPN at a time, so " +
                        "turning this on pauses the Proton VPN app's connection. A site with its own country switches Raven's VPN " +
                        "while it's on screen; tabs in the background share it meanwhile.",
                    style = MaterialTheme.typography.bodySmall, color = Space.Text3,
                )
            }
            TextButton({ VpnWatch.open(context) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Open the Proton VPN app", color = Space.Text2)
            }
        }
    }

    editing?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${p.flag} ${p.label}") },
            text = {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { c.ravenVpn.rename(p.id, name); editing = null }),
                )
            },
            confirmButton = { TextButton({ c.ravenVpn.rename(p.id, name); editing = null }) { Text("Save", color = Green) } },
            dismissButton = {
                TextButton({ c.scope.launch { c.ravenVpn.remove(p.id) }; editing = null }) { Text("Remove", color = Space.Solar) }
            },
            containerColor = Space.Surface,
        )
    }
}

private val Green = Color(0xFF8FD6B4)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaceRow(p: VpnPlace, on: Boolean, enabled: Boolean, onTap: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).clip(CircleShape)
            .background(if (on) Green.copy(alpha = 0.14f) else Space.Surface2)
            .border(1.dp, if (on) Green.copy(alpha = 0.55f) else Space.Hairline, CircleShape)
            .combinedClickable(enabled = enabled, onClickLabel = if (on) "Turn off" else "Browse from here", onClick = onTap, onLongClick = onMore)
            .semantics { contentDescription = "${p.label}${if (on) ", connected" else ""}" }
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Space.Surface), contentAlignment = Alignment.Center) { Text(p.flag, fontSize = 22.sp) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(p.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (on) "Connected" else p.name, style = MaterialTheme.typography.bodySmall, color = if (on) Green else Space.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(Icons.Menu, "Rename or remove ${p.label}", onMore, size = 44.dp, iconSize = 18.dp, tint = Space.Text2)
    }
}

/** The site on screen, and where it goes: following the switch, a country of its own, or no VPN. */
@Composable
private fun SiteRow(site: String, rule: String?, places: List<VpnPlace>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val now = when (rule) {
        null -> "Follows the switch"
        app.raven.browser.engine.RavenVpn.DIRECT -> "No VPN"
        else -> places.firstOrNull { it.id == rule }?.let { "${it.flag} ${it.label}" } ?: "Follows the switch"
    }
    Box {
        Row(
            Modifier.fillMaxWidth().height(62.dp).clip(CircleShape).background(Space.Surface2).border(1.dp, Space.Hairline, CircleShape)
                .clickable(onClickLabel = "Choose a country for $site") { open = true }.padding(start = 18.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("This site: $site", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now, style = MaterialTheme.typography.bodySmall, color = Space.Text2)
            }
            Icon(Icons.ChevronDown, null, size = 18.dp, tint = Space.Text2)
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = Space.Surface2, shape = RoundedCornerShape(20.dp)) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("Follow the switch") }, onClick = { open = false; onPick(null) })
            places.forEach { p ->
                androidx.compose.material3.DropdownMenuItem(text = { Text("${p.flag} ${p.label}") }, onClick = { open = false; onPick(p.id) })
            }
            androidx.compose.material3.DropdownMenuItem(text = { Text("No VPN") }, onClick = { open = false; onPick(app.raven.browser.engine.RavenVpn.DIRECT) })
        }
    }
}
