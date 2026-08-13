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
}

@Database(
    entities = [Transaction::class, Category::class, Counterparty::class, Check::class, SmartRule::class, UnidentifiedSms::class],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun counterpartyDao(): CounterpartyDao
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_finance.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    // Safety net only for schema versions not explicitly covered above --
                    // every version bump should get a real Migration, not rely on this.
                    .fallbackToDestructiveMigration()
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
