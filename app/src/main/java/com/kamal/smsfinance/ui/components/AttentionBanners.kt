package com.kamal.smsfinance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.util.JalaliDate

@Composable
fun BackupReminderBanner(
    lastBackupTimestamp: Long?,
    onCreateBackup: () -> Unit,
    onSnooze: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("از اطلاعاتتان پشتیبان بگیرید", style = MaterialTheme.typography.titleMedium)
            Text(
                lastBackupTimestamp?.let {
                    "آخرین پشتیبان‌گیری: ${JalaliDate.formatDateTime(it)}"
                } ?: "هنوز نسخه پشتیبان نگرفته‌اید.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "پشتیبان روی گوشی ساخته می‌شود و می‌توانید آن را در جای امن نگه دارید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreateBackup, modifier = Modifier.weight(1f)) {
                    Text("پشتیبان‌گیری")
                }
                TextButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                    Text("بعداً")
                }
            }
        }
    }
}

@Composable
fun SmsPermissionBanner(onRequestPermission: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("اسکن خودکار پیامک فعال نیست", style = MaterialTheme.typography.titleSmall)
            Text(
                "می‌توانید تراکنش‌ها را دستی ثبت کنید. برای اسکن خودکار، اجازه خواندن پیامک را فعال کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onRequestPermission) {
                Text("اجازه خواندن پیامک")
            }
        }
    }
}

@Composable
fun ReviewSummaryCard(
    uncategorizedCount: Int,
    unidentifiedSmsCount: Int,
    suggestionCount: Int,
    checksDueSoonCount: Int,
    notesCount: Int = 0,
    onOpenUnidentifiedSms: () -> Unit,
    onOpenChecks: () -> Unit,
    onOpenNotes: () -> Unit = {}
) {
    val total = uncategorizedCount + unidentifiedSmsCount + suggestionCount + checksDueSoonCount + notesCount
    if (total == 0) return
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("مواردی که باید بررسی شوند", style = MaterialTheme.typography.titleMedium)
            Text(
                "برنامه تصمیم مالی را به‌جای شما نمی‌گیرد؛ این موارد منتظر بررسی شما هستند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uncategorizedCount > 0) {
                Text("$uncategorizedCount تراکنش بدون دسته", style = MaterialTheme.typography.bodyMedium)
            }
            if (unidentifiedSmsCount > 0) {
                TextButton(onClick = onOpenUnidentifiedSms) {
                    Text("پیامک‌های ناشناس ($unidentifiedSmsCount)")
                }
            }
            if (suggestionCount > 0) {
                Text("$suggestionCount پیشنهاد هوشمند پایین همین صفحه منتظر بررسی است", style = MaterialTheme.typography.bodyMedium)
            }
            if (checksDueSoonCount > 0) {
                TextButton(onClick = onOpenChecks) {
                    Text("چک‌های نزدیک سررسید ($checksDueSoonCount)")
                }
            }
            if (notesCount > 0) {
                TextButton(onClick = onOpenNotes) {
                    Text("همه یادداشت‌ها ($notesCount)")
                }
            }
        }
    }
}
