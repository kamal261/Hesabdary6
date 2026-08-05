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

    // First-run scan: window chosen by the user on first launch; after the
    // initial scan we remember it so the app never asks/rescans automatically.
    private val INITIAL_SCAN_WINDOW_DAYS_KEY = longPreferencesKey("initial_scan_window_days")
    private val INITIAL_SCAN_DONE_KEY = booleanPreferencesKey("initial_scan_done")
    private val LAST_SCAN_TIMESTAMP_KEY = longPreferencesKey("last_scan_timestamp")

    val initialScanWindowDays: Flow<Long> = context.dataStore.data.map { it[INITIAL_SCAN_WINDOW_DAYS_KEY] ?: 7L }
    val initialScanDone: Flow<Boolean> = context.dataStore.data.map { it[INITIAL_SCAN_DONE_KEY] ?: false }
    val lastScanTimestamp: Flow<Long> = context.dataStore.data.map { it[LAST_SCAN_TIMESTAMP_KEY] ?: 0L }

    suspend fun setInitialScanWindowDays(days: Long) {
        context.dataStore.edit { it[INITIAL_SCAN_WINDOW_DAYS_KEY] = days }
    }

    suspend fun setInitialScanDone(done: Boolean) {
        context.dataStore.edit { it[INITIAL_SCAN_DONE_KEY] = done }
    }

    suspend fun setLastScanTimestamp(ts: Long) {
        context.dataStore.edit { it[LAST_SCAN_TIMESTAMP_KEY] = ts }
    }

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
}
