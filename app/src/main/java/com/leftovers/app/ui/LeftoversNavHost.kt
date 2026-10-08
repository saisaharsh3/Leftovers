package com.leftovers.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.leftovers.app.ui.components.LocalUndo
import com.leftovers.app.ui.components.UndoBar
import com.leftovers.app.ui.components.UndoRequest
import com.leftovers.app.ui.components.UndoState
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.LocalNavAnimatedScope
import com.leftovers.app.ui.components.LocalSharedTransitionScope
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.components.glassBorder
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.screens.AccountsScreen
import com.leftovers.app.ui.screens.AssistantScreen
import com.leftovers.app.ui.screens.BudgetPlanScreen
import com.leftovers.app.ui.screens.BudgetsScreen
import com.leftovers.app.ui.screens.CategoriesScreen
import com.leftovers.app.ui.screens.ActivityRequest
import com.leftovers.app.ui.screens.DebtsScreen
import com.leftovers.app.ui.screens.TypeFilter
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.screens.DeletedScreen
import com.leftovers.app.ui.screens.EditorScreen
import com.leftovers.app.ui.screens.GoalDetailScreen
import com.leftovers.app.ui.screens.GoalsScreen
import com.leftovers.app.ui.screens.HistoryScreen
import com.leftovers.app.ui.screens.HomeScreen
import com.leftovers.app.ui.screens.PlanScreen
import com.leftovers.app.ui.screens.RecapScreen
import com.leftovers.app.ui.screens.SettingsScreen
import com.leftovers.app.ui.screens.StatsScreen
import com.leftovers.app.ui.screens.SubscriptionsScreen
import com.leftovers.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private object Routes {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val INSIGHTS = "insights"
    const val PLAN = "plan"
    const val DEBTS = "debts"
    const val DELETED = "deleted"
    const val ADD = "add?day={day}&amount={amount}&note={note}&sms={sms}"
    const val EDIT = "edit/{id}"
    const val BUDGET = "budget"
    const val BUDGET_PLAN = "budget/plan"
    const val SUBSCRIPTIONS = "subscriptions?income={income}"
    fun subscriptions(income: Boolean = false) = "subscriptions?income=$income"
    const val GOALS = "goals"
    const val GOAL = "goals/{id}"
    const val ACCOUNTS = "accounts"
    const val RECAP = "recap/{month}"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val ASSISTANT = "assistant"

    fun add(day: Long = -1L, amount: Long = -1L, note: String = "", sms: Long = -1L) =
        "add?day=$day&amount=$amount&note=${Uri.encode(note)}&sms=$sms"
    fun edit(id: Long) = "edit/$id"
    fun goal(id: Long) = "goals/$id"
    fun recap(month: String) = "recap/$month"

    /** The four tabs live side by side in one pager on this screen, so a swipe follows the finger. */
    const val TABS = "tabs"

    /** In dock order, left to right. */
    val tabOrder = listOf(HOME, ACTIVITY, INSIGHTS, PLAN)
}

private data class Tab(val route: String, val icon: ImageVector, val label: String)

private val tabs = listOf(
    Tab(Routes.HOME, Lucide.House, "Home"),
    Tab(Routes.ACTIVITY, Lucide.ReceiptText, "Activity"),
    Tab(Routes.INSIGHTS, Lucide.ChartPie, "Insights"),
    Tab(Routes.PLAN, Lucide.Target, "Plan"),
)

/**
 * Fixed-length easing for screen changes. A back swipe scrubs these animations with the finger
 * (predictive back), which works smoothly with tweens but makes spring animations jump.
 */
private val navTween = tween<IntOffset>(340, easing = FastOutSlowInEasing)

private fun NavBackStackEntry.route() = destination.route.orEmpty()
private fun NavBackStackEntry.isAdd() = route().startsWith("add") || route().startsWith("edit")
private fun NavBackStackEntry.isSheet() = route().startsWith("recap")

