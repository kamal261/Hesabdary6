package com.kamal.smsfinance.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kamal.smsfinance.MainActivity
import com.kamal.smsfinance.R
import com.kamal.smsfinance.SmsFinanceApp
import com.kamal.smsfinance.data.TransactionType
import com.kamal.smsfinance.util.JalaliDate
import com.kamal.smsfinance.util.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Small, privacy-conscious widget. It shows local data only and is refreshed when
 * Android asks for an update or the app explicitly requests one. It never starts
 * a background worker and always labels the data with the latest scan time.
 */
class FinanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { widgetId ->
                    val views = buildViews(context, widgetId)
                    withContext(Dispatchers.Main) { manager.updateAppWidget(widgetId, views) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FinanceWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, FinanceWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }

        private suspend fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val app = context.applicationContext as SmsFinanceApp
            val transactions = app.database.transactionDao().getAllOnce()
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val today = transactions.filter { it.date >= startOfDay && it.transferGroupId == null }
            val income = today.filter { it.type == TransactionType.INCOME }.sumOf { it.amountToman }
            val expense = today.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountToman }
            val lastScan = SettingsStore(context).lastScanTimestamp.first()
            return RemoteViews(context.packageName, R.layout.widget_finance).apply {
                setTextViewText(R.id.widget_income, "ورودی امروز: ${formatToman(income)} تومان")
                setTextViewText(R.id.widget_expense, "خروجی امروز: ${formatToman(expense)} تومان")
                setTextViewText(
                    R.id.widget_last_scan,
                    lastScan?.let { "آخرین بررسی: ${JalaliDate.formatDateTime(it)}" }
                        ?: "هنوز اسکن نشده است"
                )
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    1000 + widgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
        }

        private fun formatToman(value: Long): String = "%,d".format(value)
    }
}
