package com.leftovers.app.util

import com.leftovers.app.data.Debt
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Money owed, sent to a friend who also uses Leftovers: a link in the message you send them carries the open entries
 * between you, and their app adds them the other way round (what you lent, they borrowed). Nothing goes through a
 * server: the entries are in the link itself, after the "#", which a browser never sends anywhere.
 */
object SharedOwed {
    /** The page the link opens, which hands the entries to the app (or says where to get it). */
    const val PAGE = "https://saisaharsh3.github.io/Leftovers/add"
    const val SCHEME = "leftovers"
    const val HOST = "owed"
    /** Leftovers Dev's own page and scheme, so a link it sends opens Leftovers Dev, not the real app. */
    const val DEV_PAGE = "https://saisaharsh3.github.io/Leftovers/add-dev"
    const val DEV_SCHEME = "leftovers-dev"

    /** Set when Leftovers Dev starts; its links use [DEV_PAGE]. */
    @Volatile var devBuild = false
    val page: String get() = if (devBuild) DEV_PAGE else PAGE

    /** A link this app opens: the page's own (https, which Android hands to the app), or the page's button's. */
    fun isLink(scheme: String?, host: String?, path: String?): Boolean =
        ((scheme == SCHEME || scheme == DEV_SCHEME) && host == HOST) ||
            (scheme == "https" && host == "saisaharsh3.github.io" && path.orEmpty().startsWith("/Leftovers/add"))

    /**
     * One entry as the sender has it: [amountMinor] above zero when the receiver owes the sender. A share of a group
     * bill carries the bill's [splitTotalMinor] and how many [splitPeople] shared it (0 when it isn't one).
     */
    data class Line(
        val id: String,
        val amountMinor: Long,
        val epochDay: Long,
        val note: String,
        val repayment: Boolean,
        val splitTotalMinor: Long = 0,
        val splitPeople: Int = 0,
        /** In a link shared with a whole group: whose share this is, so each person picks their own. */
        val name: String = "",
    )

