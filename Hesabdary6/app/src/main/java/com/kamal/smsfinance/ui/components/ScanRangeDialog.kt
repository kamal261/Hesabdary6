// SmsFinance file version: 1 — used both for the first-run onboarding prompt (right after
// READ_SMS is granted) and for Settings > "اسکن مجدد پیامک‌ها" (available any time afterward,
// not a one-time choice -- addresses "عدم تعیین مدت زمان برای اسکن پیامک‌ها").
package com.kamal.smsfinance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

data class ScanRangeOption(val label: String, val days: Int?)

val SCAN_RANGE_OPTIONS = listOf(
    ScanRangeOption("۳۰ روز گذشته", 30),
    ScanRangeOption("۹۰ روز گذشته", 90),
    ScanRangeOption("یک سال گذشته", 365),
    ScanRangeOption("کل تاریخچه پیامک‌ها", null)
)

/**
 * First-run: shown once, right after READ_SMS is first granted. Reading the entire SMS inbox
 * by default (years of history on most phones) is slow and mostly irrelevant to "چقدر پول
 * امروز/این ماه اومده و رفته" -- letting the user pick a range up front keeps the first scan
 * fast and relevant, without permanently losing older messages (کل تاریخچه is always an option,
 * and nothing on-device is deleted regardless of choice).
 *
 * @param dismissible false for the first-run prompt (must choose something), true when opened
 * from Settings for a repeat scan (can be cancelled without doing anything).
 */
@Composable
fun ScanRangeDialog(
    dismissible: Boolean = false,
    onDismiss: () -> Unit = {},
    onChoose: (days: Int?) -> Unit
) {
    Dialog(onDismissRequest = { if (dismissible) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("بررسی پیامک‌های بانکی", style = MaterialTheme.typography.titleLarge)
                Text(
                    "چند وقت گذشته بررسی بشه؟ هر وقت بخوای بعداً هم می‌تونی از تنظیمات، بازه‌ی دیگه‌ای رو اسکن کنی.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                SCAN_RANGE_OPTIONS.forEach { option ->
                    Button(
                        onClick = { onChoose(option.days) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(option.label)
                    }
                }
                if (dismissible) {
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("انصراف")
                    }
                }
            }
        }
    }
}
