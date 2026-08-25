package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CATEGORIZE (default): apply categoryId/counterpartyId to a matching transaction, as before.
 * IGNORE: the matched SMS is dropped entirely and never stored anywhere -- not as a transaction,
 * not in the "needs review" queue. For a known, unwanted, RECURRING message pattern (a specific
 * merchant, a specific notification format) the user has explicitly decided isn't worth tracking
 * -- not a general spam/OTP filter, which stays in BlocklistEngine's separate, non-user-editable
 * seed list.
 */
enum class RuleAction { CATEGORIZE, IGNORE }

/**
 * A user-defined, fully transparent auto-categorization rule (Single Source
 * of Truth for pattern-matching). If `pattern` is found inside an incoming
 * SMS's text, this rule's categoryId/counterpartyId are applied to the new
 * transaction automatically. The user can see, edit, and delete every rule
 * from the Rules screen -- nothing here is a hidden or black-box decision.
 */
@Entity(tableName = "smart_rules")
data class SmartRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pattern: String,
    val categoryId: Long? = null,
    val counterpartyId: Long? = null,
    val action: RuleAction = RuleAction.CATEGORIZE,
    val createdAt: Long = System.currentTimeMillis()
)