    /**
     * How the sender would like to be paid: a UPI ID and the name it's under, and for people without UPI a
     * payment link (PayPal.me, Revolut, Wise...) and/or bank details to copy. Sent only when set, and only for money
     * the receiver owes. Never anything that could move money out: no PIN, password or card number has a place here.
     */
    data class Pay(
        val upi: String = "",
        val name: String = "",
        val link: String = "",
        val bank: String = "",
        /** The way the sender would rather be paid: [UPI], [LINK] or [BANK]; blank picks UPI where it works. */
        val first: String = "",
    ) {
        val isEmpty: Boolean get() = upi.isBlank() && link.isBlank() && bank.isBlank()

        /**
         * The ways a friend can pay, the sender's choice first and the others after it for anyone who doesn't use
         * that one. UPI only for rupees.
         */
        fun ways(currency: String = "INR"): List<String> {
            val all = listOfNotNull(
                UPI.takeIf { upi.isNotBlank() && (currency.isBlank() || currency == "INR") },
                LINK.takeIf { link.isNotBlank() },
                BANK.takeIf { bank.isNotBlank() },
            )
            return all.sortedBy { if (it == first) 0 else 1 }
        }

        /** How many ways are set up, whatever the currency: a first choice only matters with two or more. */
        val count: Int get() = listOf(upi, link, bank).count { it.isNotBlank() }

        companion object {
            const val UPI = "upi"
            const val LINK = "link"
            const val BANK = "bank"
        }

        /** The payment link's site, to say where it goes: "paypal.me". */
        val linkHost: String get() = runCatching { java.net.URI(link).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    }

    data class Shared(val currency: String, val lines: List<Line>, val pay: Pay? = null, val group: Boolean = false) {
        /** In a group link: everyone's name, in the order sent. */
        val names: List<String> get() = lines.map { it.name }.filter { it.isNotBlank() }.distinct()

        /** Only [name]'s share, as if the link had been sent to them alone. */
        fun forName(name: String): Shared = copy(lines = lines.filter { it.name == name }, group = false)

        /** What the receiver owes in all (above zero) or is owed (below). */
        val netMinor: Long get() = lines.sumOf { it.amountMinor }
    }

    /**
     * The link for [debts], the open entries with one person. Each entry keeps one id however often it's sent (and
     * the id it came with, when it was received from them), so nobody gets the same entry twice.
     */
    fun link(device: String, currency: String, debts: List<Debt>, pay: Pay? = null, splits: Map<Long, DebtReminder.Split> = emptyMap()): String? {
        val lines = debts.filter { it.amountMinor != 0L }.take(40)
        if (lines.isEmpty()) return null
        val json = JSONObject().put("v", 1).put("c", currency).put("e", JSONArray(lines.map { d ->
            JSONArray().put(idOf(device, d)).put(d.amountMinor).put(d.epochDay).put(d.note.trim().take(60)).put(if (d.repayment) 1 else 0).apply {
                // A share of a group bill: the bill's total and the head count, so they see what their share is of.
                d.splitOf?.let { splits[it] }?.takeIf { it.people > 1 }?.let { put(it.totalMinor).put(it.people) }
            }
        }))
        // Payment details only when they owe you: there's nothing for them to pay otherwise.
        val clean = pay?.let { clean(it) }
        if (clean != null && !clean.isEmpty && lines.sumOf { it.amountMinor } > 0) {
            json.put("p", JSONObject().apply {
                if (clean.upi.isNotEmpty()) put("u", clean.upi)
                if (clean.name.isNotEmpty()) put("n", clean.name)
                if (clean.link.isNotEmpty()) put("l", clean.link)
                if (clean.bank.isNotEmpty()) put("b", clean.bank)
                if (clean.first.isNotEmpty()) put("f", clean.first)
            })
        }
        return "$page#" + encode(json.toString())
    }

    /**
     * One link for a whole group a bill was split with, to send to the group's chat: each open share with the
     * person's name, the bill's total and head count, and how to pay you. Each friend picks their own name and gets
     * only their share. Settled shares and anything else about you stay out.
     */
    fun groupLink(device: String, currency: String, shares: List<Debt>, split: DebtReminder.Split, pay: Pay? = null): String? {
        val open = shares.filter { it.amountMinor > 0 && !it.settled && !it.repayment }.take(40)
        if (open.isEmpty()) return null
        val json = JSONObject().put("v", 1).put("c", currency).put("g", 1).put("e", JSONArray(open.map { d ->
            JSONArray().put(idOf(device, d)).put(d.amountMinor).put(d.epochDay).put(d.note.trim().take(60)).put(0)
                .put(split.totalMinor).put(split.people).put(d.person.trim().take(30))
        }))
        val clean = pay?.let { clean(it) }
        if (clean != null && !clean.isEmpty) {
            json.put("p", JSONObject().apply {
                if (clean.upi.isNotEmpty()) put("u", clean.upi)
                if (clean.name.isNotEmpty()) put("n", clean.name)
                if (clean.link.isNotEmpty()) put("l", clean.link)
                if (clean.bank.isNotEmpty()) put("b", clean.bank)
                if (clean.first.isNotEmpty()) put("f", clean.first)
            })
        }
        return "$page#" + encode(json.toString())
    }

    /**
     * What goes after the "#": the entries as JSON in URL-safe base64, or compressed first ("z" in front) when that's
     * shorter, as it is for a group, whose shares repeat the same bill. Plain base64 of JSON never starts with "z".
     */
    internal fun encode(json: String): String {
        val raw = json.toByteArray()
        val plain = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val deflater = java.util.zip.Deflater(9, true)
        val packed = try {
            deflater.setInput(raw)
            deflater.finish()
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
            out.toByteArray()
        } finally {
            deflater.end()
        }
        val z = "z" + Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
        return if (z.length < plain.length) z else plain
    }

    /** The JSON back from [encode]'s text, capped so a crafted link can't unpack into something huge. */
    internal fun decode(code: String): String {
        if (!code.startsWith("z")) return String(Base64.getUrlDecoder().decode(code))
        val inflater = java.util.zip.Inflater(true)
        try {
            inflater.setInput(Base64.getUrlDecoder().decode(code.substring(1)))
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > 64 * 1024) throw IllegalArgumentException("Too big")
            }
            return out.toString("UTF-8")
        } finally {
            inflater.end()
        }
    }

    fun idOf(device: String, debt: Debt): String =
        debt.sharedId ?: (device.takeLast(6) + "." + debt.createdAt.toString(36))

    /** Reads a link, the app's own (leftovers://owed?d=…) or the page's (…/add#…); null when it isn't one. */
    fun parse(text: String): Shared? = runCatching {
        val code = text.trim().let { t ->
            when {
                "d=" in t.substringAfter('?', "") -> t.substringAfter("d=").substringBefore('&')
                '#' in t -> t.substringAfter('#')
                else -> t
            }
        }
        val json = JSONObject(decode(code))
        if (json.optInt("v") != 1) return null
        val entries = json.getJSONArray("e")
        val lines = (0 until minOf(entries.length(), 40)).map { i ->
            val e = entries.getJSONArray(i)
            Line(
                e.getString(0).take(40), e.getLong(1), e.getLong(2), e.optString(3).take(60), e.optInt(4) == 1,
                splitTotalMinor = e.optLong(5).coerceAtLeast(0), splitPeople = e.optInt(6).coerceIn(0, 99),
                name = e.optString(7).replace(Regex("[\\p{Cntrl}]"), "").trim().take(30),
            )
        }.filter { it.id.isNotBlank() && it.amountMinor != 0L }
        val pay = json.optJSONObject("p")?.let { p -> clean(Pay(p.optString("u"), p.optString("n"), p.optString("l"), p.optString("b"), p.optString("f"))) }?.takeIf { !it.isEmpty }
        val group = json.optInt("g") == 1 && lines.all { it.name.isNotBlank() }
        if (lines.isEmpty()) null else Shared(json.optString("c").take(3).uppercase(), lines, pay, group)
    }.getOrNull()

    /** A UPI ID as banks issue them: "name@bank", letters, digits, dots, dashes and underscores. */
    fun isUpi(text: String): Boolean = Regex("^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,30}$").matches(text.trim())

    /** A payment link: https only (never a script or an app's own scheme), and short. "paypal.me/you" gets its https. */
    fun payLink(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val full = if ("://" in t) t else "https://$t"
        val uri = runCatching { java.net.URI(full) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank() || '.' !in uri.host || full.length > 200) return null
        return full
    }

    /**
     * The payment link with [amountMinor] filled in, where the service says how: PayPal.me takes the amount and its
     * currency after the name, and Monzo.me (pounds only) the amount. Any other link, or one that already has an
     * amount, is opened as it is, and they type the amount there.
     */
    fun linkWithAmount(link: String, amountMinor: Long, currency: String): String {
        if (amountMinor <= 0) return link
        val uri = runCatching { java.net.URI(link) }.getOrNull() ?: return link
        val host = uri.host.orEmpty().lowercase().removePrefix("www.")
        val parts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        // Just a name: nothing filled in yet, and no query to keep.
        if (parts.size != 1 || uri.rawQuery != null || uri.rawFragment != null) return link
        val amount = java.math.BigDecimal.valueOf(amountMinor, 2).let { if (amountMinor % 100 == 0L) it.setScale(0) else it }.toPlainString()
        val code = currency.uppercase().takeIf { it.length == 3 && it.all(Char::isLetter) }
        return when {
            host == "paypal.me" && code != null -> "https://paypal.me/${parts[0]}/$amount$code"
            host == "monzo.me" && code == "GBP" -> "https://monzo.me/${parts[0]}/$amount"
            else -> link
        }
    }

    /** Whether [linkWithAmount] fills in the amount for this link. */
    fun fillsAmount(link: String, currency: String): Boolean = linkWithAmount(link, 100, currency) != link

    /** Only what's valid survives, from the user's own fields or from a link someone sent. */
    fun clean(pay: Pay): Pay = Pay(
        upi = pay.upi.trim().takeIf { isUpi(it) }.orEmpty(),
        name = pay.name.trim().replace(Regex("[\\p{Cntrl}]"), "").take(40),
        link = payLink(pay.link).orEmpty(),
        bank = pay.bank.trim().replace(Regex("[\\p{Cntrl}&&[^\\n]]"), "").lines().map { it.trim() }.filter { it.isNotEmpty() }.take(4)
            .joinToString("\n").take(140),
        first = pay.first.takeIf { it == Pay.UPI || it == Pay.LINK || it == Pay.BANK }.orEmpty(),
    )

    /**
     * The address that opens the phone's UPI app (Google Pay, PhonePe, Paytm, BHIM...) with the payment filled in:
     * to [pay]'s UPI ID, for [amountMinor] rupees. Null without a UPI ID or when the amount isn't in rupees.
     */
    fun upiUri(pay: Pay, amountMinor: Long, currency: String, note: String = "Leftovers"): String? {
        if (pay.upi.isBlank() || amountMinor <= 0 || (currency.isNotBlank() && currency != "INR")) return null
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val name = pay.name.ifBlank { pay.upi.substringBefore('@') }
        val amount = "%d.%02d".format(amountMinor / 100, amountMinor % 100)
        return "upi://pay?pa=${enc(pay.upi)}&pn=${enc(name)}&am=$amount&cu=INR&tn=${enc(note.take(40))}"
    }

    /** [line] as the receiver's own entry with [person], who sent it: the other way round, and in no account. */
    /** The shares in [lines] that the receiver owes for a bill (not money lent, nor a payback): their own spending. */
    fun shares(lines: List<Line>): List<Line> = lines.filter { it.amountMinor > 0 && !it.repayment }

    /**
     * The receiver's own share of each bill as their spending, when they choose to add it there too: on its day, in
     * [categoryId], with what it was a share of. No account: [person] paid, so no money left yours yet; it does when
     * you pay them back, so it's counted once.
     */
    fun spending(lines: List<Line>, person: String, categoryId: Long, format: (Long) -> String): List<com.leftovers.app.data.Transaction> =
        shares(lines).map { l ->
            com.leftovers.app.data.Transaction(
                amountMinor = l.amountMinor,
                type = com.leftovers.app.data.TxType.EXPENSE,
                categoryId = categoryId,
                epochDay = l.epochDay,
                note = l.note.trim().ifBlank { "Split with ${person.trim()}" },
                details = if (l.splitPeople > 1) "Your share of ${format(l.splitTotalMinor)}, split ${l.splitPeople} ways · paid by ${person.trim()}"
                else "Paid by ${person.trim()}",
            )
        }

    fun mirror(line: Line, person: String): Debt =
        Debt(person = person.trim(), amountMinor = -line.amountMinor, note = line.note, epochDay = line.epochDay, repayment = line.repayment, sharedId = line.id)
}
