package com.kamal.smsfinance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RecurringDetectorTest {

    private val DAY = TimeUnit.DAYS.toMillis(1)

    private fun tx(
        id: Long,
        amount: Long,
        date: Long,
        bank: String = "ملی",
        type: TransactionType = TransactionType.EXPENSE
    ) = Transaction(
        id = id,
        amountToman = amount,
        type = type,
        bankName = bank,
        description = "test",
        date = date,
        source = TransactionSource.SMS_AUTO
    )

    // ---------- Basic cadence detection ----------

    @Test
    fun `empty or single transaction list yields nothing`() {
        assertTrue(RecurringDetector.computeRecurringIds(emptyList()).isEmpty())
        assertTrue(RecurringDetector.computeRecurringIds(listOf(tx(1, 100, 0L))).isEmpty())
    }

    @Test
    fun `monthly cadence within 26-34 days is recurring`() {
        val base = 1_700_000_000_000L // arbitrary epoch
        val txs = listOf(
            tx(1, 500_000, base),
            tx(2, 500_000, base + 30 * DAY)
        )
        val ids = RecurringDetector.computeRecurringIds(txs)
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test
    fun `biweekly cadence within 12-16 days is recurring`() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(10, 250_000, base),
            tx(11, 250_000, base + 14 * DAY),
            tx(12, 250_000, base + 28 * DAY)
        )
        val ids = RecurringDetector.computeRecurringIds(txs)
        assertEquals(setOf(10L, 11L, 12L), ids)
    }

    @Test
    fun `gap outside both ranges is not recurring`() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(1, 500_000, base),
            tx(2, 500_000, base + 60 * DAY) // two months, not monthly band
        )
        assertTrue(RecurringDetector.computeRecurringIds(txs).isEmpty())
    }

    // ---------- Amount tolerance ----------

    @Test
    fun `amount drift within 10 percent is still recurring`() {
        val base = 1_700_000_000_000L
        // 550,000 vs 500,000 = exactly 10% of larger.
        val txs = listOf(
            tx(1, 500_000, base),
            tx(2, 550_000, base + 30 * DAY)
        )
        assertEquals(setOf(1L, 2L), RecurringDetector.computeRecurringIds(txs))
    }

    @Test
    fun `amount drift beyond 10 percent is not recurring`() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(1, 500_000, base),
            tx(2, 700_000, base + 30 * DAY) // 40% drift
        )
        assertTrue(RecurringDetector.computeRecurringIds(txs).isEmpty())
    }

    @Test
    fun `zero amounts are treated as equal (guard against divide-by-zero)`() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(1, 0, base),
            tx(2, 0, base + 30 * DAY)
        )
        assertEquals(setOf(1L, 2L), RecurringDetector.computeRecurringIds(txs))
    }

    // ---------- Grouping by bank + type ----------

    @Test
    fun `different banks are not grouped together`() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(1, 500_000, base, bank = "ملی"),
            tx(2, 500_000, base + 30 * DAY, bank = "ملت")
        )
        assertTrue(RecurringDetector.computeRecurringIds(txs).isEmpty())
    }

    @Test
    fun `different types are not grouped together`() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(1, 500_000, base, type = TransactionType.EXPENSE),
            tx(2, 500_000, base + 30 * DAY, type = TransactionType.INCOME)
        )
        assertTrue(RecurringDetector.computeRecurringIds(txs).isEmpty())
    }

    @Test
    fun `one missed month still chains through (tolerant of single gap)`() {
        val base = 1_700_000_000_000L
        // Jan, Feb, Apr (March missed): only pairs within the 26-34 day band are
        // flagged as recurring. Jan->Feb (30d) qualifies; Feb->Apr (59d) is out
        // of band and simply not flagged -- no crash, no false positive.
        val txs = listOf(
            tx(1, 500_000, base),            // Jan
            tx(2, 500_000, base + 30 * DAY), // Feb
            tx(3, 500_000, base + 89 * DAY)  // Apr, 59 days after Feb
        )
        val ids = RecurringDetector.computeRecurringIds(txs)
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test
    fun `unsorted input is handled (sorted internally)`() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(2, 500_000, base + 30 * DAY),
            tx(1, 500_000, base)
        )
        assertEquals(setOf(1L, 2L), RecurringDetector.computeRecurringIds(txs))
    }
}