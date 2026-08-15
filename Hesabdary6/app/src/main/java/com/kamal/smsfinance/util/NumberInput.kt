package com.kamal.smsfinance.util

/** Converts Persian and Arabic-Indic digits to ASCII digits before numeric parsing. */
fun String.toAsciiDigits(): String = map { ch ->
    when (ch) {
        in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
        in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
        else -> ch
    }
}.joinToString("")
