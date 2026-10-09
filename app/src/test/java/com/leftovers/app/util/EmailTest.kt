package com.leftovers.app.util

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Properties

class EmailParserTest {
    @Test fun bankAlert() {
        val p = EmailParser.parse("Transaction alert", "HDFC Bank", "Rs.1,250.00 debited from A/c XX1234 to VPA swiggy@icici on 07-10-26.")!!
        assertEquals(125_000L, p.amountMinor)
        assertEquals("Swiggy", p.merchant)
    }

    @Test fun shopReceiptUsesTheTotalAndTheSender() {
        val body = EmailParser.htmlToText(
            "<html><body><p>Thanks for your order!</p><table><tr><td>Item</td><td>&#8377;450.00</td></tr>" +
                "<tr><td>Order Total:</td><td>&#8377;499.00</td></tr></table></body></html>",
        )
        val p = EmailParser.parse("Your order has been placed", "Swiggy Orders", body)!!
        assertEquals(49_900L, p.amountMinor)
        assertEquals("Swiggy", p.merchant)
    }

    @Test fun otherCurrencies() {
        assertEquals(1_299L, EmailParser.parse("Receipt", "Spotify", "Amount paid: $12.99")!!.amountMinor)
        assertEquals(850L, EmailParser.parse("Your receipt", "Uber", "Total €8.50 charged to your card")!!.amountMinor)
    }

    @Test fun skipsPromotionsCodesAndBills() {
        assertNull(EmailParser.parse("50% off today!", "Shop", "Get 50% off on orders above ₹499. Order now."))
        assertNull(EmailParser.parse("OTP", "Bank", "Your OTP for the transaction of Rs 500 is 123456"))
        assertNull(EmailParser.parse("Card bill", "Bank", "Minimum due Rs 1,000. Payment due date 20 Oct"))
    }

    @Test fun moneyInIsIncome() {
        val refund = EmailParser.parse("Refund processed", "Shop", "Rs 300 has been credited as a refund")
        assertEquals(30_000L, refund?.amountMinor)
        assertEquals(true, refund?.isIncome)
        val salary = EmailParser.parse("Credit alert", "HDFC Bank", "INR 75,000.00 credited to your A/c XX1234 from ACME CORP on 01-10-26")
        assertEquals(7_500_000L, salary?.amountMinor)
        assertEquals("Acme Corp", salary?.merchant)
    }

    @Test fun htmlBecomesText() {
        assertEquals("Hi\nTotal: ₹99", EmailParser.htmlToText("<style>p{}</style><p>Hi</p><b>Total:</b>&nbsp;&#8377;99"))
    }
}

/** End to end against a throwaway local mail server. */
class EmailCheckerTest {
    private lateinit var mail: GreenMail
    private val session = Session.getInstance(Properties())

    @Before fun start() {
        mail = GreenMail(ServerSetupTest.IMAP).apply { start() }
    }

    @After fun stop() = mail.stop()

    private fun message(from: String, subject: String, configure: MimeMessage.() -> Unit) =
        MimeMessage(session).apply {
            setFrom(InternetAddress(from))
            setRecipients(Message.RecipientType.TO, "me@example.com")
            setSubject(subject)
            sentDate = java.util.Date()
            configure()
            saveChanges()
        }

    @Test fun findsOnlyBankPaymentsAndLeavesMailUnread() {
        val user = mail.setUser("me@example.com", "me", "secret")
        // Bank alerts: these count.
        user.deliver(message("\"HDFC Bank InstaAlerts\" <alerts@hdfcbank.net>", "Transaction alert") {
            setText("Rs.250.00 debited from A/c XX1234 to VPA zomato@icici on 07-10-26.")
        })
        user.deliver(message("\"ICICI Bank Credit Card\" <credit_cards@icicibank.com>", "Transaction on your card") {
            setContent(
                MimeMultipart("alternative").apply {
                    addBodyPart(MimeBodyPart().apply { setText("INR 432.50 spent on ICICI Bank Card XX9876 at Swiggy on 07-Oct-26.", "UTF-8") })
                    addBodyPart(MimeBodyPart().apply { setContent("<p>INR <b>432.50</b> spent at Swiggy</p>", "text/html; charset=UTF-8") })
                },
            )
        })
        // Not banks, or not payments: all skipped.
        user.deliver(message("\"Swiggy Orders\" <noreply@swiggy.in>", "Your order is confirmed") {
            setText("Order total: Rs 432.50. Paid via UPI.")
        })
        user.deliver(message("LinkedIn <jobs-noreply@linkedin.com>", "Jobs paying ₹25,00,000 a year") {
            setText("New jobs for you. Salary up to ₹25,00,000. Payment of relocation bonus.")
        })
        user.deliver(message("\"HDFC Bank\" <offers@hdfcbank.net>", "Cashback offer") {
            setText("Get 10% cashback offer on card spends above Rs 5,000. Apply now!")
        })
        user.deliver(message("\"Axis Bank\" <loans@axisbank.com>", "Pre-approved loan") {
            setText("You are eligible for a pre-approved loan of Rs 5,00,000. Payment in easy EMIs.")
        })
        user.deliver(message("Friend <friend@example.com>", "Dinner?") {
            setText("I paid Rs 800 for dinner, send me half?")
        })

        val store = session.getStore("imap")
        store.connect("127.0.0.1", ServerSetupTest.IMAP.port, "me", "secret")
        val found = store.use { EmailChecker.findPayments(it, System.currentTimeMillis() - 60_000) }

        assertEquals(listOf(25_000L, 43_250L), found.map { it.amountMinor }.sorted())
        assertEquals(setOf("Zomato", "Swiggy"), found.map { it.merchant }.toSet())
        assertEquals(setOf("HDFC Bank InstaAlerts", "ICICI Bank Credit Card"), found.map { it.sender }.toSet())

        // Nothing was marked as read.
        session.getStore("imap").use { s ->
            s.connect("127.0.0.1", ServerSetupTest.IMAP.port, "me", "secret")
            val inbox = s.getFolder("INBOX").apply { open(Folder.READ_ONLY) }
            inbox.messages.forEach { assertFalse(it.isSet(Flags.Flag.SEEN)) }
        }
    }
}

class BankSendersTest {
    @Test fun banksCardsAndPaymentApps() {
        assertTrue(BankSenders.isBank("alerts@hdfcbank.net", "HDFC Bank InstaAlerts"))
        assertTrue(BankSenders.isBank("credit_cards@icicibank.com", ""))
        assertTrue(BankSenders.isBank("no-reply@alerts.chase.com", "Chase"))
        assertTrue(BankSenders.isBank("noreply@paytm.com", "Paytm"))
        // Unknown bank, but clearly one.
        assertTrue(BankSenders.isBank("alerts@mylocalcoop.org", "Coop Bank Alerts"))
    }

    @Test fun everythingElse() {
        assertFalse(BankSenders.isBank("jobs-noreply@linkedin.com", "LinkedIn"))
        assertFalse(BankSenders.isBank("noreply@swiggy.in", "Swiggy Orders"))
        assertFalse(BankSenders.isBank("auto-confirm@amazon.in", "Amazon.in"))
        assertFalse(BankSenders.isBank("news@substack.com", "Weekly money newsletter"))
        assertFalse(BankSenders.isBank("friend@example.com", "Friend"))
        // A shop's name can't make it look like a bank.
        assertFalse(BankSenders.isBank("offers@flipkart.com", "Flipkart Credit Card offers"))
    }
}
