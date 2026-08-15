package com.kamal.smsfinance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.kamal.smsfinance.SmsFinanceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles SMS_DELIVER broadcast - REQUIRED for Default SMS App.
 * This is the primary way the system delivers incoming SMS to the default SMS app.
 * Unlike SMS_RECEIVED, this is only sent to the default SMS app.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    private val TAG = "SmsDeliverReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val app = context.applicationContext as? SmsFinanceApp ?: run {
            Log.w(TAG, "SmsFinanceApp not available, dropping ${messages.size} messages")
            return
        }

        for (message in messages) {
            val originatingAddress = message.originatingAddress ?: ""
            val messageBody = message.messageBody ?: ""
            val timestampMillis = message.timestampMillis

            Log.d(TAG, "SMS_DELIVER from $originatingAddress: $messageBody")

            // Skip if this is from our own app (prevent loops)
            if (isFromSelf(context, originatingAddress)) continue

            // Delegate to the repository's existing import pipeline (parse + dedup + insert)
            CoroutineScope(Dispatchers.IO).launch {
                app.repository.importSingleSms(originatingAddress, messageBody, timestampMillis)
            }
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
}