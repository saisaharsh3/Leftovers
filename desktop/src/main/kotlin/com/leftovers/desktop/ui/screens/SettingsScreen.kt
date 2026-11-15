package com.leftovers.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.desktop.data.Theme
import com.leftovers.desktop.data.View
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Card
import com.leftovers.desktop.ui.components.Segmented
import com.leftovers.desktop.ui.components.SectionLabel
import com.leftovers.desktop.ui.components.SettingRow
import com.leftovers.desktop.ui.components.Toggle
import com.leftovers.desktop.ui.theme.T

@Composable
fun SettingsScreen(view: View) {
    val app = LocalApp.current
    val store = app.store
    val prefs = view.book.prefs
    val opener = remember { BackupOpener() }
    var saving by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(app.intent) { if (app.take("backup")) saving = true }
    Screen("Settings", maxWidth = 760.dp) {
        Group("Look") {
            SettingRow("Theme") {
                Segmented(listOf(Theme.SYSTEM to "Windows", Theme.LIGHT to "Light", Theme.DARK to "Dark"), prefs.theme, { t -> store.setPrefs { copy(theme = t) } })
            }
            SettingRow("Glass", "Frosted cards over soft colour, like the phone's look.") {
                Toggle(prefs.glass) { on -> store.setPrefs { copy(glass = on) } }
            }
        }
        Group("Money") {
            SettingRow("Currency", view.book.currency, onClick = { currency = true }) { Chevron() }
            SettingRow("Split bills as one", "A split bill shows as one entry with the people's shares under it.") {
                Toggle(prefs.splitsAsOneBill) { on -> store.setPrefs { copy(splitsAsOneBill = on) } }
            }
            SettingRow("Over today, show the new daily limit", "Instead of how much over, what each day after today can be.") {
                Toggle(prefs.showNewDailyLimit) { on -> store.setPrefs { copy(showNewDailyLimit = on) } }
            }
        }
        Group("Backup") {
            SettingRow("Save a backup", "A file your phone can open.", onClick = { saving = true }) { Chevron() }
            SettingRow("Open a backup", "From your phone, or from this computer.", onClick = { opener.start() }) { Chevron() }
            SettingRow("Where your data is", store.dir.absolutePath, onClick = { runCatching { java.awt.Desktop.getDesktop().open(store.dir) } }) {
                Icon(Lucide.ExternalLink, null, tint = T.c.text3, modifier = Modifier)
            }
        }
    }
    BackupDialogs(opener, askHow = true) { json, replace ->
        if (replace) {
            store.replaceWith(json)
            app.tell("Backup opened")
        } else {
            val plan = store.mergeWith(json)
            val n = plan.add.transactions.size
            app.tell(if (n == 0) "Nothing new in it" else "Added $n ${if (n == 1) "entry" else "entries"}")
        }
    }
    BackupSaveDialog(saving) { saving = false }
    CurrencyDialog(currency, onDismiss = { currency = false }) { code ->
        store.setCurrency(code)
        currency = false
    }
}

@Composable
fun Group(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label, Modifier)
        Card(Modifier.fillMaxWidth(), padding = 6.dp) { content() }
    }
}

@Composable
fun Chevron() = Icon(Lucide.ChevronRight, null, tint = T.c.text3)
