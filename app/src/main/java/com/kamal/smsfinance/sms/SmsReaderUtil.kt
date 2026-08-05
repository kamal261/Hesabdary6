package com.kamal.smsfinance.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RawSms(val sender: String, val body: String, val timestamp: Long)

object SmsReaderUtil {

    fun hasReadSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Reads every inbox SMS. Must only be called after READ_SMS is granted. */
    suspend fun readInbox(context: Context): List<RawSms> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission(context)) return@withContext emptyList()

        val results = mutableListOf<RawSms>()
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        context.contentResolver.query(uri, projection, null, null, "${Telephony.Sms.DATE} DESC")
            ?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressIdx) ?: continue
                    val body = cursor.getString(bodyIdx) ?: continue
                    val date = cursor.getLong(dateIdx)
                    results.add(RawSms(sender, body, date))
                }
            }
        results
    }

    /**
     * Reads inbox SMS from one sender only, ascending by time, for the
     * "SMS context" screen. Sender matching uses the last 8 characters
     * (handles "+981000..." vs "1000..." vs "Mellat" variations).
     */
    suspend fun readInboxForSender(context: Context, sender: String): List<RawSms> =
        withContext(Dispatchers.IO) {
            val target = sender.takeLast(8)
            readInbox(context)
                .filter { it.sender.takeLast(8) == target }
                .sortedBy { it.timestamp }
        }
}
