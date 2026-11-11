package com.leftovers.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.leftovers.app.ui.components.toggleHaptic
import com.leftovers.app.ui.components.pressable
import androidx.compose.foundation.layout.Box
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.PrimaryButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.leftovers.app.util.DeviceSync
import com.leftovers.app.util.WebDavPlace
import com.leftovers.app.util.BackupManager
import com.leftovers.app.util.friendlyLabel
import com.leftovers.app.util.CsvExporter
import com.leftovers.app.util.Reminders
import com.leftovers.app.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** [newCategories] are made on import; entries waiting for one carry its name in [pendingCategory] (by position in [toAdd]). */
    data class CsvPreview(
        val toAdd: List<com.leftovers.app.data.Transaction>, val duplicates: Int, val unreadable: Int, val noCategory: Int, val transfers: Int = 0,
        val newCategories: List<Pair<com.leftovers.app.data.TxType, String>> = emptyList(),
        val pendingCategory: Map<Int, Pair<com.leftovers.app.data.TxType, String>> = emptyMap(),
    )

    var csvPreview by mutableStateOf<CsvPreview?>(null)
        private set

    /** Reads [uri] (CSV, Excel or a bank's statement file) and prepares the entries; nothing is saved until [confirmCsvImport]. */
    fun readCsv(
        context: android.content.Context,
        uri: android.net.Uri,
        password: String? = null,
        onNeedPassword: (wrong: Boolean) -> Unit = {},
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let(com.leftovers.app.util.StatementFile::read)
            }
            val parsed = when (file) {
                null -> {
                    onError("Couldn't open that file")
                    return@launch
                }
                is com.leftovers.app.util.StatementFile.Result.Unsupported -> {
                    onError(file.message)
                    return@launch
                }
                is com.leftovers.app.util.StatementFile.Result.Text -> com.leftovers.app.util.CsvImporter.parse(file.text)
                is com.leftovers.app.util.StatementFile.Result.Table -> com.leftovers.app.util.CsvImporter.parseTable(file.rows)
                is com.leftovers.app.util.StatementFile.Result.Pdf -> {
                    // Most bank e-statements have a password (often a date of birth or customer ID).
                    val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { com.leftovers.app.util.PdfStatement.text(context, file.bytes, password) }
                    }
                    text.exceptionOrNull()?.let {
                        if (it is com.leftovers.app.util.PdfStatement.PasswordNeeded) onNeedPassword(it.wrong) else onError("Couldn't read this PDF.")
                        return@launch
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.leftovers.app.util.PdfStatement.parse(text.getOrThrow()) }
                }
            }
            parsed.error?.let {
                onError(it)
                return@launch
            }
            val cats = repository.categories.first()
            val accountList = accounts.accounts.first()
            val defaultAccount = settingsRepository.settings.first().defaultAccountId
            // A category is matched by name; one you don't have yet is made on import, as the other app had it.
            // Entries with no category at all go to the type's "Other" category.
            fun fallback(type: com.leftovers.app.data.TxType) = cats.filter { it.type == type }.let { list ->
                list.firstOrNull { it.name.lowercase() in setOf("other", "others", "miscellaneous", "misc", "general") } ?: list.firstOrNull()
            }
            // Already in the app, counted: a file with two real payments alike (₹80 at the same shop twice a day)
            // keeps both, and only as many as the app already has are skipped as duplicates.
            val existing = repository.getAllTransactions().groupingBy { Triple(it.epochDay, it.amountMinor, it.type to it.note.trim().lowercase()) }
                .eachCount().toMutableMap()
            var duplicates = 0
            var noCategory = 0
            val newCategories = linkedMapOf<Pair<com.leftovers.app.data.TxType, String>, String>()
            val pending = mutableMapOf<Int, Pair<com.leftovers.app.data.TxType, String>>()
            val toAdd = mutableListOf<com.leftovers.app.data.Transaction>()
            parsed.rows.forEach { row ->
                val key = Triple(row.date.toEpochDay(), row.amountMinor, row.type to row.note.trim().lowercase())
                val left = existing[key] ?: 0
                if (left > 0) {
                    existing[key] = left - 1
                    duplicates++
                    return@forEach
                }
                val wanted = row.category?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
                val match = wanted?.let { name -> cats.firstOrNull { it.type == row.type && it.name.equals(name, ignoreCase = true) } }
                val toMake = if (match == null && wanted != null) (row.type to wanted.lowercase()).also { newCategories.putIfAbsent(it, wanted) } else null
                val category = match ?: fallback(row.type).also { if (wanted == null) noCategory++ } ?: return@forEach
                if (toMake != null) pending[toAdd.size] = toMake
                val account = row.account?.let { name -> accountList.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id } ?: defaultAccount
                toAdd += com.leftovers.app.data.Transaction(
                    amountMinor = row.amountMinor,
                    type = row.type,
                    categoryId = category.id,
                    epochDay = row.date.toEpochDay(),
                    note = row.note,
                    accountId = account,
                )
            }
            csvPreview = CsvPreview(
                toAdd, duplicates, parsed.unreadable, noCategory, parsed.transfers,
                newCategories = newCategories.map { (k, name) -> k.first to name },
                pendingCategory = pending.mapValues { (_, k) -> k.first to newCategories.getValue(k) },
            )
        }
    }

    fun cancelCsvImport() {
        csvPreview = null
    }

    fun confirmCsvImport(onDone: (Int, Long) -> Unit) {
        val preview = csvPreview ?: return
        csvPreview = null
        viewModelScope.launch {
            val have = repository.categories.first()
            preview.newCategories.forEachIndexed { i, (type, name) ->
                if (have.none { it.type == type && it.name.equals(name, ignoreCase = true) }) {
                    repository.saveCategory(
                        com.leftovers.app.data.Category(
                            name = name, emoji = com.leftovers.app.util.CategoryGuess.icon(name, type),
                            color = com.leftovers.app.data.Palette.all[(have.size + i) % com.leftovers.app.data.Palette.all.size],
                            type = type, addsToBudget = type == com.leftovers.app.data.TxType.INCOME,
                        ),
                    )
                }
            }
            val cats = repository.categories.first()
            val at = System.currentTimeMillis()
            val entries = preview.toAdd.mapIndexed { i, t0 ->
                val t = t0.copy(createdAt = at)
                val (type, name) = preview.pendingCategory[i] ?: return@mapIndexed t
                cats.firstOrNull { it.type == type && it.name.equals(name, ignoreCase = true) }?.let { t.copy(categoryId = it.id) } ?: t
            }
            repository.addTransactions(entries)
            onDone(entries.size, at)
        }
    }

    /** Files imported before, newest first, to take one back if it came in wrong. */
    val imports: StateFlow<List<com.leftovers.app.util.ImportBatches.Batch>> = repository.allTransactions
        .map { com.leftovers.app.util.ImportBatches.find(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Takes an import back: its entries go to Deleted entries, so they can still be restored. */
    fun removeImport(batch: com.leftovers.app.util.ImportBatches.Batch, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val ids = repository.getAllTransactions().filter { com.leftovers.app.util.ImportBatches.inBatch(it, batch) }.map { it.id }
            ids.forEach { id -> repository.getTransaction(id)?.let { repository.deleteTransaction(it) } }
            onDone(ids.size)
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

    fun setTimeFormat(format: com.leftovers.app.data.TimeFormat) {
        viewModelScope.launch { settingsRepository.setTimeFormat(format) }
    }

    fun setWidgetStyle(style: com.leftovers.app.data.WidgetStyle) {
        viewModelScope.launch { settingsRepository.setWidgetStyle(style) }
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

    fun setPureBlack(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPureBlack(enabled) }
    }

    fun setLiquidGlass(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLiquidGlass(enabled) }
    }

    fun setBackdrop(b: com.leftovers.app.ui.theme.Backdrop) {
        viewModelScope.launch { settingsRepository.setBackdrop(b.name) }
    }

    fun setPhotoDim(value: Float) {
        viewModelScope.launch { settingsRepository.setPhotoDim(value) }
    }

    fun setPhotoBlur(value: Float) {
        viewModelScope.launch { settingsRepository.setPhotoBlur(value) }
    }

    fun setPhotoText(value: String) {
        viewModelScope.launch { settingsRepository.setPhotoText(value) }
    }

    fun setAccentColor(argb: Long) {
        viewModelScope.launch { settingsRepository.setAccentColor(argb) }
    }

    /** Keeps a copy of the chosen picture as the background; [onFailed] when it can't be read. */
    fun setBackgroundPhoto(context: Context, uri: android.net.Uri, onFailed: () -> Unit) {
        viewModelScope.launch {
            val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.leftovers.app.ui.theme.BackgroundPhoto.save(context, uri) }
            if (saved) settingsRepository.photoChanged() else onFailed()
        }
    }

    fun setBudgetAlerts(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBudgetAlerts(enabled) }
    }

    fun setSplitsAsOneBill(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSplitsAsOneBill(enabled) }
    }

    fun setReminder(enabled: Boolean, minutes: Int) {
        viewModelScope.launch { settingsRepository.setReminder(enabled, minutes) }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLock(enabled) }
    }

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBlockScreenshots(enabled) }
    }

    fun setLockAfter(seconds: Int) {
        viewModelScope.launch { settingsRepository.setLockAfter(seconds) }
    }

    fun setLockFingerprint(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLockFingerprint(enabled) }
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

    fun setFestivalGreetings(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFestivalGreetings(enabled) }
    }

    fun setWeeklyNotes(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWeeklyNotes(enabled) }
    }

    /** Remembers [tree] for weekly backups and makes the first one straight away. */
    fun enableAutoBackup(context: Context, tree: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupDir(tree.toString())
            // Into the folder just chosen: reading it back from settings straight away could still give the old one.
            AutoBackup.backUp(context, tree, settingsRepository.settings.first().autoBackupKeep).fold(
                { onResult("Backed up $it entries") },
                { onResult("Couldn't back up: ${AutoBackup.problem(it)}") },
            )
        }
    }

    /** Makes the automatic backup now, into the chosen folder. */
    fun backUpNow(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            val dir = s.autoBackupDir ?: return@launch
            AutoBackup.backUp(context, Uri.parse(dir), s.autoBackupKeep).fold(
                { onResult("Backed up $it entries") },
                { onResult("Couldn't back up: ${AutoBackup.problem(it)}") },
            )
        }
    }

    /** Remembers [tree] as the folder shared with the user's other phones, and syncs straight away. */
    fun enableSync(context: Context, tree: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            settingsRepository.setSyncServer(null, null)
            settingsRepository.setSyncDir(tree.toString())
            syncNow(context, onResult)
        }
    }

    fun syncNow(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = DeviceSync.runIfOn(context) ?: return@launch
            result.fold({ onResult(DeviceSync.describe(it)) }, { onResult("Couldn't sync: ${WebDavPlace.problem(it)}") })
        }
    }

    /** Tries the server first, and keeps it only when a sync works; [onResult] gets null on success, or what went wrong. */
    fun enableServerSync(context: Context, url: String, user: String, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val place = runCatching { WebDavPlace(url, user, password) }.getOrElse { onResult(it.message); return@launch }
            DeviceSync.runNow(context, place).fold(
                { outcome ->
                    (context.applicationContext as com.leftovers.app.LeftoversApp).container.syncLogin.set(password)
                    settingsRepository.setSyncDir(null)
                    settingsRepository.setSyncServer(url.trim().trimEnd('/'), user)
                    onResult(null)
                    android.widget.Toast.makeText(context, DeviceSync.describe(outcome), android.widget.Toast.LENGTH_LONG).show()
                },
                { onResult(WebDavPlace.problem(it)) },
            )
        }
    }

    fun setUpdateCheck(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUpdateCheck(enabled) }
    }

    /** Asks GitHub now; [onResult] says what it found. */
    fun checkForUpdate(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            com.leftovers.app.util.UpdateCheck.latest(com.leftovers.app.util.UpdateCheck.isDevBuild(context)).fold(
                { release ->
                    if (com.leftovers.app.util.UpdateCheck.isNewerThanInstalled(context, release)) {
                        onResult("Leftovers ${release.version} is out")
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        onResult("You have the latest version")
                    }
                },
                { onResult("Couldn't check: ${WebDavPlace.problem(it)}") },
            )
        }
    }

    fun disableSync(context: Context, dir: String?) {
        viewModelScope.launch {
            settingsRepository.setSyncDir(null)
            settingsRepository.setSyncServer(null, null)
            (context.applicationContext as com.leftovers.app.LeftoversApp).container.syncLogin.set(null)
            // Backups may use the same folder; it stays for them.
            com.leftovers.app.util.FolderAccess.release(context, dir, listOf(settingsRepository.settings.first().autoBackupDir))
        }
    }

    fun disableAutoBackup(context: Context, dir: String?) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupDir(null)
            // Sync may use the same folder; it stays for it.
            com.leftovers.app.util.FolderAccess.release(context, dir, listOf(settingsRepository.settings.first().syncDir))
        }
    }

    fun setSmsDetection(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSmsDetection(enabled) }
    }

    fun setSmsAutoAdd(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSmsAutoAdd(enabled) }
    }

    fun setSmsAutoMax(minor: Long) {
        viewModelScope.launch { settingsRepository.setSmsAutoMax(minor) }
    }

    fun setAppNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppNotifications(enabled) }
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
    fun restoreFrom(
        uri: Uri,
        password: String? = null,
        keep: Boolean = false,
        onNeedPassword: (wrong: Boolean) -> Unit,
        onNotBackup: () -> Unit = {},
        onResult: (String) -> Unit,
    ) {
        viewModelScope.launch {
            backup.import(uri, password, keep).fold(
                {
                    onResult(
                        when {
                            !keep -> "Restored ${it.entries} entries"
                            it.alreadyHere > 0 -> "Added ${it.entries} entries; ${it.alreadyHere} were already here"
                            else -> "Added ${it.entries} entries"
                        },
                    )
                },
                {
                    when (it) {
                        is BackupPasswordException -> onNeedPassword(password != null)
                        is com.leftovers.app.util.NotABackupException -> onNotBackup()
                        else -> onResult("Couldn't restore: ${it.message}")
                    }
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
    onOpenHelp: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val context = LocalContext.current
    // Payment apps' notifications need notification access, given in the phone's settings; checked again on return.
    var notificationAccess by remember { mutableStateOf(com.leftovers.app.util.PaymentApps.accessGranted(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        notificationAccess = com.leftovers.app.util.PaymentApps.accessGranted(context)
        onPauseOrDispose { }
    }
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
    var lockedPdf by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    // Whether the restore being done keeps what's already in the app (true) or replaces it.
    var restoreKeeps by remember { mutableStateOf(true) }
    var setPassword by rememberSaveable { mutableStateOf(false) }
    var chooseSync by rememberSaveable { mutableStateOf(false) }
    var syncServerForm by rememberSaveable { mutableStateOf(false) }
    val passwordSet by viewModel.backupPasswordSet.collectAsStateWithLifecycle()
    fun toast(text: String) = scope.launch { snackbar.showSnackbar(text) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.backupTo(uri) { toast(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readCsv(context, uri, onNeedPassword = { wrong -> lockedPdf = uri to wrong }) { message -> scope.launch { snackbar.showSnackbar(message) } }
    }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            // Android has to let the app keep the folder, or the next backup would fail (#41).
            if (com.leftovers.app.util.FolderAccess.keep(context, tree, listOf(settings?.syncDir))) viewModel.enableAutoBackup(context, tree) { toast(it) }
            else toast("Android won't let Leftovers keep that folder. Try another one")
        }
    }
    val pickSyncFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            if (com.leftovers.app.util.FolderAccess.keep(context, tree, listOf(settings?.autoBackupDir))) viewModel.enableSync(context, tree) { toast(it) }
            else toast("Android won't let Leftovers keep that folder. Try another one")
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setSmsDetection(granted)
        if (!granted) toast("SMS permission is needed to spot bank payments")
    }

    // A category's page, or null for the main page; Back leaves the page before it leaves Settings.
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    GlassScreen(title = page ?: "Settings", onBack = { if (page != null) page = null else onBack() }, snackbar = snackbar) { padding ->
        val s = settings ?: return@GlassScreen
        val passcode = (context.applicationContext as com.leftovers.app.LeftoversApp).container.passcode
        val passcodeKind by passcode.kind.collectAsStateWithLifecycle()
        var showLockOptions by rememberSaveable { mutableStateOf(false) }
    var showPaymentApps by rememberSaveable { mutableStateOf(false) }
    if (showPaymentApps) PaymentAppsSheet(onDismiss = { showPaymentApps = false })
        // App lock was switched on with no phone screen lock: it turns on once an app PIN or password is saved.
        var enableLockAfterCode by rememberSaveable { mutableStateOf(false) }
        if (showLockOptions) {
            LockOptionsSheet(
                kind = passcodeKind,
                phoneLock = canLock(context),
                lockAfter = s.lockAfterSeconds,
                fingerprint = s.lockFingerprint,
                onSaveCode = { code, kind ->
                    // Read now: closing the sheet clears the flag before the save below finishes.
                    val turnOn = enableLockAfterCode
                    scope.launch {
                        passcode.set(code, kind)
                        if (turnOn) viewModel.setAppLock(true)
                        enableLockAfterCode = false
                        snackbar.showSnackbar(if (kind == com.leftovers.app.util.AppPasscode.Kind.PIN) "App PIN set" else "Password set")
                    }
                },
                onUsePhoneLock = {
                    passcode.clear()
                    scope.launch { snackbar.showSnackbar("Unlocks with the phone's screen lock") }
                },
                onLockAfter = viewModel::setLockAfter,
                onFingerprint = viewModel::setLockFingerprint,
                onDismiss = {
                    showLockOptions = false
                    enableLockAfterCode = false
                },
            )
        }
        val money = Money(s.currencyCode)
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        val scrolls = remember { mutableMapOf<String?, androidx.compose.foundation.lazy.LazyListState>() }
        fun scrollOf(of: String?) = scrolls.getOrPut(of) { androidx.compose.foundation.lazy.LazyListState() }
        val density = androidx.compose.ui.platform.LocalDensity.current
        // Opened from Help or the search on one setting: its page, then the row itself into view, lit up for a moment.
        LaunchedEffect(SettingsJump.section) {
            val section = SettingsJump.section ?: return@LaunchedEffect
            // The page the setting is on; the AI assistant's settings are a sheet of their own.
            val target = SettingsIndex.pageFor(section, SettingsJump.row)
            if (target == SettingsIndex.AI) {
                showAssistant = true
                SettingsJump.section = null
                SettingsJump.row = null
                return@LaunchedEffect
            }
            page = target
            val listState = scrollOf(target)
            // After the page has slid in, so the row is found where it will stay.
            kotlinx.coroutines.delay(320)
            listState.scrollToItem(0)
            val row = SettingsJump.row
            if (row != null) {
                // Where the row is after that scroll, not before it: wait for the list to lay out there first.
                repeat(2) { androidx.compose.runtime.withFrameNanos { } }
                val top = SettingsJump.rowTop ?: kotlinx.coroutines.withTimeoutOrNull(800) {
                    androidx.compose.runtime.snapshotFlow { SettingsJump.rowTop }.first { it != null }
                }
                if (top != null) listState.animateScrollBy(top - with(density) { 200.dp.toPx() })
                SettingsJump.flash = row
            }
            SettingsJump.section = null
            SettingsJump.row = null
            SettingsJump.rowTop = null
            kotlinx.coroutines.delay(1600)
            SettingsJump.flash = null
        }
        // Like a phone's Settings: a page slides in from the right and back out to the right, on a spring with no bounce,
        // the page below easing back a little and dimming as it does.
        androidx.compose.animation.AnimatedContent(
            targetState = page,
            transitionSpec = {
                val slide = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
                    dampingRatio = 1f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                )
                val fade = androidx.compose.animation.core.tween<Float>(220)
                val forward = targetState != null
                if (forward) {
                    (androidx.compose.animation.slideInHorizontally(slide) { it / 3 } + androidx.compose.animation.fadeIn(fade)) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(slide) { -it / 8 } + androidx.compose.animation.fadeOut(fade))
                } else {
                    (androidx.compose.animation.slideInHorizontally(slide) { -it / 8 } + androidx.compose.animation.fadeIn(fade)) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(slide) { it / 3 } + androidx.compose.animation.fadeOut(fade))
                }.using(androidx.compose.animation.SizeTransform(clip = false))
            },
            label = "settingsPage",
        ) { shown ->
            LazyColumn(
                Modifier.fillMaxSize(),
                state = scrollOf(shown),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 48.dp),
            ) {
                // Like a phone's Settings: a search, Help, and the categories, each opening a page of its own.
                if (shown == null) {
                    item(key = "search") {
                        GlassTextField(query, { query = it.take(40) }, placeholder = "Search settings", modifier = Modifier.padding(top = 8.dp))
                    }
                    if (query.isNotBlank()) {
                        val found = SettingsIndex.search(query)
                        if (found.isEmpty()) {
                            item(key = "none") {
                                Text(
                                    "No setting called that. Help & features covers every feature, settings too.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textSecondary,
                                    modifier = Modifier.padding(start = 8.dp, top = 16.dp),
                                )
                            }
                        } else {
                            item(key = "found") {
                                Glass(Modifier.fillMaxWidth().padding(top = 12.dp).animateContentSize(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))) {
                                    Column(Modifier.padding(vertical = 6.dp)) {
                                        found.forEachIndexed { i, entry ->
                                            if (i > 0) RowDivider()
                                            PageRow(entry.icon, entry.title, entry.page) {
                                                query = ""
                                                focus.clearFocus()
                                                if (entry.page == SettingsIndex.AI) showAssistant = true
                                                else { page = entry.page; SettingsJump.row = entry.title.takeIf { entry.row }; SettingsJump.section = entry.page }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Help first, where someone new opening Settings finds it.
                        item(key = "help") {
                            Glass(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    NavRow(Lucide.Search, "Help & features", "Find anything", onOpenHelp)
                                }
                            }
                        }
                        item(key = "pages") {
                            val assistant by viewModel.assistant.config.collectAsStateWithLifecycle()
                            Glass(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    SettingsIndex.pages.forEachIndexed { i, pg ->
                                        if (i > 0) RowDivider()
                                        PageRow(
                                            pg.icon,
                                            pg.title,
                                            if (pg.title == SettingsIndex.AI) assistant?.let { "${it.provider.label} · connected" } ?: "Not connected" else pg.summary,
                                        ) {
                                            if (pg.title == SettingsIndex.AI) showAssistant = true else page = pg.title
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (shown == SettingsIndex.APPEARANCE) {
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
                            // Over a photo, cards are always glass and the photo is the background, so these two are hidden.
                            val photoBackground = s.backdrop == com.leftovers.app.ui.theme.Backdrop.PHOTO.name
                            // Off: solid bars, cards, dialogs and sheets, with no blur (also a little easier on the battery).
                            if (!photoBackground) ToggleRow(Lucide.Droplet, "Glass effects", s.glassEffects, viewModel::setGlassEffects, inset = false)
                            // Over a photo, in its place: which glass. Liquid bends the photo (Android 13 and newer); frosted
                            // is lighter, for smooth scrolling on any phone.
                            if (photoBackground && Build.VERSION.SDK_INT >= 33) {
                                Spacer(Modifier.height(8.dp))
                                // Switching to liquid asks first: it's the heavier of the two.
                                var askLiquid by remember { mutableStateOf(false) }
                                SegmentedToggle(
                                    listOf(true, false), s.liquidGlass, { if (it) "Liquid glass" else "Frosted glass" },
                                    { liquid -> if (liquid && !s.liquidGlass) askLiquid = true else viewModel.setLiquidGlass(liquid) },
                                    Modifier.fillMaxWidth(),
                                )
                                if (askLiquid) {
                                    GlassAlertDialog(
                                        onDismissRequest = { askLiquid = false },
                                        text = {
                                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                                com.leftovers.app.ui.components.IconTile(Lucide.Droplet, c.accent, size = 56.dp)
                                                Spacer(Modifier.height(16.dp))
                                                Text("Liquid glass", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    "Cards bend your photo at their edges, like real glass.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = c.textSecondary,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                )
                                                Spacer(Modifier.height(20.dp))
                                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    LiquidCost(Lucide.BatteryMedium, "Uses more battery")
                                                    LiquidCost(Lucide.Zap, "Works your phone harder, so scrolling can stutter on some phones")
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                com.leftovers.app.ui.components.PrimaryButton("Use liquid glass", { askLiquid = false; viewModel.setLiquidGlass(true) }, Modifier.fillMaxWidth())
                                                com.leftovers.app.ui.components.SecondaryButton("Keep frosted", { askLiquid = false }, Modifier.fillMaxWidth())
                                            }
                                        },
                                    )
                                }
                                if (s.liquidGlass) {
                                    Text(
                                        "Uses more battery and graphics power. If scrolling isn't smooth on your phone, Frosted glass is lighter.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                                    )
                                }
                            }
                            // Only means something with the dark theme, so it's hidden when the app is always light.
                            if (s.themeMode != ThemeMode.LIGHT && !photoBackground) {
                                ToggleRow(Lucide.Moon, "Pure black (AMOLED)", s.pureBlack, viewModel::setPureBlack, inset = false)
                            }
                            Spacer(Modifier.height(12.dp))
                            LooksPicker(
                                s,
                                onBackdrop = viewModel::setBackdrop,
                                onPhoto = { uri -> viewModel.setBackgroundPhoto(context, uri) { toast("Couldn't open that picture") } },
                                onDim = viewModel::setPhotoDim,
                                onBlur = viewModel::setPhotoBlur,
                                onText = viewModel::setPhotoText,
                                onAccent = viewModel::setAccentColor,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Time", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.jumpTarget("Time").padding(start = 4.dp, bottom = 8.dp))
                            SegmentedToggle(
                                com.leftovers.app.data.TimeFormat.entries,
                                s.timeFormat,
                                { it.label },
                                viewModel::setTimeFormat,
                                Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Home screen widgets", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.jumpTarget("Home screen widgets").padding(start = 4.dp, bottom = 8.dp))
                            SegmentedToggle(
                                com.leftovers.app.data.WidgetStyle.entries,
                                s.widgetStyle,
                                { it.label },
                                viewModel::setWidgetStyle,
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                }
                if (shown == SettingsIndex.SECURITY) {
                item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            ToggleRow(Lucide.Lock, "App lock", s.appLock, { on ->
                                // Without a phone screen lock, an app PIN or password is the way to lock it.
                                if (on && !canLock(context) && passcodeKind == null) {
                                    enableLockAfterCode = true
                                    showLockOptions = true
                                } else {
                                    viewModel.setAppLock(on)
                                }
                            })
                            if (s.appLock) {
                                RowDivider()
                                NavRow(
                                    Lucide.Shield,
                                    "Lock options",
                                    when (passcodeKind) {
                                        com.leftovers.app.util.AppPasscode.Kind.PIN -> "App PIN"
                                        com.leftovers.app.util.AppPasscode.Kind.PASSWORD -> "Password"
                                        null -> "Phone lock"
                                    } + " · " + lockAfterLabel(s.lockAfterSeconds).lowercase(),
                                ) { showLockOptions = true }
                            } else {
                                // App lock blocks them already; without it, this can.
                                RowDivider()
                                ToggleRow(Lucide.Camera, "Block screenshots", s.blockScreenshots, viewModel::setBlockScreenshots)
                            }
                        }
                    }
                }
                }
                if (shown == SettingsIndex.REMINDERS) {
                item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
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
                                NavRow(Lucide.CalendarClock, "Remind me at", Reminders.label(s.reminderMinutes, com.leftovers.app.util.LocalUse24Hour.current)) { showTime = true }
                            }
                            RowDivider()
                            ToggleRow(Lucide.Bell, "Budget alerts", s.budgetAlerts, viewModel::setBudgetAlerts)
                        }
                    }
                }
                }
                if (shown == SettingsIndex.DETECTION) {
                item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            ToggleRow(Lucide.Smartphone, "Detect bank SMS", s.smsDetection, { on ->
                                if (on) smsPermission.launch(Manifest.permission.RECEIVE_SMS) else viewModel.setSmsDetection(false)
                            })
                            // With bank SMS read: small spending can go straight in, up to an amount of the user's choosing.
                            if (s.smsDetection) {
                                RowDivider()
                                ToggleRow(Lucide.Zap, "Add small payments automatically", s.smsAutoAdd, viewModel::setSmsAutoAdd)
                                if (s.smsAutoAdd) {
                                    var editingMax by rememberSaveable { mutableStateOf(false) }
                                    RowDivider()
                                    NavRow(Lucide.Coins, "Up to", money.format(s.smsAutoMaxMinor)) { editingMax = true }
                                    if (editingMax) {
                                        // Empty, with the amount now in its label: typing replaces it rather than adding to its front.
                                        var typed by rememberSaveable { mutableStateOf("") }
                                        val minor = com.leftovers.app.util.AmountInput.toMinor(typed) ?: 0L
                                        GlassAlertDialog(
                                            onDismissRequest = { editingMax = false },
                                            title = { Text("Add automatically up to") },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Text(
                                                        "Payments from bank SMS up to this amount are added right away. Bigger ones, and money coming in, still wait on Home for you to check.",
                                                        color = c.textSecondary,
                                                    )
                                                    MoneyField(typed, { typed = it }, label = "Amount (now ${money.format(s.smsAutoMaxMinor)})")
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(enabled = minor > 0, onClick = { viewModel.setSmsAutoMax(minor); editingMax = false }) {
                                                    Text("Save", color = if (minor > 0) c.textPrimary else c.textTertiary)
                                                }
                                            },
                                            dismissButton = { TextButton(onClick = { editingMax = false }) { Text("Cancel", color = c.textSecondary) } },
                                        )
                                    }
                                }
                            }
                            RowDivider()
                            ToggleRow(Lucide.Bell, "Detect payment apps", s.appNotifications && notificationAccess, { on ->
                                viewModel.setAppNotifications(on)
                                if (on && !notificationAccess) {
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                            .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, com.leftovers.app.util.PaymentApps.component(context).flattenToString())
                                    } else {
                                        android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    }
                                    runCatching { context.startActivity(intent) }
                                        .onFailure { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
                                }
                            })
                            if (s.appNotifications && notificationAccess) {
                                RowDivider()
                                val apps by (context.applicationContext as com.leftovers.app.LeftoversApp).container.paymentApps.apps.collectAsStateWithLifecycle()
                                val reading = apps.count { it.read }
                                NavRow(Lucide.Smartphone, "Payment apps", "$reading ${if (reading == 1) "app" else "apps"}") { showPaymentApps = true }
                            }
                            RowDivider()
                            NavRow(Lucide.Mail, "Detect payments in email", emailConnection?.address ?: "Off") { showEmail = true }
                            if (s.smsDetection || emailConnection != null) {
                                RowDivider()
                                NavRow(Lucide.Tag, "Detection keywords", if (s.detectionKeywords.isEmpty()) "Test a message" else "${s.detectionKeywords.size} added") { showKeywords = true }
                            }
                        }
                    }
                    if (s.appNotifications) {
                        Text(
                            if (notificationAccess) {
                                "Reads only notifications from ${com.leftovers.app.util.PaymentApps.names.values.joinToString(", ")}, and only a fingerprint of each is kept. Offers and reminders are skipped."
                            } else {
                                "Turn on notification access for Leftovers in the screen that opened, then come back."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                        )
                    }
                    if (s.smsDetection) {
                        Text(
                            if (s.smsAutoAdd) "Payments up to ${money.format(s.smsAutoMaxMinor)} are added as they come; bigger ones show up on Home for you to confirm. Messages never leave the phone."
                            else "New debit messages from your bank show up on Home for you to confirm. Nothing is added without your tap, and messages never leave the phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textTertiary,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                        )
                    }
                }

                }
                if (shown == SettingsIndex.GENERAL) {
                item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            NavRow(Lucide.Coins, "Currency", "${money.currency.displayName} (${money.symbol})") { showCurrency = true }
                            RowDivider()
                            ToggleRow(Lucide.Split, "Show a split bill as one entry", s.splitsAsOneBill, viewModel::setSplitsAsOneBill)
                            RowDivider()
                            NavRow(Lucide.Tag, "Categories", null, onManageCategories)
                            RowDivider()
                            NavRow(Lucide.Wallet, "Accounts", "Cash, bank, cards", onOpenAccounts)
                        }
                    }
                }

                }
                if (shown == SettingsIndex.DATA) {
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
                                    // The last try failed: say why, until a backup works again.
                                    s.autoBackupError?.let { problem ->
                                        Text("Last automatic backup failed: $problem", style = MaterialTheme.typography.bodySmall, color = c.negative, modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                            }
                            if (s.autoBackupDir != null) {
                                RowDivider()
                                NavRow(Lucide.Download, "Back up now", "To the chosen folder") { viewModel.backUpNow(context) { toast(it) } }
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
                            NavRow(Lucide.Download, "Import entries", "Statements, other apps") {
                                openCsv.launch(
                                    arrayOf(
                                        "text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/pdf",
                                        "application/json", "application/zip",
                                    ),
                                )
                            }
                            // Files imported before, to take one back if it came in wrong.
                            val imports by viewModel.imports.collectAsStateWithLifecycle()
                            var removing by remember { mutableStateOf<com.leftovers.app.util.ImportBatches.Batch?>(null) }
                            imports.take(5).forEach { b ->
                                RowDivider()
                                val whenText = java.time.Instant.ofEpochMilli(b.from).atZone(java.time.ZoneId.systemDefault()).let {
                                    it.toLocalDate().friendlyLabel() + " · " + it.toLocalTime().format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
                                }
                                ListRow(
                                    "Imported $whenText",
                                    subtitle = "${b.count} entries · ${money.format(b.spent)} spent" + if (b.received > 0) " · ${money.format(b.received)} received" else "",
                                    leading = { IconTile(Lucide.History, c.textSecondary, size = 38.dp) },
                                    trailing = { Text("Remove", style = MaterialTheme.typography.labelLarge, color = c.negative) },
                                    onClick = { removing = b },
                                )
                            }
                            removing?.let { b ->
                                GlassAlertDialog(
                                    onDismissRequest = { removing = null },
                                    title = { Text("Remove this import?") },
                                    text = { Text("Its ${b.count} entries go to Deleted entries, where they can be restored.", color = c.textSecondary) },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            removing = null
                                            viewModel.removeImport(b) { n -> scope.launch { snackbar.showSnackbar("Removed $n entries") } }
                                        }) { Text("Remove", color = c.negative) }
                                    },
                                    dismissButton = { androidx.compose.material3.TextButton(onClick = { removing = null }) { Text("Cancel", color = c.textSecondary) } },
                                )
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
                                        " Last automatic backup: ${at.toLocalDate().friendlyLabel()}, ${at.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}."
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

                }
                if (shown == SettingsIndex.SYNC) {
                item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            val syncOn = s.syncDir != null || s.syncServer != null
                            ToggleRow(Lucide.Smartphone, "Sync between devices", syncOn, { on ->
                                if (on) chooseSync = true else viewModel.disableSync(context, s.syncDir)
                            })
                            if (syncOn) {
                                RowDivider()
                                NavRow(
                                    if (s.syncServer != null) Lucide.Globe else Lucide.Folder,
                                    if (s.syncServer != null) "Server" else "Folder",
                                    s.syncServer?.removePrefix("https://")?.substringBefore('/') ?: "Chosen folder",
                                ) { chooseSync = true }
                                RowDivider()
                                NavRow(
                                    Lucide.Repeat,
                                    "Sync now",
                                    if (s.syncLast > 0) {
                                        val at = java.time.Instant.ofEpochMilli(s.syncLast).atZone(java.time.ZoneId.systemDefault())
                                        "${at.toLocalDate().friendlyLabel()}, ${at.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}"
                                    } else "Not synced yet",
                                ) { viewModel.syncNow(context) { toast(it) } }
                            }
                        }
                    }
                    Text(
                        if (s.syncDir == null && s.syncServer == null) {
                            "Keep your phones in step with no account: use a folder that Syncthing or Nextcloud's app keeps the same on each phone, or your own WebDAV server, and choose the same one on every phone."
                        } else {
                            "Each phone keeps its data there and merges in the others' when the app opens and every few hours. Entries deleted or changed on one phone are removed on the others; Money owed, goals and subscriptions are only added." +
                                if (passwordSet) " Files are encrypted with your backup password; use the same one on every phone." else " Set a backup password to keep these files encrypted."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    )
                }

                }
                if (shown == SettingsIndex.ABOUT) {
                item {
                    val uriHandler = LocalUriHandler.current
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            // Opens GitHub's issue forms (bug report or feature request) in the browser.
                            NavRow(Lucide.MessageCircle, "Report a problem or suggest an idea", "GitHub") {
                                uriHandler.openUri("https://github.com/saisaharsh3/Leftovers/issues/new/choose")
                            }
                            RowDivider()
                            // Off unless turned on: F-Droid and Obtainium already update the app.
                            ToggleRow(Lucide.Bell, "Tell me about new versions", s.updateCheck, { on ->
                                viewModel.setUpdateCheck(on)
                                if (on && android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            })
                            RowDivider()
                            NavRow(Lucide.Download, "Check for a new version", null) { viewModel.checkForUpdate(context) { toast(it) } }
                        }
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Lock, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
                        val where = if (s.syncDir != null || s.syncServer != null) "Stored on your devices only" else "Stored only on this device"
                        Text("$where · Leftovers $version", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, textAlign = TextAlign.Center)
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
                text = {
                    fun restore(keep: Boolean) {
                        pendingRestore = null
                        restoreKeeps = keep
                        viewModel.restoreFrom(
                            uri,
                            keep = keep,
                            onNeedPassword = { wrong -> lockedRestore = uri to wrong },
                            // Not a backup, but maybe a statement or another app's export: offer its entries instead.
                            onNotBackup = {
                                viewModel.readCsv(context, uri, onNeedPassword = { wrong -> lockedPdf = uri to wrong }) { message -> toast("That isn't a Leftovers backup. $message") }
                            },
                        ) { toast(it) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Keep what's in the app and add the backup's entries (any already here aren't added twice), or replace everything with the backup.",
                            color = c.textSecondary,
                        )
                        PrimaryButton("Keep mine and add the backup", { restore(keep = true) }, Modifier.fillMaxWidth())
                        SecondaryButton("Replace everything", { restore(keep = false) }, Modifier.fillMaxWidth())
                        Text(
                            "Replacing removes what's in the app now, including its settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textTertiary,
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        lockedPdf?.let { (uri, wrong) ->
            var typed by remember(uri, wrong) { mutableStateOf("") }
            GlassAlertDialog(
                onDismissRequest = { lockedPdf = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("This statement has a password") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Banks usually set it to something like your date of birth or customer ID; the email or the bank's website says which. It's only used to open the file on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                        )
                        GlassTextField(typed, { typed = it }, placeholder = "Password", password = true)
                        if (wrong) Text("That password didn't work", style = MaterialTheme.typography.bodySmall, color = c.negative)
                    }
                },
                confirmButton = {
                    TextButton(enabled = typed.isNotEmpty(), onClick = {
                        lockedPdf = null
                        viewModel.readCsv(context, uri, typed, onNeedPassword = { w -> lockedPdf = uri to w }) { toast(it) }
                    }) { Text("Open", color = c.textPrimary) }
                },
                dismissButton = { TextButton(onClick = { lockedPdf = null }) { Text("Cancel", color = c.textSecondary) } },
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
                        viewModel.restoreFrom(uri, typed, keep = restoreKeeps, onNeedPassword = { w -> lockedRestore = uri to w }) { toast(it) }
                    }) { Text("Restore", color = c.textPrimary) }
                },
                dismissButton = { TextButton(onClick = { lockedRestore = null }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        if (chooseSync) {
            GlassAlertDialog(
                onDismissRequest = { chooseSync = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                title = { Text("Sync through") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ListRow("A folder", subtitle = "Syncthing, Nextcloud app, any synced folder", leading = { IconTile(Lucide.Folder, c.textPrimary, size = 38.dp) }) {
                            chooseSync = false
                            pickSyncFolder.launch(null)
                        }
                        ListRow("My WebDAV server", subtitle = "Nextcloud, ownCloud, any WebDAV", leading = { IconTile(Lucide.Globe, c.textPrimary, size = 38.dp) }) {
                            chooseSync = false
                            syncServerForm = true
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { chooseSync = false }) { Text("Cancel", color = c.textSecondary) } },
            )
        }

        if (syncServerForm) {
            var url by rememberSaveable { mutableStateOf(s.syncServer ?: "https://") }
            var user by rememberSaveable { mutableStateOf(s.syncUser.orEmpty()) }
            var pass by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val ok = url.length > "https://".length && user.isNotBlank() && pass.isNotEmpty() && !busy
            GlassSheet({ if (!busy) syncServerForm = false }) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("WebDAV server", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                    GlassTextField(url, { url = it.trim(); error = null }, placeholder = "https://", label = "Folder address", keyboardType = KeyboardType.Uri)
                    Text(
                        "For Nextcloud: https://your-server/remote.php/dav/files/your-name/Leftovers",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    GlassTextField(user, { user = it.trim(); error = null }, placeholder = "Your user name", label = "User name", keyboardType = KeyboardType.Email)
                    GlassTextField(pass, { pass = it; error = null }, placeholder = "Password", label = "Password", password = true)
                    Text(
                        "With Nextcloud, make an app password in Settings › Security, so this phone can be signed out on its own. It's kept encrypted on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.negative, modifier = Modifier.padding(start = 6.dp)) }
                    PrimaryButton(
                        if (busy) "Connecting…" else "Connect",
                        {
                            busy = true
                            viewModel.enableServerSync(context, url, user.trim(), pass) { problem ->
                                busy = false
                                if (problem == null) syncServerForm = false else error = problem
                            }
                        },
                        Modifier.fillMaxWidth(),
                        enabled = ok,
                    )
                }
            }
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
            val state = rememberTimePickerState(s.reminderMinutes / 60, s.reminderMinutes % 60, is24Hour = com.leftovers.app.util.LocalUse24Hour.current)
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
                            // The totals, so an amount read wrongly shows before anything is added.
                            if (adding > 0) {
                                val spent = preview.toAdd.filter { it.type == com.leftovers.app.data.TxType.EXPENSE }.sumOf { it.amountMinor }
                                val earned = preview.toAdd.filter { it.type == com.leftovers.app.data.TxType.INCOME }.sumOf { it.amountMinor }
                                listOfNotNull(
                                    if (spent > 0) "${money.format(spent)} spending" else null,
                                    if (earned > 0) "${money.format(earned)} income" else null,
                                ).joinToString(" and ").takeIf { it.isNotEmpty() }?.let { "$it in total." }
                            } else null,
                            if (preview.duplicates > 0) "${preview.duplicates} already in Leftovers ${if (preview.duplicates == 1) "is" else "are"} skipped." else null,
                            if (preview.noCategory > 0) "${if (preview.noCategory == adding) "They have" else "${preview.noCategory} have"} no category, so ${if (preview.noCategory == 1) "it goes" else "they go"} to Other; change any later." else null,
                            preview.newCategories.takeIf { it.isNotEmpty() }?.let { list ->
                                val names = list.map { it.second }
                                val shown = if (names.size > 4) names.take(3).joinToString(", ") + " and ${names.size - 3} more"
                                    else names.dropLast(1).joinToString(", ").let { if (it.isEmpty()) names.last() else "$it and ${names.last()}" }
                                "${if (names.size == 1) "A new category is" else "${names.size} new categories are"} added: $shown."
                            },
                            if (preview.unreadable > 0) "${preview.unreadable} ${if (preview.unreadable == 1) "row" else "rows"} without a readable date or amount ${if (preview.unreadable == 1) "is" else "are"} left out." else null,
                            if (preview.transfers > 0) "${preview.transfers} ${if (preview.transfers == 1) "transfer" else "transfers"} between accounts ${if (preview.transfers == 1) "is" else "are"} left out, as ${if (preview.transfers == 1) "it isn't" else "they aren't"} spending or income." else null,
                        ).joinToString(" "),
                    )
                },
                confirmButton = {
                    if (adding > 0) {
                        TextButton(onClick = {
                            viewModel.confirmCsvImport { n, at ->
                                scope.launch {
                                    val r = snackbar.showSnackbar("Imported $n ${if (n == 1) "entry" else "entries"}", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Long)
                                    // Undo: the import's entries go to Deleted entries, like removing it from the list.
                                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        viewModel.removeImport(com.leftovers.app.util.ImportBatches.Batch(at, at, n, 0, 0)) { m -> scope.launch { snackbar.showSnackbar("Removed $m ${if (m == 1) "entry" else "entries"}") } }
                                    }
                                }
                            }
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
        modifier = Modifier.jumpTarget(title),
        leading = { IconTile(icon, c.textPrimary, size = 38.dp) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    // Capped, so a long value shortens itself and the row's title always shows in full.
                    Text(value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp).padding(end = 6.dp))
                }
                Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
        },
        onClick = onClick,
    )
}

/** A category on Settings' main page (or a search result): its name, with what's in it underneath, like a phone's. */
@Composable
private fun PageRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    ListRow(
        title,
        subtitle = summary,
        leading = { IconTile(icon, c.textPrimary, size = 38.dp) },
        trailing = { Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit, inset: Boolean = true) {
    val c = LocalAppColors.current
    val flip = toggleHaptic(onChange)
    ListRow(
        title,
        modifier = (if (inset) Modifier else Modifier.padding(horizontal = (0).dp)).jumpTarget(title),
        leading = { IconTile(icon, c.textPrimary, size = 38.dp) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = flip,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.accent,
                    checkedThumbColor = c.onAccent,
                    uncheckedTrackColor = c.glass,
                    uncheckedBorderColor = c.borderTop,
                    uncheckedThumbColor = c.textSecondary,
                ),
            )
        },
        onClick = { flip(!checked) },
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

private fun lockAfterLabel(seconds: Int) = when (seconds) {
    0 -> "Immediately"
    30 -> "After 30 s"
    else -> "After ${seconds / 60} min"
}

/** How app lock unlocks (the phone's lock, an app PIN or a password) and how soon it locks after leaving. */
@Composable
private fun LockOptionsSheet(
    kind: com.leftovers.app.util.AppPasscode.Kind?,
    phoneLock: Boolean,
    lockAfter: Int,
    fingerprint: Boolean,
    onSaveCode: (String, com.leftovers.app.util.AppPasscode.Kind) -> Unit,
    onUsePhoneLock: () -> Unit,
    onLockAfter: (Int) -> Unit,
    onFingerprint: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    // null is the phone's lock; it can't be picked when the phone has none.
    var method by rememberSaveable { mutableStateOf(kind?.name ?: if (phoneLock) "PHONE" else com.leftovers.app.util.AppPasscode.Kind.PIN.name) }
    var first by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    val choosing = method != "PHONE" && method != kind?.name
    val pin = method == com.leftovers.app.util.AppPasscode.Kind.PIN.name
    val hasBiometric = androidx.biometric.BiometricManager.from(context)
        .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Lock options", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text("Unlock with", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
            SegmentedToggle(
                options = listOfNotNull("PHONE".takeIf { phoneLock }, "PIN", "PASSWORD"),
                selected = method,
                label = { when (it) { "PHONE" -> "Phone lock"; "PIN" -> "App PIN"; else -> "Password" } },
                onSelect = { next ->
                    method = next
                    first = ""
                    again = ""
                    if (next == "PHONE" && kind != null) onUsePhoneLock()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when (method) {
                    "PHONE" -> "Your fingerprint, face or the phone's own screen lock."
                    "PIN" -> "A PIN just for Leftovers, ${com.leftovers.app.util.AppPasscode.MIN_PIN} to ${com.leftovers.app.util.AppPasscode.MAX_PIN} digits, separate from the phone's lock."
                    else -> "A password just for Leftovers, at least ${com.leftovers.app.util.AppPasscode.MIN_PASSWORD} characters."
                } + if (method != "PHONE") " It can't be recovered; if you forget it, the phone's screen lock still opens the app${if (phoneLock) "" else " once you set one up"}." else "",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            if (method != "PHONE") {
                if (choosing || kind == null) {
                    val valid = if (pin) first.length in com.leftovers.app.util.AppPasscode.MIN_PIN..com.leftovers.app.util.AppPasscode.MAX_PIN else first.length >= com.leftovers.app.util.AppPasscode.MIN_PASSWORD
                    GlassTextField(
                        first,
                        { first = if (pin) it.filter(Char::isDigit).take(com.leftovers.app.util.AppPasscode.MAX_PIN) else it.take(64) },
                        placeholder = if (pin) "New PIN" else "New password",
                        keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Text,
                        password = true,
                    )
                    GlassTextField(
                        again,
                        { again = if (pin) it.filter(Char::isDigit).take(com.leftovers.app.util.AppPasscode.MAX_PIN) else it.take(64) },
                        placeholder = if (pin) "Same PIN again" else "Same password again",
                        keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Text,
                        password = true,
                    )
                    if (again.isNotEmpty() && again != first) {
                        Text("They don't match", style = MaterialTheme.typography.bodySmall, color = c.negative, modifier = Modifier.padding(start = 4.dp))
                    }
                    PrimaryButton(
                        if (pin) "Save PIN" else "Save password",
                        {
                            onSaveCode(first, if (pin) com.leftovers.app.util.AppPasscode.Kind.PIN else com.leftovers.app.util.AppPasscode.Kind.PASSWORD)
                            onDismiss()
                        },
                        Modifier.fillMaxWidth(),
                        enabled = valid && first == again,
                    )
                } else {
                    SecondaryButton(if (pin) "Change PIN" else "Change password", {
                        method = ""
                        method = if (pin) "PIN" else "PASSWORD"
                        first = " "
                        first = ""
                    }, Modifier.fillMaxWidth())
                }
                if (hasBiometric && kind != null && !choosing) {
                    Glass(Modifier.fillMaxWidth()) {
                        ToggleRow(Lucide.Lock, "Also unlock with fingerprint or face", fingerprint, onFingerprint)
                    }
                }
            }
            Text("Lock after leaving the app", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            SegmentedToggle(
                options = listOf(0, 30, 300),
                selected = if (lockAfter in listOf(0, 30, 300)) lockAfter else 0,
                label = { lockAfterLabel(it) },
                onSelect = onLockAfter,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Picking a photo, taking one or sharing a file doesn't lock it.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

/**
 * Which apps' notifications are read for payments: the built-in payment apps, and any app that has sent a
 * notification recently (a bank's own app, say). Each can put its payments in a chosen account.
 */
@Composable
private fun PaymentAppsSheet(onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val container = (LocalContext.current.applicationContext as com.leftovers.app.LeftoversApp).container
    val apps by container.paymentApps.apps.collectAsStateWithLifecycle()
    val accounts by container.accounts.accounts.collectAsStateWithLifecycle(emptyList())
    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Payment apps", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                "Only the apps switched on are read. Apps that sent you a notification lately show up here too, so you can add your bank's app.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    apps.forEachIndexed { i, app ->
                        if (i > 0) RowDivider()
                        var choosing by remember { mutableStateOf(false) }
                        val account = accounts.firstOrNull { it.id == app.accountId }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                if (app.read && accounts.size > 1) {
                                    Box {
                                        Row(
                                            Modifier.padding(top = 2.dp).pressable({ choosing = true }, pressedScale = 0.96f),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                account?.let { "Goes to ${it.name}" } ?: "Account from the message",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = c.accent,
                                            )
                                            Icon(Lucide.ChevronDown, contentDescription = "Choose account", tint = c.accent, modifier = Modifier.size(14.dp))
                                        }
                                        androidx.compose.material3.DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("Account from the message") },
                                                onClick = { container.paymentApps.setAccount(app.pkg, null); choosing = false },
                                            )
                                            accounts.forEach { a ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(a.name) },
                                                    onClick = { container.paymentApps.setAccount(app.pkg, a.id); choosing = false },
                                                )
                                            }
                                        }
                                    }
                                } else if (!app.read && app.pkg !in com.leftovers.app.util.PaymentApps.names) {
                                    Text("Sent you a notification lately", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                                }
                            }
                            // An app that isn't read can be taken off the list (#17).
                            if (!app.read) {
                                androidx.compose.material3.IconButton(onClick = { container.paymentApps.hide(app.pkg) }) {
                                    Icon(Lucide.X, contentDescription = "Hide ${app.name}", tint = c.textTertiary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Switch(
                                checked = app.read,
                                onCheckedChange = toggleHaptic({ container.paymentApps.setRead(app.pkg, it) }),
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
                            )
                        }
                    }
                }
            }
            val hiddenCount by container.paymentApps.hiddenCount.collectAsStateWithLifecycle()
            if (hiddenCount > 0) {
                Text(
                    "Show $hiddenCount hidden ${if (hiddenCount == 1) "app" else "apps"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.pressable({ container.paymentApps.showHidden() }).padding(vertical = 4.dp, horizontal = 6.dp),
                )
            }
            Text(
                "With no account chosen, the message's last digits or bank name pick it, like bank SMS.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

/** One thing liquid glass costs, as a line with its icon. */
@Composable
private fun LiquidCost(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    }
}

/** Where Help asked Settings to open, by the titles in its path ("Settings → Backup & data → Import entries"). */
object SettingsJump {
    var section by mutableStateOf<String?>(null)
    var row by mutableStateOf<String?>(null)
    /** Where the asked-for row is on the screen, once drawn. */
    var rowTop by mutableStateOf<Float?>(null)
    /** The row lit up for a moment after arriving. */
    var flash by mutableStateOf<String?>(null)

    fun open(where: String) {
        val parts = where.split(" → ")
        section = parts.getOrNull(1) ?: "bottom"
        row = parts.getOrNull(2)
        rowTop = null
    }
}

/** A row Help can point at: reports where it is when asked for, and glows briefly once there. */
@Composable
private fun Modifier.jumpTarget(title: String): Modifier {
    val c = LocalAppColors.current
    val glow by androidx.compose.animation.animateColorAsState(
        if (SettingsJump.flash == title) c.accent.copy(alpha = 0.2f) else Color.Transparent,
        androidx.compose.animation.core.tween(450),
        label = "jumpGlow",
    )
    return this
        .then(if (SettingsJump.row == title) Modifier.onGloballyPositioned { SettingsJump.rowTop = it.positionInRoot().y } else Modifier)
        .background(glow, RoundedCornerShape(16.dp))
}
