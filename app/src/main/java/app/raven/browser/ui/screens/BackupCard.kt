package app.raven.browser.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.raven.browser.Container
import app.raven.browser.data.Backup
import app.raven.browser.ui.components.Divider
import app.raven.browser.ui.components.ListRow
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A backup read and opened, waiting for "Restore and restart". */
private class Opened(val contents: JSONObject)

/**
 * Backup and restore in Settings: one file with tabs, history, bookmarks, home sites, settings and profiles, locked
 * with a password, to keep wherever you like (Proton Drive, for one). Lives inside a Card.
 */
@Composable
fun BackupRows(c: Container) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askNew by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf<CharArray?>(null) }
    var file by remember { mutableStateOf<ByteArray?>(null) }
    var wrong by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Opened?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun say(text: String) { c.engine.messages.tryEmit(text) }

    // Backing up: the password first, then where the file goes.
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val pw = newPassword
        newPassword = null
        if (uri == null || pw == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = runCatching {
                val plain = c.backupContents().toString().toByteArray()
                val sealed = withContext(Dispatchers.Default) { Backup.seal(plain, pw) }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sealed) } }
            }.isSuccess
            pw.fill(' ')
            busy = false
            say(if (ok) "Backup saved" else "Couldn't save the backup there")
        }
    }
    // Restoring: the file first, then its password.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull() }
            if (bytes == null) say("Couldn't read that file") else { wrong = false; file = bytes }
        }
    }

    ListRow(
        "Back up", detail = "Tabs, history, bookmarks, home sites, settings and profiles, in one file locked with a password. Never your sign-ins to websites.",
        icon = Icons.Download, chevron = true, onClick = { if (!busy) askNew = true },
    )
    Divider()
    ListRow("Restore from a backup", detail = "Puts a backup file's tabs, history, bookmarks and settings back", icon = Icons.History, chevron = true, onClick = { if (!busy) pick.launch(arrayOf("*/*")) })

    if (askNew) {
        NewPasswordDialog(onDismiss = { askNew = false }) { pw ->
            askNew = false
            newPassword = pw
            save.launch("Raven backup ${LocalDate.now()}.${Backup.EXTENSION}")
        }
    }
    file?.let { bytes ->
        PasswordDialog(wrong, onDismiss = { file = null }) { pw ->
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.Default) { runCatching { JSONObject(String(Backup.open(bytes, pw))) } }
                pw.fill(' ')
                busy = false
                result.onSuccess { file = null; opened = Opened(it) }.onFailure { e ->
                    when (e) {
                        is Backup.WrongPassword -> wrong = true
                        else -> { file = null; say("That isn't a Raven backup") }
                    }
                }
            }
        }
    }
    opened?.let { o ->
        val made = o.contents.optLong("made").takeIf { it > 0 }?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        }
        val tabs = o.contents.optJSONObject("tabs")?.optJSONArray("tabs")?.length() ?: 0
        val pages = o.contents.optJSONArray("history")?.length() ?: 0
        val marks = o.contents.optJSONArray("bookmarks")?.length() ?: 0
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (made != null) Text("Made on $made", style = MaterialTheme.typography.bodyMedium)
                    Text("$tabs tabs · $pages pages of history · $marks bookmarks", style = MaterialTheme.typography.bodyMedium, color = Space.Text2)
                    Text(
                        "Your open tabs, settings and profiles are replaced by the backup's. Its history and bookmarks are added to yours. Raven then restarts.",
                        style = MaterialTheme.typography.bodySmall, color = Space.Text2,
                    )
                }
            },
            confirmButton = {
                TextButton({
                    opened = null
                    busy = true
                    scope.launch {
                        c.restoreBackup(o.contents)
                        (context as? Activity)?.let { restart(it) }
                    }
                }) { Text("Restore and restart", color = Raven.accent) }
            },
            dismissButton = { TextButton({ opened = null }) { Text("Cancel", color = Space.Text2) } },
            containerColor = Space.Surface,
        )
    }
}

@Composable
private fun NewPasswordDialog(onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    var one by remember { mutableStateOf("") }
    var two by remember { mutableStateOf("") }
    val long = one.length >= 8
    val same = one == two
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A password for the backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The file is locked with it. Keep it safe: without it, nobody can open the backup, not even Raven.",
                    style = MaterialTheme.typography.bodySmall, color = Space.Text2,
                )
                OutlinedTextField(
                    one, { one = it }, singleLine = true, label = { Text("Password (8 or more characters)") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    two, { two = it }, singleLine = true, label = { Text("The same again") }, isError = two.isNotEmpty() && !same,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton({ onDone(one.toCharArray()) }, enabled = long && same) { Text("Choose where to save", color = if (long && same) Raven.accent else Space.Text3) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}

@Composable
private fun PasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("The backup's password") },
        text = {
            OutlinedTextField(
                pw, { pw = it }, singleLine = true, label = { Text("Password") }, isError = wrong,
                supportingText = if (wrong) ({ Text("That isn't its password") }) else null,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = { TextButton({ onDone(pw.toCharArray()) }, enabled = pw.isNotEmpty()) { Text("Open", color = Raven.accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}
