package com.kamal.smsfinance.sms

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Extracts the transaction DATE/TIME that is printed INSIDE the bank SMS
 * body, rather than relying on the SMS delivery timestamp. Iranian bank SMS
 * commonly embed a Jalali date (e.g. "۱۴۰۴/۰۵/۱۲" or "1404/05/12") and a
 * clock time (e.g. "14:30"), sometimes with "تاریخ:"/"ساعت:" labels.
 *
 * Pure, deterministic, unit-testable. Falls back to the delivery timestamp
 * when the body has no usable date.
 */
object SmsDateExtractor {

    private val FA_DIGITS = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    /** Converts Persian digits to Latin. Keeps other chars as-is. */
    private fun toLatin(s: String): String = s.map { FA_DIGITS[it] ?: it }.joinToString("")

    // Jalali date: 1404/05/12  or  ۱۴۰۴/۰۵/۱۲  (4-2-2 digits, any of / - . \ separators)
    private val JALALI_DATE = Regex("""(13|14)\d{2}\s*[\-\\/\.]\s*\d{1,2}\s*[\-\\/\.]\s*\d{1,2}""")

    // Gregorian date: 2025/07/15 or 2025-07-15
    private val GREGORIAN_DATE = Regex("""(20\d{2})\s*[\-\\/\.]\s*\d{1,2}\s*[\-\\/\.]\s*\d{1,2}""")

    // Clock time: 14:30 or 14:30:00 (also Persian digits)
    private val TIME = Regex("""([01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?""")

    private val JALALI_MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    // e.g. "12 مرداد 1404"
    private val JALALI_NAMED = Regex("""(\d{1,2})\s+(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)\s+(13|14)\d{2}""")

    /** Jalali (y,m,d) -> epoch millis at 00:00 local. */
    private fun jalaliToEpoch(jy: Int, jm: Int, jd: Int): Long {
        val gregorian = jalaliToGregorian(jy, jm, jd)
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(gregorian.first, gregorian.second - 1, gregorian.third, 0, 0, 0)
        return cal.timeInMillis
    }

    /** Well-known arithmetic Jalali->Gregorian conversion (jalaali algorithm). */
    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> =
        canonicalJalaliToGregorian(jy, jm, jd)

    private fun canonicalJalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        // Standard JDF algorithm (from jalaali-js)
        val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm2 = breaks[i]
            jump = jm2 - jp
            if (jy < jm2) break
            leapJ += jump / 33 * 8 + ((jump % 33) + 3) / 4
            jp = jm2
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        val leapG = (gy / 4) - (gy / 100) + (gy / 400) - 460
        val march = 20 + leapJ - leapG
        var jm2 = if (jm <= 7) (jm - 1) * 31 else (jm - 1) * 30 + 6
        jm2 += jd
        val gd = jm2 - march + 79
        val gy2 = gy
        // Adjust month/day
        var gm = 0
        var gd2 = gd
        if (gd <= 0) {
            gm = 12
            gd2 += 31
        } else {
            val gMonthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            for (i in 0 until 12) {
                if (gd2 <= gMonthDays[i]) { gm = i + 1; break }
                gd2 -= gMonthDays[i]
            }
        }
        return Triple(gy2, gm, gd2)
    }

    /**
     * Returns the epoch millis of the transaction date embedded in the SMS
     * body, or `fallbackTimestamp` if none is found.
     */
    fun extract(body: String, fallbackTimestamp: Long): Long {
        if (body.isBlank()) return fallbackTimestamp

        val latin = toLatin(body)

        // 1) Named Jalali month: "12 مرداد 1404"
        JALALI_NAMED.find(latin)?.let { m ->
            val (day, monthName, year) = m.destructured
            val month = JALALI_MONTH_NAMES.indexOf(monthName) + 1
            if (month > 0) return withTime(jalaliToEpoch(year.toInt(), month, day.toInt()), latin)
        }

        // 2) Numeric Jalali date: 1404/05/12
        JALALI_DATE.find(latin)?.let { m ->
            val parts = m.value.replace(Regex("""\s"""), "").split(Regex("""[/\\-\.]"""))
            if (parts.size == 3) {
                val (y, mo, d) = parts.map { it.toInt() }
                return withTime(jalaliToEpoch(y, mo, d), latin)
            }
        }

        // 3) Gregorian date: 2025/07/15
        GREGORIAN_DATE.find(latin)?.let { m ->
            val parts = m.value.replace(Regex("""\s"""), "").split(Regex("""[/\\-\.]"""))
            if (parts.size == 3) {
                val (y, mo, d) = parts.map { it.toInt() }
                val cal = Calendar.getInstance()
                cal.clear()
                cal.set(y, mo - 1, d, 0, 0, 0)
                return withTime(cal.timeInMillis, latin)
            }
        }

        return fallbackTimestamp
    }

    /** Overlays the clock time (HH:mm) found in the body onto the date, if present. */
    private fun withTime(dateAtMidnight: Long, latinBody: String): Long {
        val t = TIME.find(latinBody) ?: return dateAtMidnight
        val parts = t.value.split(":")
        val h = parts[0].toInt()
        val min = parts[1].toInt()
        val cal = Calendar.getInstance()
        cal.timeInMillis = dateAtMidnight
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, min)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
