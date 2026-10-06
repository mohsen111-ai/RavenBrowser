package app.raven.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.raven.browser.Container
import app.raven.browser.data.Visit
import app.raven.browser.ui.Screen
import app.raven.browser.ui.UiState
import app.raven.browser.ui.browser.siteLabel
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.components.ScreenHeader
import app.raven.browser.ui.components.SectionLabel
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Range(val label: String, val days: Long?) { TODAY("Today", 0), YESTERDAY("Yesterday", 1), WEEK("This week", 7), ALL("All", null) }

@Composable
fun HistoryScreen(c: Container, ui: UiState, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(Range.ALL) }
    var visits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var clearing by remember { mutableStateOf(false) }
    val version by c.db.historyVersion.collectAsState()
    // The history of the profile you're in.
    val profile by c.tabs.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    LaunchedEffect(query, range, version, profile) {
        val since = range.days?.let { LocalDate.now().minusDays(it).atStartOfDay(zone).toInstant().toEpochMilli() } ?: 0L
        visits = c.db.history(query, since, profile = profile).let { list ->
            if (range == Range.YESTERDAY) {
                val today = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                list.filter { it.time < today }
            } else list
        }
    }
    val groups = visits.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    Column(Modifier.fillMaxSize().background(Raven.ground).navigationBarsPadding()) {
        ScreenHeader("History", onBack) {
            PillButton("Clear…", { clearing = true }, style = PillStyle.Outline, height = 40.dp)
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(24.dp)).background(Space.Surface).border(1.dp, Color(0x17FFFFFF), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Search, null, size = 18.dp, tint = Space.Text2)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search history", color = Space.Text2, style = MaterialTheme.typography.bodyMedium)
                BasicTextField(
                    query, { query = it }, singleLine = true, cursorBrush = SolidColor(Raven.accent),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Space.Text),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search history" },
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.entries.forEach { r ->
                val on = r == range
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (on) Space.Surface3 else Color.Transparent)
                        .border(1.dp, if (on) Color.Transparent else Color(0x14FFFFFF), RoundedCornerShape(18.dp))
                        .clickable { range = r }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(r.label, style = MaterialTheme.typography.labelMedium, color = if (on) Space.Text else Space.Text2, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
            if (visits.isEmpty()) item {
                Text(
                    if (query.isBlank()) "Nothing here yet. Private tabs are never saved." else "No matches.",
                    style = MaterialTheme.typography.bodyMedium, color = Space.Text2, modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
                )
            }
            groups.forEach { (day, list) ->
                item(key = "h$day") {
                    val label = when (day) {
                        LocalDate.now() -> "Today"
                        LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
                    }
                    SectionLabel(label)
                }
                items(list, key = { it.id }) { v ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .clickable { c.tabs.newTab(url = v.url); ui.go(Screen.Browser) }
                            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SitePlanet(siteLabel(v.host), 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.title.ifBlank { v.host }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${v.host} · ${Instant.ofEpochMilli(v.time).atZone(zone).format(timeFmt)}", style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 1)
                        }
                        IconButton(Icons.Close, "Delete ${v.title.ifBlank { v.host }} from history", { scope.launch { c.db.deleteVisit(v.id) } }, size = 44.dp, iconSize = 15.dp, tint = Space.Text2)
                    }
                }
            }
        }
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("Clear history") },
            text = {
                Column {
                    listOf("Last hour" to 3_600_000L, "Today" to -1L, "Everything" to 0L).forEach { (label, span) ->
                        Text(
                            label, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                val since = when (span) {
                                    0L -> 0L
                                    -1L -> LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                                    else -> System.currentTimeMillis() - span
                                }
                                scope.launch { c.db.clearHistory(since, profile) }
                                clearing = false
                            }.padding(vertical = 14.dp, horizontal = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton({ clearing = false }) { Text("Cancel") } },
            containerColor = Space.Surface,
        )
    }
}
