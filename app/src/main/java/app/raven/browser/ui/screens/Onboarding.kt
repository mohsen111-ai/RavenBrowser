package app.raven.browser.ui.screens

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.Container
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.components.RavenMark
import app.raven.browser.ui.components.glass
import app.raven.browser.ui.sky.nightSky
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space

@Composable
fun Onboarding(c: Container) {
    val context = LocalContext.current
    fun done() = c.settings.update { it.copy(onboardingDone = true) }
    // Whatever Android's answer, the welcome is over.
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { done() }
    val wallpaper by c.sky.current.collectAsState()
    val image by c.sky.image.collectAsState()
    WelcomeContent(
        sky = Modifier.nightSky(image, wallpaper, Raven.ground, moving = !Raven.reduceMotion),
        onDefault = {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = context.getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                } else done()
            } else {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                done()
            }
        },
        onLater = ::done,
    )
}

/** The first thing Raven shows: its name over tonight's wallpaper, what it does, and the default-browser question. */
@Composable
fun WelcomeContent(sky: Modifier, onDefault: () -> Unit, onLater: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().then(sky), contentAlignment = Alignment.TopCenter) {
        // On a small phone the name shrinks to stay on one line, and the buttons stay in view while the rest scrolls.
        val compact = maxHeight < 720.dp
        val nameSize = minOf(56f, (minOf(maxWidth, 520.dp).value - 64f) / 5.2f).sp
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val room = maxHeight
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Column(Modifier.heightIn(min = room), verticalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.padding(start = 8.dp, top = if (compact) 8.dp else 36.dp)) {
                            RavenMark(if (compact) 60.dp else 84.dp)
                            Text(
                                "Raven", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = nameSize, lineHeight = nameSize * 1.04f,
                                letterSpacing = (-1.5).sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = if (compact) 12.dp else 22.dp),
                            )
                            Text("Quiet as midnight.", style = MaterialTheme.typography.titleLarge, color = Space.Text2, fontWeight = FontWeight.Medium)
                        }
                        Column(
                            Modifier.padding(top = if (compact) 18.dp else 28.dp).fillMaxWidth().glass(RoundedCornerShape(28.dp), fill = Color(0xE00A0E18))
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Point(Icons.Shield, Raven.accent, "Trackers turned away", "uBlock Origin is built in and already on, from the very first page.")
                            Point(Icons.Fingerprint, Space.Nebula, "Private, under your finger", "Private tabs keep nothing, and can lock when you leave.")
                            Point(Icons.Moon, Raven.accent, "A new sky every time", "Your home screen changes its wallpaper each time you open Raven.")
                        }
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
            PillButton("Make Raven my browser", onDefault, Modifier.fillMaxWidth(), height = if (compact) 52.dp else 58.dp)
            PillButton("Not now", onLater, Modifier.fillMaxWidth(), style = PillStyle.Outline, height = if (compact) 48.dp else 52.dp)
        }
    }
}

@Composable
private fun Point(icon: RavenIcon, tint: Color, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).glass(CircleShape, fill = Space.Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, size = 19.dp, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
