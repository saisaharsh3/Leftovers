package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPasscodeTest {
    @Test fun waitsLongerAfterRepeatedWrongTries() {
        // The first four wrong tries cost nothing; then 30 s, doubling, up to 15 minutes.
        assertEquals(0L, AppPasscode.waitAfter(4))
        assertEquals(30_000L, AppPasscode.waitAfter(5))
        assertEquals(60_000L, AppPasscode.waitAfter(6))
        assertEquals(480_000L, AppPasscode.waitAfter(9))
        assertEquals(900_000L, AppPasscode.waitAfter(10))
        assertEquals(900_000L, AppPasscode.waitAfter(40))
    }
}
