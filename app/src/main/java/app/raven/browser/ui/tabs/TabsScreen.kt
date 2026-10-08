package app.raven.browser.ui.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.Container
import app.raven.browser.engine.BrowserTab
import app.raven.browser.ui.Screen
import app.raven.browser.ui.UiState
import app.raven.browser.ui.browser.siteLabel
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.components.SitePlanet
import app.raven.browser.ui.components.glass
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Tabs as a hand of glass cards (Onyx's idea, in Raven's night): each card sits a little lower than the one
 * before and turns a touch, the tab you were on edged in moonlight. Tap a card to open it, swipe it away to
 * close it. Private tabs have their own hand, in eclipse violet.
 */
@Composable
fun TabsScreen(c: Container, ui: UiState) {
    val tabs by c.tabs.tabs.collectAsState()
    val selectedId by c.tabs.selectedId.collectAsState()
    val prefs by c.settings.prefs.collectAsState()
    val flocksVersion by c.tabs.flocksVersion.collectAsState()
    // Opens on the side of the tab you were on.
    var sideName by rememberSaveable { mutableStateOf(if (tabs.firstOrNull { it.id == selectedId }?.private == true) TabSide.Private.name else TabSide.Everyday.name) }
    val side = TabSide.valueOf(sideName)
    val privateSide = side == TabSide.Private
    var openFlock by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmCloseAll by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<NewFlock?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var addingProfile by remember { mutableStateOf(false) }

    // Profiles: the everyday side shows one person's tabs at a time, picked with the chips at the top.
    val profiles by c.profiles.all.collectAsState()
    val activeProfile by c.tabs.profile.collectAsState()
    var shownProfile by rememberSaveable { mutableStateOf(activeProfile) }
    LaunchedEffect(profiles) { if (profiles.none { it.id == shownProfile }) shownProfile = "" }
    val many = profiles.size > 1
    fun colorOf(id: String): Color? = if (!many) null else profiles.firstOrNull { it.id == id }?.let { Color(app.raven.browser.data.Profiles.colors[it.color % app.raven.browser.data.Profiles.colors.size]) }

    val everyday = tabs.filter { !it.private && it.profile == shownProfile }
    // Read so the flocks below are worked out again whenever a tab joins or leaves one.
    @Suppress("UNUSED_VARIABLE") val v = flocksVersion
    val flocks = everyday.filter { it.flock.value != null }.groupBy { it.flock.value!! }
    // The open flock emptied out or was renamed elsewhere: back to all flocks.
    LaunchedEffect(flocks.keys) { if (openFlock != null && openFlock !in flocks) openFlock = null }
    val shown = when (side) {
        TabSide.Private -> tabs.filter { it.private }
        TabSide.Everyday -> everyday
        TabSide.Flocks -> openFlock?.let { flocks[it] }.orEmpty()
    }
    val normalCount = everyday.size
    val privateCount = tabs.count { it.private }

    fun open(tab: BrowserTab) {
        c.tabs.select(tab.id)
        ui.go(Screen.Browser)
    }
    val locked by c.privateLocked.collectAsState()
    val main = androidx.compose.ui.platform.LocalContext.current as? app.raven.browser.MainActivity
    fun newTab() {
        when {
            side == TabSide.Flocks && openFlock == null -> naming = NewFlock(null)
            else -> {
                val open = { c.tabs.newTab(private = privateSide, flock = openFlock.takeIf { side == TabSide.Flocks }, profile = shownProfile); ui.go(Screen.Browser) }
                if (privateSide && main != null) main.unlockPrivate(open) else open()
            }
        }
    }
    // The Private side is kept out of screenshots and recent apps while the lock is on (RavenRoot).
    androidx.compose.runtime.SideEffect { ui.privateSideShown = privateSide }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ui.privateSideShown = false } }
    LaunchedEffect(privateSide, locked) { if (privateSide && locked) main?.unlockPrivate() }

    val wallpaper by c.sky.image.collectAsState()
    LaunchedEffect(privateSide) { if (privateSide) c.sky.loadEclipse() }
    val eclipse by c.sky.eclipse.collectAsState()
    val edge = if (privateSide) Space.Nebula else Raven.accent

    val resting = shown.count { it.asleep.value && it.id != selectedId }
    TabsLayout(
        backdrop = if (privateSide) eclipse else wallpaper,
        side = side,
        normalCount = normalCount,
        privateCount = privateCount,
        flockCount = flocks.size,
        subtitle = when {
            side == TabSide.Flocks && openFlock != null -> "${shown.size} in $openFlock"
            side == TabSide.Flocks -> if (flocks.isEmpty()) "Tabs that fly together" else "${flocks.size} ${if (flocks.size == 1) "flock" else "flocks"} · tabs that fly together"
            privateSide && shown.isNotEmpty() -> "${shown.size} private" + if (prefs.lockPrivateTabs) " · locks when you leave" else ""
            privateSide -> "Nothing kept, nothing seen"
            else -> "${shown.size} open" + if (resting > 0) " · $resting resting" else ""
        },
        onCloseAll = if (shown.isNotEmpty() && side != TabSide.Flocks) ({ confirmCloseAll = true }) else null,
        onSide = { sideName = it.name; if (it != TabSide.Flocks) openFlock = null },
        newLabel = when {
            privateSide -> "New private tab"
            side == TabSide.Flocks && openFlock == null -> "New flock"
            else -> "New tab"
        },
        onNewTab = ::newTab,
        // Done on another profile's tabs: that person's last tab comes on screen (or a new one, if they have none).
        onDone = {
            val current = tabs.firstOrNull { it.id == selectedId }
            if (!privateSide && current != null && current.profile != shownProfile && !current.private) {
                val last = everyday.maxByOrNull { it.lastActive }
                if (last != null) c.tabs.select(last.id) else c.tabs.newTab(select = true, profile = shownProfile)
            }
            ui.go(Screen.Browser)
        },
        profiles = if (many && !privateSide) profiles.map { p ->
            ProfileChip(p.id, p.name, colorOf(p.id) ?: Space.Text, tabs.count { !it.private && it.profile == p.id }, p.id == shownProfile)
        } else emptyList(),
        onProfile = { id -> shownProfile = id; openFlock = null },
        onAddProfile = { addingProfile = true },
    ) {
        val cards = shown.map { it.card(it.id == selectedId, colorOf(it.profile).takeIf { _ -> !it.private }) }
        val hand: @Composable () -> Unit = {
            Hand(cards, edge, onOpen = { id -> shown.firstOrNull { it.id == id }?.let(::open) }, onClose = { c.tabs.close(it, undoable = true) }, onHold = { actionsFor = it })
        }
        when {
            privateSide && locked -> app.raven.browser.ui.browser.PrivateLocked(onUnlock = { main?.unlockPrivate() })
            side == TabSide.Flocks && openFlock == null -> FlocksGrid(
                flocks.map { (name, list) -> FlockInfo(name, list.size, list.take(4).map { t -> t.card(false, null) }) },
                onOpen = { openFlock = it },
                onNew = { naming = NewFlock(null) },
            )
            side == TabSide.Flocks -> Column {
                FlockHeader(openFlock!!, shown.size, onBack = { openFlock = null }, onRename = { renaming = openFlock }, onUngroup = { c.tabs.ungroup(openFlock!!, shownProfile) }, onClose = { c.tabs.closeFlock(openFlock!!, shownProfile) })
                Box(Modifier.weight(1f)) { hand() }
            }
            shown.isEmpty() -> EmptyHand(privateSide)
            else -> hand()
        }
    }

    if (confirmCloseAll) {
        AlertDialog(
            onDismissRequest = { confirmCloseAll = false },
            title = { Text(if (privateSide) "Close all private tabs?" else "Close all tabs?") },
            text = { Text("${shown.size} ${if (shown.size == 1) "tab" else "tabs"} will close.", color = Space.Text2) },
            confirmButton = {
                TextButton({ confirmCloseAll = false; c.tabs.closeTabs(shown.map { it.id }.toSet()) }) { Text("Close all", color = if (privateSide) Space.Nebula else Raven.accent) }
            },
            dismissButton = { TextButton({ confirmCloseAll = false }) { Text("Cancel", color = Space.Text2) } },
            containerColor = Space.Surface,
        )
    }
    // Holding a card: put the tab in a flock, take it out, or close it.
    val held = actionsFor?.let { id -> tabs.firstOrNull { it.id == id } }
    if (held != null) {
        val id = held.id
        val tab = held
        TabActionsSheet(
            name = tab.card(tab.id == selectedId, null).name,
            private = tab.private,
            flock = tab.flock.value,
            flocks = flocks.keys.toList(),
            onDismiss = { actionsFor = null },
            onFlock = { f -> c.tabs.setFlock(listOf(id), f); actionsFor = null },
            onNewFlock = { actionsFor = null; naming = NewFlock(id) },
            onClose = { c.tabs.close(id, undoable = true); actionsFor = null },
            onFloat = if (!tab.private && !tab.hasNoPage) ({ actionsFor = null; if (c.tabs.float(id)) ui.go(Screen.Browser) }) else null,
            // Shares the screen with the tab you were on: that one on top, this one below.
            onSplit = tabs.firstOrNull { it.id == selectedId }?.takeIf { it.id != id && it.private == tab.private }?.let {
                { actionsFor = null; if (c.tabs.split(id)) ui.go(Screen.Browser) }
            },
        )
    }
    naming?.let { start ->
        NewFlockDialog(
            tabs = everyday.map { it.id to it.card(false, null).name },
            preselected = listOfNotNull(start.tabId),
            onDismiss = { naming = null },
        ) { name, ids ->
            if (ids.isEmpty()) c.tabs.newTab(flock = name, select = false, profile = shownProfile) else c.tabs.setFlock(ids, name)
            sideName = TabSide.Flocks.name
            openFlock = name
            naming = null
        }
    }
    if (addingProfile) {
        NameDialog("New profile", "Their own sign-ins, history and tabs. Settings, bookmarks and home sites are shared.", "", "Add", onDismiss = { addingProfile = false }) { name ->
            val p = c.profiles.add(name)
            shownProfile = p.id
            addingProfile = false
        }
    }
    renaming?.let { old ->
        RenameFlockDialog(old, onDismiss = { renaming = null }) { new ->
            c.tabs.renameFlock(old, new, shownProfile)
            openFlock = new.trim().ifEmpty { old }
            renaming = null
        }
    }
}

