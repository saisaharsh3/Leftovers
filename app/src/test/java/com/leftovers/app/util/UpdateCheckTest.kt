package com.leftovers.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun comparesVersionsByNumber() {
        assertTrue(UpdateCheck.isNewer("1.8.2", "1.8.1"))
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.3"))
        assertTrue(UpdateCheck.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("1.8.1", "1.8.1"))
        assertFalse(UpdateCheck.isNewer("1.8.0", "1.8.1"))
        assertFalse(UpdateCheck.isNewer("1.8", "1.8.0"))
    }
}
