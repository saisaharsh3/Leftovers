package com.leftovers.app.ui.screens

import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.leftovers.app.util.ReceiptStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.Debt
import com.leftovers.app.data.MAX_DEBT_PHOTOS
import com.leftovers.app.data.photosToPath
import com.leftovers.app.data.PersonBalance
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.openBalances
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.sharedContainer
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.appear
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AmountInput
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class DebtsViewModel(
    private val planning: PlanningRepository,
    accountRepository: com.leftovers.app.data.AccountRepository,
    settings: com.leftovers.app.data.SettingsRepository,
) : ViewModel() {
    val accounts: StateFlow<List<com.leftovers.app.data.AccountWithBalance>> =
        accountRepository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val defaultAccountId: StateFlow<Long?> =
        settings.settings.map<com.leftovers.app.data.AppSettings, Long?> { it.defaultAccountId }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until loaded, so the screen doesn't flash "All square" first. */
    val balances: StateFlow<List<PersonBalance>?> =
        planning.debts.map { it.openBalances() }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Everyone ever recorded, for quick picking. */
    val people: StateFlow<List<String>> =
        planning.debts.map { all -> all.map { it.person.trim() }.distinctBy { it.lowercase() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(debt: Debt) = viewModelScope.launch { planning.saveDebt(debt) }
    fun delete(debt: Debt) = viewModelScope.launch { planning.deleteDebt(debt) }
    fun settle(person: String) = viewModelScope.launch { planning.settle(person) }
    fun settleUp(person: String, amountMinor: Long, accountId: Long?) = viewModelScope.launch { planning.settleUp(person, amountMinor, accountId, chosen = true) }
}

/** Money lent to and borrowed from people. It never counts as spending or income. */
@Composable
fun DebtsScreen(onBack: () -> Unit, viewModel: DebtsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val loadedBalances by viewModel.balances.collectAsStateWithLifecycle()
    val balances = loadedBalances.orEmpty()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val defaultAccountId by viewModel.defaultAccountId.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var editing by remember { mutableStateOf<Debt?>(null) }
    var settling by remember { mutableStateOf<String?>(null) }
    val owedToYou = balances.filter { it.netMinor > 0 }.sumOf { it.netMinor }
    val youOwe = balances.filter { it.netMinor < 0 }.sumOf { -it.netMinor }

    GlassScreen(
        title = "Money owed",
        subtitle = "Lent and borrowed · kept out of your budget",
        onBack = onBack,
        bottomBar = {
            Box(Modifier.frosted(fadeTop = true).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 16.dp)) {
                PrimaryButton("Add", { editing = Debt(person = "", amountMinor = 0) }, Modifier.fillMaxWidth(), icon = Lucide.Plus)
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loadedBalances == null) return@LazyColumn
            if (balances.isEmpty()) {
                item {
                    Glass(Modifier.fillMaxWidth().appear(0)) {
                        EmptyState(
                            Lucide.HandCoins,
                            "All square",
                            "Note down money you lend or borrow, like splitting a dinner. Settle up when it's paid back.",
                        )
                    }
                }
                return@LazyColumn
            }
            item {
                Glass(Modifier.fillMaxWidth().appear(0), strong = true, shape = RoundedCornerShape(32.dp)) {
                    Row(Modifier.padding(22.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("You're owed", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                            Text(money.format(owedToYou), style = MaterialTheme.typography.headlineSmall, color = c.positive)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("You owe", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                            Text(money.format(youOwe), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                        }
                    }
                }
            }
            items(balances, key = { it.person.lowercase() }) { b ->
                Glass(Modifier.sharedContainer("owed-${b.person.lowercase()}").fillMaxWidth().appear(1), shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.person, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                                Text(
                                    when {
                                        b.netMinor > 0 -> "Owes you ${money.format(b.netMinor)}"
                                        b.netMinor < 0 -> "You owe ${money.format(-b.netMinor)}"
                                        else -> "Even"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (b.netMinor > 0) c.positive else c.textSecondary,
                                )
                            }
                            Chip("Pay back", { settling = b.person }, icon = Lucide.Check)
                        }
                        b.entries.forEach { d ->
                            RowDivider(inset = 16.dp)
                            Row(
                                Modifier.fillMaxWidth().pressable({ editing = d }, pressedScale = 0.985f).padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    listOfNotNull(d.note.ifBlank { null }, LocalDate.ofEpochDay(d.epochDay).friendlyLabel()).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (d.photos.isNotEmpty()) {
                                    Icon(Lucide.Camera, contentDescription = if (d.photos.size == 1) "Has a photo" else "Has ${d.photos.size} photos", tint = c.textTertiary, modifier = Modifier.padding(end = 8.dp).size(14.dp))
                                }
                                Text(
                                    (if (d.repayment) "Paid back " else if (d.amountMinor > 0) "Lent " else "Borrowed ") + money.format(abs(d.amountMinor)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textPrimary,
                                )
                                // Like other rows that open something, it ends in a chevron.
                                Icon(Lucide.ChevronRight, contentDescription = "Edit", tint = c.textTertiary, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    balances.firstOrNull { it.person.equals(settling, ignoreCase = true) }?.let { b ->
        SettleSheet(b, accounts, onDismiss = { settling = null }) { amount, account ->
            viewModel.settleUp(b.person, amount, account)
            settling = null
        }
    }

    editing?.let { debt ->
        DebtEditor(
            initial = debt,
            people = people,
            accounts = accounts,
            defaultAccountId = defaultAccountId,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = if (debt.id != 0L) {
                {
                    viewModel.delete(debt)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebtEditor(
    initial: Debt,
    people: List<String>,
    onDismiss: () -> Unit,
    onSave: (Debt) -> Unit,
    onDelete: (() -> Unit)?,
    accounts: List<com.leftovers.app.data.AccountWithBalance> = emptyList(),
    defaultAccountId: Long? = null,
) {
    val c = LocalAppColors.current
    // New entries start on the default account; older ones made before this keep no account until one is picked.
    val known = accounts.map { it.id }
    var accountId by rememberSaveable {
        mutableStateOf(
            if (initial.id == 0L) initial.accountId ?: defaultAccountId?.takeIf { it in known } ?: known.firstOrNull()
            else initial.accountId,
        )
    }
    var lent by rememberSaveable { mutableStateOf(initial.amountMinor >= 0) }
    var person by rememberSaveable { mutableStateOf(initial.person) }
    var amountText by rememberSaveable { mutableStateOf(if (initial.amountMinor != 0L) AmountInput.fromMinor(abs(initial.amountMinor)) else "") }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    // Paths, one per line, so the list survives rotation like the other fields.
    var photoText by rememberSaveable { mutableStateOf(initial.receiptPath.orEmpty()) }
    val photos = photoText.split('\n').filter { it.isNotBlank() }
    val original = initial.photos
    var viewing by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val amount = AmountInput.toMinor(amountText) ?: 0L
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun attach(uris: List<Uri>, cleanup: () -> Unit = {}) {
        scope.launch {
            val added = uris.mapNotNull { ReceiptStore.import(context, it) }
            cleanup()
            val all = photoText.split('\n').filter { it.isNotBlank() } + added
            all.drop(MAX_DEBT_PHOTOS).forEach { if (it !in original) ReceiptStore.delete(it) }
            photoText = all.take(MAX_DEBT_PHOTOS).joinToString("\n")
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_DEBT_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) attach(uris)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) attach(listOf(Uri.fromFile(file))) { file.delete() } else file?.delete()
    }
    // Photos added but not saved are thrown away.
    val close = {
        photos.filter { it !in original }.forEach { ReceiptStore.delete(it) }
        onDismiss()
    }

    GlassSheet(close) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial.id == 0L) "Lent or borrowed" else initial.person, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            SegmentedToggle(listOf(true, false), lent, { if (it) "I lent" else "I borrowed" }, { lent = it }, Modifier.fillMaxWidth())
            GlassTextField(person, { person = it.take(30) }, placeholder = "e.g. Nick", label = if (lent) "To" else "From")
            val matches = people.filter { it.contains(person.trim(), ignoreCase = true) && !it.equals(person.trim(), ignoreCase = true) }.take(6)
            if (matches.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matches.forEach { Chip(it, { person = it }) }
                }
            }
            MoneyField(amountText, { amountText = it }, label = "Amount")
            // Lending takes the money out of this account until it's paid back; borrowing puts it in.
            if (accounts.size > 1 || (accounts.isNotEmpty() && accountId == null)) {
                Text(
                    if (lent) "Paid from" else "Received into",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a ->
                        Chip(a.name, { accountId = a.id }, icon = com.leftovers.app.ui.icons.CategoryIcons[a.icon], iconTint = androidx.compose.ui.graphics.Color(a.color), selected = accountId == a.id)
                    }
                    if (initial.accountId == null && initial.id != 0L) Chip("No account", { accountId = null }, selected = accountId == null)
                }
            }
            GlassTextField(note, { note = it.take(60) }, placeholder = "e.g. Dinner at Toit", label = "Note")
            // Photos of the bill, chat screenshots or payment receipts. Everything wraps on narrow phones.
            if (photos.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    photos.forEach { p ->
                        ReceiptThumb(p, onOpen = { viewing = p }, size = 64.dp)
                    }
                }
            }
            if (photos.size < MAX_DEBT_PHOTOS) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Photo", {
                        val (uri, file) = ReceiptStore.newCameraUri(context)
                        cameraFile = file
                        takePhoto.launch(uri)
                    }, icon = Lucide.Camera)
                    Chip(if (photos.isEmpty()) "Screenshots" else "Add more", {
                        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, icon = Lucide.ReceiptText)
                }
            }
            Spacer(Modifier.height(2.dp))
            PrimaryButton(
                "Save",
                {
                    // Photos removed while editing are deleted once the change is saved.
                    original.filter { it !in photos }.forEach { ReceiptStore.delete(it) }
                    onSave(initial.copy(person = person.trim(), amountMinor = if (lent) amount else -amount, note = note.trim(), receiptPath = photosToPath(photos), accountId = accountId))
                },
                Modifier.fillMaxWidth(),
                enabled = person.isNotBlank() && amount > 0,
            )
            viewing?.let { p ->
                ReceiptViewer(p, onDismiss = { viewing = null }, onRemove = {
                    if (p !in original) ReceiptStore.delete(p)
                    photoText = photos.filter { it != p }.joinToString("\n")
                })
            }
            if (onDelete != null) {
                Text(
                    "Delete",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.negative,
                    modifier = Modifier.align(Alignment.CenterHorizontally).pressable(onDelete),
                )
            }
        }
    }
}

/**
 * Paying back, all of it or part (#20): the amount starts at everything owed, and the account it went into
 * or came out of starts at the one their latest entry used. Entries from before accounts were tracked have
 * none, so their repayment starts with none too and balances stay as they were.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettleSheet(
    balance: com.leftovers.app.data.PersonBalance,
    accounts: List<com.leftovers.app.data.AccountWithBalance>,
    onDismiss: () -> Unit,
    onSettle: (amountMinor: Long, accountId: Long?) -> Unit,
) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val owed = abs(balance.netMinor)
    val theyPay = balance.netMinor > 0
    var amountText by rememberSaveable { mutableStateOf(AmountInput.fromMinor(owed)) }
    val noAccountYet = balance.entries.all { it.accountId == null }
    var accountId by rememberSaveable { mutableStateOf(balance.entries.firstNotNullOfOrNull { it.accountId }) }
    val amount = AmountInput.toMinor(amountText) ?: 0L
    val all = amount >= owed
    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (theyPay) "${balance.person} paid you back" else "You paid ${balance.person} back", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                if (theyPay) "They owe you ${money.format(owed)}. Change the amount if they paid part of it." else "You owe ${money.format(owed)}. Change the amount if you paid part of it.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            MoneyField(amountText, { amountText = it }, label = "Amount")
            if (accounts.size > 1 || noAccountYet) {
                Text(
                    if (theyPay) "Received into" else "Paid from",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a ->
                        Chip(a.name, { accountId = a.id }, icon = com.leftovers.app.ui.icons.CategoryIcons[a.icon], iconTint = androidx.compose.ui.graphics.Color(a.color), selected = accountId == a.id)
                    }
                    if (noAccountYet) Chip("No account", { accountId = null }, selected = accountId == null)
                }
            }
            Spacer(Modifier.height(2.dp))
            PrimaryButton(
                if (all) "Settle up" else "Save ${money.format(amount)} paid back",
                { onSettle(minOf(amount, owed), accountId) },
                Modifier.fillMaxWidth(),
                enabled = amount > 0,
                icon = Lucide.Check,
            )
            Text(
                if (all) "Everything with ${balance.person} is then settled." else "${money.format(owed - amount)} stays open.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
