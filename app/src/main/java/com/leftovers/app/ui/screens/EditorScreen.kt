package com.leftovers.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.AccountRepository
import com.leftovers.app.data.AccountWithBalance
import com.leftovers.app.data.Category
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.SmsRepository
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.Keypad
import com.leftovers.app.ui.components.KeypadKey
import com.leftovers.app.ui.components.RollingText
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.glassBorder
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.CategoryIcons
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AmountInput
import com.leftovers.app.util.BudgetAlertManager
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.ReceiptStore
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A frequent entry the user can repeat in one tap. */
data class QuickPick(val categoryId: Long, val amountMinor: Long, val note: String, val icon: String, val color: Long, val label: String)

/** Drives the keypad editor for adding a new entry or editing an existing one. */
class EditorViewModel(
    savedState: SavedStateHandle,
    private val app: Application,
    private val repository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val settings: SettingsRepository,
    private val sms: SmsRepository,
    private val planning: PlanningRepository,
    private val budgetAlerts: BudgetAlertManager,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: -1L
    private val smsId: Long = savedState.get<Long>("sms") ?: -1L
    val isEditing = id > 0

    var amountText by mutableStateOf(savedState.get<Long>("amount")?.takeIf { it > 0 }?.let(AmountInput::fromMinor).orEmpty())
        private set
    var type by mutableStateOf(TxType.EXPENSE)
        private set
    var categoryId by mutableStateOf<Long?>(null)
        private set
    /** New entries can start on a specific day, e.g. when adding from the calendar. */
    var date by mutableStateOf(savedState.get<Long>("day")?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay) ?: LocalDate.now())
    var note by mutableStateOf(savedState.get<String>("note").orEmpty())
    var accountId by mutableStateOf<Long?>(null)
    var receiptPath by mutableStateOf<String?>(null)
        private set
    /** When on, saving also creates a subscription that repeats on this day every month. */
    var repeatMonthly by mutableStateOf(false)
    var needCategory by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(!isEditing)
        private set

    private var createdAt = System.currentTimeMillis()
    private var originalReceipt: String? = null

    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<AccountWithBalance>> =
        accountRepository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<TransactionItem>> =
        repository.allTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (isEditing) {
                repository.getTransaction(id)?.let { tx ->
                    amountText = AmountInput.fromMinor(tx.amountMinor)
                    type = tx.type
                    categoryId = tx.categoryId
                    date = LocalDate.ofEpochDay(tx.epochDay)
                    note = tx.note
                    createdAt = tx.createdAt
                    accountId = tx.accountId
                    receiptPath = tx.receiptPath
                    originalReceipt = tx.receiptPath
                }
                loaded = true
            }
            if (accountId == null) accountId = settings.settings.first().defaultAccountId
        }
    }

    val amountMinor: Long get() = AmountInput.toMinor(amountText) ?: 0L

    fun onKey(key: KeypadKey) {
        val next = when (key) {
            is KeypadKey.Digit -> if (amountText == "0") key.value.toString() else amountText + key.value
            KeypadKey.Dot -> when {
                '.' in amountText -> amountText
                amountText.isEmpty() -> "0."
                else -> "$amountText."
            }
            KeypadKey.DoubleZero -> if (amountText.isEmpty() || amountText == "0") amountText else amountText + "00"
            KeypadKey.Erase -> amountText.dropLast(1)
        }
        AmountInput.sanitize(next)?.let { amountText = it }
    }

    fun clearAmount() {
        amountText = ""
    }

    fun onTypeChange(newType: TxType) {
        if (newType != type) {
            type = newType
            categoryId = null
        }
    }

    fun selectCategory(id: Long) {
        categoryId = id
        needCategory = false
    }

    fun applyQuickPick(pick: QuickPick) {
        amountText = AmountInput.fromMinor(pick.amountMinor)
        categoryId = pick.categoryId
        note = pick.note
        needCategory = false
    }

    fun attachReceipt(uri: Uri, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val path = ReceiptStore.import(app, uri)
            if (path != null) {
                if (receiptPath != originalReceipt) ReceiptStore.delete(receiptPath)
                receiptPath = path
            }
            onDone()
        }
    }

    fun removeReceipt() {
        if (receiptPath != originalReceipt) ReceiptStore.delete(receiptPath)
        receiptPath = null
    }

    /** Categories of the current type, most used first. */
    fun sortedCategories(all: List<Category>, items: List<TransactionItem>): List<Category> {
        val since = LocalDate.now().minusDays(120).toEpochDay()
        val usage = items.filter { it.epochDay >= since }.groupingBy { it.categoryId }.eachCount()
        return all.filter { it.type == type }.sortedByDescending { usage[it.id] ?: 0 }
    }

    /** Entries repeated at least twice recently, e.g. the daily coffee. */
    fun quickPicks(items: List<TransactionItem>): List<QuickPick> {
        val since = LocalDate.now().minusDays(120).toEpochDay()
        return items.asSequence()
            .filter { it.type == type && it.epochDay >= since }
            .groupBy { Triple(it.categoryId, it.amountMinor, it.note.trim()) }
            .filter { it.value.size >= 2 }
            .entries
            .sortedByDescending { it.value.size }
            .take(6)
            .map { (key, list) ->
                val first = list.first()
                QuickPick(key.first, key.second, key.third, first.categoryEmoji, first.categoryColor, key.third.ifBlank { first.categoryName })
            }
            .toList()
    }

    /**
     * Saves and reports a short confirmation message. Returns false (and flags the category row)
     * when the entry isn't complete yet.
     */
    fun save(onDone: (String) -> Unit, describe: (Long) -> String): Boolean {
        if (amountMinor <= 0) return false
        val category = categoryId
        if (category == null) {
            needCategory = true
            return false
        }
        val savedType = type
        val savedAmount = amountMinor
        val savedDate = date
        viewModelScope.launch {
            repository.saveTransaction(
                Transaction(
                    id = if (isEditing) id else 0,
                    amountMinor = savedAmount,
                    type = savedType,
                    categoryId = category,
                    epochDay = savedDate.toEpochDay(),
                    note = note.trim(),
                    createdAt = createdAt,
                    accountId = accountId,
                    receiptPath = receiptPath,
                ),
            )
            if (originalReceipt != null && originalReceipt != receiptPath) ReceiptStore.delete(originalReceipt)
            if (smsId > 0) sms.dismiss(smsId)
            val repeats = repeatMonthly && !isEditing
            if (repeats) {
                val month = YearMonth.from(savedDate).toString()
                planning.saveRecurring(
                    Recurring(
                        name = note.trim().ifBlank { categories.value.find { it.id == category }?.name.orEmpty() },
                        amountMinor = savedAmount,
                        type = savedType,
                        categoryId = category,
                        dayOfMonth = savedDate.dayOfMonth,
                        startMonth = month,
                        // The entry just saved is this month's charge.
                        lastPostedMonth = month,
                    ),
                )
            }
            if (savedType == TxType.EXPENSE) budgetAlerts.checkAfterExpense(savedDate, category)
            val name = categories.value.find { it.id == category }?.name.orEmpty()
            val whenText = if (savedDate == LocalDate.now()) "" else " · ${savedDate.friendlyLabel()}"
            onDone(
                when {
                    isEditing -> "Changes saved"
                    repeats -> "${describe(savedAmount)} added · repeats every ${ordinal(savedDate.dayOfMonth)}"
                    else -> "${describe(savedAmount)} added to $name$whenText"
                },
            )
        }
        return true
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.getTransaction(id)?.let {
                repository.deleteTransaction(it)
                ReceiptStore.delete(it.receiptPath)
            }
            onDone()
        }
    }
}

