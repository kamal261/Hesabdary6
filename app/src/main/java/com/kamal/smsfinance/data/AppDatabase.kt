// SmsFinance file version: 4 — P0 finding from a technical/accounting review, confirmed:
// versions 1-4 have no exported schema JSON (nothing under app/schemas/ before this session),
// so a real Migration for that gap cannot be written blind -- guessing at an old table
// definition risks corrupting data worse than a clean wipe would. Two things follow from that:
// 1. build.gradle.kts now exports schema JSON on every build going forward (room.schemaLocation),
//    so this exact situation can't recur for any FUTURE version bump -- every migration from
//    schema 5 onward (this one included) has a real Migration, verified against the actual
//    previous schema, not a guess.
// 2. The residual risk is scoped to installs still on schema ≤4 specifically (pre-dates
//    subcategories). For a personal/small-team app not yet distributed to an unknown user base,
//    treat this as a known, bounded gap -- not silently "fixed" by this commit -- until schema
//    JSON for 1-4 can be recovered (e.g. from an old APK's compiled resources, if one exists) or
//    it's confirmed no real install is still that old.
package com.kamal.smsfinance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromType(value: TransactionType): String = value.name
    @TypeConverter
    fun toType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromSource(value: TransactionSource): String = value.name
    @TypeConverter
    fun toSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter
    fun fromCategoryKind(value: CategoryKind): String = value.name
    @TypeConverter
    fun toCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    @TypeConverter
    fun fromCounterpartyType(value: CounterpartyType): String = value.name
    @TypeConverter
    fun toCounterpartyType(value: String): CounterpartyType = CounterpartyType.valueOf(value)

    @TypeConverter
    fun fromCheckType(value: CheckType): String = value.name
    @TypeConverter
    fun toCheckType(value: String): CheckType = CheckType.valueOf(value)

    @TypeConverter
    fun fromCheckStatus(value: CheckStatus): String = value.name
    @TypeConverter
    fun toCheckStatus(value: String): CheckStatus = CheckStatus.valueOf(value)

    @TypeConverter
    fun fromRuleAction(value: RuleAction): String = value.name
    @TypeConverter
    fun toRuleAction(value: String): RuleAction = RuleAction.valueOf(value)
}

@Database(
    entities = [
        Transaction::class,
        Category::class,
        Counterparty::class,
        CounterpartyReminder::class,
        Check::class,
        SmartRule::class,
        UnidentifiedSms::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun counterpartyDao(): CounterpartyDao
    abstract fun counterpartyReminderDao(): CounterpartyReminderDao
    abstract fun checkDao(): CheckDao
    abstract fun smartRuleDao(): SmartRuleDao
    abstract fun unidentifiedSmsDao(): UnidentifiedSmsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** No FK constraint on categories.parentId by design (see Category.kt) -- a plain
         * ALTER TABLE ADD COLUMN is all this needs, no table recreation required. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN notes TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferGroupId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE transactions ADD COLUMN linkedCheckId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_transferGroupId ON transactions(transferGroupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_linkedCheckId ON transactions(linkedCheckId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS counterparty_reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        counterpartyId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        details TEXT,
                        dueAt INTEGER,
                        isDone INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        FOREIGN KEY(counterpartyId) REFERENCES counterparties(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counterparty_reminders_counterpartyId ON counterparty_reminders(counterpartyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counterparty_reminders_dueAt ON counterparty_reminders(dueAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counterparty_reminders_isDone_dueAt ON counterparty_reminders(isDone, dueAt)")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_checks_status_dueDate ON checks(status, dueDate)")
            }
        }

        // Supports the new existsExact(sender, body, timestamp) check in tryInsertUnidentified()
        // (v0.6.6) -- without this index, a large historical scan calls that lookup once per
        // unrecognized SMS with a full table scan each time, which gets slow exactly in the
        // case it matters most (many thousands of messages on first install).
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_unidentified_sms_sender_body_timestamp " +
                        "ON unidentified_sms(sender, body, timestamp)"
                )
            }
        }

        // Adds the IGNORE rule action (v0.6.6) -- existing rules default to CATEGORIZE, their
        // only action until now, so this is a pure additive change with no data implications.
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE smart_rules ADD COLUMN action TEXT NOT NULL DEFAULT 'CATEGORIZE'")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_finance.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    // Never silently wipe a user's financial history. Every supported schema
                    // change must have an explicit migration and an upgrade failure must remain
                    // visible instead of destroying data.
                    .addCallback(SeedCallback)
                    .build()
                    .also { INSTANCE = it }
            }

        /** Seeds the four default categories the first time the DB is created. */
        private object SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { instance ->
                    CoroutineScope(Dispatchers.IO).launch {
                        instance.categoryDao().insertAll(DefaultCategories.seed)
                    }
                }
            }
        }
    }
}
