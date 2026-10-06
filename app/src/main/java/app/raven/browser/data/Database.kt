package app.raven.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class Visit(val id: Long, val url: String, val title: String, val host: String, val time: Long)
class TopSite(val host: String, val url: String, val title: String)
class Bookmark(val id: Long, val url: String, val title: String, val host: String, val folder: String, val created: Long)

/**
 * History, bookmarks and download records in a small SQLite database. Private tabs never write history here. Each
 * visit belongs to a profile ("" is the first one); bookmarks and downloads are shared by all.
 */
class Database(context: Context) : SQLiteOpenHelper(context, "raven.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', host TEXT NOT NULL, time INTEGER NOT NULL, profile TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX visits_time ON visits(time)")
        db.execSQL("CREATE INDEX visits_url ON visits(url)")
        db.execSQL("CREATE TABLE downloads (id TEXT PRIMARY KEY, json TEXT NOT NULL, created INTEGER NOT NULL)")
        createBookmarks(db)
    }

    // 2: bookmarks. 3: profiles (the history so far is the first profile's). History and downloads are kept.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createBookmarks(db)
        if (oldVersion < 3) db.execSQL("ALTER TABLE visits ADD COLUMN profile TEXT NOT NULL DEFAULT ''")
    }

    private fun createBookmarks(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, title TEXT NOT NULL DEFAULT '', host TEXT NOT NULL, folder TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)")
    }

    /** Bumped on every history change so screens can refresh. */
    private val _version = MutableStateFlow(0)
    val historyVersion: StateFlow<Int> = _version

    suspend fun recordVisit(url: String, title: String, profile: String = "") = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext
        val host = Uri.parse(url).host?.removePrefix("www.") ?: return@withContext
        val now = System.currentTimeMillis()
        val db = writableDatabase
        // Revisiting the same page within 30 minutes refreshes the existing entry instead of adding a new one.
        db.rawQuery("SELECT id FROM visits WHERE url = ? AND profile = ? AND time > ? ORDER BY time DESC LIMIT 1", arrayOf(url, profile, (now - 30 * 60_000).toString())).use { c ->
            if (c.moveToFirst()) {
                db.update("visits", ContentValues().apply { put("time", now); if (title.isNotBlank()) put("title", title) }, "id = ?", arrayOf(c.getLong(0).toString()))
            } else {
                db.insert("visits", null, ContentValues().apply { put("url", url); put("title", title); put("host", host); put("time", now); put("profile", profile) })
            }
        }
        _version.value++
    }

    suspend fun updateTitle(url: String, title: String, profile: String = "") = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext
        writableDatabase.execSQL(
            "UPDATE visits SET title = ? WHERE id = (SELECT id FROM visits WHERE url = ? AND profile = ? ORDER BY time DESC LIMIT 1)",
            arrayOf(title, url, profile),
        )
        _version.value++
    }

    suspend fun history(query: String = "", since: Long = 0, limit: Int = 500, profile: String = ""): List<Visit> = withContext(Dispatchers.IO) {
        val q = "%${query.trim()}%"
        readableDatabase.rawQuery(
            "SELECT id, url, title, host, time FROM visits WHERE profile = ? AND time >= ? AND (url LIKE ? OR title LIKE ?) ORDER BY time DESC LIMIT ?",
            arrayOf(profile, since.toString(), q, q, limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Visit(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))) } }
    }

    /** Address-bar suggestions: pages whose address or title matches, most visited first. */
    suspend fun suggest(text: String, limit: Int = 6, profile: String = ""): List<Visit> = withContext(Dispatchers.IO) {
        val t = text.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        if (t.isEmpty()) return@withContext emptyList()
        readableDatabase.rawQuery(
            """SELECT MAX(id), url, title, host, MAX(time), COUNT(*) AS n FROM visits
               WHERE profile = ? AND (host LIKE ? OR url LIKE ? OR title LIKE ?)
               GROUP BY url ORDER BY (host LIKE ?) DESC, n DESC, MAX(time) DESC LIMIT ?""",
            arrayOf(profile, "$t%", "%$t%", "%$t%", "$t%", limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Visit(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))) } }
    }

    suspend fun topSites(limit: Int, exclude: Collection<String>, profile: String = ""): List<TopSite> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT host, COUNT(*) AS n, MAX(time) FROM visits WHERE profile = ? AND time > ? GROUP BY host ORDER BY n DESC LIMIT ?",
            arrayOf(profile, (System.currentTimeMillis() - 60L * 86_400_000).toString(), (limit + exclude.size).toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val host = c.getString(0)
                    if (host !in exclude) add(TopSite(host, "https://$host/", host))
                }
            }.take(limit)
        }
    }

    suspend fun deleteVisit(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("visits", "id = ?", arrayOf(id.toString()))
        _version.value++
    }

    /** [profile]: only that profile's history; null for everyone's (Clean slate). */
    suspend fun clearHistory(since: Long = 0, profile: String? = null) = withContext(Dispatchers.IO) {
        if (profile == null) writableDatabase.delete("visits", "time >= ?", arrayOf(since.toString()))
        else writableDatabase.delete("visits", "time >= ? AND profile = ?", arrayOf(since.toString(), profile))
        _version.value++
    }

    // Download records (the download manager keeps their details as JSON).
    suspend fun saveDownload(id: String, json: String, created: Long) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            "downloads", null,
            ContentValues().apply { put("id", id); put("json", json); put("created", created) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    suspend fun deleteDownload(id: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("downloads", "id = ?", arrayOf(id))
    }

    suspend fun clearDownloads() = withContext(Dispatchers.IO) { writableDatabase.delete("downloads", null, null) }

    suspend fun downloads(): List<String> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT json FROM downloads ORDER BY created DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    // ------------------------------------------------------------------ bookmarks

    private val _bookmarked = MutableStateFlow<Set<String>>(emptySet())
    /** Every bookmarked address, so the menu can show at once whether the page is saved. */
    val bookmarked: StateFlow<Set<String>> = _bookmarked
    private val _bookmarksVersion = MutableStateFlow(0)
    val bookmarksVersion: StateFlow<Int> = _bookmarksVersion

    private fun bookmarksChanged() {
        _bookmarked.value = readableDatabase.rawQuery("SELECT url FROM bookmarks", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        _bookmarksVersion.value++
    }

    suspend fun loadBookmarked() = withContext(Dispatchers.IO) { bookmarksChanged() }

    suspend fun addBookmark(url: String, title: String, folder: String = ""): Unit = withContext(Dispatchers.IO) {
        val host = Uri.parse(url).host?.removePrefix("www.") ?: url
        writableDatabase.insertWithOnConflict(
            "bookmarks", null,
            ContentValues().apply { put("url", url); put("title", title.ifBlank { host }); put("host", host); put("folder", folder); put("created", System.currentTimeMillis()) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        bookmarksChanged()
    }

    suspend fun removeBookmark(url: String): Unit = withContext(Dispatchers.IO) {
        writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
        bookmarksChanged()
    }

    /** Puts a removed bookmark back where it was (Undo). */
    suspend fun restoreBookmark(b: Bookmark): Unit = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            "bookmarks", null,
            ContentValues().apply { put("url", b.url); put("title", b.title); put("host", b.host); put("folder", b.folder); put("created", b.created) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        bookmarksChanged()
    }

    suspend fun editBookmark(url: String, title: String? = null, folder: String? = null): Unit = withContext(Dispatchers.IO) {
        val v = ContentValues().apply { title?.let { put("title", it) }; folder?.let { put("folder", it) } }
        if (v.size() > 0) writableDatabase.update("bookmarks", v, "url = ?", arrayOf(url))
        bookmarksChanged()
    }

    suspend fun bookmark(url: String): Bookmark? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT id, url, title, host, folder, created FROM bookmarks WHERE url = ?", arrayOf(url)).use { c ->
            if (c.moveToFirst()) Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5)) else null
        }
    }

    /** Newest first; [folder] null for all of them. */
    suspend fun bookmarks(query: String = "", folder: String? = null, limit: Int = 2000): List<Bookmark> = withContext(Dispatchers.IO) {
        val q = "%${query.trim()}%"
        val sql = "SELECT id, url, title, host, folder, created FROM bookmarks WHERE (url LIKE ? OR title LIKE ?)" +
            (if (folder != null) " AND folder = ?" else "") + " ORDER BY created DESC LIMIT ?"
        val args = listOfNotNull(q, q, folder, limit.toString()).toTypedArray()
        readableDatabase.rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5))) }
        }
    }

    /** Folder names with how many bookmarks each holds, biggest first. */
    suspend fun bookmarkFolders(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT folder, COUNT(*) AS n FROM bookmarks WHERE folder != '' GROUP BY folder ORDER BY n DESC, folder", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) }
        }
    }

    /** Address-bar suggestions from bookmarks. */
    suspend fun suggestBookmarks(text: String, limit: Int = 3): List<Bookmark> = withContext(Dispatchers.IO) {
        val t = text.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        if (t.isEmpty()) return@withContext emptyList()
        readableDatabase.rawQuery(
            "SELECT id, url, title, host, folder, created FROM bookmarks WHERE host LIKE ? OR url LIKE ? OR title LIKE ? ORDER BY (host LIKE ?) DESC, created DESC LIMIT ?",
            arrayOf("$t%", "%$t%", "%$t%", "$t%", limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5))) } }
    }
}
