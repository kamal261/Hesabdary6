// SmsFinance file version: 5 — added independent acceptance path: "strong datetime + balance + amount"
// signature (hasStrongDateTimeBalanceSignature) as an OR alongside existing keyword-based and
// signed-amount-anchored-by-balance paths. This covers Resalat, Blu, and central-payment
// system SMS (e.g. "پرداخت صورتحساب...") without making any single pattern mandatory.
// 1. Fee/insurance messages (کارمزد/حق بیمه) now carry a resolved default category name
//    (defaultCategoryName on ParsedSms) instead of landing in "بدون دسته" -- this was defined
//    but never actually wired to output in v1 (Template.defaultCategory was written, never read).
//    Here it's a plain field on ParsedSms that TransactionRepository actually resolves.
// 2. Unidentified-SMS dedup now goes through DedupEngine (SHA-256 + sliding window) instead of
//    the old exact (sender, body, timestamp) match, which missed near-duplicate broadcast retries.
//    Recognized-transaction dedup (existsExact + existsSimilar with accountTail) was already solid
//    in this file and is untouched.
package com.kamal.smsfinance.sms

import com.kamal.smsfinance.data.TransactionType

/** Intermediate result of parsing one SMS, before it becomes a Room Transaction. */
data class ParsedSms(
    val sender: String,
    val amountToman: Long,
    val type: TransactionType,
    val bankName: String,
    val description: String,
    val timestamp: Long,
    val rawSms: String,
    val accountTail: String?,
    /**
     * Suggested category *name* (not id -- categories are user-owned and DB-assigned) for
     * messages that are confidently a specific kind of real transaction even though no
     * SmartRule exists yet for them. Currently only set for bank fees and insurance premiums,
     * both real money movements that should never sit in "بدون دسته". Null for everything else --
     * the repository still gives SmartRule/small-amount categorization first priority over this.
     */
    val defaultCategoryName: String? = null
)

/**
 * Outcome of trying to parse one SMS.
 * - Recognized: a full transaction was extracted.
 * - Unidentified: the message looked bank-related (known bank sender, or a
 *   type keyword matched) but a full transaction couldn't be extracted --
 *   kept for the user to review rather than silently dropped.
 * - Ignored: confidently not a transaction (blank, OTP/promo/balance-check,
 *   delivery/loan-offer spam with a link, or ordinary non-bank text) --
 *   never stored, never shown.
 */
sealed class SmsParseResult {
    data class Recognized(val parsed: ParsedSms) : SmsParseResult()
    data class Unidentified(val sender: String, val body: String, val timestamp: Long) : SmsParseResult()
    object Ignored : SmsParseResult()
}

