package com.leftovers.app.util

import com.leftovers.app.data.Debt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Money owed sent to a friend's Leftovers: the link carries the entries, and they arrive the other way round. */
class SharedOwedTest {
    private val lent = Debt(id = 4, person = "Asha", amountMinor = 25_000, note = "Dinner", epochDay = 20_760, createdAt = 1_700_000_000_000)
    private val paidBack = Debt(id = 5, person = "Asha", amountMinor = -5_000, note = "", epochDay = 20_762, createdAt = 1_700_000_500_000, repayment = true)

    @Test
    fun `a link carries the entries and reads back the same`() {
        val link = SharedOwed.link("pixel-8-abc234", "INR", listOf(lent, paidBack))!!
        assertTrue(link.startsWith(SharedOwed.PAGE + "#"))
        val shared = SharedOwed.parse(link)!!
        assertEquals("INR", shared.currency)
        assertEquals(2, shared.lines.size)
        assertEquals(20_000, shared.netMinor)
        assertEquals("Dinner", shared.lines[0].note)
        assertTrue(shared.lines[1].repayment)
    }

    @Test
    fun `the app's own link reads the same as the page's`() {
        val code = SharedOwed.link("pixel-8-abc234", "INR", listOf(lent))!!.substringAfter('#')
        assertEquals(SharedOwed.parse("${SharedOwed.PAGE}#$code"), SharedOwed.parse("leftovers://owed?d=$code"))
    }

