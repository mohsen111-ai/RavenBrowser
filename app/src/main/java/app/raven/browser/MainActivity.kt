package app.raven.browser

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.lifecycle.Lifecycle
import app.raven.browser.engine.MediaButtons
import app.raven.browser.engine.MediaControls
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.raven.browser.engine.Displays
import app.raven.browser.engine.RavenSelectionDelegate
import app.raven.browser.engine.Taps
import app.raven.browser.engine.UrlInput
import app.raven.browser.ui.RavenRoot
import app.raven.browser.ui.Screen
import app.raven.browser.ui.UiState
import java.io.File

class MainActivity : FragmentActivity() {
    private val ui = UiState()
    private val container get() = (application as RavenApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val c = container
        c.tabs.selectionDelegateFactory = { RavenSelectionDelegate(this) }
        c.tabs.refreshSelectionDelegates()
        prompt  // created now, while the screen is being made
        setContent { RavenRoot(c, ui, this) }
        if (savedInstanceState == null) handle(intent)
    }

    // A new wallpaper and greeting each time Raven is opened again, even if Android threw this screen away meanwhile.
    override fun onStart() {
        super.onStart()
        val c = container
        if (c.wasAway) {
            c.wasAway = false
            c.sky.next()
            c.greetings.next()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.wasAway = true
    }

    // The activity handles rotation and dark-mode changes itself (see the manifest), so pass them on
    // to the engine: pages follow the system theme and get orientation events.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.engine.runtime.configurationChanged(newConfig)
        container.engine.runtime.orientationChanged()
    }

    // Every touch passes here first (and is never consumed): Taps spots double taps for the selection toolbar.
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        Taps.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    // ------------------------------------------------------------------ private tabs lock

    /** The phone has a fingerprint, face or screen lock Raven can ask for. */
    fun canLock(): Boolean = BiometricManager.from(this).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private var asking = false
    private var onAuthenticated: (() -> Unit)? = null

    // Made with the screen (Android wants it before the screen can save its state), used for every question.
    private val prompt by lazy {
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                asking = false
                onAuthenticated?.invoke()
                onAuthenticated = null
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                asking = false
                onAuthenticated = null
            }
        })
    }

    /** Asks for a fingerprint, face or the screen lock; [onSuccess] runs once it's given. */
    fun authenticate(title: String, onSuccess: () -> Unit) {
        if (asking || isFinishing || supportFragmentManager.isStateSaved) return
        if (!canLock()) { onSuccess(); return }  // the screen lock was removed meanwhile: nothing left to ask for
        asking = true
        onAuthenticated = onSuccess
        runCatching {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle("Raven")
                    .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
                    .build(),
            )
        }.onFailure { asking = false }
    }

    /** Opens the private tabs (asking first if they're locked), then runs [then]. */
    fun unlockPrivate(then: () -> Unit = {}) {
        if (!container.privateLocked.value) { then(); return }
        authenticate("Unlock private tabs") {
            container.privateLocked.value = false
            then()
        }
    }

    // ------------------------------------------------------------------ picture-in-picture

    /** A fullscreen video is playing and picture-in-picture is on: leaving Raven shrinks it into a window. */
    private var pipReady = false
    private var pipParams: PictureInPictureParams? = null

    /** Called by RavenRoot whenever the fullscreen video, its size or its playing state changes. */
    fun updatePip(ready: Boolean, size: Pair<Int, Int>?, playing: Boolean) {
        pipReady = ready
        val b = PictureInPictureParams.Builder()
        // Android refuses shapes beyond 2.39:1 either way; most videos are 16:9.
        val (w, h) = size ?: (16 to 9)
        val ratio = (w.toFloat() / h).coerceIn(1 / 2.39f, 2.39f)
        b.setAspectRatio(if (ratio > 1) Rational((ratio * 1000).toInt(), 1000) else Rational(1000, (1000 / ratio).toInt()))
        b.setActions(listOf(pipAction(playing)))
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(ready)
            b.setSeamlessResizeEnabled(true)
        }
        val params = b.build()
        pipParams = params
        runCatching { setPictureInPictureParams(params) }
    }

    /** Play or pause, the one button the little window needs. It goes to the media controls like the notification's. */
    private fun pipAction(playing: Boolean): RemoteAction {
        val what = if (playing) MediaControls.ACTION_PAUSE else MediaControls.ACTION_PLAY
        val intent = PendingIntent.getBroadcast(
            this, what.hashCode() + 1, Intent(this, MediaButtons::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = if (playing) "Pause" else "Play"
        return RemoteAction(Icon.createWithResource(this, if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play), label, label, intent)
    }

    // Android 12+ enters by itself (setAutoEnterEnabled); older versions are asked when you leave.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && pipReady && container.settings.current.pictureInPicture) {
            pipParams?.let { runCatching { enterPictureInPictureMode(it) } }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.pip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            ui.sheet = null
            ui.editing = false
            ui.findOpen = false
        } else if (lifecycle.currentState == Lifecycle.State.CREATED) {
            // The little window was closed (not opened back up into Raven): the video stops, as in Chrome.
            val c = container
            (ui.fullscreenTabId?.let { id -> c.tabs.tabs.value.firstOrNull { it.id == id } } ?: c.tabs.selected)?.media?.pause()
        }
    }

    override fun onDestroy() {
        // This screen is going (Android may rebuild it later): let the page go so the next screen can show it.
        ui.geckoView?.let { Displays.releaseView(it) }
        ui.geckoView = null
        if (isFinishing && !isChangingConfigurations && container.settings.current.eraseOnClose) {
            container.tabs.closeAll()
            container.eraseBrowsingData(history = true, cookies = true, cache = true)
        }
        super.onDestroy()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val c = container
        if (intent.getBooleanExtra(EXTRA_SHOW_DOWNLOADS, false)) {
            ui.go(Screen.Downloads)
            return
        }
        // From the media controls: back to the tab that's playing.
        intent.getStringExtra(EXTRA_TAB_ID)?.let { id ->
            if (c.tabs.tabs.value.any { it.id == id }) {
                c.tabs.select(id)
                ui.go(Screen.Browser)
            }
            return
        }
        val text: String? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> intent.getStringExtra("query")
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (text.isNullOrBlank()) return
        val fromApp = intent.action == Intent.ACTION_VIEW
        val url = UrlInput.resolve(text, c.settings.current.searchEngine)
        ui.go(Screen.Browser)
        val current = c.tabs.selected
        if (current != null && current.isNewTabPage && !current.private) {
            current.openedFromApp = fromApp
            c.tabs.load(current, url, fromNewTab = !fromApp)
        }
        else c.tabs.newTab(url = url, fromApp = fromApp)
    }

    companion object {
        const val EXTRA_SHOW_DOWNLOADS = "show_downloads"
        const val EXTRA_TAB_ID = "tab_id"
        /** A fingerprint or face, or else the phone's own PIN, pattern or password. */
        const val LOCK_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

/** Runs in its own process: waits for the old browser process to be gone, then starts it again. */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra("pid", -1)
        Thread {
            repeat(50) { if (pid > 0 && File("/proc/$pid").exists()) Thread.sleep(100) }
            Thread.sleep(300)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            Process.killProcess(Process.myPid())
        }.start()
    }
}
