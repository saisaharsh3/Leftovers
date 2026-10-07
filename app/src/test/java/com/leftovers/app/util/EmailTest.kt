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

    @Test fun skipsPromotionsCodesRefundsAndBills() {
        assertNull(EmailParser.parse("50% off today!", "Shop", "Get 50% off on orders above ₹499. Order now."))
        assertNull(EmailParser.parse("OTP", "Bank", "Your OTP for the transaction of Rs 500 is 123456"))
        assertNull(EmailParser.parse("Refund processed", "Shop", "Rs 300 has been credited as a refund"))
        assertNull(EmailParser.parse("Card bill", "Bank", "Minimum due Rs 1,000. Payment due date 20 Oct"))
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

    @Test fun findsOnlyPaymentsAndLeavesMailUnread() {
        val user = mail.setUser("me@example.com", "me", "secret")
        user.deliver(message("\"HDFC Bank\" <alerts@hdfcbank.net>", "Transaction alert") {
            setText("Rs.250.00 debited from A/c XX1234 to VPA zomato@icici on 07-10-26.")
        })
        user.deliver(message("Shop <deals@shop.com>", "Big sale") {
            setText("Get 50% off on every order above ₹999. Order now!")
        })
        user.deliver(message("Friend <friend@example.com>", "Dinner?") {
            setText("Are we still on for dinner tonight?")
        })
        user.deliver(message("\"Swiggy Orders\" <noreply@swiggy.in>", "Your order is confirmed") {
            setContent(
                MimeMultipart("alternative").apply {
                    addBodyPart(MimeBodyPart().apply { setText("Order total: Rs 432.50. Paid via UPI.", "UTF-8") })
                    addBodyPart(MimeBodyPart().apply { setContent("<p>Order total: <b>&#8377;432.50</b></p>", "text/html; charset=UTF-8") })
                },
            )
        })

        val store = session.getStore("imap")
        store.connect("127.0.0.1", ServerSetupTest.IMAP.port, "me", "secret")
        val found = store.use { EmailChecker.findPayments(it, System.currentTimeMillis() - 60_000) }

        assertEquals(listOf(25_000L, 43_250L), found.map { it.amountMinor }.sorted())
        assertEquals(setOf("Zomato", "Swiggy"), found.map { it.merchant }.toSet())

        // Nothing was marked as read.
        session.getStore("imap").use { s ->
            s.connect("127.0.0.1", ServerSetupTest.IMAP.port, "me", "secret")
            val inbox = s.getFolder("INBOX").apply { open(Folder.READ_ONLY) }
            inbox.messages.forEach { assertFalse(it.isSet(Flags.Flag.SEEN)) }
        }
    }
}
