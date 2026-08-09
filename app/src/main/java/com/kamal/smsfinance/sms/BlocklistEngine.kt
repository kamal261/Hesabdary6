package com.kamal.smsfinance.sms

import java.util.regex.Pattern

/**
 * Blocklist Engine: Hard-rejects messages that are NEVER real transactions.
 * 
 * CRITICAL FIXES from v2.1 critique:
 * 1. Uses `containsMatchIn` (substring) instead of `matches()` (full string match)
 * 2. Only OTP/promo/phishing patterns - NO real financial transactions (fees, insurance)
 * 3. Seed blocklist for Day-1 OTP flood protection
 */
object BlocklistEngine {

    /** Pre-compiled patterns for performance */
    private val compiledPatterns: List<CompiledPattern> = buildCompiledPatterns()

    private fun buildCompiledPatterns(): List<CompiledPattern> {
        return SEED_BLOCKLIST.map { pattern ->
            CompiledPattern(pattern.id, Pattern.compile(pattern.regex, Pattern.CASE_INSENSITIVE), pattern.description)
        }
    }

    /**
     * Checks if a message body matches any blocklist pattern.
     * Uses substring matching (containsMatchIn equivalent) - NOT full string match.
     * 
     * @return true if message should be IGNORED (never stored, never shown)
     */
    fun isBlocked(body: String): Boolean {
        if (body.isBlank()) return true
        return compiledPatterns.any { it.pattern.matcher(body).find() }
    }

    /**
     * Returns the matched pattern ID if blocked, null otherwise.
     * Useful for logging/analytics.
     */
    fun getMatchedPatternId(body: String): String? {
        if (body.isBlank()) return "blank"
        return compiledPatterns.firstOrNull { it.pattern.matcher(body).find() }?.id
    }

    private data class CompiledPattern(
        val id: String,
        val pattern: Pattern,
        val description: String
    )

    /**
     * SEED BLOCKLIST - Only patterns for messages with ZERO financial impact.
     * 
     * CRITICAL: The following are NOT financial transactions and must be blocked:
     * - OTP / 2FA codes
     * - Promotional loans/credits/contests
     * - Phishing / scam patterns
     * - Courier / delivery notifications
     * - Balance-only inquiries (no money movement)
     * 
     * The following ARE real transactions and MUST NOT be here:
     * - Bank fees (کارمزد پیامک/حساب)
     * - Insurance premiums (بیمه/حق بیمه)
     * - Card fees (کارمزد کارت)
     * - Any message where money actually moves in/out of account
     */
    private val SEED_BLOCKLIST = listOf(
        // OTP / 2FA signatures - these are authentication, never transactions
        BlockPattern("otp_dynamic", "رمز\\s*(پویا|یکبار|مؤید|اینترنتی|موقت)", "OTP dynamic password"),
        BlockPattern("otp_verification", "کد\\s*(تایید|فعال.*سازی|احراز)", "OTP verification code"),
        BlockPattern("otp_generic", "رمز\\s*اینترنتی|OTP|one.time.password", "Generic OTP"),
        
        // Promotional loans/credits - marketing, no money movement
        BlockPattern("promo_loan", "تسهیلات|قرض\\s*الحسنه|ضامن|قسط\\s*بندی", "Loan/credit promo"),
        BlockPattern("promo_contest", "قرعه.*کشی|جایزه|برنده|به\\s*ارزش|اعتبار.*دریافت", "Contest/prize promo"),
        BlockPattern("promo_investment", "سهام\\s*عدالت|ابلاغیه|مشاوره.*سرمایه", "Investment promo"),
        
        // Phishing / scam patterns
        BlockPattern("phishing_card", "اسم\\s*کارت|کد\\s*ملی|به.*روزرسانی.*اطلاعات", "Phishing for card/ID info"),
        BlockPattern("scam_transfer", "انتقال.*وجه.*به.*حساب|کارت.*به.*کارت.*لینک", "Card-to-card scam"),
        
        // Courier / delivery (no financial transaction)
        BlockPattern("courier", "پستی.*پیش.*رو|مرسوله|پیک|بار.*نامند", "Courier/delivery"),
        
        // Balance inquiries only (no money movement)
        BlockPattern("balance_only", "^\\s*موجودی\\s*شما\\s*[:\\d،,۰-۹]*\\s*$", "Balance inquiry only"),
        
        // Marketing/ads
        BlockPattern("marketing", "تخفیف|جشنواره|تبلیغ|کد.*فعال|اعلان", "Marketing/advertisement")
    )
}