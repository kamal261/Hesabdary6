// SmsFinance file version: 5 — this session, found via a real fresh export where Blu Bank
// (over half the user's transaction volume) was showing up entirely as "نامشخص": the body-text
// bank-name fallback required identifiers longer than 3 characters, which silently excluded
// "بلو"/"Blu"/"BLU" (all exactly 3 chars) -- see identifyBank(). Also added two IGNORE_KEYWORDS
// entries for false positives found in the same export: a carrier recharge-offer promo template
// counted as a withdrawal, and a bill-issuance notice ("قبض صادره شده", informational, not a
// payment) counted as a withdrawal.
package com.kamal.smsfinance.sms

import com.kamal.smsfinance.data.TransactionType

/**
 * Outcome of trying to parse one SMS.
 * - Recognized: a full transaction was extracted.
 * - Unidentified: the message looked bank-related (known bank sender, or a
 *   type keyword matched) but a full transaction couldn't be extracted --
 *   kept for the user to review rather than silently dropped.
 * - Ignored: confidently not a transaction (blank, OTP/promo/balance-check,
 *   or ordinary non-bank text) -- never stored, never shown.
 */
// sealed class SmsParseResult {
//     data class Recognized(val parsed: ParsedSms) : SmsParseResult()
//     data class Unidentified(val sender: String, val body: String, val timestamp: Long) : SmsParseResult()
//     object Ignored : SmsParseResult()
// }

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
        "پرداخت شد", "انتقال به", "کسر از", "کسر گردید", "از حساب شما کسر", "از حساب شما پرید",
        "چک", "قبض", "حق بیمه"
    )
    private val INCOME_KEYWORDS = listOf(
        "واریز", "دریافت وجه", "دریافت مبلغ", "واریزی", "به حساب شما", "بازگشت وجه", "سود سپرده"
    )

    // Messages that only mention balance / OTP / promos, never a real txn.
    private val IGNORE_KEYWORDS = listOf(
        "موجودی شما", "رمز یکبار مصرف", "رمز پویا", "کد تایید", "کد فعال",
        "تخفیف", "جشنواره", "تبلیغ", "OTP", "قرعه‌کشی", "قرعه کشی", "جایزه", "تسهیلات",
        // Carrier recharge-offer promo template ("with every 100,000 Toman recharge or credit
        // top-up, get X") -- was being counted as a real 100,000 Toman withdrawal, repeatedly,
        // in a real export.
        "با خرید هر شارژ",
        // Bill *issuance* notice ("a bill has been issued for you") -- informational, not a
        // payment confirmation. Was being counted as a completed withdrawal.
        "قبض صادره شده",
        // Third-party wallet apps (Avanoo and others) are not a valid transaction source per
        // product policy: wallet credit is itself ultimately funded by a real bank transaction,
        // so counting the wallet deduction too would double-count the same money. Rejecting on
        // the generic phrase "کیف پول" ("wallet") rather than naming individual apps (اوانو,
        // اسنپ, تپسی, ...) is deliberate -- no real bank SMS uses this phrase (banks report
        // "حساب"/"کارت", never "کیف پول"), so this rule doesn't need to be updated every time a
        // new wallet app shows up, and can't reject a genuine bank message.
        "کیف پول",
        // Bank admin/security notices that mention "ثبت شده" (registered) or phone-number
        // change confirmations -- real recurring template (confirmed: ResalatBank sends this
        // every time internet-banking mobile number is updated), never a transaction. Without
        // this, it lands in the Unidentified queue purely because the sender is a known bank
        // (see the "Zero Silent Loss" design note on parse()) even though it's pure noise.
        "شماره همراه ثبت شده",
        // Confirmed recurring templates with zero transactional content, from real bank/carrier
        // senders: Pasargad's pure login/OTP-delivery notices (no amount ever), and a carrier's
        // "this phone number is now available for purchase" listing (not a purchase itself).
        "ورود به وی بنک", "کد شناسایی ورود", "کد ارسالی محرمانه",
        "بسته مکالمه همراهی",
        // Carrier missed-call notification service ("989914") -- confirmed from a real export
        // to be, by far, the single largest source of Unidentified-queue noise (87% of one
        // user's queue). Frequently carries an embedded USSD data-package ad ("خرید بسته‌های
        // دیتا با #2*100*") whose "خرید" keyword survives promo-tail truncation since it comes
        // *before* the "#" marker, not after -- so this needed its own rejection, truncation
        // alone wasn't enough.
        "تماس بی پاسخ", "تماس بی‌پاسخ",
        // Carrier "bill issued, pay for a free data/call bonus" promo -- variant wording of the
        // "قبض صادره شده" rule already above ("صادر شد" vs "صادره شده").
        "قبض شما صادر"
    )

    // A known promotional "tail" is truncated off before parsing, so a real transaction
    // bundled with an ad (e.g. "...انجام شد. شگفت‌زده شوید! ...") still gets parsed correctly
    // from the part before the ad.
    private val PROMO_TAIL_MARKERS = listOf("شگفت زده شوید", "شگفت‌زده شوید", "طرح ویژه")
    private val PROMO_USSD_REGEX = Regex("""#\d+\*""") // e.g. "#4444*" dial-a-code prompts

    // A raw link almost never appears in a genuine bank transaction SMS, but is near-universal
    // in loan-offer, delivery-company, and carrier/marketing promotional SMS. Covers both
    // prefixed links (https://, www.) and bare domains (e.g. "sell.ir", common in carrier ads
    // that omit the protocol). Only trusted as a signal when the sender ISN'T a known bank -- a
    // real bank occasionally does include a receipt/tracking link.
    private val URL_REGEX = Regex(
        """https?://|www\.|\b[a-zA-Z0-9-]+\.(ir|com|net|org|io)\b""",
        RegexOption.IGNORE_CASE
    )

    private val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    // Matches amounts like: "مبلغ: 500,000 تومان", "500000 ريال", "1,250,000ریال". Supports
    // plain comma, Persian thousands separator ٬, and the Arabic/Persian comma ، (U+060C) --
    // some banks use ، instead of a plain comma (e.g. "150،000").
    private val AMOUNT_REGEX = Regex(
        """([\d۰-۹][\d۰-۹,٬،./]*)\s*(تومان|ریال|ريال|Rials?|Toman)""",
        RegexOption.IGNORE_CASE
    )

    // Fallback: a bare number of 5+ digits immediately followed by common
    // currency-less bank phrasing ("مبلغ 500000 از").
    private val BARE_AMOUNT_REGEX = Regex("""مبلغ[:\s]*([\d۰-۹][\d۰-۹,٬،]*)""")

    // Resalat's own "خرید ساده" (simple purchase) receipt template labels the amount as
    // "مبلغ (ریال): 125" -- unit inside parentheses, between the label and the number -- which
    // neither AMOUNT_REGEX (needs "number unit" adjacency) nor BARE_AMOUNT_REGEX (needs the
    // number right after "مبلغ") can match. Confirmed against a real export: several genuine
    // small purchases (125, 842 Rial) were landing in the Unidentified queue purely because of
    // this label format, not because anything about them was actually ambiguous.
    private val LABELED_AMOUNT_REGEX = Regex("""مبلغ\s*\((ریال|تومان)\)\s*[:\s]*([\d۰-۹][\d۰-۹,٬،]*)""")

    private val BALANCE_LINE_REGEX = Regex("""(مانده|موجودی)[:\s]*[\d۰-۹][\d۰-۹,٬،]*""")

    // For terse, keyword-less, unit-less ledger lines. Two real formats exist and BOTH must be
    // supported:
    //   - trailing sign, digits then sign ("500,000-")  -- Melli, Pasargad
    //   - leading sign, sign then digits ("-9,000")      -- Resalat
    // Only trusted when a balance line ("مانده:"/"موجودی:") is also present, and the search is
    // restricted to the text BEFORE that balance line. Both anchors matter: promotional text,
    // phone numbers, and price ranges also contain +/- next to digits but essentially never
    // report a balance line; and the date/time trailer that follows the balance line (e.g.
    // "0708-13:07") has its own hyphen between date and time components, which an unrestricted
    // search matches by accident -- confirmed against a real 659-message Melli export, where
    // this misread a 1,800,000 Rial withdrawal as an amount of "1".
    private val LEDGER_TRAILING_SIGN_REGEX = Regex("""([\d۰-۹][\d۰-۹,٬،]*)\s*([+\-])(?!\d)""")
    private val LEDGER_LEADING_SIGN_REGEX = Regex("""([+\-])\s*([\d۰-۹][\d۰-۹,٬،]*)""")

    // Card/account tail, e.g. "...1234" or "حساب ****1234"
    private val TAIL_REGEX = Regex("""[*x]{2,}(\d{4})""")

    // Arabic-script Unicode code points that visually resemble Persian letters but are
    // different characters and never equal under Kotlin string matching. Bank Melli's SMS
    // gateway sends "بانك ملي" (Arabic ك ي) rather than "بانک ملی" (Persian ک ی) -- without this,
    // no keyword or bank-name match involving these letters can ever succeed against that text.
    private fun normalizeArabicToPersian(text: String): String = text
        .replace('ك', 'ک')
        .replace('ي', 'ی')
        .replace('ة', 'ه')
        .replace('ٱ', 'ا')

    // Iranian personal mobile numbers (any carrier: 091x/092x/093x/099x...), in any of the
    // forms senders normally appear as: 09123456789, +989123456789, 00989123456789, 9123456789.
    // Checked before any keyword/regex parsing of the body, so an ordinary text from a friend
    // about money never has a chance to look like a transaction. Real bank senders are always
    // either a short numeric code (5-8 digits, no leading 09) or a text identifier ("Mellat",
    // "BMI", ...) -- never a full personal mobile number -- so this can't reject a genuine
    // bank message.
    private val PERSONAL_MOBILE_REGEX = Regex("""^(\+98|0098|98|0)?9\d{9}$""")

    private fun isPersonalMobileSender(sender: String): Boolean {
        val normalized = sender.replace(Regex("""[\s\-()]"""), "")
        return PERSONAL_MOBILE_REGEX.matches(normalized)
    }

    // Specific, confirmed non-bank senders that pass the shortcode-shape check (short numeric
    // id) and send frequent, varied marketing copy -- keyword/content rules can't reliably
    // catch this content because it's simply ordinary Persian, and it changes constantly.
    // Sender identity is stable in a way content never is, so this list is a more durable fix
    // than chasing new ad phrasings one at a time. Confirmed from a real export: Digikala's
    // shortcode alone accounted for a large share of the Unidentified queue, including messages
    // with no link at all ("مرضیه جان، خریدت با ارسال سه ساعته می‌رسه...") that no keyword or
    // URL rule could have caught anyway.
    private val NON_BANK_SENDER_IDS = listOf("5000333", "7171") // Digikala, ringtone/caller-tune service

    private fun isKnownNonBankSender(sender: String): Boolean {
        val normalized = sender.replace(Regex("""[\s\-()+]"""), "")
        return NON_BANK_SENDER_IDS.any { normalized.endsWith(it) }
    }

    // Real bank SMS come from either a known bank's own identifier (BANK_SENDERS) or a generic
    // numeric "shortcode" -- Iranian SMS gateways issue short digit-only sender ids (roughly
    // 4-9 digits) to businesses generally, banks included. A sender that's an alphabetic brand
    // NAME ("DIGIKALA", "BimehIran") or a full 10-digit number (closer to personal-mobile
    // length than a shortcode -- confirmed against a real "+9890005252" marketing sender) is
    // never a bank we don't already recognize by name. This matters because content-keyword
    // matching alone (identifyType) is too weak a signal for an unverified sender: "خرید",
    // "بیمه", "دریافت" are ordinary Persian words that appear constantly in non-bank SMS too,
    // and no fixed keyword list can ever fully close that gap. Restricting *which senders* a
    // bare keyword match is trusted for closes a whole class of false positives at once,
    // instead of chasing them one phrase at a time.
    private val SHORTCODE_REGEX = Regex("""^\+?\d{4,9}$""")
    private fun looksLikeBankShortcode(sender: String): Boolean =
        SHORTCODE_REGEX.matches(sender.replace(Regex("""[\s\-()]"""), ""))

    // Bank fees and insurance premiums are real money leaving the account, never blocklisted --
    // just given a sensible default category instead of "بدون دسته" when no SmartRule has
    // claimed them yet. Checked only after a message is already headed toward Recognized (type +
    // amount both resolved), so this can't itself cause a false positive.
    private fun defaultCategoryNameFor(type: TransactionType, body: String): String? {
        if (type != TransactionType.EXPENSE) return null
        if (body.contains("کارمزد")) return "کارمزد بانکی"
        if (body.contains("بیمه")) return "بیمه"
        return null
    }

    /** Extracts (isExpense, amountToman) from a terse ledger line, or null if no balance line
     * is present or no signed number can be found before it. See LEDGER_*_SIGN_REGEX docs. */
    private fun extractLedgerSignedAmount(body: String): Pair<Boolean, Long>? {
        val balanceMatch = BALANCE_LINE_REGEX.find(body) ?: return null
        val window = body.substring(0, balanceMatch.range.first)

        val trailing = LEDGER_TRAILING_SIGN_REGEX.findAll(window).lastOrNull()
        if (trailing != null) {
            val amount = normalizeNumber(trailing.groupValues[1]) ?: return null
            return (trailing.groupValues[2] == "-") to (amount / 10)
        }
        val leading = LEDGER_LEADING_SIGN_REGEX.findAll(window).lastOrNull()
        if (leading != null) {
            val amount = normalizeNumber(leading.groupValues[2]) ?: return null
            return (leading.groupValues[1] == "-") to (amount / 10)
        }
        return null
    }

    /** Cuts off a known promotional "tail" so it doesn't interfere with parsing the real
     * transaction sentence before it. */
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

    fun parse(sender: String, body: String, timestamp: Long): SmsParseResult {
        if (body.isBlank()) return SmsParseResult.Ignored
        if (isPersonalMobileSender(sender)) return SmsParseResult.Ignored
        if (isKnownNonBankSender(sender)) return SmsParseResult.Ignored

        val normalizedBody = normalizeArabicToPersian(body)
        if (IGNORE_KEYWORDS.any { normalizedBody.contains(it) }) return SmsParseResult.Ignored

        val isKnownBank = isKnownBankSender(sender)
        // A URL is a strong promotional/redirect signal. Keeping it out even for a known bank
        // sender avoids treating tracking and loan-offer links as financial transactions.
        if (URL_REGEX.containsMatchIn(normalizedBody)) return SmsParseResult.Ignored

        val workingBody = truncatePromoTail(normalizedBody)

        val type = identifyType(workingBody)
        val amount = if (type != null) extractAmountToman(workingBody) else null

        if (type != null && amount != null && amount > 0) {
            val bank = identifyBank(sender, normalizedBody) ?: "نامشخص"
            val tail = TAIL_REGEX.find(normalizedBody)?.groupValues?.get(1)
            val description = buildDescription(workingBody, type)
            return SmsParseResult.Recognized(
                ParsedSms(
                    sender = sender,
                    amountToman = amount,
                    type = type,
                    bankName = bank,
                    description = description,
                    timestamp = timestamp,
                    rawSms = body,
                    accountTail = tail,
                    defaultCategoryName = defaultCategoryNameFor(type, workingBody)
                )
            )
        }

        // Couldn't fully parse. Only worth flagging for review if there was
        // *some* bank-like signal -- a type keyword matched (amount just
        // failed to extract), or the sender is a known bank short-code.
        // Otherwise this is ordinary non-bank text and is safely ignored,
        // so the review list doesn't fill up with unrelated personal SMS.
        val looksBankRelated = isKnownBank || (type != null && looksLikeBankShortcode(sender))
        return if (looksBankRelated) SmsParseResult.Unidentified(sender, body, timestamp) else SmsParseResult.Ignored
    }

    // For an identifier that's all digits (a shortcode), a substring "contains" match is
    // dangerously loose: a long sender number can coincidentally CONTAIN a shorter real
    // shortcode purely by chance (confirmed twice against real data -- Iran Post's
    // "98100000193" contains Bank Melli's "10000019", and an immigration-consulting ad's
    // "9810004555" contains Bank Shahr's "10004555"). For a numeric identifier, this requires
    // the sender -- after stripping a leading +98/0098/98/0 country-code-style prefix -- to
    // EXACTLY equal the identifier, not just contain it somewhere. Text identifiers ("Resalat",
    // "ملی") keep substring matching, since real senders often add a suffix ("ResalatBank").
    private fun senderMatchesIdentifier(sender: String, identifier: String): Boolean {
        if (identifier.all { it.isDigit() }) {
            val strippedSender = sender.replace(Regex("""^(\+98|0098|98|0)"""), "")
            return strippedSender == identifier
        }
        return sender.contains(identifier, ignoreCase = true)
    }

    private fun isKnownBankSender(sender: String): Boolean =
        BANK_SENDERS.values.any { identifiers -> identifiers.any { senderMatchesIdentifier(sender, it) } }

    private fun identifyBank(sender: String, body: String): String? {
        for ((bankName, identifiers) in BANK_SENDERS) {
            if (identifiers.any { senderMatchesIdentifier(sender, it) }) return bankName
        }
        // Fall back to scanning the body text itself for a bank name mention.
        // Threshold is >= 3, not > 3: a handful of real bank names/abbreviations are exactly
        // 3 characters ("بلو", "Blu", "BLU", "ملت", "سپه") and were being silently excluded by
        // a stricter >3 guard -- confirmed against a real export where Blu Bank (matching
        // "بلو برداشت پول..." in the SMS body, not the longer "بلوبانک") accounted for over
        // half the user's transactions and every single one landed as "نامشخص". 2-character
        // identifiers ("دی") stay excluded -- too short to be a safe substring match.
        for ((bankName, identifiers) in BANK_SENDERS) {
            if (identifiers.any { it.length >= 3 && body.contains(it, ignoreCase = true) }) return bankName
        }
        return null
    }

    private fun identifyType(body: String): TransactionType? {
        val hasExpense = EXPENSE_KEYWORDS.any { bodyHasKeyword(body, it) }
        val hasIncome = INCOME_KEYWORDS.any { bodyHasKeyword(body, it) }
        return when {
            hasIncome && !hasExpense -> TransactionType.INCOME
            hasExpense && !hasIncome -> TransactionType.EXPENSE
            // Both matched (e.g. "کارمزد" inside a deposit message) -- prefer
            // whichever keyword appears first in the text, it's usually the
            // primary action.
            hasExpense && hasIncome -> {
                val expenseIdx = EXPENSE_KEYWORDS.minOf { kw -> keywordIndex(body, kw).let { if (it < 0) Int.MAX_VALUE else it } }
                val incomeIdx = INCOME_KEYWORDS.minOf { kw -> keywordIndex(body, kw).let { if (it < 0) Int.MAX_VALUE else it } }
                if (expenseIdx <= incomeIdx) TransactionType.EXPENSE else TransactionType.INCOME
            }
            // No keyword at all -- terse ledger-style SMS (e.g. Melli, Resalat Bank).
            else -> extractLedgerSignedAmount(body)?.let { (isExpense, _) ->
                if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
            }
        }
    }

    // "واریز" alone would also match the ad-imperative "واریزکن" ("go deposit/pay [us]") found
    // in promotional copy, not just real deposit notifications (real data: "...تومان واریزکن و
    // یک سایت حرفه‌ای تحویل بگیر..." -- a web-design ad -- was misread as income). This
    // negative-lookahead excludes that specific imperative form while still matching "واریز
    // شد", "واریز به", etc.
    private val VARIZ_REGEX = Regex("""واریز(?!\s*کن)""")

    private fun keywordIndex(body: String, keyword: String): Int =
        if (keyword == "واریز") VARIZ_REGEX.find(body)?.range?.first ?: -1
        else body.indexOf(keyword)

    private fun bodyHasKeyword(body: String, keyword: String): Boolean = keywordIndex(body, keyword) >= 0

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
            val number = normalizeNumber(bareMatch.groupValues[1]) ?: return null
            return number / 10 // No unit word at all -> defaults to Rial, same as every other branch here
        }
        val labeledMatch = LABELED_AMOUNT_REGEX.find(body)
        if (labeledMatch != null) {
            val number = normalizeNumber(labeledMatch.groupValues[2]) ?: return null
            return if (labeledMatch.groupValues[1].startsWith("ری")) number / 10 else number
        }
        // Unit-less ledger line (e.g. "500,000-" or "-2,260,000", no تومان/ریال word at all).
        return extractLedgerSignedAmount(body)?.second
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
