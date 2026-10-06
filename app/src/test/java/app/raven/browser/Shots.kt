package app.raven.browser

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.raven.browser.ui.browser.BarStrip
import app.raven.browser.ui.browser.Continue
import app.raven.browser.ui.browser.MenuContent
import app.raven.browser.ui.browser.FloatEdgeIcon
import app.raven.browser.ui.browser.FloatFrame
import app.raven.browser.ui.browser.HalfChrome
import app.raven.browser.ui.browser.SplitLayout
import app.raven.browser.engine.TabManager
import app.raven.browser.ui.browser.MenuTileSpec
import app.raven.browser.ui.browser.PrivateLocked
import app.raven.browser.ui.browser.RoundAction
import app.raven.browser.ui.components.Card
import app.raven.browser.ui.components.Divider
import app.raven.browser.ui.components.ListRow
import app.raven.browser.ui.components.Toggle
import app.raven.browser.ui.components.SheetHandle
import app.raven.browser.ui.screens.WallpaperPicker
import app.raven.browser.ui.screens.BookmarksContent
import app.raven.browser.data.Bookmark
import app.raven.browser.engine.VpnPlace
import app.raven.browser.ui.browser.PlaceRow
import app.raven.browser.ui.screens.WelcomeContent
import app.raven.browser.ui.browser.HomeContent
import app.raven.browser.ui.browser.PinnedSite
import app.raven.browser.ui.browser.PrivateHomeContent
import app.raven.browser.ui.browser.TabsButton
import app.raven.browser.ui.components.IconButton
import app.raven.browser.ui.sky.Wallpaper
import app.raven.browser.ui.tabs.Hand
import app.raven.browser.ui.tabs.TabCardInfo
import app.raven.browser.ui.tabs.TabsLayout
import app.raven.browser.ui.tabs.TabSide
import app.raven.browser.ui.tabs.FlocksGrid
import app.raven.browser.ui.tabs.FlockInfo
import app.raven.browser.ui.sky.Wallpapers
import app.raven.browser.ui.sky.nightSky
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.RavenTheme
import app.raven.browser.ui.theme.Space
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A plain application: the screens are drawn without starting the browser engine. */
class ShotsApp : Application()

