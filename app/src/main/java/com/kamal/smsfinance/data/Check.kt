package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** RECEIVABLE: a check the user is holding, expecting to collect. PAYABLE: a check the user has issued. */
enum class CheckType { RECEIVABLE, PAYABLE }

enum class CheckStatus { PENDING, CLEARED, BOUNCED }

@Entity(tableName = "checks")
data class Check(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: CheckType,
    val counterpartyId: Long? = null,
    val amountToman: Long,
    val dueDate: Long,
    val status: CheckStatus = CheckStatus.PENDING,
    val paidDate: Long? = null,
    // How many days before dueDate the in-app reminder should surface (e.g. 1, 3, 7).
    val reminderDays: Int = 3,
    val description: String? = null,
    // Set once CLEARED, pointing at the Transaction auto-created for the settlement.
    val settledTransactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
