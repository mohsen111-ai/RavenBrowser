package app.raven.browser.ui.browser

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.raven.browser.Container
import app.raven.browser.engine.UiPrompt
import app.raven.browser.ui.components.PillButton
import app.raven.browser.ui.components.PillStyle
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.RavenIcon
import app.raven.browser.ui.theme.Space
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import java.util.Calendar

/** Answers whatever the current page is asking: dialogs, form pickers, file uploads and permissions. */
@Composable
fun PromptHost(c: Container) {
    val prompts by c.tabs.prompts.collectAsState()
    val selectedId by c.tabs.selectedId.collectAsState()
    // Only the tab on screen may ask; other tabs' questions wait until you switch to them.
    val p = prompts.firstOrNull { it.tabId == selectedId } ?: return
    fun done() = c.tabs.finish(p)
    when (p) {
        is UiPrompt.Page -> PagePrompt(p, ::done)
        is UiPrompt.Permission -> PermissionPrompt(c, p, ::done)
        is UiPrompt.Media -> MediaPrompt(c, p, ::done)
        is UiPrompt.AndroidPermissions -> AndroidPermissionPrompt(p, ::done)
    }
}

@Composable
private fun PagePrompt(p: UiPrompt.Page, done: () -> Unit) {
    val prompt = p.prompt
    fun respond(r: PromptDelegate.PromptResponse) { p.answer { p.result.complete(r) }; done() }
    val title = prompt.title?.takeIf { it.isNotBlank() } ?: p.host.ifBlank { "This page" } + " says"
    when (prompt) {
        is PromptDelegate.AlertPrompt -> Dialog(title, prompt.message, { respond(prompt.dismiss()) }, confirm = "OK" to { respond(prompt.dismiss()) })
        is PromptDelegate.ButtonPrompt -> Dialog(
            title, prompt.message, { respond(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)) },
            confirm = "OK" to { respond(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)) },
            dismiss = "Cancel" to { respond(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)) },
        )
        is PromptDelegate.TextPrompt -> {
            var text by remember { mutableStateOf(prompt.defaultValue.orEmpty()) }
            Dialog(
                title, prompt.message, { respond(prompt.dismiss()) },
                confirm = "OK" to { respond(prompt.confirm(text)) }, dismiss = "Cancel" to { respond(prompt.dismiss()) },
            ) { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        }
        is PromptDelegate.AuthPrompt -> {
            val onlyPassword = (prompt.authOptions.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
            var user by remember { mutableStateOf(prompt.authOptions.username.orEmpty()) }
            var pass by remember { mutableStateOf(prompt.authOptions.password.orEmpty()) }
            Dialog(
                "Sign in to ${p.host}", prompt.message, { respond(prompt.dismiss()) },
                confirm = "Sign in" to { respond(if (onlyPassword) prompt.confirm(pass) else prompt.confirm(user, pass)) },
                dismiss = "Cancel" to { respond(prompt.dismiss()) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!onlyPassword) OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
        }
        is PromptDelegate.ChoicePrompt -> ChoiceDialog(prompt, ::respond)
        is PromptDelegate.DateTimePrompt -> DateTimeDialog(prompt, ::respond)
        is PromptDelegate.ColorPrompt -> ColorDialog(prompt, ::respond)
        is PromptDelegate.FilePrompt -> FilePicker(prompt, ::respond)
        is PromptDelegate.BeforeUnloadPrompt -> Dialog(
            "Leave this page?", "Changes you made may not be saved.", { respond(prompt.confirm(AllowOrDeny.DENY)) },
            confirm = "Leave" to { respond(prompt.confirm(AllowOrDeny.ALLOW)) }, dismiss = "Stay" to { respond(prompt.confirm(AllowOrDeny.DENY)) },
        )
        is PromptDelegate.RepostConfirmPrompt -> Dialog(
            "Send the form again?", "This page needs to resend information you entered, which may repeat an action like a purchase.",
            { respond(prompt.confirm(AllowOrDeny.DENY)) },
            confirm = "Resend" to { respond(prompt.confirm(AllowOrDeny.ALLOW)) }, dismiss = "Cancel" to { respond(prompt.confirm(AllowOrDeny.DENY)) },
        )
        else -> LaunchedEffect(prompt) { respond(prompt.dismiss()) }
    }
}

@Composable
private fun Dialog(
    title: String,
    message: String?,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>,
    dismiss: Pair<String, () -> Unit>? = null,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!message.isNullOrBlank()) Text(message, style = MaterialTheme.typography.bodyMedium, color = Space.Text2)
                content?.invoke()
            }
        },
        confirmButton = { TextButton(confirm.second) { Text(confirm.first, color = Raven.accent) } },
        dismissButton = dismiss?.let { { TextButton(it.second) { Text(it.first, color = Space.Text2) } } },
        containerColor = Space.Surface,
    )
}

