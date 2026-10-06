package app.raven.browser.ui.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.raven.browser.Container
import app.raven.browser.data.Visit
import app.raven.browser.engine.BrowserTab
import app.raven.browser.engine.Taps
import app.raven.browser.engine.UrlInput
import app.raven.browser.ui.Screen
import app.raven.browser.ui.Sheet
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.components.glass
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import app.raven.browser.engine.Displays

@Composable
fun BrowserScreen(c: Container, ui: UiState) {
    val tabs by c.tabs.tabs.collectAsState()
    val selectedId by c.tabs.selectedId.collectAsState()
    val tab = tabs.firstOrNull { it.id == selectedId }
    val ground = Raven.ground

    Column(
        Modifier
            .fillMaxSize()
            .background(if (tab?.private == true) Space.NebulaGround else ground)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (!ui.fullscreen && tab != null) Bars(c, ui, tab, tabs.size)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Split screen: two tabs, the bar belonging to the half you touched last. A video going fullscreen in
            // a half fills the whole screen, and the split comes back when it leaves fullscreen.
            val split by c.tabs.split.collectAsState()
            val top = split?.let { s -> tabs.firstOrNull { it.id == s.top } }
            val bottom = split?.let { s -> tabs.firstOrNull { it.id == s.bottom } }
            val fullscreen = tab?.fullscreen?.collectAsState()?.value == true
            if (tab != null && top != null && bottom != null && !fullscreen) {
                SplitArea(c, ui, top, bottom, tab.id)
            } else if (tab != null) {
                TabPage(c, ui, tab, primary = true)
            }
            if (ui.editing && tab != null) Suggestions(c, ui, tab)
        }
    }
}

/** The address bar, or in its place the address being typed or the find-in-page field. */
@Composable
private fun Bars(c: Container, ui: UiState, tab: BrowserTab, tabCount: Int) {
    when {
        ui.findOpen -> FindBar(tab, onClose = { ui.findOpen = false; tab.session.finder.clear() })
        ui.editing -> AddressEditor(c, ui, tab)
        else -> TopBar(c, ui, tab, tabCount)
    }
}

// ---------------------------------------------------------------------------------- engine view

/** One tab's page: the engine's view, with the new tab page over it when there is no page. */
@Composable
internal fun TabPage(c: Container, ui: UiState, tab: BrowserTab, primary: Boolean) {
    val overNewTab by tab.ntpOverlay.collectAsState()
    // Read here, so the new tab page goes as soon as the tab gets an address (and comes back when it loses it).
    val url by tab.url.collectAsState()
    PageView(tab, ui, primary)
    if (overNewTab || url.isBlank() || url == "about:blank") {
        if (tab.private) PrivateNewTabPage(c) else NewTabPage(c, ui, tab)
    }
}

/**
 * The engine's view of [tab]'s page. [primary]: the view of the tab on screen (the one the bar belongs to), used
 * for its picture in the Tabs screen. [floating]: drawn in a way that can sit on top of another page and have
 * rounded corners (the floating tab); slightly more work for the phone, so only there.
 */
