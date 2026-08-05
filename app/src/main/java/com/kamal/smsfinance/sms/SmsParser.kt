// SmsFinance file version: 2 — parse() now returns a sealed SmsParseResult (Recognized/Unidentified/Ignored) instead of a nullable ParsedSms, so bank-like messages that fail to parse are surfaced for review instead of silently dropped
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
    val accountTail: String?
)

/**
 * Outcome of trying to parse one SMS.
 * - Recognized: a full transaction was extracted.
 * - Unidentified: the message looked bank-related (known bank sender, or a
 *   type keyword matched) but a full transaction couldn't be extracted --
 *   kept for the user to review rather than silently dropped.
 * - Ignored: confidently not a transaction (blank, OTP/promo/balance-check,
 *   or ordinary non-bank text) -- never stored, never shown.
 */
sealed class SmsParseResult {
    data class Recognized(val parsed: ParsedSms) : SmsParseResult()
    data class Unidentified(val sender: String, val body: String, val timestamp: Long) : SmsParseResult()
    object Ignored : SmsParseResult()
}

/**
 * Parses Iranian bank SMS messages into structured transaction data.
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
 *   almost never do. This anchor prevents phone numbers, discount codes,
 *   and price ranges (which also contain +/- next to digits) from being
 *   misread as transactions.
 * - Ambiguous or promotional messages ("تخفیف", "تبلیغ") are rejected
 *   outright so they never get filed as false transactions.
 * - A message that has *some* bank-like signal (known sender, or a type
 *   keyword) but still can't be fully parsed is returned as Unidentified
 *   instead of being dropped -- see SmsParseResult.
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
        "بلو" to listOf("Blu", "BLU", "بلو", "بلوبانک")
    )

    // Keyword -> transaction type. Order matters: more specific phrases first.
    private val EXPENSE_KEYWORDS = listOf(
        "پرداخت قسط", "خرید", "برداشت", "کارمزد", "هزینه", "پرداخت اینترنتی",
        "پرداخت شد", "انتقال به", "کسر از", "چک", "قبض"
    )
    private val INCOME_KEYWORDS = listOf(
        "واریز", "دریافت", "واریزی", "به حساب شما", "بازگشت وجه", "سود سپرده"
    )

    // Messages that only mention balance / OTP / promos, never a real txn.
    private val IGNORE_KEYWORDS = listOf(
        "موجودی شما", "رمز یکبار مصرف", "کد تایید", "تخفیف", "جشنواره",
        "تبلیغ", "کد فعال", "OTP"
    )

    // Strong negative signals — presence of any immediately marks the message
    // as NOT a genuine transaction (loan/credit promos, OTP codes, courier
    // parcels, phishing, contests with links). Based on researched Iranian
    // bank SMS patterns: genuine transaction SMS never contain these.
    // NOTE: "رسید" is intentionally NOT here — a genuine "رسید خرید" must pass;
    // رسید+link (the card-to-card scam) is already caught by URL_REGEX below.
    private val PROMO_REJECT_KEYWORDS = listOf(
        "تسهیلات", "قرض الحسنه", "ضامن", "قسط", "قرعه‌کشی", "جایزه",
        "به ارزش", "اعتبار", "دریافت و ثبت", "برنده", "مشاوره",
        "اسم کارت", "سهام عدالت", "ابلاغیه", "کد ملی", "به‌روزرسانی"
    )

    // OTP footer signatures that precede a one-time code (never a transaction).
    private val OTP_SIGNATURES = listOf(
        "رمز پویا", "رمز یکبار مصرف", "کد تایید", "رمز تأیید",
        "رمز اینترنتی", "کد فعال‌سازی", "OTP"
    )

    // Any URL — genuine bank transaction SMS never contain links.
    private val URL_REGEX = Regex("""https?://|www\.|t\.me/|bit\.ly/|\.ir/|\.com/""")

    // Real transaction SMS almost always report a balance (مانده/موجودی).
    // Used as a REQUIRED anchor for terse/keyword-less cases, and as a strong
    // positive confirmation when a type+amount is otherwise ambiguous.
    private val BALANCE_KEYWORD_REGEX = Regex("""(مانده|موجودی|مبلغ مانده|موجودی حساب)""")

    private fun looksPromotionalOrOtp(body: String): Boolean {
        // URLs are a hard kill: genuine bank transaction SMS never carry links.
        if (URL_REGEX.containsMatchIn(body)) return true

        // OTP signatures: "رمز پویا ... code" etc. — these are 2FA, not txn.
        if (OTP_SIGNATURES.any { body.contains(it) }) return true

        // Marketing loan/credit/contest words.
        if (PROMO_REJECT_KEYWORDS.any { body.contains(it) }) return true

        return false
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
    private val BARE_AMOUNT_REGEX = Regex("""مبلغ[:\s]*([\d۰-۹][\d۰-۹,٬]*)""")

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
    private val BALANCE_LINE_REGEX = Regex("""(مانده|موجودی)[:\s]*[\d۰-۹][\d۰-۹,٬]*""")

    // Card/account tail, e.g. "...1234", "حساب ****1234", or "621986*0771"
    private val TAIL_REGEX = Regex("""([*x]{2,}\d{4})|(\d{6}\*\d{4})""")

    // Phrases that only appear in genuine bank debit alerts (bill payment
    // deducted from the account). "بابت قبض ... از حساب شما پرید" is the
    // classic bank phrasing -- telecoms say "پرداخت موفق بود", not "پرید".
    private val BANK_DEBIT_PHRASES = listOf("از حساب شما پرید", "از حساب شما کسر", "از حساب شما برداشت")

    // Transaction identifiers that only appear on genuine bank/ledger SMS.
    private val TXN_ID_KEYWORDS = listOf("شناسه", "کد پیگیری", "شماره تراکنش", "پیگیری")

    /**
     * Genuine bank transactions come ONLY from the bank's own sender line
     * (shortcode like 20000 / 2000766 / 3000766 / 15560026, or a bank name
     * like Mellat / TejaratBank / بلو). Service-provider SMS (telecom bill
     * payment, courier, loan ads) look similar on the surface -- amount +
     * transaction id -- but are NOT sent by a bank and must never be filed.
     *
     * Acceptance rule:
     * 1. sender is a known bank sender, OR
     * 2. the body names the bank itself (e.g. "بلو برداشت پول ..." -- بلو's
     *    real-world SMS carry the bank name in the body, not the sender), OR
     * 3. the body carries a strong bank signature (a balance line AND a card
     *    tail / transaction id).
     * A lone amount + "شناسه تراکنش" from a non-bank sender (e.g. "پرداخت
     * صورتحساب ... انجام شد" from a telecom) is rejected.
     */
    private fun isBankOriginated(sender: String, body: String): Boolean {
        if (isKnownBankSender(sender)) return true
        // Some banks identify themselves only in the body. Require the bank
        // name to be followed by a separator so "بلوتوث" is not mistaken for
        // the bank "بلو".
        if (BANK_SENDERS.values.any { ids ->
                ids.any { id ->
                    id.length >= 3 && (body.contains("$id ") || body.contains("$id\n") || body.contains("$id،"))
                }
            }) return true
        // A real balance line (مانده/موجودی + number) is a near-universal
        // bank signature: telecoms, shops, ads and personal SMS never report
        // the account balance. If the body has one, treat it as bank-originated.
        if (BALANCE_LINE_REGEX.containsMatchIn(body)) return true
        // Classic bank debit phrasing ("از حساب شما پرید/کسر/برداشت").
        if (BANK_DEBIT_PHRASES.any { body.contains(it) }) return true
        // A card/account tail (****1234 or 621986*0771) is a strong bank
        // signature -- telecoms, shops and ads never print one. It is
        // sufficient on its own.
        if (TAIL_REGEX.containsMatchIn(body)) return true
        // A transaction id without any other bank signal is NOT enough:
        // telecom bill-payment confirmations also print "شناسه تراکنش".
        return TXN_ID_KEYWORDS.any { body.contains(it) } && BALANCE_KEYWORD_REGEX.containsMatchIn(body)
    }

    fun parse(sender: String, body: String, timestamp: Long): SmsParseResult {
        if (body.isBlank()) return SmsParseResult.Ignored
        if (IGNORE_KEYWORDS.any { body.contains(it) }) return SmsParseResult.Ignored
        // Hard-reject promotional/OTP/phishing SMS before any amount/type parsing.
        // A message that has a URL, an OTP signature, or a marketing keyword is
        // never a genuine bank transaction, regardless of what numbers it carries.
        if (looksPromotionalOrOtp(body)) return SmsParseResult.Ignored

        val type = identifyType(body)
        val amount = if (type != null) extractAmountToman(body) else null

        if (type != null && amount != null && amount > 0 && isBankOriginated(sender, body)) {
            val bank = identifyBank(sender, body) ?: "نامشخص"
            val tail = TAIL_REGEX.find(body)?.groupValues?.get(1)
            val description = buildDescription(body, type)
            // Use the transaction date printed INSIDE the SMS when present;
            // fall back to the delivery timestamp otherwise.
            val txnDate = SmsDateExtractor.extract(body, timestamp)
            return SmsParseResult.Recognized(
                ParsedSms(
                    sender = sender,
                    amountToman = amount,
                    type = type,
                    bankName = bank,
                    description = description,
                    timestamp = txnDate,
                    rawSms = body,
                    accountTail = tail
                )
            )
        }

        // Couldn't fully parse. Only worth flagging for review if there was
        // *some* bank-like signal -- a type keyword matched (amount just
        // failed to extract), or the sender is a known bank short-code.
        // Otherwise this is ordinary non-bank text and is safely ignored,
        // so the review list doesn't fill up with unrelated personal SMS.
        val looksBankRelated = type != null || isKnownBankSender(sender)
        return if (looksBankRelated) SmsParseResult.Unidentified(sender, body, timestamp) else SmsParseResult.Ignored
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
