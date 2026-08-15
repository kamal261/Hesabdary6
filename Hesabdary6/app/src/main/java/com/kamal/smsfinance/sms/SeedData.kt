package com.kamal.smsfinance.sms

import com.kamal.smsfinance.data.TransactionType

/**
 * Seed Data: Initial blocklist patterns and templates.
 * 
 * CRITICAL FIXES from v2.1 critique:
 * 1. Fee/insurance transactions are TEMPLATES (SEMI_RICH), NOT blocklist
 * 2. Blocklist ONLY contains zero-financial-impact patterns (OTP, promo, phishing)
 * 3. All patterns use substring matching (containsMatchIn equivalent)
 */
object SeedData {

    /**
     * Initial blocklist patterns - ONLY for messages with ZERO financial impact.
     * These are inserted into BlockPattern table on first app launch.
     */
    val INITIAL_BLOCK_PATTERNS = listOf(
        // OTP / 2FA
        BlockPattern("otp_dynamic", "رمز\\s*(پویا|یکبار|مؤید|اینترنتی|موقت)", "OTP رمز پویا/یکبار مصرف"),
        BlockPattern("otp_verification", "کد\\s*(تایید|فعال.*سازی|احراز)", "OTP کد تایید/فعال‌سازی"),
        BlockPattern("otp_generic", "رمز\\s*اینترنتی|OTP|one.time.password", "OTP عمومی"),
        
        // Promotional loans/credits
        BlockPattern("promo_loan", "تسهیلات|قرض\\s*الحسنه|ضامن|قسط\\s*بندی", "تبلیغ تسهیلات/قرض"),
        BlockPattern("promo_contest", "قرعه.*کشی|جایزه|برنده|به\\s*ارزش|اعتبار.*دریافت", "تبلیغ قرعه‌کشی/جایزه"),
        BlockPattern("promo_investment", "سهام\\s*عدالت|ابلاغیه|مشاوره.*سرمایه", "تبلیغ سهام/سرمایه‌گذاری"),
        
        // Phishing/scam
        BlockPattern("phishing_card", "اسم\\s*کارت|کد\\s*ملی|به.*روزرسانی.*اطلاعات", "فیشینگ اطلاعات کارت/ملی"),
        BlockPattern("scam_transfer", "انتقال.*وجه.*به.*حساب|کارت.*به.*کارت.*لینک", "کلاهبرداری کارت به کارت"),
        
        // Courier/delivery
        BlockPattern("courier", "پستی.*پیش.*رو|مرسوله|پیک|بار.*نامند", "پیامک پستی/پیک"),
        
        // Balance only (no transaction)
        BlockPattern("balance_only", "^\\s*موجودی\\s*شما\\s*[:\\d،,۰-۹]*\\s*$", "فقط موجودی (بدون تراکنش)"),
        
        // Marketing
        BlockPattern("marketing", "تخفیف|جشنواره|تبلیغ|کد.*فعال|اعلان", "تبلیغات/مارکتینگ")
    )

