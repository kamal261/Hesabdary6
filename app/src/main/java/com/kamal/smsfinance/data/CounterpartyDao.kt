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

    // Balance = Σ(income linked to this counterparty) − Σ(expense linked to this counterparty).
    // Positive: the counterparty still owes the user. Negative: the user still owes the counterparty.
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountToman ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountToman ELSE 0 END), 0)
        FROM transactions WHERE counterpartyId = :id
    """)
    fun balanceFor(id: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM counterparties WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    @Query("SELECT COALESCE(SUM(amountToman), 0) FROM transactions WHERE counterpartyId = :id")
    fun totalVolumeFor(id: Long): Flow<Long>
}
