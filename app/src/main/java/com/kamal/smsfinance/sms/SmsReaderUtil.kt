// SmsFinance file version: 2 — readInbox() now accepts an optional sinceMillis filter (ported
// from the sibling branch's fix for the same gap: reading the entire SMS history by default,
// with no way to scope it, was both slow and mostly irrelevant on a phone with years of SMS).
package com.kamal.smsfinance.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RawSms(
    val sender: String,
    val body: String,
    val timestamp: Long,
    /** Telephony provider row id; null when a caller constructs a message manually. */
    val id: String? = null
)

object SmsReaderUtil {

    fun hasReadSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Reads inbox SMS after a persisted cursor. The inclusive timestamp fallback is intentional:
     * SMS providers may assign the same millisecond to multiple messages, while the optional id
     * breaks ties for a stable incremental scan. */
    suspend fun readInbox(
        context: Context,
        sinceMillis: Long? = null,
        afterSmsId: String? = null
    ): List<RawSms> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission(context)) return@withContext emptyList()

        val results = mutableListOf<RawSms>()
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection: String?
        val selectionArgs: Array<String>?
        if (sinceMillis != null && afterSmsId != null) {
            selection = "(${Telephony.Sms.DATE} > ?) OR (${Telephony.Sms.DATE} = ? AND ${Telephony.Sms._ID} > ?)"
            selectionArgs = arrayOf(sinceMillis.toString(), sinceMillis.toString(), afterSmsId)
        } else if (sinceMillis != null) {
            selection = "${Telephony.Sms.DATE} >= ?"
            selectionArgs = arrayOf(sinceMillis.toString())
        } else if (afterSmsId != null) {
            selection = "${Telephony.Sms._ID} > ?"
            selectionArgs = arrayOf(afterSmsId)
        } else {
            selection = null
            selectionArgs = null
        }

        context.contentResolver.query(uri, projection, selection, selectionArgs, "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC")
            ?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressIdx) ?: continue
                    val body = cursor.getString(bodyIdx) ?: continue
                    val date = cursor.getLong(dateIdx)
                    val id = cursor.getString(idIdx)
                    results.add(RawSms(sender, body, date, id))
                }
            }
        results
    }

    /** Reads inbox SMS from a specific sender. Must only be called after READ_SMS is granted. */
    suspend fun readInboxForSender(context: Context, sender: String): List<RawSms> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission(context)) return@withContext emptyList()

        val results = mutableListOf<RawSms>()
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.ADDRESS} = ?"
        val selectionArgs = arrayOf(sender)

        context.contentResolver.query(uri, projection, selection, selectionArgs, "${Telephony.Sms.DATE} DESC")
            ?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val s = cursor.getString(addressIdx) ?: continue
                    val body = cursor.getString(bodyIdx) ?: continue
                    val date = cursor.getLong(dateIdx)
                    results.add(RawSms(s, body, date))
                }
            }
        results
    }
}
