// SmsFinance file version: 1
package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Counterparty
import com.kamal.smsfinance.data.CounterpartyType
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionType
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense
import com.kamal.smsfinance.util.JalaliDate
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterpartyProfileScreen(
    counterparty: Counterparty,
    transactions: List<Transaction>,
    balance: Long,
    totalVolume: Long,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSaveNotes: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var notesText by remember(counterparty.id) { mutableStateOf(counterparty.notes ?: "") }
    val notesDirty = notesText != (counterparty.notes ?: "")
    val typeLabel = if (counterparty.type == CounterpartyType.CUSTOMER) "مشتری" else "عامل / کارگر"
    val balanceColor = if (balance >= 0) GreenIncome else RedExpense
    val balanceLabel = if (balance >= 0) "طلب شما از او" else "بدهی شما به او"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(counterparty.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } },
                actions = {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(typeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        counterparty.phone?.let { Text("تلفن: $it", style = MaterialTheme.typography.bodyMedium) }
                        counterparty.address?.let { Text("آدرس: $it", style = MaterialTheme.typography.bodyMedium) }
                        counterparty.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(balanceLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${"%,d".format(kotlin.math.abs(balance))} ت",
                                style = MaterialTheme.typography.titleLarge,
                                color = balanceColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("حجم کل تراکنش‌ها", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${"%,d".format(totalVolume)} ت",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item { Text("تراکنش‌های مرتبط", style = MaterialTheme.typography.titleLarge) }

            if (transactions.isEmpty()) {
                item {
                    Text("هنوز تراکنشی برای این طرف حساب ثبت نشده", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(transactions, key = { it.id }) { txn ->
                    CounterpartyTransactionRow(txn)
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("یادداشت", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "برای اطلاعات موقت مثل تعداد سفارش، تاریخ تحویل، یا وضعیت کار",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        if (notesDirty) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { onSaveNotes(notesText) }, modifier = Modifier.align(Alignment.End)) {
                                Text("ذخیره یادداشت")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف این طرف حساب؟") },
            text = { Text("تراکنش‌های مرتبط حذف نمی‌شوند اما دیگر به این طرف حساب متصل نخواهند بود.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun CounterpartyTransactionRow(txn: Transaction) {
    val isIncome = txn.type == TransactionType.INCOME
    val color = if (isIncome) GreenIncome else RedExpense
    val dateStr = remember(txn.date) { JalaliDate.formatDate(txn.date) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(txn.description, style = MaterialTheme.typography.bodyLarge)
                Text(dateStr, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            }
            Text("${"%,d".format(txn.amountToman)} ت", color = color, fontWeight = FontWeight.Bold)
        }
    }
}
