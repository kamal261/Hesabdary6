package com.kamal.smsfinance.data

/** The suggestion kinds shown to the user; none of them changes data until approved. */
enum class SmartSuggestionType {
    RECURRING_PATTERN,
    PERSONAL_TRANSFER,
    CHECK_MATCH,
    /** A later bank SMS may be the same check already settled manually. Review only. */
    POSSIBLE_DUPLICATE_CHECK
}

enum class SmartSuggestionStatus { PENDING, ACCEPTED, REJECTED }

data class SmartSuggestion(
    val id: String,
    val type: SmartSuggestionType,
    val transactionIds: List<Long>,
    val title: String,
    val explanation: String,
    val confidence: Float,
    val suggestedCategoryId: Long? = null,
    val checkId: Long? = null,
    val status: SmartSuggestionStatus = SmartSuggestionStatus.PENDING
)
