package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.Money

@Composable
fun WelcomeScreen(defaultCurrency: String, onDone: (String) -> Unit) {
    val c = LocalAppColors.current
    var selected by rememberSaveable { mutableStateOf(defaultCurrency) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Glass(Modifier.size(68.dp).appear(0), strong = true, shape = RoundedCornerShape(22.dp)) {
            Icon(Lucide.Wallet, contentDescription = null, tint = c.accent, modifier = Modifier.size(32.dp).align(Alignment.Center))
        }
        Spacer(Modifier.height(24.dp))
        Text("Know what's\nleft.", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary, modifier = Modifier.appear(1))
        Spacer(Modifier.height(10.dp))
        Text(
            "Log a spend in two taps, see what's safe to spend today, and plan for the things you want.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
            modifier = Modifier.appear(2),
        )
        Spacer(Modifier.height(28.dp))
        Text("Your currency", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.appear(3))
        Spacer(Modifier.height(10.dp))
        Glass(Modifier.weight(1f).fillMaxWidth().appear(3), shape = RoundedCornerShape(28.dp)) {
            CurrencyList(selected = selected, onSelect = { selected = it.code }, modifier = Modifier.padding(12.dp))
        }
        PrimaryButton(
            "Continue with ${Money(selected).label}",
            { onDone(selected) },
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}
