package com.leftovers.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.leftovers.app.ui.components.LocalUndo
import com.leftovers.app.ui.components.DragToDismiss
import com.leftovers.app.ui.components.Haptics
import com.leftovers.app.ui.components.Overlays
import com.leftovers.app.ui.components.AuroraShift
import com.leftovers.app.ui.components.drawAurora
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import androidx.compose.runtime.snapshotFlow
import com.leftovers.app.ui.components.rememberReduceMotion
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
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

private object Routes {
    /** The four tabs, side by side in one pager. */
    const val TABS = "tabs"
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
    const val SUBSCRIPTIONS = "subscriptions?income={income}&open={open}"
    /** [open] is a subscription to show straight away, or -1. */
    fun subscriptions(income: Boolean = false, open: Long = -1) = "subscriptions?income=$income&open=$open"
    const val GOALS = "goals"
    const val GOAL = "goals/{id}"
    const val ACCOUNTS = "accounts"
    const val RECAP = "recap/{month}"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val ASSISTANT = "assistant?ask={ask}"
    /** Opens the assistant; a non-empty [ask] is sent as the first question. */
    fun assistant(ask: String = "") = "assistant?ask=" + android.net.Uri.encode(ask)

    fun add(day: Long = -1L, amount: Long = -1L, note: String = "", sms: Long = -1L) =
        "add?day=$day&amount=$amount&note=${Uri.encode(note)}&sms=$sms"
    fun edit(id: Long) = "edit/$id"
    fun goal(id: Long) = "goals/$id"
    fun recap(month: String) = "recap/$month"

