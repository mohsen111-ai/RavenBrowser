package app.raven.browser.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.raven.browser.Container
import app.raven.browser.downloads.DownloadItem
import app.raven.browser.downloads.DownloadStatus
import app.raven.browser.downloads.DownloadText
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.components.RingedPlanet
import app.raven.browser.ui.components.ScreenHeader
import app.raven.browser.ui.components.SectionLabel
import app.raven.browser.ui.components.starfield
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space

@Composable
fun DownloadsScreen(c: Container, ui: UiState, onBack: () -> Unit) {
    val items by c.downloads.items.collectAsState()
    val context = LocalContext.current
    val running = items.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PAUSED }
    val finished = items.filter { it.status == DownloadStatus.DONE || it.status == DownloadStatus.FAILED }
    val active = items.filter { it.status == DownloadStatus.RUNNING }
    Column(Modifier.fillMaxSize().starfield(seed = 5, ground = Raven.ground).navigationBarsPadding()) {
        ScreenHeader("Downloads", onBack) {
            if (finished.isNotEmpty()) PillButton("Clear list", { c.downloads.clearFinished() }, style = PillStyle.Outline, height = 40.dp)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RingedPlanet(56.dp, ringWidth = 3.6f, ring = Color(0x1FFFFFFF))
                    Spacer(Modifier.height(16.dp))
                    val speed = active.sumOf { it.speed }
                    Text(
                        when {
                            active.isNotEmpty() -> "${active.size} downloading"
                            items.isEmpty() -> "No downloads yet"
                            running.isNotEmpty() -> if (running.size == 1) "1 paused" else "${running.size} paused"
                            else -> "All done"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                    )
                    Text(
                        if (active.isNotEmpty()) "${Formatter.formatShortFileSize(context, speed)}/s" else "Files go to your phone's Downloads folder",
                        style = MaterialTheme.typography.bodySmall, color = Space.Text2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (running.isNotEmpty()) item { SectionLabel("In progress") }
            items(running, key = { it.id }) { DownloadRow(c, it) }
            if (finished.isNotEmpty()) item { SectionLabel("Finished") }
            items(finished, key = { it.id }) { DownloadRow(c, it) }
        }
    }
}

/**
 * A finished file was tapped: the phone's file manager opens on the Downloads folder, where the file is. Tries the
 * ways file managers answer to, one after another, and only says so if none of them does.
 */
private fun showInFiles(context: android.content.Context, item: DownloadItem) {
    val ways = listOf(
        // The Downloads folder in the Files app (the document provider's own folder address).
        android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"), "vnd.android.document/directory"),
        // Android's own Downloads list.
        android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS),
        // The file manager's chooser view of everything.
        android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(android.content.Intent.CATEGORY_OPENABLE),
    )
    for (way in ways) {
        val started = runCatching { context.startActivity(way.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (started) return
    }
    android.widget.Toast.makeText(context, "No file manager found. ${item.name} is in your Downloads folder.", android.widget.Toast.LENGTH_LONG).show()
}

@Composable
private fun DownloadRow(c: Container, item: DownloadItem) {
    val context = LocalContext.current
    val accent = Raven.accent
    val fmt = { b: Long -> Formatter.formatShortFileSize(context, b) }
    val detail = when (item.status) {
        DownloadStatus.RUNNING -> buildString {
            append(if (item.total > 0) "${fmt(item.done)} of ${fmt(item.total)}" else fmt(item.done))
            if (!item.waiting) append(" · ${fmt(item.speed)}/s")
            DownloadText.timeLeft(item)?.let { append(" · $it") }
            if (item.connections > 1) append(" · ${item.connections} connections")
        }
        DownloadStatus.PAUSED -> "Paused at ${(item.progress * 100).toInt()}%" + (item.error?.let { " · $it" } ?: " · resumes where it stopped")
        DownloadStatus.DONE -> if (item.activeMs > 0) {
            "${fmt(item.done)} · Finished in ${DownloadText.duration(item.activeMs)} · ${fmt(DownloadText.averageSpeed(item))}/s average"
        } else {
            fmt(item.done)
        }
        DownloadStatus.FAILED -> item.error ?: "Failed"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Space.Surface)
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(20.dp))
            .clickable(enabled = item.status == DownloadStatus.DONE) { showInFiles(context, item) }
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            val done = item.status == DownloadStatus.DONE
            Canvas(Modifier.size(48.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                drawArc(Color(0x1AFFFFFF), 0f, 360f, false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                val sweep = if (done) 360f else 360f * item.progress
                drawArc(
                    if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) Space.Text3 else accent,
                    -90f, sweep, false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Icon(if (done) Icons.Check else Icons.File, null, size = 20.dp, tint = if (done) accent else Space.Text, stroke = if (done) 2.2f else 1.7f)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        when (item.status) {
            DownloadStatus.RUNNING -> IconButton(Icons.Pause, "Pause ${item.name}", { c.downloads.pause(item.id) }, background = Space.Surface2, size = 44.dp, iconSize = 18.dp)
            DownloadStatus.PAUSED -> Row {
                if (item.resumable) IconButton(Icons.Play, "Resume ${item.name}", { c.downloads.resume(item.id) }, background = Space.Surface2, size = 44.dp, iconSize = 16.dp)
                IconButton(Icons.Close, "Cancel ${item.name}", { c.downloads.cancel(item.id) }, size = 44.dp, iconSize = 16.dp, tint = Space.Text2)
            }
            DownloadStatus.DONE -> IconButton(Icons.Close, "Remove ${item.name} from the list", { c.downloads.remove(item.id) }, size = 44.dp, iconSize = 16.dp, tint = Space.Text2)
            DownloadStatus.FAILED -> IconButton(Icons.Close, "Remove ${item.name}", { c.downloads.cancel(item.id) }, size = 44.dp, iconSize = 16.dp, tint = Space.Text2)
        }
    }
}
