package app.raven.browser.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.raven.browser.Container
import app.raven.browser.engine.Engine
import app.raven.browser.ui.Screen
import app.raven.browser.ui.UiState
import app.raven.browser.ui.browser.siteLabel
import app.raven.browser.ui.components.AmberPlanet
import app.raven.browser.ui.components.Card
import app.raven.browser.ui.components.Divider
import app.raven.browser.ui.components.ListRow
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.components.ScreenHeader
import app.raven.browser.ui.components.SectionLabel
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.components.Toggle
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import org.mozilla.geckoview.WebExtension
import java.io.File

@Composable
fun AddonsScreen(c: Container, ui: UiState, onBack: () -> Unit) {
    val addons by c.engine.addons.collectAsState()
    val prefs by c.settings.prefs.collectAsState()
    val context = LocalContext.current
    var removing by remember { mutableStateOf<WebExtension?>(null) }
    var checking by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = File(context.cacheDir, "install-${System.currentTimeMillis()}.xpi")
        runCatching { context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } } }
            .onSuccess { c.engine.install(Uri.fromFile(file).toString()) }
            .onFailure { c.engine.messages.tryEmit("Couldn't read that file") }
    }
    val sorted = addons.sortedWith(compareBy({ it.id != Engine.UBO_ID }, { it.metaData.name?.lowercase() }))
    Column(Modifier.fillMaxSize().background(Raven.ground).navigationBarsPadding()) {
        ScreenHeader("Add-ons", onBack) {
            PillButton("Get more", {
                c.tabs.newTab(url = "https://addons.mozilla.org/android/")
                ui.go(Screen.Browser)
            }, icon = Icons.Plus, style = PillStyle.Soft, height = 40.dp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.width(1.dp))
            if (sorted.isEmpty()) {
                Text("uBlock Origin is being installed…", style = MaterialTheme.typography.bodyMedium, color = Space.Text2, modifier = Modifier.padding(8.dp))
            }
            sorted.forEach { ext ->
                val meta = ext.metaData
                Card {
                    Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (ext.id == Engine.UBO_ID) AmberPlanet(22.dp, Modifier.padding(end = 4.dp)) else SitePlanet(meta.name ?: siteLabel(ext.id), 44.dp, sleeping = !meta.enabled)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(meta.name ?: ext.id, style = MaterialTheme.typography.titleMedium, color = if (meta.enabled) Space.Text else Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(meta.version, meta.creatorName).joinToString(" · ") + if (!meta.enabled) " · turned off" else "",
                                style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Toggle(meta.enabled, { c.engine.setEnabled(ext, it) }, "${meta.name} enabled")
                    }
                    if (meta.enabled) {
                        Divider()
                        ListRow("Runs in private tabs", trailing = { Toggle(meta.allowedInPrivateBrowsing, { c.engine.setAllowedInPrivate(ext, it) }, "Allow ${meta.name} in private tabs") })
                    }
                    Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = meta.optionsPageUrl
                        if (options != null && meta.enabled) PillButton(if (ext.id == Engine.UBO_ID) "Dashboard" else "Settings", {
                            c.tabs.newTab(url = options)
                            ui.go(Screen.Browser)
                        }, style = PillStyle.Outline, height = 40.dp)
                        PillButton("Remove", { removing = ext }, style = PillStyle.Outline, height = 40.dp)
                    }
                }
            }
            SectionLabel("Updates")
            Card {
                ListRow(
                    "Check for add-on updates",
                    detail = "Raven checks every day by itself" + if (prefs.addonsLastChecked > 0) " · last ${android.text.format.DateUtils.getRelativeTimeSpanString(prefs.addonsLastChecked)}" else "",
                    trailing = {
                        PillButton(if (checking) "Checking…" else "Check now", {
                            checking = true
                            c.engine.updateAll { n ->
                                checking = false
                                c.engine.messages.tryEmit(if (n == 0) "Everything is up to date" else "Updated $n add-on${if (n > 1) "s" else ""}")
                            }
                        }, style = PillStyle.Outline, height = 40.dp, enabled = !checking)
                    },
                )
            }
            SectionLabel("Install")
            Card {
                ListRow("Browse Firefox add-ons", icon = Icons.Puzzle, chevron = true, onClick = {
                    c.tabs.newTab(url = "https://addons.mozilla.org/android/")
                    ui.go(Screen.Browser)
                })
                Divider()
                ListRow("Install from a file (.xpi)", icon = Icons.File, chevron = true, onClick = { pickFile.launch(arrayOf("application/x-xpinstall", "application/zip", "application/octet-stream")) })
            }
            if (addons.none { it.id == Engine.UBO_ID }) {
                PillButton("Reinstall uBlock Origin", { c.engine.installUbo() }, Modifier.fillMaxWidth(), style = PillStyle.Amber)
            }
        }
    }
    removing?.let { ext ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${ext.metaData.name}?") },
            text = { Text(if (ext.id == Engine.UBO_ID) "Ads and trackers will no longer be blocked by uBlock Origin. You can reinstall it from this screen." else "Its settings will be deleted.") },
            confirmButton = { TextButton({ c.engine.uninstall(ext); removing = null }) { Text("Remove", color = Space.Solar) } },
            dismissButton = { TextButton({ removing = null }) { Text("Cancel") } },
            containerColor = Space.Surface,
        )
    }
}
