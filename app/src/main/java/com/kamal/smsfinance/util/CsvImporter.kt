// SmsFinance file version: 1
package com.kamal.smsfinance.util

import android.content.Context
import android.net.Uri
import com.kamal.smsfinance.data.AppDatabase
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.CategoryKind
import com.kamal.smsfinance.data.Counterparty
import com.kamal.smsfinance.data.CounterpartyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CsvImportResult(val imported: Int, val skipped: Int)

/**
 * One-way CSV import for categories and counterparties. Duplicate names
 * (case-insensitive) are skipped, so re-importing the same file -- or a file
 * that happens to include the default category names -- never creates
 * duplicates and never touches the seeded defaults.
 *
 * Expected formats (first line may optionally be a header; any values not
 * recognized are ignored gracefully):
 *   categories.csv:     name,kind        e.g. "خواربار,هزینه" or "خواربار,EXPENSE"
 *   counterparties.csv: name,type[,phone[,address[,description]]]
 *                        e.g. "علی رضایی,عامل" or "علی رضایی,WORKER,0912...,,"
 */
object CsvImporter {

    private val CATEGORY_KIND_LABELS = mapOf(
        "درآمد" to CategoryKind.INCOME,
        "هزینه" to CategoryKind.EXPENSE,
        "وصول طلب" to CategoryKind.DEBT_COLLECTION,
        "پرداخت بدهی" to CategoryKind.DEBT_PAYMENT
    )

    private val COUNTERPARTY_TYPE_LABELS = mapOf(
        "مشتری" to CounterpartyType.CUSTOMER,
        "عامل" to CounterpartyType.WORKER,
        "کارگر" to CounterpartyType.WORKER
    )

    suspend fun importCategories(context: Context, uri: Uri, db: AppDatabase): CsvImportResult =
        withContext(Dispatchers.IO) {
            var imported = 0
            var skipped = 0
            readLines(context, uri).forEach { line ->
                val cols = splitCsvLine(line)
                if (cols.size < 2) return@forEach
                val name = cols[0].trim()
                if (name.isEmpty() || name.equals("name", ignoreCase = true)) return@forEach // header row
                val kind = parseCategoryKind(cols[1].trim()) ?: return@forEach

                if (db.categoryDao().countByName(name) > 0) {
                    skipped++
                } else {
                    db.categoryDao().insert(Category(name = name, kind = kind, isDefault = false))
                    imported++
                }
            }
            CsvImportResult(imported, skipped)
        }

    suspend fun importCounterparties(context: Context, uri: Uri, db: AppDatabase): CsvImportResult =
        withContext(Dispatchers.IO) {
            var imported = 0
            var skipped = 0
            readLines(context, uri).forEach { line ->
                val cols = splitCsvLine(line)
                if (cols.size < 2) return@forEach
                val name = cols[0].trim()
                if (name.isEmpty() || name.equals("name", ignoreCase = true)) return@forEach // header row
                val type = parseCounterpartyType(cols[1].trim()) ?: return@forEach
                val phone = cols.getOrNull(2)?.trim()?.ifBlank { null }
                val address = cols.getOrNull(3)?.trim()?.ifBlank { null }
                val description = cols.getOrNull(4)?.trim()?.ifBlank { null }

                if (db.counterpartyDao().countByName(name) > 0) {
                    skipped++
                } else {
                    db.counterpartyDao().insert(
                        Counterparty(name = name, type = type, phone = phone, address = address, description = description)
                    )
                    imported++
                }
            }
            CsvImportResult(imported, skipped)
        }

    private fun parseCategoryKind(raw: String): CategoryKind? =
        CATEGORY_KIND_LABELS[raw] ?: runCatching { CategoryKind.valueOf(raw.uppercase()) }.getOrNull()

    private fun parseCounterpartyType(raw: String): CounterpartyType? =
        COUNTERPARTY_TYPE_LABELS[raw] ?: runCatching { CounterpartyType.valueOf(raw.uppercase()) }.getOrNull()

    private fun readLines(context: Context, uri: Uri): List<String> =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** Minimal CSV split: comma-separated, no quoted-field support (kept simple for a basic name/kind file). */
    private fun splitCsvLine(line: String): List<String> = line.split(",")
}
