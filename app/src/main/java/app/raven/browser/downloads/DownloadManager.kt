package app.raven.browser.downloads

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.raven.browser.data.Database
import app.raven.browser.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class DownloadStatus { RUNNING, PAUSED, DONE, FAILED }

private const val TAG = "Raven"

private const val FREE = -1
private const val DONE = Int.MAX_VALUE
private const val STREAM = 0
private const val FETCHER = 1

/** A byte range of the file fetched over its own connection. */
data class Part(val start: Long, val end: Long, val done: Long) {
    val finished get() = start + done > end
}

data class DownloadItem(
    val id: String,
    val name: String,
    val url: String,
    val mime: String,
    val total: Long,
    val done: Long,
    val status: DownloadStatus,
    val target: String,
    val private: Boolean,
    val created: Long,
    val resumable: Boolean,
    val parts: List<Part> = emptyList(),
    val speed: Long = 0,
    val connections: Int = 1,
    val error: String? = null,
    /** Time spent actually downloading (pauses not counted), for "Finished in" and the average speed. */
    val activeMs: Long = 0,
    /** Seconds left, or -1 while there isn't enough to go on yet. Not saved. */
    val eta: Long = -1,
    /** Nothing is arriving because the server asked Raven to wait. Not saved. */
    val waiting: Boolean = false,
) {
    val progress: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f

    fun toJson(): String = JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("mime", mime); put("total", total); put("done", done)
        put("status", status.name); put("target", target); put("private", private); put("created", created)
        put("resumable", resumable); put("connections", connections); error?.let { put("error", it) }
        put("activeMs", activeMs)
        put("parts", JSONArray().apply { parts.forEach { put(JSONArray().put(it.start).put(it.end).put(it.done)) } })
    }.toString()

    companion object {
        fun fromJson(s: String): DownloadItem {
            val o = JSONObject(s)
            val parts = o.optJSONArray("parts")?.let { a ->
                (0 until a.length()).map { i -> a.getJSONArray(i).let { Part(it.getLong(0), it.getLong(1), it.getLong(2)) } }
            }.orEmpty()
            return DownloadItem(
                o.getString("id"), o.getString("name"), o.getString("url"), o.getString("mime"), o.getLong("total"), o.getLong("done"),
                DownloadStatus.valueOf(o.getString("status")), o.getString("target"), o.getBoolean("private"), o.getLong("created"),
                o.optBoolean("resumable"), parts, 0, o.optInt("connections", 1), o.optString("error").ifBlank { null },
                activeMs = o.optLong("activeMs"),
            )
        }
    }
}

/**
 * Downloads through Gecko's own network stack (same cookies, encrypted DNS and privacy settings as
 * the page). Large files on servers that allow it are split across several connections.
 */
