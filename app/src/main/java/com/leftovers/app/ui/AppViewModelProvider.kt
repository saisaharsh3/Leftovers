package com.leftovers.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leftovers.app.LeftoversApp
import com.leftovers.app.ui.screens.AccountsViewModel
import com.leftovers.app.ui.screens.AssistantSetupViewModel
import com.leftovers.app.ui.screens.AssistantViewModel
import com.leftovers.app.ui.screens.BudgetPlanViewModel
import com.leftovers.app.ui.screens.BudgetsViewModel
import com.leftovers.app.ui.screens.CategoriesViewModel
import com.leftovers.app.ui.screens.DebtsViewModel
import com.leftovers.app.ui.screens.EditorViewModel
import com.leftovers.app.ui.screens.EmailSetupViewModel
import com.leftovers.app.ui.screens.GoalDetailViewModel
import com.leftovers.app.ui.screens.GoalsViewModel
import com.leftovers.app.ui.screens.HistoryViewModel
import com.leftovers.app.ui.screens.HomeViewModel
import com.leftovers.app.ui.screens.PlanViewModel
import com.leftovers.app.ui.screens.RecapViewModel
import com.leftovers.app.ui.screens.SettingsViewModel
import com.leftovers.app.ui.screens.StatsViewModel
import com.leftovers.app.ui.screens.SubscriptionsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(container().repository, container().settings, container().planning, container().sms, container().accounts) }
        initializer {
            val c = container()
            EditorViewModel(createSavedStateHandle(), app(), c.repository, c.accounts, c.settings, c.sms, c.planning, c.budgetAlerts)
        }
        initializer { AccountsViewModel(container().accounts, container().settings, container().repository) }
        initializer { RecapViewModel(createSavedStateHandle(), container().repository, container().planning, container().settings) }
        initializer { HistoryViewModel(container().repository, container().accounts) }
        initializer { StatsViewModel(container().repository, container().settings) }
        initializer { PlanViewModel(container().repository, container().settings, container().planning) }
        initializer { BudgetsViewModel(container().repository, container().settings, container().planning) }
        initializer { BudgetPlanViewModel(container().settings, container().repository) }
        initializer {
            val c = container()
            SubscriptionsViewModel(c.planning, c.repository, c.settings) { c.syncRecurring() }
        }
        initializer { GoalsViewModel(container().planning) }
        initializer { DebtsViewModel(container().planning) }
        initializer { GoalDetailViewModel(createSavedStateHandle(), container().planning) }
        initializer { CategoriesViewModel(container().repository) }
        initializer { SettingsViewModel(container().repository, container().settings, container().backup, container().assistant) }
        initializer { AssistantViewModel(container(), container().assistant) }
        initializer { AssistantSetupViewModel(container().assistant) }
        initializer { EmailSetupViewModel(app()) }
    }
}

private fun CreationExtras.app() = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LeftoversApp

private fun CreationExtras.container() = app().container
