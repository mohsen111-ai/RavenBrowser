package app.raven.browser.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.raven.browser.ui.components.Toggle
import app.raven.browser.ui.sky.Wallpaper
import app.raven.browser.ui.sky.Wallpapers
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space

/**
 * Wallpapers in Settings. With the rotation on, each one with a tick takes its turn (tap to leave it out); with it
 * off, tap the one to keep.
 */
@Composable
fun WallpaperPicker(
    rotate: Boolean,
    chosen: String,
    off: List<String>,
    thumbs: Map<String, ImageBitmap?>,
    onRotate: (Boolean) -> Unit,
    onTap: (Wallpaper) -> Unit,
) {
    val taking = Wallpapers.all.count { it.id !in off }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("New wallpaper each time", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (rotate) "$taking take turns, one each time you open Raven. Tap one to leave it out." else "Tap the wallpaper to keep.",
                    style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Toggle(rotate, onRotate, "New wallpaper each time")
        }
        Wallpapers.all.chunked(6).forEach { row ->
            Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { w ->
                    val included = w.id !in off
                    val on = if (rotate) included else w.id == chosen
                    val accent = Raven.accent
                    Box(
                        Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(14.dp)).background(Space.Ground)
                            .border(if (!rotate && on) 2.dp else 1.dp, if (!rotate && on) accent else Space.Hairline, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Checkbox) { onTap(w) }
                            .semantics {
                                contentDescription = w.name
                                selected = on
                                stateDescription = if (rotate) (if (included) "In the rotation" else "Left out") else if (on) "Chosen" else ""
                            },
                    ) {
                        thumbs[w.id]?.let {
                            Image(
                                it, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (rotate && !included) 0.3f else 1f },
                            )
                        }
                        if (rotate && included) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(5.dp).size(17.dp).clip(CircleShape).background(accent),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Check, null, size = 11.dp, tint = Space.OnAccent, stroke = 3f) }
                        }
                    }
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
