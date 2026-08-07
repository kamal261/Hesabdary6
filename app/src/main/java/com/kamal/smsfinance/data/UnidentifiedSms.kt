// SmsFinance file version: 1
package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A message that looked bank-related (matched a known bank sender, or had a
 * type keyword) but couldn't be fully parsed into a transaction -- kept
 * visible for the user to review instead of being silently dropped. This is
 * the explainable alternative to auto-guessing from pattern history: the
 * user sees the raw text and decides (dismiss, or add manually) rather than
 * the app making an invisible guess.
 */
@Entity(tableName = "unidentified_sms")
data class UnidentifiedSms(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val dismissed: Boolean = false
)
