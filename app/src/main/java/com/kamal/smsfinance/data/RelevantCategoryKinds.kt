package com.kamal.smsfinance.data

/** Which categories make sense for a transaction of [type]. Ported from Hesabdary6-main rev22
 * (a P0 fix from a review report): the UI previously showed ALL categories regardless of
 * transaction type, so a withdrawal could be filed under a "وصول طلب" (income-only) category
 * or a deposit under "پرداخت بدهی" (expense-only) -- a combination counterpartyBalanceSign()
 * has no correct answer for, since it's simply invalid. Preventing the invalid combination at
 * the picker is the fix; a category with kind DEBT_PAYMENT should never be selectable for an
 * INCOME transaction and vice versa. */
fun relevantCategoryKinds(type: TransactionType): Set<CategoryKind> = when (type) {
    TransactionType.EXPENSE -> setOf(CategoryKind.EXPENSE, CategoryKind.DEBT_PAYMENT)
    TransactionType.INCOME -> setOf(CategoryKind.INCOME, CategoryKind.DEBT_COLLECTION)
}
