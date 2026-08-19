package com.kamal.smsfinance.data

/** Pure validation rules shared by Repository code and unit tests. */
object FinancialLinkRules {

    fun validateTransfer(
        selected: List<Transaction>,
        existingMembers: List<Transaction>,
        groupId: Long
    ): TransferGroupRejection? {
        if (selected.size < 2) return TransferGroupRejection.TOO_FEW_TRANSACTIONS
        if (groupId <= 0L) return TransferGroupRejection.INVALID_GROUP_ID

        val existingGroups = selected.mapNotNull { it.transferGroupId }.toSet()
        if (existingGroups.any { it != groupId } || existingGroups.size > 1) {
            return TransferGroupRejection.GROUP_CONFLICT
        }

        val finalMembers = (existingMembers + selected).distinctBy { it.id }
        val incomeCount = finalMembers.count { it.type == TransactionType.INCOME }
        val expenseCount = finalMembers.count { it.type == TransactionType.EXPENSE }
        return if (finalMembers.size == 2 && incomeCount == 1 && expenseCount == 1) {
            null
        } else {
            TransferGroupRejection.INVALID_COMPOSITION
        }
    }

    fun validateCheckLink(transaction: Transaction, check: Check): CheckLinkRejection? {
        if (transaction.linkedCheckId != null) return CheckLinkRejection.TRANSACTION_ALREADY_LINKED
        if (check.settledTransactionId != null) return CheckLinkRejection.CHECK_ALREADY_SETTLED
        if (check.status != CheckStatus.PENDING) return CheckLinkRejection.CHECK_NOT_PENDING
        if (transaction.amountToman != check.amountToman) return CheckLinkRejection.AMOUNT_MISMATCH

        val expectedType = if (check.type == CheckType.RECEIVABLE) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
        if (transaction.type != expectedType) return CheckLinkRejection.TYPE_MISMATCH
        return null
    }
}
