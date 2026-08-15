// NOTE: BlockPattern, Template, TemplateRichness, and TemplateMatchResult below are used only
// by TemplateEngine.kt / BlocklistEngine.kt / SeedData.kt, none of which are currently called
// from anywhere in the app (TransactionRepository calls SmsParser.parse() directly). They're
// dead code as of this writing -- kept as-is rather than deleted, since removing an unused
// module is a bigger call than fixing the live parsing path this session was about. ParsedSms
// and SmsParseResult below ARE live (constructed by SmsParser.kt, the actual parser in use).
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

/**
 * Parsed SMS ready to become a Transaction
 */
data class ParsedSms(
    val sender: String,
    val amountToman: Long,
    val type: TransactionType,
    val bankName: String,
    val description: String,
    val timestamp: Long,
    val rawSms: String,
    val accountTail: String?,
    /** Suggested category *name* (bank fees / insurance premiums only, currently) for
     * transactions that are confidently a specific kind even though no SmartRule exists yet
     * for them. Null for everything else -- TransactionRepository still gives SmartRule and
     * small-amount categorization first priority over this. See SmsParser.defaultCategoryNameFor. */
    val defaultCategoryName: String? = null
)

/**
 * Outcome of trying to parse one SMS.
 * - Recognized: a full transaction was extracted (via Template RICH/SEMI_RICH).
 * - Unidentified: the message looked bank-related but couldn't be fully parsed.
 * - Ignored: confidently not a transaction (OTP, promo, balance-only, non-bank).
 */
sealed class SmsParseResult {
    data class Recognized(val parsed: ParsedSms) : SmsParseResult()
    data class Unidentified(val sender: String, val body: String, val timestamp: Long) : SmsParseResult()
    object Ignored : SmsParseResult()
}