package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Counterparty
import com.kamal.smsfinance.data.CounterpartyReminder
import com.kamal.smsfinance.data.CounterpartyType
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.data.TransactionType
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense
import com.kamal.smsfinance.util.JalaliDate
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterpartyProfileScreen(
    counterparty: Counterparty,
    transactions: List<Transaction>,
    balance: Long,
    totalVolume: Long,
    reminders: List<CounterpartyReminder> = emptyList(),
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSaveNotes: (String) -> Unit,
    onAddReminder: (CounterpartyReminder) -> Unit = {},
    onToggleReminder: (CounterpartyReminder, Boolean) -> Unit = { _, _ -> },
    onDeleteReminder: (CounterpartyReminder) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReminderForm by remember { mutableStateOf(false) }
    var notesText by remember(counterparty.id) { mutableStateOf(counterparty.notes ?: "") }
    val notesDirty = notesText != (counterparty.notes ?: "")
    val typeLabel = if (counterparty.type == CounterpartyType.CUSTOMER) "مشتری" else "عامل / کارگر"
    val balanceColor = if (balance >= 0) GreenIncome else RedExpense
    val balanceLabel = if (balance >= 0) "طلب شما از او" else "بدهی شما به او"
    val nextAction = when {
        reminders.any { !it.isDone } -> "قدم بعدی: یادآوری باز را پیگیری کنید"
        balance > 0L -> "قدم بعدی: پیگیری طلب از این طرف‌حساب"
        balance < 0L -> "قدم بعدی: ثبت پرداخت یا یادداشت بدهی"
        else -> "قدم بعدی: در صورت نیاز، یادداشت یا یادآوری اضافه کنید"
    }

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

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("قدم بعدی", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            nextAction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                ReminderSection(
                    reminders = reminders,
                    onAdd = { showReminderForm = true },
                    onToggle = onToggleReminder,
                    onDelete = onDeleteReminder
                )
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
                        Text("یادداشت طرف‌حساب", style = MaterialTheme.typography.titleMedium)
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

    if (showReminderForm) {
        AddReminderDialog(
            counterpartyId = counterparty.id,
            onDismiss = { showReminderForm = false },
            onSave = { reminder ->
                onAddReminder(reminder)
                showReminderForm = false
            }
        )
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
private fun ReminderSection(
    reminders: List<CounterpartyReminder>,
    onAdd: () -> Unit,
    onToggle: (CounterpartyReminder, Boolean) -> Unit,
    onDelete: (CounterpartyReminder) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("یادآوری‌ها", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) { Text("یادآوری جدید") }
            }
            if (reminders.isEmpty()) {
                Text("برای تماس، دریافت طلب یا پیگیری کار چیزی ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                reminders.forEach { reminder ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = reminder.isDone,
                            onCheckedChange = { onToggle(reminder, it) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(reminder.title, fontWeight = FontWeight.SemiBold)
                            reminder.details?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            reminder.dueAt?.let {
                                Text("موعد: ${JalaliDate.formatDateFriendly(it)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TextButton(onClick = { onDelete(reminder) }) { Text("حذف") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    counterpartyId: Long,
    onDismiss: () -> Unit,
    onSave: (CounterpartyReminder) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("یادآوری برای طرف‌حساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("موضوع") }, singleLine = true)
                OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("توضیح کوتاه") }, minLines = 2)
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedDate?.let { "تاریخ: ${JalaliDate.formatDateFriendly(it)}" } ?: "انتخاب تاریخ یادآوری")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        CounterpartyReminder(
                            counterpartyId = counterpartyId,
                            title = title.trim(),
                            details = details.trim().takeIf { it.isNotBlank() },
                            dueAt = selectedDate
                        )
                    )
                }
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { selectedDate = datePickerState.selectedDateMillis; showDatePicker = false }) { Text("انتخاب") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("انصراف") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun CounterpartyTransactionRow(txn: Transaction) {
    val isIncome = txn.type == TransactionType.INCOME
    val color = if (isIncome) GreenIncome else RedExpense
    val dateStr = remember(txn.date) { JalaliDate.formatDateTime(txn.date) }

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