    /**
     * Initial templates - known bank SMS formats.
     * Fee/insurance are SEMI_RICH templates (real transactions).
     */
    val INITIAL_TEMPLATES = listOf(
        // ===== MELLAT (ملت) =====
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

        // ===== TEJARAT (تجارت) =====
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

        // ===== MELLI (ملی) =====
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

        // ===== SAMAN (سامان) =====
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

        // ===== PASARGAD (پاسارگاد) =====
        Template(
            id = "pasargad_expense_rich",
            regex = "پاسارگاد.*خرید\\s*[\\d،,٬]+\\s*تومان.*مانده.*شناسه",
            richness = TemplateRichness.RICH,
            bankName = "پاسارگاد",
            type = TransactionType.EXPENSE,
            description = "پاسارگاد - خرید با نام بانک در متن"
        ),

        // ===== BLU (بلو) - Terse ledger =====
        Template(
            id = "blu_terse_rich",
            regex = "[+\\-]\\s*[\\d،,٬]+\\s*مانده[:\\s]*[\\d،,٬]+",
            richness = TemplateRichness.RICH,
            bankName = "بلو",
            type = TransactionType.EXPENSE,
            description = "بلو - خط خلاصه حسابداری با مانده"
        ),

        // ===== RESALAT (رسالت) - Unit-less =====
        Template(
            id = "resalat_terse_rich",
            regex = "رسالت.*[+\\-]\\s*[\\d،,٬]+\\s*مانده[:\\s]*[\\d،,٬]+",
            richness = TemplateRichness.RICH,
            bankName = "رسالت",
            type = TransactionType.EXPENSE,
            description = "رسالت - خط خلاصه بدون واحد پولی"
        ),

        // ===== GENERIC BANK DEBIT =====
        Template(
            id = "generic_bank_debit_semi",
            regex = "از\\s*حساب\\s*شما\\s*(پرید|کسر|برداشت)",
            richness = TemplateRichness.SEMI_RICH,
            bankName = "نامشخص",
            type = TransactionType.EXPENSE,
            description = "عبارت کلیدی کسر از حساب (بانک شناخته‌شده)"
        ),

        // ===== OPAQUE: Known sender only =====
        Template(
            id = "mellat_opaque", regex = "ملت|Mellat|MELLAT|10000210|10000211",
            richness = TemplateRichness.OPAQUE, bankName = "ملت", type = TransactionType.EXPENSE,
            description = "ملت - فرستنده شناخته‌شده"
        ),
        Template(
            id = "tejarat_opaque", regex = "تجارت|Tejarat|10000017",
            richness = TemplateRichness.OPAQUE, bankName = "تجارت", type = TransactionType.EXPENSE,
            description = "تجارت - فرستنده شناخته‌شده"
        ),
        Template(
            id = "melli_opaque", regex = "ملی|BMI|10000019|بانک\\s*ملی",
            richness = TemplateRichness.OPAQUE, bankName = "ملی", type = TransactionType.EXPENSE,
            description = "ملی - فرستنده شناخته‌شده"
        ),
        Template(
            id = "saman_opaque", regex = "سامان|Saman|SAMAN|10000770|10005010",
            richness = TemplateRichness.OPAQUE, bankName = "سامان", type = TransactionType.EXPENSE,
            description = "سامان - فرستنده شناخته‌شده"
        ),
        Template(
            id = "pasargad_opaque", regex = "پاسارگاد|Pasargad|PASARGAD|10000068|500068",
            richness = TemplateRichness.OPAQUE, bankName = "پاسارگاد", type = TransactionType.EXPENSE,
            description = "پاسارگاد - فرستنده شناخته‌شده"
        ),
        Template(
            id = "sepah_opaque", regex = "سپه|Sepah|SEPAH|10009999|10000155",
            richness = TemplateRichness.OPAQUE, bankName = "سپه", type = TransactionType.EXPENSE,
            description = "سپه - فرستنده شناخته‌شده"
        ),
        Template(
            id = "enbank_opaque", regex = "اقتصاد\\s*نوین|EN\\s*Bank|10000079",
            richness = TemplateRichness.OPAQUE, bankName = "اقتصاد نوین", type = TransactionType.EXPENSE,
            description = "اقتصاد نوین - فرستنده شناخته‌شده"
        ),
        Template(
            id = "parsian_opaque", regex = "پارسیان|Parsian|10000622",
            richness = TemplateRichness.OPAQUE, bankName = "پارسیان", type = TransactionType.EXPENSE,
            description = "پارسیان - فرستنده شناخته‌شده"
        ),
        Template(
            id = "ayandeh_opaque", regex = "آینده|Ayandeh|10008485",
            richness = TemplateRichness.OPAQUE, bankName = "آینده", type = TransactionType.EXPENSE,
            description = "آینده - فرستنده شناخته‌شده"
        ),
        Template(
            id = "city_opaque", regex = "بانک\\s*شهر|City\\s*Bank|10004555",
            richness = TemplateRichness.OPAQUE, bankName = "شهر", type = TransactionType.EXPENSE,
            description = "شهر - فرستنده شناخته‌شده"
        ),
        Template(
            id = "day_opaque", regex = "بانک\\s*دی|Day\\s*Bank|10009898",
            richness = TemplateRichness.OPAQUE, bankName = "دی", type = TransactionType.EXPENSE,
            description = "دی - فرستنده شناخته‌شده"
        ),
        Template(
            id = "karafarin_opaque", regex = "کارآفرین|Karafarin|10008717",
            richness = TemplateRichness.OPAQUE, bankName = "کارآفرین", type = TransactionType.EXPENSE,
            description = "کارآفرین - فرستنده شناخته‌شده"
        ),
        Template(
            id = "maskan_opaque", regex = "مسکن|Maskan|10000129",
            richness = TemplateRichness.OPAQUE, bankName = "مسکن", type = TransactionType.EXPENSE,
            description = "مسکن - فرستنده شناخته‌شده"
        ),
        Template(
            id = "resalat_opaque", regex = "رسالت|Resalat|قرض\\s*الحسنه\\s*رسالت",
            richness = TemplateRichness.OPAQUE, bankName = "رسالت", type = TransactionType.EXPENSE,
            description = "رسالت - فرستنده شناخته‌شده"
        ),
        Template(
            id = "blu_opaque", regex = "بلو|Blu|BLU|بلوبانک",
            richness = TemplateRichness.OPAQUE, bankName = "بلو", type = TransactionType.EXPENSE,
            description = "بلو - فرستنده شناخته‌شده"
        ),
        Template(
            id = "keshavarzi_opaque", regex = "کشاورزی|Keshavarzi|10000160",
            richness = TemplateRichness.OPAQUE, bankName = "کشاورزی", type = TransactionType.EXPENSE,
            description = "کشاورزی - فرستنده شناخته‌شده"
        ),
        Template(
            id = "refah_opaque", regex = "رفاه|Refah|10000144|رفاه\\s*کارگران",
            richness = TemplateRichness.OPAQUE, bankName = "رفاه", type = TransactionType.EXPENSE,
            description = "رفاه - فرستنده شناخته‌شده"
        )
    )
}