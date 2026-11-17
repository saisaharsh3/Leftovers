package com.leftovers.app.util

/** The phone's own icon pictures are keyed "img:<file>"; on the desktop they live in the book (see Book.icons). */
object CustomIcons {
    const val PREFIX = "img:"
    fun isCustom(key: String) = key.startsWith(PREFIX)
}