/**
 * Parses Iranian bank SMS messages into structured transaction data.
 *
 * Design principle -- two guarantees, kept in tension on purpose:
 *   1. Zero silent loss: a message with real bank-like signal is never
 *      dropped without a trace -- if it can't be fully parsed, it becomes
 *      Unidentified (visible, reviewable), never just discarded.
 *   2. Zero false positive: a message is never Recognized as a transaction
 *      unless the evidence is strong enough (keyword + amount, or a
 *      balance-line-anchored sign, from a plausible source).
 * These two goals pull in opposite directions, so every rule below is
 * either a *positive* signal (raises confidence towards Recognized) or a
 * *negative* signal (drops confidence towards Ignored) -- never a rule that
 * tries to do both, to keep the logic auditable.
 *
 * Coverage notes:
 * - Type (expense/income) and amount are resolved first, from the message
 *   body alone; bank identification is resolved last and is allowed to fall
 *   back to "نامشخص" once the message already looks like a real transaction
 *   (some banks, especially newer digital/micro-finance ones, don't always
 *   match a known sender code or name).
 * - Amount extraction supports "تومان" and "ریال" (auto-converted /10), with
 *   or without thousands separators (٬ , or normal comma), both Persian and
 *   Latin digits. Some banks (e.g. Resalat) send terse, unit-less ledger
 *   lines like "-2,260,000" with no keyword or currency word at all --
 *   Iranian core-banking systems are denominated in Rial internally, so an
 *   unlabeled amount defaults to Rial (divided by 10), not Toman.
 * - Type (expense/income) is inferred from a keyword table first; if no
 *   keyword matches (same terse-SMS case), a leading "-"/"+" immediately
 *   before the amount is used as a fallback signal -- but only when the
 *   message also reports an account balance ("مانده:"/"موجودی:"), which
 *   real bank ledger SMS almost always include and promotional/spam SMS
 *   almost never do.
 * - OTP/dynamic-password messages ("رمز پویا", "رمز:") are blocked outright,
 *   even when they also contain "خرید"/an amount (banks include the
 *   purchase context inside the OTP text itself).
 * - A message containing a raw link (http/https/www) from a sender that
 *   isn't a known bank is rejected -- loan-offer and delivery-company spam
 *   consistently include a link; genuine bank transaction SMS essentially
 *   never do.
 * - A known promotional "tail" appended after a real transaction sentence
 *   (e.g. "شگفت‌زده شوید! ...") is truncated off *before* parsing, so a
 *   genuine transaction bundled with an ad still gets recognized correctly
 *   instead of the whole message being poisoned by the ad's wording.
 * - NEW (v5): Independent acceptance path for "strong datetime + balance +
 *   amount" signature. If a message has a valid date, a valid time, a balance
 *   line (مانده/موجودی), and ANY amount signal (explicit currency, bare "مبلغ",
 *   or signed number), it is accepted as a transaction even without an
 *   explicit type keyword. This covers Resalat (05/06_12:44 format), Blu
 *   (multi-line datetime), and central-payment SMS ("پرداخت صورتحساب...").
 */
object SmsParser {

    // Sender short-codes / names, per bank. These are the most common ones;
    // extend freely as new bank sender IDs are observed on-device.
    private val BANK_SENDERS = mapOf(
        "ملت" to listOf("Mellat", "MELLAT", "ملت", "10000210", "10000211"),
        "سپه" to listOf("Sepah", "SEPAH", "سپه", "10009999", "10000155"),
        "پاسارگاد" to listOf("Pasargad", "PASARGAD", "پاسارگاد", "10000068", "500068"),
        "سامان" to listOf("Saman", "SAMAN", "سامان", "10000770", "10005010"),
        "ملی" to listOf("BMI", "10000019", "ملی ایران", "بانک ملی"),
        "تجارت" to listOf("Tejarat", "10000017", "تجارت"),
        "صادرات" to listOf("Saderat", "10000019", "صادرات"),
        "کشاورزی" to listOf("Keshavarzi", "10000160", "کشاورزی"),
        "رفاه" to listOf("Refah", "10000144", "رفاه کارگران"),
        "اقتصاد نوین" to listOf("EN Bank", "10000079", "اقتصادنوین"),
        "پارسیان" to listOf("Parsian", "10000622", "پارسیان"),
        "آینده" to listOf("Ayandeh", "10008485", "آینده"),
        "شهر" to listOf("City Bank", "10004555", "بانک شهر"),
        "دی" to listOf("Day Bank", "10009898", "بانک دی"),
        "کارآفرین" to listOf("Karafarin", "10008717", "کارآفرین"),
        "مسکن" to listOf("Maskan", "10000129", "مسکن"),
        "رسالت" to listOf("Resalat", "رسالت", "قرض الحسنه رسالت"),
        "بلو" to listOf("Blu", "BLU", "بلو", "بلوبانک", "0999")
    )

    // Keyword -> transaction type. Order matters: more specific phrases first.
    private val EXPENSE_KEYWORDS = listOf(
        "پرداخت قسط", "پرداخت صورتحساب", "خرید", "برداشت", "کارمزد", "هزینه",
        "پرداخت اینترنتی", "پرداخت شد", "انتقال به", "کسر از", "چک", "قبض"
    )

