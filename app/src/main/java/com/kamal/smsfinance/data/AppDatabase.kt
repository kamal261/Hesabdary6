// SmsFinance file version: 3 — bumped to schema v6 for Category.parentId (subcategory
// support), with a real Migration(5, 6) instead of relying on fallbackToDestructiveMigration --
// that fallback would have wiped every user's transactions/categories/rules on this update,
// which is unacceptable for a personal finance app (see the product's "بدون تصمیم مالی
// غیرقابل‌بازگشت" principle -- silently deleting someone's transaction history on an app update
// is about as irreversible as it gets). fallbackToDestructiveMigration() is kept only as a
// safety net for schema versions not explicitly covered by a real Migration.
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
    version = 6,
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_finance.db"
                )
                    .addMigrations(MIGRATION_5_6)
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
