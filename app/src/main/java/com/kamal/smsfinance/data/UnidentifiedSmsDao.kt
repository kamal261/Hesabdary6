// SmsFinance file version: 1
package com.kamal.smsfinance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnidentifiedSmsDao {

    @Query("SELECT * FROM unidentified_sms WHERE dismissed = 0 ORDER BY timestamp DESC")
    fun getActive(): Flow<List<UnidentifiedSms>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: UnidentifiedSms): Long

    @Query("""
        SELECT COUNT(*) FROM unidentified_sms
        WHERE sender = :sender AND body = :body AND timestamp = :timestamp
    """)
    suspend fun existsExact(sender: String, body: String, timestamp: Long): Int

    @Query("UPDATE unidentified_sms SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE unidentified_sms SET dismissed = 1 WHERE dismissed = 0")
    suspend fun dismissAll()

    @Query("SELECT COUNT(*) FROM unidentified_sms WHERE dismissed = 0")
    suspend fun countActive(): Int
}
