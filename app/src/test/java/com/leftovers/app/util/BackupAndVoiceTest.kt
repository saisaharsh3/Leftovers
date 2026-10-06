package com.leftovers.app.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val backup = """{"app":"leftovers","transactions":[{"note":"Rent"}]}"""

    @Test fun roundTripsWithTheRightPassword() {
        val sealed = BackupCrypto.encrypt(backup, "correct horse")
        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertEquals(backup, BackupCrypto.decrypt(JSONObject(sealed.toString()), "correct horse"))
    }

    @Test fun hidesTheContents() {
        val sealed = BackupCrypto.encrypt(backup, "pw").toString()
        assertFalse(sealed.contains("Rent"))
        // Same input twice gives different files (fresh salt and IV).
        assertTrue(sealed != BackupCrypto.encrypt(backup, "pw").toString())
    }

    @Test fun rejectsTheWrongPassword() {
        val sealed = BackupCrypto.encrypt(backup, "right")
        assertThrows(BackupPasswordException::class.java) { BackupCrypto.decrypt(sealed, "wrong") }
    }

    @Test fun plainBackupsAreNotEncrypted() {
        assertFalse(BackupCrypto.isEncrypted(JSONObject(backup)))
    }
}

class VoiceEntryTest {
    private val categories = listOf(1L to "Food & Dining", 2L to "Transport", 3L to "Groceries", 4L to "Bills & Utilities", 5L to "Other")

    @Test fun amountAndNote() {
        val e = VoiceEntry.parse("250 for lunch", categories)
        assertEquals(25_000L, e.amountMinor)
        assertEquals("Lunch", e.note)
        assertEquals(1L, e.categoryId)
    }

    @Test fun fillerWordsAndCurrencyAreDropped() {
        val e = VoiceEntry.parse("spent 1,200 rupees on groceries at dmart", categories)
        assertEquals(120_000L, e.amountMinor)
        assertEquals("Groceries at dmart", e.note)
        assertEquals(3L, e.categoryId)
    }

    @Test fun decimalsAndThousands() {
        assertEquals(4_550L, VoiceEntry.parse("45.50 coffee", categories).amountMinor)
        assertEquals(150_000L, VoiceEntry.parse("1.5k flight tickets", categories).amountMinor)
    }

    @Test fun keywordsPickACategory() {
        assertEquals(2L, VoiceEntry.parse("uber 180", categories).categoryId)
        assertEquals(4L, VoiceEntry.parse("paid electricity 900", categories).categoryId)
    }

    @Test fun nothingClearLeavesCategoryAlone() {
        val e = VoiceEntry.parse("gift for mom", categories)
        assertNull(e.amountMinor)
        assertNull(e.categoryId)
        assertEquals("Gift for mom", e.note)
    }
}
