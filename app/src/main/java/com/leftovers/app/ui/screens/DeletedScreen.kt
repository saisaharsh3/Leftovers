package com.leftovers.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.DeletedFilter
import com.leftovers.app.data.DeletedItem
import com.leftovers.app.data.SettingsRepository
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.EmptyState
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class DeletedUiState(
    val loaded: Boolean = false,
    val items: List<DeletedItem> = emptyList(),
    val total: Int = 0,
    val filter: DeletedFilter = DeletedFilter.All,
    val keepDays: Int = 90,
)

class DeletedViewModel(private val repository: TransactionRepository, private val settings: SettingsRepository) : ViewModel() {
    private val filter = MutableStateFlow<DeletedFilter>(DeletedFilter.All)

    val state: StateFlow<DeletedUiState> = combine(repository.deleted, filter, settings.settings) { all, f, s ->
        val today = LocalDate.now()
        DeletedUiState(true, all.filter { f.matches(it.deletedOn(), today) }, all.size, f, s.deletedKeepDays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeletedUiState())

    fun setFilter(value: DeletedFilter) { filter.value = value }
    fun restore(ids: List<Long>) = viewModelScope.launch { repository.restoreDeleted(ids) }
    fun deleteForever(ids: List<Long>) = viewModelScope.launch { repository.deleteForever(ids) }
    fun setKeepDays(days: Int) = viewModelScope.launch {
        settings.setDeletedKeepDays(days)
        repository.purgeDeleted(days)
    }
}

private val keepOptions = listOf(30, 90, 365, 0)
private fun keepLabel(days: Int) = when (days) {
    0 -> "Forever"
    365 -> "1 year"
    else -> "$days days"
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM")

/** Everything the user deleted, to restore or remove for good, filtered by when it was deleted. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeletedScreen(onBack: () -> Unit, viewModel: DeletedViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val money = LocalMoney.current
    var pickDates by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDeleteOne by remember { mutableStateOf<DeletedItem?>(null) }
    val shownIds = state.items.map { it.entry.id }

    GlassScreen(
        title = "Deleted entries",
        subtitle = "Restore anything you deleted",
        onBack = onBack,
        bottomBar = {
            if (state.items.isNotEmpty()) {
                Row(
                    Modifier.frosted().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton("Restore ${if (state.filter == DeletedFilter.All) "all" else "these"}", { viewModel.restore(shownIds) }, Modifier.weight(1f), icon = Lucide.RotateCcw)
                    SecondaryButton("Delete ${if (state.filter == DeletedFilter.All) "all" else "these"}", { confirmDeleteAll = true }, Modifier.weight(1f), icon = Lucide.Trash2)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "filters") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("All", { viewModel.setFilter(DeletedFilter.All) }, selected = state.filter == DeletedFilter.All)
                    Chip("Today", { viewModel.setFilter(DeletedFilter.Today) }, selected = state.filter == DeletedFilter.Today)
                    Chip("Last 7 days", { viewModel.setFilter(DeletedFilter.Week) }, selected = state.filter == DeletedFilter.Week)
                    Chip("Last 30 days", { viewModel.setFilter(DeletedFilter.Month) }, selected = state.filter == DeletedFilter.Month)
                    val range = state.filter as? DeletedFilter.Range
                    Chip(
                        range?.let { "${it.from.format(shortDate)} – ${it.to.format(shortDate)}" } ?: "Pick dates",
                        { pickDates = true },
                        icon = Lucide.Calendar,
                        selected = range != null,
                    )
                }
            }

            if (state.loaded && state.items.isEmpty()) {
                item(key = "empty") {
                    Glass(Modifier.fillMaxWidth()) {
                        if (state.total == 0) {
                            EmptyState(Lucide.Trash2, "Nothing deleted", "Entries you delete wait here, so you can bring them back.")
                        } else {
                            EmptyState(Lucide.Calendar, "Nothing deleted then", "Try another date range.")
                        }
                    }
                }
            } else if (state.items.isNotEmpty()) {
                item(key = "summary") {
                    val spent = state.items.filter { it.entry.type == TxType.EXPENSE }.sumOf { it.entry.amountMinor }
                    Text(
                        "${state.items.size} ${if (state.items.size == 1) "entry" else "entries"}" + if (spent > 0) " · ${money.format(spent)} spent" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textSecondary,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                }
                state.items.groupBy { it.deletedOn() }.forEach { (day, items) ->
                    item(key = "day-$day") {
                        Text(
                            "Deleted ${day.friendlyLabel().replaceFirstChar { it.lowercase() }}",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary,
                            modifier = Modifier.animateItem().padding(start = 6.dp, top = 12.dp),
                        )
                    }
                    item(key = "group-$day") {
                        Glass(Modifier.fillMaxWidth().animateItem()) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                items.forEachIndexed { i, item ->
                                    if (i > 0) RowDivider()
                                    DeletedRow(item, onRestore = { viewModel.restore(listOf(item.entry.id)) }, onDelete = { confirmDeleteOne = item })
                                }
                            }
                        }
                    }
                }
            }

            item(key = "keep") {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Keep deleted entries for", style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
                    SegmentedToggle(keepOptions, state.keepDays, ::keepLabel, { viewModel.setKeepDays(it) }, Modifier.fillMaxWidth())
                    Text(
                        if (state.keepDays == 0) "They stay until you delete them here." else "After that they're removed for good, with any receipt photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }

    if (pickDates) {
        val picker = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedStartDateMillis != null,
                    onClick = {
                        val from = picker.selectedStartDateMillis!!.toUtcDate()
                        val to = picker.selectedEndDateMillis?.toUtcDate() ?: from
                        viewModel.setFilter(DeletedFilter.Range(from, to))
                        pickDates = false
                    },
                ) { Text("Show", color = c.textPrimary) }
            },
            dismissButton = { TextButton(onClick = { pickDates = false }) { Text("Cancel", color = c.textSecondary) } },
        ) {
            DateRangePicker(picker, title = { Text("Deleted between", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }, modifier = Modifier.height(480.dp))
        }
    }

    if (confirmDeleteAll) {
        DeleteForeverDialog(
            title = "Delete ${state.items.size} ${if (state.items.size == 1) "entry" else "entries"} for good?",
            onConfirm = {
                viewModel.deleteForever(shownIds)
                confirmDeleteAll = false
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
    confirmDeleteOne?.let { item ->
        DeleteForeverDialog(
            title = "Delete for good?",
            onConfirm = {
                viewModel.deleteForever(listOf(item.entry.id))
                confirmDeleteOne = null
            },
            onDismiss = { confirmDeleteOne = null },
        )
    }
}

@Composable
private fun DeletedRow(item: DeletedItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val e = item.entry
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(item.categoryEmoji ?: "package", item.categoryColor ?: 0xFF8A8A99, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.note.ifBlank { item.categoryName ?: "Entry" },
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(item.date.friendlyLabel(), item.categoryName?.takeIf { e.note.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
            )
        }
        Text(
            (if (e.type == TxType.INCOME) "+" else "−") + money.format(e.amountMinor),
            style = MaterialTheme.typography.titleSmall,
            color = if (e.type == TxType.INCOME) c.positive else c.textPrimary,
        )
        Spacer(Modifier.width(8.dp))
        RoundButton(Lucide.RotateCcw, "Restore", onRestore, size = 36.dp, tint = c.accent)
        Spacer(Modifier.width(6.dp))
        RoundButton(Lucide.Trash2, "Delete for good", onDelete, size = 36.dp, tint = c.negative)
    }
}

@Composable
private fun DeleteForeverDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(30.dp),
        title = { Text(title) },
        text = { Text("They can't be restored after this.", color = c.textSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = c.negative) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } },
    )
}

/** Date pickers report midnight UTC for the chosen day. */
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
