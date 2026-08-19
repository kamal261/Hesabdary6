package com.kamal.smsfinance.analysis

import com.kamal.smsfinance.data.SmartSuggestion
import com.kamal.smsfinance.data.SmartSuggestionType
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionType
import kotlin.math.abs

/** Detects likely movement between the user's own accounts, never auto-merging entries. */
class TransferDetector(private val windowMillis: Long = 15 * 60 * 1000L) {

    fun detect(transactions: List<Transaction>): List<SmartSuggestion> {
        val candidates = transactions
            .filter { it.amountToman > 0 }
            .sortedBy { it.date }
        val used = mutableSetOf<Long>()
        val suggestions = mutableListOf<SmartSuggestion>()

        candidates.forEach { outgoing ->
            if (outgoing.id in used || outgoing.type != TransactionType.EXPENSE) return@forEach
            val incoming = candidates
                .asSequence()
                .filter { it.id !in used && it.type == TransactionType.INCOME }
                .filter { it.amountToman == outgoing.amountToman }
                .filter { abs(it.date - outgoing.date) <= windowMillis }
                .filterNot {
                    outgoing.accountTail != null && it.accountTail != null &&
                        outgoing.accountTail == it.accountTail
                }
                .minByOrNull { abs(it.date - outgoing.date) }
                ?: return@forEach

            val distance = abs(incoming.date - outgoing.date)
            val confidence = (0.72 - distance.toDouble() / windowMillis * 0.22).coerceIn(0.5, 0.95).toFloat()
            used += outgoing.id
            used += incoming.id
            suggestions += SmartSuggestion(
                id = "transfer:${outgoing.id}:${incoming.id}",
                type = SmartSuggestionType.PERSONAL_TRANSFER,
                transactionIds = listOf(outgoing.id, incoming.id),
                title = "احتمال انتقال بین حساب‌های شخصی",
                explanation = "یک برداشت و یک واریز با مبلغ برابر در فاصله ${formatMinutes(distance)} دیده شد. اگر این دو حساب برای خودتان هستند، آن‌ها را یک انتقال داخلی علامت بزنید تا هزینه یا درآمد دوباره شمرده نشود.",
                confidence = confidence
            )
        }
        return suggestions
    }

    private fun formatMinutes(millis: Long): String {
        val minutes = (millis / 60_000L).coerceAtLeast(1)
        return "$minutes دقیقه"
    }
}
