package com.kamal.smsfinance.analysis

import com.kamal.smsfinance.data.Check
import com.kamal.smsfinance.data.CheckStatus
import com.kamal.smsfinance.data.CheckType
import com.kamal.smsfinance.data.SmartSuggestionType
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionSource
import com.kamal.smsfinance.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAnalysisTest {

    @Test
    fun recurring_monthly_pattern_is_explainable() {
        val transactions = listOf(
            tx(1, 500_000, TransactionType.EXPENSE, 10 * DAY, "اجاره خانه"),
            tx(2, 500_000, TransactionType.EXPENSE, 40 * DAY, "اجاره خانه"),
            tx(3, 500_000, TransactionType.EXPENSE, 70 * DAY, "اجاره خانه")
        )

        val result = PatternAnalyzer().analyze(transactions)

        assertEquals(1, result.size)
        assertEquals(SmartSuggestionType.RECURRING_PATTERN, result.single().type)
        assertTrue(result.single().explanation.contains("تکرار"))
    }

    @Test
    fun tolerant_pattern_accepts_ten_percent_amount_drift_and_biweekly_cadence() {
        val result = PatternAnalyzer().analyze(
            listOf(
                tx(40, 500_000, TransactionType.INCOME, 10 * DAY, "حقوق", bankName = "بانک الف"),
                tx(41, 520_000, TransactionType.INCOME, 24 * DAY, "حقوق", bankName = "بانک الف"),
                tx(42, 510_000, TransactionType.INCOME, 38 * DAY, "حقوق", bankName = "بانک الف")
            )
        )

        assertEquals(1, result.size)
        assertEquals(SmartSuggestionType.RECURRING_PATTERN, result.single().type)
        assertEquals(listOf(40L, 41L, 42L), result.single().transactionIds)
        assertTrue(result.single().confidence >= 0.5f)
    }

    @Test
    fun equal_opposite_transactions_are_personal_transfer_candidate() {
        val result = TransferDetector().detect(
            listOf(
                tx(10, 2_000_000, TransactionType.EXPENSE, 100 * DAY, "انتقال از حساب"),
                tx(11, 2_000_000, TransactionType.INCOME, 100 * DAY + 4 * 60_000L, "واریز به حساب")
            )
        )

        assertEquals(SmartSuggestionType.PERSONAL_TRANSFER, result.single().type)
        assertEquals(listOf(10L, 11L), result.single().transactionIds)
    }

    @Test
    fun cleared_check_with_later_same_amount_sms_creates_review_only_suggestion() {
        val settledDate = 300 * DAY
        val check = Check(
            id = 8,
            type = CheckType.RECEIVABLE,
            amountToman = 4_000_000,
            dueDate = settledDate,
            status = CheckStatus.CLEARED,
            paidDate = settledDate,
            settledTransactionId = 30
        )
        val settlement = tx(30, 4_000_000, TransactionType.INCOME, settledDate, "وصول چک").copy(
            source = TransactionSource.CHECK_SETTLEMENT
        )
        val laterSms = tx(31, 4_000_000, TransactionType.INCOME, settledDate + DAY, "واریز بانکی")

        val result = CheckMatcher().match(listOf(check), listOf(settlement, laterSms))

        assertEquals(1, result.size)
        assertEquals(SmartSuggestionType.POSSIBLE_DUPLICATE_CHECK, result.single().type)
        assertEquals(listOf(31L), result.single().transactionIds)
        assertTrue(result.single().explanation.contains("حذف"))
    }

    @Test
    fun pending_check_can_match_same_amount_income_without_creating_second_row() {
        val due = 200 * DAY
        val check = Check(id = 7, type = CheckType.RECEIVABLE, amountToman = 3_500_000, dueDate = due)
        val transaction = tx(20, 3_500_000, TransactionType.INCOME, due + DAY, "وصول چک")

        val result = CheckMatcher().match(listOf(check), listOf(transaction))

        assertEquals(SmartSuggestionType.CHECK_MATCH, result.single().type)
        assertEquals(7L, result.single().checkId)
        assertTrue(result.single().explanation.contains("دوباره"))
    }

    private fun tx(
        id: Long,
        amount: Long,
        type: TransactionType,
        date: Long,
        description: String,
        bankName: String = "آزمایشی"
    ) = Transaction(
            id = id,
            amountToman = amount,
            type = type,
            bankName = bankName,
            description = description,
            date = date,
            source = TransactionSource.SMS_AUTO
        )

    private companion object {
        const val DAY = 86_400_000L
    }
}
