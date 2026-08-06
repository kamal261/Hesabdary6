package com.kamal.smsfinance.sms

import com.kamal.smsfinance.data.TransactionType
import com.kamal.smsfinance.util.JalaliDate
import java.util.regex.Pattern

/**
 * Template Engine: Matches known bank SMS formats for reliable parsing.
 * 
 * CRITICAL FIXES from v2.1 critique:
 * 1. Uses `containsMatchIn` (substring) instead of `matches()` (full string match)
 * 2. Tolerant patterns with controlled wildcards - not strict full-match
 * 3. SEMI_RICH templates for fee/insurance transactions (real money movement)
 * 4. Pre-compiled patterns for performance
 */
object TemplateEngine {

    /** Pre-compiled template patterns - lazy initialization to avoid compile-time constant requirement */
    private val compiledTemplates: List<CompiledTemplate> by lazy {
        TEMPLATES.map { template ->
            CompiledTemplate(
                template,
                Pattern.compile(template.regex, Pattern.CASE_INSENSITIVE)
            )
        }
    }

    /**
     * Tries to match the message against known templates.
     * Uses substring matching - NOT full string match.
     * 
     * @param sender SMS sender
     * @param body SMS body text
     * @param timestamp Message timestamp (delivery time as fallback)
     * @return TemplateMatchResult.Matched with extracted ParsedSms, or NoMatch
     */
    fun match(sender: String, body: String, timestamp: Long): TemplateMatchResult {
        if (body.isBlank()) return TemplateMatchResult.NoMatch

        for (compiled in compiledTemplates) {
            val matcher = compiled.pattern.matcher(body)
            if (matcher.find()) {
                val extracted = extractFromMatch(compiled.template, body, sender, timestamp, matcher)
                if (extracted != null) {
                    return TemplateMatchResult.Matched(compiled.template, extracted)
                }
            }
        }
        return TemplateMatchResult.NoMatch
    }

    /**
     * Extracts ParsedSms from a regex match using the template's parsing logic.
     */
    private fun extractFromMatch(
        template: Template,
        body: String,
        sender: String,
        timestamp: Long,
        matcher: java.util.regex.Matcher
    ): ParsedSms? {
        return when (template.richness) {
            TemplateRichness.RICH -> extractRich(template, body, sender, timestamp, matcher)
            TemplateRichness.SEMI_RICH -> extractSemiRich(template, body, sender, timestamp)
            TemplateRichness.OPAQUE -> null // OPAQUE templates don't extract, just signal bank-origin
        }
    }

    /**
     * RICH template: Full extraction (amount, type, balance, tail, txnId, date)
     */
    private fun extractRich(
        template: Template,
        body: String,
        sender: String,
        timestamp: Long,
        matcher: java.util.regex.Matcher
    ): ParsedSms? {
        val amount = extractAmount(body) ?: return null
        val txnDate = SmsDateExtractor.extract(body, timestamp)
        val tail = extractTail(body)
        val description = buildDescription(body, template.type)
        return ParsedSms(
            sender = sender,
            amountToman = amount,
            type = template.type,
            bankName = template.bankName,
            description = description,
            timestamp = txnDate,
            rawSms = body,
            accountTail = tail
        )
    }

    /**
     * SEMI_RICH template: Reliable partial extraction (type + amount + bank signal)
     * Used for: bank fees, insurance premiums, card fees - real transactions with simpler format
     */
    private fun extractSemiRich(
        template: Template,
        body: String,
        sender: String,
        timestamp: Long
    ): ParsedSms {
        val amount = extractAmount(body) ?: 0L // If no amount found, still create with 0 (user will fix)
        val txnDate = SmsDateExtractor.extract(body, timestamp)
        val tail = extractTail(body)
        val description = buildDescription(body, template.type)
        return ParsedSms(
            sender = sender,
            amountToman = amount,
            type = template.type,
            bankName = template.bankName,
            description = description,
            timestamp = txnDate,
            rawSms = body,
            accountTail = tail
        )
    }