@Composable
private fun ChoiceDialog(prompt: PromptDelegate.ChoicePrompt, respond: (PromptDelegate.PromptResponse) -> Unit) {
    val multiple = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
    val flat = remember(prompt) {
        buildList<Pair<PromptDelegate.ChoicePrompt.Choice, Boolean>> {
            prompt.choices.forEach { ch ->
                if (ch.items != null) { add(ch to true); ch.items!!.forEach { add(it to false) } } else add(ch to false)
            }
        }
    }
    var picked by remember { mutableStateOf(flat.filter { it.first.selected }.map { it.first.id }.toSet()) }
    AlertDialog(
        onDismissRequest = { respond(prompt.dismiss()) },
        title = prompt.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(flat) { (ch, header) ->
                    when {
                        ch.separator -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).size(1.dp).background(Space.Hairline))
                        header -> Text(ch.label, style = MaterialTheme.typography.labelSmall, color = Space.Text2, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        else -> Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = !ch.disabled) {
                                    if (multiple) picked = if (ch.id in picked) picked - ch.id else picked + ch.id
                                    else respond(prompt.confirm(ch))
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiple) Checkbox(ch.id in picked, null)
                            else Box(Modifier.size(10.dp).clip(CircleShape).background(if (ch.selected) Raven.accent else Color.Transparent))
                            Spacer(Modifier.width(12.dp))
                            Text(ch.label, color = if (ch.disabled) Space.Text3 else Space.Text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multiple) TextButton({ respond(prompt.confirm(picked.toTypedArray())) }) { Text("OK", color = Raven.accent) }
        },
        dismissButton = { TextButton({ respond(prompt.dismiss()) }) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}

@Composable
private fun DateTimeDialog(prompt: PromptDelegate.DateTimePrompt, respond: (PromptDelegate.PromptResponse) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(prompt) {
        val cal = Calendar.getInstance()
        val type = prompt.type
        val default = prompt.defaultValue.orEmpty()
        fun pad(n: Int) = n.toString().padStart(2, '0')
        val date = Regex("(\\d{4})-(\\d{2})(?:-(\\d{2}))?").find(default)
        if (date != null) cal.set(date.groupValues[1].toInt(), date.groupValues[2].toInt() - 1, date.groupValues[3].ifEmpty { "1" }.toInt())
        val time = Regex("(\\d{2}):(\\d{2})").find(default)
        if (time != null) { cal.set(Calendar.HOUR_OF_DAY, time.groupValues[1].toInt()); cal.set(Calendar.MINUTE, time.groupValues[2].toInt()) }
        fun pickTime(prefix: String) {
            TimePickerDialog(context, { _, h, m -> respond(prompt.confirm(prefix + "${pad(h)}:${pad(m)}")) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)
                .apply { setOnCancelListener { respond(prompt.dismiss()) } }.show()
        }
        if (type == PromptDelegate.DateTimePrompt.Type.TIME) {
            pickTime("")
        } else {
            DatePickerDialog(context, { _, y, mo, d ->
                val ymd = "$y-${pad(mo + 1)}-${pad(d)}"
                when (type) {
                    PromptDelegate.DateTimePrompt.Type.MONTH -> respond(prompt.confirm("$y-${pad(mo + 1)}"))
                    PromptDelegate.DateTimePrompt.Type.WEEK -> {
                        val c = Calendar.getInstance().apply { set(y, mo, d); minimalDaysInFirstWeek = 4; firstDayOfWeek = Calendar.MONDAY }
                        respond(prompt.confirm("$y-W${pad(c.get(Calendar.WEEK_OF_YEAR))}"))
                    }
                    PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> pickTime("${ymd}T")
                    else -> respond(prompt.confirm(ymd))
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                setOnCancelListener { respond(prompt.dismiss()) }
            }.show()
        }
    }
}

@Composable
private fun ColorDialog(prompt: PromptDelegate.ColorPrompt, respond: (PromptDelegate.PromptResponse) -> Unit) {
    val palette = (prompt.predefinedValues?.toList().orEmpty() + listOf(
        "#000000", "#FFFFFF", "#8EA2FF", "#7FD1C7", "#F2B46D", "#E3A6C8", "#E5484D", "#30A46C", "#0090FF", "#FFC53D", "#8E4EC6", "#6E7489",
    )).distinct()
    var hex by remember { mutableStateOf(prompt.defaultValue ?: "#8EA2FF") }
    Dialog("Pick a color", null, { respond(prompt.dismiss()) }, confirm = "OK" to { respond(prompt.confirm(hex)) }, dismiss = "Cancel" to { respond(prompt.dismiss()) }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            palette.forEach { h ->
                val color = runCatching { Color(android.graphics.Color.parseColor(h)) }.getOrNull() ?: return@forEach
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (h.equals(hex, true)) 3.dp else 1.dp, if (h.equals(hex, true)) Space.Text else Color(0x33FFFFFF), CircleShape)
                        .clickable { hex = h },
                )
            }
        }
        OutlinedTextField(hex, { hex = it }, singleLine = true, label = { Text("Hex") })
    }
}

@Composable
private fun FilePicker(prompt: PromptDelegate.FilePrompt, respond: (PromptDelegate.PromptResponse) -> Unit) {
    val context = LocalContext.current
    val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
    val one = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        respond(if (uri != null) prompt.confirm(context, uri) else prompt.dismiss())
    }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        respond(if (uris.isNotEmpty()) prompt.confirm(context, uris.toTypedArray()) else prompt.dismiss())
    }
    LaunchedEffect(prompt) {
        val types = prompt.mimeTypes?.filter { it.contains('/') }?.ifEmpty { null }?.toTypedArray() ?: arrayOf("*/*")
        if (multiple) many.launch(types) else one.launch(types)
    }
}

