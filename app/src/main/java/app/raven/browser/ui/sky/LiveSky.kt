package app.raven.browser.ui.sky

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The live wallpapers: what moves over each one's background image, drawn in code (no video). Every place here is in
 * the 390 × 844 frame the wallpapers are drawn in, the same numbers as in the backgrounds (the fire, the candle, the
 * moon...). [t] is in seconds; the same [t] always draws the same picture, so a wallpaper standing still (Reduce
 * motion, Battery Saver) is simply one moment of it.
 */
enum class Live(val still: Float) {
    METEORS(2.75f), RAVEN_MOON(7.3f), STORM(17.0f), FIREFLIES(3f), CAMPFIRE(3f), SEA(3f), SNOWFALL(5f), CANDLE(3f),
    WIND(3f), CIRCLING(4f),
}

/** One path, used again for every shape of a frame, so drawing makes no garbage. */
internal class LiveCanvas {
    val path = Path()
}

internal fun DrawScope.drawLive(live: Live, map: SkyMap, t: Float, c: LiveCanvas) {
    when (live) {
        Live.METEORS -> meteors(map, t)
        Live.RAVEN_MOON -> ravenMoon(map, t, c)
        Live.STORM -> storm(map, t, c)
        Live.FIREFLIES -> fireflies(map, t)
        Live.CAMPFIRE -> campfire(map, t, c)
        Live.SEA -> sea(map, t)
        Live.SNOWFALL -> snowfall(map, t)
        Live.CANDLE -> candle(map, t, c)
        Live.WIND -> wind(map, t, c)
        Live.CIRCLING -> circling(map, t, c)
    }
}

// ------------------------------------------------------------------------------------------------ helpers

/** A number in [0, 1) that's always the same for the same [n]: where each star, drop or spark goes. */
private fun rnd(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

/** Smooth wobbling between about -1 and 1 (a few waves added up), for flames and wind. */
private fun wobble(t: Float, seed: Int): Float =
    0.5f * sin(t * 1.7f + seed * 1.3f) + 0.3f * sin(t * 3.9f + seed * 2.1f) + 0.2f * sin(t * 7.3f + seed * 0.7f)

private const val TAU = (2 * PI).toFloat()

/**
 * A raven seen from the side, facing right (or left), its wing at [flap]: -1 down, 0 level, 1 up (a glide holds it in a
 * shallow V, about 0.4). [x], [y]: its middle.
 */
private fun DrawScope.flyingRaven(map: SkyMap, c: LiveCanvas, x: Float, y: Float, scale: Float, flap: Float, left: Boolean, color: Color) {
    val p = c.path
    val d = if (left) -1f else 1f
    fun at(px: Float, py: Float): Offset = map.at(x + px * scale * d, y + py * scale)
    p.reset()
    // Body: the heavy beak, a big head, the back, the wedge of the tail, the belly.
    RAVEN_BODY.forEachIndexed { i, (px, py) -> val o = at(px, py); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
    p.close()
    // The near wing, broad at the body, out to a fingered tip, up or down with the beat.
    val tipY = -26f * flap
    val tipX = -6f - 5f * abs(flap)
    val sign = if (flap >= 0f) 1f else -1f
    val front = at(8f, -1.5f)
    p.moveTo(front.x, front.y)
    val wrist = at(6f, tipY * 0.55f - 2f * sign)
    val tip = at(tipX, tipY)
    p.quadraticTo(wrist.x, wrist.y, tip.x, tip.y)
    for (k in 1..4) {
        val a = at(tipX - 1.2f * k + 1f, tipY + sign * 3.2f * k)
        val b = at(tipX - 1.2f * k + 3.2f, tipY + sign * (3.2f * k - 0.6f))
        p.lineTo(a.x, a.y)
        p.lineTo(b.x, b.y)
    }
    val trail = at(-6f, tipY * 0.3f + sign * 2f)
    val root = at(-10f, 0f)
    p.quadraticTo(trail.x, trail.y, root.x, root.y)
    p.close()
    drawPath(p, color)
}

private val RAVEN_BODY = listOf(
    24f to 1f, 17f to -1.6f, 15f to -4f, 12f to -5f, 8.5f to -4.6f, 0f to -4.6f, -10f to -3.2f, -21f to -5.8f,
    -24.5f to 0.6f, -22f to 4f, -10f to 3.4f, 0f to 5.2f, 9f to 4.8f, 14f to 2.8f, 17f to 2f,
)

// ------------------------------------------------------------------------------------------------ the wallpapers

/** Shooting stars: now and then a streak across the upper sky, fading as it goes. */
private fun DrawScope.meteors(map: SkyMap, t: Float) {
    val slot = 4.2f
    for (back in 0..1) {
        val n = floor(t / slot).toInt() - back
        if (rnd(n * 7) > 0.8f) continue
        val start = n * slot + rnd(n * 7 + 1) * 2.2f
        val p = (t - start) / 1.0f
        if (p < 0f || p > 1f) continue
        val x0 = 120f + rnd(n * 7 + 2) * 260f
        val y0 = 40f + rnd(n * 7 + 3) * 280f
        val ang = (205f + rnd(n * 7 + 4) * 30f) * PI.toFloat() / 180f
        val len = 70f + rnd(n * 7 + 5) * 70f
        val headD = len * 1.6f * p
        val tail = len * min(1f, p * 3f) * (1f - p * 0.4f)
        val hx = x0 + cos(ang) * headD
        val hy = y0 - sin(ang) * headD
        val tx = hx - cos(ang) * tail
        val ty = hy + sin(ang) * tail
        val a = sin(p * PI.toFloat())
        val head = map.at(hx, hy)
        val tl = map.at(tx, ty)
        drawLine(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.9f * a)), start = tl, end = head),
            tl, head, strokeWidth = map.len(1.3f), cap = StrokeCap.Round,
        )
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * a), Color.Transparent), center = head, radius = map.len(5f)), map.len(5f), head)
    }
}

