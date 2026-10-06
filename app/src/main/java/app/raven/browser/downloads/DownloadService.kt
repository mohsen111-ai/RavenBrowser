package app.raven.browser.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import app.raven.browser.MainActivity
import app.raven.browser.RavenApp
import app.raven.browser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps downloads running when you leave the app, with a progress notification. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val manager = (application as RavenApp).container.downloads
        if (intent?.action == ACTION_PAUSE_ALL) manager.active.forEach { manager.pause(it.id) }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, progressNotification(manager.active),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        scope.launch {
            manager.items.collectLatest {
                val active = manager.active
                if (active.isEmpty()) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    runCatching { NotificationManagerCompat.from(this@DownloadService).notify(NOTIFICATION_ID, progressNotification(active)) }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(active: List<DownloadItem>) = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
        .setSmallIcon(R.drawable.ic_stat_raven)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        // Tapping it while files download opens Raven's Downloads screen.
        .setContentIntent(openApp(this, showDownloads = true))
        .apply {
            val first = active.firstOrNull()
            if (first == null) {
                setContentTitle("Downloads")
            } else {
                val total = active.sumOf { maxOf(it.total, 0) }
                val done = active.sumOf { it.done }
                val speed = active.sumOf { it.speed }
                setContentTitle(if (active.size == 1) first.name else "${active.size} downloads")
                val sizeText = if (total > 0) "${Formatter.formatShortFileSize(this@DownloadService, done)} of ${Formatter.formatShortFileSize(this@DownloadService, total)}" else Formatter.formatShortFileSize(this@DownloadService, done)
                val waiting = active.all { it.waiting }
                // All of them finish when the slowest one does.
                val left = when {
                    waiting -> "Waiting for the server…"
                    active.size == 1 -> DownloadText.timeLeft(first)
                    active.any { it.total <= 0 } -> null
                    active.any { it.eta < 0 && !it.waiting } -> "Calculating…"
                    else -> DownloadText.left(active.maxOf { it.eta })
                }
                setContentText(listOfNotNull(sizeText, if (waiting) null else "${Formatter.formatShortFileSize(this@DownloadService, speed)}/s", left).joinToString(" · "))
                if (total > 0) setProgress(1000, (done * 1000 / total).toInt(), false) else setProgress(0, 0, true)
                addAction(0, "Pause", PendingIntent.getService(
                    this@DownloadService, 1, Intent(this@DownloadService, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
            }
        }
        .build()

    companion object {
        private const val CHANNEL_PROGRESS = "downloads"
        private const val CHANNEL_DONE = "downloads_done"
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_PAUSE_ALL = "pause_all"

        fun start(context: Context) {
            runCatching { context.startForegroundCompat(Intent(context, DownloadService::class.java)) }
        }

        fun notifyFinished(context: Context, item: DownloadItem) {
            ensureChannels(context)
            val manager = (context.applicationContext as RavenApp).container.downloads
            val open = PendingIntent.getActivity(
                context, item.id.hashCode(), manager.openIntent(item),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_raven)
                .setContentTitle("Download finished")
                .setContentText(
                    "${item.name} · ${Formatter.formatShortFileSize(context, item.done)}" +
                        if (item.activeMs > 0) " in ${DownloadText.duration(item.activeMs)}" else "",
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, "Open", open)
                .addAction(0, "Show in Downloads", openApp(context, showDownloads = true))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(item.id.hashCode(), n) }
        }

        private fun openApp(context: Context, showDownloads: Boolean = false): PendingIntent = PendingIntent.getActivity(
            context, if (showDownloads) 2 else 3,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_DOWNLOADS, showDownloads)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
