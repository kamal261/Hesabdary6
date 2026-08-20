package com.kamal.smsfinance.ui.screens

import androidx.compose.runtime.Composable
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.Transaction

/**
 * Drill-down opened from the statistics bank breakdown. Each row opens the full
 * SMS text and offers a copy action through TransactionDrilldownScreen.
 */
@Composable
fun BankTransactionsScreen(
    bank: String,
    transactions: List<Transaction>,
    categories: List<Category>,
    onBack: () -> Unit
) {
    TransactionDrilldownScreen(
        title = "تراکنش‌های $bank",
        transactions = transactions,
        categories = categories,
        onBack = onBack
    )
}
