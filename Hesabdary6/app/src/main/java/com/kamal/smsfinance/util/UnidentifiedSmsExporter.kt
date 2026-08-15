// SmsFinance file version: 1 — exports the full, untruncated raw body of every active
// (non-dismissed) Unidentified SMS. Unlike Transaction.description (which is built from a
// truncated/processed working copy of the SMS body -- see SmsParser.buildDescription), this
// exports UnidentifiedSms.body as-is: the whole point of this export is finding real,
// full-text patterns across many messages at once, not a human-readable summary of one.
package com.kamal.smsfinance.util

import android.content.Context
import com.kamal.smsfinance.data.UnidentifiedSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UnidentifiedSmsExporter {

    /** Writes every active Unidentified SMS (sender, full raw body, timestamp) to a CSV under
     * context.getExternalFilesDir("exports"). Reuses CsvExporter.shareIntent() to share it --
     * that function is generic over any CSV file, not specific to transactions. */
    suspend fun export(context: Context, items: List<UnidentifiedSms>): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
            val file = File(dir, "unidentified_sms_${System.currentTimeMillis()}.csv")

            file.outputStream().use { out ->
                out.write(0xEF); out.write(0xBB); out.write(0xBF) // UTF-8 BOM for Excel
                out.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.appendLine("تاریخ,فرستنده,متن کامل پیامک")
                    for (item in items) {
                        val date = JalaliDate.formatDateTime(item.timestamp)
                        val sender = item.sender.replace("\"", "'")
                        val body = item.body.replace("\"", "'").replace("\n", " ")
                        writer.appendLine("\"$date\",\"$sender\",\"$body\"")
                    }
                }
            }
            file
        }
}
