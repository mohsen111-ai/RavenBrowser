package app.raven.browser.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.raven.browser.BuildConfig
import app.raven.browser.CrashLog
import app.raven.browser.Container
import app.raven.browser.MainActivity
import app.raven.browser.data.DnsProvider
import app.raven.browser.data.Isolation
import app.raven.browser.data.Motion
import app.raven.browser.data.Prefs
import app.raven.browser.data.SearchEngine
import app.raven.browser.ui.components.Card
import app.raven.browser.ui.components.Divider
import app.raven.browser.ui.components.ListRow
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.components.ScreenHeader
import app.raven.browser.ui.components.SectionLabel
import app.raven.browser.ui.components.Toggle
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space

private class Choice<T>(val title: String, val options: List<Pair<T, String>>, val current: T, val onPick: (T) -> Unit)

@Composable
fun SettingsScreen(c: Container, onBack: () -> Unit) {
    val p by c.settings.prefs.collectAsState()
    val startPrefs = remember { c.settings.current }
    val needsRestart = p.isolation != startPrefs.isolation
    var choice by remember { mutableStateOf<Choice<*>?>(null) }
    val context = LocalContext.current
    fun set(t: (Prefs) -> Prefs) {
        c.settings.update(t)
        c.engine.applyLiveSettings(c.settings.current)
    }

    Column(Modifier.fillMaxSize().background(Raven.ground).navigationBarsPadding()) {
        ScreenHeader("Settings", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            if (needsRestart) {
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Raven.accent.copy(alpha = 0.12f)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Restart Raven to apply your changes", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    PillButton("Restart", { restart(context as Activity) }, height = 40.dp)
                }
            }

            SectionLabel("Search")
            Card {
                ListRow("Search engine", value = p.searchEngine.label, chevron = true, onClick = {
                    choice = Choice("Search engine", SearchEngine.entries.map { it to it.label }, p.searchEngine) { v -> set { it.copy(searchEngine = v) } }
                })
            }

            SectionLabel("Privacy and security")
            Card {
                ListRow("Tracking protection", detail = "Blocks trackers even without uBlock Origin", value = if (p.strictTracking) "Strict" else "Standard", chevron = true, onClick = {
                    choice = Choice("Tracking protection", listOf(true to "Strict (recommended)", false to "Standard (fewer broken sites)"), p.strictTracking) { v -> set { it.copy(strictTracking = v) } }
                })
                Divider()
                ListRow("Cookie popups", detail = "Says no for you: the site's Reject button is pressed, and popups without one are hidden", trailing = { Toggle(p.cookiePopups, { v -> set { it.copy(cookiePopups = v) } }, "Turn down cookie popups") })
                Divider()
                ListRow("HTTPS-only mode", detail = "Warns before opening unsecure sites", trailing = { Toggle(p.httpsOnly, { v -> set { it.copy(httpsOnly = v) } }, "HTTPS-only mode") })
                Divider()
                ListRow("Encrypted DNS", detail = "Hides which sites you look up from your network", value = p.dns.label.substringBefore(" ("), chevron = true, onClick = {
                    choice = Choice("Encrypted DNS", DnsProvider.entries.map { it to it.label }, p.dns) { v -> set { it.copy(dns = v) } }
                })
                Divider()
                ListRow("Site isolation", detail = p.isolation.detail, value = p.isolation.label, chevron = true, onClick = {
                    choice = Choice("Site isolation", Isolation.entries.map { it to "${it.label}: ${it.detail}" }, p.isolation) { v -> set { it.copy(isolation = v) } }
                })
                Divider()
                ListRow(
                    "Lock private tabs", detail = "Your fingerprint or screen lock opens them after you leave Raven. They're also kept out of screenshots and recent apps.",
                    trailing = {
                        Toggle(p.lockPrivateTabs, { v ->
                            val main = context as? app.raven.browser.MainActivity
                            when {
                                main == null -> Unit
                                v && !main.canLock() -> android.widget.Toast.makeText(context, "Set a screen lock on your phone first (Settings, Security)", android.widget.Toast.LENGTH_LONG).show()
                                else -> main.authenticate(if (v) "Lock private tabs" else "Stop locking private tabs") {
                                    set { it.copy(lockPrivateTabs = v) }
                                    if (!v) c.privateLocked.value = false
                                }
                            }
                        }, "Lock private tabs")
                    },
                )
                Divider()
                ListRow("Erase data when closing", detail = "History, cookies and site data", trailing = { Toggle(p.eraseOnClose, { v -> set { it.copy(eraseOnClose = v) } }, "Erase data when closing") })
                Divider()
                ListRow("Clean slate button", detail = "In the menu: closes every tab and erases what you choose. Close all on the Tabs screen only closes tabs", trailing = { Toggle(p.supernovaButton, { v -> set { it.copy(supernovaButton = v) } }, "Clean slate button") })
            }

            SectionLabel("Look and feel")
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
                ListRow("Moving sky", detail = "Stars twinkle and the moon breathes on the home screen", trailing = { Toggle(p.movingSky, { v -> set { it.copy(movingSky = v) } }, "Moving sky") })
                Divider()
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
                ListRow("Pure black", detail = "Darker than midnight behind web pages, saves battery on OLED screens", trailing = { Toggle(p.trueBlack, { v -> set { it.copy(trueBlack = v) } }, "Pure black") })
                Divider()
                ListRow("Dark websites", detail = "Ask sites for their dark version when they have one", trailing = { Toggle(p.darkWebsites, { v -> set { it.copy(darkWebsites = v) } }, "Dark websites") })
                Divider()
                ListRow("Reduce motion", value = p.reduceMotion.label, chevron = true, onClick = {
                    choice = Choice("Reduce motion", Motion.entries.map { it to it.label }, p.reduceMotion) { v -> set { it.copy(reduceMotion = v) } }
                })
                Divider()
                ListRow("Picture-in-picture", detail = "A fullscreen video keeps playing in a small window when you leave Raven", trailing = { Toggle(p.pictureInPicture, { v -> set { it.copy(pictureInPicture = v) } }, "Picture-in-picture") })
                Divider()
                ListRow("Text size", value = "${p.textScale}%", chevron = true, onClick = {
                    choice = Choice("Text size on websites", listOf(80, 90, 100, 110, 125, 150).map { it to "$it%" }, p.textScale) { v -> set { it.copy(textScale = v) } }
                })
            }

            SectionLabel("Tabs")
            Card {
                ListRow("Put unused tabs to sleep", detail = "Frees memory; they reload when opened", value = minutes(p.sleepAfterMinutes), chevron = true, onClick = {
                    choice = Choice("Sleep tabs not used for", listOf(5, 10, 30, 60, 0).map { it to minutes(it) }, p.sleepAfterMinutes) { v -> set { it.copy(sleepAfterMinutes = v) } }
                })
                Divider()
                ListRow("Close tabs not used for", value = days(p.closeAfterDays), chevron = true, onClick = {
                    choice = Choice("Close tabs not used for", listOf(0, 1, 7, 30).map { it to days(it) }, p.closeAfterDays) { v -> set { it.copy(closeAfterDays = v) } }
                })
            }

            SectionLabel("Downloads")
            Card {
                ListRow("Connections per download", detail = "Used when the server allows it", value = "${p.connections}", chevron = true, onClick = {
                    choice = Choice(
                        "Connections per download",
                        listOf(1, 2, 4, 6, 8, 12, 16).map {
                            it to when {
                                it == 1 -> "1 (off)"
                                it > 8 -> "$it (some servers refuse this many)"
                                else -> "$it"
                            }
                        },
                        p.connections,
                    ) { v -> set { it.copy(connections = v) } }
                })
            }

            SectionLabel("About")
            Card {
                ListRow("Raven ${BuildConfig.VERSION_NAME}", detail = "Gecko engine ${BuildConfig.GECKO_VERSION} · uBlock Origin by Raymond Hill")
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
    }

    choice?.let { ch ->
        @Suppress("UNCHECKED_CAST")
        val typed = ch as Choice<Any?>
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(typed.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    typed.options.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { typed.onPick(value); choice = null }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(value == typed.current, null)
                            Spacer(Modifier.size(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ choice = null }) { Text("Close") } },
            containerColor = Space.Surface,
        )
    }
}

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

