package app.raven.browser.ui.sky

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the wallpaper behind this element as if it filled the whole window, so the address bar and the page
 * under it show one continuous sky. With [moving], the moon breathes and a few stars twinkle.
 */
@Composable
fun Modifier.nightSky(
    image: ImageBitmap?,
    wallpaper: Wallpaper,
    ground: Color,
    moving: Boolean,
    private: Boolean = false,
): Modifier {
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    // A new wallpaper fades in over the last one.
    var shown by remember { mutableStateOf(image) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(image) {
        if (image === shown) return@LaunchedEffect
        previous = shown
        shown = image
        fade.snapTo(0f)
        fade.animateTo(1f, tween(700))
        previous = null
    }
    val clock: State<Float>? = if (moving) {
        rememberInfiniteTransition(label = "sky").animateFloat(
            0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "skyClock",
        )
    } else null
    val stars = remember(wallpaper.id) { twinkles(wallpaper) }

    return this
        .clipToBounds()
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            drawRect(ground)
            val root = view.rootView
            val winW = root.width.toFloat().coerceAtLeast(size.width)
            val winH = root.height.toFloat().coerceAtLeast(size.height)
            translate(-origin.x, -origin.y) {
                val p = previous
                if (p != null) drawWall(p, winW, winH, 1f)
                shown?.let { drawWall(it, winW, winH, if (p != null) fade.value else 1f) }
                val t = clock?.value
                if (t != null && shown != null) {
                    val map = SkyMap(winW, winH)
                    wallpaper.moon?.let { breathe(map, it, t, private) }
                    stars.forEach { s -> twinkle(map, s, t) }
                }
            }
        }
}

/** The wallpaper frame (390 × 844, filled and centred the way the image is) mapped onto the window. */
private class SkyMap(winW: Float, winH: Float) {
    private val scale = max(winW / 390f, winH / 844f)
    private val left = (winW - 390f * scale) / 2f
    private val top = (winH - 844f * scale) / 2f
    fun at(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
    fun len(d: Float) = d * scale
}

private fun DrawScope.drawWall(image: ImageBitmap, winW: Float, winH: Float, alpha: Float) {
    val scale = max(winW / image.width, winH / image.height)
    val w = (image.width * scale).toInt()
    val h = (image.height * scale).toInt()
    drawImage(
        image,
        dstOffset = IntOffset(((winW - w) / 2f).toInt(), ((winH - h) / 2f).toInt()),
        dstSize = IntSize(w, h),
        alpha = alpha,
    )
}

/** The moon's glow swells and fades, slowly (twice in the 12 second cycle). */
private fun DrawScope.breathe(map: SkyMap, moon: Moon, t: Float, private: Boolean) {
    val b = 0.5f - 0.5f * cos(t * 4f * PI.toFloat())
    val center = map.at(moon.x, moon.y)
    val r = map.len(moon.r) * (1.55f + 0.1f * b)
    val glow = if (private) Color(0xFF9A8CFF) else Color(0xFFC7CCD8)
    val a = if (private) 0.10f + 0.16f * b else 0.05f + 0.10f * b
    drawCircle(
        Brush.radialGradient(
            0f to glow.copy(alpha = 0f), 0.55f to glow.copy(alpha = a), 1f to glow.copy(alpha = 0f),
            center = center, radius = r,
        ),
        radius = r, center = center,
    )
}

private class Twinkle(val x: Float, val y: Float, val size: Float, val speed: Int, val phase: Float)

private fun twinkles(w: Wallpaper): List<Twinkle> {
    val r = Random(w.id.hashCode())
    val out = mutableListOf<Twinkle>()
    while (out.size < 6) {
        val x = 20f + r.nextFloat() * 350f
        val y = 100f + r.nextFloat() * 340f
        val m = w.moon
        // Not on the moon.
        if (m != null && (x - m.x) * (x - m.x) + (y - m.y) * (y - m.y) < (m.r + 24f) * (m.r + 24f)) continue
        out += Twinkle(x, y, 1.1f + r.nextFloat() * 0.8f, 2 + r.nextInt(3), r.nextFloat())
    }
    return out
}

private fun DrawScope.twinkle(map: SkyMap, s: Twinkle, t: Float) {
    val a = 0.5f + 0.5f * sin((t * s.speed + s.phase) * 2f * PI.toFloat())
    val c = map.at(s.x, s.y)
    val r = map.len(s.size)
    drawCircle(Color.White.copy(alpha = 0.15f + 0.8f * a), r, c)
    drawCircle(Color.White.copy(alpha = 0.18f * a), r * 3.2f, c)
}
