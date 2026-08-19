package com.kamal.smsfinance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterpartyReminderDao {
    @Query("SELECT * FROM counterparty_reminders WHERE counterpartyId = :counterpartyId ORDER BY isDone ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun forCounterparty(counterpartyId: Long): Flow<List<CounterpartyReminder>>

    @Query("SELECT * FROM counterparty_reminders ORDER BY isDone ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    suspend fun getAllOnce(): List<CounterpartyReminder>

    @Query("SELECT * FROM counterparty_reminders WHERE isDone = 0 AND (dueAt IS NULL OR dueAt <= :until) ORDER BY dueAt IS NULL, dueAt ASC")
    fun due(until: Long): Flow<List<CounterpartyReminder>>

    @Query("SELECT * FROM counterparty_reminders WHERE id = :id")
    suspend fun getById(id: Long): CounterpartyReminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: CounterpartyReminder): Long

    @Update
    suspend fun update(reminder: CounterpartyReminder)

    @Delete
    suspend fun delete(reminder: CounterpartyReminder)

    @Query("UPDATE counterparty_reminders SET isDone = :done, completedAt = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?)
}
