package com.leftovers.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Category::class, Transaction::class, Recurring::class, Goal::class, GoalDeposit::class,
        Account::class, Transfer::class, SmsSuggestion::class, Debt::class, DeletedTransaction::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recurringDao(): RecurringDao
    abstract fun goalDao(): GoalDao
    abstract fun accountDao(): AccountDao
    abstract fun transferDao(): TransferDao
    abstract fun smsDao(): SmsDao
    abstract fun backupDao(): BackupDao
    abstract fun debtDao(): DebtDao
    abstract fun deletedDao(): DeletedDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "leftovers.db")
                .addCallback(SeedCategories)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}

/** v10 lets a subscription or recurring income go to a chosen account. */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `accountId` INTEGER")
    }
}

/** v9 keeps deleted entries so they can be restored. */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_transactions` (`id` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `accountId` INTEGER, `receiptPath` TEXT, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/** v8 lets detected payments come from email as well as SMS. */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sms_suggestions` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'sms'")
    }
}

/** v7 adds money lent and borrowed. */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, `note` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `settled` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
    }
}

/** v2 adds recurring payments and savings goals. Existing data is kept. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `type` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                "`dayOfMonth` INTEGER NOT NULL, `startMonth` TEXT NOT NULL, `lastPostedMonth` TEXT, `active` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `color` INTEGER NOT NULL, `targetMinor` INTEGER NOT NULL, `targetMonth` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_deposits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`goalId` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_deposits_goalId` ON `goal_deposits` (`goalId`)")
    }
}

/** v4 adds accounts, transfers, receipt photos and SMS suggestions. Existing entries go to "Cash". */
/** v6 adds yearly subscriptions; existing ones stay monthly. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `everyMonths` INTEGER NOT NULL DEFAULT 1")
    }
}

/** v5 lets income categories add to the month's budget; on for everything except Salary. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `addsToBudget` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `categories` SET `addsToBudget` = 1 WHERE `type` = 'INCOME' AND `name` <> 'Salary'")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`icon` TEXT NOT NULL, `color` INTEGER NOT NULL, `openingMinor` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromAccountId` INTEGER NOT NULL, " +
                "`toAccountId` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`fromAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`toAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_fromAccountId` ON `transfers` (`fromAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_toAccountId` ON `transfers` (`toAccountId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sms_suggestions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountMinor` INTEGER NOT NULL, " +
                "`merchant` TEXT NOT NULL, `sender` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `body` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL)",
        )
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountId` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptPath` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
        seedAccounts(db)
        db.execSQL("UPDATE transactions SET accountId = 1")
    }
}

private fun seedAccounts(db: SupportSQLiteDatabase) {
    val now = System.currentTimeMillis()
    DefaultAccounts.forEach { a ->
        db.execSQL(
            "INSERT INTO accounts (name, icon, color, openingMinor, createdAt) VALUES (?, ?, ?, 0, ?)",
            arrayOf<Any>(a.name, a.icon, a.color, now),
        )
    }
}

val DefaultAccounts = listOf(
    Account(name = "Cash", icon = "banknote", color = Palette.MINT),
    Account(name = "Bank", icon = "landmark", color = Palette.SKY),
)

private object SeedCategories : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        seedAccounts(db)
        DefaultCategories.forEach { c ->
            db.execSQL(
                "INSERT INTO categories (name, emoji, color, type, addsToBudget) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(c.name, c.emoji, c.color, c.type.name, if (c.addsToBudget) 1 else 0),
            )
        }
    }
}

/** v3 swaps emoji for line icons and moves colours to the refined palette. No schema change. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        LegacyEmojiToIcon.forEach { (emoji, key) ->
            db.execSQL("UPDATE categories SET emoji = ? WHERE emoji = ?", arrayOf<Any>(key, emoji))
            db.execSQL("UPDATE goals SET emoji = ? WHERE emoji = ?", arrayOf<Any>(key, emoji))
        }
        LegacyColorToPalette.forEach { (old, new) ->
            db.execSQL("UPDATE categories SET color = ? WHERE color = ?", arrayOf<Any>(new, old))
            db.execSQL("UPDATE goals SET color = ? WHERE color = ?", arrayOf<Any>(new, old))
        }
        db.execSQL("UPDATE transactions SET note = replace(note, '🔁 ', '') WHERE note LIKE '🔁 %'")
    }
}

private val LegacyEmojiToIcon = mapOf(
    "🍔" to "utensils", "🍕" to "utensils", "☕" to "coffee", "🛒" to "shopping-cart", "🚕" to "taxi",
    "⛽" to "fuel", "🚌" to "bus", "🛍️" to "shopping-bag", "👕" to "shirt", "🧾" to "receipt", "💡" to "zap",
    "📱" to "smartphone", "🏠" to "house", "🔧" to "wrench", "🎬" to "clapperboard", "🎮" to "gamepad",
    "🎵" to "music", "💊" to "heart-pulse", "🏋️" to "dumbbell", "📚" to "graduation-cap", "✈️" to "plane",
    "🏨" to "building", "🎁" to "gift", "🐶" to "paw", "👶" to "baby", "💄" to "sparkles", "🍺" to "beer",
    "🚬" to "package", "💳" to "credit-card", "📦" to "package", "💼" to "briefcase", "💻" to "laptop",
    "📈" to "trending-up", "🎉" to "party", "💰" to "coins", "🏦" to "landmark", "🪙" to "coins", "🧧" to "gift",
    "🖥️" to "monitor", "🚗" to "car", "🏍️" to "bike", "💍" to "gem", "🎓" to "graduation-cap",
    "📷" to "camera", "🎸" to "guitar", "🛡️" to "shield",
)

private val LegacyColorToPalette = mapOf(
    0xFFFF7043 to Palette.PEACH, 0xFFEF5350 to Palette.CORAL, 0xFFEC407A to Palette.ROSE,
    0xFFAB47BC to Palette.LILAC, 0xFF7E57C2 to Palette.IRIS, 0xFF5C6BC0 to Palette.IRIS,
    0xFF42A5F5 to Palette.SKY, 0xFF29B6F6 to Palette.TEAL, 0xFF26A69A to Palette.TEAL,
    0xFF66BB6A to Palette.MINT, 0xFF9CCC65 to Palette.LIME, 0xFFFFCA28 to Palette.AMBER,
    0xFFFFB300 to Palette.AMBER, 0xFF8D6E63 to Palette.SAND, 0xFF78909C to Palette.SLATE,
    0xFF43A047 to Palette.MINT, 0xFF00ACC1 to Palette.SKY, 0xFF7CB342 to Palette.LIME,
    0xFF8E24AA to Palette.LILAC,
)

/** Soft colours tuned to sit on dark glass without shouting. */
object Palette {
    const val PEACH = 0xFFF4A68AL
    const val AMBER = 0xFFF2C46DL
    const val LIME = 0xFFC8E07AL
    const val MINT = 0xFF7FD9A8L
    const val TEAL = 0xFF6FD0D0L
    const val SKY = 0xFF7DB7F5L
    const val IRIS = 0xFF9C9CF8L
    const val LILAC = 0xFFC59AF2L
    const val ROSE = 0xFFF291B4L
    const val CORAL = 0xFFF27E7EL
    const val SAND = 0xFFD9B99BL
    const val SLATE = 0xFFA3AEBDL

