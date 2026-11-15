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

fun main() {
    val store = Store()
    // Subscriptions whose day came while the app was closed, and old deleted entries.
    runCatching { store.postDueRecurring(); store.purgeDeleted() }
    val state = AppState(store)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Leftovers",
            state = rememberWindowState(size = DpSize(1240.dp, 820.dp)),
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
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@Window false
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
