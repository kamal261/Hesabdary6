package com.kamal.smsfinance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<Transaction>>

    // One-shot snapshot (not a Flow) used by the recurring-transaction detector.
    @Query("SELECT * FROM transactions ORDER BY date ASC")
    suspend fun getAllOnce(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getBetween(from: Long, to: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getByType(type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE bankName = :bank ORDER BY date DESC")
    fun getByBank(bank: String): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions
        WHERE description LIKE '%' || :query || '%'
           OR bankName LIKE '%' || :query || '%'
        ORDER BY date DESC
    """)
    fun search(query: String): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    // Primary dedup guard: avoid re-inserting the exact same SMS (same
    // sender + raw text + date). This is the strict, always-correct check.
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE smsSender = :sender AND rawSms = :rawSms AND date = :date
    """)
    suspend fun existsExact(sender: String, rawSms: String, date: Long): Int

    // Returns the id of an existing row matching the exact same SMS (sender + raw
    // text + date), or null if none. Used by restore so a skipped (already-present)
    // transaction still maps oldId -> the existing row's id, keeping check/linkedCheck
    // links intact instead of silently dropping them.
    @Query("""
        SELECT id FROM transactions
        WHERE smsSender = :sender AND rawSms = :rawSms AND date = :date
        LIMIT 1
    """)
    suspend fun findExactId(sender: String, rawSms: String, date: Long): Long?

    // Fallback dedup guard: banks occasionally change their SMS short-code
    // (sender), which would defeat existsExact() and cause a duplicate row
    // for the same real-world transaction. When the SMS exposed an account
    // tail, treat a same-account + same-amount + same-type transaction
    // within a small time window as the same event. Narrow on purpose (only
    // applies when accountTail is known) so it never merges two genuinely
    // different transactions that happen to share an amount.
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE accountTail = :accountTail AND amountToman = :amount AND type = :type
          AND ABS(date - :date) <= :windowMillis
    """)
    suspend fun existsSimilar(accountTail: String, amount: Long, type: TransactionType, date: Long, windowMillis: Long): Int

    @Query("SELECT SUM(amountToman) FROM transactions WHERE type = 'INCOME' AND date BETWEEN :from AND :to")
    fun sumIncome(from: Long, to: Long): Flow<Long?>

    @Query("SELECT SUM(amountToman) FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :from AND :to")
    fun sumExpense(from: Long, to: Long): Flow<Long?>

    @Query("SELECT DISTINCT bankName FROM transactions ORDER BY bankName")
    fun getDistinctBanks(): Flow<List<String>>

    @Query("SELECT * FROM transactions WHERE categoryId IS NULL ORDER BY date DESC")
    fun getUncategorized(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY date DESC")
    fun getByCategory(categoryId: Long): Flow<List<Transaction>>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :transactionId")
    suspend fun assignCategory(transactionId: Long, categoryId: Long?)

    @Query("UPDATE transactions SET type = :type, categoryId = NULL WHERE id = :transactionId")
    suspend fun changeTypeAndClearCategory(transactionId: Long, type: TransactionType)

    @Query("UPDATE transactions SET counterpartyId = :counterpartyId WHERE id = :transactionId")
    suspend fun assignCounterparty(transactionId: Long, counterpartyId: Long?)

    @Query("UPDATE transactions SET notes = :notes WHERE id = :transactionId")
    suspend fun updateNotes(transactionId: Long, notes: String?)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("""
        SELECT * FROM transactions
        WHERE amountToman = :amount
          AND date BETWEEN :from AND :to
          AND linkedCheckId IS NULL
        ORDER BY ABS(date - :centerDate) ASC
    """)
    suspend fun findCheckCandidates(amount: Long, from: Long, to: Long, centerDate: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId ORDER BY date ASC")
    fun byTransferGroup(groupId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId ORDER BY date ASC")
    suspend fun byTransferGroupOnce(groupId: Long): List<Transaction>

    @Query("UPDATE transactions SET transferGroupId = :groupId WHERE id IN (:transactionIds)")
    suspend fun assignTransferGroup(transactionIds: List<Long>, groupId: Long)

    @Query("UPDATE transactions SET transferGroupId = NULL WHERE id = :transactionId")
    suspend fun clearTransferGroup(transactionId: Long)

    @Query("UPDATE transactions SET linkedCheckId = :checkId WHERE id = :transactionId")
    suspend fun linkCheck(transactionId: Long, checkId: Long?)
}
