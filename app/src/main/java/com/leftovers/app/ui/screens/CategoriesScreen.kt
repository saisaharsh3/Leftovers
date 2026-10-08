package com.leftovers.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.Category
import com.leftovers.app.data.Palette
import com.leftovers.app.data.TransactionRepository
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.CategoryIcon
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.ListRow
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.CategoryIcons
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repository: TransactionRepository) : ViewModel() {
    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(category: Category) {
        viewModelScope.launch { repository.saveCategory(category) }
    }

    /** Calls [onBlocked] with the usage count if the category is still in use. */
    fun delete(category: Category, onBlocked: (Int) -> Unit) {
        viewModelScope.launch {
            val used = repository.deleteCategory(category)
            if (used > 0) onBlocked(used)
        }
    }
}

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    var tab by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var confirmDelete by remember { mutableStateOf<Category?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    GlassScreen(
        title = "Categories",
        onBack = onBack,
        snackbar = snackbar,
        bottomBar = {
            Box(Modifier.frosted().navigationBarsPadding().padding(16.dp)) {
                PrimaryButton(
                    "New category",
                    { editing = Category(name = "", emoji = CategoryIcons.keys.first(), color = Palette.all.first(), type = tab, addsToBudget = tab == TxType.INCOME) },
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Plus,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SegmentedToggle(TxType.entries, tab, { if (it == TxType.EXPENSE) "Spending" else "Income" }, { tab = it }, Modifier.fillMaxWidth())
            }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        categories.filter { it.type == tab }.forEachIndexed { i, cat ->
                            if (i > 0) RowDivider()
                            ListRow(
                                title = cat.name,
                                leading = { CategoryIcon(cat.emoji, cat.color, size = 42.dp) },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (cat.type == TxType.INCOME) {
                                            Switch(
                                                checked = cat.addsToBudget,
                                                onCheckedChange = { viewModel.save(cat.copy(addsToBudget = it)) },
                                                colors = SwitchDefaults.colors(
                                                    checkedTrackColor = c.accent,
                                                    checkedThumbColor = c.onAccent,
                                                    uncheckedTrackColor = c.glass,
                                                    uncheckedBorderColor = c.borderTop,
                                                    uncheckedThumbColor = c.textSecondary,
                                                ),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        RoundButton(Lucide.Trash2, "Delete ${cat.name}", { confirmDelete = cat }, size = 36.dp, tint = c.textTertiary)
                                    }
                                },
                                onClick = { editing = cat },
                            )
                        }
                    }
                }
                if (tab == TxType.INCOME) {
                    Text(
                        "Switched on: money you receive in that category is added to the month's budget, like a gift or bonus. Leave Salary off if your budget already comes from it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    )
                }
            }
        }
    }

    editing?.let { category ->
        CategoryEditor(
            initial = category,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
        )
    }

    confirmDelete?.let { category ->
        GlassAlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Delete ${category.name}?") },
            text = { Text("Only categories without any entries or subscriptions can be deleted.", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.delete(category) { used ->
                        scope.launch { snackbar.showSnackbar("${category.name} is used by $used ${if (used == 1) "entry" else "entries"}") }
                    }
                }) { Text("Delete", color = c.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditor(initial: Category, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    val c = LocalAppColors.current
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var icon by rememberSaveable { mutableStateOf(initial.emoji) }
    var color by rememberSaveable { mutableStateOf(initial.color) }

    GlassSheet(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(icon, color, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Text(if (initial.id == 0L) "New category" else "Edit category", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            }
            GlassTextField(name, { name = it.take(24) }, placeholder = "e.g. Coffee", label = "Name")

            Text("Colour", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.all.forEach { col ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .pressable({ color = col }, pressedScale = 0.85f)
                            .clip(CircleShape)
                            .background(Color(col))
                            .then(if (col == color) Modifier.border(2.dp, c.textPrimary, CircleShape) else Modifier),
                    )
                }
            }

            Text("Icon", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryIcons.keys.forEach { key ->
                    val selected = key == icon
                    val bg by animateColorAsState(if (selected) Color(color).copy(alpha = 0.22f) else c.glass, label = "iconBg")
                    Box(
                        Modifier
                            .size(46.dp)
                            .pressable({ icon = key }, pressedScale = 0.9f)
                            .clip(RoundedCornerShape(15.dp))
                            .background(bg)
                            .border(1.dp, if (selected) Color(color) else c.borderBottom, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(CategoryIcons[key], contentDescription = key, tint = if (selected) Color(color) else c.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }

            PrimaryButton(
                "Save",
                { onSave(initial.copy(name = name.trim(), emoji = icon, color = color)) },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            )
        }
    }
}
