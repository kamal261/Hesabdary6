package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.sms.RawSms
import com.kamal.smsfinance.sms.SmsReaderUtil
import com.kamal.smsfinance.util.JalaliDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows the raw SMS inbox messages from the same sender as the selected
 * transaction, scrolled to and highlighting the exact SMS that produced the
 * transaction. Lets the user read messages before/after it to recall context
 * (who the payment belonged to, what it was for, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsContextScreen(
    sender: String,
    txnTimestamp: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<RawSms>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(sender) {
        loading = true
        messages = withContext(Dispatchers.IO) {
            SmsReaderUtil.readInboxForSender(context, sender)
        }
        loading = false
    }

    val listState = rememberLazyListState()

    // Index of the message whose timestamp matches this transaction (±5 min).
    val targetIndex = remember(messages, txnTimestamp) {
        messages.indexOfFirst { kotlin.math.abs(it.timestamp - txnTimestamp) < 5 * 60 * 1000 }
    }

    LaunchedEffect(targetIndex, loading) {
        if (!loading && targetIndex >= 0) {
            listState.scrollToItem((targetIndex - 2).coerceAtLeast(0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیامک‌های فرستنده") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "پیامکی از این فرستنده در صندوق پیام‌ها یافت نشد\n(ممکن است حذف شده باشد)",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(state = listState, contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(messages, key = { _, m -> m.timestamp }) { index, sms ->
                            val isTarget = index == targetIndex
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth().then(
                                    if (isTarget) Modifier.background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.shapes.medium
                                    ) else Modifier
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        JalaliDate.formatDateTime(sms.timestamp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    if (isTarget) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "تراکنش انتخاب‌شده",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(sms.body, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
