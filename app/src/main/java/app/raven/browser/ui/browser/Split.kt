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
import app.raven.browser.ui.Screen
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space

/** Two tabs sharing the screen. The bar belongs to the half you touched last; a speaker on each picks the sound. */
@Composable
internal fun SplitArea(c: Container, ui: UiState, top: BrowserTab, bottom: BrowserTab, selectedId: String) {
    val sound by c.tabs.splitSound.collectAsState()
    val onBrowser = ui.screen == Screen.Browser
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
        first = { sideBySide -> Half(c, ui, top, first = true, sideBySide, top.id == selectedId, sound) },
        second = { sideBySide -> Half(c, ui, bottom, first = false, sideBySide, bottom.id == selectedId, sound) },
    )
}

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
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(r).fillMaxHeight()) { first(true) }
                handle()
                Box(Modifier.weight(1f - r).fillMaxHeight()) { second(true) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(r).fillMaxWidth()) { first(false) }
                handle()
                Box(Modifier.weight(1f - r).fillMaxWidth()) { second(false) }
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

/** One half: the tab's page, a thin glow when the bar belongs to it, and its speaker. Touching it makes it the active half. */
@Composable
private fun Half(c: Container, ui: UiState, tab: BrowserTab, first: Boolean, sideBySide: Boolean, active: Boolean, sound: SplitSound) {
    val muted by tab.muted.collectAsState()
    // Read here, so the half's name follows its page.
    val title by tab.title.collectAsState()
    val label = title.let { tab.displayTitle }
    val isActive by rememberUpdatedState(active)
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
        TabPage(c, ui, tab, primary = active)
        HalfChrome(active, muted, sound, sideBySide, onSound = { c.tabs.setSplitSound(it) })
    }
}

/** What Raven draws over a half: the active glow, the speaker and its choice, and "Muted" when it's quiet. */
@Composable
fun HalfChrome(active: Boolean, muted: Boolean, sound: SplitSound, sideBySide: Boolean, onSound: (SplitSound) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (active) Box(Modifier.matchParentSize().border(2.dp, Raven.accent.copy(alpha = 0.6f)))
        var choosing by remember { mutableStateOf(false) }
        Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (muted) {
                Text(
                    "Muted",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Space.Text,
                    modifier = Modifier.clip(CircleShape).background(Color(0xB30A0E18)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Box {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(0xB30A0E18))
                        .border(1.dp, Color(0x38C7CCD8), CircleShape)
                        .clickable(onClickLabel = "Choose whose sound you hear") { choosing = true }
                        .semantics { contentDescription = if (muted) "Sound: muted" else "Sound: on" },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (muted) Icons.Mute else Icons.Sound, null, size = 18.dp, tint = if (muted) Space.Text2 else Space.Text) }
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
    }
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
