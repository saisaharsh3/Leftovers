package com.leftovers.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RequireHttpsTest {
    @Test fun acceptsHttpsAndTidiesTheAddress() {
        assertEquals("https://my.server/v1", requireHttps(" https://my.server/v1/ "))
        assertEquals("https://my.server/v1", requireHttps("https://my.server/v1/chat/completions"))
    }

    @Test fun rejectsPlainHttpAndJunk() {
        assertThrows(IllegalArgumentException::class.java) { requireHttps("http://192.168.1.5:11434/v1") }
        assertThrows(IllegalArgumentException::class.java) { requireHttps("my.server/v1") }
        assertThrows(IllegalArgumentException::class.java) { requireHttps("https://") }
    }
}