    /** In dock order, left to right; a sideways swipe moves to the neighbour. */
    val tabOrder = listOf(HOME, ACTIVITY, INSIGHTS, PLAN)
    val tabs = tabOrder.toSet()
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
private fun NavBackStackEntry.isTab() = route() == Routes.TABS
private fun NavBackStackEntry.isAdd() = route().startsWith("add") || route().startsWith("edit")
private fun NavBackStackEntry.isSheet() = route().startsWith("recap")

/** Which way to slide between two tabs: forward when moving right along the dock. */
private fun tabDirection(from: NavBackStackEntry, to: NavBackStackEntry) =
    if (Routes.tabOrder.indexOf(to.route()) > Routes.tabOrder.indexOf(from.route())) SlideDirection.Start else SlideDirection.End

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
    // Only while there is a screen to go back to: a quick second tap on a back arrow used to remove Home itself,
    // leaving an empty screen with just the tab bar.
    val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    var toast by remember { mutableStateOf<String?>(null) }
    // A filter Home asked Activity to open with.
    var activityRequest by remember { mutableStateOf<ActivityRequest?>(null) }
    val appScope = rememberCoroutineScope()
    val undo = remember { UndoState(appScope) }
    val onTab = currentRoute == Routes.TABS
    // The tabs sit side by side: a swipe moves them with the finger and a flick carries on to the next one.
    val pager = rememberPagerState(pageCount = { Routes.tabOrder.size })
    val reduceMotion = rememberReduceMotion()
    val view = LocalView.current
    // The aurora drifts with the swipe.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { AuroraShift.x = it }
    }
    val colors = LocalAppColors.current
    // Liquid glass needs Android 13 (it bends the picture behind it with a GPU shader) and glass effects turned on.
    val liquid = com.leftovers.app.ui.components.liquidGlass()
    // What the liquid tab bar bends: the aurora and the screen above it, recorded apart from what's shown.
    val onDrawBackdrop = remember(colors) { { scope: androidx.compose.ui.graphics.drawscope.ContentDrawScope -> scope.drawAurora(colors, AuroraShift.x); scope.drawContent() } }
    val backdrop = rememberLayerBackdrop(onDraw = onDrawBackdrop)
    // Show a tab: back on the tabs first if another screen is open, then the pager moves there.
    val showTab: (String) -> Unit = { route ->
        val i = Routes.tabOrder.indexOf(route).coerceAtLeast(0)
        if (nav.currentDestination?.route != Routes.TABS) nav.popBackStack(Routes.TABS, inclusive = false)
        appScope.launch { if (reduceMotion) pager.scrollToPage(i) else pager.animateScrollToPage(i) }
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
                    modifier = Modifier.fillMaxSize().then(if (liquid) Modifier.layerBackdrop(backdrop) else Modifier),
                    enterTransition = {
                        when {
                            // Add and edit slide up like a sheet: moving a finished layout is cheap and never stretches text.
                            targetState.isAdd() -> slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(220))
                            targetState.isSheet() -> slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))
                            // Both tabs slide together and the new one fades in straight away, so there's never a blank
                            // moment. The old one fades out quicker, so the two barely show at once.
                            targetState.isTab() && initialState.isTab() ->
                                slideIntoContainer(tabDirection(initialState, targetState), tween(300, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(240))
                            else -> slideIntoContainer(SlideDirection.Start, navTween) + fadeIn(tween(220))
                        }
                    },
                    exitTransition = {
                        when {
                            // Scaling the screen behind would re-render its frosted blur every frame.
                            targetState.isAdd() -> fadeOut(tween(220))
                            targetState.isSheet() -> fadeOut(tween(300))
                            targetState.isTab() && initialState.isTab() ->
                                slideOutOfContainer(tabDirection(initialState, targetState), tween(300, easing = FastOutSlowInEasing)) { it / 6 } + fadeOut(tween(120))
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
                        // Back from another tab goes to Home first, as it did when each tab was its own screen.
                        BackHandler(enabled = pager.currentPage != 0) { showTab(Routes.HOME) }
                        TabsPager(pager) { route ->
                    when (route) {
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
                                showTab(Routes.ACTIVITY)
                            },
                            onOpenSubscriptions = { income -> nav.navigate(Routes.subscriptions(income)) },
                            onOpenGoal = { nav.navigate(Routes.goal(it)) },
                            onOpenGoals = { nav.navigate(Routes.GOALS) },
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                            onOpenTransaction = { nav.navigate(Routes.edit(it)) },
                            onReviewSms = { s -> nav.navigate(Routes.add(s.epochDay, s.amountMinor, s.merchant, s.id)) },
                            onOpenRecap = { nav.navigate(Routes.recap(it.toString())) },
                            onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) },
                            onOpenAssistant = { nav.navigate(Routes.assistant()) },
                            onOpenDebts = { nav.navigate(Routes.DEBTS) },
                            onOpenSubscription = { r -> nav.navigate(Routes.subscriptions(r.type == TxType.INCOME, r.id)) },
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
                            onOpenYear = { nav.navigate(Routes.recap(it.toString())) },
                            onOpenTransaction = { nav.navigate(Routes.edit(it)) },
                            onAskAi = { nav.navigate(Routes.assistant(it)) },
                        )
                    }
                    else -> {
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
                    screen(Routes.ASSISTANT, listOf(navArgument("ask") { type = NavType.StringType; defaultValue = "" })) { entry ->
                        AssistantScreen(onBack = back, ask = entry.arguments?.getString("ask").orEmpty())
                    }
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
                        DragToDismiss(onDismiss = back) {
                            EditorScreen(
                                onClose = back,
                                onSaved = {
                                    toast = it
                                    Haptics.confirm(view)
                                    back()
                                },
                                onManageCategories = { nav.navigate(Routes.CATEGORIES) },
                            )
                        }
                    }
                    screen(Routes.EDIT, idArg) {
                        DragToDismiss(onDismiss = back) {
                            EditorScreen(
                                onClose = back,
                                onSaved = {
                                    toast = it
                                    Haptics.confirm(view)
                                    back()
                                },
                                onManageCategories = { nav.navigate(Routes.CATEGORIES) },
                            )
                        }
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
                    screen(
                        Routes.SUBSCRIPTIONS,
                        listOf(
                            navArgument("income") { type = NavType.BoolType; defaultValue = false },
                            navArgument("open") { type = NavType.LongType; defaultValue = -1L },
                        ),
                    ) { entry ->
                        SubscriptionsScreen(
                            onBack = back,
                            startOnIncome = entry.arguments?.getBoolean("income") == true,
                            openId = entry.arguments?.getLong("open") ?: -1L,
                        )
                    }
                    screen(Routes.GOALS) { GoalsScreen(onBack = back, onOpenGoal = { nav.navigate(Routes.goal(it)) }) }
                    screen(Routes.GOAL, idArg) { GoalDetailScreen(onBack = back) }
                    screen(Routes.ACCOUNTS) { AccountsScreen(onBack = back) }
                    screen(Routes.RECAP, listOf(navArgument("month") { type = NavType.StringType })) { DragToDismiss(onDismiss = back) { RecapScreen(onClose = back) } }
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

                // Content fades out under the tab bar instead of being cut off beside it.
                AnimatedVisibility(
                    visible = onTab,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(160)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { DockFade() }

                AnimatedVisibility(
                    // A pop-up card blurs the screen behind it; the bright add button would glow through, so the bar steps aside.
                    visible = onTab && Overlays.open == 0,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it * 2 } + fadeIn(),
                    exit = slideOutVertically(tween(220)) { it * 2 } + fadeOut(tween(160)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        FloatingDock(
                            backdrop = if (liquid) backdrop else null,
                            current = Routes.tabOrder[pager.currentPage],
                            onTab = { showTab(it) },
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

/**
 * The four tabs side by side. A swipe moves them with the finger, a flick carries on to the next tab from
 * the speed it was thrown at, and they settle on a spring. Rows and charts that handle their own sideways drag
 * (swipe to delete, scrolling card rows) get it first.
 */
@Composable
private fun TabsPager(state: PagerState, content: @Composable (route: String) -> Unit) {
    HorizontalPager(
        state = state,
        modifier = Modifier.fillMaxSize(),
        key = { Routes.tabOrder[it] },
        flingBehavior = PagerDefaults.flingBehavior(
            state = state,
            snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 420f),
        ),
    ) { page -> content(Routes.tabOrder[page]) }
}

/** A soft fade at the bottom, behind the tab bar, so content scrolling under it dissolves instead of showing in pieces. */
@Composable
private fun DockFade() {
    val c = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, c.background.copy(alpha = 0.8f), c.background.copy(alpha = 0.96f))))
            .navigationBarsPadding()
            .height(104.dp),
    )
}

