package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.CategoryTree
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionType
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense
import com.kamal.smsfinance.util.JalaliDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDrilldownScreen(
    title: String,
    transactions: List<Transaction>,
    categories: List<Category>,
    onBack: () -> Unit
) {
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (transactions.isEmpty()) {
                item {
                    Text(
                        "تراکنشی برای این مورد یافت نشد",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(transactions, key = { it.id }) { txn ->
                    val isIncome = txn.type == TransactionType.INCOME
                    val color = if (isIncome) GreenIncome else RedExpense
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().clickable { selectedTransaction = txn }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                contentDescription = if (isIncome) "درآمد" else "هزینه",
                                tint = color
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${"%,d".format(txn.amountToman)} تومان",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                Text(
                                    txn.description.ifBlank { "بدون توضیح" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                Text(
                                    "${txn.bankName} · ${JalaliDate.formatDateTime(txn.date)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    "دسته: ${CategoryTree.pathOf(txn.categoryId, categories)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!txn.notes.isNullOrBlank()) {
                                    Text(
                                        "یادداشت دارد",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            Text("مشاهده", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }

    selectedTransaction?.let { txn ->
        SmsTextDialog(transaction = txn, onDismiss = { selectedTransaction = null })
    }
}

@Composable
private fun SmsTextDialog(transaction: Transaction, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val fullText = transaction.rawSms?.takeIf { it.isNotBlank() } ?: transaction.description
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("متن کامل پیامک") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "${"%,d".format(transaction.amountToman)} تومان · ${transaction.bankName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("برای ارسال یا نگهداری، متن را کپی کنید", modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(fullText)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "کپی متن پیامک")
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(fullText, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                if (!transaction.notes.isNullOrBlank()) {
                    Text("یادداشت: ${transaction.notes}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
