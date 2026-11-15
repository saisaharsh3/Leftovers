package com.leftovers.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import com.leftovers.app.data.TxType
import com.leftovers.app.util.Money
import com.leftovers.desktop.data.HomeModel
import com.leftovers.desktop.data.Store
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** The app's mark, for the window, the taskbar and the tray: what's left of a full circle. */
val AppIcon: ImageVector by lazy {
    ImageVector.Builder("leftovers", 64.dp, 64.dp, 64f, 64f).apply {
        addPath(addPathNodes("M16 2h32a14 14 0 0 1 14 14v32a14 14 0 0 1-14 14H16A14 14 0 0 1 2 48V16A14 14 0 0 1 16 2z"), fill = SolidColor(Color(0xFF16161B)))
        addPath(
            addPathNodes("M32 16A16 16 0 1 1 16 32"),
            stroke = SolidColor(Color(0xFFCDF46F)), strokeLineWidth = 7f, strokeLineCap = StrokeCap.Round,
        )
    }.build()
}

/** Where the window was and how big, so it opens the same way next time. Kept apart from the data. */
data class WindowPlace(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean) {
    companion object {
        private fun file(store: Store) = File(store.dir, "window.json")

        fun load(store: Store): WindowPlace? = runCatching {
            val o = JSONObject(file(store).readText())
            WindowPlace(o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h"), o.optBoolean("max"))
        }.getOrNull()?.takeIf { it.width >= 600 && it.height >= 400 }

        fun save(store: Store, p: WindowPlace) {
            runCatching {
                store.dir.mkdirs()
                file(store).writeText(JSONObject().put("x", p.x).put("y", p.y).put("w", p.width).put("h", p.height).put("max", p.maximized).toString())
            }
        }
    }
}

/**
 * Bills that need a hand, as a Windows notification: one waiting to be marked paid, or one charged tomorrow.
 * Each is told once a day, while Leftovers is open or in the tray.
 */
class Reminders(private val store: Store, private val tray: TrayState) {
    private val told = mutableSetOf<String>()

    fun check(today: LocalDate = LocalDate.now()) {
        if (!store.book.prefs.onboarded || !store.book.prefs.billReminders) return
        val view = store.view.value
        val money = Money(view.book.currency)
        val home = HomeModel.of(view, today)
        home.due.forEach { d ->
            val key = "due:${d.item.id}:${d.month}:$today"
            if (told.add(key)) {
                val income = d.item.type == TxType.INCOME
                tray.sendNotification(
                    Notification(
                        "${d.item.name} is due",
                        "${money.format(d.item.amountMinor)} · mark it ${if (income) "received" else "paid"} on Home when it ${if (income) "comes in" else "goes out"}.",
                        Notification.Type.Info,
                    ),
                )
            }
        }
        view.recurring.filter { it.active && !it.manual && it.type == TxType.EXPENSE }
            .filter { it.toRecurring().nextChargeDate(today) == today.plusDays(1) }
            .forEach { r ->
                if (told.add("tomorrow:${r.id}:$today")) {
                    tray.sendNotification(Notification("${r.name} tomorrow", "${money.format(r.amountMinor)} will be logged tomorrow.", Notification.Type.Info))
                }
            }
    }
}

/**
 * Only one Leftovers at a time, as two would both write the data file. A second start asks the first to show
 * its window (by touching a file it watches) and quits.
 */
object OneAtATime {
    private var lock: java.nio.channels.FileLock? = null

    /** True when this is the only one running; false when another is, which has been asked to show itself. */
    fun claim(store: Store): Boolean {
        store.dir.mkdirs()
        val channel = java.io.RandomAccessFile(File(store.dir, ".running"), "rw").channel
        lock = runCatching { channel.tryLock() }.getOrNull()
        if (lock != null) return true
        runCatching { File(store.dir, ".show").writeText(System.currentTimeMillis().toString()) }
        return false
    }

    /** Whether another start asked this one to show its window since the last look. */
    fun asked(store: Store): Boolean {
        val f = File(store.dir, ".show")
        if (!f.exists()) return false
        f.delete()
        return true
    }
}
