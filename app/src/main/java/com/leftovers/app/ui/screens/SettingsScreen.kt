package com.leftovers.app.ui.screens

import com.leftovers.app.util.BackupPasswordException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val backup: BackupManager,
    val assistant: com.leftovers.app.ai.AssistantSettings,
    private val planning: com.leftovers.app.data.PlanningRepository,
    private val accounts: com.leftovers.app.data.AccountRepository,
    private val resetEverything: suspend () -> Unit,
) : ViewModel() {

    /** A CSV file read and waiting for the user to confirm: what would be added, and what's skipped. */
    data class CsvPreview(val toAdd: List<com.leftovers.app.data.Transaction>, val duplicates: Int, val unreadable: Int, val unmatched: Int, val noCategory: Int)

    var csvPreview by mutableStateOf<CsvPreview?>(null)
        private set

    /** Reads [uri] and prepares the entries; nothing is saved until [confirmCsvImport]. */
    fun readCsv(context: android.content.Context, uri: android.net.Uri, onError: (String) -> Unit) {
        viewModelScope.launch {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (text == null) {
                onError("Couldn't open that file")
                return@launch
            }
            val parsed = com.leftovers.app.util.CsvImporter.parse(text)
            parsed.error?.let {
                onError(it)
                return@launch
            }
            val cats = repository.categories.first()
            val accountList = accounts.accounts.first()
            val defaultAccount = settingsRepository.settings.first().defaultAccountId
            // A category is matched by name; anything else goes to the type's "Other" category.
            fun fallback(type: com.leftovers.app.data.TxType) = cats.filter { it.type == type }.let { list ->
                list.firstOrNull { it.name.lowercase() in setOf("other", "others", "miscellaneous", "misc", "general") } ?: list.firstOrNull()
            }
            val existing = repository.getAllTransactions().map { Triple(it.epochDay, it.amountMinor, it.type to it.note.trim().lowercase()) }.toMutableSet()
            var duplicates = 0
            var unmatched = 0
            var noCategory = 0
            val toAdd = parsed.rows.mapNotNull { row ->
                val key = Triple(row.date.toEpochDay(), row.amountMinor, row.type to row.note.trim().lowercase())
                if (!existing.add(key)) {
                    duplicates++
                    return@mapNotNull null
                }
                val category = row.category?.let { name -> cats.firstOrNull { it.type == row.type && it.name.equals(name, ignoreCase = true) } }
                    ?: fallback(row.type).also { if (row.category == null) noCategory++ else unmatched++ }
                    ?: return@mapNotNull null
                val account = row.account?.let { name -> accountList.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id } ?: defaultAccount
                com.leftovers.app.data.Transaction(
                    amountMinor = row.amountMinor,
                    type = row.type,
                    categoryId = category.id,
                    epochDay = row.date.toEpochDay(),
                    note = row.note,
                    accountId = account,
                )
            }
            csvPreview = CsvPreview(toAdd, duplicates, parsed.unreadable, unmatched, noCategory)
        }
    }

    fun cancelCsvImport() {
        csvPreview = null
    }

    fun confirmCsvImport(onDone: (Int) -> Unit) {
        val preview = csvPreview ?: return
        csvPreview = null
        viewModelScope.launch {
            preview.toAdd.forEach { repository.saveTransaction(it) }
            onDone(preview.toAdd.size)
        }
    }

    /** Back to a fresh install: every entry, setting, connection and photo is removed. */
    fun resetApp() {
        viewModelScope.launch { resetEverything() }
    }
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val backupPasswordSet: StateFlow<Boolean> = backup.password.isSet

    fun setBackupPassword(password: String?) {
        viewModelScope.launch(Dispatchers.IO) { backup.password.set(password) }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setCurrency(code) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setGlassEffects(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGlassEffects(enabled) }
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

    fun setAutoBackupDays(days: Int) {
        viewModelScope.launch { settingsRepository.setAutoBackupDays(days) }
    }

    fun setAutoBackupKeep(count: Int) {
        viewModelScope.launch { settingsRepository.setAutoBackupKeep(count) }
    }

    fun setBackupPhotos(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBackupPhotos(enabled) }
    }

    fun setDetectionKeywords(words: Set<String>) {
        viewModelScope.launch { settingsRepository.setDetectionKeywords(words) }
    }

    fun setBillReminders(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBillReminders(enabled) }
    }

    /** Remembers [tree] for weekly backups and makes the first one straight away. */
    fun enableAutoBackup(context: Context, tree: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupDir(tree.toString())
            AutoBackup.runNow(context, tree, settingsRepository.settings.first().autoBackupKeep).fold(
                {
                    settingsRepository.setAutoBackupDone(System.currentTimeMillis())
                    onResult("Backed up $it entries")
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

    /** [onNeedPassword] is called when the backup is protected and no saved or typed password opens it. */
    fun restoreFrom(uri: Uri, password: String? = null, onNeedPassword: (wrong: Boolean) -> Unit, onResult: (String) -> Unit) {
        viewModelScope.launch {
            backup.import(uri, password).fold(
                { onResult("Restored $it entries") },
                {
                    if (it is BackupPasswordException) onNeedPassword(password != null) else onResult("Couldn't restore: ${it.message}")
                },
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
            planning.deleteAllDebts()
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
    onOpenDeleted: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showAssistant by rememberSaveable { mutableStateOf(false) }
    var showEmail by rememberSaveable { mutableStateOf(false) }
    var showKeywords by rememberSaveable { mutableStateOf(false) }
    val deletedCount by (LocalContext.current.applicationContext as com.leftovers.app.LeftoversApp).container.repository.deletedCount.collectAsStateWithLifecycle(0)
    val emailConnection by (LocalContext.current.applicationContext as com.leftovers.app.LeftoversApp).container.emailAccount.connection.collectAsStateWithLifecycle()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    // A protected backup waiting for its password; the flag says the last try was wrong.
    var lockedRestore by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    var setPassword by rememberSaveable { mutableStateOf(false) }
    val passwordSet by viewModel.backupPasswordSet.collectAsStateWithLifecycle()
    fun toast(text: String) = scope.launch { snackbar.showSnackbar(text) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.backupTo(uri) { toast(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readCsv(context, uri) { message -> scope.launch { snackbar.showSnackbar(message) } }
    }
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
                        // Off: solid bars, cards, dialogs and sheets, with no blur (also a little easier on the battery).
                        ToggleRow(Lucide.Droplet, "Glass effects", s.glassEffects, viewModel::setGlassEffects, inset = false)
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
                        RowDivider()
                        NavRow(Lucide.Mail, "Detect payments in email", emailConnection?.address ?: "Off") { showEmail = true }
                        if (s.smsDetection || emailConnection != null) {
                            RowDivider()
                            NavRow(Lucide.Tag, "Detection keywords", if (s.detectionKeywords.isEmpty()) "Test a message" else "${s.detectionKeywords.size} added") { showKeywords = true }
                        }
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
                        NavRow(Lucide.History, "Deleted entries", if (deletedCount == 0) "None" else "$deletedCount", onOpenDeleted)
                        RowDivider()
                        ToggleRow(Lucide.CalendarClock, "Automatic backup", s.autoBackupDir != null, { on ->
                            if (on) pickBackupFolder.launch(null) else viewModel.disableAutoBackup(context, s.autoBackupDir)
                        })
                        if (s.autoBackupDir != null) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SegmentedToggle(listOf(1, 7, 30), s.autoBackupDays, { if (it == 1) "Daily" else if (it == 7) "Weekly" else "Monthly" }, { viewModel.setAutoBackupDays(it) }, Modifier.fillMaxWidth())
                                Text("Backup files to keep", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
                                SegmentedToggle(listOf(1, 2, 3, 4, 5), s.autoBackupKeep, { it.toString() }, { viewModel.setAutoBackupKeep(it) }, Modifier.fillMaxWidth())
                            }
                        }
                        RowDivider()
                        ToggleRow(Lucide.Camera, "Include photos in backups", s.backupPhotos, { viewModel.setBackupPhotos(it) })
                        RowDivider()
                        ToggleRow(Lucide.Lock, "Password-protect backups", passwordSet, { on ->
                            if (on) setPassword = true else viewModel.setBackupPassword(null)
                        })
                        RowDivider()
                        NavRow(Lucide.ReceiptText, "Export to CSV", "Excel, Sheets") {
                            viewModel.export(context) { scope.launch { snackbar.showSnackbar("Nothing to export yet") } }
                        }
                        RowDivider()
                        NavRow(Lucide.Download, "Import from CSV", "Bank statement or another app") {
                            openCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                        }
                        RowDivider()
                        ListRow(
                            "Delete all entries",
                            leading = { IconTile(Lucide.Trash2, c.negative, size = 38.dp) },
                            onClick = { confirmWipe = true },
                        )
                        RowDivider()
                        ListRow(
                            "Reset app",
                            subtitle = "Erase everything and start fresh",
                            leading = { IconTile(Lucide.RotateCcw, c.negative, size = 38.dp) },
                            onClick = { confirmReset = true },
                        )
                    }
                }
                if (s.autoBackupDir != null || passwordSet || s.backupPhotos) {
                    Text(
                        listOfNotNull(
                            if (s.autoBackupDir == null) null else {
                                val every = when (s.autoBackupDays) { 1 -> "every day"; 30 -> "every month"; else -> "every week" }
                                val kept = if (s.autoBackupKeep == 1) "only the latest" else "the last ${s.autoBackupKeep}"
                                "Saves a backup to your chosen folder $every and keeps $kept." + if (s.autoBackupLast > 0) {
                                    val at = java.time.Instant.ofEpochMilli(s.autoBackupLast).atZone(java.time.ZoneId.systemDefault())
                                    " Last backup: ${at.toLocalDate().friendlyLabel()}, ${at.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}."
                                } else ""
                            },
                            if (s.backupPhotos) "Receipt and Money owed photos are included (up to 40 MB of photos; past that they're left out)." else null,
                            if (passwordSet) "New backups are encrypted. Restoring one needs the password, and it can't be recovered if forgotten." else null,
                        ).joinToString(" "),
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
                    Text("Stored only on this device · Leftovers 1.6.0", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, textAlign = TextAlign.Center)
                }
            }
            item {
                val uriHandler = LocalUriHandler.current
                Glass(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        // Opens GitHub's issue forms (bug report or feature request) in the browser.
                        NavRow(Lucide.MessageCircle, "Report a problem or suggest an idea", "GitHub") {
                            uriHandler.openUri("https://github.com/saisaharsh3/Leftovers/issues/new/choose")
                        }
                    }
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
        if (showEmail) EmailSetupSheet(onDismiss = { showEmail = false })
        if (showKeywords) DetectionSheet(s.detectionKeywords, viewModel::setDetectionKeywords, onDismiss = { showKeywords = false })

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
            GlassAlertDialog(
                onDismissRequest = { pendingRestore = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("Restore this backup?") },
                text = { Text("Everything currently in the app will be replaced with the backup's contents.", color = c.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRestore = null
                        viewModel.restoreFrom(uri, onNeedPassword = { wrong -> lockedRestore = uri to wrong }) { toast(it) }
                    }) { Text("Restore", color = c.textPrimary) }
                },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        lockedRestore?.let { (uri, wrong) ->
            var typed by remember(uri, wrong) { mutableStateOf("") }
            GlassAlertDialog(
                onDismissRequest = { lockedRestore = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("This backup has a password") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTextField(typed, { typed = it }, placeholder = "Password", password = true)
                        if (wrong) Text("That password didn't work", style = MaterialTheme.typography.bodySmall, color = c.negative)
                    }
                },
                confirmButton = {
                    TextButton(enabled = typed.isNotEmpty(), onClick = {
                        lockedRestore = null
                        viewModel.restoreFrom(uri, typed, onNeedPassword = { w -> lockedRestore = uri to w }) { toast(it) }
                    }) { Text("Restore", color = c.textPrimary) }
                },
                dismissButton = { TextButton(onClick = { lockedRestore = null }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        if (setPassword) {
            var first by remember { mutableStateOf("") }
            var second by remember { mutableStateOf("") }
            val ok = first.length >= 6 && first == second
            GlassAlertDialog(
                onDismissRequest = { setPassword = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("Backup password") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Backups you make from now on, including weekly ones, are encrypted with it. Keep it somewhere safe: without it they can't be restored.",
                            color = c.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        GlassTextField(first, { first = it }, placeholder = "At least 6 characters", password = true)
                        GlassTextField(second, { second = it }, placeholder = "Type it again", password = true)
                        if (second.isNotEmpty() && first != second) Text("The two don't match", style = MaterialTheme.typography.bodySmall, color = c.negative)
                    }
                },
                confirmButton = {
                    TextButton(enabled = ok, onClick = {
                        viewModel.setBackupPassword(first)
                        setPassword = false
                        toast("Backups will be password protected")
                    }) { Text("Turn on", color = if (ok) c.textPrimary else c.textTertiary) }
                },
                dismissButton = { TextButton(onClick = { setPassword = false }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        if (showTime) {
            val state = rememberTimePickerState(s.reminderMinutes / 60, s.reminderMinutes % 60)
            GlassAlertDialog(
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

        if (confirmReset) {
            var typed by remember { mutableStateOf("") }
            GlassAlertDialog(
                onDismissRequest = { confirmReset = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("Reset the app?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Everything is erased: entries, accounts, categories, subscriptions, goals, money owed, photos and " +
                                "settings, and the AI and email are disconnected. Back up first if you might want it back.",
                            color = c.textSecondary,
                        )
                        GlassTextField(typed, { typed = it.take(10) }, placeholder = "Type RESET to confirm")
                    }
                },
                confirmButton = {
                    val ok = typed.trim().equals("RESET", ignoreCase = true)
                    TextButton(enabled = ok, onClick = {
                        confirmReset = false
                        viewModel.resetApp()
                    }) { Text("Reset", color = if (ok) c.negative else c.textTertiary) }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        viewModel.csvPreview?.let { preview ->
            val adding = preview.toAdd.size
            GlassAlertDialog(
                onDismissRequest = viewModel::cancelCsvImport,
                title = { Text(if (adding > 0) "Import $adding ${if (adding == 1) "entry" else "entries"}?" else "Nothing new to import") },
                text = {
                    Text(
                        listOfNotNull(
                            if (adding > 0) {
                                val days = preview.toAdd.map { it.epochDay }
                                val from = java.time.LocalDate.ofEpochDay(days.min()).friendlyLabel()
                                val to = java.time.LocalDate.ofEpochDay(days.max()).friendlyLabel()
                                if (from == to) "From $from." else "From $from to $to."
                            } else null,
                            if (preview.duplicates > 0) "${preview.duplicates} already in Leftovers ${if (preview.duplicates == 1) "is" else "are"} skipped." else null,
                            if (preview.noCategory > 0) "${if (preview.noCategory == adding) "They have" else "${preview.noCategory} have"} no category, so ${if (preview.noCategory == 1) "it goes" else "they go"} to Other; change any later." else null,
                            if (preview.unmatched > 0) "${preview.unmatched} with a category you don't have go to Other." else null,
                            if (preview.unreadable > 0) "${preview.unreadable} ${if (preview.unreadable == 1) "row" else "rows"} without a readable date or amount ${if (preview.unreadable == 1) "is" else "are"} left out." else null,
                        ).joinToString(" "),
                    )
                },
                confirmButton = {
                    if (adding > 0) {
                        TextButton(onClick = {
                            viewModel.confirmCsvImport { n -> scope.launch { snackbar.showSnackbar("Imported $n ${if (n == 1) "entry" else "entries"}") } }
                        }) { Text("Import", color = c.textPrimary) }
                    } else {
                        TextButton(onClick = viewModel::cancelCsvImport) { Text("OK", color = c.textPrimary) }
                    }
                },
                dismissButton = if (adding > 0) {
                    { TextButton(onClick = viewModel::cancelCsvImport) { Text("Cancel", color = c.textSecondary) } }
                } else null,
            )
        }

        if (confirmWipe) {
            GlassAlertDialog(
                onDismissRequest = { confirmWipe = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("Delete everything?") },
                text = { Text("All entries, money owed and budgets will be permanently deleted. Categories, accounts, subscriptions, goals and settings are kept.", color = c.textSecondary) },
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