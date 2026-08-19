package com.kamal.smsfinance.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate8To9_preservesRowsAndCreatesPerformanceIndexes() {
        helper.createDatabase(testDbName, 8).apply {
            execSQL(
                """
                INSERT INTO transactions(
                    amountToman, type, bankName, description, date, source,
                    isIndirectSettlement, transferGroupId, linkedCheckId, notes
                ) VALUES (120000, 'EXPENSE', 'Test Bank', 'Migration row', 1700000000000,
                    'MANUAL', 0, NULL, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO checks(
                    type, counterpartyId, amountToman, dueDate, status,
                    paidDate, reminderDays, description, settledTransactionId, createdAt
                ) VALUES ('RECEIVABLE', NULL, 120000, 1700100000000, 'PENDING',
                    NULL, 3, 'Migration check', NULL, 1700000000000)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            9,
            true,
            AppDatabase.MIGRATION_8_9
        )

        migrated.query("SELECT COUNT(*) FROM transactions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM checks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        assertTrue(indexExists(migrated, "index_transactions_date"))
        assertTrue(indexExists(migrated, "index_checks_status_dueDate"))
        migrated.close()

        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(testDbName)
    }

    private fun indexExists(db: SupportSQLiteDatabase, name: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name)).use { cursor ->
            cursor.moveToFirst()
        }
}
