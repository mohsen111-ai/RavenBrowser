package app.raven.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.raven.browser.Container
import app.raven.browser.engine.BrowserTab
import app.raven.browser.engine.TabManager.SplitSound
import app.raven.browser.engine.UrlInput
import app.raven.browser.ui.Screen
import app.raven.browser.ui.Sheet
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space
import kotlin.math.roundToInt

/** Two tabs sharing the screen, each with its own small bar; the half you touched last is the active one. */
@Composable
internal fun SplitArea(c: Container, ui: UiState, top: BrowserTab, bottom: BrowserTab, selectedId: String, barsHidden: Boolean = false, peek: Boolean = false) {
    val sound by c.tabs.splitSound.collectAsState()
    val onBrowser = ui.screen == Screen.Browser
    // A half whose video went fullscreen fills the whole screen until it comes back.
    val topFull by top.fullscreen.collectAsState()
    val bottomFull by bottom.fullscreen.collectAsState()
    val full = when {
        topFull -> SplitFull.FIRST
        bottomFull -> SplitFull.SECOND
        else -> SplitFull.NONE
    }
    // The half that isn't the selected tab stays awake and drawn too (the selected one is looked after in RavenRoot).
    if (top.id != selectedId) KeepShown(top, onBrowser)
    if (bottom.id != selectedId) KeepShown(bottom, onBrowser)
    SplitLayout(
        ratio = ui.splitRatio,
        onRatio = { ui.splitRatio = it },
        onEnd = { keepFirst ->
            ui.splitRatio = 0.5f
            c.tabs.endSplit(if (keepFirst) top.id else bottom.id)
        },
        full = full,
        first = { sideBySide -> Half(c, ui, top, first = true, sideBySide, top.id == selectedId, sound, full != SplitFull.NONE, barsHidden, peek) },
        second = { sideBySide -> Half(c, ui, bottom, first = false, sideBySide, bottom.id == selectedId, sound, full != SplitFull.NONE, barsHidden, peek) },
    )
}

/** Which half of split screen fills the screen with its fullscreen video, if any. */
enum class SplitFull { NONE, FIRST, SECOND }

/** Keeps a tab that's on screen but isn't the selected one running and drawing (or playing, wherever it is). */
@Composable
internal fun KeepShown(tab: BrowserTab, shown: Boolean) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val playing by tab.playing.collectAsState()
    val opened by tab.opened.collectAsState()
    LaunchedEffect(shown, started, playing, opened) {
        if (tab.session.isOpen) tab.session.setActive((shown && started) || playing)
    }
}

/**
 * The two halves and the handle between them: one above the other with the phone upright, side by side when it's
 * turned. Dragging the handle resizes them; dragging it nearly to the edge ends split screen, keeping the half
 * that's left.
 */
@Composable
fun SplitLayout(
    ratio: Float,
    onRatio: (Float) -> Unit,
    onEnd: (keepFirst: Boolean) -> Unit,
    full: SplitFull = SplitFull.NONE,
    first: @Composable (sideBySide: Boolean) -> Unit,
    second: @Composable (sideBySide: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideBySide = maxWidth > maxHeight
        val totalPx = with(LocalDensity.current) { (if (sideBySide) maxWidth else maxHeight).toPx() }.coerceAtLeast(1f)
        // Follows the finger while the handle moves; settles into [ratio] when it's let go.
        var live by remember { mutableFloatStateOf(ratio) }
        LaunchedEffect(ratio) { live = ratio }
        val r = live.coerceIn(0.06f, 0.94f)
        val handle: @Composable () -> Unit = {
            SplitHandle(
                sideBySide,
                onDrag = { d -> live = (live + d / totalPx).coerceIn(0.04f, 0.96f) },
                onDragEnd = {
                    when {
                        live < 0.14f -> onEnd(false)
                        live > 0.86f -> onEnd(true)
                        else -> {
                            live = live.coerceIn(0.2f, 0.8f)
                            onRatio(live)
                        }
                    }
                },
            )
        }
        // One layout for both ways round, with the halves always in the same places: turning the phone (as a wide
        // video in fullscreen does) or a half going fullscreen must never rebuild a page. With a half fullscreen, it
        // takes all the room and the other waits at no size.
        val handlePx = with(LocalDensity.current) { 14.dp.roundToPx() }
        Layout(
            content = {
                Box { first(sideBySide) }
                Box { if (full == SplitFull.NONE) handle() }
                Box { second(sideBySide) }
            },
            modifier = Modifier.fillMaxSize(),
        ) { parts, constraints ->
            val total = if (sideBySide) constraints.maxWidth else constraints.maxHeight
            val across = if (sideBySide) constraints.maxHeight else constraints.maxWidth
            val gap = if (full == SplitFull.NONE) handlePx else 0
            val a = when (full) {
                SplitFull.NONE -> ((total - gap) * r).roundToInt()
                SplitFull.FIRST -> total
                SplitFull.SECOND -> 0
            }
            val b = (total - gap - a).coerceAtLeast(0)
            fun sized(along: Int) = if (sideBySide) Constraints.fixed(along, across) else Constraints.fixed(across, along)
            val one = parts[0].measure(sized(a))
            val bar = parts[1].measure(sized(gap))
            val two = parts[2].measure(sized(b))
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (sideBySide) {
                    one.place(0, 0); bar.place(a, 0); two.place(a + gap, 0)
                } else {
                    one.place(0, 0); bar.place(0, a); two.place(0, a + gap)
                }
            }
        }
    }
}

