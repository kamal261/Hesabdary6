package com.kamal.smsfinance.util

import java.util.Calendar

/**
 * Pure Jalali (Persian/Shamsi) calendar conversion and formatting.
 * No external dependency — the algorithm is the well-known
 * arithmetic conversion (jalaali-js / JDF style), kept deliberately
 * small, deterministic and unit-testable. All date values in the app
 * are stored as epoch millis; this helper is the single place that
 * renders them for the UI.
 */
object JalaliDate {

    private fun toJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDays[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        var jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }

    /** Returns Jalali [year, month, day] from epoch millis (local timezone). */
    fun fromEpochMillis(epochMillis: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMillis
        return toJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private val MONTH_NAMES = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private fun pad2(n: Int) = if (n < 10) "0$n" else "$n"

    /** e.g. "1404/05/14" */
    fun formatDate(epochMillis: Long): String {
        val (y, m, d) = fromEpochMillis(epochMillis)
        return "$y/${pad2(m)}/${pad2(d)}"
    }

    /** e.g. "1404/05/14 - 14:30" */
    fun formatDateTime(epochMillis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMillis
        return toPersianDigits("${formatDate(epochMillis)} - ${pad2(cal.get(Calendar.HOUR_OF_DAY))}:${pad2(cal.get(Calendar.MINUTE))}")
    }

    /** e.g. "۱۴۰۴/۰۵/۱۴" with Persian digits */
    private fun toPersianDigits(value: String): String = value.map { ch ->
        when (ch) {
            '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
            '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
            else -> ch
        }
    }.joinToString("")

    fun formatDatePersian(epochMillis: Long): String = toPersianDigits(formatDate(epochMillis))

    /** e.g. "۱۴ مهر ۱۴۰۴" — friendly format */
    fun formatDateFriendly(epochMillis: Long): String {
        val (y, m, d) = fromEpochMillis(epochMillis)
        return toPersianDigits("$d ${MONTH_NAMES[m - 1]} $y")
    }

    /**
     * Epoch millis of 00:00:00 local on the FIRST DAY of the Jalali month that
     * contains [now]. Used by "this month" filters so they align with the
     * Persian calendar instead of the Gregorian one.
     */
    fun jalaliMonthStartEpoch(now: Long): Long {
        val (jy, jm, _) = fromEpochMillis(now)
        val (gy, gm, gd) = jalaliToGregorian(jy, jm, 1)
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(gy, gm - 1, gd, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Jalali (y,m,d) -> Gregorian (y,m,d). Faithful port of the jalaali-js
    // arithmetic conversion (same algorithm as SmsDateExtractor), kept here as
    // the canonical date math so all Jalali logic can eventually funnel through
    // this single module.
    internal fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        var jm2 = 0
        for (i in 1 until breaks.size) {
            jm2 = breaks[i]
            jump = jm2 - jp
            if (jy < jm2) break
            leapJ += jump / 33 * 8 + (jump % 33) / 4
            jp = jm2
        }
        val n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - (gy / 100 + 1) * 3 / 4 - 150
        val march = 20 + leapJ - leapG

        fun g2d(gy2: Int, gm: Int, gd: Int): Int {
            var d = ((gy2 + (gm - 8) / 6 + 100100) * 1461) / 4 +
                (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
            d = d - ((gy2 + 100100 + (gm - 8) / 6) / 100) * 3 / 4 + 752
            return d
        }
        val jdn = g2d(gy, 3, march) + (jm - 1) * 31 - jm / 7 * (jm - 7) + jd - 1

        var j = 4 * jdn + 139361631
        j = j + ((4 * jdn + 183187720) / 146097) * 3 / 4 * 4 - 3908
        val di = (j % 1461) / 4 * 5 + 308
        val gd2 = (di % 153) / 5 + 1
        val gm2 = (di / 153) % 12 + 1
        val gy2 = j / 1461 - 100100 + (8 - gm2) / 6
        return Triple(gy2, gm2, gd2)
    }
}