private val stripDay = DateTimeFormatter.ofPattern("EEE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onManageCategories: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val view = LocalView.current
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var receiptMenu by rememberSaveable { mutableStateOf(false) }
    var viewReceipt by rememberSaveable { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachReceipt(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) viewModel.attachReceipt(Uri.fromFile(file)) { file.delete() } else file?.delete()
    }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Lucide.X, "Close", onClose)
            Spacer(Modifier.weight(1f))
            SegmentedToggle(
                options = TxType.entries,
                selected = viewModel.type,
                label = { if (it == TxType.EXPENSE) "Expense" else "Income" },
                onSelect = viewModel::onTypeChange,
                modifier = Modifier.width(210.dp),
            )
            Spacer(Modifier.weight(1f))
            if (viewModel.isEditing) {
                RoundButton(Lucide.Trash2, "Delete", { confirmDelete = true }, tint = c.negative)
            } else {
                Spacer(Modifier.size(44.dp))
            }
        }

        if (!viewModel.loaded) return@Column

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val display = money.groupInput(viewModel.amountText)
            val size = when (display.length) {
                in 0..5 -> 64
                in 6..7 -> 54
                in 8..9 -> 44
                else -> 36
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(money.symbol, style = MaterialTheme.typography.headlineMedium, color = c.textTertiary)
                Spacer(Modifier.width(6.dp))
                RollingText(
                    text = display,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = size.sp, lineHeight = (size * 1.1).sp),
                    color = when {
                        viewModel.amountText.isEmpty() -> c.textTertiary
                        viewModel.type == TxType.INCOME -> c.positive
                        else -> c.textPrimary
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    text = viewModel.note.ifBlank { "Note" }.let { if (it.length > 18) it.take(17) + "…" else it },
                    onClick = { showNote = true },
                    icon = Lucide.PenLine,
                    iconTint = c.textSecondary,
                )
                if (!viewModel.isEditing) {
                    Chip(
                        "Monthly",
                        onClick = { viewModel.repeatMonthly = !viewModel.repeatMonthly },
                        icon = Lucide.Repeat,
                        iconTint = c.textSecondary,
                        selected = viewModel.repeatMonthly,
                    )
                }
                val path = viewModel.receiptPath
                if (path == null) {
                    RoundButton(Lucide.Camera, "Attach receipt", { receiptMenu = true }, size = 42.dp, tint = c.textSecondary)
                } else {
                    ReceiptThumb(path, onOpen = { viewReceipt = true }, onRemove = viewModel::removeReceipt)
                }
            }
            AnimatedVisibility(viewModel.repeatMonthly, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(
                    "Logged now and again on the ${ordinal(viewModel.date.dayOfMonth)} of every month",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (accounts.size > 1) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(accounts, key = { it.id }) { a ->
                        SmallChip(a.name, CategoryIcons[a.icon], Color(a.color), selected = viewModel.accountId == a.id) { viewModel.accountId = a.id }
                    }
                }
            }
            val picks = remember(history, viewModel.type) { viewModel.quickPicks(history) }
            if (!viewModel.isEditing && picks.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Quick add", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(picks) { pick ->
                        Glass(shape = RoundedCornerShape(18.dp), onClick = { viewModel.applyQuickPick(pick) }) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(CategoryIcons[pick.icon], contentDescription = null, tint = Color(pick.color), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(pick.label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(money.format(pick.amountMinor), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        DateStrip(
            selected = viewModel.date,
            onSelect = { viewModel.date = it },
            onOpenCalendar = { showCalendar = true },
        )
        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(viewModel.needCategory, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                "Choose a category to save",
                color = c.warning,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
        }
        val sorted = remember(categories, history, viewModel.type) { viewModel.sortedCategories(categories, history) }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sorted, key = { it.id }) { cat ->
                Chip(
                    text = cat.name,
                    onClick = { viewModel.selectCategory(cat.id) },
                    icon = CategoryIcons[cat.emoji],
                    iconTint = Color(cat.color),
                    selected = viewModel.categoryId == cat.id,
                )
            }
            item { Chip("Edit", onClick = onManageCategories, icon = Lucide.SlidersHorizontal, iconTint = c.textSecondary) }
        }
        Spacer(Modifier.height(14.dp))

        Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)) {
            Keypad(
                onKey = viewModel::onKey,
                onClear = viewModel::clearAmount,
                onConfirm = {
                    val ok = viewModel.save(onDone = onSaved, describe = money::format)
                    if (!ok) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                },
                confirmEnabled = viewModel.amountMinor > 0,
                confirmLabel = if (viewModel.isEditing) "Save" else "Add",
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }

    if (receiptMenu) {
        GlassSheet(onDismiss = { receiptMenu = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Attach a receipt", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                Glass(Modifier.fillMaxWidth(), onClick = {
                    receiptMenu = false
                    val (uri, file) = ReceiptStore.newCameraUri(context)
                    cameraFile = file
                    takePhoto.launch(uri)
                }) { com.leftovers.app.ui.components.ListRow("Take a photo", leading = { Icon(Lucide.Camera, null, tint = c.textPrimary) }) }
                Glass(Modifier.fillMaxWidth(), onClick = {
                    receiptMenu = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { com.leftovers.app.ui.components.ListRow("Choose from gallery", leading = { Icon(Lucide.ReceiptText, null, tint = c.textPrimary) }) }
            }
        }
    }

    if (viewReceipt) {
        viewModel.receiptPath?.let { path -> ReceiptViewer(path) { viewReceipt = false } }
    }

    if (showCalendar) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showCalendar = false
                }) { Text("Done", color = c.textPrimary) }
            },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Cancel", color = c.textSecondary) } },
        ) {
            DatePicker(
                pickerState,
                title = { Text("Which day was it?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, top = 20.dp)) },
                colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }

    if (showNote) {
        var draft by rememberSaveable { mutableStateOf(viewModel.note) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { showNote = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Note", style = MaterialTheme.typography.titleLarge) },
            text = { GlassTextField(draft, { draft = it.take(120) }, placeholder = "e.g. Lunch with friends", focusRequester = focus) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.note = draft.trim()
                    showNote = false
                }) { Text("Done", color = c.textPrimary) }
            },
            dismissButton = { TextButton(onClick = { showNote = false }) { Text("Cancel", color = c.textSecondary) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onClose)
                }) { Text("Delete", color = c.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
}

@Composable
private fun SmallChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(if (selected) c.glassStrong.copy(alpha = c.glassStrong.alpha * 1.8f) else Color.Transparent, label = "smallChip")
    Row(
        Modifier
            .height(32.dp)
            .pressable(onClick, pressedScale = 0.94f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) tint.copy(alpha = 0.6f) else c.borderBottom, RoundedCornerShape(50))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) c.textPrimary else c.textSecondary)
    }
}

