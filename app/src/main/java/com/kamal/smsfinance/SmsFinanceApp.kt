package com.kamal.smsfinance

import android.app.Application
import com.kamal.smsfinance.data.AppDatabase
import com.kamal.smsfinance.data.TransactionRepository

/**
 * No notification channel is created here on purpose: per product requirements,
 * new transactions (from SMS) and check due-date reminders are surfaced only
 * inside the app UI, never as system notifications.
 */
class SmsFinanceApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        TransactionRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            counterpartyDao = database.counterpartyDao(),
            checkDao = database.checkDao(),
            smartRuleDao = database.smartRuleDao(),
            context = this
        )
    }
}
