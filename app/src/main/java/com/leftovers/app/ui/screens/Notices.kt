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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leftovers.app.ui.components.GlassScreen
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import com.kyant.backdrop.backdrops.layerBackdrop
import com.leftovers.app.ui.components.RoundButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.leftovers.app.ui.components.pressable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxSize
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.IconTile
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.UpdateCheck
import com.leftovers.app.util.WhatsNew

/**
 * What's new in this version, once after an update: a glass sheet, like the app's others, with the version large on
 * top as phones show a new system version, then each change with its icon, title and line, and one button.
 */
@Composable
fun WhatsNewDialog(version: String, onClose: () -> Unit) {
    val c = LocalAppColors.current
    GlassSheet(onClose) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            VersionHero(version, compact = true)
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { WhatsNewNotes() }
            com.leftovers.app.ui.components.PrimaryButton("Continue", onClose, Modifier.fillMaxWidth())
        }
    }
}

/** A button as a phone's update page has them: a bright pill for the main action, a glass one for the other. */
@Composable
private fun PillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        Modifier.fillMaxWidth().height(52.dp)
            .pressable(onClick, pressedScale = 0.97f)
            .clip(shape)
            .background(if (filled) c.textPrimary.copy(alpha = 0.92f) else c.textPrimary.copy(alpha = 0.1f))
            .border(1.dp, c.textPrimary.copy(alpha = if (filled) 0f else 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (filled) c.background else c.textPrimary)
    }
}

/**
 * The update page's artwork: a deep wash with large soft orbs and a sweep of colour from the app's accent and glow
 * colours, light on the light theme and dark on the dark one, so the version and the card always read.
 */
@Composable
private fun UpdateArtwork(modifier: Modifier) {
    val c = LocalAppColors.current
    // Light artwork under dark text, dark under light text, whatever the theme (over a photo they can differ).
    val dark = c.textPrimary.luminance() > 0.5f
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(if (dark) listOf(Color(0xFF0B0A1F), Color(0xFF15102E), Color(0xFF0A0A14)) else listOf(Color(0xFFF4F2FF), Color(0xFFE9E4FA), Color(0xFFF6F4FB))))
        fun orb(color: Color, x: Float, y: Float, r: Float, a: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = a * 0.45f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(x, y), radius = r),
            radius = r, center = androidx.compose.ui.geometry.Offset(x, y),
        )
        val glow = if (dark) 1f else 0.7f
        orb(c.auroraA, w * 0.15f, h * 0.18f, w * 0.9f, 0.55f * glow)
        // A sweep of colour across the middle, like light through glass.
        val sweep = androidx.compose.ui.graphics.Path().apply {
            moveTo(-w * 0.1f, h * 0.62f)
            cubicTo(w * 0.3f, h * 0.48f, w * 0.65f, h * 0.72f, w * 1.1f, h * 0.55f)
            lineTo(w * 1.1f, h * 0.68f)
            cubicTo(w * 0.65f, h * 0.84f, w * 0.3f, h * 0.6f, -w * 0.1f, h * 0.76f)
            close()
        }
        drawPath(sweep, Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.0f), c.accent.copy(alpha = 0.55f * glow), c.auroraB.copy(alpha = 0.5f * glow), c.auroraB.copy(alpha = 0f))))
        orb(c.accent, w * 0.85f, h * 0.55f, w * 0.7f, 0.45f * glow)
        orb(c.auroraB, w * 0.05f, h * 0.9f, w * 0.75f, 0.4f * glow)
    }
}

/** The version as a phone's update page shows it: the number large, the app's name and build under it. */
@Composable
private fun VersionHero(version: String, compact: Boolean = false) {
    val c = LocalAppColors.current
    val number = version.substringBefore("-").split('.').take(2).joinToString(".")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            number,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = if (compact) 72.sp else 120.sp,
                lineHeight = if (compact) 76.sp else 124.sp,
                letterSpacing = (-4).sp,
                brush = Brush.verticalGradient(listOf(c.textPrimary, c.textPrimary.copy(alpha = 0.78f))),
            ),
        )
        Text(if (compact) "What's New in Leftovers" else "Leftovers", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        if (!compact) Text(version, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
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
    "handcoins" -> Lucide.HandCoins
    "link" -> Lucide.Link
    "zap" -> Lucide.Zap
    "landmark" -> Lucide.Landmark
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
                { checkResult.value = "Couldn't check: " + (it.message?.takeIf { m -> m.startsWith("GitHub") } ?: "no connection. Try again when you're online.") },
            )
            checking.value = false
        }
    }
}

/**
 * Updates, from the bell on Home, laid out like a phone's software update page: the version large, and a glass card
 * at the bottom saying whether it's up to date, with Highlights (what's new) and the full update notes.
 */
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
    var highlights by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(update) { update?.let(viewModel::seen) }
    // Leftovers Dev always looks, as testers want each new build; the app when its setting is on.
    LaunchedEffect(checkOn) { if ((checkOn == true || UpdateCheck.isDevBuild(context)) && update == null && UpdateCheck.autoCheckDue(context)) viewModel.checkForUpdate(context) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    // This version's notes: the Dev build's page, or the release's.
    val notesUrl = if (UpdateCheck.isDevBuild(context)) "https://github.com/saisaharsh3/Leftovers/releases/tag/dev-build"
    else "https://github.com/saisaharsh3/Leftovers/releases/tag/v${version.substringBefore("-")}"
    // The page's own artwork, whatever the app's background: soft colour behind a large version, as a phone's
    // update page has. The glass card shows it through (Android 13+), recorded here for it.
    val art = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        UpdateArtwork(Modifier.fillMaxSize().layerBackdrop(art))
        CompositionLocalProvider(
            com.leftovers.app.ui.components.LocalBackgroundBackdrop provides (if (android.os.Build.VERSION.SDK_INT >= 33) art else null),
        ) {
            GlassScreen(
                title = "Updates",
                onBack = onBack,
                actions = {
                    if (update == null && !checking) RoundButton(Lucide.RotateCcw, "Check again", { viewModel.checkForUpdate(context) })
                },
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 56.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                ) {
                    VersionHero(version)
                    Spacer(Modifier.height(200.dp))
                    Glass(Modifier.fillMaxWidth(), strong = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val dev = update?.version?.startsWith("Dev") == true
                            Text(
                                when {
                                    update != null -> if (dev) "A new Dev build is out" else "Leftovers ${update.version} is out"
                                    checking -> "Looking for a new version…"
                                    checkResult?.startsWith("Couldn't check") == true -> "Couldn't check right now"
                                    else -> "Version up to date"
                                },
                                style = MaterialTheme.typography.titleLarge, color = c.textPrimary,
                            )
                            Text(update?.version?.takeIf { dev } ?: version, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                            // Why a check didn't work, in a line.
                            checkResult?.takeIf { update == null && it.startsWith("Couldn't check") }?.let {
                                Text(it.removePrefix("Couldn't check: ").replaceFirstChar { ch -> ch.uppercase() }, style = MaterialTheme.typography.bodySmall, color = c.warning)
                            }
                            Spacer(Modifier.height(6.dp))
                            if (update != null) {
                                PillButton("Download", filled = true) { open(update.url) }
                                PillButton("Highlights", filled = false) { highlights = true }
                            } else {
                                PillButton("Highlights", filled = true) { highlights = true }
                                PillButton("View update notes", filled = false) { open(notesUrl) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (highlights) WhatsNewDialog(version) { highlights = false }
}
