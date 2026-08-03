package com.kamal.smsfinance.util

import android.content.Context
import android.net.Uri
import com.kamal.smsfinance.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local backup/restore using a hand-rolled JSON format (no extra dependency
 * needed). Backups are plain files under getExternalFilesDir("backups") so
 * the user can move/copy them manually, or restore via a content:// Uri
 * (e.g. picked from a file manager or cloud-synced folder, including a
 * locally-synced Google Drive folder). Covers all five tables: transactions,
 * categories, counterparties, checks, smart rules.
 *
 * "version" here is a BACKUP-FORMAT version, intentionally decoupled from
 * Room's schema version (which changes independently as columns are added).
 * Forward/backward compatibility is handled by keeping every field read
 * with root.has(...) / opt*() -- an older backup missing a newer table (e.g.
 * "smartRules") simply restores nothing for it instead of failing, and a
 * newer backup opened by older code just ignores fields it doesn't expect.
 * This tolerant-parsing approach is deliberately simpler than a strict
 * migration ladder, which isn't justified yet for a single-device local file.
 */
object BackupManager {

    private const val BACKUP_FORMAT_VERSION = 3

    private fun String?.orNull(): Any = this ?: JSONObject.NULL
    private fun Long?.orNull(): Any = this ?: JSONObject.NULL

    suspend fun createBackup(context: Context, db: AppDatabase): File = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", BACKUP_FORMAT_VERSION)
        root.put("createdAt", System.currentTimeMillis())

        root.put("transactions", JSONArray().apply {
            db.transactionDao().getAllOnce().forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("amountToman", t.amountToman)
                    put("type", t.type.name)
                    put("bankName", t.bankName)
                    put("description", t.description)
                    put("date", t.date)
                    put("source", t.source.name)
                    put("rawSms", t.rawSms.orNull())
                    put("smsSender", t.smsSender.orNull())
                    put("accountTail", t.accountTail.orNull())
                    put("categoryId", t.categoryId.orNull())
                    put("counterpartyId", t.counterpartyId.orNull())
                    put("isIndirectSettlement", t.isIndirectSettlement)
                })
            }
        })

        root.put("categories", JSONArray().apply {
            db.categoryDao().getAllOnce().forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("kind", c.kind.name); put("isDefault", c.isDefault)
                })
            }
        })

        root.put("counterparties", JSONArray().apply {
            db.counterpartyDao().getAllOnce().forEach { cp ->
                put(JSONObject().apply {
                    put("id", cp.id)
                    put("name", cp.name)
                    put("type", cp.type.name)
                    put("phone", cp.phone.orNull())
                    put("address", cp.address.orNull())
                    put("description", cp.description.orNull())
                    put("createdAt", cp.createdAt)
                })
            }
        })

        root.put("checks", JSONArray().apply {
            db.checkDao().getAllOnce().forEach { chk ->
                put(JSONObject().apply {
                    put("id", chk.id)
                    put("type", chk.type.name)
                    put("counterpartyId", chk.counterpartyId.orNull())
                    put("amountToman", chk.amountToman)
                    put("dueDate", chk.dueDate)
                    put("status", chk.status.name)
                    put("paidDate", chk.paidDate.orNull())
                    put("reminderDays", chk.reminderDays)
                    put("description", chk.description.orNull())
                    put("settledTransactionId", chk.settledTransactionId.orNull())
                    put("createdAt", chk.createdAt)
                })
            }
        })

        root.put("smartRules", JSONArray().apply {
            db.smartRuleDao().getAllRulesOnce().forEach { rule ->
                put(JSONObject().apply {
                    put("id", rule.id)
                    put("pattern", rule.pattern)
                    put("categoryId", rule.categoryId.orNull())
                    put("counterpartyId", rule.counterpartyId.orNull())
                    put("createdAt", rule.createdAt)
                })
            }
        })

        val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val file = File(dir, "backup_${System.currentTimeMillis()}.json")
        file.writeText(root.toString(2))
        file
    }

    /**
     * Restores from a backup file. Note: because Transaction/Check rows
     * reference categoryId/counterpartyId/settledTransactionId by their
     * *original* auto-generated ids, restoring into a database that already
     * has other data can misassign those links if ids were reused. For a
     * clean restore, do it right after "حذف تمام تراکنش‌ها" on an otherwise
     * empty database.
     */
    suspend fun restoreBackup(context: Context, uri: Uri, db: AppDatabase): Int =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext 0
            val root = JSONObject(text)
            var restored = 0

            if (root.has("categories")) {
                val arr = root.getJSONArray("categories")
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Category(
                        name = o.getString("name"),
                        kind = CategoryKind.valueOf(o.getString("kind")),
                        isDefault = o.optBoolean("isDefault", false)
                    )
                }
                db.categoryDao().insertAll(list)
            }

            if (root.has("counterparties")) {
                val arr = root.getJSONArray("counterparties")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.counterpartyDao().insert(
                        Counterparty(
                            name = o.getString("name"),
                            type = CounterpartyType.valueOf(o.getString("type")),
                            phone = o.optStringOrNull("phone"),
                            address = o.optStringOrNull("address"),
                            description = o.optStringOrNull("description"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            if (root.has("transactions")) {
                val arr = root.getJSONArray("transactions")
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Transaction(
                        amountToman = o.getLong("amountToman"),
                        type = TransactionType.valueOf(o.getString("type")),
                        bankName = o.getString("bankName"),
                        description = o.getString("description"),
                        date = o.getLong("date"),
                        source = TransactionSource.valueOf(o.getString("source")),
                        rawSms = o.optStringOrNull("rawSms"),
                        smsSender = o.optStringOrNull("smsSender"),
                        accountTail = o.optStringOrNull("accountTail"),
                        categoryId = o.optLongOrNull("categoryId"),
                        counterpartyId = o.optLongOrNull("counterpartyId"),
                        isIndirectSettlement = o.optBoolean("isIndirectSettlement", false)
                    )
                }
                db.transactionDao().insertAll(list)
                restored += list.size
            }

            if (root.has("checks")) {
                val arr = root.getJSONArray("checks")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.checkDao().insert(
                        Check(
                            type = CheckType.valueOf(o.getString("type")),
                            counterpartyId = o.optLongOrNull("counterpartyId"),
                            amountToman = o.getLong("amountToman"),
                            dueDate = o.getLong("dueDate"),
                            status = CheckStatus.valueOf(o.getString("status")),
                            paidDate = o.optLongOrNull("paidDate"),
                            reminderDays = o.optInt("reminderDays", 3),
                            description = o.optStringOrNull("description"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            if (root.has("smartRules")) {
                val arr = root.getJSONArray("smartRules")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.smartRuleDao().insertRule(
                        SmartRule(
                            pattern = o.getString("pattern"),
                            categoryId = o.optLongOrNull("categoryId"),
                            counterpartyId = o.optLongOrNull("counterpartyId"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            restored
        }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null
}
