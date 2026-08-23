package com.kamal.smsfinance.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCategoryRulesTest {
    @Test
    fun income_accepts_income_and_debt_collection_categories() {
        assertTrue(TransactionCategoryRules.isCompatible(TransactionType.INCOME, CategoryKind.INCOME))
        assertTrue(TransactionCategoryRules.isCompatible(TransactionType.INCOME, CategoryKind.DEBT_COLLECTION))
    }

    @Test
    fun expense_accepts_expense_and_debt_payment_categories() {
        assertTrue(TransactionCategoryRules.isCompatible(TransactionType.EXPENSE, CategoryKind.EXPENSE))
        assertTrue(TransactionCategoryRules.isCompatible(TransactionType.EXPENSE, CategoryKind.DEBT_PAYMENT))
    }

    @Test
    fun opposite_side_categories_are_rejected() {
        assertFalse(TransactionCategoryRules.isCompatible(TransactionType.INCOME, CategoryKind.EXPENSE))
        assertFalse(TransactionCategoryRules.isCompatible(TransactionType.INCOME, CategoryKind.DEBT_PAYMENT))
        assertFalse(TransactionCategoryRules.isCompatible(TransactionType.EXPENSE, CategoryKind.INCOME))
        assertFalse(TransactionCategoryRules.isCompatible(TransactionType.EXPENSE, CategoryKind.DEBT_COLLECTION))
    }

    @Test
    fun uncategorized_transaction_is_allowed() {
        assertTrue(TransactionCategoryRules.isCompatible(TransactionType.INCOME, null))
        assertTrue(TransactionCategoryRules.isCompatible(TransactionType.EXPENSE, null))
    }
}
