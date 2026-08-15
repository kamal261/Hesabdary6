package com.kamal.smsfinance.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uploads/downloads the JSON backup file to the signed-in user's Google
 * Drive "appDataFolder" -- a space hidden from the user's normal Drive view
 * and inaccessible to any other app, using the Drive v3 REST API directly
 * via OkHttp (keeps the dependency footprint small; no Drive SDK needed).
 */
object GoogleDriveUploader {

    private const val BACKUP_FILE_NAME = "sms_finance_backup.json"
    private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class DriveResult {
        data class Success(val message: String) : DriveResult()
        data class Failure(val message: String) : DriveResult()
    }

    suspend fun upload(accessToken: String, backupFile: File): DriveResult = withContext(Dispatchers.IO) {
        try {
            val existingId = findExistingBackupId(accessToken)

            val metadata = JSONObject().apply {
                put("name", BACKUP_FILE_NAME)
                if (existingId == null) put("parents", listOf("appDataFolder"))
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    okhttp3.Headers.headersOf("Content-Type", "application/json; charset=UTF-8"),
                    metadata.toString().toRequestBody("application/json".toMediaType())
                )
                .addPart(
                    okhttp3.Headers.headersOf("Content-Type", "application/json"),
                    backupFile.readText().toRequestBody("application/json".toMediaType())
                )
                .build()

            val url = if (existingId != null) {
                "https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=multipart"
            } else UPLOAD_URL

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .let { if (existingId != null) it.patch(body) else it.post(body) }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    DriveResult.Success("پشتیبان با موفقیت در Google Drive ذخیره شد")
                } else {
                    DriveResult.Failure("خطای Drive: ${response.code}")
                }
            }
        } catch (e: Exception) {
            DriveResult.Failure(e.message ?: "خطای ناشناخته در اتصال به Drive")
        }
    }

    suspend fun downloadLatestBackup(accessToken: String, destination: File): DriveResult =
        withContext(Dispatchers.IO) {
            try {
                val fileId = findExistingBackupId(accessToken)
                    ?: return@withContext DriveResult.Failure("هیچ نسخه پشتیبانی در Drive یافت نشد")

                val request = Request.Builder()
                    .url("$FILES_URL/$fileId?alt=media")
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext DriveResult.Failure("خطای دانلود: ${response.code}")
                    }
                    destination.writeBytes(response.body?.bytes() ?: ByteArray(0))
                    DriveResult.Success("نسخه پشتیبان از Drive دانلود شد")
                }
            } catch (e: Exception) {
                DriveResult.Failure(e.message ?: "خطای ناشناخته در دانلود از Drive")
            }
        }

    private fun findExistingBackupId(accessToken: String): String? {
        val url = "$FILES_URL?spaces=appDataFolder&q=name='$BACKUP_FILE_NAME'&fields=files(id,name)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val files = json.optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).getString("id")
        }
    }
}
