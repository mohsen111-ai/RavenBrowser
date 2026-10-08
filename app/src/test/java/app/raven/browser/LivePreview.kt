package app.raven.browser

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import app.raven.browser.ui.sky.Live
import app.raven.browser.ui.sky.LiveCanvas
import app.raven.browser.ui.sky.SkyMap
import app.raven.browser.ui.sky.Wallpapers
import app.raven.browser.ui.sky.drawLive
import app.raven.browser.ui.sky.stormFlash
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Frames of each live wallpaper, drawn by the same code as on the phone, for checking how they move (they're put
 * together into short moving pictures on the computer). Run: ./gradlew :app:testDebugUnitTest --tests '*LivePreview*'
 * → app/build/live/<wallpaper>/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = ShotsApp::class)
class LivePreview {
    private val w = 270
    private val h = 584
    private val fps = 10

    private fun frames(id: String, from: Float, seconds: Float) {
        val wall = Wallpapers.byId(id)
        val live = wall.live!!
        val app = ApplicationProvider.getApplicationContext<Application>()
        val bg = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(app.resources, wall.res!!), w, h, true).asImageBitmap()
        val dir = File("build/live/$id").apply { deleteRecursively(); mkdirs() }
        val c = LiveCanvas()
        val n = (seconds * fps).toInt()
        for (i in 0 until n) {
            val t = from + i.toFloat() / fps
            val bmp = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
                drawImage(bg, dstSize = IntSize(w, h))
                drawLive(live, SkyMap(w.toFloat(), h.toFloat()), t, c)
            }
            File(dir, "f%03d.png".format(i)).outputStream().use { bmp.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Its still moment, as it stands with Reduce motion or Battery Saver.
        val still = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(still), Size(w.toFloat(), h.toFloat())) {
            drawImage(bg, dstSize = IntSize(w, h))
            drawLive(live, SkyMap(w.toFloat(), h.toFloat()), live.still, c)
        }
        File(dir, "still.png").outputStream().use { still.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun meteors() = frames("meteors", 0f, 9f)
    @Test fun ravenMoon() = frames("ravenmoon", 0f, 11f)
    @Test fun storm() = frames("storm", stormFlash(1) - 2.5f, 5f)
    @Test fun fireflies() = frames("fireflies", 0f, 6f)
    @Test fun campfire() = frames("campfire", 0f, 4f)
    @Test fun sea() = frames("sea", 0f, 5f)
    @Test fun snowfall() = frames("snowfall", 0f, 6f)
    @Test fun candle() = frames("candle", 0f, 4f)
    @Test fun wind() = frames("wind", 0f, 6f)
    @Test fun circling() = frames("circling", 0f, 8f)
}
