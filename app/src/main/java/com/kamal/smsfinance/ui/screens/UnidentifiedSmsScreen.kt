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
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnidentifiedSmsScreen(
    items: List<UnidentifiedSms>,
    onDismiss: (UnidentifiedSms) -> Unit,
    onDismissAll: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیامک‌های شناسایی‌نشده") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } },
                actions = {
                    if (items.isNotEmpty()) {
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
                        UnidentifiedSmsCard(item, onDismiss = { onDismiss(item) })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun UnidentifiedSmsCard(item: UnidentifiedSms, onDismiss: () -> Unit) {
    val dateStr = remember(item.timestamp) {
        JalaliDate.formatDateTime(item.timestamp)
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
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("نادیده بگیر")
            }
        }
    }
}
