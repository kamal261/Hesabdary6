package com.kamal.smsfinance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinancialDomainTest {

    @Test
    fun `debt collection reduces receivable and debt payment reduces payable`() {
        assertEquals(-1, counterpartyBalanceSign(TransactionType.INCOME, CategoryKind.DEBT_COLLECTION))
        assertEquals(1, counterpartyBalanceSign(TransactionType.EXPENSE, CategoryKind.DEBT_PAYMENT))

        val transactions = listOf(
            Transaction(
                amountToman = 100_000,
                type = TransactionType.INCOME,
                bankName = "بانک",
                description = "ایجاد طلب",
                date = 1L,
                source = TransactionSource.MANUAL,
                categoryId = 1L
            ),
            Transaction(
                amountToman = 40_000,
                type = TransactionType.INCOME,
                bankName = "بانک",
                description = "وصول طلب",
                date = 2L,
                source = TransactionSource.MANUAL,
                categoryId = 2L
            )
        )

        val balance = transactions.counterpartyBalance { categoryId ->
            when (categoryId) {
                1L -> CategoryKind.INCOME
                2L -> CategoryKind.DEBT_COLLECTION
                else -> null
            }
        }
        assertEquals(60_000L, balance)
    }

    @Test
    fun `category kinds are restricted by transaction direction`() {
        assertTrue(CategoryKind.INCOME.isCompatibleWith(TransactionType.INCOME))
        assertTrue(CategoryKind.DEBT_COLLECTION.isCompatibleWith(TransactionType.INCOME))
        assertTrue(CategoryKind.EXPENSE.isCompatibleWith(TransactionType.EXPENSE))
        assertTrue(CategoryKind.DEBT_PAYMENT.isCompatibleWith(TransactionType.EXPENSE))
        assertTrue(!CategoryKind.DEBT_PAYMENT.isCompatibleWith(TransactionType.INCOME))
        assertTrue(!CategoryKind.DEBT_COLLECTION.isCompatibleWith(TransactionType.EXPENSE))
    }

    @Test
    fun `check reminder status honors reminder days and overdue state`() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun dateOffset(days: Int): Long = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis

        val upcoming = Check(
            type = CheckType.RECEIVABLE,
            amountToman = 100,
            dueDate = dateOffset(2),
            reminderDays = 3
        )
        val todayCheck = upcoming.copy(dueDate = dateOffset(0))
        val overdue = upcoming.copy(dueDate = dateOffset(-1))
        val farAway = upcoming.copy(dueDate = dateOffset(5))

        assertEquals(ReminderStatus.UPCOMING, upcoming.reminderStatus(today))
        assertEquals(ReminderStatus.TODAY, todayCheck.reminderStatus(today))
        assertEquals(ReminderStatus.OVERDUE, overdue.reminderStatus(today))
        assertEquals(ReminderStatus.NONE, farAway.reminderStatus(today))
        assertEquals(ReminderStatus.NONE, upcoming.copy(status = CheckStatus.CLEARED).reminderStatus(today))
    }
}
