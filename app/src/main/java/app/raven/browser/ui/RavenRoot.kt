package app.raven.browser.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.PowerManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.raven.browser.Container
import app.raven.browser.data.Motion
import app.raven.browser.engine.TabEvent
import app.raven.browser.ui.browser.BrowserScreen
import app.raven.browser.ui.browser.InstallSheet
import app.raven.browser.ui.browser.LongPressSheet
import app.raven.browser.ui.browser.MenuSheet
import app.raven.browser.ui.browser.PromptHost
import app.raven.browser.ui.browser.SiteInfoSheet
import app.raven.browser.ui.browser.SupernovaSheet
import app.raven.browser.ui.browser.UboSheet
import app.raven.browser.ui.screens.AddonsScreen
import app.raven.browser.ui.screens.DownloadsScreen
import app.raven.browser.ui.screens.HistoryScreen
import app.raven.browser.ui.screens.Onboarding
import app.raven.browser.ui.screens.SettingsScreen
import app.raven.browser.ui.tabs.TabsScreen
import app.raven.browser.ui.theme.RavenTheme
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun RavenRoot(c: Container, ui: UiState, activity: Activity) {
    val prefs by c.settings.prefs.collectAsState()
    val context = LocalContext.current
    // Followed as it changes (turning Battery Saver on stills the live wallpapers at once).
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var batterySaver by remember { mutableStateOf(power.isPowerSaveMode) }
    androidx.compose.runtime.DisposableEffect(power) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) { batterySaver = power.isPowerSaveMode }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, android.content.IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val reduceMotion = when (prefs.reduceMotion) { Motion.ALWAYS -> true; Motion.NEVER -> false; Motion.AUTO -> batterySaver }
    RavenTheme(prefs.accent, prefs.trueBlack, reduceMotion) {
        if (!prefs.onboardingDone) {
            Onboarding(c)
            return@RavenTheme
        }
        val tabs by c.tabs.tabs.collectAsState()
        val selectedId by c.tabs.selectedId.collectAsState()
        val tab = tabs.firstOrNull { it.id == selectedId }
        val popup by c.engine.popup.collectAsState()
        val install by c.engine.installRequest.collectAsState()

        // Ask once for notification permission, the first time a download starts (Android 13+)...
        val notifyLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { }
        val downloads by c.downloads.items.collectAsState()
        // ...or the first time something plays, for the media controls.
        val anyPlaying = tab?.playing?.collectAsState()?.value == true
        LaunchedEffect(downloads.any { it.status == app.raven.browser.downloads.DownloadStatus.RUNNING } || anyPlaying) {
            val running = downloads.any { it.status == app.raven.browser.downloads.DownloadStatus.RUNNING } || anyPlaying
            val sp = activity.getSharedPreferences("asked", 0)
            if (running && android.os.Build.VERSION.SDK_INT >= 33 && !sp.getBoolean("notifications", false) &&
                androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                sp.edit().putBoolean("notifications", true).apply()
                notifyLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // A full-screen page (tabs, settings, downloads...) hides the web page: stop drawing it until
        // the browser is back on screen. Saves battery and graphics work.
        val onBrowser = ui.screen == Screen.Browser
        val playing = tab?.playing?.collectAsState()?.value == true
        val overNewTab = tab?.ntpOverlay?.collectAsState()?.value == true
        // In another app the page rests too, unless it's playing something.
        val visible by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val started = visible.isAtLeast(Lifecycle.State.STARTED)
        val resumed = visible.isAtLeast(Lifecycle.State.RESUMED)
        LaunchedEffect(onBrowser, playing, overNewTab, tab?.id, started, resumed) {
            val session = tab?.session ?: return@LaunchedEffect
            // Back from another app, the page view switches its page on by itself as its surface comes back: say
            // again what Raven wants once that's done (a page under the Tabs screen or Settings stays resting).
            if (resumed) { withFrameNanos { }; withFrameNanos { } }
            if (session.isOpen) session.setActive((started && onBrowser && !overNewTab) || playing)
        }

        // Fullscreen video: no bars, Android's or ours. Read from the tab, so it also holds for a screen Android
        // rebuilt while you were in another app. Any tab on screen can go fullscreen: the one the bar belongs to, a
        // half of split screen, or the floating tab; its video then fills the whole phone screen.
        val split by c.tabs.split.collectAsState()
        val floatingId by c.tabs.floatingId.collectAsState()
        val parked by c.tabs.floatParked.collectAsState()
        val onScreen = listOfNotNull(
            tab,
            split?.let { s -> tabs.firstOrNull { it.id == s.top } },
            split?.let { s -> tabs.firstOrNull { it.id == s.bottom } },
            floatingId?.takeIf { !parked }?.let { id -> tabs.firstOrNull { it.id == id } },
        ).distinct()
        // Starts from what's true now, so a screen Android rebuilt is laid out for the fullscreen video at once.
        val fullTab by remember(onScreen.map { it.id }) {
            if (onScreen.isEmpty()) flowOf(null)
            else combine(onScreen.map { t -> t.fullscreen.map { on -> if (on) t else null } }) { all -> all.firstOrNull { it != null } }
        }.collectAsState(onScreen.firstOrNull { it.fullscreen.value })
        val fullscreen = fullTab != null
        val fullPlaying = fullTab?.playing?.collectAsState()?.value == true
        // Like Firefox and Chrome: a wide video in fullscreen turns the screen to landscape (either way round,
        // following the phone), and the screen is free to turn again once fullscreen ends.
        // A page's own lock (a game, a video player) wins; otherwise the screen turns freely.
        val wideVideo = fullTab?.wideVideo?.collectAsState()?.value
        val pageLock by c.engine.orientationLock.collectAsState()
        val shown by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val inFront = shown.isAtLeast(Lifecycle.State.STARTED)
        // Leaving fullscreen ends the page's lock too, as the Screen Orientation standard says. The engine
        // doesn't always say so itself (after picture-in-picture it didn't, and the screen stayed sideways).
        var wasFull by remember { mutableStateOf(fullscreen) }
        LaunchedEffect(fullscreen) {
            if (wasFull && !fullscreen) c.engine.orientationLock.value = null
            wasFull = fullscreen
        }
        // With the rotation lock on, the direction the screen had before the video (if Android left it turned).
        val rotationHold = rememberRotationHold(activity, fullscreen)
        LaunchedEffect(pageLock, fullscreen, wideVideo, inFront, rotationHold) {
            if (!inFront) return@LaunchedEffect
            val want = when {
                pageLock != null -> pageLock!!
                fullscreen && wideVideo == true -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> rotationHold ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            android.util.Log.i("Raven", "screen: fullscreen=$fullscreen wide=$wideVideo pageLock=$pageLock hold=$rotationHold -> $want (was ${activity.requestedOrientation})")
            activity.requestedOrientation = want
        }
        // Picture-in-picture: a fullscreen video that's playing shrinks into a small window when you leave Raven
        // (never while Raven is locked).
        val videoSize = fullTab?.videoSize?.collectAsState()?.value
        val appLocked by c.appLocked.collectAsState()
        LaunchedEffect(fullscreen, fullPlaying, videoSize, prefs.pictureInPicture, appLocked) {
            (activity as? app.raven.browser.MainActivity)?.updatePip(prefs.pictureInPicture && fullscreen && fullPlaying && !appLocked, videoSize, fullPlaying)
        }
        LaunchedEffect(fullTab?.id) {
            ui.fullscreen = fullscreen
            ui.fullscreenTabId = fullTab?.id
            android.util.Log.i("Raven", "fullscreen: ${fullTab?.id?.take(6) ?: "none"}${if (fullTab != null && fullTab?.id == floatingId) " (floating tab)" else if (fullTab != null && fullTab?.id != tab?.id) " (other half)" else ""}")
        }
        // The phone's own bars go too, for a fullscreen video and for full screen pages (a swipe from the edge shows
        // them for a moment, over the page).
        val hideSystemBars = fullscreen || (ui.fullPage && ui.screen == Screen.Browser)
        LaunchedEffect(hideSystemBars) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (hideSystemBars) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        // Private tabs lock: leaving Raven locks them; coming back to one asks for a fingerprint or the screen lock.
        val locked by c.privateLocked.collectAsState()
        val owner = LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(owner, prefs.lockPrivateTabs) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && prefs.lockPrivateTabs && c.tabs.tabs.value.any { it.private }) c.privateLocked.value = true
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        val main = activity as? app.raven.browser.MainActivity
        val privateOnScreen = tab?.private == true && ui.screen == Screen.Browser
        // (While all of Raven is locked, its own question comes first and opens these too.)
        LaunchedEffect(locked, privateOnScreen, resumed, appLocked) {
            // Asked once Raven has drawn its lock screen, not over a screen still coming back.
            if (locked && privateOnScreen && resumed && !appLocked) { withFrameNanos { }; withFrameNanos { }; main?.unlockPrivate() }
        }
        // With the lock on, private tabs stay out of screenshots and the recent apps view.
        val secret = prefs.lockPrivateTabs && ((tab?.private == true && ui.screen == Screen.Browser) || (ui.screen == Screen.Tabs && ui.privateSideShown))
        LaunchedEffect(secret, prefs.appLock) {
            if (secret) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            // With the lock for all of Raven on, the recent apps view never shows what's open (screenshots still work).
            if (android.os.Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!secret && !prefs.appLock)
        }
        // The lock for all of Raven: back in front while locked, it asks for a fingerprint or the screen lock at once.
        LaunchedEffect(appLocked, resumed) {
            if (appLocked && resumed) { withFrameNanos { }; withFrameNanos { }; main?.unlockApp() }
        }

        LaunchedEffect(Unit) {
            merge(c.tabs.events, c.engine.messages.map { TabEvent.Message(it) }).collect { e ->
                when (e) {
                    // Snackbars wait their turn without holding up the events behind them.
                    is TabEvent.Message -> launch { ui.snackbar.showSnackbar(e.text) }
                    is TabEvent.OpenExternal -> try {
                        activity.startActivity(e.intent)
                    } catch (_: ActivityNotFoundException) {
                        val fallback = e.fallbackUrl
                        val target = c.tabs.tabs.value.firstOrNull { it.id == e.tabId }
                        if (fallback != null && target != null) c.tabs.load(target, fallback)
                        else launch { ui.snackbar.showSnackbar("No app on this phone can open that link") }
                    }
                    is TabEvent.ContextMenu -> ui.sheet = Sheet.LongPress(e.tabId, e.element)
                    is TabEvent.OfferApp -> launch {
                        val r = ui.snackbar.showSnackbar("Open in ${e.app}?", actionLabel = "Open", duration = androidx.compose.material3.SnackbarDuration.Short)
                        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) runCatching { activity.startActivity(e.intent) }
                    }
                    // "Tab closed · Undo": the newest close replaces the last one's bar; when the bar goes, so do the tabs.
                    is TabEvent.Closed -> launch {
                        ui.snackbar.currentSnackbarData?.dismiss()
                        val r = ui.snackbar.showSnackbar(
                            if (e.count == 1) "Tab closed" else "${e.count} tabs closed",
                            actionLabel = "Undo",
                            duration = androidx.compose.material3.SnackbarDuration.Short,
                        )
                        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) c.tabs.undoClose(e.batch) else c.tabs.finishClosed(e.batch)
                    }
                }
            }
        }

        // Locked, nothing in a window of its own (a sheet, a page's question, a dialog) stays over the lock.
        LaunchedEffect(appLocked) {
            if (appLocked) { ui.sheet = null; ui.folderFor = null; ui.editing = false; ui.findOpen = false }
        }
        Box(Modifier.fillMaxSize().background(Space.Ground)) {
            // The browser stays composed underneath other screens so the page keeps its place.
            val homeShown = ui.screen == Screen.Browser && !appLocked && !(locked && tab?.private == true) && !ui.pip
            androidx.compose.runtime.CompositionLocalProvider(app.raven.browser.ui.browser.LocalBrowserShown provides homeShown) {
                BrowserScreen(c, ui)
            }
            if (locked && tab?.private == true && ui.screen == Screen.Browser && !ui.pip) {
                app.raven.browser.ui.browser.PrivateLocked(
                    onUnlock = { main?.unlockPrivate() },
                    modifier = Modifier.navigationBarsPadding(),
                    onLeave = {
                        val everyday = c.tabs.tabs.value.lastOrNull { !it.private }
                        if (everyday != null) c.tabs.select(everyday.id) else c.tabs.newTab(select = true)
                    },
                )
            }
            // (Locked, the screens wait underneath: their dialogs would show over the lock.)
            if (!appLocked) Screens(c, ui)
            // The floating tab floats over every screen in Raven (not over other apps, and not in picture-in-picture,
            // unless it's the floating tab's own video that's playing there).
            if (!ui.pip || (fullTab != null && fullTab?.id == floatingId)) app.raven.browser.ui.browser.FloatingTab(c, ui)

            // Over the private tabs' lock too, no sheet or page question shows.
            val covered = appLocked || (locked && tab?.private == true && ui.screen == Screen.Browser)
            if (!covered) when (val s = ui.sheet) {
                Sheet.Menu -> tab?.let { MenuSheet(c, ui, it) }
                Sheet.SiteInfo -> tab?.let { SiteInfoSheet(c, ui, it) }
                Sheet.Supernova -> SupernovaSheet(c, ui)
                Sheet.Translate -> tab?.let { app.raven.browser.ui.browser.TranslateSheet(c, ui, it) }
                Sheet.Vpn -> app.raven.browser.ui.browser.VpnSheet(c, ui)
                Sheet.Split -> tab?.let { app.raven.browser.ui.browser.SplitPickerSheet(c, ui, it) }
                is Sheet.LongPress -> LongPressSheet(c, ui, s)
                null -> Unit
            }
            if (!covered) {
                popup?.let { UboSheet(c, it, tab) }
                install?.let { InstallSheet(c, it) }
                PromptHost(c)
            }

            // On the Tabs screen it sits above the New tab button, so a quick tap there never lands on Undo.
            if (!ui.pip) SnackbarHost(ui.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).padding(bottom = if (ui.screen == Screen.Tabs) 72.dp else 0.dp)) { data ->
                // Moonlight pill; an action (Undo, Folder) sits at its end.
                Snackbar(
                    data,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    containerColor = Space.Text,
                    contentColor = Space.OnAccent,
                    actionColor = Space.OnAccent,
                    actionContentColor = Space.OnAccent,
                )
            }
            // Locked: the lock covers everything, the floating tab and any sheet too.
            if (appLocked && !ui.pip) app.raven.browser.ui.browser.AppLocked(onUnlock = { main?.unlockApp() })
            ui.folderFor?.let { url ->
                var folders by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf<List<String>>(emptyList()) }
                LaunchedEffect(url) { folders = c.db.bookmarkFolders().map { it.first } }
                app.raven.browser.ui.screens.FolderDialog("", folders, onDismiss = { ui.folderFor = null }) { f ->
                    c.scope.launch { c.db.editBookmark(url, folder = f) }
                    ui.folderFor = null
                }
            }
        }

        BackHandler(enabled = true) {
            val t = tab
            when {
                c.appLocked.value -> activity.moveTaskToBack(true)
                ui.folderFor != null -> ui.folderFor = null
                ui.sheet != null -> ui.sheet = null
                ui.findOpen -> { ui.findOpen = false; t?.session?.finder?.clear() }
                ui.editing -> ui.editing = false
                // A video fullscreen (here, in a half or in the floating tab): back to where it was.
                ui.fullscreen -> (fullTab ?: t)?.session?.exitFullScreen()
                ui.screen != Screen.Browser -> ui.screen = Screen.Browser
                // Home showed the new tab page over a page: Back returns to that page.
                t != null && t.ntpOverlay.value && t.overlayFromHome -> t.leaveHome()
                // Like Chrome: page by page, then the new tab page the tab started on, then out of the app.
                t == null || t.ntpOverlay.value -> activity.moveTaskToBack(true)
                t.canGoBack.value -> t.session.goBack()
                t.openedFromApp -> { c.tabs.close(t.id); activity.moveTaskToBack(true) }
                c.tabs.opener(t) != null -> c.tabs.returnToOpener(t)
                t.startedFromNewTab && !t.hasNoPage -> t.backToNewTabPage()
                !t.isNewTabPage && tabs.size > 1 && t.private -> c.tabs.close(t.id)
                else -> activity.moveTaskToBack(true)
            }
        }
    }
}

@Composable
private fun Screens(c: Container, ui: UiState) {
    val back = { ui.screen = Screen.Browser }
    Screen.entries.filter { it != Screen.Browser }.forEach { screen ->
        // The tabs screen zooms out from the page into the orbit, and dives into the tab you pick.
        val zoom = screen == Screen.Tabs
        AnimatedVisibility(
            ui.screen == screen,
            enter = if (zoom) fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 1.08f) else fadeIn(),
            exit = if (zoom) fadeOut(tween(220)) + scaleOut(tween(260), targetScale = 1.08f) else fadeOut(),
        ) {
            when (screen) {
                Screen.Tabs -> TabsScreen(c, ui)
                Screen.Downloads -> DownloadsScreen(c, ui, back)
                Screen.History -> HistoryScreen(c, ui, back)
                Screen.Settings -> SettingsScreen(c, ui, back)
                Screen.Addons -> AddonsScreen(c, ui, back)
                Screen.Bookmarks -> app.raven.browser.ui.screens.BookmarksScreen(c, ui, back)
                Screen.Browser -> Unit
            }
        }
    }
}
