package com.leftovers.app.util

/**
 * Decides whether an email comes from a bank, card or payment service. Only those are read for payments;
 * shops, social networks, newsletters and everything else are skipped without opening the message.
 */
object BankSenders {
    /** Banks, cards and payment apps, matched against the sender's domain. */
    private val domains = listOf(
        // India
        "hdfcbank", "icicibank", "sbi.co.in", "onlinesbi", "sbicard", "axisbank", "kotak", "yesbank", "idfcfirstbank",
        "indusind", "pnb", "bankofbaroda", "canarabank", "unionbankofindia", "federalbank", "rblbank", "aubank",
        "bandhanbank", "idbibank", "iob.in", "bankofindia", "centralbank", "ucobank", "indianbank", "southindianbank",
        "kvb.co.in", "cityunionbank", "dbs.com", "sc.com", "hsbc", "citi", "americanexpress", "aexp", "onecard",
        "sliceit", "paytm", "phonepe", "mobikwik", "amazonpay", "cred.club", "jupiter.money", "fi.money", "niyo",
        // Elsewhere
        "chase", "bankofamerica", "bofa", "wellsfargo", "capitalone", "discover", "usbank", "pnc.com", "barclays",
        "lloydsbank", "natwest", "santander", "monzo", "revolut", "starlingbank", "wise.com", "n26", "paypal",
        "venmo", "cash.app", "apple.com/card", "commbank", "westpac", "anz", "nab.com.au", "rbc", "td.com",
        "scotiabank", "bmo", "dbs", "ocbc", "uob", "maybank",
    )

    /** Generic signs of a bank sender, for banks not in the list above. */
    private val bankish = Regex("""\b(bank|banking|card(s)?|credit ?card|debit ?card|upi|netbanking|insta ?alerts?)\b""", RegexOption.IGNORE_CASE)

    /** Never banks, even if they mention cards or payments. */
    private val notBanks = listOf(
        "linkedin", "facebook", "instagram", "twitter", "x.com", "google.com", "youtube", "quora", "medium",
        "substack", "naukri", "indeed", "glassdoor", "swiggy", "zomato", "amazon.in", "amazon.com", "flipkart",
        "myntra", "uber", "ola", "netflix", "spotify", "airbnb", "booking.com", "makemytrip",
    )

    fun isBank(address: String, displayName: String): Boolean {
        val domain = address.substringAfter('@', "").lowercase()
        if (domain.isEmpty()) return false
        if (notBanks.any { domain == it || domain.endsWith(".$it") || domain.contains(it) }) return false
        if (domains.any { domain.contains(it) }) return true
        return bankish.containsMatchIn(displayName) || bankish.containsMatchIn(domain.replace('.', ' '))
    }
}
