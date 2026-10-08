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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.ui.components.Toggle
import app.raven.browser.ui.sky.Wallpaper
import app.raven.browser.ui.sky.Wallpapers
import app.raven.browser.ui.sky.drawLive
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
                        val dim = if (rotate && !included) 0.3f else 1f
                        val thumb = thumbs[w.id]
                        if (thumb != null) {
                            Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = dim })
                            // A live one shows its still moment (the fire burning, the candle lit) on its tile.
                            w.live?.let { live ->
                                val canvas = androidx.compose.runtime.remember { app.raven.browser.ui.sky.LiveCanvas() }
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = dim }) {
                                    drawLive(live, app.raven.browser.ui.sky.SkyMap(size.width, size.height), live.still, canvas)
                                }
                            }
                        } else if (w.res == null) {
                            // Plain night: no picture, so a few stars and its name, to show it's there.
                            PlainNightTile(Modifier.fillMaxSize().graphicsLayer { alpha = dim })
                        }
                        if (w.live != null) {
                            Text(
                                "LIVE", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Space.Text,
                                lineHeight = 9.sp,
                                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xB3070A12)).padding(horizontal = 4.dp, vertical = 2.dp),
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

/** The plain night's tile: the bare midnight ground with a few stars and its name. */
@Composable
private fun PlainNightTile(modifier: Modifier) {
    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            listOf(0.2f to 0.15f, 0.7f to 0.1f, 0.45f to 0.3f, 0.85f to 0.38f, 0.15f to 0.5f, 0.6f to 0.55f, 0.32f to 0.72f).forEachIndexed { i, (x, y) ->
                drawCircle(Color.White.copy(alpha = if (i % 3 == 0) 0.9f else 0.5f), radius = if (i % 3 == 0) 1.6f else 1f, center = androidx.compose.ui.geometry.Offset(x * size.width, y * size.height))
            }
        }
        Text(
            "Plain\nnight", fontSize = 9.sp, lineHeight = 10.sp, color = Space.Text2, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
