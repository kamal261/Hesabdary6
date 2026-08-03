package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionType
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
    recurringIds: Set<Long>,
    isLoading: Boolean,
    onScanInbox: () -> Unit,
    onDelete: (Transaction) -> Unit,
    onAddManual: () -> Unit,
    onAssignCategory: (Transaction, Long?) -> Unit,
    onCreateRule: (pattern: String, categoryId: Long?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var detailFor by remember { mutableStateOf<Transaction?>(null) }
    var ruleSuggestionFor by remember { mutableStateOf<Pair<Transaction, Long>?>(null) }

    val filtered = remember(transactions, query) {
        if (query.isBlank()) transactions
        else transactions.filter {
            it.description.contains(query, true) || it.bankName.contains(query, true)
        }
    }
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddManual) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن دستی")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("جستجو در تراکنش‌ها...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            OutlinedButton(
                onClick = onScanInbox,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("در حال اسکن...")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("اسکن مجدد پیامک‌های بانکی")
                }
            }

            Text(
                "برای دیدن متن کامل پیامک و دسته‌بندی، روی هر تراکنش بزنید.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (filtered.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { txn ->
                        TransactionCard(
                            txn = txn,
                            categoryName = categoryById[txn.categoryId]?.name,
                            isRecurring = txn.id in recurringIds,
                            onDelete = { onDelete(txn) },
                            onClick = { detailFor = txn }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    detailFor?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            currentCategoryName = categoryById[txn.categoryId]?.name,
            categories = categories,
            onDismiss = { detailFor = null },
            onSelectCategory = { categoryId ->
                onAssignCategory(txn, categoryId)
                detailFor = null
                if (categoryId != null) ruleSuggestionFor = txn to categoryId
            }
        )
    }

    ruleSuggestionFor?.let { (txn, categoryId) ->
        val categoryName = categories.firstOrNull { it.id == categoryId }?.name.orEmpty()
        RuleSuggestionDialog(
            defaultPattern = txn.description.ifBlank { txn.bankName },
            categoryName = categoryName,
            onSkip = { ruleSuggestionFor = null },
            onSave = { pattern ->
                onCreateRule(pattern, categoryId)
                ruleSuggestionFor = null
            }
        )
    }
}

@Composable
private fun RuleSuggestionDialog(
    defaultPattern: String,
    categoryName: String,
    onSkip: () -> Unit,
    onSave: (String) -> Unit
) {
    var pattern by remember { mutableStateOf(defaultPattern) }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("تبدیل به قانون؟") },
        text = {
            Column {
                Text(
                    "پیامک‌های مشابه بعدی که شامل این عبارت باشند، خودکار در دسته «$categoryName» قرار می‌گیرند.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("عبارت تشخیص") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (pattern.isNotBlank()) onSave(pattern) }, enabled = pattern.isNotBlank()) {
                Text("ذخیره قانون")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("فقط همین یکی") }
        }
    )
}

/**
 * One combined dialog for viewing everything about a transaction (including
 * the full, untruncated SMS text) and assigning its category in the same
 * place -- so when triaging many similar-looking SMS in a row, the user
 * always sees exactly which transaction (amount/bank/date/full text) they're
 * about to categorize before picking, instead of guessing from a truncated
 * two-line snippet.
 */
@Composable
private fun TransactionDetailDialog(
    transaction: Transaction,
    currentCategoryName: String?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSelectCategory: (Long?) -> Unit
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val amountColor = if (isIncome) GreenIncome else RedExpense
    val dateStr = remember(transaction.date) {
        SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", Locale.US).format(Date(transaction.date))
    }
    val fullText = transaction.rawSms?.takeIf { it.isNotBlank() } ?: transaction.description

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = amountColor
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(transaction.bankName, style = MaterialTheme.typography.titleMedium)
                    Text(dateStr, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "${"%,d".format(transaction.amountToman)} تومان",
                    style = MaterialTheme.typography.headlineSmall,
                    color = amountColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                Text("متن کامل پیامک", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        fullText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("دسته‌بندی: ${currentCategoryName ?: "بدون دسته"}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "برای تغییر یا انتخاب دسته، روی یکی از گزینه‌های زیر بزنید:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                FlowRowCategories(
                    categories = categories,
                    selectedId = transaction.categoryId,
                    onSelect = onSelectCategory
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

/** Simple wrapping row of category choice chips (no extra library needed for FlowRow). */
@Composable
private fun FlowRowCategories(categories: List<Category>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("بدون دسته") }
            )
        }
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { cat ->
                    FilterChip(
                        selected = selectedId == cat.id,
                        onClick = { onSelect(cat.id) },
                        label = { Text(cat.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "هنوز تراکنشی ثبت نشده است.\nروی «اسکن مجدد پیامک‌های بانکی» بزنید یا یک تراکنش دستی اضافه کنید.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionCard(
    txn: Transaction,
    categoryName: String?,
    isRecurring: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val isIncome = txn.type == TransactionType.INCOME
    val amountColor = if (isIncome) GreenIncome else RedExpense
    val dateStr = remember(txn.date) {
        SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.US).format(Date(txn.date))
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = null,
                tint = amountColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(txn.bankName, style = MaterialTheme.typography.titleMedium)
                Text(
                    txn.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateStr, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                    if (isRecurring) {
                        Spacer(Modifier.width(6.dp))
                        AssistChip(onClick = {}, label = { Text("تکراری", style = MaterialTheme.typography.labelLarge) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(categoryName ?: "بدون دسته", style = MaterialTheme.typography.labelLarge) }
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${"%,d".format(txn.amountToman)} ت",
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