@Composable
internal fun PageView(tab: BrowserTab, ui: UiState, primary: Boolean, floating: Boolean = false) {
    val url by tab.url.collectAsState()
    val asleep by tab.asleep.collectAsState()
    val opened by tab.opened.collectAsState()
    val key = "$url $asleep $opened"
    AndroidView(
        factory = { ctx ->
            GeckoView(ctx).also {
                if (floating) it.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                // No white flash before a page draws its first frame.
                it.coverUntilFirstPaint(Space.Ground.toArgb())
                // The tab, not the view, owns the page: never hand the session back to Android to restore.
                it.isSaveEnabled = false
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            // Re-runs when the tab, its address, its sleep state or its session changes (a session can open later).
            @Suppress("UNUSED_VARIABLE") val k = key
            if (primary) ui.geckoView = view
            val s = tab.session
            if (s.isOpen) Displays.showWhenSettled(s, view)
        },
        // The screen is going away (Android can throw it away while you're in another app): let the page go,
        // so the next screen can show it.
        onRelease = { view ->
            Displays.releaseView(view)
            if (ui.geckoView === view) ui.geckoView = null
        },
    )
}

/** Captures the current page for the tab switcher. */
fun captureThumbnail(ui: UiState, tab: BrowserTab?, then: () -> Unit) {
    val view = ui.geckoView
    if (tab == null || view == null || tab.isNewTabPage || view.session != tab.session) { then(); return }
    view.capturePixels().accept({ bmp ->
        if (bmp != null) {
            val w = 360
            val h = (bmp.height * w / bmp.width.coerceAtLeast(1)).coerceAtMost(720)
            tab.thumbnail.value = Bitmap.createScaledBitmap(bmp, w, h.coerceAtLeast(1), true).asImageBitmap()
            bmp.recycle()
        }
        then()
    }, { then() })
}

// ---------------------------------------------------------------------------------- top bar

@Composable
private fun TopBar(c: Container, ui: UiState, tab: BrowserTab, tabCount: Int) {
    val pageUrl by tab.url.collectAsState()
    val overNewTab by tab.ntpOverlay.collectAsState()
    // On the new tab page Back led to, the bar is empty, as on any new tab page.
    val url = if (overNewTab) "" else pageUrl
    val pageLoading by tab.loading.collectAsState()
    val loading = pageLoading && !overNewTab
    val progress by tab.progress.collectAsState()
    val secure by tab.secure.collectAsState()
    val private = tab.private
    // On a narrow phone (under 360dp) the buttons give the address a little more room.
    val button = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360) 40.dp else 44.dp
    // Swiping the address sideways moves to the next or previous tab, like Chrome: the address slides with
    // the finger, and the next tab's address slides in from the other side.
    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun switchTo(step: Int, width: Float) {
        val next = c.tabs.neighbour(tab, step)
        scope.launch {
            if (next == null) {
                slide.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f))
                return@launch
            }
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            slide.animateTo(-step * width, tween(120))
            captureThumbnail(ui, tab) { }
            c.tabs.select(next.id)
            slide.snapTo(step * width)
            slide.animateTo(0f, tween(200))
        }
    }
    val onHome = tab.isNewTabPage || overNewTab
    BarStrip(private, sky = if (onHome) homeSky(c, private) else null) {
        val hasPage = !tab.isNewTabPage
        IconButton(Icons.Home, "Home", { tab.goHome() }, size = button, enabled = hasPage)
        var pillWidth by remember { mutableStateOf(1f) }
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .onSizeChanged { pillWidth = it.width.toFloat().coerceAtLeast(1f) }
                .clip(CircleShape)
                .background(
                    when {
                        onHome && private -> Color(0x1F9A8CFF)
                        onHome -> Color(0x10E9ECF3)
                        private -> Space.NebulaBar
                        else -> Space.Surface2
                    },
                )
                .pointerInput(tab.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = slide.value
                            when {
                                v < -pillWidth * 0.25f -> switchTo(+1, pillWidth)
                                v > pillWidth * 0.25f -> switchTo(-1, pillWidth)
                                else -> scope.launch { slide.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.7f)) }
                            }
                        },
                        onDragCancel = { scope.launch { slide.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        // Past the first or last tab the address only gives a little, like a rubber band.
                        val end = c.tabs.neighbour(tab, if (slide.value + dx < 0) +1 else -1) == null
                        scope.launch { slide.snapTo(slide.value + if (end) dx * 0.3f else dx) }
                    }
                }
                .clickable {
                    // Like Chrome: an empty field, with the page's address offered underneath to copy, share or edit.
                    ui.editText = ""
                    ui.editing = true
                }
                .semantics {
                    contentDescription = "Address ${UrlInput.display(url)}. Tap to search or edit"
                    customActions = listOf(
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Next tab") { c.tabs.neighbour(tab, +1)?.let { c.tabs.select(it.id) } != null },
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Previous tab") { c.tabs.neighbour(tab, -1)?.let { c.tabs.select(it.id) } != null },
                    )
                },
        ) {
            Row(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        translationX = slide.value
                        alpha = (1f - kotlin.math.abs(slide.value) / pillWidth).coerceIn(0.2f, 1f)
                    }
                    .padding(horizontal = if (button < 44.dp) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = UrlInput.searchTerms(url, c.settings.current.searchEngine) ?: UrlInput.display(url)
                when {
                    private && shown.isEmpty() -> Icon(Icons.Eclipse, null, size = 16.dp, tint = Space.NebulaText)
                    shown.isEmpty() -> Icon(Icons.Search, null, size = 16.dp, tint = Space.Text2)
                    secure == false && url.startsWith("http:") -> Icon(Icons.LockOpen, null, size = 15.dp, tint = Space.Solar)
                    url.startsWith("https") -> Icon(Icons.Lock, null, size = 15.dp, tint = Space.Text2, modifier = Modifier.clickable { ui.sheet = Sheet.SiteInfo })
                    else -> Icon(Icons.Globe, null, size = 15.dp, tint = Space.Text2)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    shown.ifEmpty { if (private) "Search privately" else "Search or type address" },
                    color = if (shown.isEmpty()) Space.Text2 else Space.Text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // The page is in another language (or already translated): a small way into Translate.
                val offer by tab.offerTranslate.collectAsState()
                val translation by tab.translation.collectAsState()
                val translated = translation?.requestedTranslationPair != null
                val roomy = with(androidx.compose.ui.platform.LocalDensity.current) { pillWidth.toDp() } >= 160.dp
                if ((offer || translated) && !onHome && roomy) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = "Translate this page") { ui.sheet = Sheet.Translate }
                            .semantics { contentDescription = if (translated) "Translated, tap for options" else "Translate this page" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Translate, null, size = 17.dp, tint = if (translated) Raven.accent else Space.Text2) }
                }
            }
            Moonlight(loading, progress / 100f, Modifier.fillMaxSize())
        }
        // The home screen has nothing to reload: the field gets the room instead.
        if (!onHome) IconButton(
            if (loading) Icons.Close else Icons.Reload, if (loading) "Stop loading" else "Reload",
            { if (loading) tab.session.stop() else tab.session.reload() },
            size = button, iconSize = 20.dp, enabled = hasPage,
        )
        // Hold for a quick new tab or private tab; tap for all of them.
        var quick by remember { mutableStateOf(false) }
        Box {
            TabsButton(tabCount, private, button, onLongClick = { quick = true }) {
                captureThumbnail(ui, tab) { ui.go(Screen.Tabs) }
            }
            val context = LocalContext.current
            androidx.compose.material3.DropdownMenu(
                quick, { quick = false },
                containerColor = Space.Surface2, shape = RoundedCornerShape(20.dp),
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("New tab") }, leadingIcon = { Icon(Icons.Plus, null, size = 18.dp) },
                    onClick = { quick = false; c.tabs.newTab(select = true) },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("New private tab") }, leadingIcon = { Icon(Icons.Eclipse, null, size = 18.dp, tint = Space.Nebula) },
                    onClick = {
                        quick = false
                        val open = { c.tabs.newTab(private = true); Unit }
                        (context as? app.raven.browser.MainActivity)?.unlockPrivate(open) ?: open()
                    },
                )
            }
        }
        IconButton(Icons.Menu, "Menu", { ui.sheet = Sheet.Menu }, size = button)
    }
}