@Composable
private fun SplitHandle(sideBySide: Boolean, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    Box(
        (if (sideBySide) Modifier.width(14.dp).fillMaxHeight() else Modifier.height(14.dp).fillMaxWidth())
            .background(Raven.ground)
            .pointerInput(sideBySide) {
                detectDragGestures(onDragEnd = { end() }, onDragCancel = { end() }) { change, amount ->
                    change.consume()
                    drag(if (sideBySide) amount.x else amount.y)
                }
            }
            .semantics { contentDescription = "Split screen handle: drag to resize, or to the edge to end split screen" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(if (sideBySide) 5.dp else 44.dp, if (sideBySide) 44.dp else 5.dp).clip(CircleShape).background(Color(0xFF6A7286)))
    }
}

/**
 * One half: its own small bar (address, sound, reload, tabs, menu) over its page, and a thin glow when it's the
 * active half. Touching it anywhere makes it the active half. The bar steps aside while you scroll down the page,
 * and comes back when you scroll up.
 */
@Composable
private fun Half(
    c: Container, ui: UiState, tab: BrowserTab, first: Boolean, sideBySide: Boolean, active: Boolean, sound: SplitSound,
    fullscreen: Boolean, barsHidden: Boolean, peek: Boolean,
) {
    val muted by tab.muted.collectAsState()
    // Read here, so the half's name follows its page.
    val title by tab.title.collectAsState()
    val label = title.let { tab.displayTitle }
    val isActive by rememberUpdatedState(active)
    val scrolledAway by tab.scrolledAway.collectAsState()
    val name = when {
        sideBySide && first -> "Left half"
        sideBySide -> "Right half"
        first -> "Top half"
        else -> "Bottom half"
    }
    Box(
        Modifier.fillMaxSize()
            // Watches touches on their way to the page (without taking them): the first touch makes this the active half.
            .pointerInput(tab.id) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press && !isActive) c.tabs.select(tab.id)
                    }
                }
            }
            .semantics { contentDescription = "$name: $label" },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!scrolledAway && !fullscreen && !barsHidden) HalfBar(c, ui, tab, muted, sound, sideBySide)
            Box(Modifier.weight(1f).fillMaxWidth()) { TabPage(c, ui, tab, primary = active) }
        }
        // Full screen for pages: the bar comes over the page for a moment after a swipe down from the top.
        if (barsHidden && peek && !fullscreen) HalfBar(c, ui, tab, muted, sound, sideBySide)
        if (!fullscreen) HalfChrome(active)
    }
}

/** A half's own bar, wired to its tab. Anything in it acts on this half (it becomes the active one first). */
@Composable
private fun HalfBar(c: Container, ui: UiState, tab: BrowserTab, muted: Boolean, sound: SplitSound, sideBySide: Boolean) {
    val pageUrl by tab.url.collectAsState()
    val overNewTab by tab.ntpOverlay.collectAsState()
    val url = if (overNewTab) "" else pageUrl
    val pageLoading by tab.loading.collectAsState()
    val progress by tab.progress.collectAsState()
    val secure by tab.secure.collectAsState()
    val tabCount = c.tabs.tabs.collectAsState().value.size
    val engine = c.settings.current.searchEngine
    fun mine() = c.tabs.select(tab.id)
    HalfBarContent(
        private = tab.private,
        address = UrlInput.searchTerms(url, engine) ?: UrlInput.display(url),
        url = url,
        secure = secure,
        loading = pageLoading && !overNewTab,
        progress = progress / 100f,
        hasPage = !tab.isNewTabPage,
        muted = muted,
        sound = sound,
        sideBySide = sideBySide,
        tabCount = tabCount,
        ring = profileColor(c, tab),
        onAddress = { mine(); ui.editText = ""; ui.editing = true },
        onSiteInfo = { mine(); ui.sheet = Sheet.SiteInfo },
        onSound = { c.tabs.setSplitSound(it) },
        onReload = { if (pageLoading) tab.session.stop() else tab.session.reload() },
        onTabs = { mine(); captureThumbnail(ui, tab) { ui.go(Screen.Tabs) } },
        onMenu = { mine(); ui.sheet = Sheet.Menu },
    )
}

