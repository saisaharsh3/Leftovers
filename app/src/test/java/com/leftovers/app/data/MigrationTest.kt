package com.leftovers.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a database from older versions to the current one and checks that Room accepts the result
 * (every table and column as the app expects) and that existing data survives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    // A full path: Room's test driver compares names with "/" and so misses on Windows.
    private val name get() = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test").absolutePath

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun upgradesFromVersion2KeepingData() {
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO categories (id, name, emoji, color, type, budgetMinor) VALUES (1, 'Food', 'food', 1, 'EXPENSE', NULL)")
            execSQL("INSERT INTO categories (id, name, emoji, color, type, budgetMinor) VALUES (2, 'Gifts', 'gift', 1, 'INCOME', NULL)")
            execSQL("INSERT INTO categories (id, name, emoji, color, type, budgetMinor) VALUES (3, 'Salary', 'briefcase', 1, 'INCOME', NULL)")
            execSQL("INSERT INTO transactions (id, amountMinor, type, categoryId, epochDay, note, createdAt) VALUES (1, 25000, 'EXPENSE', 1, 20000, 'Lunch', 0)")
            execSQL(
                "INSERT INTO recurring (id, name, amountMinor, type, categoryId, dayOfMonth, startMonth, lastPostedMonth, active) " +
                    "VALUES (1, 'Netflix', 64900, 'EXPENSE', 1, 12, '2026-01', NULL, 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 11, true, *ALL_MIGRATIONS)

        db.query("SELECT amountMinor, note, accountId FROM transactions WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(25000L, it.getLong(0))
            assertEquals("Lunch", it.getString(1))
            // v4 put existing entries in the Cash account.
            assertEquals(1L, it.getLong(2))
        }
        db.query("SELECT name, addsToBudget FROM categories WHERE type = 'INCOME' ORDER BY id").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(1)) // Gifts add to the budget
            it.moveToNext()
            assertEquals(0, it.getInt(1)) // Salary doesn't
        }
        db.query("SELECT everyMonths FROM recurring WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0)) // existing subscriptions stay monthly
        }
        db.query("SELECT COUNT(*) FROM debts").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test fun eachStepFromVersion5Validates() {
        helper.createDatabase(name, 5).close()
        helper.runMigrationsAndValidate(name, 6, true, *ALL_MIGRATIONS).close()
        helper.runMigrationsAndValidate(name, 7, true, *ALL_MIGRATIONS).close()
        helper.runMigrationsAndValidate(name, 8, true, *ALL_MIGRATIONS).close()
        helper.runMigrationsAndValidate(name, 9, true, *ALL_MIGRATIONS).close()
        helper.runMigrationsAndValidate(name, 10, true, *ALL_MIGRATIONS).close()
        helper.runMigrationsAndValidate(name, 11, true, *ALL_MIGRATIONS).close()
    }
}
