package com.leftovers.app.ui.screens

import androidx.compose.ui.graphics.vector.ImageVector
import com.leftovers.app.data.Palette
import com.leftovers.app.ui.icons.Lucide

/**
 * Settings' pages, like a phone's: each category opens a page of its own, and the search finds a setting by its
 * name or a word for it. The titles here are the rows' own, so search (and Help) can open the page and light the row.
 */
object SettingsIndex {
    const val APPEARANCE = "Appearance"
    const val SECURITY = "Security"
    const val REMINDERS = "Reminders & alerts"
    const val DETECTION = "Payment detection"
    const val PAID = "Getting paid"
    const val AI = "AI assistant"
    const val GENERAL = "General"
    const val DATA = "Backup & data"
    const val SYNC = "Sync"
    const val ABOUT = "About"

    /** [color]: its icon's, so each category is found by colour as much as by name, as on a phone. */
    class Page(val title: String, val icon: ImageVector, val summary: String, val color: Long)

    /** [row]: false for a setting with no row of its own to light up (the page opens at its top). */
    class Entry(val title: String, val page: String, val icon: ImageVector, val words: String = "", val row: Boolean = true)

    /**
     * The categories in groups that go together, each group a card of its own: how the app counts and looks, what it
     * does for you, where your data is kept, and the app itself.
     */
    val groups: List<List<Page>> by lazy {
        listOf(
            listOf(
                Page(GENERAL, Lucide.SlidersHorizontal, "Currency, categories, accounts", Palette.SLATE),
                Page(APPEARANCE, Lucide.Palette, "Theme, background, glass, widgets", Palette.LILAC),
            ),
            listOf(
                Page(DETECTION, Lucide.Smartphone, "Bank SMS, payment apps, email", Palette.MINT),
                Page(PAID, Lucide.HandCoins, "UPI ID or other ways friends pay you back", Palette.LIME),
                Page(REMINDERS, Lucide.Bell, "Daily reminder, bills, budget alerts", Palette.AMBER),
                Page(AI, Lucide.BotMessageSquare, "", Palette.IRIS),
            ),
            listOf(
                Page(SECURITY, Lucide.Shield, "App lock, screenshots", Palette.CORAL),
                Page(DATA, Lucide.Download, "Backups, import, export, deleted entries", Palette.SKY),
                Page(SYNC, Lucide.Repeat, "Between your devices", Palette.TEAL),
            ),
            listOf(Page(ABOUT, Lucide.Info, "Version, updates, feedback", Palette.SAND)),
        )
    }

    val pages: List<Page> by lazy { groups.flatten() }

    /** A page's colour, for its settings in search results. */
    fun colorOf(page: String): Long = pages.firstOrNull { it.title == page }?.color ?: Palette.SLATE

    val entries: List<Entry> by lazy {
        listOf(
            Entry("Theme", APPEARANCE, Lucide.Moon, "dark light system automatic mode night", row = false),
            Entry("Background", APPEARANCE, Lucide.Palette, "glow photo wallpaper picture plain", row = false),
            Entry("Accent from wallpaper", APPEARANCE, Lucide.Palette, "colour color dynamic material you"),
            Entry("Glass effects", APPEARANCE, Lucide.Droplet, "blur transparent liquid battery"),
            Entry("Pure black (AMOLED)", APPEARANCE, Lucide.Moon, "oled dark battery"),
            Entry("Time", APPEARANCE, Lucide.CalendarClock, "24 hour 12 clock format am pm"),
            Entry("Home screen widgets", APPEARANCE, Lucide.Palette, "widget launcher"),
            Entry("App lock", SECURITY, Lucide.Lock, "pin password fingerprint face biometric privacy"),
            Entry("Block screenshots", SECURITY, Lucide.Camera, "privacy recent apps screen record"),
            Entry("Remind me before bills", REMINDERS, Lucide.Repeat, "recurring subscription due notification"),
            Entry("Daily reminder", REMINDERS, Lucide.Bell, "notification log every day"),
            Entry("Budget alerts", REMINDERS, Lucide.Bell, "limit overspend notification warning"),
            Entry("Detect bank SMS", DETECTION, Lucide.Smartphone, "text message upi automatic"),
            Entry("Add small payments automatically", DETECTION, Lucide.Zap, "auto sms limit amount without asking"),
            Entry("Detect payment apps", DETECTION, Lucide.Bell, "notification gpay google pay phonepe paytm upi"),
            Entry("Detect payments in email", DETECTION, Lucide.Mail, "gmail mailbox inbox outlook"),
            Entry("Detection keywords", DETECTION, Lucide.Tag, "bank words test a message not detected"),
            Entry("UPI ID", PAID, Lucide.Wallet, "upi vpa gpay phonepe paytm bhim pay me back reminder"),
            Entry("Payment link", PAID, Lucide.ExternalLink, "paypal revolut wise venmo cash app monzo pay me back"),
            Entry("Bank details", PAID, Lucide.Landmark, "iban account sort code swift bic transfer"),
            Entry("Add to reminders", PAID, Lucide.Bell, "remind money owed share payment details"),
            Entry("Currency", GENERAL, Lucide.Coins, "rupee dollar euro symbol money"),
            Entry("Show a split bill as one entry", GENERAL, Lucide.Split, "split friends parts"),
            Entry("Categories", GENERAL, Lucide.Tag, "icons needs wants hide"),
            Entry("Accounts", GENERAL, Lucide.Wallet, "cash bank card balance"),
            Entry("Back up", DATA, Lucide.Download, "backup save file google drive"),
            Entry("Restore", DATA, Lucide.Repeat, "backup file load"),
            Entry("Deleted entries", DATA, Lucide.History, "trash bin undo restore recover"),
            Entry("Automatic backup", DATA, Lucide.CalendarClock, "backup daily weekly monthly folder"),
            Entry("Password-protect backups", DATA, Lucide.Lock, "encrypt backup"),
            Entry("Include photos in backups", DATA, Lucide.Camera, "receipt pictures backup"),
            Entry("Export to CSV", DATA, Lucide.ReceiptText, "excel sheets spreadsheet"),
            Entry("Import entries", DATA, Lucide.Download, "statement pdf csv other apps bank"),
            Entry("Sync between devices", SYNC, Lucide.Smartphone, "webdav folder server two phones"),
            Entry("AI assistant", AI, Lucide.BotMessageSquare, "chat gemini claude openai api key on this phone"),
            Entry("Report a problem or suggest an idea", ABOUT, Lucide.MessageCircle, "bug feedback github issue"),
            Entry("Tell me about new versions", ABOUT, Lucide.Bell, "update notification"),
            Entry("Check for a new version", ABOUT, Lucide.Download, "update"),
        )
    }

    /** Settings whose name, page or words have every word typed, closest first. */
    fun search(query: String): List<Entry> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries
            .filter { e -> words.all { w -> "${e.title} ${e.page} ${e.words}".lowercase().contains(w) } }
            .sortedBy { e ->
                val t = e.title.lowercase()
                if (t.startsWith(words.first())) 0 else if (t.contains(words.first())) 1 else 2
            }
    }

    /**
     * The page to open for a path's second part (Help says "Settings → Payment detection → Detect bank SMS"), going by
     * the row when it names one. "bottom" is About; an unknown page name falls back to the row's page, else the list.
     */
    fun pageFor(section: String, row: String?): String? {
        row?.let { r -> entries.firstOrNull { it.title == r }?.let { return it.page } }
        if (section == "bottom") return ABOUT
        return pages.firstOrNull { it.title == section }?.title
    }
}
