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

/**
 * What's new in this version, once after an update, the way phones present an app's new features: a large title,
 * each change with its icon, a bold title and a line, and one button to carry on.
 */
@Composable
fun WhatsNewDialog(version: String, onClose: () -> Unit) {
    val c = LocalAppColors.current
    MorphCard(from = null, onDismiss = onClose) { close ->
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("What's New", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Text("in Leftovers ${version.substringBefore("-dev")}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { WhatsNewNotes() }
        com.leftovers.app.ui.components.PrimaryButton("Continue", { close {} }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun WhatsNewNotes() {
    val c = LocalAppColors.current
    WhatsNew.items.forEach { note ->
        Row(verticalAlignment = Alignment.Top) {
            Icon(noteIcon(note.icon), contentDescription = null, tint = c.accent, modifier = Modifier.padding(top = 2.dp).size(30.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(note.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(note.text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
}

private fun noteIcon(name: String) = when (name) {
    "palette" -> Lucide.Palette
    "party" -> Lucide.PartyPopper
    "search" -> Lucide.Search
    "receipt" -> Lucide.ReceiptText
    "bot" -> Lucide.BotMessageSquare
    else -> Lucide.Sparkles
}

/** The Notices page's state: a newer version the last check found, and the check itself. */
class NoticesViewModel(private val settings: com.leftovers.app.data.SettingsRepository) : androidx.lifecycle.ViewModel() {
    val settingsState = settings.settings
    val checking = kotlinx.coroutines.flow.MutableStateFlow(false)
    val checkResult = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** The newer version shown here has been seen: the bell's dot goes. */
    fun seen(release: UpdateCheck.Release) {
        viewModelScope.launch { settings.setUpdateSeen(UpdateCheck.encode(release)) }
    }

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
    LaunchedEffect(update) { update?.let(viewModel::seen) }
    // Leftovers Dev always looks, as testers want each new build; the app when its setting is on.
    LaunchedEffect(checkOn) { if ((checkOn == true || UpdateCheck.isDevBuild(context)) && update == null) viewModel.checkForUpdate(context) }
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
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { WhatsNewNotes() }
                }
            }
        }
    }
}
