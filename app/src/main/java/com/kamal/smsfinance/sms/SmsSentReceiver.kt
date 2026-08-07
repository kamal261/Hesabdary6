package com.kamal.smsfinance.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.Sms
import android.util.Log

/**
 * Handles SMS_SENT broadcast - status of outgoing SMS.
 * Required for Default SMS App to track delivery status.
 */
class SmsSentReceiver : BroadcastReceiver() {
    private val TAG = "SmsSentReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val resultCode = resultCode

        when (resultCode) {
            Activity.RESULT_OK -> Log.d(TAG, "SMS sent successfully")
            Sms.RESULT_ERROR_GENERIC_FAILURE -> Log.e(TAG, "SMS generic failure")
            Sms.RESULT_ERROR_RADIO_OFF -> Log.e(TAG, "SMS radio off")
            Sms.RESULT_ERROR_NULL_PDU -> Log.e(TAG, "SMS null PDU")
            Sms.RESULT_ERROR_NO_SERVICE -> Log.e(TAG, "SMS no service")
            else -> Log.w(TAG, "SMS sent with unknown result: $resultCode")
        }

        // Could update UI/database with delivery status here
    }
}