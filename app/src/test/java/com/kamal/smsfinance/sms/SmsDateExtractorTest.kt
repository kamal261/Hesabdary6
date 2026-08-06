package com.kamal.smsfinance.sms

import com.kamal.smsfinance.util.JalaliDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Tests for SmsDateExtractor.
 *
 * Iranian bank transaction SMS always carry a Jalali (Shamsi) date — never
 * Gregorian — so these tests only cover Jalali formats (numeric, Persian
 * digits, named months) plus the fallback path. Verification is done by
 * round-tripping the extracted epoch back through JalaliDate.formatDate /
 * formatDateTime — the same representation the app displays — so every
 * assertion is stable regardless of the local timezone.
 */
class SmsDateExtractorTest {

    // ---------- Fallback when no date ----------

    @Test
    fun `blank body returns fallback`() {
        assertEquals(1111L, SmsDateExtractor.extract("", 1111L))
    }

    @Test
    fun `no embedded date returns fallback`() {
        val fallback = 1_700_000_000_000L
        assertEquals(fallback, SmsDateExtractor.extract("مبلغ 500,000 تومان", fallback))
    }

    // ---------- Numeric Jalali date (round-trip, no conversion) ----------

    @Test
    fun `numeric jalali date is parsed (1404 slash)`() {
        val result = SmsDateExtractor.extract("خرید 1404/05/12 مبلغ 100,000 تومان", 0L)
        assertEquals("1404/05/12", JalaliDate.formatDate(result))
    }

    @Test
    fun `numeric jalali with dash separators is parsed`() {
        val result = SmsDateExtractor.extract("تاریخ: 1404-05-12", 0L)
        assertEquals("1404/05/12", JalaliDate.formatDate(result))
    }

    @Test
    fun `numeric jalali with dot separators is parsed`() {
        val result = SmsDateExtractor.extract("1404.05.12", 0L)
        assertEquals("1404/05/12", JalaliDate.formatDate(result))
    }

    @Test
    fun `jalali with Persian digits is parsed`() {
        val result = SmsDateExtractor.extract("۱۴۰۴/۰۵/۱۲ مبلغ ۱۰۰،۰۰۰", 0L)
        assertEquals("1404/05/12", JalaliDate.formatDate(result))
    }

    @Test
    fun `jalali date with surrounding clock time uses the embedded time`() {
        val result = SmsDateExtractor.extract("تاریخ: 1404/05/12 ساعت 14:30 مبلغ 100,000", 0L)
        assertEquals("1404/05/12 - 14:30", JalaliDate.formatDateTime(result))
    }

    // ---------- Named Jalali month ----------

    @Test
    fun `named Jalali month is parsed (12 مرداد 1404)`() {
        val result = SmsDateExtractor.extract("مبلغ 200,000 در تاریخ 12 مرداد 1404", 0L)
        assertEquals("1404/05/12", JalaliDate.formatDate(result))
    }

    @Test
    fun `named Jalali month with Persian day and year digits is parsed`() {
        val result = SmsDateExtractor.extract("۱۲ مرداد ۱۴۰۴", 0L)
        assertEquals("1404/05/12", JalaliDate.formatDate(result))
    }

    // ---------- Sanity ----------

    @Test
    fun `extracted date is not the fallback when a date exists`() {
        val fallback = 1_700_000_000_000L
        assertNotEquals(fallback, SmsDateExtractor.extract("1404/05/12", fallback))
    }
}
