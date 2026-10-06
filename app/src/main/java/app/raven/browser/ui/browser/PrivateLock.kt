package app.raven.browser.ui.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.ui.components.FitText
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import kotlin.random.Random

/**
 * What shows instead of private tabs while they're locked: on the page itself, and on the Private side of the
 * Tabs screen. The moon in eclipse, with the fingerprint in its dark disc. Nothing of the tabs shows through,
 * and touches don't reach the page underneath.
 */
@Composable
fun PrivateLocked(onUnlock: () -> Unit, modifier: Modifier = Modifier, onLeave: (() -> Unit)? = null) = LockScreen(
    label = "PRIVATE TABS",
    title = "Locked in the eclipse.",
    text = "Use your fingerprint or screen lock to open them. Even the recent apps view only sees darkness.",
    ground = Space.NebulaGround,
    glow = Space.Nebula,
    words = Color(0xFFA9A2D6),
    labelColor = Space.Nebula,
    fingerprint = Space.NebulaText,
    button = PillStyle.Private,
    onUnlock = onUnlock,
    modifier = modifier,
    leave = onLeave?.let { "Go to everyday tabs" to it },
)

/** Raven locked (the lock for all of Raven): the same moon, in moonlight instead of eclipse violet. */
@Composable
fun AppLocked(onUnlock: () -> Unit, modifier: Modifier = Modifier) = LockScreen(
    label = "RAVEN",
    title = "Locked for the night.",
    text = "Use your fingerprint or screen lock to open Raven.",
    ground = Space.Ground,
    glow = Raven.accent,
    words = Space.Text2,
    labelColor = Raven.accent,
    fingerprint = Space.Text,
    button = PillStyle.Primary,
    onUnlock = onUnlock,
    modifier = modifier,
    leave = null,
)

@Composable
private fun LockScreen(
    label: String,
    title: String,
    text: String,
    ground: Color,
    glow: Color,
    words: Color,
    labelColor: Color,
    fingerprint: Color,
    button: PillStyle,
    onUnlock: () -> Unit,
    modifier: Modifier,
    leave: Pair<String, () -> Unit>?,
) {
    val moving = !Raven.reduceMotion
    val pulse = if (moving) {
        rememberInfiniteTransition(label = "lock").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    } else null
    val stars = remember { Random(9).let { r -> List(70) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ground)
            // Swallows touches, so nothing reaches the page underneath.
            .clickable(remember { MutableInteractionSource() }, indication = null) { },
    ) {
        val disc = (minOf(maxWidth * 0.27f, maxHeight * 0.16f)).coerceIn(56.dp, 110.dp)
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { (x, y, a) -> drawCircle(Color.White.copy(alpha = 0.12f + 0.5f * a), radius = (0.6f + a).dp.toPx(), center = Offset(x * size.width, y * size.height)) }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The eclipse: corona, silver rim, dark disc, the fingerprint inside; rings go out from it.
            Box(
                Modifier.size(disc * 3.2f).clip(CircleShape).clickable(onClickLabel = "Unlock", onClick = onUnlock)
                    .semantics { contentDescription = "Unlock with fingerprint or screen lock" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val r = disc.toPx()
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.Transparent, 0.58f to Color(0x99C7CCD8), 0.7f to glow.copy(alpha = 0.25f), 1f to Color.Transparent,
                            center = c, radius = r * 1.7f,
                        ),
                        radius = r * 1.7f, center = c,
                    )
                    pulse?.value?.let { t ->
                        for (k in 0..1) {
                            val p = (t + k * 0.5f) % 1f
                            drawCircle(glow.copy(alpha = 0.7f * (1f - p)), radius = r * (1.05f + 0.55f * p), center = c, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                    drawCircle(Color(0xFFE9ECF3).copy(alpha = 0.85f), radius = r + 1.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                    drawCircle(ground, radius = r, center = c)
                    val bead = Offset(c.x + r * 0.74f, c.y - r * 0.68f)
                    drawCircle(Color.White.copy(alpha = 0.22f), radius = 9.dp.toPx(), center = bead)
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = bead)
                }
                Icon(Icons.Fingerprint, null, size = disc * 0.62f, tint = fingerprint, stroke = 1.4f)
            }
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
                FitText(
                    title, TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
                    Modifier.padding(top = 10.dp), textAlign = TextAlign.Center,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium, color = words, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                )
                PillButton("Unlock", onUnlock, Modifier.padding(top = 22.dp).fillMaxWidth(), icon = Icons.Fingerprint, style = button)
                if (leave != null) TextButton(leave.second, modifier = Modifier.padding(top = 4.dp)) { Text(leave.first, color = Space.Text2) }
            }
        }
    }
}