    /**
     * Extracts amount from message body using multiple strategies.
     */
    private fun extractAmount(body: String): Long? {
        // Strategy 1: Amount with currency unit
        val amountWithUnit = Regex("""([\d۰-۹][\d۰-۹,٬،./]*)\s*(تومان|ریال|ريال|Rials?|Toman)""", RegexOption.IGNORE_CASE).find(body)
        if (amountWithUnit != null) {
            val (numberRaw, unit) = amountWithUnit.destructured
            val number = normalizeNumber(numberRaw) ?: return null
            return if (unit.startsWith("ری", ignoreCase = true) || unit.startsWith("Rial", ignoreCase = true)) {
                number / 10 // Rial -> Toman
            } else {
                number
            }
        }

        // Strategy 2: Bare amount with "مبلغ" keyword
        val bareAmount = Regex("""مبلغ[:\\s]*([\d۰-۹][\d۰-۹,٬،]*)""").find(body)
        if (bareAmount != null) {
            return normalizeNumber(bareAmount.groupValues[1])
        }

        // Strategy 3: Signed amount (terse ledger lines) - requires balance line anchor
        if (Regex("""(مانده|موجودی)[:\\s]*[\d۰-۹][\d۰-۹,٬]*""").containsMatchIn(body)) {
            val signedMatch = Regex("""([+\-])\s*([\d۰-۹][\d۰-۹,٬]*)""").find(body)
            if (signedMatch != null) {
                val number = normalizeNumber(signedMatch.groupValues[2]) ?: return null
                return number / 10 // Unit-less defaults to Rial
            }
        }

        return null
    }

    private fun extractTail(body: String): String? {
        return Regex("""([*x]{2,}\d{4})|(\d{6}\*\d{4})""").find(body)?.groupValues?.get(1)?.takeLast(4)
    }