/** A raven crosses the moon, wings beating, then the sky is still for a while. */
private fun DrawScope.ravenMoon(map: SkyMap, t: Float, c: LiveCanvas) {
    val period = 17f
    val local = t % period
    val cross = 10.5f
    if (local > cross) return
    val p = local / cross
    val x = -60f + p * 520f
    val y = 404f - 30f * p + 8f * sin(p * TAU)
    // A few beats, then a glide, then beats again.
    val beating = sin(local * 0.9f) > -0.4f
    val flap = if (beating) 0.1f + 0.9f * sin(local * TAU * 1.5f) else 0.42f + 0.05f * sin(local * 3f)
    flyingRaven(map, c, x, y, 1.45f, flap, left = false, color = Color(0xFF05070D))
}

/** Rain all the time; now and then lightning lights the clouds, once or twice, with a bolt. */
private fun DrawScope.storm(map: SkyMap, t: Float, c: LiveCanvas) {
    val drop = Color(0xFFAEB8CC)
    for (i in 0 until 170) {
        val speed = 560f + rnd(i * 3) * 320f
        val len = 12f + rnd(i * 3 + 1) * 12f
        val y = ((rnd(i * 3 + 2) * 900f + speed * t) % 900f) - 30f
        val x = ((rnd(i * 5 + 7) * 470f - y * 0.22f) % 470f + 470f) % 470f - 40f
        val a = map.at(x, y)
        val b = map.at(x - len * 0.22f, y + len)
        drawLine(drop.copy(alpha = 0.14f + 0.2f * rnd(i * 11)), a, b, strokeWidth = map.len(0.7f), cap = StrokeCap.Round)
    }
    val n = floor(t / STORM_SLOT).toInt()
    val local = t - stormFlash(n)
    if (local < 0f || local > 0.9f) return
    val light = when {
        local < 0.07f -> 1f
        local < 0.15f -> 0.25f
        local < 0.24f -> 0.85f
        else -> 0.85f * (1f - (local - 0.24f) / 0.66f)
    }
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFFDCE3F2).copy(alpha = 0.32f * light), Color(0xFFDCE3F2).copy(alpha = 0.05f * light), Color.Transparent), startY = 0f, endY = map.at(0f, 640f).y),
    )
    if (local < 0.3f) {
        val p = c.path
        p.reset()
        var x = 70f + rnd(n * 13 + 1) * 250f
        var y = 250f
        val first = map.at(x, y)
        p.moveTo(first.x, first.y)
        var k = 0
        while (y < 600f) {
            x += (rnd(n * 31 + k) - 0.5f) * 34f
            y += 18f + rnd(n * 37 + k) * 22f
            val o = map.at(x, y)
            p.lineTo(o.x, o.y)
            k++
        }
        drawPath(p, Color.White.copy(alpha = 0.25f * light), style = androidx.compose.ui.graphics.drawscope.Stroke(map.len(5f), cap = StrokeCap.Round))
        drawPath(p, Color.White.copy(alpha = 0.95f * light), style = androidx.compose.ui.graphics.drawscope.Stroke(map.len(1.4f), cap = StrokeCap.Round))
    }
}

