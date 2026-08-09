package com.kamal.smsfinance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartRuleDao {
    @Query("SELECT * FROM smart_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<SmartRule>>

    @Query("SELECT * FROM smart_rules")
    suspend fun getAllRulesOnce(): List<SmartRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SmartRule): Long

    @Update
    suspend fun updateRule(rule: SmartRule)

    @Delete
    suspend fun deleteRule(rule: SmartRule)

    @Query("DELETE FROM smart_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}
