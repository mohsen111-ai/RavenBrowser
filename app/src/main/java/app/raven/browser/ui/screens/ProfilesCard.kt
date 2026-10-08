package app.raven.browser.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.raven.browser.Container
import app.raven.browser.data.Profile
import app.raven.browser.data.Profiles
import app.raven.browser.ui.components.Divider
import app.raven.browser.ui.components.ListRow
import app.raven.browser.ui.tabs.NameDialog
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space

fun profileColor(p: Profile) = Color(Profiles.colors[p.color % Profiles.colors.size])

/**
 * The profiles in Settings: each person with their colour and open tabs; tap one to rename it, change its colour or
 * delete it; and a row to add another. Lives inside a Card.
 */
@Composable
fun ProfilesList(c: Container) {
    val profiles by c.profiles.all.collectAsState()
    val tabs by c.tabs.tabs.collectAsState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    profiles.forEachIndexed { i, p ->
        if (i > 0) Divider()
        val open = tabs.count { !it.private && it.profile == p.id }
        ListRow(
            p.name,
            detail = if (open == 1) "1 tab open" else "$open tabs open",
            leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(profileColor(p))) },
            chevron = true,
            onClick = { editing = p },
        )
    }
    Divider()
    ListRow("Add a profile", detail = "Another person: their own sign-ins, history and tabs", leading = { Icon(Icons.Plus, null, size = 18.dp, tint = Space.Text2) }, onClick = { adding = true })

    if (adding) {
        NameDialog("New profile", "Their own sign-ins, history and tabs. Settings, bookmarks and home sites are shared.", "", "Add", onDismiss = { adding = false }) { name ->
            c.profiles.add(name)
            adding = false
        }
    }
    editing?.let { p -> ProfileDialog(c, p, onDismiss = { editing = null }) }
}

@Composable
private fun ProfileDialog(c: Container, p: Profile, onDismiss: () -> Unit) {
    var name by remember(p.id) { mutableStateOf(p.name) }
    var color by remember(p.id) { mutableStateOf(p.color) }
    var deleting by remember(p.id) { mutableStateOf(false) }
    fun save() {
        c.profiles.edit(p.id, name = name, color = color)
        onDismiss()
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Its tabs close, and its sign-ins, cookies, site data and history are erased. Bookmarks and settings stay.", color = Space.Text2) },
            confirmButton = { TextButton({ c.tabs.removeProfile(p.id); deleting = false; onDismiss() }) { Text("Delete", color = Space.Solar) } },
            dismissButton = { TextButton({ deleting = false }) { Text("Cancel", color = Space.Text2) } },
            containerColor = Space.Surface,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Profiles.colors.forEachIndexed { i, argb ->
                        val on = i == color
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { color = i }
                                .semantics { contentDescription = Profiles.colorNames[i]; selected = on },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(argb)).then(if (on) Modifier.border(2.dp, Space.Text, CircleShape) else Modifier))
                        }
                    }
                }
                if (p.id.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).clickable(onClickLabel = "Delete this profile") { deleting = true }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Close, null, size = 16.dp, tint = Space.Solar)
                        Spacer(Modifier.width(10.dp))
                        Text("Delete this profile", style = MaterialTheme.typography.bodyMedium, color = Space.SolarText)
                    }
                }
            }
        },
        confirmButton = { TextButton(::save) { Text("Save", color = Raven.accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Space.Text2) } },
        containerColor = Space.Surface,
    )
}
