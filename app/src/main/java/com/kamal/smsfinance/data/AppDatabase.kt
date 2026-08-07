// SmsFinance file version: 2 — added UnidentifiedSms entity/DAO, bumped to schema v5 (new table + Counterparty.notes column)
package com.kamal.smsfinance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_finance.db"
                )
                    // Simple destructive fallback for now; replace with real
                    // migrations before shipping a schema change to real users.
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
