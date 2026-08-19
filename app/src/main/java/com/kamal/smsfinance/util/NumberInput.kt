package com.kamal.smsfinance.util

/** Normalizes Persian/Arabic numerals and common separators before parsing amounts. */
fun String.normalizeDigits(): String = buildString(length) {
    for (ch in this@normalizeDigits) {
        append(
            when (ch) {
                '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
                '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> ch
            }
        )
    }
}

fun String.toPositiveLongOrNull(): Long? = normalizeDigits()
    .replace(",", "")
    .replace("٬", "")
    .replace(" ", "")
    .trim()
    .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    ?.toLongOrNull()
