// SmsFinance file version: 3 — added today-dashboard card, unidentified-SMS review banner, "only uncategorized" filter, switched category picker to the shared CategoryPicker (top-4 + search), detail dialog now shows sender/accountTail
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.SmartSuggestion
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionType
import com.kamal.smsfinance.ui.components.CategoryPicker
import com.kamal.smsfinance.ui.components.TodayDashboardCard
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense
import com.kamal.smsfinance.util.JalaliDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
    categoryUsageCounts: Map<Long, Int>,
    recurringIds: Set<Long>,
    isLoading: Boolean,
    unidentifiedSmsCount: Int,
    uncategorizedCount: Int = 0,
    dashboard: DashboardData,
    smartSuggestions: List<SmartSuggestion> = emptyList(),
    lastScanTimestamp: Long? = null,
    backupReminderVisible: Boolean = false,
    lastBackupTimestamp: Long? = null,
    smsPermissionGranted: Boolean = true,
    onCreateBackup: () -> Unit = {},
    onSnoozeBackupReminder: () -> Unit = {},
    onRequestSmsPermission: () -> Unit = {},
    onAcceptSuggestion: (SmartSuggestion) -> Unit = {},
    onRejectSuggestion: (SmartSuggestion) -> Unit = {},
    onScanInbox: () -> Unit,
    onDelete: (Transaction) -> Unit,
    onAddManual: () -> Unit,
    onAssignCategory: (Transaction, Long?) -> Unit,
    onCreateRule: (pattern: String, categoryId: Long?) -> Unit,
    onOpenUnidentifiedSms: () -> Unit,
    onOpenChecks: () -> Unit,
    onOpenCounterparties: () -> Unit,
    onSaveNotes: (Transaction, String?) -> Unit,
    onViewSmsContext: (sender: String, timestamp: Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var onlyUncategorized by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<Transaction?>(null) }
    var ruleSuggestionFor by remember { mutableStateOf<Pair<Transaction, Long>?>(null) }

    val filtered = remember(transactions, query, onlyUncategorized) {
        transactions
            .filter { !onlyUncategorized || it.categoryId == null }
            .filter { query.isBlank() || it.description.contains(query, true) || it.bankName.contains(query, true) }
    }
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddManual) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن دستی")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    TodayDashboardCard(
                        todayIncome = dashboard.todayIncome,
                        todayExpense = dashboard.todayExpense,
                        estimatedProfitThisMonth = dashboard.estimatedProfitThisMonth,
                        totalOwedToMe = dashboard.totalOwedToMe,
                        totalIOwe = dashboard.totalIOwe,
                        checksDueSoonCount = dashboard.checksDueSoonCount,
                        onOpenChecks = onOpenChecks,
                        onOpenCounterparties = onOpenCounterparties
                    )
                }

                if (backupReminderVisible) {
                    item {
                        com.kamal.smsfinance.ui.components.BackupReminderBanner(
                            lastBackupTimestamp = lastBackupTimestamp,
                            onCreateBackup = onCreateBackup,
                            onSnooze = onSnoozeBackupReminder
                        )
                    }
                }

                if (!smsPermissionGranted) {
                    item {
                        com.kamal.smsfinance.ui.components.SmsPermissionBanner(
                            onRequestPermission = onRequestSmsPermission
                        )
                    }
                }

                item {
                    com.kamal.smsfinance.ui.components.ReviewSummaryCard(
                        uncategorizedCount = uncategorizedCount,
                        unidentifiedSmsCount = unidentifiedSmsCount,
                        suggestionCount = smartSuggestions.size,
                        checksDueSoonCount = dashboard.checksDueSoonCount,
                        onOpenUnidentifiedSms = onOpenUnidentifiedSms,
                        onOpenChecks = onOpenChecks
                    )
                }

                if (unidentifiedSmsCount > 0) {
                    item { UnidentifiedSmsBanner(count = unidentifiedSmsCount, onClick = onOpenUnidentifiedSms) }
                }

                if (smartSuggestions.isNotEmpty()) {
                    item {
                        SmartSuggestionsCard(
                            suggestions = smartSuggestions,
                            onAccept = onAcceptSuggestion,
                            onReject = onRejectSuggestion
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("جستجو در تراکنش‌ها...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onScanInbox,
                            enabled = !isLoading && smsPermissionGranted,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("در حال اسکن...")
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (smsPermissionGranted) "اسکن پیامک‌ها" else "اسکن پیامک‌ها (اجازه لازم است)")
                            }
                        }
                        FilterChip(
                            selected = onlyUncategorized,
                            onClick = { onlyUncategorized = !onlyUncategorized },
                            label = { Text("فقط بدون دسته") }
                        )
                    }
                }
                item {
                    Text(
                        lastScanTimestamp?.let { "آخرین بررسی پیامک‌ها: ${JalaliDate.formatDateTime(it)}" }
                            ?: "هنوز پیامکی بررسی نشده است",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (filtered.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(filtered, key = { it.id }) { txn ->
                        TransactionCard(
                            txn = txn,
                            categoryName = categoryById[txn.categoryId]?.name,
                            isRecurring = txn.id in recurringIds,
                            onDelete = { onDelete(txn) },
                            onClick = { detailFor = txn }
                        )
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    detailFor?.let { txn ->
        TransactionDetailDialog(
            transaction = txn,
            currentCategoryName = categoryById[txn.categoryId]?.name,
            categories = categories,
            categoryUsageCounts = categoryUsageCounts,
            onDismiss = { detailFor = null },
            onSelectCategory = { categoryId ->
                onAssignCategory(txn, categoryId)
                detailFor = null
                if (categoryId != null) ruleSuggestionFor = txn to categoryId
            },
            onSaveNotes = { notes -> onSaveNotes(txn, notes) },
            onViewSmsContext = {
                val sender = txn.smsSender
                if (sender != null) {
                    detailFor = null
                    onViewSmsContext(sender, txn.date)
                }
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
private fun SmartSuggestionsCard(
    suggestions: List<SmartSuggestion>,
    onAccept: (SmartSuggestion) -> Unit,
    onReject: (SmartSuggestion) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("پیشنهادهای هوشمند", style = MaterialTheme.typography.titleMedium)
            Text(
                "این‌ها فقط پیشنهاد هستند؛ تا وقتی شما تأیید نکنید چیزی تغییر نمی‌کند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            suggestions.take(3).forEach { suggestion ->
                HorizontalDivider()
                Text(suggestion.title, fontWeight = FontWeight.SemiBold)
                Text(suggestion.explanation, style = MaterialTheme.typography.bodySmall)
                Text("اطمینان تقریبی: ${(suggestion.confidence * 100).toInt()}٪", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAccept(suggestion) }) { Text("تأیید") }
                    OutlinedButton(onClick = { onReject(suggestion) }) { Text("رد") }
                }
            }
        }
    }
}

/** Everything TodayDashboardCard needs, computed once by the caller from already-loaded state. */
data class DashboardData(
    val todayIncome: Long,
    val todayExpense: Long,
    val estimatedProfitThisMonth: Long,
    val totalOwedToMe: Long,
    val totalIOwe: Long,
    val checksDueSoonCount: Int
)

@Composable
private fun UnidentifiedSmsBanner(count: Int, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.HelpOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("$count پیامک شناسایی‌نشده — بررسی کنید", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
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
 * the full, untruncated SMS text, sender, and account tail) and assigning
 * its category in the same place -- so when triaging many similar-looking
 * SMS in a row, the user always sees exactly which transaction they're about
 * to categorize before picking, instead of guessing from a truncated snippet.
 */
@Composable
private fun TransactionDetailDialog(
    transaction: Transaction,
    currentCategoryName: String?,
    categories: List<Category>,
    categoryUsageCounts: Map<Long, Int>,
    onDismiss: () -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onSaveNotes: (String?) -> Unit,
    onViewSmsContext: () -> Unit
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val amountColor = if (isIncome) GreenIncome else RedExpense
    val dateStr = remember(transaction.date) {
        JalaliDate.formatDateTime(transaction.date)
    }
    val fullText = transaction.rawSms?.takeIf { it.isNotBlank() } ?: transaction.description
    val clipboard = LocalClipboardManager.current
    var notesText by remember(transaction.id) { mutableStateOf(transaction.notes ?: "") }

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

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("متن کامل پیامک", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // Lets the user share the raw SMS text with someone else (e.g. a colleague)
                    // to jog their memory about which transaction this was -- confirmed real
                    // need: "گاهی لازمه کپی پیامک رو برای کسی ارسال کنیم".
                    IconButton(onClick = { clipboard.setText(AnnotatedString(fullText)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "کپی متن پیامک")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(fullText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                }

                if (transaction.smsSender != null || transaction.accountTail != null) {
                    Spacer(Modifier.height(8.dp))
                    transaction.smsSender?.let {
                        Text("فرستنده: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    transaction.accountTail?.let {
                        Text("چهار رقم آخر حساب: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (transaction.smsSender != null) {
                    Spacer(Modifier.height(8.dp))
                    // Shows the raw inbox around this SMS -- was already fully built
                    // (SmsContextScreen) but never wired into navigation until now.
                    OutlinedButton(onClick = onViewSmsContext, modifier = Modifier.fillMaxWidth()) {
                        Text("مشاهده پیامک‌های قبل و بعد از این فرستنده")
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("یادداشت", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    placeholder = { Text("مثلاً: علی ۱۲ جفت کفش هم آورده، از حسابش کم کنم") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (notesText != (transaction.notes ?: "")) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onSaveNotes(notesText) }, modifier = Modifier.align(Alignment.End)) {
                        Text("ذخیره یادداشت")
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("دسته‌بندی: ${currentCategoryName ?: "بدون دسته"}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                CategoryPicker(
                    categories = categories,
                    usageCounts = categoryUsageCounts,
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

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "هنوز تراکنشی ثبت نشده است.\nروی «اسکن پیامک‌ها» بزنید یا یک تراکنش دستی اضافه کنید.",
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
        JalaliDate.formatDateTime(txn.date)
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
                    if (txn.transferGroupId != null) {
                        Spacer(Modifier.width(6.dp))
                        AssistChip(onClick = {}, label = { Text("انتقال داخلی", style = MaterialTheme.typography.labelLarge) })
                    }
                    if (txn.linkedCheckId != null) {
                        Spacer(Modifier.width(6.dp))
                        AssistChip(onClick = {}, label = { Text("مرتبط با چک", style = MaterialTheme.typography.labelLarge) })
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