// ---------------------------------------------------------------------------------- permissions

private fun permissionInfo(kind: Int): Triple<String, RavenIcon, Boolean> = when (kind) {
    PermissionDelegate.PERMISSION_GEOLOCATION -> Triple("use your location", Icons.Location, true)
    PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> Triple("send you notifications", Icons.Bell, true)
    PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> Triple("store data permanently on this phone", Icons.File, true)
    PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> Triple("play protected (DRM) content", Icons.Play, true)
    PermissionDelegate.PERMISSION_STORAGE_ACCESS -> Triple("use its cookies on this site", Icons.Globe, true)
    PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS, PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> Triple("connect to devices on your local network", Icons.Globe, true)
    PermissionDelegate.PERMISSION_XR -> Triple("use virtual reality", Icons.Globe, true)
    else -> Triple("", Icons.Info, false)
}

@Composable
private fun PermissionPrompt(c: Container, p: UiPrompt.Permission, done: () -> Unit) {
    val kind = p.permission.permission
    // Silent autoplay is fine; sound waits for a tap; tracking-based access is refused.
    LaunchedEffect(p) {
        when (kind) {
            PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> { p.answer { p.result.complete(ContentPermission.VALUE_ALLOW) }; done() }
            PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE, PermissionDelegate.PERMISSION_TRACKING -> { p.answer { p.result.complete(ContentPermission.VALUE_DENY) }; done() }
        }
    }
    val (what, icon, ask) = permissionInfo(kind)
    if (!ask) return
    var remember by remember { mutableStateOf(true) }
    fun answer(allow: Boolean) {
        val value = if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY
        p.answer {
            if (remember) c.engine.runtime.storageController.setPermission(p.permission, value)
            p.result.complete(value)
        }
        done()
    }
    RavenSheet({ answer(false) }) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Raven.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Raven.accent, size = 24.dp)
            }
            Spacer(Modifier.width(16.dp))
            Text("Let ${p.host} $what?", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Only this site gets it. You can change your answer any time by tapping the padlock.",
            style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
        )
        if (!p.permission.privateMode) Row(Modifier.fillMaxWidth().clickable { remember = !remember }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(remember, { remember = it })
            Text("Remember for this site", style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Block", { answer(false) }, Modifier.weight(1f), style = PillStyle.Outline, height = 52.dp)
            PillButton("Allow", { answer(true) }, Modifier.weight(1f), height = 52.dp)
        }
    }
}

