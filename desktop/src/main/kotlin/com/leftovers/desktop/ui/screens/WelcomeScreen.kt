package com.leftovers.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.CurrencyOption
import com.leftovers.app.util.currencyOptions
import com.leftovers.desktop.data.Book
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.Mark
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.CardShape
import com.leftovers.desktop.ui.components.Dialog
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.pressable
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import java.util.Locale

/** The first time: start fresh in a currency, or open the phone's backup and carry on where the phone is. */
@Composable
fun WelcomeScreen() {
    val app = LocalApp.current
    val c = T.c
    val opener = remember { BackupOpener() }
    var picking by remember { mutableStateOf(false) }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(shown, enter = fadeIn(Motion.settle()) + slideInVertically(Motion.offset) { 40 }) {
            Column(Modifier.widthIn(max = 640.dp).padding(40.dp), horizontalAlignment = Alignment.Start) {
                Mark(44.dp)
                Spacer(Modifier.height(28.dp))
                Text("Know what's left.", style = T.type.hero, color = c.text)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Leftovers on your computer: everything the phone app does, on a bigger screen. Your data stays on this computer.",
                    style = T.type.body.copy(fontSize = T.type.body.fontSize * 1.15f), color = c.text2,
                )
                Spacer(Modifier.height(36.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Choice(Lucide.Download, "Open a phone backup", "Pick the file you saved from the phone's Settings, Backup.", Modifier.weight(1f)) { opener.start() }
                    Choice(Lucide.Sparkles, "Start fresh", "Choose your currency and start with the usual categories.", Modifier.weight(1f)) { picking = true }
                }
            }
        }
    }
    BackupDialogs(opener, askHow = false) { json, _ ->
        app.store.replaceWith(json)
        app.tell("Your phone's data is here")
    }
    CurrencyDialog(picking, onDismiss = { picking = false }) { code ->
        app.store.save(Book.starter(code).let { it.copy(prefs = it.prefs.copy(onboarded = true)) })
        picking = false
    }
}

@Composable
private fun Choice(icon: ImageVector, title: String, line: String, modifier: Modifier, onClick: () -> Unit) {
    val c = T.c
    Column(
        modifier.clip(CardShape).background(c.surface).border(1.dp, c.line, CardShape).pressable(scaleTo = 0.98f, onClick = onClick).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.raised), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = c.text) }
        Spacer(Modifier.height(10.dp))
        Text(title, style = T.type.heading, color = c.text)
        Text(line, style = T.type.small, color = c.text2)
    }
}

/** Every currency, searchable; the one this computer uses first. */
@Composable
fun CurrencyDialog(visible: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = T.c
    var query by remember { mutableStateOf("") }
    val local = remember { runCatching { java.util.Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() }
    val list = remember(query) {
        val all = currencyOptions
        val sorted = all.sortedByDescending { it.code == local }
        if (query.isBlank()) sorted else sorted.filter { it.code.contains(query, true) || it.name.contains(query, true) }
    }
    Dialog(visible, onDismiss, width = 440.dp) {
        Text("Your currency", style = T.type.title, color = c.text)
        Spacer(Modifier.height(14.dp))
        Field(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Search", leading = Lucide.Search, onEnter = { list.firstOrNull()?.let { onPick(it.code) } })
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(list, key = CurrencyOption::code) { o ->
                HoverRow({ onPick(o.code) }, Modifier.fillMaxWidth()) {
                    Text(o.code, style = T.type.bodyStrong, color = c.text, modifier = Modifier.width(52.dp))
                    Text(o.name, style = T.type.body, color = c.text2, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(o.symbol, style = T.type.body, color = c.text3)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button("Cancel", onDismiss, kind = ButtonKind.Quiet) }
    }
}
