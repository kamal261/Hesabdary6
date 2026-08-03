package com.kamal.smsfinance.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[THEME_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val webhookUrl: Flow<String> = context.dataStore.data.map { it[WEBHOOK_KEY] ?: "" }

    val autoScanOnLaunch: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SCAN_KEY] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setWebhookUrl(url: String) {
        context.dataStore.edit { it[WEBHOOK_KEY] = url }
    }

    suspend fun setAutoScanOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_SCAN_KEY] = enabled }
    }
}