/** Starting a new flock, maybe from a held tab. */
class NewFlock(val tabId: String?)

enum class TabSide { Everyday, Private, Flocks }

/** The Tabs screen around the hand: title, the Tabs | Private | Flocks switch, and New tab at the bottom. */
@Composable
fun TabsLayout(
    backdrop: ImageBitmap?,
    side: TabSide,
    normalCount: Int,
    privateCount: Int,
    flockCount: Int,
    subtitle: String,
    onCloseAll: (() -> Unit)?,
    onSide: (TabSide) -> Unit,
    newLabel: String,
    onNewTab: () -> Unit,
    onDone: () -> Unit,
    profiles: List<ProfileChip> = emptyList(),
    onProfile: (String) -> Unit = {},
    onAddProfile: () -> Unit = {},
    hand: @Composable () -> Unit,
) {
    val privateSide = side == TabSide.Private
    Box(Modifier.fillMaxSize().background(if (privateSide) Space.NebulaGround else Raven.ground)) {
        TabsBackdrop(backdrop, privateSide)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 10.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tabs", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-1).sp, lineHeight = 36.sp)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (privateSide) Color(0xFFA9A2D6) else Space.Text2)
                }
                if (onCloseAll != null) {
                    Box(
                        Modifier.height(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCloseAll).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Close all", style = MaterialTheme.typography.labelLarge, color = if (privateSide) Space.NebulaText else Space.Text2) }
                }
            }

            // Tabs | Private, side by side, in glass.
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().height(48.dp)
                    .glass(CircleShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Side("Tabs · $normalCount", null, side == TabSide.Everyday, false, Modifier.weight(1f)) { onSide(TabSide.Everyday) }
                Side("Private · $privateCount", Icons.Eclipse, privateSide, true, Modifier.weight(1f)) { onSide(TabSide.Private) }
                Side("Flocks · $flockCount", null, side == TabSide.Flocks, false, Modifier.weight(1f)) { onSide(TabSide.Flocks) }
            }

            // Whose tabs: one chip per profile (when there's more than one), and one to add another.
            if (profiles.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profiles.forEach { p -> ProfileChipView(p) { onProfile(p.id) } }
                    Box(
                        Modifier.height(38.dp).clip(CircleShape).border(1.dp, Color(0x33C7CCD8), CircleShape)
                            .clickable(onClickLabel = "Add a profile", onClick = onAddProfile).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Plus, "Add a profile", size = 16.dp, tint = Space.Text2) }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) { hand() }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    newLabel, onNewTab, Modifier.weight(1f),
                    icon = Icons.Plus, style = if (privateSide) PillStyle.Private else PillStyle.Primary,
                )
                Box(
                    Modifier.size(54.dp).glass(CircleShape).clickable(onClickLabel = "Done", role = Role.Button, onClick = onDone)
                        .semantics { contentDescription = "Done" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Check, null, size = 22.dp, tint = Space.Text, stroke = 2.2f) }
            }
        }
    }
}

