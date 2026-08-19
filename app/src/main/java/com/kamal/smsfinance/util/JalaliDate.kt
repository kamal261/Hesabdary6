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
}