/** Registers a screen and hands its animation scope to shared-element modifiers. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LeftoversNavHost(openAdd: Boolean = false, onOpenAddHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val back: () -> Unit = { nav.popBackStack() }
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    var toast by remember { mutableStateOf<String?>(null) }
    // A filter Home asked Activity to open with.
    var activityRequest by remember { mutableStateOf<ActivityRequest?>(null) }
    val appScope = rememberCoroutineScope()
    val undo = remember { UndoState(appScope) }
    val onTab = currentRoute == Routes.TABS
    // Kept here so the chosen tab survives opening and closing other screens.
    val pager = rememberPagerState { Routes.tabOrder.size }
    // A far tab slides in from its neighbour, so a tap never streams through the tabs in between.
    val showTab: (Int) -> Unit = { target ->
        appScope.launch {
            val from = pager.currentPage
            if (abs(target - from) > 1) pager.scrollToPage(if (target > from) target - 1 else target + 1)
            pager.animateScrollToPage(target, animationSpec = tween(380, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2400)
            toast = null
        }
    }
    // Each Undo stays for its full time, whichever tab the user moves to.
    LaunchedEffect(undo.current) {
        val shown = undo.current ?: return@LaunchedEffect
        delay(UndoState.VISIBLE_MS)
        undo.dismiss(shown)
    }
    LaunchedEffect(openAdd) {
        if (openAdd) {
            nav.navigate(Routes.add())
            onOpenAddHandled()
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalUndo provides undo) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.TABS,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        when {
                            // Add and edit slide up like a sheet: moving a finished layout is cheap and never stretches text.
                            targetState.isAdd() -> slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(220))
                            targetState.isSheet() -> slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))
                            else -> slideIntoContainer(SlideDirection.Start, navTween) + fadeIn(tween(220))
                        }
                    },
                    exitTransition = {
                        when {
                            // Scaling the screen behind would re-render its frosted blur every frame.
                            targetState.isAdd() -> fadeOut(tween(220))
                            targetState.isSheet() -> fadeOut(tween(300))
                            else -> slideOutOfContainer(SlideDirection.Start, navTween) { it / 4 } + fadeOut(tween(220))
                        }
                    },
                    popEnterTransition = {
                        when {
                            initialState.isAdd() -> fadeIn(tween(220))
                            initialState.isSheet() -> fadeIn(tween(300))
                            else -> slideIntoContainer(SlideDirection.End, navTween) { it / 4 } + fadeIn(tween(220))
                        }
                    },
                    popExitTransition = {
                        when {
                            initialState.isAdd() -> slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it / 5 } + fadeOut(tween(200))
                            initialState.isSheet() -> slideOutVertically(tween(360, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(260))
                            else -> slideOutOfContainer(SlideDirection.End, navTween) + fadeOut(tween(220))
                        }
                    },
                ) {
                    screen(Routes.TABS) {
                        // Back on another tab returns to Home first, as it did before.
                        BackHandler(enabled = pager.currentPage != 0) { showTab(0) }
                        // Sideways drags inside a tab (swipeable rows, card rows, the chart) are handled there
                        // first; anywhere else the pages move with the finger.
                        HorizontalPager(
                            state = pager,
                            beyondViewportPageCount = 1,
                            key = { Routes.tabOrder[it] },
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            // A page moving off eases back and fades a little, so the swipe has depth.
                            Box(
                                Modifier.fillMaxSize().graphicsLayer {
                                    val offset = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                                    val scale = 1f - 0.06f * offset
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1f - 0.45f * offset
                                },
                            ) {
                            when (Routes.tabOrder[page]) {
                                Routes.HOME -> {
                                    HomeScreen(
                                        onOpenBudget = { nav.navigate(Routes.BUDGET) },
                                        onOpenActivity = { type, accountId ->
                                            val filter = when (type) {
                                                TxType.EXPENSE -> TypeFilter.EXPENSE
                                                TxType.INCOME -> TypeFilter.INCOME
                                                null -> TypeFilter.ALL
                                            }
                                            activityRequest = ActivityRequest(filter, accountId)
                                            showTab(Routes.tabOrder.indexOf(Routes.ACTIVITY))
                                        },
                                        onOpenSubscriptions = { income -> nav.navigate(Routes.subscriptions(income)) },
                                        onOpenGoal = { nav.navigate(Routes.goal(it)) },
                                        onOpenGoals = { nav.navigate(Routes.GOALS) },
                                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                                        onOpenTransaction = { nav.navigate(Routes.edit(it)) },
                                        onReviewSms = { s -> nav.navigate(Routes.add(s.epochDay, s.amountMinor, s.merchant, s.id)) },
                                        onOpenRecap = { nav.navigate(Routes.recap(it.toString())) },
                                        onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) },
                                        onOpenAssistant = { nav.navigate(Routes.ASSISTANT) },
                                    )
                                }
                                Routes.ACTIVITY -> {
                                    HistoryScreen(
                                        onOpenTransaction = { nav.navigate(Routes.edit(it)) },
                                        onAddForDay = { nav.navigate(Routes.add(day = it.toEpochDay())) },
                                        onOpenSubscriptions = { income -> nav.navigate(Routes.subscriptions(income)) },
                                        requestedFilter = activityRequest,
                                        onRequestHandled = { activityRequest = null },
                                    )
                                }
                                Routes.INSIGHTS -> {
                                    StatsScreen(
                                        onOpenRecap = { nav.navigate(Routes.recap(it.toString())) },
                                        onOpenTransaction = { nav.navigate(Routes.edit(it)) },
                                    )
                                }
                                Routes.PLAN -> {
                                    PlanScreen(
                                        onOpenBudget = { nav.navigate(Routes.BUDGET) },
                                        onEditPlan = { nav.navigate(Routes.BUDGET_PLAN) },
                                        onOpenSubscriptions = { nav.navigate(Routes.subscriptions()) },
                                        onOpenGoals = { nav.navigate(Routes.GOALS) },
                                        onOpenGoal = { nav.navigate(Routes.goal(it)) },
                                        onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                                        onOpenDebts = { nav.navigate(Routes.DEBTS) },
                                    )
                                }
                            }
                            }
                        }
                    }
                    screen(Routes.ASSISTANT) { AssistantScreen(onBack = back) }
                    screen(Routes.DEBTS) { DebtsScreen(onBack = back) }
                    screen(Routes.DELETED) { DeletedScreen(onBack = back) }
                    screen(
                        Routes.ADD,
                        listOf(
                            navArgument("day") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("amount") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("note") { type = NavType.StringType; defaultValue = "" },
                            navArgument("sms") { type = NavType.LongType; defaultValue = -1L },
                        ),
                    ) {
                        EditorScreen(
                            onClose = back,
                            onSaved = {
                                toast = it
                                back()
                            },
                            onManageCategories = { nav.navigate(Routes.CATEGORIES) },
                        )
                    }
                    screen(Routes.EDIT, idArg) {
                        EditorScreen(
                            onClose = back,
                            onSaved = {
                                toast = it
                                back()
                            },
                            onManageCategories = { nav.navigate(Routes.CATEGORIES) },
                        )
                    }
                    screen(Routes.BUDGET) {
                        BudgetsScreen(
                            onBack = back,
                            onEditPlan = { nav.navigate(Routes.BUDGET_PLAN) },
                            onOpenSubscriptions = { nav.navigate(Routes.subscriptions()) },
                            onOpenGoals = { nav.navigate(Routes.GOALS) },
                        )
                    }
                    screen(Routes.BUDGET_PLAN) { BudgetPlanScreen(onBack = back) }
                    screen(Routes.SUBSCRIPTIONS, listOf(navArgument("income") { type = NavType.BoolType; defaultValue = false })) { entry ->
                        SubscriptionsScreen(onBack = back, startOnIncome = entry.arguments?.getBoolean("income") == true)
                    }
                    screen(Routes.GOALS) { GoalsScreen(onBack = back, onOpenGoal = { nav.navigate(Routes.goal(it)) }) }
                    screen(Routes.GOAL, idArg) { GoalDetailScreen(onBack = back) }
                    screen(Routes.ACCOUNTS) { AccountsScreen(onBack = back) }
                    screen(Routes.RECAP, listOf(navArgument("month") { type = NavType.StringType })) { RecapScreen(onClose = back) }
                    screen(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = back,
                            onManageCategories = { nav.navigate(Routes.CATEGORIES) },
                            onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) },
                            onOpenDeleted = { nav.navigate(Routes.DELETED) },
                        )
                    }
                    screen(Routes.CATEGORIES) { CategoriesScreen(onBack = back) }
                }

                AnimatedVisibility(
                    visible = onTab,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it * 2 } + fadeIn(),
                    exit = slideOutVertically(tween(220)) { it * 2 } + fadeOut(tween(160)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        FloatingDock(
                            // Follows the swipe, switching once a page is more than half way across.
                            current = Routes.tabOrder[pager.currentPage],
                            onTab = { showTab(Routes.tabOrder.indexOf(it)) },
                            onAdd = { nav.navigate(Routes.add()) },
                        )
                    }
                }

                // Sits just above the tab bar, or at the bottom on screens without one.
                val undoLift by animateDpAsState(if (onTab) 92.dp else 16.dp, label = "undoLift")
                var lastUndo by remember { mutableStateOf<UndoRequest?>(null) }
                undo.current?.let { lastUndo = it }
                AnimatedVisibility(
                    visible = undo.current != null,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = undoLift),
                ) {
                    lastUndo?.let { request ->
                        UndoBar(request, onUndo = { undo.undo(request) }, onDismiss = { undo.dismiss(request) })
                    }
                }

                AnimatedVisibility(
                    visible = toast != null,
                    enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(),
                    exit = slideOutVertically(tween(260)) { -it * 2 } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                ) {
                    Toast(toast.orEmpty())
                }
            }
        }
    }
}

/** Floating frosted tab bar with the add button in the middle; the active tab shows its name. */
@Composable
private fun FloatingDock(current: String?, onTab: (String) -> Unit, onAdd: () -> Unit) {
    val c = LocalAppColors.current
    // On narrow phones the selected tab's name is dropped and items sit closer, so every tab fits.
    val compact = LocalConfiguration.current.screenWidthDp < 400
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .clip(CircleShape)
            .frosted()
            .border(1.dp, glassBorder(c), CircleShape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.take(2).forEach { DockItem(it, current == it.route, compact) { onTab(it.route) } }
        Box(
            Modifier
                .padding(horizontal = if (compact) 4.dp else 6.dp)
                .size(54.dp)
                .pressable(onAdd, pressedScale = 0.88f)
                .clip(CircleShape)
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Plus, contentDescription = "Add entry", tint = c.onAccent, modifier = Modifier.size(26.dp))
        }
        tabs.drop(2).forEach { DockItem(it, current == it.route, compact) { onTab(it.route) } }
    }
}

@Composable
private fun DockItem(tab: Tab, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(if (selected) (if (c.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)) else Color.Transparent, label = "dockBg")
    val tint by animateColorAsState(if (selected) c.textPrimary else c.textTertiary, label = "dockTint")
    Row(
        Modifier
            .height(54.dp)
            .pressable(onClick, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = if (compact) 13.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
        AnimatedVisibility(
            visible = selected && !compact,
            enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkHorizontally(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(tab.label, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Toast(text: String) {
    val c = LocalAppColors.current
    // Clip before blurring so the frost follows the pill shape instead of filling a rectangle.
    Glass(shape = CircleShape, strong = true, modifier = Modifier.clip(CircleShape).frosted()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.CircleCheck, contentDescription = null, tint = c.positive, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1)
        }
    }
}