    val all = listOf(PEACH, AMBER, LIME, MINT, TEAL, SKY, IRIS, LILAC, ROSE, CORAL, SAND, SLATE)
}

val DefaultCategories = listOf(
    Category(name = "Food & Dining", emoji = "utensils", color = Palette.PEACH, type = TxType.EXPENSE),
    Category(name = "Groceries", emoji = "shopping-cart", color = Palette.MINT, type = TxType.EXPENSE),
    Category(name = "Transport", emoji = "taxi", color = Palette.SKY, type = TxType.EXPENSE),
    Category(name = "Shopping", emoji = "shopping-bag", color = Palette.ROSE, type = TxType.EXPENSE),
    Category(name = "Bills & Utilities", emoji = "receipt", color = Palette.AMBER, type = TxType.EXPENSE),
    Category(name = "Rent", emoji = "house", color = Palette.SAND, type = TxType.EXPENSE),
    Category(name = "Entertainment", emoji = "clapperboard", color = Palette.LILAC, type = TxType.EXPENSE),
    Category(name = "Health", emoji = "heart-pulse", color = Palette.CORAL, type = TxType.EXPENSE),
    Category(name = "Education", emoji = "graduation-cap", color = Palette.IRIS, type = TxType.EXPENSE),
    Category(name = "Travel", emoji = "plane", color = Palette.TEAL, type = TxType.EXPENSE),
    Category(name = "Gifts", emoji = "gift", color = Palette.LIME, type = TxType.EXPENSE),
    Category(name = "Other", emoji = "package", color = Palette.SLATE, type = TxType.EXPENSE),
    Category(name = "Salary", emoji = "briefcase", color = Palette.MINT, type = TxType.INCOME),
    Category(name = "Freelance", emoji = "laptop", color = Palette.SKY, type = TxType.INCOME, addsToBudget = true),
    Category(name = "Investments", emoji = "trending-up", color = Palette.LIME, type = TxType.INCOME, addsToBudget = true),
    Category(name = "Gifts", emoji = "party", color = Palette.AMBER, type = TxType.INCOME, addsToBudget = true),
    Category(name = "Other", emoji = "coins", color = Palette.LILAC, type = TxType.INCOME, addsToBudget = true),
)

/** Every upgrade step, oldest first; also used by the migration tests. */
internal val ALL_MIGRATIONS: Array<Migration>
    get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
