package com.kamal.smsfinance.sms

import com.kamal.smsfinance.data.TransactionType

/**
 * A blocklist pattern for messages that should NEVER be stored or shown.
 * Only for: OTP, promotional/spam, phishing, courier, etc.
 * REAL FINANCIAL TRANSACTIONS (fees, insurance premiums) must NOT be here.
 */
data class BlockPattern(
    val id: String,
    val regex: String,  // Pattern matched via containsMatchIn (substring)
    val description: String
)

/**
 * A template for known bank SMS formats that can be parsed reliably.
 * Richness levels:
 * - RICH: Full match (amount, type, bank, balance, tail, txnId) -> ConfirmedTransaction
 * - SEMI_RICH: Partial but reliable (type + amount + bank signal) -> ConfirmedTransaction with default category
 * - OPAQUE: Only bank sender known, body not parseable -> Unidentified (for review)
 */
enum class TemplateRichness { RICH, SEMI_RICH, OPAQUE }

data class Template(
    val id: String,
    val regex: String,  // Pattern matched via containsMatchIn (substring)
    val richness: TemplateRichness,
    val bankName: String,
    val type: TransactionType,
    val defaultCategory: String? = null,
    val defaultCounterparty: String? = null,
    val description: String
)

/**
 * Result of template matching
 */
sealed class TemplateMatchResult {
    data class Matched(val template: Template, val extracted: ParsedSms) : TemplateMatchResult()
    object NoMatch : TemplateMatchResult()
}