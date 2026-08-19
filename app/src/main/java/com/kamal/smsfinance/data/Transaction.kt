package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { EXPENSE, INCOME }

enum class TransactionSource { SMS_AUTO, MANUAL, CHECK_SETTLEMENT }

/**
 * A single financial transaction, whether parsed automatically from a bank SMS,
 * entered manually by the user, or auto-created when a Check is marked settled.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Counterparty::class,
            parentColumns = ["id"],
            childColumns = ["counterpartyId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoryId"),
        Index("counterpartyId"),
        Index("transferGroupId"),
        Index("linkedCheckId"),
        Index("date")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Amount is always stored normalized to Toman
    val amountToman: Long,

    val type: TransactionType,

    val bankName: String,

    val description: String,

    // Epoch millis
    val date: Long,

    val source: TransactionSource,

    // Raw SMS body kept for auditing / re-parsing if the regex is improved later.
    val rawSms: String? = null,

    // SMS sender address (e.g. "+9890...") used for dedup
    val smsSender: String? = null,

    // Card/account tail digits, if the SMS exposed them (e.g. "1234")
    val accountTail: String? = null,

    // Assigned later by the user; null means "uncategorized".
    val categoryId: Long? = null,

    // Links this transaction to a customer/worker profile, when relevant.
    val counterpartyId: Long? = null,

    // True when the user recorded this via the "third-party payment on my
    // behalf" reminder flow (money never touched their own bank SMS trail).
    val isIndirectSettlement: Boolean = false,

    /** Shared id for the two sides of a suspected or confirmed personal transfer. */
    val transferGroupId: Long? = null,

    /** User-approved link to the check that explains this bank transaction. */
    val linkedCheckId: Long? = null,

    // Free-text note the user can attach to a specific transaction (e.g. "علی ۱۲ جفت کفش هم
    // آورده، از حسابش کم کنم") -- separate from Category, since a note is transaction-specific
    // context, not a repeatable classification. Null means no note.
    val notes: String? = null
)
