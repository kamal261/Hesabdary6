package com.kamal.smsfinance.analysis

import com.kamal.smsfinance.data.Check
import com.kamal.smsfinance.data.SmartSuggestion
import com.kamal.smsfinance.data.Transaction

class SuggestionEngine(
    private val patternAnalyzer: PatternAnalyzer = PatternAnalyzer(),
    private val transferDetector: TransferDetector = TransferDetector(),
    private val checkMatcher: CheckMatcher = CheckMatcher()
) {
    fun analyze(transactions: List<Transaction>, checks: List<Check>): List<SmartSuggestion> =
        (patternAnalyzer.analyze(transactions) +
            transferDetector.detect(transactions) +
            checkMatcher.match(checks, transactions))
            .sortedByDescending { it.confidence }
}