    private fun buildDescription(body: String, type: TransactionType): String {
        val keywords = if (type == TransactionType.EXPENSE) {
            listOf("پرداخت قسط", "خرید", "برداشت", "کارمزد", "هزینه", "پرداخت اینترنتی", "پرداخت شد", "انتقال به", "کسر از", "چک", "قبض", "بیمه", "حق بیمه")
        } else {
            listOf("واریز", "دریافت", "واریزی", "به حساب شما", "بازگشت وجه", "سود سپرده")
        }
        val keyword = keywords.firstOrNull { body.contains(it) }
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

    private fun normalizeNumber(raw: String): Long? {
        val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
        val converted = raw.map { ch ->
            val idx = persianDigits.indexOf(ch)
            if (idx >= 0) ('0' + idx) else ch
        }.joinToString("")
        val digitsOnly = converted.filter { it.isDigit() }
        return digitsOnly.toLongOrNull()
    }

    private data class CompiledTemplate(
        val template: Template,
        val pattern: Pattern
    )

    /**
     * TEMPLATES - Known bank SMS formats.
     * 
     * CRITICAL FIX: Fee/insurance transactions are SEMI_RICH templates (real money movement),
     * NOT blocklist patterns.
     */
    private val TEMPLATES = listOf(
        // ===== MELLAT BANK (ملت) =====
        Template(
            id = "mellat_expense_rich",
            regex = "مبلغ\\s*[\\d،,٬]+\\s*تومان\\s*از\\s*حساب\\s*شما\\s*پرید.*مانده.*شناسه",
            richness = TemplateRichness.RICH,
            bankName = "ملت",
            type = TransactionType.EXPENSE,
            description = "ملت - خرید/برداشت با مانده و شناسه"
        ),
        Template(
            id = "mellat_income_rich",
            regex = "واریز\\s*[\\d،,٬]+\\s*(تومان|ریال)\\s*به\\s*حساب\\s*شما.*موجودی.*شناسه",
            richness = TemplateRichness.RICH,
            bankName = "ملت",
            type = TransactionType.INCOME,
            description = "ملت - واریز با موجودی و شناسه"
        ),
        Template(
            id = "mellat_fee_semi",
            regex = "کارمزد.*پیامک|کارمزد.*حساب",
            richness = TemplateRichness.SEMI_RICH,
            bankName = "ملت",
            type = TransactionType.EXPENSE,
            defaultCategory = "کارمزد بانکی",
            description = "ملت - کارمزد پیامک/حساب (تراکنش واقعی مالی)"
        ),
        Template(
            id = "mellat_insurance_semi",
            regex = "بیمه.*نامه.*حق\\s*بیمه|حق\\s*بیمه.*بیمه",
            richness = TemplateRichness.SEMI_RICH,
            bankName = "ملت",
            type = TransactionType.EXPENSE,
            defaultCategory = "بیمه",
            description = "ملت - حق بیمه (تراکنش واقعی مالی)"
        ),

        // ===== TEJARAT BANK (تجارت) =====
        Template(
            id = "tejarat_expense_rich",
            regex = "برداشت\\s*[\\d،,٬]+\\s*تومان.*از\\s*حساب\\s*شما\\s*کسر.*مانده.*شناسه",
            richness = TemplateRichness.RICH,
            bankName = "تجارت",
            type = TransactionType.EXPENSE,
            description = "تجارت - برداشت/کسر از حساب"
        ),
        Template(
            id = "tejarat_fee_semi",
            regex = "کارمزد.*کارت|کارمزد.*سالانه",
            richness = TemplateRichness.SEMI_RICH,
            bankName = "تجارت",
            type = TransactionType.EXPENSE,
            defaultCategory = "کارمزد بانکی",
            description = "تجارت - کارمزد کارت/سالانه"
        ),

        // ===== MELLİ BANK (ملی) =====
        Template(
            id = "melli_expense_rich",
            regex = "پرداخت\\s*[\\d،,٬]+\\s*تومان.*از\\s*حساب\\s*شما.*پرید.*مانده.*پیگیری",
            richness = TemplateRichness.RICH,
            bankName = "ملی",
            type = TransactionType.EXPENSE,
            description = "ملی - پرداخت از حساب"
        ),
        Template(
            id = "melli_fee_semi",
            regex = "کارمزد.*رسیدگی|کارمزد.*تراکنش|کارمزد.*پیامک",
            richness = TemplateRichness.SEMI_RICH,
            bankName = "ملی",
            type = TransactionType.EXPENSE,
            defaultCategory = "کارمزد بانکی",
            description = "ملی - کارمزد رسیدگی/تراکنش/پیامک"
        ),
        Template(
            id = "melli_insurance_semi",
            regex = "بیمه.*حق\\s*بیمه|پریمیوم.*بیمه",
            richness = TemplateRichness.SEMI_RICH,
            bankName = "ملی",
            type = TransactionType.EXPENSE,
            defaultCategory = "بیمه",
            description = "ملی - حق بیمه/پریمیوم"
        ),

        // ===== SAMAN BANK (سامان) =====
        Template(
            id = "saman_income_rich",
            regex = "واریز\\s*[\\d،,٬]+\\s*ریال\\s*به\\s*حساب\\s*شما.*موجودی.*شناسه",
            richness = TemplateRichness.RICH,
            bankName = "سامان",
            type = TransactionType.INCOME,
            description = "سامان - واریز ریال"
        ),
        Template(
            id = "saman_expense_rich",
            regex = "خرید\\s*[\\d،,٬]+\\s*تومان.*مانده.*شناسه",
            richness = TemplateRichness.RICH,
            bankName = "سامان",
            type = TransactionType.EXPENSE,
            description = "سامان - خرید اینترنتی"
        ),

        // ===== PASARGAD BANK (پاسارگاد) =====
        Template(
            id = "pasargad_expense_rich",
            regex = "پاسارگاد.*خرید\\s*[\\d،,٬]+\\s*تومان.*مانده.*شناسه",
            richness = TemplateRichness.RICH,
            bankName = "پاسارگاد",
            type = TransactionType.EXPENSE,
            description = "پاسارگاد - خرید با نام بانک در متن"
        ),

        // ===== BLU BANK (بلو) - Terse ledger style =====
        Template(
            id = "blu_terse_rich",
            regex = "[+\\-]\\s*[\\d،,٬]+\\s*مانده[:\\s]*[\\d،,٬]+",
            richness = TemplateRichness.RICH,
            bankName = "بلو",
            type = TransactionType.EXPENSE, // Sign determines actual type
            description = "بلو - خط خلاصه حسابداری با مانده"
        ),

        // ===== RESALAT BANK (رسالت) - Unit-less ledger =====
        Template(
            id = "resalat_terse_rich",
            regex = "رسالت.*[+\\-]\\s*[\\d،,٬]+\\s*مانده[:\\s]*[\\d،,٬]+",
            richness = TemplateRichness.RICH,
            bankName = "رسالت",
            type = TransactionType.EXPENSE,
            description = "رسالت - خط خلاصه بدون واحد پولی"
        ),

        // ===== GENERIC BANK DEBIT PHRASE (fallback for known senders) =====
        Template(
            id = "generic_bank_debit_semi",
            regex = "از\\s*حساب\\s*شما\\s*(پرید|کسر|برداشت)",
            richness = TemplateRichness.SEMI_RICH,
            bankName = "نامشخص",
            type = TransactionType.EXPENSE,
            description = "عبارت کلیدی کسر از حساب (بانک شناخته‌شده)"
        ),

        // ===== OPAQUE: Known sender but unparseable body =====
        Template(
            id = "mellat_opaque",
            regex = "ملت|Mellat|MELLAT|10000210|10000211",
            richness = TemplateRichness.OPAQUE,
            bankName = "ملت",
            type = TransactionType.EXPENSE,
            description = "ملت - فرستنده شناخته‌شده، متن پارس‌نشده"
        ),
        Template(
            id = "tejarat_opaque",
            regex = "تجارت|Tejarat|10000017",
            richness = TemplateRichness.OPAQUE,
            bankName = "تجارت",
            type = TransactionType.EXPENSE,
            description = "تجارت - فرستنده شناخته‌شده"
        ),
        Template(
            id = "melli_opaque",
            regex = "ملی|BMI|10000019|بانک\\s*ملی",
            richness = TemplateRichness.OPAQUE,
            bankName = "ملی",
            type = TransactionType.EXPENSE,
            description = "ملی - فرستنده شناخته‌شده"
        ),
        Template(
            id = "saman_opaque",
            regex = "سامان|Saman|SAMAN|10000770|10005010",
            richness = TemplateRichness.OPAQUE,
            bankName = "سامان",
            type = TransactionType.EXPENSE,
            description = "سامان - فرستنده شناخته‌شده"
        ),
        Template(
            id = "pasargad_opaque",
            regex = "پاسارگاد|Pasargad|PASARGAD|10000068|500068",
            richness = TemplateRichness.OPAQUE,
            bankName = "پاسارگاد",
            type = TransactionType.EXPENSE,
            description = "پاسارگاد - فرستنده شناخته‌شده"
        ),
        Template(
            id = "sepah_opaque",
            regex = "سپه|Sepah|SEPAH|10009999|10000155",
            richness = TemplateRichness.OPAQUE,
            bankName = "سپه",
            type = TransactionType.EXPENSE,
            description = "سپه - فرستنده شناخته‌شده"
        ),
        Template(
            id = "enbank_opaque",
            regex = "اقتصاد\\s*نوین|EN\\s*Bank|10000079",
            richness = TemplateRichness.OPAQUE,
            bankName = "اقتصاد نوین",
            type = TransactionType.EXPENSE,
            description = "اقتصاد نوین - فرستنده شناخته‌شده"
        ),
        Template(
            id = "parsian_opaque",
            regex = "پارسیان|Parsian|10000622",
            richness = TemplateRichness.OPAQUE,
            bankName = "پارسیان",
            type = TransactionType.EXPENSE,
            description = "پارسیان - فرستنده شناخته‌شده"
        ),
        Template(
            id = "ayandeh_opaque",
            regex = "آینده|Ayandeh|10008485",
            richness = TemplateRichness.OPAQUE,
            bankName = "آینده",
            type = TransactionType.EXPENSE,
            description = "آینده - فرستنده شناخته‌شده"
        ),
        Template(
            id = "city_opaque",
            regex = "بانک\\s*شهر|City\\s*Bank|10004555",
            richness = TemplateRichness.OPAQUE,
            bankName = "شهر",
            type = TransactionType.EXPENSE,
            description = "شهر - فرستنده شناخته‌شده"
        ),
        Template(
            id = "day_opaque",
            regex = "بانک\\s*دی|Day\\s*Bank|10009898",
            richness = TemplateRichness.OPAQUE,
            bankName = "دی",
            type = TransactionType.EXPENSE,
            description = "دی - فرستنده شناخته‌شده"
        ),
        Template(
            id = "karafarin_opaque",
            regex = "کارآفرین|Karafarin|10008717",
            richness = TemplateRichness.OPAQUE,
            bankName = "کارآفرین",
            type = TransactionType.EXPENSE,
            description = "کارآفرین - فرستنده شناخته‌شده"
        ),
        Template(
            id = "maskan_opaque",
            regex = "مسکن|Maskan|10000129",
            richness = TemplateRichness.OPAQUE,
            bankName = "مسکن",
            type = TransactionType.EXPENSE,
            description = "مسکن - فرستنده شناخته‌شده"
        ),
        Template(
            id = "resalat_opaque",
            regex = "رسالت|Resalat|قرض\\s*الحسنه\\s*رسالت",
            richness = TemplateRichness.OPAQUE,
            bankName = "رسالت",
            type = TransactionType.EXPENSE,
            description = "رسالت - فرستنده شناخته‌شده"
        ),
        Template(
            id = "blu_opaque",
            regex = "بلو|Blu|BLU|بلوبانک",
            richness = TemplateRichness.OPAQUE,
            bankName = "بلو",
            type = TransactionType.EXPENSE,
            description = "بلو - فرستنده شناخته‌شده"
        ),
        Template(
            id = "keshavarzi_opaque",
            regex = "کشاورزی|Keshavarzi|10000160",
            richness = TemplateRichness.OPAQUE,
            bankName = "کشاورزی",
            type = TransactionType.EXPENSE,
            description = "کشاورزی - فرستنده شناخته‌شده"
        ),
        Template(
            id = "refah_opaque",
            regex = "رفاه|Refah|10000144|رفاه\\s*کارگران",
            richness = TemplateRichness.OPAQUE,
            bankName = "رفاه",
            type = TransactionType.EXPENSE,
            description = "رفاه - فرستنده شناخته‌شده"
        )
    )
}