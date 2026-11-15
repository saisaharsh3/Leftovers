package com.leftovers.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.leftovers.desktop.data.Store
import com.leftovers.desktop.ui.App
import com.leftovers.desktop.ui.AppState
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
        ) {
            window.minimumSize = Dimension(980, 660)
            App(state)
        }
    }
}
