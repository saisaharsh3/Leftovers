package com.leftovers.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.leftovers.app.MainActivity
import com.leftovers.app.R

/**
 * What a long press on the app icon offers: add spending, add income, and the shopping list. Set in code rather than
 * a shortcuts file so the dev build, with its own package name, gets them too.
 */
object AppShortcuts {
    const val EXTRA_SCREEN = "open_screen"
    const val SCREEN_INCOME = "add_income"
    const val SCREEN_SHOPPING = "shopping"

    fun publish(context: Context) {
        fun intent(screen: String?) = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (screen == null) putExtra(MainActivity.EXTRA_OPEN_ADD, true) else putExtra(EXTRA_SCREEN, screen) }

        fun shortcut(id: String, label: String, icon: Int, screen: String?) =
            ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label)
                .setIcon(IconCompat.createWithResource(context, icon))
                .setIntent(intent(screen))
                .build()

        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(
                    shortcut("add_spending", "Add spending", R.drawable.ic_shortcut_spend, null),
                    shortcut("add_income", "Add income", R.drawable.ic_shortcut_income, SCREEN_INCOME),
                    shortcut("shopping", "Shopping list", R.drawable.ic_shortcut_shopping, SCREEN_SHOPPING),
                ),
            )
        }
    }
}
