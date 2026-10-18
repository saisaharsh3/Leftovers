package com.leftovers.app.util

import androidx.test.core.app.ApplicationProvider
import com.leftovers.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** PDF bank statements, read on the phone, with and without a password. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfStatementTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/statements/$name")!!.readBytes()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun readsAStatementWithWithdrawalAndDepositColumns() {
        val p = PdfStatement.parse(PdfStatement.text(context, bytes("statement.pdf")))
        assertEquals(0, p.unreadable)
        assertEquals(5, p.rows.size)
        val (swiggy, salary, atm, amazon, refund) = p.rows
        assertEquals(LocalDate.of(2026, 10, 1), swiggy.date)
        assertEquals(45_000L, swiggy.amountMinor)
        assertEquals(TxType.EXPENSE, swiggy.type)
        assertTrue(swiggy.note.startsWith("UPI-SWIGGY"))
        assertEquals(TxType.INCOME, salary.type)
        assertEquals(7_500_000L, salary.amountMinor)
        assertEquals(TxType.EXPENSE, atm.type)
        // The description went on to a second line.
        assertTrue(amazon.note, amazon.note.contains("LIMITED-amazon@apl"))
        assertEquals(1_29_900L, amazon.amountMinor)
        assertEquals(TxType.INCOME, refund.type)
        assertEquals(49_950L, refund.amountMinor)
        // Reference numbers and the value date aren't part of the note.
        assertFalse(swiggy.note, swiggy.note.contains("0000612345678901") || swiggy.note.contains("01/10/26"))
    }

    @Test fun passwordProtectedStatement() {
        val locked = bytes("statement-locked.pdf")
        val noPassword = runCatching { PdfStatement.text(context, locked) }.exceptionOrNull()
        assertTrue(noPassword is PdfStatement.PasswordNeeded && !noPassword.wrong)
        val wrong = runCatching { PdfStatement.text(context, locked, "0000") }.exceptionOrNull()
        assertTrue(wrong is PdfStatement.PasswordNeeded && wrong.wrong)
        assertEquals(5, PdfStatement.parse(PdfStatement.text(context, locked, "1234")).rows.size)
    }

    @Test fun drAndCrMarkersWithoutABalance() {
        val p = PdfStatement.parse(
            """
            05-10-2026 POS SPENCERS RETAIL 1,250.00 Dr
            06-10-2026 IMPS FROM RAHUL 3,000.00 Cr
            """.trimIndent(),
        )
        assertEquals(listOf(TxType.EXPENSE, TxType.INCOME), p.rows.map { it.type })
        assertEquals(listOf(1_25_000L, 3_00_000L), p.rows.map { it.amountMinor })
    }

    @Test fun scannedPdfExplains() {
        assertTrue(PdfStatement.parse("").error != null)
    }
}
