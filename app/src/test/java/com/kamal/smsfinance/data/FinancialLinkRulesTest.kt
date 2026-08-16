package com.kamal.smsfinance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinancialLinkRulesTest {

    @Test
    fun transfer_requires_exactly_one_income_and_one_expense() {
        assertNull(
            FinancialLinkRules.validateTransfer(
                selected = listOf(tx(1, TransactionType.EXPENSE), tx(2, TransactionType.INCOME)),
                existingMembers = emptyList(),
                groupId = 100L
            )
        )
        assertEquals(
            TransferGroupRejection.INVALID_COMPOSITION,
            FinancialLinkRules.validateTransfer(
                selected = listOf(tx(1, TransactionType.INCOME), tx(2, TransactionType.INCOME)),
                existingMembers = emptyList(),
                groupId = 100L
            )
        )
    }

    @Test
    fun transfer_rejects_members_of_different_groups() {
        val result = FinancialLinkRules.validateTransfer(
            selected = listOf(
                tx(1, TransactionType.EXPENSE).copy(transferGroupId = 10L),
                tx(2, TransactionType.INCOME).copy(transferGroupId = 11L)
            ),
            existingMembers = emptyList(),
            groupId = 10L
        )
        assertEquals(TransferGroupRejection.GROUP_CONFLICT, result)
    }

    @Test
    fun transfer_rejects_less_than_two_rows() {
        assertEquals(
            TransferGroupRejection.TOO_FEW_TRANSACTIONS,
            FinancialLinkRules.validateTransfer(listOf(tx(1, TransactionType.INCOME)), emptyList(), 100L)
        )
    }

    @Test
    fun check_link_accepts_matching_amount_and_type() {
        val check = Check(type = CheckType.RECEIVABLE, amountToman = 500_000L, dueDate = 1L)
        assertNull(FinancialLinkRules.validateCheckLink(tx(1, TransactionType.INCOME, 500_000L), check))
    }

    @Test
    fun check_link_rejects_amount_and_type_mismatch() {
        val check = Check(type = CheckType.RECEIVABLE, amountToman = 500_000L, dueDate = 1L)
        assertEquals(
            CheckLinkRejection.AMOUNT_MISMATCH,
            FinancialLinkRules.validateCheckLink(tx(1, TransactionType.INCOME, 400_000L), check)
        )
        assertEquals(
            CheckLinkRejection.TYPE_MISMATCH,
            FinancialLinkRules.validateCheckLink(tx(2, TransactionType.EXPENSE, 500_000L), check)
        )
    }

    @Test
    fun check_link_rejects_already_settled_or_linked_rows() {
        val settled = Check(
            type = CheckType.RECEIVABLE,
            amountToman = 500_000L,
            dueDate = 1L,
            status = CheckStatus.CLEARED,
            settledTransactionId = 9L
        )
        assertEquals(
            CheckLinkRejection.CHECK_ALREADY_SETTLED,
            FinancialLinkRules.validateCheckLink(tx(1, TransactionType.INCOME, 500_000L), settled)
        )

        val pending = Check(type = CheckType.RECEIVABLE, amountToman = 500_000L, dueDate = 1L)
        assertEquals(
            CheckLinkRejection.TRANSACTION_ALREADY_LINKED,
            FinancialLinkRules.validateCheckLink(
                tx(1, TransactionType.INCOME, 500_000L).copy(linkedCheckId = 7L),
                pending
            )
        )
    }

    private fun tx(
        id: Long,
        type: TransactionType,
        amount: Long = 500_000L
    ) = Transaction(
        id = id,
        amountToman = amount,
        type = type,
        bankName = "آزمایشی",
        description = "انتقال",
        date = 1L,
        source = TransactionSource.SMS_AUTO
    )
}
