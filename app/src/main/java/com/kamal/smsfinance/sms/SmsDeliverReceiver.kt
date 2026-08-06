package com.kamal.smsfinance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.kamal.smsfinance.SmsFinanceApp
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionSource
import com.kamal.smsfinance.data.TransactionType

/**
 * Handles SMS_DELIVER broadcast - REQUIRED for Default SMS App.
 * This is the primary way the system delivers incoming SMS to the default SMS app.
 * Unlike SMS_RECEIVED, this is only sent to the default SMS app.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    private val TAG = "SmsDeliverReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val originatingAddress = message.originatingAddress ?: ""
            val messageBody = message.messageBody ?: ""
            val timestampMillis = message.timestampMillis

            Log.d(TAG, "SMS_DELIVER from $originatingAddress: $messageBody")

            // Skip if this is from our own app (prevent loops)
            if (isFromSelf(context, originatingAddress)) continue

            // Parse and store via existing SmsParser logic
            parseAndStore(context, originatingAddress, messageBody, timestampMillis)
        }

        // Important: abortBroadcast() to prevent other apps from receiving
        // (Only default SMS app should do this)
        abortBroadcast()
    }

    private fun isFromSelf(context: Context, sender: String): Boolean {
        // Check if sender matches our own number (if known)
        val myNumber = context.getSharedPreferences("sms_finance", Context.MODE_PRIVATE)
            .getString("own_phone_number", null)
        return myNumber != null && sender.endsWith(myNumber.takeLast(10))
    }

    private fun parseAndStore(context: Context, sender: String, body: String, timestamp: Long) {
        // Delegate to existing SmsParser + Repository
        val result = SmsParser.parse(sender, body, timestamp)
        when (result) {
            is SmsParseResult.Recognized -> {
                val parsed = result.parsed
                val transaction = Transaction(
                    amountToman = parsed.amountToman,
                    type = parsed.type,
                    bankName = parsed.bankName,
                    description = parsed.description,
                    date = parsed.timestamp,
                    source = TransactionSource.SMS_DELIVER,
                    rawSms = parsed.rawSms,
                    smsSender = parsed.sender,
                    accountTail = parsed.accountTail
                )
                // Use the app's repository (via Application context)
                (context.applicationContext as? SmsFinanceApp)?.transactionRepository?.insert(transaction)
            }
            is SmsParseResult.Unidentified, SmsParseResult.Ignored -> {
                // Do nothing - Unidentified goes to separate table, Ignored is discarded
            }
        }
    }
}