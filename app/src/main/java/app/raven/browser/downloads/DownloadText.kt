package app.raven.browser.downloads

/** How a download's progress reads on screen and in the notification. */
object DownloadText {
    /** "About 3 min left", "Waiting for the server…", "Calculating…", or null when there's nothing to say. */
    fun timeLeft(item: DownloadItem): String? = when {
        item.status != DownloadStatus.RUNNING -> null
        item.waiting -> "Waiting for the server…"
        item.total <= 0 -> null
        item.eta < 0 -> "Calculating…"
        else -> left(item.eta)
    }

    /** Rounded so it reads calmly: seconds in 5s steps, then minutes, then quarter hours. */
    fun left(seconds: Long): String = when {
        seconds < 5 -> "A few seconds left"
        seconds < 60 -> "${(seconds + 4) / 5 * 5} s left"
        seconds < 600 -> "About ${(seconds + 30) / 60} min left"
        seconds < 3600 -> "About ${(seconds + 150) / 300 * 5} min left"
        else -> {
            val hours = seconds / 3600
            val quarter = ((seconds % 3600) + 450) / 900 * 15
            when (quarter) {
                0L -> "About $hours h left"
                60L -> "About ${hours + 1} h left"
                else -> "About $hours h $quarter min left"
            }
        }
    }

    /** "58 s", "3 min 12 s", "1 h 4 min". */
    fun duration(ms: Long): String {
        val s = (ms + 500) / 1000
        return when {
            s < 60 -> "$s s"
            s < 3600 -> if (s % 60 == 0L) "${s / 60} min" else "${s / 60} min ${s % 60} s"
            else -> "${s / 3600} h ${(s % 3600) / 60} min"
        }
    }

    /** Average speed over the time actually spent downloading, in bytes per second. */
    fun averageSpeed(item: DownloadItem): Long = if (item.activeMs > 0) item.done * 1000 / item.activeMs else 0
}
