package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A small, user-controlled follow-up item attached to a counterparty.
 * It deliberately does not change balances or create transactions by itself.
 */
@Entity(
    tableName = "counterparty_reminders",
    foreignKeys = [
        ForeignKey(
            entity = Counterparty::class,
            parentColumns = ["id"],
            childColumns = ["counterpartyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("counterpartyId"),
        Index("dueAt"),
        Index(value = ["isDone", "dueAt"])
    ]
)
data class CounterpartyReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val counterpartyId: Long,
    val title: String,
    val details: String? = null,
    /** Epoch milliseconds. Null means no scheduled date. */
    val dueAt: Long? = null,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
