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
        Item("Adding entries", "Add an expense or income", "Type the amount on the keypad, pick a category and the date, and save. With many categories, All shows them at once, with a search.", "The + button in the middle of the tab bar", Place.ADD, "new entry log spend spending"),
        Item("Adding entries", "Split a bill", "One bill across several categories, or shared with people: their shares go to Money owed, and it shows as one entry.", "+ → Split", Place.ADD, "share divide friends groceries parts"),
        Item("Adding entries", "Lent or borrowed", "Money you lent or borrowed isn't spending: it goes to Money owed. You can save it without an amount, as a reminder.", "+ → Lent or borrowed", Place.ADD, "loan owe debt friend pending"),
        Item("Adding entries", "Repeat every month", "Turn an entry into a subscription or a regular payment that's logged for you.", "+ → Repeat (↻)", Place.ADD, "recurring rent bill monthly"),
        Item("Adding entries", "Receipt photo", "Attach a photo of the bill to an entry. With the AI assistant connected, it can read the bill and fill the entry in.", "+ → Attach receipt", Place.ADD, "bill camera scan invoice picture"),
        Item("Adding entries", "Tags", "Add #tags like #goa or #work to an entry, then search or filter by them, or give a trip its own budget.", "+ → Tags", Place.ADD, "hashtag label trip"),
        Item("Adding entries", "Undo and deleted entries", "Deleting shows Undo for a few seconds. Deleted entries are kept, to put back later.", "Settings → Backup & data → Deleted entries", Place.DELETED, "restore recover trash bin removed"),

        // Seeing your money
        Item("Seeing your money", "Home", "What's left to spend today and this month, balances, upcoming bills, money owed and recent entries. Pull down to check email, sync and updates.", "The Home tab", Place.HOME, "dashboard safe to spend refresh"),
        Item("Seeing your money", "Safe to spend today", "The big number on Home: what you can spend today and stay on budget, or how far over you are. Tap it for the month's budget.", "Home, at the top", Place.BUDGET, "over budget left daily limit"),
        Item("Seeing your money", "Add small payments automatically", "With bank SMS on, spending up to an amount you choose is added right away, under the category that place had last time. Bigger payments, money coming in, and paybacks still wait for you on Home.", "Settings → Payment detection → Add small payments automatically", Place.SETTINGS, "auto automatic small sms limit threshold"),
        Item("Seeing your money", "Detected payments", "Payments found in your SMS, email or payment apps wait on Home: tap one to check and add it, or ✕ to dismiss it.", "Home → Detected payments", Place.HOME, "suggested pending review sms"),
        Item("Seeing your money", "One month or all time", "See one month at a time or every entry ever, newest first or by amount. Transfers between your accounts show on their day: tap one to change or delete it.", "Activity → By month / All time, and the filter button", Place.ACTIVITY, "sort highest lowest history everything transfer"),
        Item("Seeing your money", "Search and filters", "Search notes, categories, accounts, #tags or amounts; several words find entries with all of them. Filter by account, category or #tag.", "Activity → search or filter buttons", Place.ACTIVITY, "find look up tag hashtag"),
        Item("Seeing your money", "Calendar view", "Each day's spending on a calendar; tap a day to see its entries.", "Activity → calendar button", Place.ACTIVITY, "month days"),
        Item("Seeing your money", "Charts and trends", "Spending and income by category and day, compared with the month before.", "The Insights tab", Place.INSIGHTS, "stats graph analysis report"),
        Item("Seeing your money", "Monthly recap", "A look back at a month or a year.", "Insights → ✦ button", Place.INSIGHTS, "summary review year wrapped"),
        Item("Seeing your money", "PDF statement", "A statement of a month or a year as a PDF to share: income, spending, categories, subscriptions and money owed. Notes are left out unless you add them.", "Insights → download button", Place.INSIGHTS, "export print report pdf"),
        Item("Seeing your money", "Notices", "Updates, what's new, and replies about the app.", "Home → bell", Place.NOTICES, "notifications news whats new"),

        // Accounts
        Item("Accounts", "Accounts and balances", "Bank, cash, cards and wallets, each with its balance. Tap one to see its entries.", "Home → Total balance card", Place.ACCOUNTS, "bank wallet card cash balance"),
        Item("Accounts", "Transfer between accounts", "Move money between your own accounts, such as paying a card bill from the bank, today or on an earlier day. It isn't spending or income, but it shows in Activity on its day.", "Home → Total balance card → ⇄ button", Place.ACCOUNTS, "move money pay card bill withdraw"),
        Item("Accounts", "Accounts in another currency", "An account can be in its own currency; entries in it are converted for your totals.", "Accounts → an account → Currency", Place.ACCOUNTS, "foreign dollar euro travel"),
        Item("Accounts", "Leave an account out", "Keep an account out of the total balance and/or the budget, e.g. savings, or money that isn't yours such as a friend's spends on your card.", "Accounts → an account", Place.ACCOUNTS, "exclude hide savings reimbursement external"),
        Item("Accounts", "Put accounts in order", "Drag accounts into the order you want.", "Accounts → Reorder", Place.ACCOUNTS, "sort arrange drag"),

        // Budgets
        Item("Budgets", "Budget plan", "Monthly, Weeks (every week or two from payday), Dates (one amount for a trip or any dates) or 50/30/20.", "Plan → budget card → Budget plan", Place.BUDGET_PLAN, "monthly weekly payday 50 30 20 needs wants"),
        Item("Budgets", "Envelope budgets", "A Dates budget can count only entries with a #tag like #trip, or from one account, so other spending leaves it alone. With a tag, the add screen has a switch to count an entry in it, so there's no tag to type; the + on the Budget screen starts with it on.", "Plan → Budget plan → Dates", Place.BUDGET_PLAN, "envelope tag trip holiday"),
        Item("Budgets", "Category limits", "A monthly limit for a category, with alerts as you near it.", "Plan → budget card → Category limits", Place.BUDGET, "limit cap alert"),
        Item("Budgets", "Set aside", "Money kept out of what's left to spend, such as for bills still to come.", "Plan → budget card → Set aside", Place.BUDGET, "reserve save"),
        Item("Budgets", "Your own icons", "Make an icon from any picture, such as a shop's logo from a screenshot: tap the camera in an icon picker and frame it. It's kept on your phone to use again for categories, accounts and goals; long-press one to delete it.", "Settings → General → Categories → a category → Icon", Place.CATEGORIES, "logo picture photo custom icon brand shop"),
        Item("Budgets", "Categories", "Add, rename, recolour or remove categories, with an icon each. One you no longer use can be hidden from adding (Show when adding, off) and keeps its entries.", "Settings → General → Categories", Place.CATEGORIES, "category icon colour hide archive old"),

        // Bills, goals and money owed
        Item("Bills, goals and money owed", "Subscriptions and bills", "Regular payments and income, logged for you on their day, with a reminder before bills.", "Plan → Subscriptions", Place.SUBSCRIPTIONS, "netflix rent recurring salary emi"),
        Item("Bills, goals and money owed", "Regular income", "Salary or rent you receive, added on its day like a subscription.", "Plan → Subscriptions → Income", Place.SUBSCRIPTIONS, "salary paycheck recurring income"),
        Item("Bills, goals and money owed", "Savings goals", "Save towards something by a date: see what's needed each month. Add shop links to follow its price.", "Plan → Savings goals", Place.GOALS, "goal save target price"),
        Item("Bills, goals and money owed", "Money owed", "Who owes you and whom you owe. Mark one entry or everything with a person as received or settled.", "Home → Money owed, or Plan → More", Place.DEBTS, "lend borrow settle received friends"),
        Item("Bills, goals and money owed", "When someone pays you back", "A payment from someone who owes you, spotted in an SMS, email or payment app, says they're paying you back: tap ✓ and it's settled in Money owed, not counted as income. Typing it in yourself as income offers the same. Their name can be shorter in the app than in the bank's message.", "Home → Detected payments", Place.HOME, "paid back repay split share received friend income twice duplicate"),
        Item("Accounts", "Your bank's balance", "When a bank SMS says the balance, the account shows it next to its own, and offers to match it if they differ. Only the number is kept.", "Home → Total balance card → Accounts → an account", Place.ACCOUNTS, "bank balance avl bal sms reconcile match"),
        Item("Bills, goals and money owed", "Send it to a friend's Leftovers", "Remind (the bell) sends a message with a link. A friend who also uses Leftovers taps it and what they owe you is added to their Money owed, never twice. The amounts travel in the link itself, not through a server.", "Money owed → a person → bell", Place.DEBTS, "share split link friend send sync together"),
        Item("Bills, goals and money owed", "Get paid back in one tap", "Add your UPI ID, or for friends without UPI a payment link (PayPal.me, Revolut, Wise) or bank details. Reminders then carry them: UPI opens their UPI app with the amount filled in. Only your UPI ID is shared, never a PIN.", "Settings → Getting paid", Place.SETTINGS, "upi vpa paypal revolut wise bank iban pay me back reminder"),
        Item("Bills, goals and money owed", "Share a group split", "On a bill split with people, the share button sends one message to the group: each person's share, and a link where each taps their own name to pay you or add it to their Leftovers.", "Tap a split bill → Share", Place.ACTIVITY, "group split share whatsapp friends dinner trip"),

        // Detecting payments
        Item("Detecting payments", "Bank SMS", "Payments in your bank's text messages are suggested on Home, to add with one tap. Only a fingerprint of each message is kept.", "Settings → Payment detection → Detect bank SMS", Place.SETTINGS, "sms message auto upi"),
        Item("Detecting payments", "Payment app notifications", "Read payments from apps like Google Pay, PhonePe or Paytm, only the apps you choose.", "Settings → Payment detection → Detect payment apps", Place.SETTINGS, "notification gpay phonepe paytm"),
        Item("Detecting payments", "Email", "Find payments in bank and shop emails, over a secure connection to your mailbox.", "Settings → Payment detection → Detect payments in email", Place.SETTINGS, "gmail mail inbox"),
        Item("Detecting payments", "Your bank's words", "If your bank's messages aren't picked up, add the word it uses, and test a message to see what the app makes of it.", "Settings → Payment detection → Detection keywords", Place.SETTINGS, "keyword test message missing"),

        // Import, export and backup
        Item("Import, export and backup", "Import entries", "Bank statements (PDF or CSV), payment app statements, and other apps' exports. You check them before they're added; a wrong import can be removed in one go.", "Settings → Backup & data → Import entries", Place.SETTINGS, "import statement csv pdf phonepe bank other app"),
        Item("Import, export and backup", "Export to CSV", "Every entry as a spreadsheet, for Excel or Sheets.", "Settings → Backup & data → Export to CSV", Place.SETTINGS, "excel sheets spreadsheet"),
        Item("Import, export and backup", "Backups", "Back up and restore, automatically every week to a folder you choose, with a password and photos if you like.", "Settings → Backup & data", Place.SETTINGS, "backup restore save folder password"),
        Item("Import, export and backup", "Sync between devices", "Keep two phones in step through a shared folder or your own WebDAV server.", "Settings → Sync", Place.SETTINGS, "sync nextcloud webdav two phones"),

        // AI assistant
        Item("AI assistant", "Ask and log by chat", "Ask about your money or tell it what to log. Every change waits for your Apply. Connect it with your own key for Claude, ChatGPT, Gemini and others.", "Home → chat button", Place.ASSISTANT, "ai chat claude chatgpt gemini ask"),
        Item("AI assistant", "Bills, statements and goals by chat", "Send a photo of a bill to add it, ask for a PDF statement or a CSV export, or create a savings goal.", "Home → chat button", Place.ASSISTANT, "photo receipt pdf csv goal"),
        Item("AI assistant", "The AI on your phone", "Some phones have an AI built in that works offline with nothing sent anywhere (Dev builds). Check if yours does: \"How to check your phone\" under On this phone.", "Settings → AI assistant → On this phone", Place.SETTINGS, "on device offline gemini nano aicore local"),
        Item("AI assistant", "Privacy", "Notes stay on the phone unless you share them; read-only mode stops all changes. Switch between AIs without typing a key again.", "Settings → AI assistant", Place.SETTINGS, "private key switch notes"),
        Item("AI assistant", "Saved chats", "Chats are kept on the phone to open again and carry on, and switching to another AI keeps the chat. Delete one or all, or stop keeping them.", "Assistant → History", Place.ASSISTANT, "history old chat previous conversation keep delete"),
        Item("AI assistant", "Ask about a file", "Attach a CSV, Excel, PDF or text file with +, and ask about it or to add what's in it. Card and account numbers are hidden before it's sent, and changes wait for Apply.", "Assistant → +", Place.ASSISTANT, "csv pdf excel statement attach document upload"),

        // Look, security and more
        Item("Look, security and more", "Appearance", "Light, dark or automatic; a background (the glow in several colours, plain, or your own photo, blurred) and an accent colour of your choice; glass effects and pure black. Over a photo, liquid or frosted glass: frosted is lighter if scrolling isn't smooth.", "Settings → Appearance", Place.SETTINGS, "theme dark light colour amoled wallpaper background photo accent liquid frosted lag slow smooth"),
        Item("Look, security and more", "Home screen widgets", "Glass, dark or light widgets for the phone's home screen, in any size.", "Settings → Appearance → Home screen widgets", Place.SETTINGS, "widget launcher"),
        Item("Look, security and more", "App lock and screenshots", "Lock with a PIN, password, fingerprint or face, and block screenshots.", "Settings → Security", Place.SETTINGS, "lock pin fingerprint secure privacy screenshot"),
        Item("Look, security and more", "Festival greetings", "A greeting on Home and a notification on the main festivals where you live, worked out on the phone from your currency. They come with notifications on.", "Home, on the day", Place.HOME, "diwali eid christmas holiday new year"),
        Item("Look, security and more", "A note on your week", "Sunday evenings, a short note when there's something to say: spending down, a category that changed, no-spend days. It comes with notifications on.", "Your phone's notifications", null, "weekly summary notification sunday"),
        Item("Look, security and more", "Reminders and alerts", "A daily reminder to log, reminders before bills, and budget alerts.", "Settings → Reminders & alerts", Place.SETTINGS, "notification remind alert"),
        Item("Look, security and more", "Split bills as one entry", "Show a split bill as one entry with its parts under it, or every part on its own.", "Settings → General → Show a split bill as one entry", Place.SETTINGS, "split parts list"),
        Item("Look, security and more", "Currency", "The currency your amounts are shown in.", "Settings → General → Currency", Place.SETTINGS, "rupee dollar symbol"),
        Item("Look, security and more", "Updates and feedback", "Check for a new version, or report a problem and suggest an idea.", "Settings → About", Place.SETTINGS, "update version bug github feedback"),
    )

    val sections: List<String> get() = items.map { it.section }.distinct()

    /** The guide's answer for the AI assistant: the closest entries to [query], each with where to find it. */
    fun forAssistant(query: String, limit: Int = 6): List<Item> {
        val exact = search(query)
        if (exact.isNotEmpty()) return exact.take(limit)
        // No entry has every word: rank by how many of them it has.
        val words = query.lowercase().split(Regex("""\W+""")).filter { it.length > 2 }
        return items.map { i -> i to words.count { "${i.title} ${i.text} ${i.where} ${i.words}".lowercase().contains(it) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

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
