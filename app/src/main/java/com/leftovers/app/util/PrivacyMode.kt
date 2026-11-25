package com.leftovers.app.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Privacy mode (#73): amounts hidden on screen, for screenshots or when someone's looking. Toggled with the eye on
 * Home; on each time the app opens when "Hide amounts when the app opens" is set.
 */
object PrivacyMode {
    var hidden by mutableStateOf(false)

    fun toggle() { hidden = !hidden }
}