private const val STORM_SLOT = 15f

/** When the [n]th flash of lightning comes (one in each quarter minute, at a different moment each time). */
internal fun stormFlash(n: Int): Float = n * STORM_SLOT + 3f + rnd(n * 13) * 8f

/** Fireflies drifting over the meadow, each lighting up and going dark in its own time. */
private fun DrawScope.fireflies(map: SkyMap, t: Float) {
    for (i in 0 until 28) {
        val hx = 20f + rnd(i * 5) * 350f
        val hy = 450f + rnd(i * 5 + 1) * 280f
        val x = hx + 20f * sin(t * 0.35f * (1f + rnd(i * 5 + 2)) + i) + 7f * sin(t * 0.9f + i * 2.3f)
        val y = hy + 14f * sin(t * 0.27f * (1f + rnd(i * 5 + 3)) + i * 1.7f)
        val glow = max(0f, sin(t * (0.5f + rnd(i * 5 + 4) * 0.7f) + i * 3.1f)).pow(3)
        if (glow < 0.02f) continue
        val o = map.at(x, y)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE8F7A0).copy(alpha = 0.55f * glow), Color.Transparent), center = o, radius = map.len(9f)), map.len(9f), o)
        drawCircle(Color(0xFFFFFDE2).copy(alpha = glow), map.len(1.3f), o)
    }
}

/** A campfire: flames licking up, the glow on the ground breathing with them, sparks rising into the dark. */
private fun DrawScope.campfire(map: SkyMap, t: Float, c: LiveCanvas) {
    val cx = 195f
    val base = 588f
    val flick = wobble(t * 2.2f, 1)
    val g = map.at(cx, base - 10f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE8A050).copy(alpha = 0.28f + 0.05f * flick), Color.Transparent), center = g, radius = map.len(130f + 8f * flick)), map.len(130f + 8f * flick), g)
    val p = c.path
    fun tongue(dx: Float, h: Float, w: Float, sway: Float, color: Color) {
        p.reset()
        val l = map.at(cx + dx - w, base)
        val r = map.at(cx + dx + w, base)
        val tip = map.at(cx + dx + sway, base - h)
        val cl = map.at(cx + dx - w * 0.9f, base - h * 0.55f)
        val cr = map.at(cx + dx + w * 0.9f + sway * 0.3f, base - h * 0.5f)
        p.moveTo(l.x, l.y)
        p.quadraticTo(cl.x, cl.y, tip.x, tip.y)
        p.quadraticTo(cr.x, cr.y, r.x, r.y)
        p.close()
        drawPath(p, color)
    }
    for (layer in 0..2) {
        val color = when (layer) { 0 -> Color(0xFFD9602A).copy(alpha = 0.85f); 1 -> Color(0xFFF0A040).copy(alpha = 0.9f); else -> Color(0xFFFFE2A0) }
        val k = 1f - layer * 0.3f
        for (i in 0 until 5) {
            val dx = (i - 2) * 6.5f * k
            val h = (30f + 18f * rnd(i * 3 + layer)) * k * (0.85f + 0.25f * wobble(t * 3f, i * 5 + layer))
            tongue(dx, h, (8f + 3f * rnd(i + 9)) * k, 4f * wobble(t * 2.5f, i * 7 + layer), color)
        }
    }
    for (i in 0 until 24) {
        val life = 1.6f + rnd(i * 4) * 1.2f
        val age = (t + rnd(i * 4 + 1) * life) % life
        val q = age / life
        val x = cx + (rnd(i * 4 + 2) - 0.5f) * 20f + sin(age * 3f + i) * 10f * q
        val y = base - 14f - age * (55f + rnd(i * 4 + 3) * 40f)
        val o = map.at(x, y)
        drawCircle(Color(0xFFFFC46B).copy(alpha = (1f - q) * 0.9f), map.len(0.9f + 0.5f * (1f - q)), o)
    }
}

