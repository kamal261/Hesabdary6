package com.kamal.smsfinance.data

/**
 * Domain guard for the relationship between a transaction side and its category kind.
 * Null category is valid because uncategorized transactions are allowed.
 */
object TransactionCategoryRules {
    fun isCompatible(type: TransactionType, categoryKind: CategoryKind?): Boolean {
        if (categoryKind == null) return true
        return when (type) {
            TransactionType.INCOME -> categoryKind == CategoryKind.INCOME ||
                categoryKind == CategoryKind.DEBT_COLLECTION
            TransactionType.EXPENSE -> categoryKind == CategoryKind.EXPENSE ||
                categoryKind == CategoryKind.DEBT_PAYMENT
        }
    }
}
