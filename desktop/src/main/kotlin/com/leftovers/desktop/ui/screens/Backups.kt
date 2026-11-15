package com.leftovers.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.leftovers.app.util.BackupCrypto
import com.leftovers.desktop.data.Codec
import com.leftovers.desktop.data.NotABackup
import com.leftovers.desktop.data.PasswordNeeded
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.Dialog
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.theme.T
import org.json.JSONObject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The Windows file dialogs. */
object Files {
    fun open(title: String, extensions: List<String> = listOf("json")): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
        d.file = extensions.joinToString(";") { "*.$it" }
        d.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        d.isVisible = true
        return d.files.firstOrNull()
    }

    fun save(title: String, suggested: String): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.SAVE)
        d.file = suggested
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name)
    }
}

/**
 * Opening a backup: picks the file, asks for its password when it has one, and hands over its data. [askHow]
 * offers Replace or Add to what's here; without it, the backup replaces everything (a fresh start).
 */
class BackupOpener {
    var file by mutableStateOf<File?>(null)
    var asking by mutableStateOf(false)
    var wrong by mutableStateOf(false)
    var opened by mutableStateOf<JSONObject?>(null)
    var error by mutableStateOf<String?>(null)

    fun start() {
        val f = Files.open("Open a Leftovers backup") ?: return
        file = f
        tryOpen(null)
    }

    fun tryOpen(password: String?) {
        val f = file ?: return
        try {
            val json = Codec.open(f.readText(), password)
            if (json.optString("app") != "leftovers" && json.optString("app") != "cash-tracker") throw NotABackup()
            opened = json
            asking = false
        } catch (e: PasswordNeeded) {
            wrong = e.wrong
            asking = true
        } catch (e: NotABackup) {
            error = "That file isn't a Leftovers backup."
        } catch (e: Exception) {
            error = "That backup couldn't be read."
        }
    }

    fun reset() { file = null; asking = false; wrong = false; opened = null; error = null }
}

@Composable
fun BackupDialogs(opener: BackupOpener, askHow: Boolean, onOpened: (JSONObject, replace: Boolean) -> Unit) {
    val c = T.c
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Dialog(opener.asking, onDismiss = { opener.reset(); password = "" }) {
        Text("This backup has a password", style = T.type.title, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text("Type the password it was saved with on your phone.", style = T.type.body, color = c.text2)
        Spacer(Modifier.height(18.dp))
        Field(password, { password = it; opener.wrong = false }, Modifier.fillMaxWidth(), placeholder = "Password", password = true, focusRequester = focus, onEnter = { opener.tryOpen(password) })
        if (opener.wrong) {
            Spacer(Modifier.height(8.dp))
            Text("That password isn't right.", style = T.type.small, color = c.negative)
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
            Button("Cancel", { opener.reset(); password = "" }, kind = ButtonKind.Quiet)
            Button("Open", { opener.tryOpen(password) }, kind = ButtonKind.Primary, enabled = password.isNotEmpty())
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    val json = opener.opened
    LaunchedEffect(json) {
        if (json != null && !askHow) {
            onOpened(json, true)
            opener.reset(); password = ""
        }
    }
    Dialog(json != null && askHow, onDismiss = { opener.reset(); password = "" }) {
        val count = json?.optJSONArray("transactions")?.length() ?: 0
        Text("Open this backup", style = T.type.title, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(
            "It has $count ${if (count == 1) "entry" else "entries"}. Add what's new to what's here, or replace everything here with it.",
            style = T.type.body, color = c.text2,
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
            Button("Cancel", { opener.reset(); password = "" }, kind = ButtonKind.Quiet)
            Button("Replace everything", { onOpened(json!!, true); opener.reset(); password = "" }, kind = ButtonKind.Danger)
            Button("Add what's new", { onOpened(json!!, false); opener.reset(); password = "" }, kind = ButtonKind.Primary)
        }
    }
    Dialog(opener.error != null, onDismiss = { opener.reset() }) {
        Text("Couldn't open it", style = T.type.title, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(opener.error.orEmpty(), style = T.type.body, color = c.text2)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button("OK", { opener.reset() }, kind = ButtonKind.Primary) }
    }
}

/** Saving a backup the phone can open: with a password when one is given. */
@Composable
fun BackupSaveDialog(visible: Boolean, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val c = T.c
    var password by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(true) }
    Dialog(visible, onDismiss) {
        Text("Save a backup", style = T.type.title, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text("A file your phone can open in Settings, Backup. Add a password to keep it private.", style = T.type.body, color = c.text2)
        Spacer(Modifier.height(18.dp))
        Field(password, { password = it }, Modifier.fillMaxWidth(), placeholder = "Password (optional)", password = true)
        Spacer(Modifier.height(10.dp))
        com.leftovers.desktop.ui.components.SettingRow("Include photos", "Receipts and bill photos go in the file too.") {
            com.leftovers.desktop.ui.components.Toggle(photos) { photos = it }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
            Button("Cancel", onDismiss, kind = ButtonKind.Quiet)
            Button("Save…", {
                val day = java.time.LocalDate.now()
                val f = Files.save("Save a backup", "leftovers-backup-$day.json")
                if (f != null) {
                    val json = app.store.exportJson(photos).toString()
                    val text = if (password.isNotEmpty()) BackupCrypto.encrypt(json, password).toString(2) else json
                    runCatching { f.writeText(text) }
                        .onSuccess { app.tell("Backup saved"); onDismiss(); password = "" }
                        .onFailure { app.tell("Couldn't save there") }
                }
            }, kind = ButtonKind.Primary)
        }
    }
}