/** The moon's path on the sea glittering, and slow swells passing under it. */
private fun DrawScope.sea(map: SkyMap, t: Float) {
    val glitter = Color(0xFFE2E7EF)
    for (row in 0 until 46) {
        val y = 476f + row.toFloat().pow(1.45f) * 1.6f
        if (y > 800f) break
        val spread = 6f + (y - 470f) * 0.32f
        for (k in 0 until 3) {
            val n = row * 3 + k
            val x = 195f + (rnd(n * 2) - 0.5f) * 2f * spread
            val a = max(0f, sin(t * (1.6f + rnd(n * 2 + 1) * 2.2f) + n * 1.9f)).pow(2)
            if (a < 0.04f) continue
            val len = 3f + (y - 470f) * 0.04f + rnd(n + 50) * 6f
            drawLine(glitter.copy(alpha = 0.75f * a * (1f - (y - 470f) / 420f)), map.at(x - len / 2, y), map.at(x + len / 2, y), strokeWidth = map.len(1f), cap = StrokeCap.Round)
        }
    }
    for (i in 0 until 14) {
        val y = 490f + i * 22f + 6f * sin(t * 0.3f + i)
        val x = ((t * (4f + rnd(i) * 4f) + rnd(i + 20) * 390f) % 460f) - 60f
        drawLine(Color(0xFFAEB6C4).copy(alpha = 0.07f), map.at(x, y), map.at(x + 50f + rnd(i + 40) * 40f, y), strokeWidth = map.len(0.8f))
    }
}

/** Snow falling in three depths: small and slow far away, big and quicker close by, swaying as it goes. */
private fun DrawScope.snowfall(map: SkyMap, t: Float) {
    var n = 0
    for ((count, size, speed) in listOf(Triple(80, 0.6f, 14f), Triple(55, 1.0f, 24f), Triple(26, 1.7f, 40f))) {
        for (i in 0 until count) {
            n++
            val y = ((rnd(n * 3) * 870f + speed * t) % 870f) - 20f
            val sway = (5f + size * 6f) * sin(t * (0.5f + rnd(n * 3 + 1)) + n)
            val x = ((rnd(n * 3 + 2) * 390f + t * 3f * size + sway) % 400f + 400f) % 400f - 5f
            drawCircle(Color.White.copy(alpha = 0.45f + 0.35f * (size / 1.7f)), map.len(size), map.at(x, y))
        }
    }
}

