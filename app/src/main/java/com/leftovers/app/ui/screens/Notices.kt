package com.leftovers.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.IconTile
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.UpdateCheck
import com.leftovers.app.util.WhatsNew

/** What's new in this version, as a pop-up once after an update. */
@Composable
fun WhatsNewDialog(version: String, onClose: () -> Unit) {
    val c = LocalAppColors.current
    GlassAlertDialog(
        onDismissRequest = onClose,
        title = { Text("What's new") },
        text = {
            Column {
                Text(version, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(10.dp))
                WhatsNewNotes()
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Got it", color = c.accent) } },
    )
}

@Composable
private fun WhatsNewNotes() {
    val c = LocalAppColors.current
    WhatsNew.notes.forEach { note ->
        Row(Modifier.padding(top = 4.dp)) {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = c.accent)
            Spacer(Modifier.width(8.dp))
            Text(note, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

/**
 * Home's notices: a newer version when there is one, and what's new in this one.
 * [checkResult] says what the last check found, or null before one.
 */
@Composable
fun NoticesSheet(
    version: String,
    update: UpdateCheck.Release?,
    checking: Boolean,
    checkResult: String?,
    onCheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Notices", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            if (update != null) {
                val dev = update.version.startsWith("Dev")
                Glass(Modifier.fillMaxWidth(), strong = true, onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.url))) }
                }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Lucide.Download, c.accent, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (dev) "A new Dev build is out" else "Leftovers ${update.version} is out", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(if (dev) "${update.version} · tap to download it" else "Tap to download it", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            checking -> "Looking for a new version…"
                            checkResult != null -> checkResult
                            else -> "No new version found yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!checking) Chip("Check now", onCheck, icon = Lucide.Download)
                }
            }
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Lucide.Sparkles, c.accent, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("What's new", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(version, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    WhatsNewNotes()
                }
            }
        }
    }
}
