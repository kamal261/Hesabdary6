package com.kamal.smsfinance.data

/** Suggested categorization from the rule engine, or an empty result on no match. */
data class RuleMatchResult(
    val categoryId: Long? = null,
    val counterpartyId: Long? = null,
    val matchedRule: SmartRule? = null
)

/**
 * Pure pattern-matching engine, deliberately free of any DB/Android
 * dependency so it stays trivially unit-testable and swap-in/out. It only
 * ever *suggests* a categoryId/counterpartyId -- callers decide whether and
 * when to apply it; no irreversible action (delete, merge, amount change)
 * is ever performed here.
 */
class RuleEngine {

    /** Evaluates SMS/description text against the user's rules and returns the first (most specific) match. */
    fun evaluate(text: String, rules: List<SmartRule>): RuleMatchResult {
        if (text.isBlank() || rules.isEmpty()) return RuleMatchResult()

        val normalizedText = normalizeText(text)

        // Longer patterns are more specific, so they win over generic ones
        // when more than one rule matches the same text (e.g. a rule for
        // "خرید" shouldn't shadow a more specific rule for "خرید سوپرمارکت").
        val matchedRule = rules
            .sortedByDescending { it.pattern.length }
            .firstOrNull { rule ->
                val normalizedPattern = normalizeText(rule.pattern)
                normalizedPattern.isNotEmpty() && normalizedText.contains(normalizedPattern)
            }

        return if (matchedRule != null) {
            RuleMatchResult(matchedRule.categoryId, matchedRule.counterpartyId, matchedRule)
        } else {
            RuleMatchResult()
        }
    }

    /** Normalizes Arabic/Persian character variants and whitespace for reliable substring matching. */
    fun normalizeText(input: String): String =
        input.trim()
            .lowercase()
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace("\u200C", " ")
            .replace(Regex("\\s+"), " ")
}
