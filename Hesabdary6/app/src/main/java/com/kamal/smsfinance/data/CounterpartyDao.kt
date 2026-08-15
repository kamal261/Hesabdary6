package com.kamal.smsfinance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterpartyDao {

    @Query("SELECT * FROM counterparties ORDER BY name ASC")
    fun getAll(): Flow<List<Counterparty>>

    @Query("SELECT * FROM counterparties ORDER BY name ASC")
    suspend fun getAllOnce(): List<Counterparty>

    @Query("SELECT * FROM counterparties WHERE type = :type ORDER BY name ASC")
    fun getByType(type: CounterpartyType): Flow<List<Counterparty>>

    @Query("SELECT * FROM counterparties WHERE id = :id")
    fun observeById(id: Long): Flow<Counterparty?>

    @Query("SELECT * FROM counterparties WHERE id = :id")
    suspend fun getById(id: Long): Counterparty?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(counterparty: Counterparty): Long

    @Update
    suspend fun update(counterparty: Counterparty)

    @Delete
    suspend fun delete(counterparty: Counterparty)

    @Query("SELECT * FROM transactions WHERE counterpartyId = :id ORDER BY date DESC")
    fun transactionsFor(id: Long): Flow<List<Transaction>>

    // Balance = Σ(each linked transaction's amount × counterpartyBalanceSign(type, category.kind)).
    // Positive: the counterparty still owes the user. Negative: the user still owes the counterparty.
    // This CASE expression MUST mirror counterpartyBalanceSign() in CounterpartyBalance.kt exactly
    // -- SQL can't call that Kotlin function directly, so the two are kept in sync by hand. A
    // LEFT JOIN (not INNER) is required: a transaction with no category (categoryId IS NULL)
    // must still count via the "ordinary income/expense" fallback, not be silently dropped.
    @Query("""
        SELECT COALESCE(SUM(
            CASE
                WHEN c.kind = 'DEBT_COLLECTION' THEN -t.amountToman
                WHEN c.kind = 'DEBT_PAYMENT' THEN t.amountToman
                WHEN t.type = 'INCOME' THEN t.amountToman
                ELSE -t.amountToman
            END
        ), 0)
        FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.counterpartyId = :id
    """)
    fun balanceFor(id: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM counterparties WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    @Query("SELECT COALESCE(SUM(amountToman), 0) FROM transactions WHERE counterpartyId = :id")
    fun totalVolumeFor(id: Long): Flow<Long>
}
