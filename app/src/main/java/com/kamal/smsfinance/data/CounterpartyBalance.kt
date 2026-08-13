// SmsFinance file version: 1 — P0 fix from a technical/accounting review: the counterparty
// balance formula was SUM(income) - SUM(expense), which ignores CategoryKind entirely. Traced
// against a concrete example: when a customer PAYS BACK debt they owed (an INCOME transaction
// categorized as DEBT_COLLECTION), the old formula ADDED it to the balance -- making it look
// like the customer's debt GREW the more they paid off, exactly backwards. Symmetric bug for
// DEBT_PAYMENT (paying back what the user owes a vendor).
//
// This file is the single domain function both the DAO (via a matching SQL CASE expression --
// SQL can't call Kotlin, so CounterpartyDao.balanceFor must mirror this logic manually; keep
// them in sync) and any in-memory computation (TransactionViewModel.counterpartyBalanceSummary)
// must agree with. Pure function, no Android/DB dependency -- trivially unit-testable.
package com.kamal.smsfinance.data

/**
 * Sign multiplier for how a transaction linked to a counterparty affects the running balance.
 * Balance convention: positive = counterparty owes the user (receivable), negative = user owes
 * counterparty (payable).
 *
 * - Ordinary INCOME/EXPENSE (no debt-settlement category): kept as the original simple "cash
 *   flow with this person" interpretation -- income increases what's owed to the user, expense
 *   increases what the user owes them. This is the base case for a running tab and wasn't what
 *   the review flagged as wrong.
 * - DEBT_COLLECTION (customer paying back what they already owed): still an INCOME-type
 *   transaction, but its effect must REDUCE the receivable, not add to it -- the opposite sign
 *   of ordinary income.
 * - DEBT_PAYMENT (user paying back what they already owed a vendor/worker): still an
 *   EXPENSE-type transaction, but its effect must REDUCE the payable -- the opposite sign of
 *   ordinary expense.
 */
fun counterpartyBalanceSign(type: TransactionType, categoryKind: CategoryKind?): Int = when {
    categoryKind == CategoryKind.DEBT_COLLECTION -> -1
    categoryKind == CategoryKind.DEBT_PAYMENT -> 1
    type == TransactionType.INCOME -> 1
    else -> -1
}

/** Convenience for computing a balance over an in-memory list, given a way to look up each
 * transaction's category kind (categories aren't embedded in Transaction itself). */
fun List<Transaction>.counterpartyBalance(categoryKindOf: (Long?) -> CategoryKind?): Long =
    sumOf { txn -> counterpartyBalanceSign(txn.type, categoryKindOf(txn.categoryId)) * txn.amountToman }
