package app.raven.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.raven.browser.Container
import app.raven.browser.engine.BrowserTab
import app.raven.browser.ui.UiState
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.theme.Display
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.TranslationsController.Language
import org.mozilla.geckoview.TranslationsController.RuntimeTranslation
import org.mozilla.geckoview.TranslationsController.SessionTranslation
import kotlin.coroutines.resume

/** Waits for a GeckoResult; null if it failed. */
private suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { cont ->
    accept({ cont.resume(it) }, { cont.resume(null) })
}

/**
 * Translate, on the phone: Firefox's own translation engine runs inside Raven, so the page never goes to a
 * translation service. The first time for a pair of languages, the engine downloads them.
 */
@Composable
fun TranslateSheet(c: Container, ui: UiState, tab: BrowserTab) {
    val state by tab.translation.collectAsState()
    var supported by remember { mutableStateOf<Boolean?>(null) }
    var fromList by remember { mutableStateOf<List<Language>>(emptyList()) }
    var toList by remember { mutableStateOf<List<Language>>(emptyList()) }
    var preferred by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        supported = RuntimeTranslation.isTranslationsEngineSupported().await() ?: false
        RuntimeTranslation.listSupportedLanguages().await()?.let { fromList = it.fromLanguages.orEmpty().sorted(); toList = it.toLanguages.orEmpty().sorted() }
        preferred = RuntimeTranslation.preferredLanguages().await()?.firstOrNull()
    }
    val detected = state?.detectedLanguages
    val requested = state?.requestedTranslationPair
    var from by remember(detected?.docLangTag) { mutableStateOf(requested?.fromLanguage ?: detected?.docLangTag) }
    var to by remember(detected?.userLangTag, preferred) { mutableStateOf(requested?.toLanguage ?: detected?.userLangTag ?: preferred ?: "en") }
    var size by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(from, to) {
        size = null
        val f = from ?: return@LaunchedEffect
        if (f != to) size = RuntimeTranslation.checkPairDownloadSize(f, to!!).await()
    }
    fun name(code: String?, list: List<Language>) = list.firstOrNull { it.code == code }?.localizedDisplayName ?: code?.uppercase() ?: "Unknown"
    val translated = requested != null && state?.hasVisibleChange == true
    val working = requested != null && state?.hasVisibleChange != true && state?.error == null
    fun close() { ui.sheet = null }

    RavenSheet(::close) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Translate, null, size = 24.dp, tint = Raven.accent)
                Spacer(Modifier.width(12.dp))
                Text("Translate", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            }
            when {
                supported == false -> Text(
                    "This phone can't run the translation engine.",
                    style = MaterialTheme.typography.bodyMedium, color = Space.Text2,
                )
                translated -> Text(
                    "Translated from ${name(requested!!.fromLanguage, fromList)} to ${name(requested.toLanguage, toList)}.",
                    style = MaterialTheme.typography.bodyMedium, color = Space.Text2,
                )
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LanguagePicker("From", name(from, fromList), fromList, Modifier.weight(1f)) { from = it }
                        Icon(Icons.ArrowRight, null, size = 18.dp, tint = Space.Text2)
                        LanguagePicker("To", name(to, toList), toList, Modifier.weight(1f)) { to = it }
                    }
                    Text(
                        buildString {
                            append("Translated on your phone; the page isn't sent anywhere.")
                            size?.takeIf { it > 0 }?.let { append(" The first time, Raven downloads these languages (${(it + 999_999) / 1_000_000} MB).") }
                        },
                        style = MaterialTheme.typography.bodySmall, color = Space.Text2,
                    )
                }
            }
            state?.error?.let { Text("Couldn't translate: $it", style = MaterialTheme.typography.bodySmall, color = Space.SolarText) }
            when {
                translated -> PillButton("Show original", {
                    tab.session.sessionTranslation?.restoreOriginalPage()
                    close()
                }, Modifier.fillMaxWidth(), style = PillStyle.Outline)
                supported == false -> Unit
                else -> PillButton(
                    if (working) "Translating…" else "Translate", {
                        val f = from
                        val t = to
                        if (f != null && t != null && f != t) {
                            tab.session.sessionTranslation?.translate(f, t, SessionTranslation.TranslationOptions.Builder().downloadModel(true).build())
                            close()
                        }
                    },
                    Modifier.fillMaxWidth(), enabled = !working && from != null && from != to,
                )
            }
        }
    }
}

@Composable
private fun LanguagePicker(label: String, current: String, options: List<Language>, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().height(58.dp).clip(CircleShape).background(Space.Surface2).border(1.dp, Space.Hairline, CircleShape)
                .clickable(onClickLabel = "Choose the language to translate ${label.lowercase()}") { open = true }.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, color = Space.Text2)
            Text(current, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
        }
        DropdownMenu(open, { open = false }, containerColor = Space.Surface2) {
            options.forEach { l ->
                DropdownMenuItem(text = { Text(l.localizedDisplayName ?: l.code) }, onClick = { onPick(l.code); open = false })
            }
        }
    }
}
