package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.data.SmsSuggestion
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.LocalUndo
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors

/**
 * Every detected payment waiting, for when there are more than Home lists: the same rows, and Add all to add the
 * plain ones in one go with their usual categories. Paybacks and subscriptions' payments are left to check one by one.
 */
@Composable
fun DetectedPaymentsScreen(
    onBack: () -> Unit,
    onReviewSms: (SmsSuggestion) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val undo = LocalUndo.current
    val all = state.smsSuggestions
    val plain = all.filter { plainDetected(it, state) }
    // All sorted: back to Home.
    LaunchedEffect(state.loaded, all.isEmpty()) { if (state.loaded && all.isEmpty()) onBack() }
    GlassScreen(
        title = "Detected payments",
        subtitle = if (all.isEmpty()) null else "${all.size} waiting",
        onBack = onBack,
        bottomBar = {
            if (plain.size > 1) {
                Column(Modifier.frosted(fadeTop = true).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 16.dp)) {
                    PrimaryButton(
                        "Add all ${plain.size}",
                        { viewModel.addAllDetected(plain) { n, restore -> undo.show("Added $n payments", restore) } },
                        Modifier.fillMaxWidth(),
                        icon = Lucide.Check,
                    )
                    Text(
                        "Each with the category it had before, or Other for somewhere new." + if (plain.size < all.size) " Paybacks and subscriptions stay to check." else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { DetectedRows(all, state, viewModel, onReviewSms) }
        }
    }
}
