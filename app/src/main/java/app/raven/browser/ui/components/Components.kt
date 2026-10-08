package app.raven.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space
import kotlin.random.Random

/** The plain midnight ground behind full screens (the name stays from the first design). */
@Suppress("UNUSED_PARAMETER")
fun Modifier.starfield(seed: Int = 7, count: Int = 26, ground: Color = Space.Ground, glow: Color? = null): Modifier = drawBehind {
    drawRect(ground)
}

/** A raised panel of night: a dusk-blue fill with a fine silver edge (the name stays from the first design). */
fun Modifier.chocolate(shape: Shape = RoundedCornerShape(18.dp), color: Color = Space.Chocolate): Modifier = this
    .clip(shape)
    .background(color)
    .border(1.dp, Space.Hairline, shape)

/** Night glass over the wallpaper: dark and see-through, a brighter hairline catching the light along the top. */
fun Modifier.glass(shape: Shape, fill: Color = Space.Glass, edge: Color = Space.GlassEdge, top: Color = Space.GlassTop): Modifier = this
    .clip(shape)
    .background(fill)
    .border(1.dp, Brush.verticalGradient(listOf(top, edge, edge)), shape)

/** A shaded sphere, the building block of the design. */
@Composable
fun Sphere(size: Dp, light: Color, mid: Color, dark: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        drawCircle(Brush.radialGradient(listOf(light, mid, dark), center = Offset(this.size.width * 0.34f, this.size.height * 0.3f), radius = this.size.width * 0.75f))
    }
}

/** Where the first design had a ringed planet: a full moon (empty screens, headers). */
@Suppress("UNUSED_PARAMETER")
@Composable
fun RingedPlanet(
    size: Dp,
    modifier: Modifier = Modifier,
    light: Color = Space.PlanetLight,
    mid: Color = Raven.accent,
    dark: Color = Space.PlanetDeep,
    ring: Color = Color.Transparent,
    ringStroke: Dp = 1.5.dp,
    ringWidth: Float = 1.8f,
) {
    Canvas(modifier.size(size)) {
        drawCircle(Brush.radialGradient(listOf(Color(0x33C7CCD8), Color.Transparent)), radius = this.size.minDimension / 2f)
        drawCircle(
            Brush.radialGradient(listOf(light, Color(0xFFB8BFCC), dark), center = Offset(this.size.width * 0.42f, this.size.height * 0.4f), radius = this.size.width * 0.42f),
            radius = this.size.minDimension * 0.3f,
        )
    }
}

/** The app mark, as on the launcher icon: a raven's head in silver against the night. */
private val ravenHead by lazy {
    androidx.compose.ui.graphics.vector.PathParser().parsePathString(
        "M10 49 C18 44 28 39 40 36 C46 28 56 23 67 25 C78 27 85 36 86 48 C87 62 89 78 96 96 L50 96 C49 90 45 86 47 80 " +
            "L41 79 L45 74 L38 71 L43 67 L36 63 L42 59 C32 56 20 53 10 49 Z",
    ).toPath()
}

@Composable
fun RavenMark(size: Dp, modifier: Modifier = Modifier, color: Color = Raven.accent) {
    val shape = RoundedCornerShape(size * 0.3f)
    Canvas(modifier.size(size).clip(shape).background(Space.Surface).border(1.dp, Space.Hairline, shape)) {
        val s = this.size.minDimension / 100f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(ravenHead, color)
            drawLine(Space.Surface, Offset(10f, 49f), Offset(41f, 50f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawCircle(Space.Ground, radius = 2.8f, center = Offset(52f, 37f))
        }
    }
}

/** Shield, the built-in ad blocker: a silver shield in a dusk circle. */
@Composable
fun AmberPlanet(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size * 1.4f).chocolate(CircleShape, Space.Surface2), contentAlignment = Alignment.Center) {
        Icon(Icons.Shield, null, size = size, tint = Raven.accent)
    }
}

