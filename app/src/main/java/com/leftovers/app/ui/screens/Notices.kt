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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leftovers.app.ui.components.GlassScreen
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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

/** The Notices page's state: a newer version the last check found, and the check itself. */
class NoticesViewModel(private val settings: com.leftovers.app.data.SettingsRepository) : androidx.lifecycle.ViewModel() {
    val settingsState = settings.settings
    val checking = kotlinx.coroutines.flow.MutableStateFlow(false)
    val checkResult = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Asks GitHub for a newer version: when asked, or when the page opens with the check turned on. */
    fun checkForUpdate(context: android.content.Context) {
        if (checking.value) return
        checking.value = true
        viewModelScope.launch {
            UpdateCheck.latest(UpdateCheck.isDevBuild(context)).fold(
                { release ->
                    if (UpdateCheck.isNewerThanInstalled(context, release)) settings.setUpdateAvailable(UpdateCheck.encode(release))
                    else checkResult.value = "You have the latest version"
                },
                { checkResult.value = "Couldn't check for a new version" },
            )
            checking.value = false
        }
    }
}

/** Notices, from the bell on Home: a newer version when there is one, and what's new in this one. */
@Composable
fun NoticesScreen(onBack: () -> Unit, viewModel: NoticesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = com.leftovers.app.ui.AppViewModelProvider.Factory)) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val s by viewModel.settingsState.collectAsStateWithLifecycle(null)
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val checkResult by viewModel.checkResult.collectAsStateWithLifecycle()
    val version = remember { WhatsNew.version(context) }
    val update = remember(s?.updateAvailable) { UpdateCheck.decode(s?.updateAvailable)?.takeIf { UpdateCheck.isNewerThanInstalled(context, it) } }
    val checkOn = s?.updateCheck
    LaunchedEffect(checkOn) { if (checkOn == true && update == null) viewModel.checkForUpdate(context) }
    GlassScreen(title = "Notices", subtitle = "What's new, and new versions", onBack = onBack) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            } else if (checkOn != null) {
                Glass(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                checking -> "Looking for a new version…"
                                checkResult != null -> checkResult!!
                                else -> "No new version found yet"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        if (!checking) Chip("Check now", { viewModel.checkForUpdate(context) }, icon = Lucide.Download)
                    }
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