    // "دریافت" alone was too generic (matched promotional copy like "شما
    // مشمول دریافت اعتبار هستید") -- replaced with more specific phrases.
    private val INCOME_KEYWORDS = listOf(
        "واریز", "واریزی", "به حساب شما", "بازگشت وجه", "سود سپرده", "دریافت وجه", "دریافت مبلغ"
    )

    // Messages that are confidently never a transaction: OTP/dynamic
    // passwords, balance-check replies, and generic promo/ad markers. These
    // override everything else, including a "خرید"/amount that appears
    // alongside them inside an OTP message.
    private val IGNORE_KEYWORDS = listOf(
        "موجودی شما", "رمز یکبار مصرف", "رمز پویا", "رمز:", "کد تایید", "کد فعال",
        "تخفیف", "جشنواره", "تبلیغ", "OTP", "کد رهگیری", "کد تحویل"
    )

    // A known promotional "tail" is truncated off before parsing, so a real
    // transaction bundled with an ad (e.g. "...انجام شد. شگفت‌زده شوید! ...")
    // still gets parsed correctly from the part before the ad.
    private val PROMO_TAIL_MARKERS = listOf("شگفت زده شوید", "شگفت‌زده شوید", "طرح ویژه")
    private val PROMO_USSD_REGEX = Regex("""#\d+\*""") // e.g. "#4444*" dial-a-code prompts

    // A raw link almost never appears in a genuine bank transaction SMS, but
    // is near-universal in loan-offer and delivery-company promotional SMS.
    // Only trusted as a signal when the sender ISN'T a known bank -- a real
    // bank occasionally does include a receipt/tracking link.
    private val URL_REGEX = Regex("""https?://|www\.""", RegexOption.IGNORE_CASE)

    // Bank fees and insurance premiums: real money leaving the account, not spam --
    // must never be blocklisted (see EXPENSE_KEYWORDS, they already trigger EXPENSE
    // detection), but *should* get a sensible default category instead of "بدون دسته"
    // when no SmartRule has claimed them yet. Checked only after a message is already
    // headed toward Recognized (type + amount both resolved), so this can't itself
    // cause a false positive -- it only refines the category of an already-confirmed
    // expense transaction.
    private val FEE_CATEGORY_KEYWORDS = listOf("کارمزد")
    private val INSURANCE_CATEGORY_KEYWORDS = listOf("بیمه", "حق بیمه")

    private fun defaultCategoryNameFor(type: TransactionType, body: String): String? {
        if (type != TransactionType.EXPENSE) return null
        if (FEE_CATEGORY_KEYWORDS.any { body.contains(it) }) return "کارمزد بانکی"
        if (INSURANCE_CATEGORY_KEYWORDS.any { body.contains(it) }) return "بیمه"
        return null
    }

    private val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    // Matches amounts like: "مبلغ: 500,000 تومان", "500000 ريال", "1,250,000ریال"
    // Supports Persian thousands separator ٬ and plain , as well as Persian digits.
    private val AMOUNT_REGEX = Regex(
        """([\d۰-۹][\d۰-۹,٬./]*)\s*(تومان|ریال|ريال|Rials?|Toman)""",
        RegexOption.IGNORE_CASE
    )

    // Fallback: a bare number of 5+ digits immediately followed by common
    // currency-less bank phrasing ("مبلغ 500000 از").
    private val BARE_AMOUNT_REGEX = Regex("""مبلغ[:\\s]*([\d۰-۹][\d۰-۹,٬]*)""")

