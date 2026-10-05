package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.CurrencyOption
import com.leftovers.app.util.currencyOptions

/** Searchable list of currencies, shared by onboarding and settings. */
@Composable
fun CurrencyList(selected: String, onSelect: (CurrencyOption) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = currencyOptions.filter {
        query.isBlank() || it.code.contains(query, true) || it.name.contains(query, true)
    }
    Column(modifier) {
        GlassTextField(query, { query = it }, placeholder = "Search currency")
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(filtered, key = { it.code }) { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable({ onSelect(option) }, pressedScale = 0.98f)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.symbol, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.width(52.dp), maxLines = 1)
                    Column(Modifier.weight(1f)) {
                        Text(option.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(option.code, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    if (option.code == selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Lucide.Check, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CurrencyPickerSheet(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    GlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Currency", style = MaterialTheme.typography.headlineSmall, color = LocalAppColors.current.textPrimary, modifier = Modifier.padding(bottom = 14.dp))
            CurrencyList(selected, { onSelect(it.code) }, Modifier.heightIn(max = 520.dp))
        }
    }
}