/** The strip the address bar floats in. Over the home screen ([sky]) the bar is glass and the wallpaper shows through. */
@Composable
fun BarStrip(private: Boolean, sky: Modifier? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .then(sky ?: Modifier.background(if (private) Space.NebulaGround else Space.Strip))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .then(
                    when {
                        sky != null && private -> Modifier.glass(CircleShape, fill = Color(0x990E0A1C), edge = Space.Nebula.copy(alpha = 0.26f), top = Space.Nebula.copy(alpha = 0.42f))
                        sky != null -> Modifier.glass(CircleShape)
                        private -> Modifier.clip(CircleShape).background(Space.NebulaBar).border(1.dp, Space.Nebula.copy(alpha = 0.22f), CircleShape)
                        else -> Modifier.clip(CircleShape).background(Space.Bar).border(1.dp, Space.Hairline, CircleShape)
                    },
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/** The loading line: moonlight spreading out from the middle of the address field as the page comes in. */
@Composable
internal fun Moonlight(loading: Boolean, progress: Float, modifier: Modifier) {
    val accent = Raven.accent
    val shown by animateFloatAsState(if (loading) progress.coerceAtLeast(0.08f) else 1f, tween(if (Raven.reduceMotion) 0 else 300), label = "moonlight")
    val alpha by animateFloatAsState(if (loading) 1f else 0f, tween(450), label = "moonlightAlpha")
    if (alpha <= 0.01f) return
    Canvas(modifier) {
        val y = size.height - 1.5.dp.toPx()
        val half = size.width * 0.42f * shown
        val mid = size.width / 2f
        drawLine(
            Brush.horizontalGradient(
                0f to accent.copy(alpha = 0f), 0.5f to accent.copy(alpha = alpha), 1f to accent.copy(alpha = 0f),
                startX = mid - half, endX = mid + half,
            ),
            Offset(mid - half, y), Offset(mid + half, y), strokeWidth = 2.dp.toPx(),
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun TabsButton(count: Int, private: Boolean, size: androidx.compose.ui.unit.Dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val edge = if (private) Space.Nebula else Raven.accent
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "New tab or private tab")
            .semantics { contentDescription = "$count open tabs" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(25.dp).border(1.5.dp, edge, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (count > 99) "∞" else "$count", color = Space.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------------------- address editing

@Composable
private fun AddressEditor(c: Container, ui: UiState, tab: BrowserTab) {
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue(ui.editText, TextRange(0, ui.editText.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(ui.editFill) {
        if (ui.editFill > 0) {
            value = TextFieldValue(ui.editText, TextRange(ui.editText.length))
            focus.requestFocus()
        }
    }
    BarStrip(tab.private) {
        IconButton(Icons.Back, "Cancel", { ui.editing = false })
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(CircleShape)
                .background(if (tab.private) Space.NebulaBar else Space.Surface2)
                .border(1.5.dp, if (tab.private) Space.Nebula.copy(alpha = 0.6f) else Raven.accent.copy(alpha = 0.6f), CircleShape)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it; ui.editText = it.text },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Space.Text),
                cursorBrush = SolidColor(Raven.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { go(c, ui, tab, value.text) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) Text(if (tab.private) "Search privately" else "Search or type address", color = Space.Text2, style = MaterialTheme.typography.bodyMedium)
                        inner()
                    }
                },
            )
            if (value.text.isNotEmpty()) IconButton(Icons.Close, "Clear", { value = TextFieldValue(""); ui.editText = "" }, size = 40.dp, iconSize = 16.dp, tint = Space.Text2)
        }
    }
}

fun go(c: Container, ui: UiState, tab: BrowserTab, text: String) {
    val url = UrlInput.resolve(text, c.settings.current.searchEngine)
    ui.editing = false
    if (url.isNotEmpty()) c.tabs.load(tab, url)
}

@Composable
private fun Suggestions(c: Container, ui: UiState, tab: BrowserTab) {
    val text = ui.editText
    var results by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var marks by remember { mutableStateOf<List<app.raven.browser.data.Bookmark>>(emptyList()) }
    LaunchedEffect(text) {
        // Bookmarks first (private tabs may read them, never history); then history, without repeating them.
        marks = c.db.suggestBookmarks(text)
        val saved = marks.map { it.url }.toSet()
        results = if (tab.private) emptyList() else c.db.suggest(text).filter { it.url !in saved }
    }
    val engine = c.settings.current.searchEngine
    // While you type, Raven connects to where Go would take you (the site, or the search engine) and to the top
    // suggestion, once you pause: the page then starts at once. Never from a private tab, which keeps its own.
    LaunchedEffect(text, marks, results) {
        if (tab.private || text.isBlank()) return@LaunchedEffect
        delay(400)
        c.engine.warmUp(UrlInput.resolve(text, engine))
        (marks.firstOrNull()?.url ?: results.firstOrNull()?.url)?.let { c.engine.warmUp(it) }
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(if (tab.private) Space.NebulaGround else Raven.ground)
            .padding(horizontal = 12.dp),
    ) {
        val pageUrl = tab.url.value
        if (text.isEmpty() && !tab.isNewTabPage) item { CurrentPageRow(ui, tab, pageUrl, UrlInput.searchTerms(pageUrl, engine)) }
        if (text.isNotBlank()) {
            val resolved = UrlInput.resolve(text, engine)
            val isSearch = resolved.startsWith(engine.template.substringBefore("%s"))
            item {
                SuggestionRow(
                    icon = if (isSearch) Icons.Search else Icons.Globe,
                    title = if (isSearch) text else UrlInput.display(resolved).ifEmpty { resolved },
                    detail = if (isSearch) "Search with ${engine.label}" else "Go to address",
                ) { go(c, ui, tab, text) }
            }
            if (!isSearch) item {
                SuggestionRow(Icons.Search, text, "Search with ${engine.label}") {
                    ui.editing = false
                    c.tabs.load(tab, engine.template.replace("%s", android.net.Uri.encode(text)))
                }
            }
        }
        items(marks, key = { "b" + it.id }) { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { ui.editing = false; c.tabs.load(tab, b.url) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    SitePlanet(b.host, 36.dp)
                    Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(Raven.accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.BookmarkFilled, "Bookmark", size = 9.dp, tint = Space.OnAccent)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(b.url.removePrefix("https://").removePrefix("www."), style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(Icons.ArrowRight, "Edit this address", { ui.editText = b.url }, size = 44.dp, iconSize = 16.dp, tint = Space.Text2)
            }
        }
        items(results, key = { it.id }) { v ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { ui.editing = false; c.tabs.load(tab, v.url) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SitePlanet(v.host, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title.ifBlank { v.host }, style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(v.url.removePrefix("https://").removePrefix("www."), style = MaterialTheme.typography.bodySmall, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(Icons.ArrowRight, "Edit this address", {
                    ui.editText = v.url
                }, size = 44.dp, iconSize = 16.dp, tint = Space.Text2, modifier = Modifier)
            }
        }
    }
}

/** The page you're on, as Chrome shows it under an empty address field: share, copy, or edit its address. */
@Composable
private fun CurrentPageRow(ui: UiState, tab: BrowserTab, url: String, searchTerms: String?) {
    val context = LocalContext.current
    fun edit() {
        // A results page edits its search words; any other page its full address.
        ui.editText = searchTerms ?: url
        ui.editFill++
    }
    Row(
        Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (tab.private) Space.NebulaBar else Space.Surface2)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (searchTerms != null) Icons.Search else Icons.Globe, null, size = 18.dp, tint = Space.Text2)
        Spacer(Modifier.width(12.dp))
        Text(
            searchTerms ?: url, style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClickLabel = "Edit") { edit() }.padding(vertical = 14.dp),
        )
        IconButton(Icons.Share, "Share link", {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, url)
            runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
        }, size = 44.dp, iconSize = 19.dp, tint = Space.Text2)
        IconButton(Icons.Copy, "Copy link", {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", url))
            // Android 13+ shows its own "copied" message.
            if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
        }, size = 44.dp, iconSize = 19.dp, tint = Space.Text2)
        IconButton(Icons.Edit, "Edit link", { edit() }, size = 44.dp, iconSize = 19.dp, tint = Space.Text2)
    }
}

@Composable
private fun SuggestionRow(icon: app.raven.browser.ui.theme.RavenIcon, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Space.Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, size = 18.dp, tint = Space.Text)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Space.Text2)
        }
    }
}

// ---------------------------------------------------------------------------------- find in page

@Composable
private fun FindBar(tab: BrowserTab, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<GeckoSession.FinderResult?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun find(query: String?, flags: Int) {
        if (!tab.session.isOpen) return
        tab.session.finder.find(query, flags).accept({ r -> scope.launch { result = r } }, { })
    }
    BarStrip(tab.private) {
        IconButton(Icons.Close, "Close find in page", onClose)
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(CircleShape)
                .background(Space.Surface2)
                .border(1.5.dp, Raven.accent, CircleShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; if (it.isEmpty()) { tab.session.finder.clear(); result = null } else find(it, 0) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Space.Text),
                cursorBrush = SolidColor(Raven.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { find(null, 0) }),
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Find in page" },
            )
            val r = result
            if (r != null && text.isNotEmpty()) {
                Text(if (r.found) "${r.current} of ${r.total}" else "0 of 0", style = MaterialTheme.typography.labelMedium, color = Space.Text2)
            }
        }
        IconButton(Icons.ChevronUp, "Previous match", { find(null, GeckoSession.FINDER_FIND_BACKWARDS) }, size = 44.dp)
        IconButton(Icons.ChevronDown, "Next match", { find(null, 0) }, size = 44.dp)
    }
}
