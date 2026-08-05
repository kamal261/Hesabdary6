package com.kamal.smsfinance.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CsvExporter {

    /**
     * Writes transactions to a CSV file (Excel-compatible: UTF-8 BOM so
     * Persian text renders correctly when opened in Excel) under
     * context.getExternalFilesDir("exports") and returns the file.
     */
    suspend fun export(context: Context, transactions: List<Transaction>, recurringIds: Set<Long> = emptySet()): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
            val fileName = "transactions_${System.currentTimeMillis()}.csv"
            val file = File(dir, fileName)

            file.outputStream().use { out ->
                // UTF-8 BOM so Excel detects encoding correctly for Persian text
                out.write(0xEF); out.write(0xBB); out.write(0xBF)
                out.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.appendLine("تاریخ,بانک,نوع,مبلغ (تومان),توضیحات,منبع,تکراری")
                    for (t in transactions) {
                        val date = JalaliDate.formatDateTime(t.date)
                        val type = if (t.type.name == "INCOME") "واریز" else "برداشت"
                        val source = when {
                            t.isIndirectSettlement -> "تسویه غیرمستقیم"
                            t.source == TransactionSource.CHECK_SETTLEMENT -> "تسویه چک"
                            t.source == TransactionSource.SMS_AUTO -> "پیامک خودکار"
                            else -> "دستی"
                        }
                        val recurring = if (t.id in recurringIds) "بله" else "خیر"
                        val desc = t.description.replace("\"", "'").replace("\n", " ")
                        writer.appendLine(
                            "\"$date\",\"${t.bankName}\",\"$type\",${t.amountToman},\"$desc\",\"$source\",\"$recurring\""
                        )
                    }
                }
            }
            file
        }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