/** What a half's bar shows: the page's address (tap to search or type one), its sound, reload, tabs and the menu. */
@Composable
fun HalfBarContent(
    private: Boolean,
    address: String,
    url: String,
    secure: Boolean?,
    loading: Boolean,
    progress: Float,
    hasPage: Boolean,
    muted: Boolean,
    sound: SplitSound,
    sideBySide: Boolean,
    tabCount: Int,
    ring: Color? = null,
    onAddress: () -> Unit,
    onSiteInfo: () -> Unit,
    onSound: (SplitSound) -> Unit,
    onReload: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(if (private) Space.NebulaGround else Space.Strip).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.weight(1f).height(36.dp).clip(CircleShape)
                .background(if (private) Space.NebulaBar else Space.Surface2)
                .clickable(onClickLabel = "Search or type an address in this half", onClick = onAddress)
                .semantics { contentDescription = "Address ${address.ifEmpty { "empty" }}. Tap to search or edit" },
        ) {
            Row(Modifier.fillMaxSize().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    private && address.isEmpty() -> Icons.Eclipse
                    address.isEmpty() -> Icons.Search
                    secure == false && url.startsWith("http:") -> Icons.LockOpen
                    url.startsWith("https") -> Icons.Lock
                    else -> Icons.Globe
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .then(if (hasPage) Modifier.clickable(onClickLabel = "About this site", onClick = onSiteInfo) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, size = 14.dp, tint = if (icon == Icons.LockOpen) Space.Solar else if (private) Space.NebulaText else Space.Text2)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    address.ifEmpty { if (private) "Search privately" else "Search or type address" },
                    color = if (address.isEmpty()) Space.Text2 else Space.Text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Moonlight(loading, progress, Modifier.fillMaxSize())
        }
        SoundButton(muted, sound, sideBySide, onSound)
        if (hasPage) IconButton(if (loading) Icons.Close else Icons.Reload, if (loading) "Stop loading" else "Reload", onReload, size = 38.dp, iconSize = 17.dp)
        TabsButton(tabCount, private, 38.dp, ring = ring, onClick = onTabs)
        IconButton(Icons.Menu, "Menu", onMenu, size = 38.dp, iconSize = 18.dp)
    }
}

/** The speaker in a half's bar: shows whether this half is heard, and picks whose sound you hear. */
@Composable
private fun SoundButton(muted: Boolean, sound: SplitSound, sideBySide: Boolean, onSound: (SplitSound) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .clickable(onClickLabel = "Choose whose sound you hear") { choosing = true }
                .semantics { contentDescription = if (muted) "Sound: muted" else "Sound: on" },
            contentAlignment = Alignment.Center,
        ) { Icon(if (muted) Icons.Mute else Icons.Sound, null, size = 17.dp, tint = if (muted) Space.Text3 else Space.Text) }
        DropdownMenu(choosing, { choosing = false }, containerColor = Space.Surface2, shape = RoundedCornerShape(20.dp)) {
            soundChoices(sideBySide).forEach { (s, label, icon) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (s == sound) FontWeight.SemiBold else FontWeight.Normal) },
                    leadingIcon = { Icon(icon, null, size = 18.dp, tint = Space.Text2) },
                    trailingIcon = { if (s == sound) Icon(Icons.Check, "Chosen", size = 16.dp) },
                    onClick = { choosing = false; onSound(s) },
                )
            }
        }
    }
}

/** What Raven draws over a half: a thin moonlit edge around the active one. */
@Composable
fun HalfChrome(active: Boolean) {
    if (active) Box(Modifier.fillMaxSize().border(2.dp, Raven.accent.copy(alpha = 0.6f)))
}

private fun soundChoices(sideBySide: Boolean): List<Triple<SplitSound, String, RavenIcon>> = listOf(
    Triple(SplitSound.BOTH, "Both", Icons.Sound),
    Triple(SplitSound.TOP, if (sideBySide) "Left only" else "Top only", if (sideBySide) Icons.SplitLeft else Icons.SplitTop),
    Triple(SplitSound.BOTTOM, if (sideBySide) "Right only" else "Bottom only", if (sideBySide) Icons.SplitRight else Icons.SplitBottom),
)

/** Split screen: pick the tab that goes below the one on screen (or a new tab). */
@Composable
fun SplitPickerSheet(c: Container, ui: UiState, tab: BrowserTab) {
    val tabs by c.tabs.tabs.collectAsState()
    val floating by c.tabs.floatingId.collectAsState()
    val others = tabs.filter { it.id != tab.id && it.private == tab.private && it.id != floating }.sortedByDescending { it.lastActive }
    fun close() { ui.sheet = null }
    RavenSheet(::close) {
        Text("Split screen", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            "This tab goes on top. Pick the one to go below; both can play at once.",
            style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp),
        )
        PickRow(Modifier.semantics { contentDescription = "Split with a new tab" }, onClick = {
            close()
            val fresh = c.tabs.newTab(private = tab.private, select = false)
            c.tabs.split(fresh.id)
        }) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Space.Surface2), contentAlignment = Alignment.Center) { Icon(Icons.Plus, null, size = 18.dp) }
            Text("New tab", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 14.dp))
        }
        others.forEach { o ->
            PickRow(Modifier.semantics { contentDescription = "Split with ${o.displayTitle}" }, onClick = { close(); c.tabs.split(o.id) }) {
                SitePlanet(o.host.ifBlank { o.displayTitle }.take(1).uppercase(), 40.dp, private = o.private)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(o.displayTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (o.host.isNotBlank()) Text(o.host, style = MaterialTheme.typography.bodySmall, color = Space.Text3, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PickRow(modifier: Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).height(60.dp).clip(RoundedCornerShape(30.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
