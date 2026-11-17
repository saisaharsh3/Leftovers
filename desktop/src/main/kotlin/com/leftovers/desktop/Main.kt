package com.leftovers.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.leftovers.desktop.data.Store
import com.leftovers.desktop.ui.App
import com.leftovers.desktop.ui.AppState
import com.leftovers.desktop.ui.Page
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.Dimension
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberTrayState
import kotlinx.coroutines.delay

fun main() {
    val store = Store()
    if (!OneAtATime.claim(store)) return
    OneAtATime.asked(store)
    // Subscriptions whose day came while the app was closed, and old deleted entries.
    runCatching { store.postDueRecurring(); store.purgeDeleted() }
    val state = AppState(store)
    val place = WindowPlace.load(store)
    application {
        val windowState = rememberWindowState(
            placement = if (place?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = place?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
            size = place?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1240.dp, 820.dp),
        )
        var open by remember { mutableStateOf(true) }
        val tray = rememberTrayState()
        val reminders = remember { Reminders(store, tray) }
        LaunchedEffect(Unit) {
            // Started again while in the tray: show this one.
            while (true) {
                if (OneAtATime.asked(store)) { open = true; windowState.isMinimized = false }
                delay(700)
            }
        }
        LaunchedEffect(Unit) {
            // Once a minute: bills that came due, and subscriptions whose day came while the app stayed open.
            while (true) {
                runCatching { reminders.check() }
                if (java.time.LocalTime.now().minute == 0) runCatching { store.postDueRecurring() }
                delay(60_000)
            }
        }
        fun savePlace() {
            val p = windowState.position
            if (windowState.placement == WindowPlacement.Floating && p is WindowPosition.Absolute) {
                WindowPlace.save(store, WindowPlace(p.x.value.toInt(), p.y.value.toInt(), windowState.size.width.value.toInt(), windowState.size.height.value.toInt(), false))
            } else if (windowState.placement == WindowPlacement.Maximized) {
                val was = WindowPlace.load(store)
                WindowPlace.save(store, (was ?: WindowPlace(100, 100, 1240, 820, true)).copy(maximized = true))
            }
        }
        var toldTray by remember { mutableStateOf(false) }
        Tray(
            icon = rememberVectorPainter(AppIcon),
            state = tray,
            tooltip = "Leftovers",
            onAction = { open = true },
            menu = {
                Item("Open Leftovers", onClick = { open = true })
                Item("Add an entry", onClick = { open = true; state.edit() })
                Separator()
                Item("Quit", onClick = { savePlace(); exitApplication() })
            },
        )
        if (open) Window(
            onCloseRequest = {
                savePlace()
                if (store.book.prefs.tray && store.book.prefs.onboarded) {
                    open = false
                    if (!toldTray) {
                        toldTray = true
                        tray.sendNotification(Notification("Leftovers is still here", "It's by the clock, for reminders. Quit from its menu there.", Notification.Type.None))
                    }
                } else exitApplication()
            },
            title = "Leftovers",
            icon = rememberVectorPainter(AppIcon),
            state = windowState,
            onPreviewKeyEvent = { e ->
                // Ctrl+N adds an entry from anywhere; Ctrl+1 to Ctrl+9 open the pages in the sidebar's order.
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@Window false
                when {
                    e.key == Key.K -> { state.palette = !state.palette; true }
                    e.key == Key.Slash -> { state.shortcuts = !state.shortcuts; true }
                    e.key == Key.N -> { state.edit(); true }
                    e.key == Key.F && state.editing == null -> {
                        state.page = Page.ACTIVITY
                        // Once Activity is on screen, its search box takes the typing.
                        java.awt.EventQueue.invokeLater { runCatching { com.leftovers.desktop.ui.screens.ActivityMemory.searchFocus.requestFocus() } }
                        true
                    }
                    e.key == Key.T && store.book.accounts.size > 1 -> { state.edit(transfer = true); true }
                    e.key == Key.Comma -> { state.page = Page.SETTINGS; true }
                    e.key.keyCode in Key.One.keyCode..Key.Nine.keyCode && state.editing == null -> {
                        state.page = Page.entries[(e.key.keyCode - Key.One.keyCode).toInt()]; true
                    }
                    else -> false
                }
            },
            // After the focused thing had its turn: a text box undoes its own typing; anywhere else, the last change.
            onKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) return@Window false
                val picked = com.leftovers.desktop.ui.screens.ActivityMemory.selected
                if (e.key == Key.Escape && picked.isNotEmpty()) { picked.clear(); return@Window true }
                if (!e.isCtrlPressed) return@Window false
                // Ctrl+A in Activity, outside a text box: every entry on screen.
                if (e.key == Key.A && state.page == Page.ACTIVITY && state.editing == null) {
                    picked.clear(); picked.addAll(com.leftovers.desktop.ui.screens.ActivityMemory.visible); return@Window true
                }
                when {
                    e.key == Key.Z && !e.isShiftPressed -> { if (store.undo()) state.tell("Undone"); true }
                    e.key == Key.Y || (e.key == Key.Z && e.isShiftPressed) -> { if (store.redo()) state.tell("Redone"); true }
                    else -> false
                }
            },
        ) {
            window.minimumSize = Dimension(980, 660)
            App(state)
        }
    }
}
