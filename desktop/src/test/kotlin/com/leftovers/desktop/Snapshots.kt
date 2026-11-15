package com.leftovers.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.leftovers.app.data.Debt
import com.leftovers.app.data.Goal
import com.leftovers.app.data.GoalDeposit
import com.leftovers.app.data.Recurring
import com.leftovers.app.data.Transaction
import com.leftovers.app.data.TxType
import com.leftovers.desktop.data.Book
import com.leftovers.desktop.data.Prefs
import com.leftovers.desktop.data.Store
import com.leftovers.desktop.data.Theme
import com.leftovers.desktop.ui.App
import com.leftovers.desktop.ui.AppState
import com.leftovers.desktop.ui.Page
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth

/**
 * Draws pages off screen to PNG files, to look at the design without opening a window. Made-up data only.
 * Run: gradlew -p desktop snapshots -Pout=<folder> [-Ppages=HOME,SETTINGS] [-Ptheme=DARK] [-Pglass=true] [-Pwelcome=true]
 */
object Snapshots {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.getOrElse(0) { "snapshots" }).apply { mkdirs() }
        val names = args.getOrElse(1) { "" }.split(',').filter { it.isNotBlank() }
        val pages = names.filter { it != "EDITOR" && it != "PALETTE" && it != "SHORTCUTS" && it != "SELECT" }.map { Page.valueOf(it) }.ifEmpty { if (names.isNotEmpty()) emptyList() else Page.entries }
        val theme = Theme.valueOf(args.getOrElse(2) { "LIGHT" })
        val glass = args.getOrElse(3) { "false" } == "true"
        val welcome = args.getOrElse(4) { "false" } == "true"
        val store = demoStore(theme, glass, welcome)
        val state = AppState(store)
        if (welcome) {
            shoot(File(out, "welcome-${theme.name.lowercase()}${if (glass) "-glass" else ""}.png")) { App(state) }
            return
        }
        if ("EDITOR" in names) {
            state.edit(store.book.transactions.firstOrNull { it.note == "Lunch with Mia" }?.id)
            shoot(File(out, "editor-${theme.name.lowercase()}${if (glass) "-glass" else ""}.png")) { App(state) }
            state.editing = null
        }
        if ("SELECT" in names) {
            state.page = Page.ACTIVITY
            val ids = store.view.value.entries.filter { it.type == com.leftovers.app.data.TxType.EXPENSE }.take(4).drop(1).map { it.id }
            com.leftovers.desktop.ui.screens.ActivityMemory.selected.addAll(ids)
            shoot(File(out, "select-${theme.name.lowercase()}.png")) { App(state) }
            com.leftovers.desktop.ui.screens.ActivityMemory.selected.clear()
        }
        if ("PALETTE" in names) {
            state.palette = true
            shoot(File(out, "palette-${theme.name.lowercase()}${if (glass) "-glass" else ""}.png")) { App(state) }
            state.palette = false
        }
        if ("SHORTCUTS" in names) {
            state.shortcuts = true
            shoot(File(out, "shortcuts-${theme.name.lowercase()}.png")) { App(state) }
            state.shortcuts = false
        }
        pages.forEach { p ->
            state.page = p
            shoot(File(out, "${p.name.lowercase()}-${theme.name.lowercase()}${if (glass) "-glass" else ""}.png")) { App(state) }
        }
    }

    private fun shoot(file: File, width: Int = 1240, height: Int = 820, content: @Composable () -> Unit) {
        ImageComposeScene(width * 2, height * 2, Density(2f), content = content).use { scene ->
            // Let springs settle: render a few seconds of frames.
            var t = 0L
            repeat(90) { scene.render(t); t += 33_000_000L }
            val image = scene.render(t)
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        println("Wrote ${file.absolutePath}")
    }

    fun demoStore(theme: Theme, glass: Boolean, welcome: Boolean): Store {
        val store = Store(Files.createTempDirectory("leftovers-snap").toFile())
        val today = LocalDate.now()
        if (welcome) {
            store.save(Book(prefs = Prefs(theme = theme, glass = glass)))
            return store
        }
        store.save(Book.starter("EUR").let { it.copy(prefs = it.prefs.copy(onboarded = true, theme = theme, glass = glass)) })
        store.setPlan(store.book.plan.copy(monthlyMinor = 180_000))
        val demo = listOf(
            Triple("Bakery", 1, 640L), Triple("Fresh market", 2, 4_820L), Triple("Metro card", 3, 3_000L),
            Triple("Book shop", 4, 1_899L), Triple("Electricity", 5, 6_450L), Triple("Cinema", 7, 2_400L),
            Triple("Pharmacy", 8, 1_235L), Triple("Lunch with Mia", 1, 2_860L), Triple("Groceries", 2, 6_120L),
            Triple("Taxi", 3, 1_740L), Triple("Coffee", 1, 380L), Triple("Shoes", 4, 7_990L),
        )
        demo.forEachIndexed { i, (note, cat, amount) ->
            store.saveTransaction(Transaction(amountMinor = amount, type = TxType.EXPENSE, categoryId = cat.toLong(), epochDay = today.minusDays((i * 2).toLong()).toEpochDay(), note = note, accountId = if (i % 3 == 0) 1 else 2, createdAt = 1_000L + i))
        }
        store.saveTransaction(Transaction(amountMinor = 320_000, type = TxType.INCOME, categoryId = 13, epochDay = today.withDayOfMonth(1).toEpochDay(), note = "Salary", accountId = 2, createdAt = 50))
        store.saveRecurring(Recurring(name = "Music", amountMinor = 1_099, type = TxType.EXPENSE, categoryId = 7, dayOfMonth = 24, startMonth = YearMonth.from(today).toString(), lastPostedMonth = null))
        store.saveRecurring(Recurring(name = "Rent", amountMinor = 95_000, type = TxType.EXPENSE, categoryId = 6, dayOfMonth = 1, startMonth = YearMonth.from(today).plusMonths(1).toString(), lastPostedMonth = null, manual = true))
        val goal = store.saveGoal(Goal(name = "New laptop", emoji = "laptop", color = 0xFF7DB7F5L, targetMinor = 140_000, targetMonth = YearMonth.from(today).plusMonths(5).toString()))
        store.saveDeposit(GoalDeposit(goalId = goal, amountMinor = 52_000, epochDay = today.minusDays(10).toEpochDay()))
        store.saveDebt(Debt(person = "Mia", amountMinor = 1_430, note = "Lunch, her half", epochDay = today.minusDays(14).toEpochDay(), accountId = 2, splitOf = store.book.transactions.first { it.note == "Lunch with Mia" }.id))
        return store
    }
}
