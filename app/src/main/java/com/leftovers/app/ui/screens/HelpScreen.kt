package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.HelpGuide

/**
 * Help & features: every feature in a line or two, where it is, and a way to open it. A search finds one by any
 * word, so "transfer" or "pdf" goes straight to the answer.
 */
@Composable
fun HelpScreen(onBack: () -> Unit, onOpen: (HelpGuide.Place) -> Unit) {
    val c = LocalAppColors.current
    var query by rememberSaveable { mutableStateOf("") }
    val found = HelpGuide.search(query)
    GlassScreen(title = "Help & features", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                GlassTextField(query, { query = it.take(60) }, placeholder = "Search, e.g. transfer, PDF, backup")
            }
            if (found.isEmpty()) {
                item(key = "none") { EmptyState(Lucide.Search, "Nothing found", "Try another word, or ask on GitHub from Settings.") }
            }
            found.groupBy { it.section }.forEach { (section, list) ->
                item(key = "h-$section") {
                    Text(section, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
                }
                item(key = "s-$section") {
                    Glass(Modifier.fillMaxWidth()) {
                        Column {
                            list.forEachIndexed { i, item ->
                                if (i > 0) RowDivider()
                                HelpRow(item, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpRow(item: HelpGuide.Item, onOpen: (HelpGuide.Place) -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Text(item.text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.where, style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.weight(1f))
            item.place?.let { place ->
                Spacer(Modifier.width(8.dp))
                Text(
                    "Open ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary,
                    modifier = Modifier.pressable({
                        if (place == HelpGuide.Place.SETTINGS) SettingsJump.open(item.where)
                        onOpen(place)
                    }, pressedScale = 0.95f).padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
