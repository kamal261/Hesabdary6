package com.kamal.smsfinance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles MMS/WAP Push received - optional but recommended for Default SMS App.
 *
 * Full MMS PDU parsing is a hidden/internal Android API, so on receive we only
 * log the event here. Downloading and rendering MMS is out of scope for a
 * bank-transaction parser; this receiver exists so the app can claim
 * MMS-related broadcasts without crashing the Default SMS App role.
 */
class MmsReceiver : BroadcastReceiver() {
    private val TAG = "MmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val pdu = intent.getByteArrayExtra("data")
        Log.d(TAG, "MMS/WAP push received, pdu length=${pdu?.size ?: 0}. " +
            "Full MMS content download is out of scope for a bank-SMS parser.")
    }
}