@Composable
private fun MediaPrompt(c: Container, p: UiPrompt.Media, done: () -> Unit) {
    val what = when {
        p.video.isNotEmpty() && p.audio.isNotEmpty() -> "use your camera and microphone"
        p.video.isNotEmpty() -> "use your camera"
        else -> "use your microphone"
    }
    // A private tab never remembers; an everyday one can, so the site doesn't ask again (Settings, Site permissions).
    val private = c.tabs.tabs.collectAsState().value.firstOrNull { it.id == p.tabId }?.private != false
    var remember by remember { mutableStateOf(false) }
    fun answer(allow: Boolean) {
        p.answer { if (allow) p.callback.grant(p.video.firstOrNull(), p.audio.firstOrNull()) else p.callback.reject() }
        if (remember && !private) {
            if (p.video.isNotEmpty()) c.sitePermissions.set(p.host, app.raven.browser.data.SitePermissions.Kind.CAMERA, allow)
            if (p.audio.isNotEmpty()) c.sitePermissions.set(p.host, app.raven.browser.data.SitePermissions.Kind.MICROPHONE, allow)
        }
        done()
    }
    RavenSheet({ answer(false) }) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Raven.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(if (p.video.isNotEmpty()) Icons.Camera else Icons.Mic, null, tint = Raven.accent, size = 24.dp)
            }
            Spacer(Modifier.width(16.dp))
            Text("Let ${p.host} $what?", style = MaterialTheme.typography.titleLarge)
        }
        Text(if (remember) "Raven won't ask this site again. Change it in Settings, Site permissions." else "Only while this page is open.", style = MaterialTheme.typography.bodySmall, color = Space.Text2, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp))
        if (!private) Row(Modifier.fillMaxWidth().clickable { remember = !remember }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(remember, { remember = it })
            Text("Remember for ${p.host}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Block", { answer(false) }, Modifier.weight(1f), style = PillStyle.Outline, height = 52.dp)
            PillButton("Allow", { answer(true) }, Modifier.weight(1f), height = 52.dp)
        }
    }
}

@Composable
private fun AndroidPermissionPrompt(p: UiPrompt.AndroidPermissions, done: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        p.answer { if (result.values.isNotEmpty() && result.values.all { it }) p.callback.grant() else p.callback.reject() }
        done()
    }
    LaunchedEffect(p) { launcher.launch(p.permissions.toTypedArray()) }
}
