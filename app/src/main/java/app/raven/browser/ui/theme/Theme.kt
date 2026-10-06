package app.raven.browser.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.raven.browser.R

/**
 * The "Raven" palette: midnight blue-black, moonlight silver for the accent, and a dim violet for private tabs
 * (a moon in eclipse). Names kept from the first design so every screen picks the new colours up.
 */
object Space {
    val Ground = Color(0xFF070A12)
    val TrueBlack = Color(0xFF000000)
    val Strip = Color(0xFF070A12)
    val Surface = Color(0xFF0D111C)
    val Surface2 = Color(0xFF141A28)
    val Surface3 = Color(0xFF1B2232)
    val Bar = Color(0xFF0D111C)
    val Hairline = Color(0x1FC7CCD8)
    val Text = Color(0xFFE9ECF3)
    val Text2 = Color(0xFF9098AC)
    val Text3 = Color(0xFF5E667A)
    val Track = Color(0xFF222A3B)
    val OnAccent = Color(0xFF070A12)
    /** Warnings (an unsecure page, the clean slate): a cold, clear moonlight. */
    val Solar = Color(0xFFE6C77E)
    val SolarText = Color(0xFFF0DCA8)
    val SolarDeep = Color(0xFF3A4256)
    val SolarLight = Color(0xFFE9ECF3)
    /** Private tabs: the violet of an eclipse. */
    val Nebula = Color(0xFF9A8CFF)
    val NebulaText = Color(0xFFB9AFFF)
    val NebulaGround = Color(0xFF05040B)
    val NebulaBar = Color(0xFF120E24)
    val PlanetLight = Color(0xFFDCE0E8)
    val PlanetDeep = Color(0xFF8E97A8)
    val PlanetDark = Color(0xFF141A28)
    val PlanetShadow = Color(0xFF0D111C)

    /** Night glass: the panels, cards and buttons that sit over the wallpaper. */
    val Glass = Color(0xB80A0E18)
    val GlassEdge = Color(0x24C7CCD8)
    val GlassTop = Color(0x42E9ECF3)

    /** Kept for the shapes that still use them: raised tiles and their edge, and the sunk ground between. */
    val Chocolate = Color(0xFF141A28)
    val ChocolateEdge = Color(0x1FC7CCD8)
    val Groove = Color(0xFF070A12)
    val Foil = listOf(Color(0xFF8E97A8), Color(0xFFE9ECF3), Color(0xFFB8BFCC), Color(0xFFDCE0E8), Color(0xFF8E97A8), Color(0xFFC7CCD8))
    val RedFoil = listOf(Color(0xFF5B4FC0), Color(0xFFB9AFFF), Color(0xFF7466E0), Color(0xFFD2CBFF), Color(0xFF6458D0), Color(0xFF9A8CFF))

    val accents = listOf(Color(0xFFC7CCD8), Color(0xFF8FB2FF), Color(0xFFB49CFF))
    val accentNames = listOf("Silver", "Ice blue", "Lavender")
}

/** Hanken Grotesk for everything you read and tap; Syne for titles, wide and sharp. */
val Body = FontFamily(
    Font(R.font.hanken_400, FontWeight.Normal),
    Font(R.font.hanken_500, FontWeight.Medium),
    Font(R.font.hanken_600, FontWeight.SemiBold),
    Font(R.font.hanken_700, FontWeight.Bold),
    Font(R.font.hanken_800, FontWeight.ExtraBold),
)
val Display = FontFamily(
    Font(R.font.syne_700, FontWeight.Bold),
    Font(R.font.syne_800, FontWeight.ExtraBold),
)

@Immutable
data class RavenLook(val accent: Color, val ground: Color, val reduceMotion: Boolean)

val LocalLook = staticCompositionLocalOf { RavenLook(Space.accents[0], Space.Ground, false) }

object Raven {
    val accent: Color @Composable get() = LocalLook.current.accent
    val ground: Color @Composable get() = LocalLook.current.ground
    val reduceMotion: Boolean @Composable get() = LocalLook.current.reduceMotion
}

private val typography = Typography(
        displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
        headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
        bodyLarge = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 2.6.sp),
)

@Composable
fun RavenTheme(accentIndex: Int, trueBlack: Boolean, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val accent = Space.accents.getOrElse(accentIndex) { Space.accents[0] }
    val ground = if (trueBlack) Space.TrueBlack else Space.Ground
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Space.OnAccent,
        secondary = Space.Solar,
        background = ground,
        onBackground = Space.Text,
        surface = Space.Surface,
        onSurface = Space.Text,
        surfaceVariant = Space.Surface2,
        onSurfaceVariant = Space.Text2,
        surfaceContainer = Space.Surface,
        surfaceContainerHigh = Space.Surface2,
        surfaceContainerHighest = Space.Surface3,
        surfaceContainerLow = Space.Surface,
        outline = Color(0x33C7CCD8),
        outlineVariant = Space.Hairline,
        error = Space.Solar,
    )
    CompositionLocalProvider(LocalLook provides RavenLook(accent, ground, reduceMotion)) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            // Text outside a Surface would otherwise fall back to black.
            CompositionLocalProvider(LocalContentColor provides Space.Text, content = content)
        }
    }
}
