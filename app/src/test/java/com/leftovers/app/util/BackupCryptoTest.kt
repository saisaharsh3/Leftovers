package com.leftovers.app.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