/** The home screen's wallpaper behind the tabs, blurred and darkened so the cards stand out. */
@Composable
private fun TabsBackdrop(image: ImageBitmap?, private: Boolean) {
    if (image == null) return
    // Blur needs Android 12; before that the wallpaper is only darkened, a little more.
    val canBlur = android.os.Build.VERSION.SDK_INT >= 31
    Image(
        image, null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().then(if (canBlur) Modifier.blur(22.dp) else Modifier).graphicsLayer { alpha = if (canBlur) 0.6f else 0.35f },
    )
    val shade = if (private) Color(0xFF05040B) else Color(0xFF070A12)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(shade.copy(alpha = 0.45f), shade.copy(alpha = 0.85f)))))
}

/** One side of the Tabs | Private switch. */
@Composable
private fun Side(text: String, icon: app.raven.browser.ui.theme.RavenIcon?, on: Boolean, private: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(if (on && private) Color(0x339A8CFF) else if (on) Color(0x1FE9ECF3) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { role = Role.Tab; selected = on },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, size = 16.dp, tint = if (on) Space.Nebula else Space.Text2)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) Space.Text else Space.Text2, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
    }
}

/** A profile on the Tabs screen: its name, colour and how many tabs it has open. */
class ProfileChip(val id: String, val name: String, val color: Color, val count: Int, val selected: Boolean)