    // Final fallback for terse, keyword-less, unit-less ledger lines (e.g.
    // Resalat Bank: "-2,260,000"). A leading sign right before the digits is
    // both the amount marker and the type signal for these messages -- but
    // ONLY trusted when the message also reports an account balance
    // ("مانده:"/"موجودی:" + a number), which is a strong, near-universal
    // signature of genuine core-banking ledger SMS. Promotional text, phone
    // numbers, discount codes, and price ranges also contain +/- next to
    // digits but essentially never report a balance line -- this anchor is
    // what keeps the fallback from misfiring on non-bank SMS.
    private val SIGNED_AMOUNT_REGEX = Regex("""([+\-])\s*([\d۰-۹][\d۰-۹,٬]*)""")
    private val BALANCE_LINE_REGEX = Regex("""(مانده|موجودی)[:\\s]*[\d۰-۹][\d۰-۹,٬]*""")

    // Card/account tail, e.g. "...1234" or "حساب ****1234"
    private val TAIL_REGEX = Regex("""[*x]{2,}(\d{4})""")

    // Flexible date pattern: DD/MM, DD/MM/YY, DD/MM/YYYY, with separators / . - _
    // Also matches Persian digits. Year is optional (2 or 4 digits).
    private val DATE_REGEX = Regex("""[۰-۹0-9]{2}[/.\-_][۰-۹0-9]{2}([/.\-_][۰-۹0-9]{2,4})?""")
    // Time pattern: HH:MM or HH:MM:SS (24-hour), Persian or Latin digits
    private val TIME_REGEX = Regex("""[۰-۹0-9]{2}:[۰-۹0-9]{2}(:[۰-۹0-9]{2})?""")

    /**
     * Checks for the "strong datetime + balance + amount" signature:
     * - Has a valid date pattern (flexible: DD/MM, DD/MM/YY, DD/MM/YYYY)
     * - Has a valid time pattern (HH:MM or HH:MM:SS)
     * - Has a balance line (مانده/موجودی + number)
     * - Has SOME amount signal (explicit currency, bare "مبلغ", or signed number)
     *
     * This is an INDEPENDENT acceptance path (OR with existing paths).
     * Covers: Resalat (05/06_12:44), Blu (multi-line), central-payment SMS.
     */
    private fun hasStrongDateTimeBalanceSignature(body: String): Boolean {
        val hasDate = DATE_REGEX.containsMatchIn(body)
        val hasTime = TIME_REGEX.containsMatchIn(body)
        val hasBalance = BALANCE_LINE_REGEX.containsMatchIn(body)
        val hasAmount = AMOUNT_REGEX.containsMatchIn(body)
            || BARE_AMOUNT_REGEX.containsMatchIn(body)
            || SIGNED_AMOUNT_REGEX.containsMatchIn(body)
        return hasDate && hasTime && hasBalance && hasAmount
    }

