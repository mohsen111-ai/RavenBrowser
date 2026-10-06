package app.raven.browser.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stroke icons on a 24-unit grid, drawn the same way as in the design mockups. */
class RavenIcon internal constructor(internal val parts: List<Part>) {
    internal sealed interface Part
    internal class P(val d: String, val fill: Boolean) : Part
    internal class C(val cx: Float, val cy: Float, val r: Float, val fill: Boolean) : Part
}

private class IconBuilder {
    val parts = mutableListOf<RavenIcon.Part>()
    fun p(d: String, fill: Boolean = false) { parts += RavenIcon.P(d, fill) }
    fun c(cx: Float, cy: Float, r: Float, fill: Boolean = false) { parts += RavenIcon.C(cx, cy, r, fill) }
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        p("M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 -$r ${r}h-${w - 2 * r}a$r $r 0 0 1 -$r -${r}v-${h - 2 * r}a$r $r 0 0 1 $r -${r}z")
    }
}

private fun icon(block: IconBuilder.() -> Unit) = RavenIcon(IconBuilder().apply(block).parts)

object Icons {
    val Search = icon { c(11f, 11f, 7f); p("M20 20l-3.5-3.5") }
    val Lock = icon { rect(5f, 11f, 14f, 9f, 2f); p("M8 11V8a4 4 0 0 1 8 0v3") }
    val LockOpen = icon { rect(5f, 11f, 14f, 9f, 2f); p("M8 11V8a4 4 0 0 1 7.5-2") }
    val Back = icon { p("M15 5l-7 7 7 7") }
    val Forward = icon { p("M9 5l7 7-7 7") }
    val Reload = icon { p("M20 12a8 8 0 1 1-2.34-5.66"); p("M20 4v5h-5") }
    val Share = icon { p("M12 3v12"); p("M8 7l4-4 4 4"); p("M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7") }
    val History = icon { c(12f, 12f, 8f); p("M12 8v4l3 2") }
    val Download = icon { p("M12 4v11"); p("M7 10l5 5 5-5"); p("M5 20h14") }
    val Find = icon { p("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"); c(11.5f, 13.5f, 2.5f); p("M13.3 15.3L15 17") }
    val Desktop = icon { rect(3f, 4f, 18f, 12f, 2f); p("M8 20h8M12 16v4") }
    val Puzzle = icon { p("M10 4a2 2 0 0 1 4 0v2h3a1 1 0 0 1 1 1v3h-2a2 2 0 0 0 0 4h2v3a1 1 0 0 1-1 1h-3v-2a2 2 0 0 0-4 0v2H7a1 1 0 0 1-1-1v-3h2a2 2 0 0 0 0-4H6V7a1 1 0 0 1 1-1h3z") }
    val Sliders = icon { p("M4 7h10M18 7h2M4 17h4M12 17h8"); c(16f, 7f, 2f); c(10f, 17f, 2f) }
    val Plus = icon { p("M12 5v14M5 12h14") }
    val Close = icon { p("M6 6l12 12M18 6L6 18") }
    /** Private tabs: a mask. */
    val Eclipse = icon { p("M3 10c3-3 6-3 9-1c3-2 6-2 9 1c-1 4-4 5-6 4c-1-1-2-2-3-2s-2 1-3 2c-2 1-5 0-6-4z"); p("M7 11h1.5M15.5 11H17") }
    val Home = icon { p("M4 11l8-7 8 7"); p("M6 9.5V20h4.5v-5.5h3V20H18V9.5") }
    val Shield = icon { p("M12 3l7 3v5c0 5-3 8-7 10c-4-2-7-5-7-10V6z"); p("M9 12l2.2 2.2 4.3-4.4") }
    val Pause = icon { p("M9 6v12M15 6v12") }
    val Play = icon { p("M8 5l11 7-11 7z", fill = true) }
    val Check = icon { p("M5 12.5l4.5 4.5L19 7.5") }
    val File = icon { p("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"); p("M14 3v5h5") }
    val Picker = icon { c(12f, 12f, 6f); p("M12 2v4M12 18v4M2 12h4M18 12h4") }
    val Zap = icon { p("M13 3L5 14h6l-1 7 8-11h-6z") }
    val Logger = icon { p("M9 6h11M9 12h11M9 18h11"); c(4.5f, 6f, 1f, true); c(4.5f, 12f, 1f, true); c(4.5f, 18f, 1f, true) }
    val Dashboard = icon { rect(4f, 4f, 7f, 7f, 1.5f); rect(13f, 4f, 7f, 7f, 1.5f); rect(4f, 13f, 7f, 7f, 1.5f); rect(13f, 13f, 7f, 7f, 1.5f) }
    /** Clean slate: close every tab and erase everything. */
    val Supernova = icon { p("M4 7h16"); p("M9 7V4h6v3"); p("M6 7l1 13h10l1-13"); p("M10 11v5M14 11v5") }
    val Reader = icon { p("M3 5h7a2 2 0 0 1 2 2v13a2 2 0 0 0-2-2H3z"); p("M21 5h-7a2 2 0 0 0-2 2v13a2 2 0 0 1 2-2h7z") }
    val Pdf = icon { p("M7 9V3h10v6"); rect(3f, 9f, 18f, 8f, 2f); p("M7 14h10v7H7z") }
    val Link = icon { p("M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1"); p("M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1") }
    val Edit = icon { p("M4 20h4L19 9l-4-4L4 16z"); p("M13.5 6.5l4 4") }
    val Copy = icon { rect(8f, 8f, 12f, 12f, 2f); p("M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2") }
    val NewTab = icon { rect(3f, 6f, 14f, 14f, 2f); p("M7 3h12a2 2 0 0 1 2 2v12"); p("M10 10v6M7 13h6") }
    val Location = icon { p("M12 21s-6-5.5-6-11a6 6 0 0 1 12 0c0 5.5-6 11-6 11z"); c(12f, 10f, 2.2f) }
    val Camera = icon { rect(3f, 7f, 13f, 11f, 2f); p("M16 11l5-3v9l-5-3z") }
    val Mic = icon { rect(9f, 3f, 6f, 11f, 3f); p("M5 11a7 7 0 0 0 14 0M12 18v3") }
    val Bell = icon { p("M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"); p("M10 20a2 2 0 0 0 4 0") }
    val Mute = icon { p("M4 9h4l5-4v14l-5-4H4z"); p("M17 9l4 4M21 9l-4 4") }
    val Globe = icon { c(12f, 12f, 9f); p("M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18") }
    val TabsIcon = icon { rect(3f, 6f, 18f, 13f, 2f); p("M3 10h18M8 6V4h8v2") }
    val ChevronUp = icon { p("M6 15l6-6 6 6") }
    val ChevronDown = icon { p("M6 9l6 6 6-6") }
    val Menu = icon { c(12f, 5.5f, 1.7f, true); c(12f, 12f, 1.7f, true); c(12f, 18.5f, 1.7f, true) }
    val Trash = icon { p("M4 7h16"); p("M9 7V4h6v3"); p("M6 7l1 13h10l1-13") }
    val Fingerprint = icon {
        p("M12 11v3a6 6 0 0 1-1.2 3.6"); p("M8.5 10.5a3.5 3.5 0 0 1 7 0v3.5a9 9 0 0 1-.8 3.7")
        p("M5.7 8.2a7 7 0 0 1 12.6 2.3v3"); p("M5 12.5v-1.5"); p("M7.5 18.5a10 10 0 0 0 1-4.5v-2"); p("M18.3 16.5a12 12 0 0 1-.7 2.5")
    }
    val Info = icon { c(12f, 12f, 8f); p("M12 11v5M12 8v.5") }
    val Image = icon { rect(3f, 5f, 18f, 14f, 2f); c(9f, 10f, 2f); p("M21 16l-5-5-8 8") }
    val ArrowRight = icon { p("M5 12h14M13 6l6 6-6 6") }
    val Moon = icon { p("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val Warning = icon { c(12f, 12f, 8f); p("M12 8v5M12 16v.5") }
    val Bookmark = icon { p("M7 4h10v16l-5-3.5L7 20z") }
    val BookmarkFilled = icon { p("M7 4h10v16l-5-3.5L7 20z", fill = true) }
    /** Translate: a character and a Latin A. */
    val Translate = icon { p("M3 5.5h9M7.5 3.5v2"); p("M5 9c1.5 3 4 5.2 6.5 6.2"); p("M10.5 5.5c-.8 4.5-3.5 8-7.5 9.8"); p("M13 21l4-9.5 4 9.5"); p("M14.4 18h5.2") }
    /** VPN: a shield with a keyhole. */
    val Vpn = icon { p("M12 3l7 3v5c0 5-3 8-7 10c-4-2-7-5-7-10V6z"); c(12f, 10.5f, 1.8f); p("M12 12.3v3.2") }
    /** Add to the home screen's dock. */
    val AddHome = icon { p("M4 11l8-7 8 7"); p("M6 9.5V20h12V9.5"); p("M12 11.5v6M9 14.5h6") }
    val Folder = icon { p("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    /** Float: a small window over a bigger one. */
    val Float = icon { rect(3f, 5f, 18f, 14f, 2f); rect(12.5f, 11f, 6f, 5.5f, 1f) }
    /** Split screen: one screen, two halves. */
    val Split = icon { rect(4f, 4f, 16f, 16f, 2f); p("M4 12h16") }
    val SplitTop = icon { rect(4f, 4f, 16f, 16f, 2f); p("M4 12h16"); p("M6 6.5h12v4H6z", fill = true) }
    val SplitBottom = icon { rect(4f, 4f, 16f, 16f, 2f); p("M4 12h16"); p("M6 13.5h12v4H6z", fill = true) }
    val SplitLeft = icon { rect(4f, 4f, 16f, 16f, 2f); p("M12 4v16"); p("M6.5 6h4v12h-4z", fill = true) }
    val SplitRight = icon { rect(4f, 4f, 16f, 16f, 2f); p("M12 4v16"); p("M13.5 6h4v12h-4z", fill = true) }
    val Sound = icon { p("M4 9v6h4l5 4V5L8 9H4z"); p("M16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11") }
    /** Full size: arrows out to the corners. */
    val Expand = icon { p("M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7") }
    /** Full screen for pages: the four corners of the screen, nothing in between. */
    val FullPage = icon { p("M4 9V5.5A1.5 1.5 0 0 1 5.5 4H9M15 4h3.5A1.5 1.5 0 0 1 20 5.5V9M20 15v3.5a1.5 1.5 0 0 1-1.5 1.5H15M9 20H5.5A1.5 1.5 0 0 1 4 18.5V15") }
    /** Video only: a screen with a play mark. */
    val VideoOnly = icon { rect(3f, 6f, 18f, 12f, 2f); p("M10 9.5v5l4-2.5z", fill = true) }
    /** Back to the page from video only: a screen with lines of text. */
    val ToPage = icon { rect(3f, 6f, 18f, 12f, 2f); p("M7 10h10M7 14h6") }
}

@Composable
fun Icon(
    icon: RavenIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
    stroke: Float = 1.8f,
) {
    val paths = remember(icon) {
        icon.parts.map { part ->
            when (part) {
                is RavenIcon.P -> PathParser().parsePathString(part.d).toPath() to part
                is RavenIcon.C -> Path() to part
            }
        }
    }
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(semantics).size(size)) {
        val scale = this.size.minDimension / 24f
        val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            paths.forEach { (path, part) ->
                when (part) {
                    is RavenIcon.P -> drawPath(path, tint, style = if (part.fill) Fill else style)
                    is RavenIcon.C -> drawCircle(tint, part.r, Offset(part.cx, part.cy), style = if (part.fill) Fill else style)
                }
            }
        }
    }
}