class DownloadManager(private val context: Context, private val db: Database, private val settings: Settings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val counters = mutableMapOf<String, AtomicLong>()
    /** Connections of each download currently waiting because the server said it was busy. */
    private val waits = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

    private suspend fun waitFor(id: String, pause: suspend () -> Unit) {
        val n = waits.getOrPut(id) { AtomicInteger() }
        n.incrementAndGet()
        try { pause() } finally { n.decrementAndGet() }
    }
    lateinit var runtime: GeckoRuntime
    private val executor by lazy { GeckoWebExecutor(runtime) }

    init {
        scope.launch {
            _items.value = db.downloads().mapNotNull { runCatching { DownloadItem.fromJson(it) }.getOrNull() }
                .map { if (it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.PAUSED) else it }
        }
    }

    val active: List<DownloadItem> get() = _items.value.filter { it.status == DownloadStatus.RUNNING }

    fun start(response: WebResponse, private: Boolean): DownloadItem? {
        val headers = response.headers.entries.associate { it.key.lowercase() to it.value }
        val name = fileName(response.uri, headers["content-disposition"], headers["content-type"])
        val mime = headers["content-type"]?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        val encoded = headers["content-encoding"]?.let { it != "identity" } == true
        val total = if (encoded) -1 else headers["content-length"]?.toLongOrNull() ?: -1
        val ranges = headers["accept-ranges"]?.contains("bytes") == true && total > 0 && response.statusCode == 200
        val target = createTarget(name, mime) ?: run {
            response.body?.close()
            return null
        }
        val connections = if (ranges && total >= 4L * 1024 * 1024) settings.current.connections.coerceIn(1, 16) else 1
        Log.i(TAG, "download ${response.statusCode} size=$total ranges=$ranges connections=$connections ${response.uri}")
        val parts = if (ranges) split(total, connections) else emptyList()
        val item = DownloadItem(
            UUID.randomUUID().toString(), name, response.uri, mime, total, 0, DownloadStatus.RUNNING, target.toString(),
            private, System.currentTimeMillis(), resumable = ranges, parts = parts, connections = connections,
        )
        upsert(item)
        DownloadService.start(context)
        val body = response.body
        jobs[item.id] = scope.launch {
            if (connections > 1 || body == null) {
                runParts(item, body)
            } else {
                runSingle(item, body)
            }
        }
        return item
    }

    /** Saves a link, image or video the user long-pressed, using the page's cookies. */
    fun downloadUrl(url: String, private: Boolean, referrer: String?) {
        val request = WebRequest.Builder(url).apply { referrer?.let { referrer(it) } }.build()
        val flags = if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            executor.fetch(request, flags).accept({ r -> r?.let { start(it, private) } }, { })
        }
    }

    /** Saves a finished stream (for example "Save as PDF") straight into Downloads. */
    fun saveStream(name: String, mime: String, input: InputStream, private: Boolean, onDone: (Boolean) -> Unit) {
        scope.launch {
            val target = createTarget(name, mime)
            if (target == null) { withContext(Dispatchers.Main) { onDone(false) }; return@launch }
            val item = DownloadItem(UUID.randomUUID().toString(), name, "", mime, -1, 0, DownloadStatus.RUNNING, target.toString(), private, System.currentTimeMillis(), resumable = false)
            upsert(item)
            val ok = runCatching {
                var total = 0L
                openChannel(item).use { ch ->
                    input.use { inp ->
                        val buf = ByteArray(128 * 1024)
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            ch.writeFully(buf, n, total)
                            total += n
                        }
                    }
                }
                finish(item.id, total)
            }.isSuccess
            if (!ok) remove(item.id)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    fun pause(id: String) {
        jobs.remove(id)?.cancel()
        update(id) { if (it.status == DownloadStatus.RUNNING) it.copy(status = if (it.resumable) DownloadStatus.PAUSED else DownloadStatus.FAILED, speed = 0, eta = -1, waiting = false, error = if (it.resumable) null else "Can't pause this download") else it }
    }

    fun resume(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (!item.resumable) return
        val running = item.copy(status = DownloadStatus.RUNNING, error = null)
        upsert(running)
        DownloadService.start(context)
        jobs[id] = scope.launch { runParts(running) }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.DONE) deleteTarget(item)
        remove(id)
    }

    fun remove(id: String) {
        _items.value = _items.value.filter { it.id != id }
        scope.launch { db.deleteDownload(id) }
    }

    fun clearFinished() {
        val (done, rest) = _items.value.partition { it.status == DownloadStatus.DONE || it.status == DownloadStatus.FAILED }
        _items.value = rest
        scope.launch { done.forEach { db.deleteDownload(it.id) } }
    }

    fun clearList() {
        _items.value.filter { it.status == DownloadStatus.RUNNING }.forEach { pause(it.id) }
        _items.value = emptyList()
        scope.launch { db.clearDownloads() }
    }

    fun openIntent(item: DownloadItem): Intent {
        val uri = Uri.parse(item.target).let { u ->
            if (u.scheme == "file") FileProvider.getUriForFile(context, context.packageName + ".files", File(u.path!!)) else u
        }
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ------------------------------------------------------------------ workers

    private suspend fun runSingle(item: DownloadItem, body: InputStream) {
        val counter = AtomicLong(0).also { counters[item.id] = it }
        val ticker = startTicker(item.id, counter)
        try {
            openChannel(item).use { ch ->
                body.use { input ->
                    val buf = ByteArray(256 * 1024)
                    var pos = 0L
                    while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        ch.writeFully(buf, n, pos)
                        pos += n
                        counter.set(pos)
                    }
                }
            }
            finish(item.id, counter.get())
        } catch (e: Throwable) {
            fail(item.id, counter.get(), e)
        } finally {
            ticker.cancel()
        }
    }

    /**
     * Fetches the file in pieces, the way download managers do. The connection the page already
     * opened keeps going as the first piece; extra connections fetch the others. A server that
     * turns extra connections away (HTTP 429 or 503) is asked again after the wait it names, and a
     * piece no connection could get is continued by the first connection as it streams past, or
     * fetched on its own at the end. [first] is the page's own response body, at byte 0.
     */
    private suspend fun runParts(item: DownloadItem, first: InputStream? = null) {
        val progress = item.parts.map { AtomicLong(it.done) }
        val counter = AtomicLong(progress.sumOf { it.get() }).also { counters[item.id] = it }
        val ticker = startTicker(item.id, counter) { cur -> cur.copy(parts = cur.parts.mapIndexed { i, p -> p.copy(done = progress[i].get()) }) }
        var wholeFileOnly = false
        try {
            openChannel(item).use { ch ->
                val pieces = Pieces(item, ch, progress, counter)
                withContext(Dispatchers.IO) {
                    item.parts.indices.map { i ->
                        async { if (i == 0 && first != null) pieces.stream(first) else pieces.fetch(i, attempts = 3) }
                    }.awaitAll()
                    // Whatever is still missing: one piece at a time, with more patience.
                    for (i in item.parts.indices) if (!pieces.complete(i)) pieces.fetch(i, attempts = 6)
                }
                val missing = item.parts.indices.count { !pieces.complete(it) }
                if (missing > 0) {
                    wholeFileOnly = first == null && pieces.wholeFileOnly.get()
                    if (!wholeFileOnly) throw IOException("The server refused part of the file. Try again later.")
                }
            }
            if (!wholeFileOnly) finish(item.id, counter.get())
        } catch (e: Throwable) {
            runCatching { first?.close() }
            update(item.id) { it.copy(parts = it.parts.mapIndexed { i, p -> p.copy(done = progress[i].get()) }) }
            fail(item.id, counter.get(), e)
        } finally {
            ticker.cancel()
        }
        if (wholeFileOnly) {
            // Resuming, but the server now only sends the whole file: start over in one piece.
            Log.w(TAG, "server no longer sends pieces, downloading in one go: ${item.url}")
            val single = item.copy(parts = emptyList(), connections = 1, resumable = false, done = 0, speed = 0)
            upsert(single)
            runWhole(single)
        }
    }

    /** The pieces of one download and who is filling each: nobody, the first connection, or a piece fetcher. */
    private inner class Pieces(item: DownloadItem, val ch: FileChannel, val progress: List<AtomicLong>, val counter: AtomicLong) {
        private val id = item.id
        private val parts = item.parts
        private val url = item.url
        private val flags = if (item.private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        private val owner = parts.map { AtomicInteger(if (it.finished) DONE else FREE) }
        val wholeFileOnly = AtomicBoolean(false)

        private fun length(i: Int) = parts[i].end - parts[i].start + 1
        fun complete(i: Int) = progress[i].get() >= length(i)

        /** Reads the page's response from byte 0, carrying on into following pieces nobody has taken. */
        suspend fun stream(input: InputStream) {
            var i = 0
            owner[0].set(STREAM)
            try {
                input.use {
                    val buf = ByteArray(256 * 1024)
                    var pos = parts[0].start
                    while (currentCoroutineContext().isActive) {
                        val left = parts[i].end + 1 - pos
                        if (left <= 0) {
                            owner[i].set(DONE)
                            i++
                            if (i >= parts.size || !owner[i].compareAndSet(FREE, STREAM)) return
                            // Taking over a piece a fetcher started and dropped: write it again from its start.
                            counter.addAndGet(-progress[i].getAndSet(0))
                            Log.i(TAG, "first connection continues into piece $i")
                            continue
                        }
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) return
                        ch.writeFully(buf, n, pos)
                        pos += n
                        progress[i].addAndGet(n.toLong())
                        counter.addAndGet(n.toLong())
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "first connection dropped in piece $i: ${e.message}")
            } finally {
                if (i < parts.size && owner[i].get() == STREAM) owner[i].set(if (complete(i)) DONE else FREE)
            }
        }

        /** Fetches piece [i] over its own connection, waiting and retrying when the server says it's busy. */
        suspend fun fetch(i: Int, attempts: Int) {
            for (attempt in 0 until attempts) {
                if (complete(i) || owner[i].get() != FREE) return
                val from = parts[i].start + progress[i].get()
                val request = WebRequest.Builder(url)
                    .header("Range", "bytes=$from-${parts[i].end}")
                    .header("Accept-Encoding", "identity")
                    .cacheMode(WebRequest.CACHE_MODE_NO_STORE)
                    .build()
                val response = try {
                    onGeckoThread { executor.fetch(request, flags) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "piece $i: ${e.message}")
                    delay(backoff(attempt))
                    continue
                }
                val body = response.body
                when (response.statusCode) {
                    206 -> {
                        if (body == null || !owner[i].compareAndSet(FREE, FETCHER + i)) {
                            runCatching { body?.close() }
                            return
                        }
                        Log.i(TAG, "piece $i: own connection from byte $from")
                        try {
                            read(i, body, from)
                        } catch (e: IOException) {
                            Log.w(TAG, "piece $i dropped: ${e.message}")
                        } finally {
                            owner[i].set(if (complete(i)) DONE else FREE)
                        }
                    }
                    429, 503 -> {
                        runCatching { body?.close() }
                        val wait = response.headers.entries.firstOrNull { it.key.equals("Retry-After", true) }?.value?.trim()?.toLongOrNull()
                        Log.i(TAG, "piece $i: server busy (HTTP ${response.statusCode}), retry in ${wait ?: "a moment"}")
                        waitFor(id) { delay(wait?.coerceIn(1, 20)?.times(1000) ?: backoff(attempt)) }
                    }
                    else -> {
                        // 200 means the server sends only the whole file; others mean it won't serve this piece.
                        Log.w(TAG, "piece $i bytes=$from-${parts[i].end}: HTTP ${response.statusCode}")
                        runCatching { body?.close() }
                        if (response.statusCode == 200) wholeFileOnly.set(true)
                        return
                    }
                }
            }
        }

        private suspend fun read(i: Int, body: InputStream, from: Long) {
            body.use { input ->
                val buf = ByteArray(256 * 1024)
                var pos = from
                while (currentCoroutineContext().isActive) {
                    val left = parts[i].end + 1 - pos
                    if (left <= 0) return
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) return
                    ch.writeFully(buf, n, pos)
                    pos += n
                    progress[i].addAndGet(n.toLong())
                    counter.addAndGet(n.toLong())
                }
            }
        }

        private fun backoff(attempt: Int) = minOf(1000L shl attempt, 15_000L) + (0..500).random()
    }

    /** Fetches the whole file again over one connection and saves it from the start. */
    private suspend fun runWhole(item: DownloadItem) {
        val request = WebRequest.Builder(item.url).header("Accept-Encoding", "identity").cacheMode(WebRequest.CACHE_MODE_NO_STORE).build()
        val flags = if (item.private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        var last = "The server didn't answer"
        for (attempt in 0 until 4) {
            val response = try {
                onGeckoThread { executor.fetch(request, flags) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e.message ?: last
                delay(2000L shl attempt)
                continue
            }
            val body = response.body
            if (response.statusCode in 200..299 && body != null) {
                runSingle(item, body)
                return
            }
            runCatching { body?.close() }
            last = "The server answered HTTP ${response.statusCode}"
            if (response.statusCode != 429 && response.statusCode != 503) break
            waitFor(item.id) { delay(response.headers.entries.firstOrNull { it.key.equals("Retry-After", true) }?.value?.trim()?.toLongOrNull()?.coerceIn(1, 20)?.times(1000) ?: (2000L shl attempt)) }
        }
        fail(item.id, 0, IOException(last))
    }

    /**
     * Every half second: bytes so far, speed and time left. The speed is averaged over the last 8
     * seconds and the time left appears after 3 seconds of data, so it counts down steadily instead
     * of jumping with every burst or stall.
     */
    private fun startTicker(id: String, counter: AtomicLong, extra: ((DownloadItem) -> DownloadItem)? = null) = scope.launch {
        val started = SystemClock.elapsedRealtime()
        val samples = ArrayDeque<Pair<Long, Long>>().apply { addLast(started to counter.get()) }
        var last = started
        while (isActive) {
            delay(500)
            val now = SystemClock.elapsedRealtime()
            val bytes = counter.get()
            samples.addLast(now to bytes)
            while (samples.size > 2 && now - samples.first().first > 8_000) samples.removeFirst()
            val (t0, b0) = samples.first()
            val speed = if (now > t0) (bytes - b0) * 1000 / (now - t0) else 0
            // Next to nothing arriving, and either a server asked Raven to wait or it has gone quiet.
            val waiting = speed < 1024 && ((waits[id]?.get() ?: 0) > 0 || now - started > 10_000)
            val step = now - last
            last = now
            update(id, persist = false) { cur ->
                val left = if (cur.total > 0) (cur.total - bytes).coerceAtLeast(0) else -1
                val eta = if (left < 0 || now - started < 3_000 || speed <= 0) -1 else left / speed
                val next = cur.copy(done = bytes, speed = speed, eta = eta, waiting = waiting, activeMs = cur.activeMs + step)
                extra?.invoke(next) ?: next
            }
        }
    }

    private fun finish(id: String, bytes: Long) {
        jobs.remove(id)
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                context.contentResolver.update(Uri.parse(item.target), ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            }
        }
        update(id) { it.copy(status = DownloadStatus.DONE, done = bytes, total = if (it.total > 0) it.total else bytes, speed = 0, eta = -1, waiting = false) }
        DownloadService.notifyFinished(context, _items.value.first { it.id == id })
    }

    private fun fail(id: String, bytes: Long, e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) return
        Log.w(TAG, "download failed after $bytes bytes", e)
        jobs.remove(id)
        update(id) { it.copy(status = if (it.resumable) DownloadStatus.PAUSED else DownloadStatus.FAILED, done = bytes, speed = 0, eta = -1, waiting = false, error = e.message ?: "Download failed") }
    }

    // ------------------------------------------------------------------ state

    private fun upsert(item: DownloadItem) {
        _items.value = listOf(item) + _items.value.filter { it.id != item.id }
        if (!item.private) scope.launch { db.saveDownload(item.id, item.toJson(), item.created) }
    }

    private fun update(id: String, persist: Boolean = true, transform: (DownloadItem) -> DownloadItem) {
        var changed: DownloadItem? = null
        _items.value = _items.value.map { if (it.id == id) transform(it).also { n -> changed = n } else it }
        val c = changed ?: return
        if (persist && !c.private) scope.launch { db.saveDownload(c.id, c.toJson(), c.created) }
    }

    // ------------------------------------------------------------------ files

    private fun createTarget(name: String, mime: String): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            Uri.fromFile(uniqueFile(dir, name))
        }
    }.getOrNull()

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (f.exists()) f = File(dir, "$base (${i++})$ext")
        return f
    }

    private fun openChannel(item: DownloadItem): FileChannel {
        val uri = Uri.parse(item.target)
        return if (uri.scheme == "file") {
            java.io.RandomAccessFile(File(uri.path!!), "rw").channel
        } else {
            // The stream keeps the descriptor alive and closes it with the channel. A bare
            // FileOutputStream(pfd.fileDescriptor) let the descriptor be garbage-collected and
            // closed mid-download ("Bad file descriptor").
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: error("Can't write to Downloads")
            ParcelFileDescriptor.AutoCloseOutputStream(pfd).channel
        }
    }

    /** Positional writes can be partial; keep going until the whole chunk is on disk. */
    private fun FileChannel.writeFully(buf: ByteArray, length: Int, position: Long) {
        val b = ByteBuffer.wrap(buf, 0, length)
        var at = position
        while (b.hasRemaining()) at += write(b, at)
    }

    private fun deleteTarget(item: DownloadItem) {
        val uri = Uri.parse(item.target)
        runCatching { if (uri.scheme == "file") File(uri.path!!).delete() else context.contentResolver.delete(uri, null, null) }
    }

    private fun split(total: Long, n: Int): List<Part> {
        val size = total / n
        return (0 until n).map { i ->
            val start = i * size
            val end = if (i == n - 1) total - 1 else start + size - 1
            Part(start, end, 0)
        }
    }

    companion object {
        fun fileName(url: String, disposition: String?, contentType: String?): String {
            val fromHeader = disposition?.let {
                Regex("filename\\*=(?:UTF-8|utf-8)''([^;]+)").find(it)?.groupValues?.get(1)?.let(Uri::decode)
                    ?: Regex("filename=\"?([^\";]+)\"?").find(it)?.groupValues?.get(1)
            }
            var name = (fromHeader ?: Uri.parse(url).lastPathSegment ?: "download").trim()
            name = name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").ifBlank { "download" }
            if (!name.contains('.')) {
                val ext = contentType?.substringBefore(';')?.trim()?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                if (ext != null) name += ".$ext"
            }
            return name.take(120)
        }
    }
}

/** Starts a Gecko call on the main thread, where GeckoView objects live, and waits for its result. */
suspend fun <T> onGeckoThread(start: () -> GeckoResult<T>): T = suspendCancellableCoroutine { cont ->
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            start().accept(
                { v -> if (cont.isActive) cont.resume(v as T) },
                { e -> if (cont.isActive) cont.resumeWithException(e ?: IllegalStateException("failed")) },
            )
        } catch (e: Throwable) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }
}

fun Context.startForegroundCompat(intent: Intent) = ContextCompat.startForegroundService(this, intent)
