package app.raven.browser.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import app.raven.browser.MainActivity
import app.raven.browser.R
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/**
 * The tab playing audio or video, in Android's media controls: a notification with the title, the site,
 * play/pause, skip and a progress bar, the same controls on the lock screen and for headphone buttons.
 * Playback keeps going when you switch apps. Pausing for a phone call and when headphones come out, too.
 */
class MediaControls(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    /** The tab whose media the controls are for: the one that played most recently. */
    var current: BrowserTab? = null
        private set

    /** What each tab's page last said about its media (it can tell us before it starts playing). */
    private class Info {
        var title = ""
        var artist = ""
        var artwork: Bitmap? = null
        var features = 0L
        var duration = 0.0
        var position = 0.0
        var rate = 1.0
        var positionAt = 0L
    }
    private val infos = java.util.WeakHashMap<BrowserTab, Info>()
    /**
     * Gecko keeps one page's media session active at a time: when another tab's starts, the first one's is set
     * aside, though its sound may still be playing. Kept here so it can be paused when another tab plays.
     */
    private val setAside = java.util.WeakHashMap<BrowserTab, MediaSession>()
    private fun info(tab: BrowserTab) = infos.getOrPut(tab) { Info() }
    private val now: Info get() = current?.let { info(it) } ?: Info()
    private var playing = false

    private val media: MediaSession? get() = current?.media

    val session = android.media.session.MediaSession(context, "Raven").apply {
        setCallback(object : android.media.session.MediaSession.Callback() {
            override fun onPlay() { media?.play() }
            override fun onPause() { media?.pause() }
            override fun onStop() { media?.stop() }
            override fun onSeekTo(pos: Long) { media?.seekTo(pos / 1000.0, false) }
            override fun onSkipToNext() { media?.nextTrack() }
            override fun onSkipToPrevious() { media?.previousTrack() }
            override fun onFastForward() { media?.seekForward() }
            override fun onRewind() { media?.seekBackward() }
        })
    }

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playing media", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for audio and video playing in Raven"
                setShowBadge(false)
            },
        )
    }

    // ------------------------------------------------------------------ what the page tells us

    fun delegate(tab: BrowserTab) = object : MediaSession.Delegate {
        override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
            Log.i("Raven", "media: activated tab ${tab.id.take(6)} (playing ${tab.playing.value}, current ${current?.id?.take(6)})")
            tab.media = mediaSession
        }

        override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
            Log.i("Raven", "media: deactivated tab ${tab.id.take(6)} (playing ${tab.playing.value}, current ${current?.id?.take(6)})")
            if (tab.playing.value) setAside[tab] = mediaSession
            tab.media = null
            tab.playing.value = false
            if (current === tab) end()
        }

        override fun onMetadata(session: GeckoSession, mediaSession: MediaSession, meta: MediaSession.Metadata) {
            tab.media = mediaSession
            val i = info(tab)
            i.title = meta.title.orEmpty()
            i.artist = meta.artist.orEmpty()
            i.artwork = null
            if (current === tab) publish()
            meta.artwork?.getBitmap(512)?.accept({ bmp ->
                if (bmp != null) { i.artwork = bmp; if (current === tab) publish() }
            }, { })
        }

        override fun onFeatures(session: GeckoSession, mediaSession: MediaSession, f: Long) {
            tab.media = mediaSession
            info(tab).features = f
            if (current === tab) publish()
        }

        override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
            Log.i("Raven", "media: play tab ${tab.id.take(6)} (playing ${tab.playing.value}, current ${current?.id?.take(6)})")
            tab.media = mediaSession
            tab.playing.value = true
            setAside.remove(tab)
            // One speaker in the room: a tab set aside while it was still playing pauses too.
            setAside.values.toList().forEach { runCatching { it.pause() } }
            if (setAside.isNotEmpty()) Log.i("Raven", "media: paused ${setAside.size} set-aside tab(s)")
            setAside.clear()
            if (current !== tab) take(tab)
            playing = true
            info(tab).positionAt = SystemClock.elapsedRealtime()
            focus(true)
            publish()
        }

        override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
            Log.i("Raven", "media: pause tab ${tab.id.take(6)} (playing ${tab.playing.value}, current ${current?.id?.take(6)})")
            tab.playing.value = false
            if (current !== tab) return
            settlePosition()
            playing = false
            publish()
        }

        override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
            Log.i("Raven", "media: stop tab ${tab.id.take(6)} (playing ${tab.playing.value}, current ${current?.id?.take(6)})")
            tab.playing.value = false
            if (current === tab) end()
        }

        // A video going fullscreen says its size: a wide one turns the screen to landscape (see RavenRoot).
        // It's forgotten when the page leaves fullscreen (TabManager), not here: the media session can report
        // "not fullscreen" while you're in another app, and the video is still fullscreen when you come back.
        override fun onFullscreen(session: GeckoSession, mediaSession: MediaSession, enabled: Boolean, meta: MediaSession.ElementMetadata?) {
            if (enabled && meta != null && meta.width > 0 && meta.height > 0) {
                tab.wideVideo.value = meta.width > meta.height
                tab.videoSize.value = meta.width.toInt() to meta.height.toInt()
            }
        }

        override fun onPositionState(session: GeckoSession, mediaSession: MediaSession, state: MediaSession.PositionState) {
            val i = info(tab)
            i.duration = state.duration
            i.position = state.position
            i.rate = state.playbackRate
            i.positionAt = SystemClock.elapsedRealtime()
            if (current === tab) publish()
        }
    }

    /** The tab closed or went to sleep: its controls go with it. */
    fun forget(tab: BrowserTab) {
        if (current === tab) end()
        infos.remove(tab)
        setAside.remove(tab)
    }

    /**
     * A tab started playing: Raven pauses the others (TabManager), except tabs on screen together. Set by the tab
     * manager; without it only the tab that played before is asked to pause.
     */
    var onTabPlays: ((BrowserTab) -> Unit)? = null

    private fun take(tab: BrowserTab) {
        // Another tab was playing: it pauses, like one speaker in a room.
        Log.i("Raven", "media: ${tab.id.take(6)} takes over from ${current?.id?.take(6)} (its session ${if (current?.media != null) "known" else "unknown"})")
        val hook = onTabPlays
        if (hook != null) hook(tab) else current?.takeIf { it !== tab }?.media?.pause()
        current = tab
    }

    private fun settlePosition() {
        val i = now
        if (playing) i.position += (SystemClock.elapsedRealtime() - i.positionAt) / 1000.0 * i.rate
        i.positionAt = SystemClock.elapsedRealtime()
    }

    private fun end() {
        current = null
        playing = false
        focus(false)
        session.isActive = false
        service(MediaService.STOP)
        manager.cancel(NOTIFICATION_ID)
    }

    // ------------------------------------------------------------------ Android's side

    private fun publish() {
        val tab = current ?: return
        val i = info(tab)
        val title = i.title
        val artist = i.artist
        val artwork = i.artwork
        val duration = i.duration
        val position = i.position
        val rate = i.rate
        val positionAt = i.positionAt
        val private = tab.private
        val shownTitle = when {
            private -> "Playing in a private tab"
            title.isNotBlank() -> title
            else -> tab.title.value.ifBlank { tab.host.ifBlank { "Playing" } }
        }
        val shownText = if (private) "" else artist.ifBlank { tab.host }
        val art = if (private) null else artwork

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, shownTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, shownText)
                .apply { if (duration > 0) putLong(MediaMetadata.METADATA_KEY_DURATION, (duration * 1000).toLong()) }
                .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ART, art) }
                .build(),
        )
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        if (has(MediaSession.Feature.SEEK_TO) && duration > 0) actions = actions or PlaybackState.ACTION_SEEK_TO
        if (has(MediaSession.Feature.NEXT_TRACK)) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (has(MediaSession.Feature.PREVIOUS_TRACK)) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (has(MediaSession.Feature.SEEK_FORWARD)) actions = actions or PlaybackState.ACTION_FAST_FORWARD
        if (has(MediaSession.Feature.SEEK_BACKWARD)) actions = actions or PlaybackState.ACTION_REWIND
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    (position * 1000).toLong().coerceAtLeast(0), if (playing) rate.toFloat() else 0f, positionAt,
                )
                .build(),
        )
        session.setSessionActivity(openTab(tab))
        session.isActive = true

        val n = notification(tab, shownTitle, shownText, art)
        if (playing) {
            MediaService.pending = n
            try {
                context.startForegroundService(Intent(context, MediaService::class.java).setAction(MediaService.SHOW))
                serviceAsked = true
            } catch (e: Exception) {
                // Android sometimes won't let a background app start this: the controls still show.
                Log.w("Raven", "media controls without a foreground service", e)
                manager.notify(NOTIFICATION_ID, n)
            }
        } else {
            service(MediaService.DETACH)
            manager.notify(NOTIFICATION_ID, n)
        }
    }

    private fun has(feature: Long) = now.features and feature != 0L

    /**
     * Asked to start, and not told to stop since. The service may not be running yet (it starts a moment
     * after it's asked), and a pause or a closed tab in that moment must still reach it, or it would come up
     * afterwards and keep a stale "playing" notification.
     */
    private var serviceAsked = false

    /** DETACH (paused) or STOP (gone) for the service, if it's running or about to be. Both end it. */
    private fun service(what: String) {
        if (!serviceAsked && !MediaService.running) return
        serviceAsked = false
        runCatching { context.startService(Intent(context, MediaService::class.java).setAction(what)) }
    }

    private fun notification(tab: BrowserTab, title: String, text: String, art: Bitmap?): Notification {
        val buttons = ArrayList<Notification.Action>()
        if (has(MediaSession.Feature.PREVIOUS_TRACK)) buttons += action(R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS)
        buttons += if (playing) action(R.drawable.ic_media_pause, "Pause", ACTION_PAUSE) else action(R.drawable.ic_media_play, "Play", ACTION_PLAY)
        if (has(MediaSession.Feature.NEXT_TRACK)) buttons += action(R.drawable.ic_media_next, "Next", ACTION_NEXT)
        val compact = when (buttons.size) { 1 -> intArrayOf(0); 2 -> intArrayOf(0, 1); else -> intArrayOf(0, 1, 2) }
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_raven)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(art)
            .setContentIntent(openTab(tab))
            .setDeleteIntent(broadcast(ACTION_STOP))
            .setVisibility(if (tab.private) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .setShowWhen(false)
            .setColor(0xFFC7CCD8.toInt())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(*compact))
            .apply { buttons.forEach { addAction(it) } }
            .build()
    }

    private fun action(icon: Int, label: String, what: String) =
        Notification.Action.Builder(Icon.createWithResource(context, icon), label, broadcast(what)).build()

    private fun broadcast(what: String): PendingIntent = PendingIntent.getBroadcast(
        context, what.hashCode(), Intent(context, MediaButtons::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openTab(tab: BrowserTab): PendingIntent = PendingIntent.getActivity(
        context, 7, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB_ID, tab.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** From the notification's buttons. */
    fun press(what: String) {
        when (what) {
            ACTION_PLAY -> media?.play()
            ACTION_PAUSE -> media?.pause()
            ACTION_NEXT -> media?.nextTrack()
            ACTION_PREVIOUS -> media?.previousTrack()
            ACTION_STOP -> { media?.pause(); end() }
        }
    }

    // ------------------------------------------------------------------ sharing the speaker

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .setOnAudioFocusChangeListener { change ->
            val m = media ?: return@setOnAudioFocusChangeListener
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> m.notifySystemAudioFocusChange(MediaSession.SYSTEM_AUDIO_FOCUS_PERMANENT_LOSS)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                    m.notifySystemAudioFocusChange(MediaSession.SYSTEM_AUDIO_FOCUS_TRANSIENT_LOSS)
                AudioManager.AUDIOFOCUS_GAIN -> m.notifySystemAudioFocusChange(MediaSession.SYSTEM_AUDIO_FOCUS_GAIN)
            }
        }
        .build()
    private var focused = false

    /** Headphones came out: pause, so the sound doesn't suddenly come from the speaker. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { media?.pause() }
    }

    private fun focus(want: Boolean) {
        if (want) {
            // Asked again on every play: another app may have taken the speaker while we were paused.
            audio.requestAudioFocus(focusRequest)
            if (focused) return
            focused = true
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        } else {
            if (!focused) return
            focused = false
            audio.abandonAudioFocusRequest(focusRequest)
            runCatching { context.unregisterReceiver(noisy) }
        }
    }

    companion object {
        const val CHANNEL = "media"
        const val NOTIFICATION_ID = 7001
        const val ACTION_PLAY = "app.raven.media.PLAY"
        const val ACTION_PAUSE = "app.raven.media.PAUSE"
        const val ACTION_NEXT = "app.raven.media.NEXT"
        const val ACTION_PREVIOUS = "app.raven.media.PREVIOUS"
        const val ACTION_STOP = "app.raven.media.STOP"
    }
}

/** The notification's buttons arrive here. */
class MediaButtons : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? app.raven.browser.RavenApp ?: return
        app.container.media.press(intent.action ?: return)
    }
}

/** Keeps Raven running while something plays and you're in another app, with the controls as its notification. */
class MediaService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SHOW -> {
                val n = pending
                if (n != null) {
                    if (Build.VERSION.SDK_INT >= 29) startForeground(MediaControls.NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    else startForeground(MediaControls.NOTIFICATION_ID, n)
                } else stopSelf()
            }
            // Paused: the controls stay (and can be swiped away), but Raven no longer has to keep running.
            DETACH -> { stopForeground(STOP_FOREGROUND_DETACH); stopSelf() }
            STOP -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        const val SHOW = "show"
        const val DETACH = "detach"
        const val STOP = "stop"
        @Volatile var pending: Notification? = null
        @Volatile var running = false
    }
}
