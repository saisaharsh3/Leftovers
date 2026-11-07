package com.leftovers.app.util

/**
 * Every feature, what it does and where to find it, for Settings → Help & features. When a feature is added or
 * moved, its entry here changes with it (like the changelog line), so the guide stays true.
 */
object HelpGuide {
    /** A screen the guide can open. */
    enum class Place { HOME, ACTIVITY, INSIGHTS, PLAN, ADD, ACCOUNTS, BUDGET, BUDGET_PLAN, SUBSCRIPTIONS, GOALS, DEBTS, CATEGORIES, SETTINGS, DELETED, ASSISTANT, NOTICES }

    /** [where] is the path, in the app's own words; [words] are other words people might search for. */
    data class Item(val section: String, val title: String, val text: String, val where: String, val place: Place? = null, val words: String = "")

    val items: List<Item> = listOf(
        // Adding entries
        Item("Adding entries", "Add an expense or income", "Type the amount on the keypad, pick a category and the date, and save.", "The + button in the middle of the tab bar", Place.ADD, "new entry log spend spending"),
        Item("Adding entries", "Split a bill", "One bill across several categories, or shared with people: their shares go to Money owed, and it shows as one entry.", "+ → Split", Place.ADD, "share divide friends groceries parts"),
        Item("Adding entries", "Lent or borrowed", "Money you lent or borrowed isn't spending: it goes to Money owed. You can save it without an amount, as a reminder.", "+ → Lent or borrowed", Place.ADD, "loan owe debt friend pending"),
        Item("Adding entries", "Repeat every month", "Turn an entry into a subscription or a regular payment that's logged for you.", "+ → Repeat (↻)", Place.ADD, "recurring rent bill monthly"),
        Item("Adding entries", "Receipt photo", "Attach a photo of the bill to an entry. With the AI assistant connected, it can read the bill and fill the entry in.", "+ → Attach receipt", Place.ADD, "bill camera scan invoice picture"),
        Item("Adding entries", "Undo and deleted entries", "Deleting shows Undo for a few seconds. Deleted entries are kept, to put back later.", "Settings → Data → Deleted entries", Place.DELETED, "restore recover trash bin removed"),

        // Seeing your money
        Item("Seeing your money", "Home", "What's left to spend today and this month, balances, upcoming bills, money owed and recent entries. Pull down to check email, sync and updates.", "The Home tab", Place.HOME, "dashboard safe to spend refresh"),
        Item("Seeing your money", "Search and filters", "Search notes, categories, accounts, #tags or amounts; several words find entries with all of them. Filter by account, category or #tag.", "Activity → search or filter buttons", Place.ACTIVITY, "find look up tag hashtag"),
        Item("Seeing your money", "Calendar view", "Each day's spending on a calendar; tap a day to see its entries.", "Activity → calendar button", Place.ACTIVITY, "month days"),
        Item("Seeing your money", "Charts and trends", "Spending and income by category and day, compared with the month before.", "The Insights tab", Place.INSIGHTS, "stats graph analysis report"),
        Item("Seeing your money", "Monthly recap", "A look back at a month or a year.", "Insights → ✦ button", Place.INSIGHTS, "summary review year wrapped"),
        Item("Seeing your money", "PDF statement", "A statement of a month or a year as a PDF to share: income, spending, categories, subscriptions and money owed. Notes are left out unless you add them.", "Insights → download button", Place.INSIGHTS, "export print report pdf"),
        Item("Seeing your money", "Notices", "Updates, what's new, and replies about the app.", "Home → bell", Place.NOTICES, "notifications news whats new"),

        // Accounts
        Item("Accounts", "Accounts and balances", "Bank, cash, cards and wallets, each with its balance. Tap one to see its entries.", "Home → Total balance card", Place.ACCOUNTS, "bank wallet card cash balance"),
        Item("Accounts", "Transfer between accounts", "Move money between your own accounts, such as paying a card bill from the bank. It isn't spending or income.", "Home → Total balance card → Transfer", Place.ACCOUNTS, "move money pay card bill withdraw"),
        Item("Accounts", "Accounts in another currency", "An account can be in its own currency; entries in it are converted for your totals.", "Accounts → an account → Currency", Place.ACCOUNTS, "foreign dollar euro travel"),
        Item("Accounts", "Leave an account out", "Keep an account out of the total balance and/or the budget, e.g. savings, or money that isn't yours such as a friend's spends on your card.", "Accounts → an account", Place.ACCOUNTS, "exclude hide savings reimbursement external"),
        Item("Accounts", "Put accounts in order", "Drag accounts into the order you want.", "Accounts → Reorder", Place.ACCOUNTS, "sort arrange drag"),

        // Budgets
        Item("Budgets", "Budget plan", "Monthly, Weeks (every week or two from payday), Dates (one amount for a trip or any dates) or 50/30/20.", "Plan → budget card → Budget plan", Place.BUDGET_PLAN, "monthly weekly payday 50 30 20 needs wants"),
        Item("Budgets", "Envelope budgets", "A Dates budget can count only entries with a #tag like #trip, or from one account, so other spending leaves it alone.", "Plan → Budget plan → Dates", Place.BUDGET_PLAN, "envelope tag trip holiday"),
        Item("Budgets", "Category limits", "A monthly limit for a category, with alerts as you near it.", "Plan → budget card → Category limits", Place.BUDGET, "limit cap alert"),
        Item("Budgets", "Set aside", "Money kept out of what's left to spend, such as for bills still to come.", "Plan → budget card → Set aside", Place.BUDGET, "reserve save"),
        Item("Budgets", "Categories", "Add, rename, recolour or remove categories, with an icon each.", "Settings → General → Categories", Place.CATEGORIES, "category icon colour"),

        // Bills, goals and money owed
        Item("Bills, goals and money owed", "Subscriptions and bills", "Regular payments and income, logged for you on their day, with a reminder before bills.", "Plan → Subscriptions", Place.SUBSCRIPTIONS, "netflix rent recurring salary emi"),
        Item("Bills, goals and money owed", "Savings goals", "Save towards something by a date: see what's needed each month. Add shop links to follow its price.", "Plan → Savings goals", Place.GOALS, "goal save target price"),
        Item("Bills, goals and money owed", "Money owed", "Who owes you and whom you owe. Mark one entry or everything with a person as received or settled.", "Home → Money owed, or Plan → More", Place.DEBTS, "lend borrow settle received friends"),

        // Detecting payments
        Item("Detecting payments", "Bank SMS", "Payments in your bank's text messages are suggested on Home, to add with one tap. Only a fingerprint of each message is kept.", "Settings → Security & reminders → Detect bank SMS", Place.SETTINGS, "sms message auto upi"),
        Item("Detecting payments", "Payment app notifications", "Read payments from apps like Google Pay, PhonePe or Paytm, only the apps you choose.", "Settings → Security & reminders → Detect payment apps", Place.SETTINGS, "notification gpay phonepe paytm"),
        Item("Detecting payments", "Email", "Find payments in bank and shop emails, over a secure connection to your mailbox.", "Settings → Security & reminders → Detect payments in email", Place.SETTINGS, "gmail mail inbox"),
        Item("Detecting payments", "Your bank's words", "If your bank's messages aren't picked up, add the word it uses, and test a message to see what the app makes of it.", "Settings → Security & reminders → Detection keywords", Place.SETTINGS, "keyword test message missing"),

        // Import, export and backup
        Item("Import, export and backup", "Import entries", "Bank statements (PDF or CSV), payment app statements, and other apps' exports. You check them before they're added; a wrong import can be removed in one go.", "Settings → Data → Import entries", Place.SETTINGS, "import statement csv pdf phonepe bank other app"),
        Item("Import, export and backup", "Export to CSV", "Every entry as a spreadsheet, for Excel or Sheets.", "Settings → Data → Export to CSV", Place.SETTINGS, "excel sheets spreadsheet"),
        Item("Import, export and backup", "Backups", "Back up and restore, automatically every week to a folder you choose, with a password and photos if you like.", "Settings → Data", Place.SETTINGS, "backup restore save folder password"),
        Item("Import, export and backup", "Sync between devices", "Keep two phones in step through a shared folder or your own WebDAV server.", "Settings → Sync", Place.SETTINGS, "sync nextcloud webdav two phones"),

        // AI assistant
        Item("AI assistant", "Ask and log by chat", "Ask about your money or tell it what to log. Every change waits for your Apply. Connect it with your own key for Claude, ChatGPT, Gemini and others.", "Home → chat button", Place.ASSISTANT, "ai chat claude chatgpt gemini ask"),
        Item("AI assistant", "Bills, statements and goals by chat", "Send a photo of a bill to add it, ask for a PDF statement or a CSV export, or create a savings goal.", "Home → chat button", Place.ASSISTANT, "photo receipt pdf csv goal"),
        Item("AI assistant", "Privacy", "Notes stay on the phone unless you share them; read-only mode stops all changes. Switch between AIs without typing a key again.", "Settings → AI assistant", Place.SETTINGS, "private key switch notes"),

        // Look, security and more
        Item("Look, security and more", "Appearance", "Light, dark or automatic, accent from your wallpaper, glass effects and pure black.", "Settings → Appearance", Place.SETTINGS, "theme dark light colour amoled"),
        Item("Look, security and more", "Home screen widgets", "Glass, dark or light widgets for the phone's home screen, in any size.", "Settings → Appearance → Home screen widgets", Place.SETTINGS, "widget launcher"),
        Item("Look, security and more", "App lock and screenshots", "Lock with a PIN, password, fingerprint or face, and block screenshots.", "Settings → Security & reminders", Place.SETTINGS, "lock pin fingerprint secure privacy screenshot"),
        Item("Look, security and more", "Reminders and alerts", "A daily reminder to log, reminders before bills, and budget alerts.", "Settings → Security & reminders", Place.SETTINGS, "notification remind alert"),
        Item("Look, security and more", "Currency", "The currency your amounts are shown in.", "Settings → General → Currency", Place.SETTINGS, "rupee dollar symbol"),
        Item("Look, security and more", "Updates and feedback", "Check for a new version, or report a problem and suggest an idea.", "Settings, at the bottom", Place.SETTINGS, "update version bug github feedback"),
    )

    val sections: List<String> get() = items.map { it.section }.distinct()

    /** Items with every word of [query] in their title, text, path or search words; all of them for a blank query. */
    fun search(query: String): List<Item> {
        val words = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return items
        return items.filter { i ->
            val hay = "${i.title} ${i.text} ${i.where} ${i.section} ${i.words}".lowercase()
            words.all { it in hay }
        }
    }
}
