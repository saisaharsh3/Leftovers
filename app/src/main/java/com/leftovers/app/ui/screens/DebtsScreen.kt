package com.leftovers.app.ui.screens

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
import com.leftovers.app.data.PersonBalance
import com.leftovers.app.data.PlanningRepository
import com.leftovers.app.data.openBalances
import com.leftovers.app.ui.AppViewModelProvider
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

class DebtsViewModel(private val planning: PlanningRepository) : ViewModel() {
    val balances: StateFlow<List<PersonBalance>> =
        planning.debts.map { it.openBalances() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everyone ever recorded, for quick picking. */
    val people: StateFlow<List<String>> =
        planning.debts.map { all -> all.map { it.person.trim() }.distinctBy { it.lowercase() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(debt: Debt) = viewModelScope.launch { planning.saveDebt(debt) }
    fun delete(debt: Debt) = viewModelScope.launch { planning.deleteDebt(debt) }
    fun settle(person: String) = viewModelScope.launch { planning.settle(person) }
}

/** Money lent to and borrowed from people. It never counts as spending or income. */
@Composable
fun DebtsScreen(onBack: () -> Unit, viewModel: DebtsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var editing by remember { mutableStateOf<Debt?>(null) }
    val owedToYou = balances.filter { it.netMinor > 0 }.sumOf { it.netMinor }
    val youOwe = balances.filter { it.netMinor < 0 }.sumOf { -it.netMinor }

    GlassScreen(
        title = "Money owed",
        subtitle = "Lent and borrowed · kept out of your budget",
        onBack = onBack,
        bottomBar = {
            Box(Modifier.frosted().navigationBarsPadding().padding(16.dp)) {
                PrimaryButton("Add", { editing = Debt(person = "", amountMinor = 0) }, Modifier.fillMaxWidth(), icon = Lucide.Plus)
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                Glass(Modifier.fillMaxWidth().appear(1), shape = RoundedCornerShape(26.dp)) {
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
                            Chip("Settle up", { viewModel.settle(b.person) }, icon = Lucide.Check)
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
                                if (d.receiptPath != null) {
                                    Icon(Lucide.Camera, contentDescription = "Has a photo", tint = c.textTertiary, modifier = Modifier.padding(end = 8.dp).size(14.dp))
                                }
                                Text(
                                    (if (d.amountMinor > 0) "Lent " else "Borrowed ") + money.format(abs(d.amountMinor)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { debt ->
        DebtEditor(
            initial = debt,
            people = people,
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
private fun DebtEditor(initial: Debt, people: List<String>, onDismiss: () -> Unit, onSave: (Debt) -> Unit, onDelete: (() -> Unit)?) {
    val c = LocalAppColors.current
    var lent by rememberSaveable { mutableStateOf(initial.amountMinor >= 0) }
    var person by rememberSaveable { mutableStateOf(initial.person) }
    var amountText by rememberSaveable { mutableStateOf(if (initial.amountMinor != 0L) AmountInput.fromMinor(abs(initial.amountMinor)) else "") }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var photo by rememberSaveable { mutableStateOf(initial.receiptPath) }
    var viewPhoto by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val amount = AmountInput.toMinor(amountText) ?: 0L
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun attach(uri: Uri, cleanup: () -> Unit = {}) {
        scope.launch {
            val path = ReceiptStore.import(context, uri)
            cleanup()
            if (path != null) {
                if (photo != initial.receiptPath) ReceiptStore.delete(photo)
                photo = path
            }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) attach(uri) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) attach(Uri.fromFile(file)) { file.delete() } else file?.delete()
    }
    // A photo added but not saved is thrown away.
    val close = {
        if (photo != initial.receiptPath) ReceiptStore.delete(photo)
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
            GlassTextField(note, { note = it.take(60) }, placeholder = "e.g. Dinner at Toit", label = "Note")
            // A photo of the bill, a chat screenshot or a payment receipt.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val current = photo
                if (current != null) {
                    ReceiptThumb(current, onOpen = { viewPhoto = true }, onRemove = {
                        if (current != initial.receiptPath) ReceiptStore.delete(current)
                        photo = null
                    })
                    Text("Photo attached", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                } else {
                    Chip("Take a photo", {
                        val (uri, file) = ReceiptStore.newCameraUri(context)
                        cameraFile = file
                        takePhoto.launch(uri)
                    }, icon = Lucide.Camera)
                    Chip("Add a screenshot", { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Lucide.ReceiptText)
                }
            }
            Spacer(Modifier.height(2.dp))
            PrimaryButton(
                "Save",
                {
                    // A replaced photo is removed once the new one is saved.
                    if (initial.receiptPath != null && initial.receiptPath != photo) ReceiptStore.delete(initial.receiptPath)
                    onSave(initial.copy(person = person.trim(), amountMinor = if (lent) amount else -amount, note = note.trim(), receiptPath = photo))
                },
                Modifier.fillMaxWidth(),
                enabled = person.isNotBlank() && amount > 0,
            )
            if (viewPhoto) photo?.let { ReceiptViewer(it) { viewPhoto = false } }
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
