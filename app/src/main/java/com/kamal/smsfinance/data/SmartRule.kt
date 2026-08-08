package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val createdAt: Long = System.currentTimeMillis()
)
