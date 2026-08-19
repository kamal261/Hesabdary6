// SmsFinance file version: 1
package com.kamal.smsfinance.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sms_finance_settings")

/** Theme mode choice. SYSTEM follows the OS setting. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

class SettingsStore(private val context: Context) {

    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val WEBHOOK_KEY = stringPreferencesKey("sheets_webhook_url")
    private val AUTO_SCAN_KEY = booleanPreferencesKey("auto_scan_on_launch")
    private val SMALL_AMOUNT_ENABLED_KEY = booleanPreferencesKey("small_amount_enabled")
    private val SMALL_AMOUNT_THRESHOLD_KEY = longPreferencesKey("small_amount_threshold")
    private val SMALL_AMOUNT_CATEGORY_KEY = longPreferencesKey("small_amount_category_id")
    private val ONBOARDING_SCAN_DONE_KEY = booleanPreferencesKey("onboarding_scan_done")
    private val INITIAL_SCAN_DONE_KEY = booleanPreferencesKey("initial_scan_done")
    private val LAST_SCAN_TIMESTAMP_KEY = longPreferencesKey("last_scan_timestamp")
    private val LAST_SCAN_SMS_ID_KEY = stringPreferencesKey("last_scan_sms_id")
    private val ONBOARDING_GUIDE_DONE_KEY = booleanPreferencesKey("onboarding_guide_done")
    private val LAST_LOCAL_BACKUP_TIMESTAMP_KEY = longPreferencesKey("last_local_backup_timestamp")
    private val LAST_DRIVE_BACKUP_TIMESTAMP_KEY = longPreferencesKey("last_drive_backup_timestamp")
    private val BACKUP_REMINDER_SNOOZE_UNTIL_KEY = longPreferencesKey("backup_reminder_snooze_until")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[THEME_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val webhookUrl: Flow<String> = context.dataStore.data.map { it[WEBHOOK_KEY] ?: "" }

    val autoScanOnLaunch: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SCAN_KEY] ?: true }

    // Auto-categorization for small expenses (e.g. anything under 100,000
    // Toman goes straight into a "متفرقه"-style category the user picks).
    val smallAmountEnabled: Flow<Boolean> = context.dataStore.data.map { it[SMALL_AMOUNT_ENABLED_KEY] ?: false }
    val smallAmountThreshold: Flow<Long> = context.dataStore.data.map { it[SMALL_AMOUNT_THRESHOLD_KEY] ?: 100_000L }
    val smallAmountCategoryId: Flow<Long?> = context.dataStore.data.map { it[SMALL_AMOUNT_CATEGORY_KEY] }

    /** True once the first historical scan range has been accepted and processed. */
    val initialScanDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[INITIAL_SCAN_DONE_KEY] ?: prefs[ONBOARDING_SCAN_DONE_KEY] ?: false
    }

    /** Backward-compatible name used by older UI code. */
    val onboardingScanDone: Flow<Boolean> = initialScanDone

    /** Timestamp of the newest SMS cursor committed by a completed scan. */
    val lastScanTimestamp: Flow<Long?> = context.dataStore.data.map { it[LAST_SCAN_TIMESTAMP_KEY] }

    /** Provider row id of the newest SMS cursor, when available. */
    val lastScanSmsId: Flow<String?> = context.dataStore.data.map { it[LAST_SCAN_SMS_ID_KEY] }

    /** True once the user has been through (or explicitly skipped) the first-run guide/tour +
     * category-builder step -- gates OnboardingScreen so it only ever shows once. */
    val onboardingGuideDone: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_GUIDE_DONE_KEY] ?: false }

    val lastLocalBackupTimestamp: Flow<Long?> = context.dataStore.data.map { it[LAST_LOCAL_BACKUP_TIMESTAMP_KEY] }
    val lastDriveBackupTimestamp: Flow<Long?> = context.dataStore.data.map { it[LAST_DRIVE_BACKUP_TIMESTAMP_KEY] }
    val backupReminderSnoozeUntil: Flow<Long> = context.dataStore.data.map { it[BACKUP_REMINDER_SNOOZE_UNTIL_KEY] ?: 0L }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setWebhookUrl(url: String) {
        context.dataStore.edit { it[WEBHOOK_KEY] = url }
    }

    suspend fun setAutoScanOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_SCAN_KEY] = enabled }
    }

    suspend fun setSmallAmountEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SMALL_AMOUNT_ENABLED_KEY] = enabled }
    }

    suspend fun setSmallAmountThreshold(threshold: Long) {
        context.dataStore.edit { it[SMALL_AMOUNT_THRESHOLD_KEY] = threshold }
    }

    suspend fun setSmallAmountCategoryId(categoryId: Long?) {
        context.dataStore.edit {
            if (categoryId != null) it[SMALL_AMOUNT_CATEGORY_KEY] = categoryId else it.remove(SMALL_AMOUNT_CATEGORY_KEY)
        }
    }

    suspend fun setInitialScanDone(done: Boolean) {
        context.dataStore.edit {
            it[INITIAL_SCAN_DONE_KEY] = done
            // Keep the legacy key in sync for installs upgraded from the previous branch.
            it[ONBOARDING_SCAN_DONE_KEY] = done
        }
    }

    suspend fun setOnboardingScanDone(done: Boolean) = setInitialScanDone(done)

    suspend fun setLastScanCursor(timestamp: Long, smsId: String?) {
        context.dataStore.edit {
            it[LAST_SCAN_TIMESTAMP_KEY] = timestamp
            if (smsId.isNullOrBlank()) it.remove(LAST_SCAN_SMS_ID_KEY) else it[LAST_SCAN_SMS_ID_KEY] = smsId
        }
    }

    suspend fun clearLastScanCursor() {
        context.dataStore.edit {
            it.remove(LAST_SCAN_TIMESTAMP_KEY)
            it.remove(LAST_SCAN_SMS_ID_KEY)
        }
    }

    suspend fun setOnboardingGuideDone(done: Boolean) {
        context.dataStore.edit { it[ONBOARDING_GUIDE_DONE_KEY] = done }
    }

    suspend fun markLocalBackupSucceeded(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            it[LAST_LOCAL_BACKUP_TIMESTAMP_KEY] = timestamp
            it.remove(BACKUP_REMINDER_SNOOZE_UNTIL_KEY)
        }
    }

    suspend fun markDriveBackupSucceeded(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            it[LAST_DRIVE_BACKUP_TIMESTAMP_KEY] = timestamp
            it.remove(BACKUP_REMINDER_SNOOZE_UNTIL_KEY)
        }
    }

    suspend fun snoozeBackupReminder(until: Long) {
        context.dataStore.edit { it[BACKUP_REMINDER_SNOOZE_UNTIL_KEY] = until }
    }
}
