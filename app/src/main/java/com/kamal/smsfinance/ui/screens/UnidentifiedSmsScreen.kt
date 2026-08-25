// SmsFinance file version: 1
package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.UnidentifiedSms
import com.kamal.smsfinance.util.JalaliDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnidentifiedSmsScreen(
    items: List<UnidentifiedSms>,
    onDismiss: (UnidentifiedSms) -> Unit,
    onDismissAll: () -> Unit,
    onExport: () -> Unit,
    onIgnoreSimilar: (UnidentifiedSms, pattern: String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیامک‌های شناسایی‌نشده") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } },
                actions = {
                    if (items.isNotEmpty()) {
                        // Exports sender + full raw text of every item below, for spotting real
                        // patterns across many messages at once instead of one screenshot at a
                        // time (CSV, UTF-8 BOM for Excel; see UnidentifiedSmsExporter).
                        TextButton(onClick = onExport) { Text("خروجی برای بررسی") }
                        TextButton(onClick = onDismissAll) { Text("پاک کردن همه") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "این پیامک‌ها شبیه پیامک بانکی به نظر می‌رسیدند ولی برنامه نتوانست به‌طور کامل آن‌ها را " +
                    "تشخیص دهد. متن کامل هرکدام را ببینید و در صورت نیاز از «افزودن دستی» برای ثبت آن استفاده کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("موردی برای بررسی نیست", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        UnidentifiedSmsCard(
                            item,
                            onDismiss = { onDismiss(item) },
                            onIgnoreSimilar = { pattern -> onIgnoreSimilar(item, pattern) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun UnidentifiedSmsCard(item: UnidentifiedSms, onDismiss: () -> Unit, onIgnoreSimilar: (String) -> Unit) {
    val dateStr = remember(item.timestamp) {
        JalaliDate.formatDateTime(item.timestamp)
    }
    var showIgnorePatternDialog by remember(item.id) { mutableStateOf(false) }

    if (showIgnorePatternDialog) {
        IgnorePatternDialog(
            initialPattern = item.sender,
            onConfirm = { pattern ->
                onIgnoreSimilar(pattern)
                showIgnorePatternDialog = false
            },
            onDismiss = { showIgnorePatternDialog = false }
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.sender, style = MaterialTheme.typography.titleMedium)
            Text(dateStr, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                Text(item.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Distinct from the one-time dismiss below: this creates a lasting rule so
                // every future SMS matching the pattern is dropped, not just this one instance.
                TextButton(onClick = { showIgnorePatternDialog = true }) {
                    Text("همیشه نادیده بگیر")
                }
                TextButton(onClick = onDismiss) {
                    Text("فقط این یکی")
                }
            }
        }
    }
}

@Composable
private fun IgnorePatternDialog(
    initialPattern: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pattern by remember { mutableStateOf(initialPattern) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("همیشه نادیده گرفته شود") },
        text = {
            Column {
                Text(
                    "هر پیامک بعدی که شامل عبارت زیر باشد، دیگر نه ثبت می‌شود و نه در این فهرست ظاهر می‌شود. " +
                        "می‌توانید عبارت را ویرایش کنید تا دقیق‌تر یا کلی‌تر شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("عبارت تشخیص") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pattern) }, enabled = pattern.isNotBlank()) { Text("همیشه نادیده بگیر") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
