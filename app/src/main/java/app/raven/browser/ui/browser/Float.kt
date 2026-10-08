package app.raven.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.Container
import app.raven.browser.ui.Screen
import app.raven.browser.ui.UiState
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The floating tab: one tab in a small window over everything else in Raven. Tall for a page, wide in the video's
 * shape for "Video only". Moved by its top bar (or anywhere on a video), resized from its bottom corners, parked on
 * the edge as a small icon (its video paused) when pushed against the side.
 */
@Composable
fun FloatingTab(c: Container, ui: UiState) {
    val id by c.tabs.floatingId.collectAsState()
    val tabs by c.tabs.tabs.collectAsState()
    val tab = tabs.firstOrNull { it.id == id } ?: return
    val parked by c.tabs.floatParked.collectAsState()
    val fullscreen by tab.fullscreen.collectAsState()
    val muted by tab.muted.collectAsState()
    val url by tab.url.collectAsState()
    val title by tab.title.collectAsState()
    KeepShown(tab, shown = !parked)
    // The page's own fullscreen fills the whole screen (BrowserScreen shows it); the window comes back afterwards.
    if (fullscreen) return

    // A new floating tab, or a new page in it: back to the whole page.
    LaunchedEffect(tab.id, url) { ui.floatVideoOnly = false }
    // The page's answer to "Video only".
    DisposableEffect(tab.id) {
        c.engine.helper.onVideoOnly = { session, ok, w, h ->
            if (session === tab.session) {
                if (ok) {
                    if (w > 0 && h > 0) ui.floatAspect = (w.toFloat() / h).coerceIn(0.6f, 2.4f)
                    ui.floatVideoOnly = true
                } else {
                    c.engine.messages.tryEmit("There's no video on this page")
                }
            }
        }
        onDispose { c.engine.helper.onVideoOnly = null }
    }

    val wide = ui.floatVideoOnly
    val aspect = ui.floatAspect
    // Reads the title, so the window's name follows its page.
    val label = title.let { tab.displayTitle }
    val letter = tab.host.ifBlank { label }.take(1).uppercase()

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val density = LocalDensity.current
        val dp = density.density
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        // Usual widths: about half the screen for a page, two thirds for a video; [UiState.floatScale] from the corners.
        val minW = 168f * dp
        val w = ((if (wide) areaW * 0.66f else areaW * 0.46f) * ui.floatScale).coerceIn(minW, areaW * 0.96f)
        val h = (if (wide) w / aspect else w * 5f / 3f).coerceAtMost(areaH * 0.85f)
        val aw by animateFloatAsState(w, spring(stiffness = 500f), label = "floatW")
        val ah by animateFloatAsState(h, spring(stiffness = 500f), label = "floatH")
        var dragging by remember { mutableStateOf(false) }
        val defaultX = areaW - aw - 12f * dp
        val defaultY = 72f * dp
        val rawX = if (ui.floatX.isNaN()) defaultX else ui.floatX
        val rawY = if (ui.floatY.isNaN()) defaultY else ui.floatY
        // While a finger moves it, it may go past the edges; otherwise it stays on screen.
        val x = if (dragging) rawX else rawX.coerceIn(0f, (areaW - aw).coerceAtLeast(0f))
        val y = if (dragging) rawY else rawY.coerceIn(0f, (areaH - ah).coerceAtLeast(0f))

        fun move(d: Offset) {
            dragging = true
            ui.floatX = (if (ui.floatX.isNaN()) defaultX else ui.floatX) + d.x
            ui.floatY = ((if (ui.floatY.isNaN()) defaultY else ui.floatY) + d.y).coerceIn(-ah * 0.5f, areaH - ah * 0.5f)
        }
        fun moveEnd() {
            dragging = false
            val fx = ui.floatX
            val leftOut = -fx
            val rightOut = fx + aw - areaW
            // Pushed more than a third past the side: it parks there as an icon.
            if (leftOut > aw * 0.34f || rightOut > aw * 0.34f) {
                ui.floatParkLeft = leftOut > rightOut
                ui.floatParkY = (ui.floatY + ah / 2f - 28f * dp).coerceIn(0f, areaH - 56f * dp)
                ui.floatX = if (ui.floatParkLeft) 12f * dp else areaW - aw - 12f * dp
                c.tabs.parkFloat(true)
            } else {
                ui.floatX = fx.coerceIn(0f, (areaW - aw).coerceAtLeast(0f))
                ui.floatY = ui.floatY.coerceIn(0f, (areaH - ah).coerceAtLeast(0f))
            }
        }
        fun resize(dScale: Float, fromLeft: Boolean) {
            val before = w
            ui.floatScale = (ui.floatScale + dScale).coerceIn(0.6f, 2.0f)
            if (fromLeft) {
                val after = ((if (wide) areaW * 0.66f else areaW * 0.46f) * ui.floatScale).coerceIn(minW, areaW * 0.96f)
                ui.floatX = (if (ui.floatX.isNaN()) defaultX else ui.floatX) - (after - before)
            }
        }

        if (parked) {
            val parkY = if (ui.floatParkY.isNaN()) areaH / 3f else ui.floatParkY
            FloatEdgeIcon(
                letter, label, left = ui.floatParkLeft,
                modifier = Modifier.offset { IntOffset(if (ui.floatParkLeft) (-28f * dp).roundToInt() else (areaW - 28f * dp).roundToInt(), parkY.roundToInt()) },
                onOpen = { c.tabs.parkFloat(false) },
                onDrag = { dy -> ui.floatParkY = (parkY + dy).coerceIn(0f, areaH - 56f * dp) },
            )
        } else {
            var controls by remember { mutableStateOf(false) }
            LaunchedEffect(controls) { if (controls) { delay(5000); controls = false } }
            FloatFrame(
                width = with(density) { aw.toDp() },
                height = with(density) { ah.toDp() },
                wide = wide,
                letter = letter,
                host = tab.host.ifBlank { label },
                label = label,
                controls = controls,
                muted = muted,
                modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                onMove = ::move,
                onMoveEnd = ::moveEnd,
                onTap = { controls = !controls },
                onResize = { dx, fromLeft -> resize((if (fromLeft) -dx else dx) / areaW * 1.6f, fromLeft) },
                onFullSize = { c.tabs.unfloat(fullSize = true); ui.go(Screen.Browser) },
                onSound = { c.tabs.setMuted(tab, !muted) },
                onVideoOnly = {
                    controls = false
                    if (c.engine.helper.reaches(tab.session)) c.engine.helper.videoOnly(tab.session, true)
                    else c.engine.messages.tryEmit("There's no video on this page")
                },
                onToPage = {
                    controls = false
                    c.engine.helper.videoOnly(tab.session, false)
                    ui.floatVideoOnly = false
                },
                onPlayPause = { c.engine.helper.toggle(tab.session) },
                onClose = { c.tabs.unfloat() },
            ) {
                PageView(tab, ui, primary = false, floating = true)
                if (tab.isNewTabPage) {
                    Box(Modifier.fillMaxSize().background(Space.Surface), contentAlignment = Alignment.Center) {
                        Text("New tab", color = Space.Text2, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** The floating window itself: its bar (for a page), the page, its buttons and resize corners. */
@Composable
fun FloatFrame(
    width: Dp,
    height: Dp,
    wide: Boolean,
    letter: String,
    host: String,
    label: String,
    controls: Boolean,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onMove: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    onTap: () -> Unit,
    onResize: (dx: Float, fromLeft: Boolean) -> Unit,
    onFullSize: () -> Unit,
    onSound: () -> Unit,
    onVideoOnly: () -> Unit,
    onToPage: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    page: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val moveBy by rememberUpdatedState(onMove)
    val moveEnd by rememberUpdatedState(onMoveEnd)
    val tap by rememberUpdatedState(onTap)
    val dragToMove = Modifier
        .pointerInput(Unit) { detectDragGestures(onDragEnd = { moveEnd() }, onDragCancel = { moveEnd() }) { change, d -> change.consume(); moveBy(d) } }
        .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
    Box(
        modifier.size(width, height).shadow(18.dp, shape).clip(shape).background(Color(0xFF0F1113))
            .border(1.dp, Color(0x4DC7CCD8), shape)
            .semantics { contentDescription = "Floating tab: $label" },
    ) {
        if (!wide) {
            Column(Modifier.fillMaxSize()) {
                // The bar: moves the window; a tap shows its buttons.
                Row(
                    Modifier.fillMaxWidth().height(30.dp).background(Space.Surface).then(dragToMove)
                        .semantics { contentDescription = "Move the floating tab"; onClick("Show its buttons") { tap(); true } }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Space.Surface3), contentAlignment = Alignment.Center) {
                        Text(letter, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Space.Text)
                    }
                    Text(host, fontSize = 10.sp, color = Space.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(Color(0xFF3A4256)))
                    Spacer(Modifier.weight(0.4f))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    page()
                    Fade(controls) {
                        Box(Modifier.fillMaxSize().background(Color(0x59070A12)), contentAlignment = Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundGlass(Icons.Expand, "Full size", onFullSize)
                                RoundGlass(if (muted) Icons.Mute else Icons.Sound, if (muted) "Sound on" else "Mute", onSound)
                                RoundGlass(Icons.VideoOnly, "Video only", onVideoOnly)
                                RoundGlass(Icons.Close, "Close the floating tab", onClose)
                            }
                        }
                    }
                }
            }
        } else {
            page()
            // Over a video the page doesn't take touches: the whole window moves, and a tap shows its buttons.
            Box(Modifier.fillMaxSize().then(dragToMove).semantics { contentDescription = "Move the floating video"; onClick("Show its buttons") { tap(); true } })
            Fade(controls) {
                Box(Modifier.fillMaxSize().background(Color(0x33070A12)).padding(8.dp)) {
                    RoundGlass(Icons.ToPage, "Back to the page", onToPage, Modifier.align(Alignment.TopStart))
                    RoundGlass(Icons.Close, "Close the floating tab", onClose, Modifier.align(Alignment.TopEnd))
                    RoundGlass(Icons.Pause, "Play or pause", onPlayPause, Modifier.align(Alignment.Center), size = 42.dp)
                    RoundGlass(if (muted) Icons.Mute else Icons.Sound, if (muted) "Sound on" else "Mute", onSound, Modifier.align(Alignment.BottomStart))
                    RoundGlass(Icons.Expand, "Full size", onFullSize, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        // Over the page (which would otherwise take the touch), and away while the buttons show, so a button on a
        // corner gets its tap.
        if (!controls) {
            ResizeCorner(Modifier.align(Alignment.BottomStart), fromLeft = true, onResize)
            ResizeCorner(Modifier.align(Alignment.BottomEnd), fromLeft = false, onResize)
        }
    }
}

/** Fades its content in and out (outside any row or column, whose own version would otherwise be picked). */
@Composable
private fun Fade(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) { content() }
}

@Composable
private fun RoundGlass(icon: RavenIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color(0x9E070A12)).border(1.dp, Color(0x38C7CCD8), CircleShape)
            .clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, size = size * 0.46f, tint = Space.Text) }
}

/** A bottom corner: drag it out to make the window bigger, in to make it smaller. */
@Composable
private fun ResizeCorner(modifier: Modifier, fromLeft: Boolean, onResize: (Float, Boolean) -> Unit) {
    val resize by rememberUpdatedState(onResize)
    Box(
        modifier.size(30.dp)
            .pointerInput(fromLeft) { detectDragGestures { change, d -> change.consume(); resize(d.x + d.y * 0.6f * (if (fromLeft) -1 else 1), fromLeft) } }
            .semantics { contentDescription = if (fromLeft) "Resize from the left corner" else "Resize from the right corner" }
            .drawBehind {
                val s = 2.dp.toPx()
                val len = 12.dp.toPx()
                val m = 7.dp.toPx()
                val p = Path().apply {
                    if (fromLeft) { moveTo(m, size.height - m - len); lineTo(m, size.height - m); lineTo(m + len, size.height - m) }
                    else { moveTo(size.width - m, size.height - m - len); lineTo(size.width - m, size.height - m); lineTo(size.width - m - len, size.height - m) }
                }
                drawPath(p, Color(0xCCC7CCD8), style = Stroke(s, cap = StrokeCap.Round))
            },
    )
}

/** Parked on the edge: half a small round icon with the site's letter and a pause mark. Tap to open, slide to move. */
@Composable
fun FloatEdgeIcon(letter: String, label: String, left: Boolean, modifier: Modifier = Modifier, onOpen: () -> Unit, onDrag: (Float) -> Unit) {
    val open by rememberUpdatedState(onOpen)
    val drag by rememberUpdatedState(onDrag)
    Box(
        modifier.size(56.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(Space.Surface)
            .border(1.dp, Color(0x59C7CCD8), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onTap = { open() }) }
            .pointerInput(Unit) { detectDragGestures { change, d -> change.consume(); drag(d.y) } }
            .semantics { contentDescription = "Floating tab parked: $label. Tap to open"; onClick("Open") { open(); true } },
    ) {
        // Only the half on screen shows: the letter and pause mark sit in it.
        Row(
            Modifier.align(if (left) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Space.Surface3), contentAlignment = Alignment.Center) {
                Text(letter, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Space.Text)
            }
        }
        Box(
            Modifier.align(if (left) Alignment.BottomEnd else Alignment.BottomStart).padding(horizontal = 4.dp, vertical = 4.dp)
                .size(16.dp).clip(CircleShape).background(Space.Text),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Pause, null, size = 10.dp, tint = Space.OnAccent) }
    }
}
