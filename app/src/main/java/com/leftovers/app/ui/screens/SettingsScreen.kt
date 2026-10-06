package com.leftovers.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.AppSettings
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.ThemeMode
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.IconTile
import com.leftovers.app.ui.components.ListRow
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SectionHeader
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AutoBackup
import com.leftovers.app.util.BackupManager
import com.leftovers.app.util.friendlyLabel
import com.leftovers.app.util.CsvExporter
import com.leftovers.app.util.Reminders
import com.leftovers.app.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val backup: BackupManager,
    val assistant: com.leftovers.app.ai.AssistantSettings,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setCurrency(code) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setBudgetAlerts(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBudgetAlerts(enabled) }
    }

    fun setReminder(enabled: Boolean, minutes: Int) {
        viewModelScope.launch { settingsRepository.setReminder(enabled, minutes) }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLock(enabled) }
    }

    fun setBillReminders(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBillReminders(enabled) }
    }

    /** Remembers [tree] for weekly backups and makes the first one straight away. */
    fun enableAutoBackup(context: Context, tree: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupDir(tree.toString())
            AutoBackup.runNow(context, tree).fold(
                {
                    settingsRepository.setAutoBackupDone(System.currentTimeMillis())
                    onResult("Backed up $it entries. Next backup in a week")
                },
                { onResult("Couldn't back up to that folder: ${it.message}") },
            )
        }
    }

    fun disableAutoBackup(context: Context, dir: String?) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupDir(null)
            dir?.let { runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        }
    }

    fun setSmsDetection(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSmsDetection(enabled) }
    }

    fun backupTo(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            backup.export(uri).fold(
                { onResult("Backed up $it entries") },
                { onResult("Backup failed: ${it.message}") },
            )
        }
    }

    fun restoreFrom(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            backup.import(uri).fold(
                { onResult("Restored $it entries") },
                { onResult("Couldn't restore: ${it.message}") },
            )
        }
    }

    fun export(context: Context, onEmpty: () -> Unit) {
        viewModelScope.launch {
            val items = repository.getAllTransactions()
            if (items.isEmpty()) {
                onEmpty()
                return@launch
            }
            val uri = CsvExporter.writeCsv(context, items)
            context.startActivity(CsvExporter.shareIntent(uri))
        }
    }

    fun deleteAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAllData()
            settingsRepository.clearBudgetData()
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showAssistant by rememberSaveable { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    fun toast(text: String) = scope.launch { snackbar.showSnackbar(text) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.backupTo(uri) { toast(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            viewModel.enableAutoBackup(context, tree) { toast(it) }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setSmsDetection(granted)
        if (!granted) toast("SMS permission is needed to spot bank payments")
    }

    GlassScreen(title = "Settings", onBack = onBack, snackbar = snackbar) { padding ->
        val s = settings ?: return@GlassScreen
        val money = Money(s.currencyCode)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 48.dp),
        ) {
            item { SectionHeader("Appearance") }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SegmentedToggle(
                            listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM),
                            s.themeMode,
                            { if (it == ThemeMode.SYSTEM) "Auto" else it.label },
                            viewModel::setTheme,
                            Modifier.fillMaxWidth(),
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Spacer(Modifier.height(8.dp))
                            ToggleRow(Lucide.Palette, "Accent from wallpaper", s.dynamicColor, viewModel::setDynamicColor, inset = false)
                        }
                    }
                }
            }

            item { SectionHeader("Security & reminders") }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        ToggleRow(Lucide.Lock, "App lock", s.appLock, { on ->
                            if (on && !canLock(context)) toast("Set up a screen lock on your phone first") else viewModel.setAppLock(on)
                        })
                        RowDivider()
                        ToggleRow(Lucide.Repeat, "Remind me before bills", s.billReminders, { on ->
                            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            viewModel.setBillReminders(on)
                        })
                        RowDivider()
                        ToggleRow(Lucide.Bell, "Daily reminder", s.reminderEnabled, { on ->
                            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            viewModel.setReminder(on, s.reminderMinutes)
                        })
                        if (s.reminderEnabled) {
                            RowDivider()
                            NavRow(Lucide.CalendarClock, "Remind me at", Reminders.label(s.reminderMinutes)) { showTime = true }
                        }
                        RowDivider()
                        ToggleRow(Lucide.Smartphone, "Detect bank SMS", s.smsDetection, { on ->
                            if (on) smsPermission.launch(Manifest.permission.RECEIVE_SMS) else viewModel.setSmsDetection(false)
                        })
                    }
                }
                if (s.smsDetection) {
                    Text(
                        "New debit messages from your bank show up on Home for you to confirm. Nothing is added without your tap, and messages never leave the phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    )
                }
            }

            item { SectionHeader("AI assistant") }
            item {
                val assistant by viewModel.assistant.config.collectAsStateWithLifecycle()
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        NavRow(Lucide.MessageCircle, "AI assistant", assistant?.let { "${it.provider.label} · connected" } ?: "Not connected") { showAssistant = true }
                    }
                }
            }

            item { SectionHeader("General") }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        NavRow(Lucide.Coins, "Currency", "${money.currency.displayName} (${money.symbol})") { showCurrency = true }
                        RowDivider()
                        ToggleRow(Lucide.Bell, "Budget alerts", s.budgetAlerts, viewModel::setBudgetAlerts)
                        RowDivider()
                        NavRow(Lucide.Tag, "Categories", null, onManageCategories)
                        RowDivider()
                        NavRow(Lucide.Wallet, "Accounts", "Cash, bank, cards", onOpenAccounts)
                    }
                }
            }

            item { SectionHeader("Data") }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        NavRow(Lucide.Download, "Back up", "To a file or Google Drive") {
                            createBackup.launch("leftovers-backup-${java.time.LocalDate.now()}.json")
                        }
                        RowDivider()
                        NavRow(Lucide.Repeat, "Restore", "From a backup file") { openBackup.launch(arrayOf("application/json", "*/*")) }
                        RowDivider()
                        ToggleRow(Lucide.CalendarClock, "Weekly automatic backup", s.autoBackupDir != null, { on ->
                            if (on) pickBackupFolder.launch(null) else viewModel.disableAutoBackup(context, s.autoBackupDir)
                        })
                        RowDivider()
                        NavRow(Lucide.ReceiptText, "Export to CSV", "Excel, Sheets") {
                            viewModel.export(context) { scope.launch { snackbar.showSnackbar("Nothing to export yet") } }
                        }
                        RowDivider()
                        ListRow(
                            "Delete all entries",
                            leading = { IconTile(Lucide.Trash2, c.negative, size = 38.dp) },
                            onClick = { confirmWipe = true },
                        )
                    }
                }
                if (s.autoBackupDir != null) {
                    Text(
                        "Saves a backup to your chosen folder every week and keeps the last 4." +
                            if (s.autoBackupLast > 0) " Last backup: ${java.time.Instant.ofEpochMilli(s.autoBackupLast).atZone(java.time.ZoneId.systemDefault()).toLocalDate().friendlyLabel()}." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    )
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.Lock, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stored only on this device · Leftovers 1.0.6", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, textAlign = TextAlign.Center)
                }
            }
            item {
                val uriHandler = LocalUriHandler.current
                Text(
                    "Made by C. Sai Saharsh · GitHub",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable { uriHandler.openUri("https://github.com/saisaharsh3") }
                        .padding(vertical = 8.dp),
                )
            }
        }

        if (showAssistant) AssistantSetupSheet(onDismiss = { showAssistant = false })

        if (showCurrency) {
            CurrencyPickerSheet(
                selected = s.currencyCode,
                onDismiss = { showCurrency = false },
                onSelect = {
                    viewModel.setCurrency(it)
                    showCurrency = false
                },
            )
        }

        pendingRestore?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("Restore this backup?") },
                text = { Text("Everything currently in the app will be replaced with the backup's contents.", color = c.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRestore = null
                        viewModel.restoreFrom(uri) { toast(it) }
                    }) { Text("Restore", color = c.textPrimary) }
                },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        if (showTime) {
            val state = rememberTimePickerState(s.reminderMinutes / 60, s.reminderMinutes % 60)
            AlertDialog(
                onDismissRequest = { showTime = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("Remind me at") },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setReminder(true, state.hour * 60 + state.minute)
                        showTime = false
                    }) { Text("Done", color = c.textPrimary) }
                },
                dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        if (confirmWipe) {
            AlertDialog(
                onDismissRequest = { confirmWipe = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("Delete everything?") },
                text = { Text("All entries and budgets will be permanently deleted. Categories, subscriptions and goals are kept.", color = c.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmWipe = false
                        viewModel.deleteAll { scope.launch { snackbar.showSnackbar("All entries deleted") } }
                    }) { Text("Delete", color = c.negative) }
                },
                dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel", color = c.textSecondary) } },
            )
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    val c = LocalAppColors.current
    ListRow(
        title,
        leading = { IconTile(icon, c.textPrimary, size = 38.dp) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(end = 6.dp))
                }
                Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit, inset: Boolean = true) {
    val c = LocalAppColors.current
    ListRow(
        title,
        modifier = if (inset) Modifier else Modifier.padding(horizontal = (0).dp),
        leading = { IconTile(icon, c.textPrimary, size = 38.dp) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.accent,
                    checkedThumbColor = c.onAccent,
                    uncheckedTrackColor = c.glass,
                    uncheckedBorderColor = c.borderTop,
                    uncheckedThumbColor = c.textSecondary,
                ),
            )
        },
        onClick = { onChange(!checked) },
    )
}

private fun canLock(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    } else {
        context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure
    }
}