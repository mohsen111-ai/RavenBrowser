package app.raven.browser.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.ui.browser.RavenSheet
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.glass
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space

/** Each flock keeps its colour: ice, silver, amber, moss, lavender, rose. */
private val flockColors = listOf(
    Color(0xFF8FB2FF), Color(0xFFC7CCD8), Color(0xFFE8B07A), Color(0xFF8FD6B4), Color(0xFFB49CFF), Color(0xFFE89AB0),
)

fun flockColor(name: String): Color = flockColors[(name.lowercase().hashCode() and 0x7fffffff) % flockColors.size]

class FlockInfo(val name: String, val count: Int, val cards: List<TabCardInfo>)

/** The Flocks side: each flock a tile with a few of its pages, and a way to start a new one. */
@Composable
fun FlocksGrid(flocks: List<FlockInfo>, onOpen: (String) -> Unit, onNew: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(flocks, key = { it.name }) { f -> FlockTile(f, Modifier.animateItem()) { onOpen(f.name) } }
        item(span = { GridItemSpan(maxLineSpan) }, key = "new") {
            Row(
                Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(26.dp))
                    .drawBehind {
                        drawRoundRect(
                            Color(0x4DC7CCD8), cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    .clickable(role = Role.Button, onClick = onNew),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Plus, null, size = 18.dp, tint = Space.Text)
                Spacer(Modifier.width(8.dp))
                Text("New flock", style = MaterialTheme.typography.labelLarge, color = Space.Text)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "hint") {
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.TabsIcon, null, size = 22.dp, tint = Space.Text2)
                Spacer(Modifier.width(14.dp))
                Text(
                    if (flocks.isEmpty()) "Tabs that belong together fly together. Hold any tab and choose a flock, or start one here."
                    else "Hold any tab to add it to a flock. A flock opens as its own hand of cards.",
                    style = MaterialTheme.typography.bodySmall, color = Space.Text2,
                )
            }
        }
    }
}

@Composable
private fun FlockTile(f: FlockInfo, modifier: Modifier, onClick: () -> Unit) {
    val color = flockColor(f.name)
    Column(
        modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), fill = Color(0xC70D111C))
            .clickable(onClickLabel = "Open flock", onClick = onClick)
            .semantics { contentDescription = "Flock ${f.name}, ${f.count} ${if (f.count == 1) "tab" else "tabs"}" }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Up to four of its pages, two by two; empty places stay faint outlines.
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            (0 until 4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { k ->
                        val card = f.cards.getOrNull(k)
                        Box(
                            Modifier.weight(1f).aspectRatio(1.15f).clip(RoundedCornerShape(10.dp))
                                .then(if (card != null) Modifier.background(Space.Ground) else Modifier.border(1.dp, Color(0x24C7CCD8), RoundedCornerShape(10.dp))),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                card?.thumbnail != null -> Image(card.thumbnail, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                                card != null -> Text(card.label.take(1).uppercase(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Space.Text2)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(f.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${f.count}", style = MaterialTheme.typography.bodySmall, color = Space.Text2)
        }
    }
}

/** Above an open flock's hand: back to all flocks, its name, and what can be done with it. */
@Composable
fun FlockHeader(name: String, count: Int, onBack: () -> Unit, onRename: () -> Unit, onUngroup: () -> Unit, onClose: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Icons.Back, "All flocks", onBack, size = 44.dp, iconSize = 18.dp)
        Box(Modifier.size(11.dp).clip(CircleShape).background(flockColor(name)))
        Spacer(Modifier.width(10.dp))
        Text(name, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(Icons.Menu, "Flock options", { menu = true }, size = 44.dp, iconSize = 18.dp, tint = Space.Text2)
    }
    if (menu) {
        RavenSheet({ menu = false }) {
            Text(name, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            Text("$count ${if (count == 1) "tab" else "tabs"}", style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            SheetAction(Icons.Edit, "Rename flock") { menu = false; onRename() }
            SheetAction(Icons.TabsIcon, "Ungroup (keep the tabs)") { menu = false; onUngroup() }
            SheetAction(Icons.Close, "Close the flock's tabs", Space.Solar) { menu = false; onClose() }
        }
    }
}

/** Holding a tab card. */
@Composable
fun TabActionsSheet(
    name: String,
    private: Boolean,
    flock: String?,
    flocks: List<String>,
    onDismiss: () -> Unit,
    onFlock: (String?) -> Unit,
    onNewFlock: () -> Unit,
    onClose: () -> Unit,
    onFloat: (() -> Unit)? = null,
    onSplit: (() -> Unit)? = null,
) {
    RavenSheet(onDismiss) {
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        onFloat?.let { SheetAction(Icons.Float, "Float") { it() } }
        onSplit?.let { SheetAction(Icons.Split, "Split with this tab") { it() } }
        if (!private) {
            Text("FLOCKS", style = MaterialTheme.typography.labelSmall, color = Space.Text2, modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 4.dp))
            flocks.filter { it != flock }.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(52.dp).clip(CircleShape).clickable { onFlock(f) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(flockColor(f)))
                    Spacer(Modifier.width(18.dp))
                    Text(if (flock == null) "Add to $f" else "Move to $f", style = MaterialTheme.typography.bodyMedium)
                }
            }
            SheetAction(Icons.Plus, "New flock with this tab") { onNewFlock() }
            if (flock != null) SheetAction(Icons.Close, "Take out of $flock") { onFlock(null) }
        }
        SheetAction(Icons.Trash, "Close tab", Space.Solar) { onClose() }
    }
}

@Composable
private fun SheetAction(icon: RavenIcon, text: String, tint: Color = Space.Text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(52.dp).clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, size = 20.dp, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Name a new flock and choose which tabs fly in it. */
@Composable
fun NewFlockDialog(tabs: List<Pair<String, String>>, preselected: List<String>, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(preselected) } }
    fun create() { if (name.isNotBlank()) onCreate(name.trim(), chosen.toList()) }
    // On a short screen the list of tabs is shorter, so the name and the buttons stay in sight.
    val listHeight = if (LocalConfiguration.current.screenHeightDp < 700) 150.dp else 260.dp
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New flock") },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, placeholder = { Text("Work, Reading, Trip…") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                )
                if (tabs.isNotEmpty()) {
                    Text("Tabs in it", style = MaterialTheme.typography.labelMedium, color = Space.Text2, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    Column(Modifier.heightIn(max = listHeight).verticalScroll(rememberScrollState())) {
                        tabs.forEach { (id, title) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (id in chosen) chosen.remove(id) else chosen.add(id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(id in chosen, { on -> if (on) chosen.add(id) else chosen.remove(id) }, colors = CheckboxDefaults.colors(checkedColor = Raven.accent, checkmarkColor = Space.OnAccent, uncheckedColor = Space.Text2))
                                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(::create, enabled = name.isNotBlank()) { Text("Create", color = if (name.isNotBlank()) Raven.accent else Space.Text3) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}

@Composable
fun RenameFlockDialog(old: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(old) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename flock") },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onRename(name) }),
            )
        },
        confirmButton = { TextButton({ onRename(name) }) { Text("Save", color = Raven.accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}

/** Asks for a name: [title], a line on what it's for, and the button that keeps it. */
@Composable
fun NameDialog(title: String, detail: String?, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    name, { name = it }, singleLine = true, placeholder = { Text("Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone(name) }),
                )
            }
        },
        confirmButton = { TextButton({ onDone(name) }) { Text(confirm, color = Raven.accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}
