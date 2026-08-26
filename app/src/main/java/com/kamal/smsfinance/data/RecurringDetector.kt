package com.kamal.smsfinance.data

import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Pure, DB-free detector: groups an in-memory list of transactions by
 * (bank, type) and compares each transaction against earlier ones looking for a monthly or
 * biweekly cadence with a close-enough amount -- covers bills,
 * installments ("قسط"), and recurring payments like rent/salary whose
 * amount can drift slightly month to month. Tolerant of one missed month.
 *
 * Deliberately stateless and DB-free: "recurring" is derived data, so it is
 * computed on demand from the current transaction list rather than stored
 * on the Transaction row (Single Source of Truth). This also makes it
 * trivially unit-testable with a plain list, no Room/Android dependency.
 */
object RecurringDetector {

    private val MONTHLY_RANGE = 26L..34L // days
    private val BIWEEKLY_RANGE = 12L..16L // days
    // One missed month (~2x monthly) still counts as recurring, matching the
    // detector's "tolerant of one missed month" contract.
    private val BI_MONTHLY_RANGE = 54L..66L // days

    // Amounts within this fraction of each other still count as "the same"
    // payment (e.g. rent that goes up slightly, or a salary with minor
    // month-to-month variation) -- exact-match only was too strict for
    // real-world recurring payments.
    private const val AMOUNT_TOLERANCE = 0.10

    /** Returns the ids of transactions that are part of a recurring series. */
    fun computeRecurringIds(transactions: List<Transaction>): Set<Long> {
        if (transactions.size < 2) return emptySet()

        val groups = transactions.groupBy { it.bankName to it.type }
        val recurringIds = mutableSetOf<Long>()

        for ((_, group) in groups) {
            if (group.size < 2) continue
            val sorted = group.sortedBy { it.date }
            // Compare each transaction against ANY earlier one in the group, so a
            // single missed cycle (~2 months) still links the pair together.
            for (i in 1 until sorted.size) {
                val curr = sorted[i]
                for (j in 0 until i) {
                    val prev = sorted[j]
                    val gapDays = TimeUnit.MILLISECONDS.toDays(abs(curr.date - prev.date))
                    val cadenceMatches = gapDays in MONTHLY_RANGE || gapDays in BIWEEKLY_RANGE || gapDays in BI_MONTHLY_RANGE
                    if (cadenceMatches && amountsAreClose(prev.amountToman, curr.amountToman)) {
                        recurringIds.add(prev.id)
                        recurringIds.add(curr.id)
                        break
                    }
                }
            }
        }

        return recurringIds
    }

    private fun amountsAreClose(a: Long, b: Long): Boolean {
        if (a == b) return true
        val larger = maxOf(a, b).toDouble()
        if (larger == 0.0) return true
        return abs(a - b) / larger <= AMOUNT_TOLERANCE
    }
}
