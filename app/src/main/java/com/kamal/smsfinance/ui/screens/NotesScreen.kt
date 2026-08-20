package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
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
fun NotesScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
    onBack: () -> Unit,
    onSaveNote: (Transaction, String?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Transaction?>(null) }

    val notes = remember(transactions, query, categories) {
        transactions
            .filter { !it.notes.isNullOrBlank() }
            .filter { txn ->
                query.isBlank() ||
                    txn.notes.orEmpty().contains(query, ignoreCase = true) ||
                    txn.description.contains(query, ignoreCase = true) ||
                    txn.bankName.contains(query, ignoreCase = true) ||
                    CategoryTree.pathOf(txn.categoryId, categories).contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("همه یادداشت‌ها") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("جستجو در یادداشت‌ها، دسته یا شرح") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            Text(
                "${notes.size} یادداشت",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (notes.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "هنوز یادداشتی برای تراکنش‌ها ثبت نشده است." else "یادداشتی با این جستجو پیدا نشد.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    items(notes, key = { it.id }) { txn ->
                        NoteCard(
                            transaction = txn,
                            categoryPath = CategoryTree.pathOf(txn.categoryId, categories),
                            onClick = { selected = txn }
                        )
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    selected?.let { txn ->
        NoteDetailDialog(
            transaction = txn,
            categoryPath = CategoryTree.pathOf(txn.categoryId, categories),
            onDismiss = { selected = null },
            onSave = { updatedNotes ->
                onSaveNote(txn, updatedNotes)
                selected = null
            }
        )
    }
}

@Composable
private fun NoteCard(
    transaction: Transaction,
    categoryPath: String,
    onClick: () -> Unit
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val color = if (isIncome) GreenIncome else RedExpense
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(transaction.bankName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${"%,d".format(transaction.amountToman)} ت",
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                transaction.notes.orEmpty(),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "دسته فعلی: $categoryPath",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${transaction.description.ifBlank { "بدون شرح" }} · ${JalaliDate.formatDateTime(transaction.date)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteDetailDialog(
    transaction: Transaction,
    categoryPath: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var draft by remember(transaction.id, transaction.notes) { mutableStateOf(transaction.notes.orEmpty()) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("یادداشت تراکنش") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("دسته فعلی: $categoryPath", color = MaterialTheme.colorScheme.primary)
                Text("${transaction.bankName} · ${JalaliDate.formatDateTime(transaction.date)}")
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("یادداشت") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("برای کپی یادداشت روی دکمه بزنید", modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(draft)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "کپی یادداشت")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.takeIf { it.isNotBlank() }) }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