    @Test
    fun `what was lent arrives as borrowed, under the sender's name`() {
        val line = SharedOwed.parse(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent))!!)!!.lines.single()
        val mine = SharedOwed.mirror(line, " Ravi ")
        assertEquals("Ravi", mine.person)
        assertEquals(-25_000, mine.amountMinor)
        assertEquals(20_760, mine.epochDay)
        assertNull(mine.accountId)
        assertNotNull(mine.sharedId)
    }

    @Test
    fun `an entry keeps one id however often it is sent, and the id it came with`() {
        val first = SharedOwed.parse(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent))!!)!!.lines.single().id
        val again = SharedOwed.parse(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent, paidBack))!!)!!.lines.first().id
        assertEquals(first, again)
        // Sent back by the one who received it: still the same id, so the first phone doesn't add its own entry.
        val received = SharedOwed.mirror(SharedOwed.Line(first, 25_000, 20_760, "Dinner", false), "Ravi")
        assertEquals(first, SharedOwed.idOf("other-phone-zzz999", received))
    }

    @Test
    fun `the link opens in the app, and so does the page's button, but nothing else`() {
        assertTrue(SharedOwed.isLink("https", "saisaharsh3.github.io", "/Leftovers/add"))
        assertTrue(SharedOwed.isLink("https", "saisaharsh3.github.io", "/Leftovers/add.html"))
        assertTrue(SharedOwed.isLink("leftovers", "owed", ""))
        assertTrue(!SharedOwed.isLink("https", "saisaharsh3.github.io", "/Leftovers/"))
        assertTrue(!SharedOwed.isLink("https", "example.com", "/Leftovers/add"))
        assertTrue(!SharedOwed.isLink("http", "saisaharsh3.github.io", "/Leftovers/add"))
    }

    @Test
    fun `nothing to send and anything that isn't a link give nothing`() {
        assertNull(SharedOwed.link("pixel-8-abc234", "INR", emptyList()))
        assertNull(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent.copy(amountMinor = 0))))
        assertNull(SharedOwed.parse("https://example.com/"))
        assertNull(SharedOwed.parse("leftovers://owed?d=not-a-link"))
        assertNull(SharedOwed.parse(""))
    }

    private val pay = SharedOwed.Pay(upi = "asha.k@okaxis", name = "Asha K", link = "paypal.me/ashak", bank = "IBAN DE89 3704 0044 0532 0130 00")

    /** The link's JSON, to see exactly what goes to the other person. */
    private fun json(link: String) = org.json.JSONObject(SharedOwed.decode(link.substringAfter('#')))

    @Test
    fun `how to pay you travels with the link, when they owe you`() {
        val shared = SharedOwed.parse(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent), pay)!!)!!
        assertEquals("asha.k@okaxis", shared.pay!!.upi)
        assertEquals("Asha K", shared.pay!!.name)
        assertEquals("https://paypal.me/ashak", shared.pay!!.link)
        assertEquals("paypal.me", shared.pay!!.linkHost)
        assertTrue(shared.pay!!.bank.startsWith("IBAN"))
    }

    @Test
    fun `the link carries only what the other person needs`() {
        val j = json(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent), SharedOwed.Pay(upi = "asha.k@okaxis"))!!)
        assertEquals(setOf("v", "c", "e", "p"), j.keys().asSequence().toSet())
        // Only the field that was filled in.
        assertEquals(setOf("u"), j.getJSONObject("p").keys().asSequence().toSet())
        // Not the phone's name: each entry's id has only the random tag.
        assertTrue(j.getJSONArray("e").getJSONArray(0).getString(0).startsWith("abc234."))
        assertTrue("pixel" !in j.toString())
    }

    @Test
    fun `no payment details when they don't owe you, or none are set`() {
        assertTrue(json(SharedOwed.link("pixel-8-abc234", "INR", listOf(paidBack), pay)!!).has("p").not())
        assertTrue(json(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent), SharedOwed.Pay())!!).has("p").not())
        assertTrue(json(SharedOwed.link("pixel-8-abc234", "INR", listOf(lent), null)!!).has("p").not())
    }

    @Test
    fun `only safe payment details are kept, sent or received`() {
        val bad = SharedOwed.clean(SharedOwed.Pay(upi = "not a upi", link = "javascript:alert(1)", name = "AshaK"))
        assertTrue(bad.upi.isEmpty())
        assertTrue(bad.link.isEmpty())
        assertEquals("AshaK", bad.name)
        assertNull(SharedOwed.payLink("http://paypal.me/x"))
        assertNull(SharedOwed.payLink("upi://pay?pa=x@y"))
        assertEquals("https://revolut.me/asha", SharedOwed.payLink("revolut.me/asha"))
        assertTrue(SharedOwed.isUpi("9876543210@ybl"))
    }

    @Test
    fun `the UPI address opens a UPI app with the amount, in rupees only`() {
        assertEquals(
            "upi://pay?pa=asha.k%40okaxis&pn=Asha%20K&am=250.00&cu=INR&tn=Leftovers",
            SharedOwed.upiUri(pay, 25_000, "INR"),
        )
        assertNull(SharedOwed.upiUri(pay, 25_000, "EUR"))
        assertNull(SharedOwed.upiUri(SharedOwed.Pay(link = "https://paypal.me/a"), 25_000, "INR"))
    }

    @Test
    fun `the reminder says how to pay, for anyone who doesn't open the link`() {
        val lines = DebtReminder.payLines(pay)
        assertTrue("Pay by UPI: asha.k@okaxis" in lines)
        assertTrue("Or pay here: https://paypal.me/ashak" in lines)
        assertTrue("Bank: IBAN" in lines)
        assertEquals("", DebtReminder.payLines(SharedOwed.Pay()))
    }

    @Test
    fun `a group link carries each open share by name, and each person gets only theirs`() {
        val shares = listOf(
            Debt(id = 10, person = "Rahul", amountMinor = 75_000, note = "Dinner", epochDay = 20_768, createdAt = 1, splitOf = 9),
            Debt(id = 11, person = "Priya", amountMinor = 75_000, note = "Dinner", epochDay = 20_768, createdAt = 2, splitOf = 9),
            Debt(id = 12, person = "Nick", amountMinor = 75_000, note = "Dinner", epochDay = 20_768, createdAt = 3, splitOf = 9, settled = true),
        )
        val shared = SharedOwed.parse(SharedOwed.groupLink("pixel-8-abc234", "INR", shares, DebtReminder.Split(300_000, 4), SharedOwed.Pay(upi = "asha.k@okaxis"))!!)!!
        assertTrue(shared.group)
        // Nick has paid: left out.
        assertEquals(listOf("Rahul", "Priya"), shared.names)
        val mine = shared.forName("Priya")
        assertEquals(1, mine.lines.size)
        assertEquals(75_000, mine.netMinor)
        assertEquals(300_000, mine.lines[0].splitTotalMinor)
        assertEquals(4, mine.lines[0].splitPeople)
        assertEquals("asha.k@okaxis", mine.pay!!.upi)
    }

    @Test
    fun `the group message lists who still owes what`() {
        val shares = listOf(
            Debt(person = "Rahul", amountMinor = 75_000, note = "Dinner", splitOf = 9),
            Debt(person = "Nick", amountMinor = 75_000, note = "Dinner", splitOf = 9, settled = true),
        )
        val rupees: (Long) -> String = { "₹" + java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(it / 100) }
        assertEquals("Dinner: ₹3,000, split 4 ways.\n\nRahul: ₹750", DebtReminder.groupMessage("Dinner", DebtReminder.Split(300_000, 4), shares, rupees))
    }

    @Test
    fun `a reminder for one share of a group bill says it was split`() {
        val rupees: (Long) -> String = { "₹" + java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(it / 100) }
        val b = com.leftovers.app.data.PersonBalance("Rahul Verma", 75_000, listOf(Debt(person = "Rahul Verma", amountMinor = 75_000, note = "Dinner", splitOf = 9)))
        assertEquals(
            "Hi Rahul, a quick reminder: your share of Dinner is ₹750 (a ₹3,000 bill split 4 ways). Thanks!",
            DebtReminder.message(b, rupees, mapOf(9L to DebtReminder.Split(300_000, 4))),
        )
    }

    @Test
    fun `a group's link is compressed, much shorter, and reads back the same`() {
        val names = listOf("Rohit", "Anil", "Rahul", "Koushik")
        val shares = names.mapIndexed { i, n -> Debt(id = 20L + i, person = n, amountMinor = 20_000, note = "Food & Dining", epochDay = 20_768, createdAt = 1_794_000_000_000 + i * 997L, splitOf = 7) }
        val link = SharedOwed.groupLink("pixel-8-393t4e", "EUR", shares, DebtReminder.Split(100_000, 5), SharedOwed.Pay(upi = "asha.k@okaxis"))!!
        val code = link.substringAfter('#')
        assertTrue(code.startsWith("z"))
        val plain = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(SharedOwed.decode(code).toByteArray())
        assertTrue(code.length * 10 < plain.length * 6)
        assertEquals(names, SharedOwed.parse(link)!!.names)
    }

    @Test
    fun `a short link stays plain, and links from before still open`() {
        val code = SharedOwed.link("pixel-8-abc234", "INR", listOf(lent))!!.substringAfter('#')
        assertTrue(code.startsWith("ey"))
        // Written the way the app wrote links before compression.
        val old = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"v":1,"c":"INR","e":[["abc234.x1",25000,20760,"Dinner",0]]}""".toByteArray())
        assertEquals(25_000, SharedOwed.parse("${SharedOwed.PAGE}#$old")!!.netMinor)
    }

    @Test
    fun `UPI is offered only for rupees`() {
        assertEquals("", DebtReminder.payLines(SharedOwed.Pay(upi = "asha.k@okaxis"), "EUR"))
        assertTrue("Pay here: https://paypal.me/a" in DebtReminder.payLines(SharedOwed.Pay(upi = "asha.k@okaxis", link = "paypal.me/a"), "EUR"))
    }
}
