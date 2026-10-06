package app.raven.browser.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.Container
import app.raven.browser.data.Bookmark
import app.raven.browser.ui.Screen
import app.raven.browser.ui.UiState
import app.raven.browser.ui.browser.RavenSheet
import app.raven.browser.ui.browser.copy
import app.raven.browser.ui.browser.share
import app.raven.browser.ui.browser.siteLabel
import app.raven.browser.ui.components.FitText
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.launch

/** Bookmarks: the hoard. Like a raven's, it keeps the shiny things you found. */
@Composable
fun BookmarksScreen(c: Container, ui: UiState, onBack: () -> Unit) {
    val version by c.db.bookmarksVersion.collectAsState()
    val all by c.db.bookmarked.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var folders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(version, query, folder) {
        folders = c.db.bookmarkFolders()
        // The folder emptied out (its last bookmark moved or removed): back to all of them.
        if (folder != null && folders.none { it.first == folder }) folder = null
        items = c.db.bookmarks(query, folder)
    }
    var actions by remember { mutableStateOf<Bookmark?>(null) }
    var renaming by remember { mutableStateOf<Bookmark?>(null) }
    var moving by remember { mutableStateOf<Bookmark?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun open(b: Bookmark, newTab: Boolean = false, private: Boolean = false) {
        val tab = c.tabs.selected
        when {
            private -> c.tabs.newTab(url = b.url, private = true)
            newTab || tab == null || tab.private -> c.tabs.newTab(url = b.url)
            else -> c.tabs.load(tab, b.url)
        }
        ui.go(Screen.Browser)
    }

    BookmarksContent(
        total = all.size, query = query, onQuery = { query = it }, folders = folders, folder = folder, onFolder = { folder = it },
        items = items, onOpen = { open(it) }, onMore = { actions = it }, onBack = onBack,
    )

    actions?.let { b ->
        RavenSheet({ actions = null }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SitePlanet(siteLabel(b.url), 46.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(b.url.removePrefix("https://").removePrefix("www."), style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(6.dp))
            SheetRow(Icons.NewTab, "Open in new tab") { actions = null; open(b, newTab = true) }
            SheetRow(Icons.Eclipse, "Open in private tab", Space.Nebula) {
                actions = null
                val go = { open(b, private = true) }
                (context as? app.raven.browser.MainActivity)?.unlockPrivate(go) ?: go()
            }
            SheetRow(Icons.Edit, "Rename") { actions = null; renaming = b }
            SheetRow(Icons.Folder, if (b.folder.isEmpty()) "Put in a folder" else "Move from ${b.folder}") { actions = null; moving = b }
            SheetRow(Icons.Link, "Copy link") { copy(context, b.url); actions = null }
            SheetRow(Icons.Share, "Share") { share(context, b.url); actions = null }
            SheetRow(Icons.Trash, "Remove", Space.Solar) {
                actions = null
                scope.launch {
                    c.db.removeBookmark(b.url)
                    ui.snackbar.currentSnackbarData?.dismiss()
                    val r = ui.snackbar.showSnackbar("Removed from bookmarks", actionLabel = "Undo", duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) c.db.restoreBookmark(b)
                }
            }
        }
    }
    renaming?.let { b ->
        var text by remember(b.url) { mutableStateOf(b.title) }
        fun save() { scope.launch { c.db.editBookmark(b.url, title = text.trim().ifEmpty { b.host }) }; renaming = null }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    text, { text = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
                )
            },
            confirmButton = { TextButton(::save) { Text("Save", color = Raven.accent) } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel", color = Space.Text2) } },
            containerColor = Space.Surface,
        )
    }
    moving?.let { b ->
        FolderDialog(b.folder, folders.map { it.first }, onDismiss = { moving = null }) { f ->
            scope.launch { c.db.editBookmark(b.url, folder = f) }
            moving = null
        }
    }
}

/** Pick a folder for a bookmark, or make a new one. */
@Composable
fun FolderDialog(current: String, folders: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (naming) "New folder" else "Folder") },
        text = {
            if (naming) {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, placeholder = { Text("Reading, Recipes…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onPick(name.trim()) }),
                )
            } else Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                (listOf("") + folders).forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(f) }.padding(horizontal = 6.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (f.isEmpty()) Icons.Bookmark else Icons.Folder, null, size = 18.dp, tint = if (f == current) Raven.accent else Space.Text2)
                        Spacer(Modifier.width(12.dp))
                        Text(f.ifEmpty { "No folder" }, style = MaterialTheme.typography.bodyMedium, color = if (f == current) Raven.accent else Space.Text, modifier = Modifier.weight(1f))
                        if (f == current) Icon(Icons.Check, "Current", size = 16.dp, tint = Raven.accent)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { naming = true }.padding(horizontal = 6.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Plus, null, size = 18.dp, tint = Raven.accent)
                    Spacer(Modifier.width(12.dp))
                    Text("New folder", style = MaterialTheme.typography.bodyMedium, color = Raven.accent)
                }
            }
        },
        confirmButton = {
            if (naming) TextButton({ if (name.isNotBlank()) onPick(name.trim()) }) { Text("Create", color = Raven.accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}

@Composable
private fun SheetRow(icon: RavenIcon, text: String, tint: Color = Space.Text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(52.dp).clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, size = 20.dp, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarksContent(
    total: Int,
    query: String,
    onQuery: (String) -> Unit,
    folders: List<Pair<String, Int>>,
    folder: String?,
    onFolder: (String?) -> Unit,
    items: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onMore: (Bookmark) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Raven.ground)
            .drawBehind {
                // A little moonlight in the corner.
                val r = size.width * 0.9f
                drawCircle(Brush.radialGradient(listOf(Color(0x1AC7CCD8), Color.Transparent), center = Offset(size.width * 0.1f, 0f), radius = r), r, Offset(size.width * 0.1f, 0f))
            }
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Icons.Back, "Back", onBack, background = Space.Surface, size = 44.dp, iconSize = 20.dp)
        }
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp)) {
            Text("THE HOARD · $total SAVED", style = MaterialTheme.typography.labelSmall, color = Space.Text2)
            FitText("Bookmarks", TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-1).sp, lineHeight = 38.sp), Modifier.padding(top = 4.dp))
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().height(50.dp).clip(CircleShape).background(Space.Surface)
                .border(1.dp, Space.Hairline, CircleShape).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Search, null, size = 17.dp, tint = Space.Text2)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                query, onQuery, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Space.Text),
                cursorBrush = SolidColor(Raven.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Search your bookmarks" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search your bookmarks", style = MaterialTheme.typography.bodyLarge, color = Space.Text3)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) IconButton(Icons.Close, "Clear", { onQuery("") }, size = 42.dp, iconSize = 15.dp, tint = Space.Text2)
        }
        if (folders.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                item { FolderChip("All", folder == null) { onFolder(null) } }
                items(folders, key = { it.first }) { (name, n) -> FolderChip("$name · $n", folder == name) { onFolder(name) } }
            }
        }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (query.isNotEmpty()) "Nothing matches" else "Nothing hoarded yet",
                    fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center,
                )
                Text(
                    if (query.isNotEmpty()) "Try other words." else "Open the menu on any page and tap the bookmark to keep it here.",
                    style = MaterialTheme.typography.bodyMedium, color = Space.Text2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(items, key = { it.id }) { b ->
                Row(
                    Modifier.animateItem().fillMaxWidth().height(66.dp).clip(CircleShape).background(Space.Surface)
                        .border(1.dp, Space.Hairline, CircleShape)
                        .combinedClickable(onClickLabel = "Open", onLongClickLabel = "More", onClick = { onOpen(b) }, onLongClick = { onMore(b) })
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SitePlanet(siteLabel(b.url), 44.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            b.host + if (b.folder.isNotEmpty() && folder == null) " · ${b.folder}" else "",
                            style = MaterialTheme.typography.bodySmall, color = Space.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(Icons.Menu, "More for ${b.title}", { onMore(b) }, size = 44.dp, iconSize = 18.dp, tint = Space.Text2)
                }
            }
        }
    }
}

@Composable
private fun FolderChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(CircleShape)
            .then(if (on) Modifier.background(Space.Text) else Modifier.border(1.dp, Color(0x33C7CCD8), CircleShape))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (on) Space.OnAccent else Space.Text)
    }
}
