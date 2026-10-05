package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.leftovers.app.ui.components.AuroraBackground
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = LocalAppColors.current
    AuroraBackground()
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glass(Modifier.size(76.dp), strong = true, shape = RoundedCornerShape(26.dp)) {
            Icon(Lucide.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(32.dp).align(Alignment.Center))
        }
        Spacer(Modifier.height(24.dp))
        Text("Leftovers is locked", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Unlock with your fingerprint, face or screen lock.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Unlock", onUnlock, Modifier.width(220.dp), icon = Lucide.Lock)
    }
}
