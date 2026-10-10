package com.leftovers.app.ui.screens

import com.leftovers.app.data.Debt
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.input.KeyboardType

import com.leftovers.app.data.allTags
import com.leftovers.app.data.RecurringItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.SecondaryButton
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
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
import com.leftovers.app.ui.components.AmountCursor
import com.leftovers.app.ui.components.AmountEdit
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.applyKey
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

/** A slice of a split entry: [amountMinor] goes to [categoryId]; the rest stays with the main category. */
data class SplitPart(val categoryId: Long, val amountMinor: Long)

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
    /** Other categories that take part of the amount; saved as separate entries. */
    val splits = mutableStateListOf<SplitPart>()
    val splitTotal: Long get() = splits.sumOf { it.amountMinor }
    /** People sharing this expense; each one's share is saved to Money owed. */
    val people = mutableStateListOf<String>()
    /** What each other person owes when it's split equally. You keep any odd paisa, so the shares always add up to the total. */
    val personShare: Long get() = if (people.isEmpty()) 0 else totalMinor / (people.size + 1)
    /** Off when each person's amount is typed in [customShares] instead of split equally. */
    var equalSplit by mutableStateOf(true)
    /** Typed amounts by person, as text, for an unequal split. */
    val customShares = mutableStateMapOf<String, String>()

    fun shareOf(person: String): Long = if (equalSplit) personShare else AmountInput.toMinor(customShares[person].orEmpty()) ?: 0L

    fun clearPeople() {
        people.clear()
        customShares.clear()
        equalSplit = true
    }

    /** Travel mode: the amount is typed in this currency and saved converted at [rateText]; null is the app's currency. */
    var foreignCurrency by mutableStateOf<String?>(null)
        private set
    /** How much one unit of [foreignCurrency] is in the app's currency, as typed ("22.7"). */
    var rateText by mutableStateOf("")
        private set
    private val rate: java.math.BigDecimal? get() = rateText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    /** The amount in the app's currency: what's saved and counted. */
    val totalMinor: Long get() {
        val code = foreignCurrency ?: return amountMinor
        val r = rate ?: return 0
        return java.math.BigDecimal.valueOf(amountMinor).multiply(r).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    fun useForeign(code: String?, rate: String) {
        foreignCurrency = code
        rateText = if (code == null) "" else rate
    }

    /** The rate last used for [code], to start the rate box with. */
    suspend fun lastRate(code: String): String = settings.settings.first().foreignRates[code].orEmpty()
    val othersOwe: Long get() = people.sumOf { shareOf(it) }
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
    /** Names from Money owed, most recent first, to pick from when splitting. */
    val knownPeople: StateFlow<List<String>> = planning.debts
        .map { debts -> debts.sortedByDescending { it.createdAt }.map { it.person.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (isEditing) {
                repository.getTransaction(id)?.let { tx ->
                    amountText = AmountInput.fromMinor(tx.amountMinor)
                    val foreign = tx.foreignMinor
                    if (tx.foreignCurrency != null && foreign != null && foreign > 0) {
                        foreignCurrency = tx.foreignCurrency
                        amountText = AmountInput.fromMinor(foreign)
                        rateText = java.math.BigDecimal.valueOf(tx.amountMinor)
                            .divide(java.math.BigDecimal.valueOf(foreign), 4, java.math.RoundingMode.HALF_UP)
                            .stripTrailingZeros().toPlainString()
                    }
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
            if (accountId == null) {
                // The account Home is showing, if one is picked there; otherwise the default account.
                val s = settings.settings.first()
                val known = accountRepository.accounts.first().map { it.id }
                // A payment from a bank SMS goes to the account whose last digits it names.
                val fromSms = if (smsId > 0) sms.digitsFor(smsId) else null
                // Money coming in ("credited", "received") opens as income.
                if (smsId > 0 && sms.isIncome(smsId)) onTypeChange(TxType.INCOME)
                // The account the message matched when it arrived (digits or name); older ones by digits only.
                val matched = (if (smsId > 0) sms.accountIdFor(smsId)?.takeIf { it in known } else null)
                    ?: fromSms?.let { d -> accountRepository.accounts.first().firstOrNull { it.matchesDigits(d) }?.id }
                accountId = matched ?: s.homeAccountId?.takeIf { it in known } ?: s.defaultAccountId
            }
        }
    }

    val amountMinor: Long get() = AmountInput.toMinor(amountText) ?: 0L

    /** Where the keypad types; null means the end, with no visible caret. */
    var cursor by mutableStateOf<Int?>(null)
        private set

    fun placeCursor(position: Int?) {
        cursor = position?.coerceIn(0, amountText.length)
    }

    fun onKey(key: KeypadKey) {
        val placed = cursor
        val result = applyKey(AmountEdit(amountText, placed ?: amountText.length), key) ?: return
        amountText = result.text
        cursor = if (placed == null) null else result.cursor
    }

    fun clearAmount() {
        amountText = ""
        cursor = null
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

    /**
     * Saves and reports a short confirmation message. Returns false (and flags the category row)
     * when the entry isn't complete yet.
     */
    fun save(onDone: (String) -> Unit, describe: (Long) -> String): Boolean {
        if (amountMinor <= 0 || totalMinor <= 0) return false
        val category = categoryId
        if (category == null) {
            needCategory = true
            return false
        }
        val parts = if (isEditing || repeatMonthly) emptyList() else splits.toList()
        // Others' shares go to Money owed; only your own share is your expense.
        val sharers = if (isEditing || repeatMonthly || type != TxType.EXPENSE) emptyList()
        else people.map { it to shareOf(it) }.filter { it.second > 0 }
        val yours = totalMinor - sharers.sumOf { it.second }
        // The original amount abroad is kept only when this entry is the whole bill.
        val abroad = foreignCurrency?.takeIf { parts.isEmpty() && sharers.isEmpty() }
        val abroadMinor = amountMinor
        val abroadRate = rateText
        // The main category keeps whatever the split parts don't take.
        if (yours - parts.sumOf { it.amountMinor } <= 0) return false
        val savedType = type
        val savedAmount = yours - parts.sumOf { it.amountMinor }
        val savedDate = date
        viewModelScope.launch {
            parts.forEach { part ->
                repository.saveTransaction(
                    Transaction(
                        amountMinor = part.amountMinor,
                        type = savedType,
                        categoryId = part.categoryId,
                        epochDay = savedDate.toEpochDay(),
                        note = note.trim(),
                        createdAt = createdAt,
                        accountId = accountId,
                    ),
                )
                if (savedType == TxType.EXPENSE) budgetAlerts.checkAfterExpense(savedDate, part.categoryId)
            }
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
                    foreignMinor = if (abroad != null) abroadMinor else null,
                    foreignCurrency = abroad,
                ),
            )
            foreignCurrency?.let { settings.setForeignRate(it, abroadRate) }
            if (originalReceipt != null && originalReceipt != receiptPath) ReceiptStore.delete(originalReceipt)
            val owedNote = note.trim().ifBlank { categories.value.find { it.id == category }?.name.orEmpty() }
            sharers.forEach { (person, share) ->
                planning.saveDebt(Debt(person = person, amountMinor = share, note = owedNote, epochDay = savedDate.toEpochDay()))
            }
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
                        accountId = accountId,
                    ),
                )
            }
            if (savedType == TxType.EXPENSE) budgetAlerts.checkAfterExpense(savedDate, category)
            val name = categories.value.find { it.id == category }?.name.orEmpty()
            val whenText = if (savedDate == LocalDate.now()) "" else " · ${savedDate.friendlyLabel()}"
            onDone(
                when {
                    isEditing -> "Changes saved"
                    sharers.isNotEmpty() && sharers.map { it.second }.distinct().size == 1 -> "${describe(savedAmount + parts.sumOf { it.amountMinor })} added · " +
                        "${sharers.joinToString(" and ") { it.first }} ${if (sharers.size == 1) "owes" else "owe"} you ${describe(sharers[0].second)}${if (sharers.size > 1) " each" else ""}"
                    sharers.isNotEmpty() -> "${describe(savedAmount + parts.sumOf { it.amountMinor })} added · " +
                        "owed to you: " + sharers.joinToString(", ") { "${it.first} ${describe(it.second)}" }
                    parts.isNotEmpty() -> "${describe(savedAmount + parts.sumOf { it.amountMinor })} split across ${parts.size + 1} categories"
                    repeats -> "${describe(savedAmount)} added · repeats every ${ordinal(savedDate.dayOfMonth)}"
                    else -> "${describe(savedAmount)} added to $name$whenText"
                },
            )
        }
        return true
    }

    /** The subscription or recurring income that logged this entry, if any. */
    suspend fun linkedSubscription(): RecurringItem? {
        val tx = repository.getTransaction(id) ?: return null
        val categoryName = repository.categories.first().firstOrNull { it.id == tx.categoryId }?.name.orEmpty()
        return planning.recurring.first().firstOrNull { it.logged(tx.type, tx.categoryId, tx.note, categoryName) }
    }

    /** Deletes the entry; with [stopRepeating], also removes the subscription that logged it (past entries stay). */
    fun delete(stopRepeating: RecurringItem? = null, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.getTransaction(id)?.let {
                // The photo stays with the entry in Deleted entries until it's removed for good.
                repository.deleteTransaction(it)
            }
            stopRepeating?.let { planning.deleteRecurring(it.toRecurring()) }
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
    var showSplit by rememberSaveable { mutableStateOf(false) }
    var showForeign by rememberSaveable { mutableStateOf(false) }
    val knownPeople by viewModel.knownPeople.collectAsStateWithLifecycle()
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
            val targetSize = when (display.length) {
                in 0..5 -> 64f
                in 6..7 -> 54f
                in 8..9 -> 44f
                else -> 36f
            }
            // Ease between sizes as digits are added instead of jumping.
            val size by animateFloatAsState(targetSize, spring(dampingRatio = 0.9f, stiffness = 500f), label = "amountSize")
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tapping the symbol switches currency, for spending abroad.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.pressable({ showForeign = true }, pressedScale = 0.9f),
                ) {
                    Text(
                        viewModel.foreignCurrency?.let { com.leftovers.app.util.Money(it).symbol } ?: money.symbol,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (viewModel.foreignCurrency != null) c.accent else c.textTertiary,
                    )
                    Icon(Lucide.ChevronDown, contentDescription = "Change currency", tint = c.textTertiary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                val raw = viewModel.amountText
                val placed = viewModel.cursor
                RollingText(
                    text = display,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = size.sp, lineHeight = (size * 1.1).sp),
                    color = when {
                        raw.isEmpty() -> c.textTertiary
                        viewModel.type == TxType.INCOME -> c.positive
                        else -> c.textPrimary
                    },
                    caretAfter = if (placed != null && raw.isNotEmpty()) AmountCursor.displayAfter(display, raw, placed) else null,
                    caretColor = c.accent,
                    onCharTap = if (raw.isEmpty()) null else { i ->
                        val pos = AmountCursor.rawAfter(display, raw, i)
                        // Tapping where the caret already is hides it again.
                        viewModel.placeCursor(if (pos == placed) null else pos)
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            // A long note is cut short here rather than pushing the buttons below off screen.
            NotePill(viewModel.note, onClick = { showNote = true }, modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 320.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (accounts.size > 1) {
                    AccountPicker(accounts, viewModel.accountId) { viewModel.accountId = it }
                }
                if (!viewModel.isEditing) {
                    val repeating = viewModel.repeatMonthly
                    RoundButton(
                        Lucide.Repeat,
                        if (repeating) "Don't repeat" else "Repeat every month",
                        {
                            viewModel.repeatMonthly = !repeating
                            if (!repeating) {
                                viewModel.splits.clear()
                                viewModel.clearPeople()
                            }
                            if (repeating && viewModel.date > LocalDate.now()) viewModel.date = LocalDate.now()
                        },
                        size = 40.dp,
                        tint = if (repeating) c.onAccent else c.textSecondary,
                        container = if (repeating) c.accent else null,
                    )
                    if (!repeating) {
                        // One button for both kinds of split: with people, or across your own categories.
                        val split = viewModel.splits.isNotEmpty() || (viewModel.people.isNotEmpty() && viewModel.type == TxType.EXPENSE)
                        RoundButton(
                            Lucide.Split,
                            "Split this",
                            { showSplit = true },
                            size = 40.dp,
                            tint = if (split) c.onAccent else c.textSecondary,
                            container = if (split) c.accent else null,
                        )
                    }
                }
                val path = viewModel.receiptPath
                if (path == null) {
                    RoundButton(Lucide.Camera, "Attach receipt", { receiptMenu = true }, size = 40.dp, tint = c.textSecondary)
                } else {
                    ReceiptThumb(path, onOpen = { viewReceipt = true }, onRemove = viewModel::removeReceipt)
                }
            }
            viewModel.foreignCurrency?.let { code ->
                Text(
                    if (viewModel.totalMinor > 0) "≈ ${money.format(viewModel.totalMinor)} · 1 $code = ${money.symbol}${viewModel.rateText}"
                    else "Set the rate for $code",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp).pressable({ showForeign = true }, pressedScale = 0.96f),
                )
            }
            val splitting = viewModel.people.isNotEmpty() && viewModel.type == TxType.EXPENSE && !viewModel.repeatMonthly
            AnimatedVisibility(splitting, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val share = viewModel.personShare
                Text(
                    "Your share ${money.format(viewModel.totalMinor - viewModel.othersOwe)} · " +
                        if (viewModel.equalSplit) {
                            viewModel.people.joinToString(", ") + if (viewModel.people.size == 1) " owes ${money.format(share)}" else " owe ${money.format(share)} each"
                        } else {
                            viewModel.people.joinToString(", ") { "$it ${money.format(viewModel.shareOf(it))}" }
                        },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                    modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
                )
            }
            AnimatedVisibility(viewModel.repeatMonthly, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(
                    if (viewModel.date > LocalDate.now()) {
                        "First logged on ${viewModel.date.friendlyLabel()}, then the ${ordinal(viewModel.date.dayOfMonth)} of every month · tap the calendar to change"
                    } else {
                        "Logged now and again on the ${ordinal(viewModel.date.dayOfMonth)} of every month · tap the calendar to pick any day"
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                    modifier = Modifier.padding(top = 10.dp),
                )
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

    if (showSplit) {
        val expense = viewModel.type == TxType.EXPENSE
        // Opens on whichever split is already set; with neither, on people, the more common one.
        var withPeople by rememberSaveable { mutableStateOf(expense && (viewModel.people.isNotEmpty() || viewModel.splits.isEmpty())) }
        val header: @Composable () -> Unit = {
            if (expense) {
                SegmentedToggle(
                    options = listOf(true, false),
                    selected = withPeople,
                    label = { if (it) "With people" else "Across categories" },
                    onSelect = { withPeople = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        GlassSheet(onDismiss = { showSplit = false }) {
            if (withPeople && expense) {
                PeopleBody(
                    total = viewModel.totalMinor,
                    known = knownPeople,
                    viewModel = viewModel,
                    onDismiss = { showSplit = false },
                    header = header,
                )
            } else {
                SplitBody(
                    total = viewModel.totalMinor,
                    mainCategory = categories.find { it.id == viewModel.categoryId },
                    categories = categories.filter { it.type == viewModel.type },
                    parts = viewModel.splits,
                    onDismiss = { showSplit = false },
                    header = header,
                )
            }
        }
    }

    if (showForeign) {
        ForeignSheet(
            homeCode = money.currency.currencyCode,
            current = viewModel.foreignCurrency,
            currentRate = viewModel.rateText,
            lastRate = viewModel::lastRate,
            onDone = { code, rate ->
                viewModel.useForeign(code, rate)
                showForeign = false
            },
            onDismiss = { showForeign = false },
        )
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
                // Every day can be tapped; a later day is only accepted for a repeating entry (see below).
                override fun isSelectableDate(utcTimeMillis: Long) = true
            },
        )
        GlassDatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                val laterDay = (pickerState.selectedDateMillis ?: 0L) > todayUtc && !viewModel.repeatMonthly
                TextButton(enabled = !laterDay, onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showCalendar = false
                }) { Text("Done", color = if (laterDay) c.textTertiary else c.textPrimary) }
            },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Cancel", color = c.textSecondary) } },
        ) {
            DatePicker(
                pickerState,
                title = {
                    val laterDay = (pickerState.selectedDateMillis ?: 0L) > todayUtc && !viewModel.repeatMonthly
                    if (laterDay) {
                        Text(
                            "Later days are for repeating entries. Close this and tap Repeat (↻) to pick this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.warning,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
                        )
                    } else {
                        Text(if (viewModel.repeatMonthly) "Which day does it repeat from?" else "Which day was it?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, top = 20.dp))
                    }
                },
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    if (showNote) {
        var draft by rememberSaveable { mutableStateOf(viewModel.note) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        GlassAlertDialog(
            onDismissRequest = { showNote = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Note", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField(draft, { draft = it.take(120) }, placeholder = "e.g. Lunch with friends #goa", focusRequester = focus)
                    // While a #tag is being typed, offer tags used before that start the same way.
                    val typing = Regex("""#([\p{L}\p{N}_-]*)$""").find(draft)
                    val usedTags = remember(history) { history.allTags() }
                    val matches = typing?.let { m ->
                        val start = m.groupValues[1].lowercase()
                        usedTags.filter { it.startsWith(start) && it != start }.take(6)
                    }.orEmpty()
                    if (matches.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            matches.forEach { tag ->
                                Chip("#$tag", { draft = draft.substring(0, typing!!.range.first) + "#$tag " })
                            }
                        }
                    }
                }
            },
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
        val linked by produceState<RecurringItem?>(null) { value = viewModel.linkedSubscription() }
        var stopRepeating by remember { mutableStateOf(true) }
        GlassAlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Delete this entry?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("You can restore it later from Settings → Deleted entries.", color = c.textSecondary)
                    linked?.let { r ->
                        Row(
                            Modifier.fillMaxWidth().pressable({ stopRepeating = !stopRepeating }, pressedScale = 0.98f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Also stop the ${if (r.type == TxType.INCOME) "recurring income" else "subscription"} \"${r.name}\"",
                                color = c.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = stopRepeating,
                                onCheckedChange = { stopRepeating = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(linked?.takeIf { stopRepeating }, onClose)
                }) { Text("Delete", color = c.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
}

/** The note on one line, cut short with "…" when it's long. */
@Composable
private fun NotePill(note: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(
        modifier
            .height(40.dp)
            .pressable(onClick, pressedScale = 0.97f)
            .clip(RoundedCornerShape(50))
            .background(c.glass)
            .border(1.dp, c.borderBottom, RoundedCornerShape(50))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.PenLine, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            note.ifBlank { "Add a note" },
            style = MaterialTheme.typography.labelLarge,
            color = if (note.isBlank()) c.textTertiary else c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The account this entry goes to; tap to pick another. */
@Composable
private fun AccountPicker(accounts: List<AccountWithBalance>, selectedId: Long?, onSelect: (Long) -> Unit) {
    val c = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    val current = accounts.firstOrNull { it.id == selectedId } ?: accounts.first()
    Box {
        Row(
            Modifier
                .height(40.dp)
                .pressable({ open = true }, pressedScale = 0.95f)
                .clip(RoundedCornerShape(50))
                .background(c.glass)
                .border(1.dp, c.borderBottom, RoundedCornerShape(50))
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CategoryIcons[current.icon], contentDescription = null, tint = Color(current.color), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                current.name,
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronDown, contentDescription = "Change account", tint = c.textTertiary, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            accounts.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.name, color = c.textPrimary) },
                    leadingIcon = { Icon(CategoryIcons[a.icon], contentDescription = null, tint = Color(a.color), modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (a.id == current.id) {
                        { Icon(Lucide.Check, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(a.id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberReceiptBitmap(path: String, maxSide: Int): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, path) { value = ReceiptStore.loadPreview(path, maxSide) }
    return bitmap
}

@Composable
internal fun ReceiptThumb(path: String, onOpen: () -> Unit, onRemove: () -> Unit) {
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
internal fun ReceiptViewer(path: String, onDismiss: () -> Unit) {
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
                        if (day == today) "Today" else day.format(stripDay),
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

/** Moves parts of the amount into other categories; the main category keeps the rest. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitBody(
    total: Long,
    mainCategory: Category?,
    categories: List<Category>,
    parts: MutableList<SplitPart>,
    onDismiss: () -> Unit,
    header: @Composable () -> Unit,
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var pick by rememberSaveable { mutableStateOf<Long?>(null) }
    var amountText by rememberSaveable { mutableStateOf("") }
    val rest = total - parts.sumOf { it.amountMinor }
    val partMinor = AmountInput.toMinor(amountText) ?: 0L

    run {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            header()
            Text(if (total > 0) "Split ${money.format(total)} across categories" else "Split across categories", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            if (total <= 0) {
                Text("Type the full amount first, then split it here.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                return@Column
            }
            Text(
                "Each part is saved as its own entry with the same date and note.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    com.leftovers.app.ui.components.ListRow(
                        mainCategory?.name ?: "Main category",
                        subtitle = if (mainCategory == null) "Pick one on the add screen" else "Keeps the rest",
                        leading = { if (mainCategory != null) CategoryIcon(mainCategory.emoji, mainCategory.color, size = 38.dp) },
                        trailing = { Text(money.format(rest), style = MaterialTheme.typography.titleSmall, color = if (rest > 0) c.textPrimary else c.negative) },
                    )
                    parts.forEachIndexed { i, part ->
                        val cat = categories.find { it.id == part.categoryId }
                        com.leftovers.app.ui.components.RowDivider()
                        com.leftovers.app.ui.components.ListRow(
                            cat?.name ?: "Category",
                            leading = { if (cat != null) CategoryIcon(cat.emoji, cat.color, size = 38.dp) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(money.format(part.amountMinor), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                    Spacer(Modifier.width(8.dp))
                                    RoundButton(Lucide.X, "Remove part", { parts.removeAt(i) }, size = 32.dp, tint = c.textTertiary)
                                }
                            },
                        )
                    }
                }
            }
            Text("Add a part", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { it.id != mainCategory?.id }.forEach { cat ->
                    Chip(cat.name, onClick = { pick = cat.id }, icon = CategoryIcons[cat.emoji], iconTint = Color(cat.color), selected = pick == cat.id)
                }
            }
            MoneyField(amountText, { amountText = it }, label = "Amount for this part")
            PrimaryButton(
                "Add part",
                {
                    val id = pick ?: return@PrimaryButton
                    val existing = parts.indexOfFirst { it.categoryId == id }
                    if (existing >= 0) parts[existing] = parts[existing].copy(amountMinor = parts[existing].amountMinor + partMinor)
                    else parts += SplitPart(id, partMinor)
                    amountText = ""
                    pick = null
                },
                Modifier.fillMaxWidth(),
                enabled = pick != null && partMinor > 0 && partMinor < rest,
            )
            if (rest <= 0) {
                Text("The parts add up to the whole amount. Leave something for ${mainCategory?.name ?: "the main category"}.", style = MaterialTheme.typography.bodySmall, color = c.negative)
            }
            SecondaryButton("Done", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Who shares this expense. It's split equally; your share stays your expense and theirs goes to Money owed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleBody(total: Long, known: List<String>, viewModel: EditorViewModel, onDismiss: () -> Unit, header: @Composable () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val people = viewModel.people
    var newName by rememberSaveable { mutableStateOf("") }
    fun toggle(name: String) {
        val at = people.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (at >= 0) people.removeAt(at) else people += name
    }
    fun addTyped() {
        val name = newName.trim().take(30)
        if (name.isNotEmpty() && people.none { it.equals(name, ignoreCase = true) }) people += name
        newName = ""
    }

    run {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            header()
            Text(if (total > 0) "Split ${money.format(total)} with" else "Split with", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                "Your share counts as your expense, and what each person owes goes to Money owed.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            val choices = (known + people).distinctBy { it.lowercase() }
            if (choices.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { name ->
                        val picked = people.any { it.equals(name, ignoreCase = true) }
                        Chip(name, onClick = { toggle(name) }, icon = if (picked) Lucide.Check else null, selected = picked)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassTextField(newName, { newName = it.take(30) }, placeholder = "Add a name", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Chip("Add", ::addTyped, icon = Lucide.Plus, selected = newName.isNotBlank())
            }
            if (people.isNotEmpty() && total > 0) {
                SegmentedToggle(
                    options = listOf(true, false),
                    selected = viewModel.equalSplit,
                    label = { if (it) "Equally" else "By amount" },
                    onSelect = { equal ->
                        // Typing amounts starts from the equal shares.
                        if (!equal && viewModel.equalSplit) people.forEach { viewModel.customShares[it] = AmountInput.fromMinor(viewModel.personShare) }
                        viewModel.equalSplit = equal
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                val yours = total - viewModel.othersOwe
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        com.leftovers.app.ui.components.ListRow(
                            "You",
                            subtitle = if (viewModel.equalSplit) "Your expense" else "Keeps the rest",
                            trailing = { Text(money.format(yours), style = MaterialTheme.typography.titleSmall, color = if (yours > 0) c.textPrimary else c.negative) },
                        )
                        people.forEach { name ->
                            com.leftovers.app.ui.components.RowDivider()
                            com.leftovers.app.ui.components.ListRow(
                                name,
                                subtitle = "Owes you",
                                trailing = {
                                    if (viewModel.equalSplit) {
                                        Text(money.format(viewModel.personShare), style = MaterialTheme.typography.titleSmall, color = c.positive)
                                    } else {
                                        MoneyField(viewModel.customShares[name].orEmpty(), { viewModel.customShares[name] = it }, Modifier.width(132.dp))
                                    }
                                },
                            )
                        }
                    }
                }
                if (yours <= 0) {
                    Text("That's the whole amount or more. Leave something for your own share.", style = MaterialTheme.typography.bodySmall, color = c.negative)
                }
            } else if (total <= 0) {
                Text("Type the full amount first, then choose who's sharing it.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (people.isNotEmpty()) {
                    SecondaryButton("Don't split", {
                        viewModel.clearPeople()
                        onDismiss()
                    }, Modifier.weight(1f))
                }
                PrimaryButton("Done", onDismiss, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Travel mode: type the amount in another currency, at a rate you set ("1 AED = ₹22.70"). It's saved in your
 * own currency, with the original amount kept alongside. Nothing is looked up online.
 */
@Composable
private fun ForeignSheet(
    homeCode: String,
    current: String?,
    currentRate: String,
    lastRate: suspend (String) -> String,
    onDone: (code: String?, rate: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    val home = LocalMoney.current
    var code by rememberSaveable { mutableStateOf(current) }
    var rate by rememberSaveable { mutableStateOf(currentRate) }
    var picking by rememberSaveable { mutableStateOf(current == null) }
    val scope = rememberCoroutineScope()
    val valid = rate.toBigDecimalOrNull()?.let { it.signum() > 0 } == true

    GlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Spent in another currency?", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            val chosen = code
            if (picking || chosen == null) {
                Text(
                    "Pick the currency you paid in. You'll type the amount in it, and it's saved in $homeCode at your rate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                CurrencyList(chosen ?: "", { option ->
                    if (option.code == homeCode) {
                        onDone(null, "")
                    } else {
                        code = option.code
                        picking = false
                        scope.launch { rate = lastRate(option.code) }
                    }
                }, Modifier.heightIn(max = 420.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1 $chosen =", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.width(10.dp))
                    GlassTextField(
                        rate,
                        { raw -> if (raw.length <= 12 && raw.all { it.isDigit() || it == '.' } && raw.count { it == '.' } <= 1) rate = raw },
                        placeholder = "0",
                        prefix = home.symbol,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Use the rate your card or exchange gave you. It's remembered for next time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                PrimaryButton("Use $chosen", { onDone(chosen, rate.trim()) }, Modifier.fillMaxWidth(), enabled = valid)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Other currency", { picking = true }, Modifier.weight(1f))
                    SecondaryButton("Back to $homeCode", { onDone(null, "") }, Modifier.weight(1f))
                }
            }
        }
    }
}