@Composable
private fun rememberReceiptBitmap(path: String, maxSide: Int): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, path) { value = ReceiptStore.loadPreview(path, maxSide) }
    return bitmap
}

@Composable
private fun ReceiptThumb(path: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val c = LocalAppColors.current
    val bitmap = rememberReceiptBitmap(path, 200)
    Box {
        Box(
            Modifier
                .size(42.dp)
                .pressable(onOpen)
                .clip(RoundedCornerShape(12.dp))
                .background(c.glass)
                .border(1.dp, glassBorder(c), RoundedCornerShape(12.dp)),
        ) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Receipt", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(start = 30.dp)
                .size(18.dp)
                .pressable(onRemove)
                .clip(RoundedCornerShape(50))
                .background(c.negative),
            contentAlignment = Alignment.Center,
        ) { Icon(Lucide.X, contentDescription = "Remove receipt", tint = Color.White, modifier = Modifier.size(11.dp)) }
    }
}

@Composable
private fun ReceiptViewer(path: String, onDismiss: () -> Unit) {
    val bitmap = rememberReceiptBitmap(path, 2000)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .pressable(onDismiss, pressedScale = 1f),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Receipt", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp)) }
        }
    }
}

/**
 * Horizontal strip of the last few weeks so a forgotten spend can be logged in one tap,
 * plus a button for the full calendar.
 */
@Composable
private fun DateStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit, onOpenCalendar: () -> Unit) {
    val c = LocalAppColors.current
    val today = LocalDate.now()
    val days = remember(today) { (0L until 45L).map { today.minusDays(it) } }
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = days.indexOf(selected)
        if (index >= 0) listState.animateScrollToItem((index - 2).coerceAtLeast(0))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true,
        ) {
            items(days, key = { it.toEpochDay() }) { day ->
                val isSelected = day == selected
                val bg by animateColorAsState(if (isSelected) c.accent else c.glass, label = "dayBg")
                val fg by animateColorAsState(if (isSelected) c.onAccent else c.textPrimary, label = "dayFg")
                Column(
                    Modifier
                        .width(52.dp)
                        .pressable({ onSelect(day) }, pressedScale = 0.93f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(bg)
                        .border(1.dp, glassBorder(c), RoundedCornerShape(18.dp))
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when (day) {
                            today -> "Today"
                            today.minusDays(1) -> "Yday"
                            else -> day.format(stripDay)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = fg)
                }
            }
        }
        RoundButton(Lucide.Calendar, "Pick a date", onOpenCalendar, size = 52.dp, modifier = Modifier.padding(end = 16.dp))
    }
}
