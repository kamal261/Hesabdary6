package com.kamal.smsfinance

import android.app.Application
import com.kamal.smsfinance.data.AppDatabase
import com.kamal.smsfinance.data.TransactionRepository

/**
 * No notification channel is created here on purpose: per product requirements,
 * new transactions (from inbox scans) and check due-date reminders are surfaced only
 * inside the app UI, never as system notifications. SMS scanning runs when the app is opened;
 * the app does not keep a background receiver alive.
 */
class SmsFinanceApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        TransactionRepository(
            db = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            counterpartyDao = database.counterpartyDao(),
            counterpartyReminderDao = database.counterpartyReminderDao(),
            checkDao = database.checkDao(),
            smartRuleDao = database.smartRuleDao(),
            unidentifiedSmsDao = database.unidentifiedSmsDao(),
            context = this
        )
    }
}
