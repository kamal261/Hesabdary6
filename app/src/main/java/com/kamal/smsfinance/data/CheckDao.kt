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

    // All PENDING checks whose due date falls within the next `withinDays`
    // days -- used to build in-app reminder banners (no system notification).
    @Query("""
        SELECT * FROM checks
        WHERE status = 'PENDING' AND dueDate BETWEEN :now AND :now + (:withinDays * 86400000)
        ORDER BY dueDate ASC
    """)
    fun getDueSoon(now: Long, withinDays: Long = 7): Flow<List<Check>>
}
