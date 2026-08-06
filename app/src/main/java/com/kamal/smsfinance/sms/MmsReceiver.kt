package com.kamal.smsfinance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Handles MMS/WAP Push received - optional but recommended for Default SMS App.
 */
class MmsReceiver : BroadcastReceiver() {
    private val TAG = "MmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val pdu = intent.getByteArrayExtra("data")
        if (pdu == null) return

        try {
            val pduParser = Telephony.Mms.PduParser(pdu)
            val notificationInd = pduParser.parseNotificationInd()
            val transactionId = notificationInd.transactionId
            val contentLocation = notificationInd.contentLocation

            Log.d(TAG, "MMS received: transactionId=${transactionId?.toHexString()}, contentLocation=${contentLocation?.toHexString()}")

            // For now, just log. Full MMS download requires carrier connection.
            // Could trigger download via Telephony.Mms.downloadMultipartMms()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MMS notification", e)
        }
    }
}