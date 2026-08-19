package com.kamal.smsfinance.analysis

import com.kamal.smsfinance.data.Check
import com.kamal.smsfinance.data.CheckStatus
import com.kamal.smsfinance.data.CheckType
import com.kamal.smsfinance.data.SmartSuggestion
import com.kamal.smsfinance.data.SmartSuggestionType
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionSource
import com.kamal.smsfinance.data.TransactionType
import kotlin.math.abs

/**
 * Suggests reconciliation links; accepting a normal check match links rows, while accepting a
 * possible post-settlement duplicate only dismisses the review suggestion. No check suggestion
 * deletes or mutates a financial row automatically.
 */
private const val CHECK_DAY_MILLIS = 86_400_000L

class CheckMatcher(
    private val windowBeforeMillis: Long = 45L * CHECK_DAY_MILLIS,
    private val windowAfterMillis: Long = 14L * CHECK_DAY_MILLIS,
    private val postSettlementWindowMillis: Long = 14L * CHECK_DAY_MILLIS
) {

    fun match(checks: List<Check>, transactions: List<Transaction>): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()
        checks.forEach { check ->
            when (check.status) {
                CheckStatus.PENDING -> matchPendingCheck(check, transactions, suggestions)
                CheckStatus.CLEARED -> matchPossiblePostSettlementDuplicate(check, transactions, suggestions)
                CheckStatus.BOUNCED -> Unit
            }
        }
        return suggestions
    }

    private fun matchPendingCheck(
        check: Check,
        transactions: List<Transaction>,
        suggestions: MutableList<SmartSuggestion>
    ) {
        val expectedType = expectedTransactionType(check)
        val candidate = transactions
            .asSequence()
            .filter { it.linkedCheckId == null }
            .filter { it.amountToman == check.amountToman && it.type == expectedType }
            .filter { it.date in (check.dueDate - windowBeforeMillis)..(check.dueDate + windowAfterMillis) }
            .minByOrNull { abs(it.date - check.dueDate) }
            ?: return

        val distance = abs(candidate.date - check.dueDate)
        val confidence = (0.78 - (distance.toDouble() / windowBeforeMillis.coerceAtLeast(1L)) * 0.25)
            .coerceIn(0.5, 0.95).toFloat()
        suggestions += SmartSuggestion(
            id = "check:${check.id}:${candidate.id}",
            type = SmartSuggestionType.CHECK_MATCH,
            transactionIds = listOf(candidate.id),
            title = "پیامک این چک پیدا شد",
            explanation = "مبلغ پیامک با مبلغ چک برابر است و تاریخ آن نزدیک سررسید چک است. با تأیید شما فقط ارتباط ثبت می‌شود و مبلغ دوباره ثبت نمی‌شود.",
            confidence = confidence,
            checkId = check.id
        )
    }

    /**
     * A check settled manually already has its own CHECK_SETTLEMENT row. If a later SMS row has
     * the same amount/type and arrives shortly afterward, show a review-only suggestion instead
     * of silently linking, deleting, or excluding the SMS from reports.
     */
    private fun matchPossiblePostSettlementDuplicate(
        check: Check,
        transactions: List<Transaction>,
        suggestions: MutableList<SmartSuggestion>
    ) {
        val settledId = check.settledTransactionId ?: return
        val settlement = transactions.firstOrNull { it.id == settledId } ?: return
        val expectedType = expectedTransactionType(check)
        val referenceDate = check.paidDate ?: settlement.date
        val candidate = transactions
            .asSequence()
            .filter { it.id != settledId }
            .filter { it.linkedCheckId == null }
            .filter { it.source == TransactionSource.SMS_AUTO || it.rawSms != null }
            .filter { it.amountToman == check.amountToman && it.type == expectedType }
            .filter { it.date in referenceDate..(referenceDate + postSettlementWindowMillis) }
            .minByOrNull { abs(it.date - referenceDate) }
            ?: return

        val distance = abs(candidate.date - referenceDate)
        val confidence = (0.68 - (distance.toDouble() / postSettlementWindowMillis.coerceAtLeast(1L)) * 0.18)
            .coerceIn(0.5, 0.86).toFloat()
        suggestions += SmartSuggestion(
            id = "check-review:${check.id}:${candidate.id}",
            type = SmartSuggestionType.POSSIBLE_DUPLICATE_CHECK,
            transactionIds = listOf(candidate.id),
            title = "پیامک هم‌مبلغ بعد از تسویه چک",
            explanation = "این پیامک بانکی بعد از تسویه دستی چک با همان مبلغ دیده شده است. برنامه هیچ چیزی را حذف یا به چک متصل نمی‌کند؛ فقط پیشنهاد می‌دهد جزئیات را بررسی کنید.",
            confidence = confidence,
            checkId = check.id
        )
    }

    private fun expectedTransactionType(check: Check): TransactionType =
        if (check.type == CheckType.RECEIVABLE) TransactionType.INCOME else TransactionType.EXPENSE
}
