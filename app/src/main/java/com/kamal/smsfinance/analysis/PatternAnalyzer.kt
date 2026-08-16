package com.kamal.smsfinance.analysis

import com.kamal.smsfinance.data.SmartSuggestion
import com.kamal.smsfinance.data.SmartSuggestionType
import com.kamal.smsfinance.data.Transaction
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Finds repeated real-world patterns without silently creating rules. Two deliberately explicit
 * strategies are kept in one public analyzer: exact description/amount matching preserves the
 * original high-precision behavior, while tolerant bank/cadence matching preserves the legacy
 * detector's useful 10% amount drift and biweekly cadence.
 */
class PatternAnalyzer {

    fun analyze(transactions: List<Transaction>): List<SmartSuggestion> {
        val eligible = transactions.filter { it.description.isNotBlank() && it.amountToman > 0 }
        val exact = analyzeExact(eligible)
        val tolerant = analyzeTolerant(eligible)

        // The same series can satisfy both strategies. Never show two cards for one series; keep
        // the stronger explainable suggestion while retaining each strategy's own confidence.
        return (exact + tolerant)
            .groupBy { it.transactionIds.sorted() }
            .values
            .map { suggestions -> suggestions.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }
    }

    private fun analyzeExact(transactions: List<Transaction>): List<SmartSuggestion> =
        transactions
            .groupBy {
                PatternKey(
                    strategy = Strategy.EXACT_DESCRIPTION_AMOUNT,
                    type = it.type.name,
                    bankName = "",
                    description = normalize(it.description),
                    amountBucket = it.amountToman
                )
            }
            .values
            .mapNotNull { buildSuggestion(it, Strategy.EXACT_DESCRIPTION_AMOUNT) }

    /**
     * Groups by bank + type + normalized description first, then clusters nearby amounts. The
     * amount is intentionally not placed as an exact key because salaries, rent and installments
     * can drift by up to ten percent while remaining the same real-world pattern.
     */
    private fun analyzeTolerant(transactions: List<Transaction>): List<SmartSuggestion> =
        transactions
            .groupBy {
                PatternKey(
                    strategy = Strategy.TOLERANT_BANK_CADENCE,
                    type = it.type.name,
                    bankName = normalize(it.bankName),
                    description = normalize(it.description),
                    amountBucket = 0L
                )
            }
            .values
            .flatMap { group -> clusterByAmount(group).mapNotNull { buildSuggestion(it, Strategy.TOLERANT_BANK_CADENCE) } }

    private fun clusterByAmount(group: List<Transaction>): List<List<Transaction>> {
        val clusters = mutableListOf<MutableList<Transaction>>()
        group.sortedBy { it.amountToman }.forEach { transaction ->
            val cluster = clusters.lastOrNull()
            if (cluster == null || !cluster.all { amountsAreClose(it.amountToman, transaction.amountToman) }) {
                clusters += mutableListOf(transaction)
            } else {
                cluster += transaction
            }
        }
        return clusters
    }

    private fun buildSuggestion(group: List<Transaction>, strategy: Strategy): SmartSuggestion? {
        if (group.size < 3) return null
        val ordered = group.sortedBy { it.date }
        val gaps = ordered.zipWithNext { a, b -> (b.date - a.date) / DAY_MILLIS.toDouble() }
        if (gaps.isEmpty()) return null
        val averageGap = gaps.average()
        val stable = gaps.all { abs(it - averageGap) <= maxOf(3.0, averageGap * 0.25) }
        if (!stable) return null

        val period = when {
            averageGap in 6.0..8.0 -> "هفتگی"
            averageGap in 12.0..16.0 -> "دوهفته‌ای"
            averageGap in 25.0..35.0 -> "ماهانه"
            averageGap in 85.0..100.0 -> "سه‌ماهه"
            else -> return null
        }
        val label = when {
            normalize(ordered.first().description).contains("اجاره") -> "اجاره $period"
            normalize(ordered.first().description).contains("حقوق") -> "حقوق $period"
            normalize(ordered.first().description).contains("قسط") -> "قسط $period"
            else -> "تراکنش تکراری $period"
        }
        val regularity = 1.0 - (gaps.map { abs(it - averageGap) }.average() / maxOf(averageGap, 1.0))
            .coerceIn(0.0, 1.0)
        val amountStability = amountStability(ordered)
        val base = if (strategy == Strategy.EXACT_DESCRIPTION_AMOUNT) 0.55 else 0.50
        val confidence = (base + regularity * 0.25 + amountStability * 0.15 +
            (ordered.size.coerceAtMost(8) - 3) * 0.02).coerceIn(0.0, 0.95).toFloat()
        val ids = ordered.map { it.id }
        val strategyLabel = if (strategy == Strategy.EXACT_DESCRIPTION_AMOUNT) "مبلغ و توضیح یکسان" else "بانک، الگو و مبلغ نزدیک"
        return SmartSuggestion(
            id = "pattern:${strategy.name.lowercase(Locale.ROOT)}:${ids.joinToString("-")}",
            type = SmartSuggestionType.RECURRING_PATTERN,
            transactionIds = ids,
            title = "احتمال $label",
            explanation = "این مبلغ ${ordered.size} بار با فاصله تقریباً ${averageGap.roundToInt()} روز تکرار شده است ($strategyLabel)؛ قبل از ساخت قانون آن را بررسی کنید.",
            confidence = confidence,
            suggestedCategoryId = ordered.first().categoryId
        )
    }

    private fun amountStability(ordered: List<Transaction>): Double {
        val max = ordered.maxOf { it.amountToman }.toDouble().coerceAtLeast(1.0)
        val min = ordered.minOf { it.amountToman }.toDouble()
        return (1.0 - (max - min) / max).coerceIn(0.0, 1.0)
    }

    private fun amountsAreClose(a: Long, b: Long): Boolean {
        if (a == b) return true
        val larger = maxOf(a, b).toDouble()
        return larger == 0.0 || abs(a - b) / larger <= AMOUNT_TOLERANCE
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[۰-۹]"), "#")
        .replace(Regex("[0-9]"), "#")
        .replace(Regex("\\s+"), " ")
        .trim()

    private enum class Strategy {
        EXACT_DESCRIPTION_AMOUNT,
        TOLERANT_BANK_CADENCE
    }

    private data class PatternKey(
        val strategy: Strategy,
        val type: String,
        val bankName: String,
        val description: String,
        val amountBucket: Long
    )

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val AMOUNT_TOLERANCE = 0.10
    }
}