/** A candle on the sill: its flame flickering and leaning, its light on the wall breathing with it. */
private fun DrawScope.candle(map: SkyMap, t: Float, c: LiveCanvas) {
    val x = 250f
    val base = 437f
    val flick = wobble(t * 2.6f, 3)
    val g = map.at(x, base - 8f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF2B35C).copy(alpha = 0.2f + 0.03f * flick), Color.Transparent), center = g, radius = map.len(150f + 6f * flick)), map.len(150f + 6f * flick), g)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6B0).copy(alpha = 0.35f), Color.Transparent), center = g, radius = map.len(28f)), map.len(28f), g)
    val p = c.path
    fun flame(h: Float, w: Float, lean: Float, color: Color) {
        p.reset()
        val b = map.at(x, base)
        val tip = map.at(x + lean, base - h)
        p.moveTo(b.x, b.y)
        val c1 = map.at(x - w, base - 1f)
        val c2 = map.at(x - w * 0.8f, base - h * 0.6f)
        p.cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        val c3 = map.at(x + w * 0.8f + lean * 0.2f, base - h * 0.6f)
        val c4 = map.at(x + w, base - 1f)
        p.cubicTo(c3.x, c3.y, c4.x, c4.y, b.x, b.y)
        p.close()
        drawPath(p, color)
    }
    val h = 19f * (1f + 0.1f * flick)
    val lean = 2f * wobble(t * 1.8f, 8)
    flame(h, 5.2f, lean, Color(0xFFF6B44E).copy(alpha = 0.92f))
    flame(h * 0.66f, 3.2f, lean * 0.7f, Color(0xFFFFF1C8))
    drawCircle(Color(0xFF6E8BD8).copy(alpha = 0.45f), map.len(1.6f), map.at(x, base - 1.8f))
    drawLine(Color(0xFF1A120C), map.at(x, base + 1f), map.at(x + lean * 0.1f, base - 4f), strokeWidth = map.len(0.8f))
}

/** Tall grass in front, bending in the wind; gusts come and go and travel across it. */
private fun DrawScope.wind(map: SkyMap, t: Float, c: LiveCanvas) {
    val p = c.path
    val gust = 0.5f + 0.5f * sin(t * 0.23f)
    for (i in 0 until 150) {
        val x = -10f + rnd(i * 4) * 410f
        val h = 110f + rnd(i * 4 + 1) * 170f
        val w = 2.6f + rnd(i * 4 + 2) * 3f
        val sway = 0.55f * sin(t * 1.1f - x * 0.018f) + 0.3f * sin(t * 2.3f - x * 0.05f + 1f) + 0.15f * sin(t * 4.1f + i)
        val tipDx = (10f + 30f * gust) * sway * (h / 200f) + 6f
        val bottom = 850f
        p.reset()
        val bl = map.at(x - w / 2, bottom)
        val ctl = map.at(x + tipDx * 0.3f, bottom - h * 0.55f)
        val tip = map.at(x + tipDx, bottom - h)
        val ctr = map.at(x + tipDx * 0.3f + w * 0.4f, bottom - h * 0.55f)
        val br = map.at(x + w / 2, bottom)
        p.moveTo(bl.x, bl.y)
        p.quadraticTo(ctl.x, ctl.y, tip.x, tip.y)
        p.quadraticTo(ctr.x, ctr.y, br.x, br.y)
        p.close()
        drawPath(p, if (i % 5 == 0) Color(0xFF0A0E18) else Color(0xFF05070D))
    }
}

/** Ravens circling the tower: gliding round, beating their wings now and then; the far ones smaller and paler. */
private fun DrawScope.circling(map: SkyMap, t: Float, c: LiveCanvas) {
    val birds = 5
    val order = (0 until birds).sortedBy { i -> sin(t * 0.32f + i * TAU / birds) }
    for (i in order) {
        val a = t * 0.32f + i * TAU / birds + rnd(i) * 0.4f
        val rx = 118f + rnd(i + 10) * 40f
        val ry = 30f + rnd(i + 20) * 18f
        val x = 195f + rx * cos(a)
        val y = 318f + (rnd(i + 30) - 0.5f) * 60f + ry * sin(a) + 4f * sin(t * 1.3f + i)
        val near = (sin(a) + 1f) / 2f
        val movingRight = -sin(a) > 0f
        val beating = sin(t * 0.7f + i * 1.9f) > 0.45f
        val flap = if (beating) 0.1f + 0.9f * sin(t * TAU * 1.6f + i) else 0.42f + 0.04f * sin(t * 2f + i)
        val color = lerp(Color(0xFF1A2132), Color(0xFF05070D), near)
        flyingRaven(map, c, x, y, 0.72f + 0.38f * near, flap, left = !movingRight, color = color)
    }
}

private fun lerp(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f,
)
