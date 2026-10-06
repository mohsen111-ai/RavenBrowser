package app.raven.browser.ui.sky

import android.app.Application
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.raven.browser.R
import app.raven.browser.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a wallpaper's moon is, in the 390 × 844 frame the wallpapers were drawn in. */
class Moon(val x: Float, val y: Float, val r: Float)

class Wallpaper(val id: String, val name: String, val res: Int?, val moon: Moon? = null)

object Wallpapers {
    val all = listOf(
        Wallpaper("moonrise", "Moonrise", R.drawable.wall_moonrise, Moon(246f, 452f, 96f)),
        Wallpaper("pines", "Pines", R.drawable.wall_pines, Moon(196f, 446f, 72f)),
        Wallpaper("feather", "Feather", R.drawable.wall_feather),
        Wallpaper("rooftops", "Rooftops", R.drawable.wall_rooftops, Moon(292f, 330f, 54f)),
        Wallpaper("aurora", "Aurora", R.drawable.wall_aurora),
        Wallpaper("water", "Still water", R.drawable.wall_water, Moon(195f, 300f, 50f)),
        Wallpaper("snow", "Snow", R.drawable.wall_snow, Moon(92f, 330f, 30f)),
        Wallpaper("corvus", "Corvus", R.drawable.wall_corvus),
        Wallpaper("clouds", "Clouds", R.drawable.wall_clouds, Moon(236f, 392f, 68f)),
        Wallpaper("flight", "Night flight", R.drawable.wall_flight, Moon(300f, 338f, 40f)),
        Wallpaper("crescent", "Crescent", R.drawable.wall_crescent, Moon(196f, 400f, 118f)),
        Wallpaper("none", "Plain night", null),
    )

    /** Private tabs: the moon in eclipse. Never in the rotation. */
    val eclipse = Wallpaper("eclipse", "Eclipse", R.drawable.wall_eclipse, Moon(195f, 380f, 98f))

    fun byId(id: String): Wallpaper = all.firstOrNull { it.id == id } ?: all[0]
}

/**
 * The home screen's wallpaper. Like Brave's backgrounds, a new one each time Raven opens: they take turns in a
 * shuffled order, so you see every one before any comes back, and never the same one twice in a row.
 * Only the wallpaper on screen is kept in memory at full size.
 */
class Sky(private val app: Application, private val settings: Settings) {
    private val sp = app.getSharedPreferences("sky", 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _current = MutableStateFlow(pick())
    val current: StateFlow<Wallpaper> = _current.asStateFlow()

    private val _image = MutableStateFlow<ImageBitmap?>(null)
    /** The current wallpaper, decoded; null while it loads or for the plain night. */
    val image: StateFlow<ImageBitmap?> = _image.asStateFlow()

    private val _eclipse = MutableStateFlow<ImageBitmap?>(null)
    val eclipse: StateFlow<ImageBitmap?> = _eclipse.asStateFlow()

    private val thumbs = LruCache<String, ImageBitmap>(16)

    init { load(_current.value) }

    /** Raven was opened again: the next wallpaper's turn (or the chosen one, with the rotation off). */
    fun next() = show(pick())

    /** Settings changed: keep the wallpaper unless it's no longer allowed. */
    fun settingsChanged() {
        val p = settings.current
        val now = _current.value
        val keep = if (p.wallpaperRotate) now.id !in p.wallpapersOff else now.id == p.wallpaper
        if (!keep) show(if (p.wallpaperRotate) pick() else Wallpapers.byId(p.wallpaper))
    }

    /** Shows this one now (a tap in Settings). */
    fun show(w: Wallpaper) {
        if (w.id == _current.value.id && (_image.value != null || w.res == null)) return
        _current.value = w
        load(w)
    }

    fun loadEclipse() {
        if (_eclipse.value != null) return
        scope.launch { _eclipse.value = decode(Wallpapers.eclipse.res!!, 1) }
    }

    /** A small copy for the Settings grid. */
    suspend fun thumbnail(w: Wallpaper): ImageBitmap? {
        val res = w.res ?: return null
        thumbs.get(w.id)?.let { return it }
        return decode(res, 8)?.also { thumbs.put(w.id, it) }
    }

    private fun pick(): Wallpaper {
        val p = settings.current
        if (!p.wallpaperRotate) return Wallpapers.byId(p.wallpaper)
        val pool = Wallpapers.all.filter { it.id !in p.wallpapersOff }.ifEmpty { listOf(Wallpapers.all[0]) }
        val ids = pool.map { it.id }
        val last = sp.getString("last", null)
        var bag = sp.getString("bag", "")!!.split(',').filter { it in ids }
        if (bag.isEmpty()) {
            bag = ids.shuffled()
            if (bag.size > 1 && bag.first() == last) bag = bag.drop(1) + bag.first()
        }
        val id = bag.first()
        sp.edit().putString("bag", bag.drop(1).joinToString(",")).putString("last", id).apply()
        return Wallpapers.byId(id)
    }

    private fun load(w: Wallpaper) {
        val res = w.res
        if (res == null) { _image.value = null; return }
        scope.launch {
            val bmp = decode(res, 1)
            // Another wallpaper may have been picked meanwhile.
            if (_current.value.id == w.id) _image.value = bmp
        }
    }

    private suspend fun decode(res: Int, sample: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val o = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
            BitmapFactory.decodeResource(app.resources, res, o)?.asImageBitmap()
        }.getOrNull()
    }
}
