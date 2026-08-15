package com.kamal.smsfinance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckDao {

    @Query("SELECT * FROM checks ORDER BY dueDate ASC")
    fun getAll(): Flow<List<Check>>

    @Query("SELECT * FROM checks ORDER BY dueDate ASC")
    suspend fun getAllOnce(): List<Check>

    @Query("SELECT * FROM checks WHERE status = :status ORDER BY dueDate ASC")
    fun getByStatus(status: CheckStatus): Flow<List<Check>>

    @Query("SELECT * FROM checks WHERE counterpartyId = :counterpartyId ORDER BY dueDate ASC")
    fun getByCounterparty(counterpartyId: Long): Flow<List<Check>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(check: Check): Long

    @Update
    suspend fun update(check: Check)

    @Delete
    suspend fun delete(check: Check)

    @Query("SELECT * FROM checks WHERE id = :id")
    suspend fun getById(id: Long): Check?

    // Each check controls its own reminder window. Overdue checks are included
    // because they still need attention until they are cleared or marked bounced.
    @Query("""
        SELECT * FROM checks
        WHERE status = 'PENDING'
          AND dueDate <= :now + (reminderDays * 86400000)
        ORDER BY dueDate ASC
    """)
    fun getDueSoon(now: Long): Flow<List<Check>>
}
