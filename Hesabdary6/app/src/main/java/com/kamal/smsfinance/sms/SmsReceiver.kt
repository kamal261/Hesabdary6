package com.kamal.smsfinance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kamal.smsfinance.SmsFinanceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives SMS_RECEIVED broadcasts, parses each PDU with SmsParser, and if it
 * looks like a bank transaction, stores it immediately -- silently, with no
 * system notification. The user reviews and categorizes new transactions
 * next time they open the transaction list. Runs off the main thread using a
 * short-lived coroutine scope since BroadcastReceivers must finish quickly.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val pendingResult = goAsync()

        val app = context.applicationContext as SmsFinanceApp
        val repository = app.repository

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (msg in messages) {
                    val sender = msg.originatingAddress ?: continue
                    val body = msg.messageBody ?: continue
                    val timestamp = msg.timestampMillis
                    repository.importSingleSms(sender, body, timestamp)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