    fun parse(sender: String, body: String, timestamp: Long): SmsParseResult {
        if (body.isBlank()) return SmsParseResult.Ignored

        // ── HARD GATES (negative signals - reject outright) ──
        // 1. Must NOT be from personal Iranian mobile (09xx after normalizing +98/0098)
        //    This is the strongest negative signal - personal numbers never send bank transactions
        val normalizedSender = sender.replace(" ", "").replace("-", "")
        val strippedSender = normalizedSender
            .removePrefix("+98")
            .removePrefix("0098")
        val isPersonalMobile = strippedSender.startsWith("09")
        val isKnownBankShortCode = BANK_SENDERS.values.flatten().any { code ->
            code.length >= 4 && strippedSender.contains(code)
        }
        if (isPersonalMobile && !isKnownBankShortCode) return SmsParseResult.Ignored

        // 2. OTP/promo/balance-check keywords override everything
        if (IGNORE_KEYWORDS.any { body.contains(it) }) return SmsParseResult.Ignored

        // 3. Raw link from non-bank sender = spam
        val isKnownBank = isKnownBankSender(sender)
        if (URL_REGEX.containsMatchIn(body) && !isKnownBank) return SmsParseResult.Ignored

        // ── POSITIVE ACCEPTANCE PATHS (any one is sufficient) ──
        val workingBody = truncatePromoTail(body)

        // Path A: Explicit type keyword + amount (traditional path)
        val type = identifyType(workingBody)
        val amount = if (type != null) extractAmountToman(workingBody) else null

        // Path B: Signed amount (+N/-N) anchored by balance line (terse ledger SMS, e.g. Resalat)
        val hasSignedAmountWithBalance = BALANCE_LINE_REGEX.containsMatchIn(body) && SIGNED_AMOUNT_REGEX.containsMatchIn(body)
        val signedType = if (hasSignedAmountWithBalance) {
            val signed = SIGNED_AMOUNT_REGEX.find(body)
            if (signed != null && signed.groupValues[1] == "-") TransactionType.EXPENSE else TransactionType.INCOME
        } else null
        val signedAmount = if (hasSignedAmountWithBalance) extractAmountToman(workingBody) else null

        // Path C (NEW v5): Strong datetime + balance + amount signature
        // Accepts transaction even WITHOUT explicit keyword if this structural signature exists
        val hasStrongSignature = hasStrongDateTimeBalanceSignature(body)
        val signatureType = if (hasStrongSignature && type == null) {
            // Fallback to signed amount for type if available, else try keyword on workingBody
            if (hasSignedAmountWithBalance) signedType
            else identifyType(workingBody) // may still find keyword after promo truncation
        } else type
        val signatureAmount = if (hasStrongSignature && amount == null) {
            extractAmountToman(workingBody)
        } else amount

        // ── ACCEPT if ANY path succeeded ──
        val finalType = type ?: signedType ?: signatureType
        val finalAmount = amount ?: signedAmount ?: signatureAmount

        if (finalType != null && finalAmount != null && finalAmount > 0) {
            val bank = identifyBank(sender, body) ?: "نامشخص"
            val tail = TAIL_REGEX.find(body)?.groupValues?.get(1)
            val description = buildDescription(workingBody, finalType)
            return SmsParseResult.Recognized(
                ParsedSms(
                    sender = sender,
                    amountToman = finalAmount,
                    type = finalType,
                    bankName = bank,
                    description = description,
                    timestamp = timestamp,
                    rawSms = body,
                    accountTail = tail,
                    defaultCategoryName = defaultCategoryNameFor(finalType, workingBody)
                )
            )
        }

        // ── UNIDENTIFIED: bank-related signals present but couldn't fully parse ──
        val hasExplicitAmount = AMOUNT_REGEX.containsMatchIn(body) || BARE_AMOUNT_REGEX.containsMatchIn(body)
        val hasAmountSignal = hasExplicitAmount || hasSignedAmountWithBalance
        val hasBalance = BALANCE_LINE_REGEX.containsMatchIn(body)
        val looksBankRelated = isKnownBank || hasBalance || hasAmountSignal || hasStrongSignature
        return if (looksBankRelated) SmsParseResult.Unidentified(sender, body, timestamp) else SmsParseResult.Ignored
    }

    /** Cuts off a known promotional "tail" so it doesn't interfere with parsing the real transaction sentence before it. */
    private fun truncatePromoTail(body: String): String {
        var cutIndex = body.length
        for (marker in PROMO_TAIL_MARKERS) {
            val idx = body.indexOf(marker)
            if (idx in 0 until cutIndex) cutIndex = idx
        }
        val ussdMatch = PROMO_USSD_REGEX.find(body)
        if (ussdMatch != null && ussdMatch.range.first < cutIndex) cutIndex = ussdMatch.range.first
        return body.substring(0, cutIndex)
    }

    private fun isKnownBankSender(sender: String): Boolean =
        BANK_SENDERS.values.any { identifiers -> identifiers.any { sender.contains(it, ignoreCase = true) } }

    private fun identifyBank(sender: String, body: String): String? {
        for ((bankName, identifiers) in BANK_SENDERS) {
            if (identifiers.any { sender.contains(it, ignoreCase = true) }) return bankName
        }
        // Fall back to scanning the body text itself for a bank name mention.
        for ((bankName, identifiers) in BANK_SENDERS) {
            if (identifiers.any { it.length > 3 && body.contains(it, ignoreCase = true) }) return bankName
        }
        return null
    }

