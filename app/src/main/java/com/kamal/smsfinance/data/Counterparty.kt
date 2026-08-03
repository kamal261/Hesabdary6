package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CUSTOMER: someone the user provides goods/services to -- the user is
 *   owed money by them (a receivable).
 * WORKER: someone who provides goods/services to the user -- the user
 *   owes money to them (a payable).
 */
enum class CounterpartyType { CUSTOMER, WORKER }

@Entity(tableName = "counterparties")
data class Counterparty(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: CounterpartyType,
    val phone: String? = null,
    val address: String? = null,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
