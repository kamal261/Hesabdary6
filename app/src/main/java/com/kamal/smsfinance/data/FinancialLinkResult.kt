package com.kamal.smsfinance.data

/** Explicit outcomes for user-approved financial relationships. */
sealed interface TransferGroupResult {
    data class Success(val groupId: Long, val transactionIds: List<Long>) : TransferGroupResult
    data class Rejected(val reason: TransferGroupRejection) : TransferGroupResult
}

enum class TransferGroupRejection {
    TOO_FEW_TRANSACTIONS,
    INVALID_GROUP_ID,
    TRANSACTION_NOT_FOUND,
    INVALID_COMPOSITION,
    GROUP_CONFLICT
}

/** Explicit outcome for linking an existing bank transaction to a check. */
sealed interface CheckLinkResult {
    data class Success(val transactionId: Long, val checkId: Long) : CheckLinkResult
    data class Rejected(val reason: CheckLinkRejection) : CheckLinkResult
}

enum class CheckLinkRejection {
    TRANSACTION_NOT_FOUND,
    CHECK_NOT_FOUND,
    TRANSACTION_ALREADY_LINKED,
    CHECK_NOT_PENDING,
    AMOUNT_MISMATCH,
    TYPE_MISMATCH,
    CHECK_ALREADY_SETTLED
}