/** Floating frosted tab bar with the add button in the middle; the active tab shows its name. */
@Composable
private fun FloatingDock(backdrop: LayerBackdrop?, current: String?, onTab: (String) -> Unit, onAdd: () -> Unit) {
    val c = LocalAppColors.current
    // On narrow phones the selected tab's name is dropped and items sit closer, so every tab fits.
    val compact = LocalConfiguration.current.screenWidthDp < 400
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .then(
                if (backdrop != null) {
                    // Liquid glass: what's behind bends at the bar's rounded edges, a little colour splits at the
                    // rim, and a bright edge catches the light.
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(6.dp.toPx())
                            lens(18.dp.toPx(), 36.dp.toPx(), chromaticAberration = true)
                        },
                        onDrawSurface = { drawRect(if (c.isDark) Color(0xFF0E0E13).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.32f)) },
                    )
                } else {
                    Modifier
                        .clip(CircleShape)
                        // Light mode: a lighter tint than the headers, so the bar is glass like the cards, not milky white.
                        .frosted(if (c.isDark) c else c.copy(barTint = Color.White.copy(alpha = 0.18f)))
                        // A soft top-lit sheen, so the bar reads as glass in both themes.
                        .background(Brush.verticalGradient(listOf(c.glassStrong, c.glass.copy(alpha = c.glass.alpha * 0.4f))))
                        .border(1.dp, glassBorder(c), CircleShape)
                },
            )
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
