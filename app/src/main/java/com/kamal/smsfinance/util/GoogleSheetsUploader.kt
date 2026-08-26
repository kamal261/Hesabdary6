package com.kamal.smsfinance.util

import com.kamal.smsfinance.data.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends transactions to a Google Sheet through a Google Apps Script Web App
 * acting as a simple webhook (no OAuth needed on-device, which keeps the app
 * free of the Google Sign-In SDK). The user deploys a tiny Apps Script bound
 * to their sheet and pastes its /exec URL into Settings.
 *
 * This is deliberately append-only (no update/delete sync): each upload
 * appends the current transaction list as new rows. The payload includes
 * each transaction's `id` so a future idempotent-sync version could diff
 * against it, but building that sync logic now would be accounting-grade
 * complexity for what's meant to stay an optional, simple export feature.
 *
 * Example Apps Script (deploy as Web App, "Anyone" access):
 *
 *   function doPost(e) {
 *     var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
 *     var rows = JSON.parse(e.postData.contents);
 *     rows.forEach(function(r) {
 *       sheet.appendRow([r.id, r.date, r.bank, r.type, r.amount, r.description]);
 *     });
 *     return ContentService.createTextOutput("OK");
 *   }
 */
object GoogleSheetsUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    sealed class UploadResult {
        data class Success(val count: Int) : UploadResult()
        data class Failure(val message: String) : UploadResult()
    }

    suspend fun upload(webhookUrl: String, transactions: List<Transaction>): UploadResult =
        withContext(Dispatchers.IO) {
            if (webhookUrl.isBlank()) return@withContext UploadResult.Failure("آدرس webhook تنظیم نشده است")
            if (!webhookUrl.startsWith("https://"))
                return@withContext UploadResult.Failure("فقط آدرس https پشتیبانی می‌شود (ارسال داده‌های مالی روی اتصال ناامن ممنوع است)")

            try {
                val array = JSONArray()
                for (t in transactions) {
                    val obj = JSONObject()
                    obj.put("id", t.id)
                    obj.put("date", t.date)
                    obj.put("bank", t.bankName)
                    obj.put("type", if (t.type.name == "INCOME") "واریز" else "برداشت")
                    obj.put("amount", t.amountToman)
                    obj.put("description", t.description)
                    array.put(obj)
                }

                val body = array.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(webhookUrl).post(body).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        UploadResult.Success(transactions.size)
                    } else {
                        UploadResult.Failure("خطای سرور: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                UploadResult.Failure(e.message ?: "خطای ناشناخته در ارسال")
            }
        }
}