    private fun identifyType(body: String): TransactionType? {
        val hasExpense = EXPENSE_KEYWORDS.any { body.contains(it) }
        val hasIncome = INCOME_KEYWORDS.any { body.contains(it) }
        return when {
            hasIncome && !hasExpense -> TransactionType.INCOME
            hasExpense && !hasIncome -> TransactionType.EXPENSE
            // Both matched (e.g. "کارمزد" inside a deposit message) -- prefer
            // whichever keyword appears first in the text, it's usually the
            // primary action.
            hasExpense && hasIncome -> {
                val expenseIdx = EXPENSE_KEYWORDS.minOf { kw -> body.indexOf(kw).let { if (it < 0) Int.MAX_VALUE else it } }
                val incomeIdx = INCOME_KEYWORDS.minOf { kw -> body.indexOf(kw).let { if (it < 0) Int.MAX_VALUE else it } }
                if (expenseIdx <= incomeIdx) TransactionType.EXPENSE else TransactionType.INCOME
            }
            // No keyword at all -- terse ledger-style SMS (e.g. Resalat Bank).
            // Only trusted when a balance line is also present (see
            // BALANCE_LINE_REGEX doc-comment); otherwise this is almost
            // certainly not a bank transaction at all, so it's rejected
            // rather than guessed at.
            else -> {
                if (!BALANCE_LINE_REGEX.containsMatchIn(body)) return null
                val signed = SIGNED_AMOUNT_REGEX.find(body) ?: return null
                if (signed.groupValues[1] == "-") TransactionType.EXPENSE else TransactionType.INCOME
            }
        }
    }

    private fun extractAmountToman(body: String): Long? {
        val match = AMOUNT_REGEX.find(body)
        if (match != null) {
            val (numberRaw, unit) = match.destructured
            val number = normalizeNumber(numberRaw) ?: return null
            return if (unit.startsWith("ری", ignoreCase = true) || unit.startsWith("Rial", ignoreCase = true)) {
                number / 10 // Rial -> Toman
            } else {
                number
            }
        }
        val bareMatch = BARE_AMOUNT_REGEX.find(body)
        if (bareMatch != null) {
            return normalizeNumber(bareMatch.groupValues[1])
        }
        // Unit-less ledger line (e.g. "-2,260,000" with no تومان/ریال word at
        // all). Same anchor requirement as identifyType's fallback: only
        // trusted alongside a balance line, otherwise rejected.
        if (BALANCE_LINE_REGEX.containsMatchIn(body)) {
            val signedMatch = SIGNED_AMOUNT_REGEX.find(body)
            if (signedMatch != null) {
                val number = normalizeNumber(signedMatch.groupValues[2]) ?: return null
                return number / 10
            }
        }
        return null
    }

    private fun normalizeNumber(raw: String): Long? {
        val converted = raw.map { ch ->
            val idx = PERSIAN_DIGITS.indexOf(ch)
            if (idx >= 0) ('0' + idx) else ch
        }.joinToString("")
        val digitsOnly = converted.filter { it.isDigit() }
        return digitsOnly.toLongOrNull()
    }

    private fun buildDescription(body: String, type: TransactionType): String {
        // Take a short, human-readable slice around the matched keyword so the
        // list screen shows something meaningful instead of the full SMS.
        val keyword = (if (type == TransactionType.EXPENSE) EXPENSE_KEYWORDS else INCOME_KEYWORDS)
            .firstOrNull { body.contains(it) }
        val trimmed = body.replace(Regex("\\s+"), " ").trim()
        return if (keyword != null && trimmed.length > 60) {
            val idx = trimmed.indexOf(keyword).coerceAtLeast(0)
            val start = (idx - 15).coerceAtLeast(0)
            val end = (idx + 45).coerceAtMost(trimmed.length)
            "…" + trimmed.substring(start, end) + "…"
        } else {
            trimmed.take(80)
        }
    }
}