/**
 * Pictures of Raven's screens, drawn on the computer (no phone needed), for checking the design.
 * Run: ./gradlew :app:testDebugUnitTest --tests '*Shots*'  →  app/build/shots/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi", application = ShotsApp::class)
open class Shots {
    @get:Rule val compose = createComposeRule()
    /** Where the pictures go. */
    protected open val dir = "build/shots"

    private fun image(w: Wallpaper): ImageBitmap? = w.res?.let {
        BitmapFactory.decodeResource(ApplicationProvider.getApplicationContext<Application>().resources, it).asImageBitmap()
    }

    private val sites = listOf(
        PinnedSite("https://wikipedia.org/", "Wikipedia", true),
        PinnedSite("https://youtube.com/", "YouTube", false),
        PinnedSite("https://github.com/", "GitHub", false),
        PinnedSite("https://reddit.com/", "Reddit", false),
        PinnedSite("https://duckduckgo.com/", "DuckDuckGo", false),
    )

    @Composable
    private fun Bar(sky: Modifier?, private: Boolean = false, text: String = "Search or type address", tabs: Int = 4) {
        BarStrip(private, sky) {
            IconButton(Icons.Home, "Home", {}, size = 44.dp, enabled = false)
            Row(
                Modifier.weight(1f).height(44.dp).clip(CircleShape)
                    .background(if (sky != null) (if (private) Color(0x1F9A8CFF) else Color(0x10E9ECF3)) else Space.Surface2)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (private) Icons.Eclipse else Icons.Search, null, size = 16.dp, tint = if (private) Space.NebulaText else Space.Text2)
                Spacer(Modifier.width(8.dp))
                Text(text, color = Space.Text2, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            if (sky == null) IconButton(Icons.Reload, "Reload", {}, size = 44.dp, iconSize = 20.dp)
            TabsButton(tabs, private, 44.dp) {}
            IconButton(Icons.Menu, "Menu", {}, size = 44.dp)
        }
    }

    private fun home(w: Wallpaper) {
        val img = image(w)
        compose.setContent {
            RavenTheme(0, false, true) {
                Column(Modifier.fillMaxSize()) {
                    Bar(Modifier.nightSky(img, w, Space.Ground, moving = false))
                    Box(Modifier.weight(1f)) {
                        HomeContent(
                            sky = Modifier.nightSky(img, w, Space.Ground, moving = false), wallpaper = w.name,
                            date = "MONDAY · 5 OCTOBER", greeting = "Good\nevening.",
                            recent = Continue("Obsidian · Wikipedia", "Wikipedia"), sites = sites, blocked = 214,
                            onRecent = {}, onSite = {}, onSiteMenu = {}, onAdd = {}, onShield = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("$dir/home_${w.id}.png")
    }

    @Test fun homeMoonrise() = home(Wallpapers.byId("moonrise"))
    @Test fun homePines() = home(Wallpapers.byId("pines"))
    @Test fun homeFeather() = home(Wallpapers.byId("feather"))
    @Test fun homeCrescent() = home(Wallpapers.byId("crescent"))
    @Test fun homePlain() = home(Wallpapers.byId("none"))

    @Test fun privateHome() {
        val w = Wallpapers.eclipse
        val img = image(w)
        compose.setContent {
            RavenTheme(0, false, true) {
                Column(Modifier.fillMaxSize()) {
                    Bar(Modifier.nightSky(img, w, Space.NebulaGround, moving = false, private = true), private = true, text = "Search privately", tabs = 1)
                    Box(Modifier.weight(1f)) {
                        PrivateHomeContent(Modifier.nightSky(img, w, Space.NebulaGround, moving = false, private = true), shieldOn = true, locks = true)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("$dir/home_private.png")
    }

    @Test fun pageBar() {
        compose.setContent {
            RavenTheme(0, false, true) {
                Column(Modifier.fillMaxSize().background(Color.White)) {
                    Bar(null, text = "en.wikipedia.org")
                }
            }
        }
        compose.onRoot().captureRoboImage("$dir/bar_page.png")
    }

    /** A made-up page for a tab's picture: a coloured header, a title and lines of text. */
    private fun page(header: Int, dark: Boolean): ImageBitmap {
        val b = android.graphics.Bitmap.createBitmap(360, 720, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        cv.drawColor(if (dark) 0xFF0F0F0F.toInt() else 0xFFF8F9FA.toInt())
        p.color = header; cv.drawRect(0f, 0f, 360f, 190f, p)
        p.color = if (dark) 0xFFE0E0E0.toInt() else 0xFF202122.toInt(); cv.drawRoundRect(20f, 214f, 280f, 238f, 6f, 6f, p)
        p.color = if (dark) 0xFF555555.toInt() else 0xFFB0B4BA.toInt()
        for (i in 0 until 12) cv.drawRoundRect(20f, 262f + i * 30, 20f + 300f - (i % 3) * 40, 276f + i * 30, 5f, 5f, p)
        return b.asImageBitmap()
    }

    private fun tabs(private: Boolean) {
        val w = if (private) Wallpapers.eclipse else Wallpapers.byId("flight")
        val img = image(w)
        val cards = if (private) listOf(
            TabCardInfo("1", "Reddit · r/AskHistorians", "Reddit", true, false, false, page(0xFF2A1830.toInt(), true)),
            TabCardInfo("2", "Amazon · Gift ideas", "Amazon", true, true, false, page(0xFF241B36.toInt(), false)),
        ) else listOf(
            TabCardInfo("1", "BBC News · Home", "BBC", false, false, true, page(0xFFB80000.toInt(), false)),
            TabCardInfo("2", "GitHub · Your repositories", "GitHub", false, false, false, page(0xFF24292F.toInt(), true), flock = "Work"),
            TabCardInfo("3", "YouTube", "YouTube", false, false, false, page(0xFF2B1416.toInt(), true)),
            TabCardInfo("4", "Obsidian · Wikipedia", "Wikipedia", false, true, false, page(0xFF3A3F4A.toInt(), false)),
        )
        compose.setContent {
            RavenTheme(0, false, true) {
                TabsLayout(
                    backdrop = img, side = if (private) TabSide.Private else TabSide.Everyday, normalCount = 4, privateCount = 2, flockCount = 3,
                    subtitle = if (private) "2 private · locks when you leave" else "4 open · 1 resting",
                    onCloseAll = {}, onSide = {}, newLabel = if (private) "New private tab" else "New tab", onNewTab = {}, onDone = {},
                ) { Hand(cards, if (private) Space.Nebula else Space.accents[0], onOpen = {}, onClose = {}) }
            }
        }
        compose.onRoot().captureRoboImage("$dir/tabs_${if (private) "private" else "everyday"}.png")
    }

    @Test fun tabsEveryday() = tabs(false)
    @Test fun tabsPrivate() = tabs(true)

    @Test fun menu() {
        compose.setContent {
            RavenTheme(0, false, true) {
                Box(Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
                    Box(Modifier.fillMaxSize().background(Color(0x73040611)))
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Space.Surface)
                            .padding(bottom = 16.dp),
                    ) {
                        SheetHandle()
                        MenuContent(
                            RoundAction(Icons.Back, "Back", true) {}, RoundAction(Icons.Forward, "Forward", false) {},
                            RoundAction(Icons.BookmarkFilled, "Remove bookmark", true, on = true) {}, RoundAction(Icons.Share, "Share", true) {},
                            RoundAction(Icons.AddHome, "Add to home screen", true) {},
                            tiles = listOf(
                                MenuTileSpec(Icons.Plus, "New tab", "New tab") {},
                                MenuTileSpec(Icons.Bookmark, "Bookmarks", "Bookmarks") {},
                                MenuTileSpec(Icons.Download, "Downloads", "Downloads", badge = "2") {},
                                MenuTileSpec(Icons.History, "History", "History") {},
                                MenuTileSpec(Icons.Float, "Float", "Float this tab") {},
                                MenuTileSpec(Icons.Split, "Split screen", "Split screen") {},
                                MenuTileSpec(Icons.Find, "Find in page", "Find in page") {},
                                MenuTileSpec(Icons.Translate, "Translate", "Translate page") {},
                                MenuTileSpec(Icons.Desktop, "Desktop site", "Desktop site", on = true) {},
                                MenuTileSpec(Icons.Sliders, "Settings", "Settings") {},
                                MenuTileSpec(Icons.Puzzle, "Add-ons", "Add-ons") {},
                                MenuTileSpec(Icons.Pdf, "Save as PDF", "Save as PDF") {},
                                MenuTileSpec(Icons.Vpn, "VPN on", "VPN is on", tint = Color(0xFF8FD6B4), on = true, onColor = Color(0xFF8FD6B4)) {},
                                MenuTileSpec(Icons.Supernova, "Clean slate", "Clean slate", tint = Space.Solar) {},
                            ),
                            shieldDetail = "18 trackers turned away on this page", onShield = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("$dir/menu.png")
    }

    @Test fun lookSettings() {
        val thumbs = Wallpapers.all.associate { w ->
            w.id to w.res?.let {
                val o = BitmapFactory.Options().apply { inSampleSize = 8 }
                BitmapFactory.decodeResource(ApplicationProvider.getApplicationContext<Application>().resources, it, o).asImageBitmap()
            }
        }
        compose.setContent {
            RavenTheme(0, false, true) {
                Column(Modifier.fillMaxSize().background(Space.Ground).padding(16.dp)) {
                    Card {
                        WallpaperPicker(true, "moonrise", listOf("none", "snow"), thumbs, onRotate = {}, onTap = {})
                        Divider()
                        ListRow("Moving sky", detail = "Stars twinkle and the moon breathes on the home screen", trailing = { Toggle(true, {}, "Moving sky") })
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("$dir/settings_look.png")
    }

    @Test fun welcome() {
        val w = Wallpapers.byId("flight")
        val img = image(w)
        compose.setContent {
            RavenTheme(0, false, true) { WelcomeContent(Modifier.nightSky(img, w, Space.Ground, moving = false), onDefault = {}, onLater = {}) }
        }
        compose.onRoot().captureRoboImage("$dir/welcome.png")
    }

    @Test fun locked() {
        compose.setContent { RavenTheme(0, false, true) { PrivateLocked(onUnlock = {}, onLeave = {}) } }
        compose.onRoot().captureRoboImage("$dir/locked.png")
    }

    @Test fun bookmarks() {
        val now = 0L
        val items = listOf(
            Bookmark(1, "https://en.wikipedia.org/wiki/Obsidian", "Obsidian · Wikipedia", "en.wikipedia.org", "Reading", now),
            Bookmark(2, "https://www.nationalgeographic.com/ravens", "Corvid intelligence: what ravens know", "nationalgeographic.com", "Reading", now),
            Bookmark(3, "https://www.seriouseats.com/lamb", "Slow-cooked lamb with rosemary", "seriouseats.com", "Recipes", now),
            Bookmark(4, "https://www.audubon.org/field-guide/bird/common-raven", "Common Raven · Audubon field guide", "audubon.org", "", now),
            Bookmark(5, "https://www.carris.pt/28", "Tram 28 timetable", "carris.pt", "Lisbon", now),
            Bookmark(6, "https://www.youtube.com/watch?v=raven", "Ravens solve a puzzle box", "youtube.com", "", now),
        )
        compose.setContent {
            RavenTheme(0, false, true) {
                BookmarksContent(
                    total = 24, query = "", onQuery = {}, folders = listOf("Reading" to 9, "Recipes" to 6, "Work" to 5, "Lisbon" to 4),
                    folder = null, onFolder = {}, items = items, onOpen = {}, onMore = {}, onBack = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("$dir/bookmarks.png")
    }

    @Test fun flocks() {
        val img = image(Wallpapers.byId("flight"))
        fun card(id: String, name: String, label: String, color: Int, dark: Boolean) = TabCardInfo(id, name, label, false, false, false, page(color, dark))
        val flocks = listOf(
            FlockInfo("Work", 3, listOf(card("1", "GitHub", "GitHub", 0xFF24292F.toInt(), true), card("2", "Docs", "Google", 0xFF1C2433.toInt(), false), card("3", "Mail", "Proton", 0xFF6D4AFF.toInt(), false))),
            FlockInfo("Reading", 4, listOf(card("4", "Obsidian", "Wikipedia", 0xFF3A3F4A.toInt(), false), card("5", "Ravens", "Nat Geo", 0xFFFFCE00.toInt(), false), card("6", "Essay", "Aeon", 0xFF20283A.toInt(), false), card("7", "Long read", "Guardian", 0xFF052962.toInt(), false))),
            FlockInfo("Lisbon trip", 2, listOf(card("8", "Tram 28", "Carris", 0xFFE8B07A.toInt(), false), card("9", "Flights", "TAP", 0xFF2A2230.toInt(), true))),
            FlockInfo("Recipes", 3, listOf(card("10", "Lamb", "Serious Eats", 0xFF1C3028.toInt(), false), card("11", "Bread", "King Arthur", 0xFF22302A.toInt(), false), card("12", "Soup", "NYT", 0xFF1A2620.toInt(), false))),
        )
        compose.setContent {
            RavenTheme(0, false, true) {
                TabsLayout(
                    backdrop = img, side = TabSide.Flocks, normalCount = 12, privateCount = 1, flockCount = 4,
                    subtitle = "4 flocks · tabs that fly together", onCloseAll = null, onSide = {},
                    newLabel = "New flock", onNewTab = {}, onDone = {},
                ) { FlocksGrid(flocks, onOpen = {}, onNew = {}) }
            }
        }
        compose.onRoot().captureRoboImage("$dir/flocks.png")
    }

    @Test fun vpn() {
        val places = listOf(VpnPlace("1", "phone-NL-87", "NL"), VpnPlace("2", "phone-US-NY-12", "US"), VpnPlace("3", "phone-JP-3", "JP"))
        compose.setContent {
            RavenTheme(0, false, true) {
                Box(Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Space.Surface)
                            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        SheetHandle()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Vpn, null, size = 26.dp, tint = Color(0xFF8FD6B4))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("VPN", fontFamily = app.raven.browser.ui.theme.Display, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
                                Text("Raven is browsing from the Netherlands", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8FD6B4))
                            }
                            Toggle(true, {}, "VPN")
                        }
                        places.forEachIndexed { i, p -> PlaceRow(p, i == 0, true, {}, {}) }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("$dir/vpn.png")
    }

    /** A made-up page for the new screens' pictures: a heading and lines of text on a light or dark page. */
    @Composable
    private fun FakePage(color: Long, dark: Boolean, title: String) {
        Column(Modifier.fillMaxSize().background(Color(color)).padding(14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Text(title, color = if (dark) Color(0xFFF1F1F1) else Color(0xFF202122), style = MaterialTheme.typography.titleMedium)
            repeat(8) { i -> Box(Modifier.fillMaxWidth(if (i % 3 == 2) 0.6f else 0.95f).height(8.dp).clip(CircleShape).background(if (dark) Color(0xFF2A2F3A) else Color(0xFFD5D8DD))) }
        }
    }

    @Composable
    private fun FloatOver(wide: Boolean, controls: Boolean) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Bar(null, text = "en.wikipedia.org")
                FakePage(0xFFF8F9FA, false, "Obsidian")
            }
            FloatFrame(
                width = if (wide) 260.dp else 180.dp, height = if (wide) 146.dp else 300.dp, wide = wide, letter = if (wide) "Y" else "S",
                host = if (wide) "youtube.com" else "seriouseats.com", label = "Floating", controls = controls, muted = false,
                modifier = Modifier.offset(x = if (wide) 116.dp else 196.dp, y = 150.dp),
                onMove = {}, onMoveEnd = {}, onTap = {}, onResize = { _, _ -> }, onFullSize = {}, onSound = {}, onVideoOnly = {}, onToPage = {}, onPlayPause = {}, onClose = {},
            ) {
                if (wide) Box(Modifier.fillMaxSize().background(Color(0xFF141A28))) else FakePage(0xFFFBF7F1, false, "Slow-cooked lamb")
            }
        }
    }

    @Test fun floatTall() {
        compose.setContent { RavenTheme(0, false, true) { FloatOver(wide = false, controls = true) } }
        compose.onRoot().captureRoboImage("$dir/float_tall.png")
    }

    @Test fun floatWide() {
        compose.setContent { RavenTheme(0, false, true) { FloatOver(wide = true, controls = true) } }
        compose.onRoot().captureRoboImage("$dir/float_wide.png")
    }

    @Test fun floatParked() {
        compose.setContent {
            RavenTheme(0, false, true) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val edge = maxWidth - 28.dp
                    Column(Modifier.fillMaxSize()) {
                        Bar(null, text = "en.wikipedia.org")
                        FakePage(0xFFF8F9FA, false, "Obsidian")
                    }
                    FloatEdgeIcon("Y", "Ravens", left = false, modifier = Modifier.offset(x = edge, y = 360.dp), onOpen = {}, onDrag = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("$dir/float_parked.png")
    }

    @Test fun split() {
        compose.setContent {
            RavenTheme(0, false, true) {
                Column(Modifier.fillMaxSize().background(Space.Ground)) {
                    Bar(null, text = "youtube.com", tabs = 6)
                    SplitLayout(0.5f, {}, {},
                        first = { side -> Box(Modifier.fillMaxSize()) { FakePage(0xFF0F0F0F, true, "Ravens solve a puzzle box"); HalfChrome(true, false, TabManager.SplitSound.TOP, side) {} } },
                        second = { side -> Box(Modifier.fillMaxSize()) { FakePage(0xFF0F0F0F, true, "Northern lights over Tromsø"); HalfChrome(false, true, TabManager.SplitSound.TOP, side) {} } },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("$dir/split.png")
    }
}

/** The same screens on a small phone (320 × 640, like the test emulator), to catch anything that doesn't fit. */
@Config(sdk = [35], qualifiers = "w320dp-h640dp-mdpi", application = ShotsApp::class)
class SmallShots : Shots() {
    override val dir = "build/shots/small"
}