@Composable
private fun ProfileChipView(p: ProfileChip, onClick: () -> Unit) {
    Row(
        Modifier.height(38.dp).clip(CircleShape)
            .background(if (p.selected) p.color.copy(alpha = 0.16f) else Color(0x0DE9ECF3))
            .border(1.dp, if (p.selected) p.color.copy(alpha = 0.7f) else Color(0x1FC7CCD8), CircleShape)
            .clickable(onClick = onClick)
            .semantics { role = Role.Tab; selected = p.selected; contentDescription = "Profile ${p.name}, ${p.count} tabs" }
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(p.color))
        Spacer(Modifier.width(8.dp))
        Text(p.name, style = MaterialTheme.typography.labelLarge, color = if (p.selected) Space.Text else Space.Text2, fontWeight = if (p.selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("${p.count}", style = MaterialTheme.typography.labelMedium, color = Space.Text3)
    }
}

/** What a card shows of its tab. */
class TabCardInfo(
    val id: String,
    val name: String,
    val label: String,
    val private: Boolean,
    val selected: Boolean,
    val resting: Boolean,
    val thumbnail: ImageBitmap?,
    val flock: String? = null,
    /** Its profile's colour, when there's more than one profile. */
    val profileColor: Color? = null,
)

@Composable
private fun BrowserTab.card(selected: Boolean, profileColor: Color?): TabCardInfo {
    val thumb by thumbnail.collectAsState()
    val asleep by asleep.collectAsState()
    val title by title.collectAsState()
    val url by url.collectAsState()
    val overNewTab by ntpOverlay.collectAsState()
    val blank = overNewTab || hasNoPage
    return TabCardInfo(
        id = id,
        name = if (blank) (if (private) "Private tab" else "New tab") else title.ifBlank { host.ifBlank { "New tab" } },
        label = siteLabel(if (blank) "new" else url),
        private = private,
        selected = selected,
        resting = asleep && !selected,
        thumbnail = thumb.takeIf { !blank },
        flock = flock.collectAsState().value,
        profileColor = profileColor,
    )
}

/** The hand: cards overlapping down the screen; only the cards on screen are drawn, so a hundred tabs are fine. */
@Composable
fun Hand(cards: List<TabCardInfo>, edge: Color, onOpen: (String) -> Unit, onClose: (String) -> Unit, onHold: (String) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val card = (maxHeight * 0.5f).coerceIn(220.dp, 330.dp)
        val peek = 92.dp
        val list = rememberLazyListState()
        // Starts with the tab you were on in view.
        LaunchedEffect(Unit) {
            val i = cards.indexOfFirst { it.selected }
            if (i > 0) list.scrollToItem(i)
        }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(cards, key = { _, t -> t.id }) { i, tab ->
                val last = i == cards.lastIndex
                // Each card owns only its top strip; the rest is covered by the next card, as in a hand of cards.
                Box(Modifier.animateItem().fillMaxWidth().height(if (last) card else peek)) {
                    TabCard(
                        tab, edge,
                        tilt = ((i % 3) - 1) * 0.7f,
                        onOpen = { onOpen(tab.id) }, onClose = { onClose(tab.id) }, onHold = { onHold(tab.id) },
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).requiredHeight(card),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TabCard(tab: TabCardInfo, edge: Color, tilt: Float, onOpen: () -> Unit, onClose: () -> Unit, onHold: () -> Unit, modifier: Modifier) {
    val selected = tab.selected
    val resting = tab.resting
    val name = tab.name
    val scope = rememberCoroutineScope()
    val slide = remember(tab.id) { Animatable(0f) }
    var gone by remember(tab.id) { mutableStateOf(false) }
    val widthPx = with(LocalDensity.current) { 220.dp.toPx() }

    fun throwAway(direction: Float) {
        if (gone) return
        gone = true
        scope.launch {
            // The tab closes even if the screen goes away mid-throw.
            try { slide.animateTo(direction * widthPx * 1.8f, tween(220, easing = FastOutLinearInEasing)) } finally { onClose() }
        }
    }

    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val v = slide.value
                        if (v.absoluteValue > widthPx * 0.42f) throwAway(if (v > 0) 1f else -1f)
                        else scope.launch { slide.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                    onDragCancel = { scope.launch { slide.animateTo(0f, spring(dampingRatio = 0.7f)) } },
                ) { change, dx ->
                    change.consume()
                    if (!gone) scope.launch { slide.snapTo(slide.value + dx) }
                }
            }
            .graphicsLayer {
                val t = (slide.value / widthPx).coerceIn(-2f, 2f)
                translationX = slide.value
                // Thrown away: the card spins off like a card flicked from a hand.
                rotationZ = tilt + t * 10f
                alpha = (1f - t.absoluteValue * 0.5f).coerceIn(0f, 1f)
                shadowElevation = 18.dp.toPx()
                this.shape = shape
                clip = false
            }
            .clip(shape)
            .background(if (tab.private) Color(0xF0100C1E) else Color(0xF00D111C))
            .border(
                1.dp,
                when {
                    selected -> Brush.verticalGradient(listOf(edge, edge.copy(alpha = 0.7f)))
                    tab.private -> Brush.verticalGradient(listOf(Space.Nebula.copy(alpha = 0.4f), Space.Nebula.copy(alpha = 0.18f)))
                    else -> Brush.verticalGradient(listOf(Space.GlassTop, Space.GlassEdge))
                },
                shape,
            )
            .combinedClickable(onClickLabel = "Open tab", onLongClickLabel = "More", onClick = onOpen, onLongClick = onHold)
            .semantics { contentDescription = "$name${if (resting) ", resting" else ""}${if (selected) ", current tab" else ""}" },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(50.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // The profile's colour rings the site, so each tab shows whose it is.
                SitePlanet(
                    tab.label, 28.dp, sleeping = resting, private = tab.private,
                    modifier = tab.profileColor?.let { Modifier.border(2.dp, it, CircleShape) } ?: Modifier,
                )
                Spacer(Modifier.width(10.dp))
                tab.flock?.let { f ->
                    Box(Modifier.padding(end = 8.dp).size(9.dp).clip(CircleShape).background(flockColor(f)).semantics { contentDescription = "In $f" })
                }
                Text(
                    name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = if (resting) Space.Text2 else Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (resting) {
                    Text(
                        "RESTING", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Space.Text2,
                        modifier = Modifier.padding(horizontal = 6.dp).clip(CircleShape).background(Color(0x14E9ECF3)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                IconButton(Icons.Close, "Close tab: $name", { throwAway(1f) }, size = 40.dp, iconSize = 14.dp, tint = Space.Text2)
            }
            // The page, as you left it.
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Space.Ground),
                contentAlignment = Alignment.Center,
            ) {
                val t = tab.thumbnail
                if (t != null) {
                    Image(t, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (resting) 0.5f else 1f })
                } else {
                    Text(
                        tab.label.take(1).uppercase(),
                        fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp,
                        color = if (tab.private) Space.NebulaText.copy(alpha = 0.7f) else Space.Text.copy(alpha = if (resting) 0.3f else 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHand(private: Boolean) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (private) "No private tabs" else "No tabs open",
            fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center,
        )
        Text(
            if (private) "Private tabs keep no history, cookies or site data once you close them." else "Open one below.",
            style = MaterialTheme.typography.bodyMedium, color = if (private) Color(0xFFA9A2D6) else Space.Text2, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