/** A site shown as a dusk circle with its initial in silver. */
@Composable
fun SitePlanet(
    letter: String,
    size: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    sleeping: Boolean = false,
    private: Boolean = false,
) {
    val accent = Raven.accent
    val shape = CircleShape
    Box(
        modifier
            .size(size)
            .chocolate(shape, if (sleeping) Space.Surface else Space.Surface2)
            .then(
                when {
                    private -> Modifier.border(1.5.dp, Space.Nebula.copy(alpha = 0.7f), shape)
                    highlighted -> Modifier.border(1.5.dp, accent, shape)
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter.take(1).uppercase(),
            color = if (sleeping) Space.Text2 else Space.Text,
            fontSize = (size.value * 0.38f).sp,
            fontFamily = app.raven.browser.ui.theme.Display,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun OrbitRing(modifier: Modifier, color: Color = Color(0x14FFFFFF), dashed: Boolean = false, tilt: Float = 0f) {
    Canvas(modifier) {
        rotate(tilt) {
            drawOval(
                color, style = Stroke(
                    1.dp.toPx(),
                    pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null,
                ),
            )
        }
    }
}

/** Round icon button with a 48dp touch target. */
@Composable
fun IconButton(
    icon: RavenIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Space.Text,
    background: Color = Color.Transparent,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, size = iconSize, tint = if (enabled) tint else Space.Text3)
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: RavenIcon? = null,
    style: PillStyle = PillStyle.Primary,
    height: Dp = 54.dp,
    enabled: Boolean = true,
) {
    val accent = Raven.accent
    val (bg, fg, border) = when (style) {
        PillStyle.Primary -> Triple(accent, Space.OnAccent, null)
        PillStyle.Outline -> Triple(Color.Transparent, Space.Text, Color(0x33C7CCD8))
        PillStyle.Private -> Triple(Space.Nebula, Color(0xFF0B0818), null)
        PillStyle.Amber -> Triple(Space.Solar, Space.OnAccent, null)
        PillStyle.AmberOutline -> Triple(Color.Transparent, Space.SolarText, Space.Solar.copy(alpha = 0.45f))
        PillStyle.Soft -> Triple(accent.copy(alpha = 0.14f), Space.Text, null)
    }
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, size = 18.dp, tint = fg, stroke = 2f)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (style == PillStyle.Primary || style == PillStyle.Amber) FontWeight.SemiBold else FontWeight.Medium)
    }
}

enum class PillStyle { Primary, Outline, Private, Amber, AmberOutline, Soft }

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, description: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val accent = Raven.accent
    Box(
        modifier
            .size(60.dp, 48.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .semantics {
                contentDescription = description
                stateDescription = if (checked) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(52.dp, 32.dp)
                .clip(CircleShape)
                .background(if (checked) accent else Space.Track),
        ) {
            val x by androidx.compose.animation.core.animateDpAsState(if (checked) 20.dp else 0.dp, label = "toggle")
            Box(
                Modifier
                    .padding(4.dp)
                    .size(24.dp)
                    .offset(x = x)
                    .clip(CircleShape)
                    .background(if (checked) Space.OnAccent else Space.Text3),
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(Icons.Back, "Back", onBack, background = Space.Surface, size = 44.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        // On a narrow phone the title shrinks to fit beside the buttons rather than being cut off.
        val style = MaterialTheme.typography.headlineSmall
        androidx.compose.foundation.text.BasicText(
            title, Modifier.weight(1f), style = style.copy(color = Space.Text), maxLines = 1, overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
        )
        actions()
    }
}

/**
 * Text that shrinks just enough for its widest word to fit, so a big title on a narrow phone never breaks a word in
 * two ("Bookmark-s"). Lines still wrap between words as usual.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Space.Text,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val fitted = remember(text, style, room) {
            val widest = text.split(' ', '\n').filter { it.isNotEmpty() }
                .maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
            if (room == Constraints.Infinity || widest <= room) style else {
                val k = room.toFloat() / widest * 0.98f
                style.copy(
                    fontSize = style.fontSize * k,
                    lineHeight = if (style.lineHeight.isSp) style.lineHeight * k else style.lineHeight,
                    letterSpacing = if (style.letterSpacing.isSp) style.letterSpacing * k else style.letterSpacing,
                )
            }
        }
        Text(text, style = fitted, color = color, textAlign = textAlign, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Space.Text2,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Space.Surface)
            .border(1.dp, Space.Hairline, shape),
        content = content,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Color(0x14C7CCD8)))
}

/** One settings-style row: title, optional detail, optional trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: RavenIcon? = null,
    iconTint: Color = Space.Text,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = if (trailing != null) 6.dp else 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, size = 20.dp, tint = iconTint)
            Spacer(Modifier.width(14.dp))
        }
        if (leading != null) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Space.Text)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) Text(value, style = MaterialTheme.typography.labelMedium, color = Space.Text2, modifier = Modifier.padding(start = 8.dp))
        if (chevron) Icon(Icons.Forward, null, size = 16.dp, tint = Space.Text2, modifier = Modifier.padding(start = 6.dp))
        trailing?.invoke()
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, dot: Color = Raven.accent, background: Color = Color(0x1AC7CCD8), color: Color = Space.Text) {
    Row(
        modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A4256)))
    }
}

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)

@Composable
fun WithContentColor(color: Color, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides color, content = content)

val ScreenPadding = PaddingValues(horizontal = 16.dp)

@Composable
fun Tile(text: String, icon: RavenIcon, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Space.Text, background: Color = Space.Surface2, badge: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, size = 20.dp, tint = tint)
        Spacer(Modifier.width(12.dp))
        // Two lines, so labels like "Desktop site" next to a switch still fit on narrow phones.
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint, modifier = Modifier.weight(1f).padding(vertical = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (badge != null) {
            Box(Modifier.defaultMinSize(minWidth = 22.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(Raven.accent).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(badge, color = Space.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Tilted(modifier: Modifier, degrees: Float, content: @Composable BoxScope.() -> Unit) = Box(modifier.rotate(degrees), content = content)
