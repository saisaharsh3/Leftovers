package com.leftovers.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An entry is always saved to an account that exists, the one the account button shows (#67). */
class SettleAccountTest {
    private val known = listOf(3L, 1L, 7L)

    @Test fun theOnePickedWhenItExists() = assertEquals(7L, settleAccount(7, known, home = 1, default = 3))

    @Test fun aDeletedOneGivesTheFirstAsTheButtonShows() = assertEquals(3L, settleAccount(9, known, home = 1, default = 1))

    @Test fun nonePickedYetGivesHomesThenTheDefaultThenTheFirst() {
        assertEquals(1L, settleAccount(null, known, home = 1, default = 7))
        assertEquals(7L, settleAccount(null, known, home = 9, default = 7))
        // The default account was deleted: not a missing id, the first one.
        assertEquals(3L, settleAccount(null, known, home = null, default = 9))
    }

    @Test fun noAccountsAtAll() = assertNull(settleAccount(null, emptyList(), home = null, default = 1))
}
