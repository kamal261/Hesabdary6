package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class CheckFilter(val label: String) { ALL("همه"), PENDING("در انتظار"), CLEARED("تسویه شده"), BOUNCED("برگشتی") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecksScreen(
    checks: List<Check>,
    dueSoon: List<Check>,
    counterparties: List<Counterparty>,
    onAdd: (Check) -> Unit,
    onSettle: (Check) -> Unit,
    onMarkBounced: (Check) -> Unit,
    onDelete: (Check) -> Unit
) {
    var filter by remember { mutableStateOf(CheckFilter.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    val counterpartyById = remember(counterparties) { counterparties.associateBy { it.id } }

    val filtered = remember(checks, filter) {
        when (filter) {
            CheckFilter.ALL -> checks
            CheckFilter.PENDING -> checks.filter { it.status == CheckStatus.PENDING }
            CheckFilter.CLEARED -> checks.filter { it.status == CheckStatus.CLEARED }
            CheckFilter.BOUNCED -> checks.filter { it.status == CheckStatus.BOUNCED }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن چک")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (dueSoon.isNotEmpty()) {
                DueSoonBanner(dueSoon, counterpartyById)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CheckFilter.values().forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("چکی در این دسته یافت نشد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { chk ->
                        CheckCard(
                            check = chk,
                            counterpartyName = chk.counterpartyId?.let { counterpartyById[it]?.name },
                            onSettle = { onSettle(chk) },
                            onBounced = { onMarkBounced(chk) },
                            onDelete = { onDelete(chk) }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCheckDialog(
            counterparties = counterparties,
            onDismiss = { showAddDialog = false },
            onConfirm = { check -> onAdd(check); showAddDialog = false }
        )
    }
}

@Composable
private fun DueSoonBanner(dueSoon: List<Check>, counterpartyById: Map<Long, Counterparty>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    "یادآوری: ${dueSoon.size} چک نزدیک سررسید است",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            dueSoon.take(3).forEach { chk ->
                val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(chk.dueDate))
                val name = chk.counterpartyId?.let { counterpartyById[it]?.name } ?: "بدون طرف حساب"
                Text(
                    "$name — ${"%,d".format(chk.amountToman)} تومان — سررسید $dateStr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun CheckCard(
    check: Check,
    counterpartyName: String?,
    onSettle: () -> Unit,
    onBounced: () -> Unit,
    onDelete: () -> Unit
) {
    val typeLabel = if (check.type == CheckType.RECEIVABLE) "چک دریافتی" else "چک پرداختی"
    val statusLabel = when (check.status) {
        CheckStatus.PENDING -> "در انتظار"
        CheckStatus.CLEARED -> "تسویه شده"
        CheckStatus.BOUNCED -> "برگشتی"
    }
    val dueDateStr = remember(check.dueDate) { SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(check.dueDate)) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(typeLabel, style = MaterialTheme.typography.titleMedium)
                    counterpartyName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text("${"%,d".format(check.amountToman)} ت", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text("سررسید: $dueDateStr", style = MaterialTheme.typography.bodyMedium)
            Text("وضعیت: $statusLabel", style = MaterialTheme.typography.bodyMedium)
            check.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (check.status == CheckStatus.PENDING) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSettle, modifier = Modifier.weight(1f)) { Text("تسویه شد") }
                    OutlinedButton(onClick = onBounced, modifier = Modifier.weight(1f)) { Text("برگشت خورد") }
                }
            }
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text("حذف", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCheckDialog(
    counterparties: List<Counterparty>,
    onDismiss: () -> Unit,
    onConfirm: (Check) -> Unit
) {
    var type by remember { mutableStateOf(CheckType.RECEIVABLE) }
    var amountText by remember { mutableStateOf("") }
    var daysUntilDue by remember { mutableStateOf("30") }
    var reminderDays by remember { mutableStateOf("3") }
    var description by remember { mutableStateOf("") }
    var selectedCounterparty by remember { mutableStateOf<Counterparty?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val amountValid = amountText.toLongOrNull()?.let { it > 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن چک") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == CheckType.RECEIVABLE, onClick = { type = CheckType.RECEIVABLE }, label = { Text("دریافتی") })
                    FilterChip(selected = type == CheckType.PAYABLE, onClick = { type = CheckType.PAYABLE }, label = { Text("پرداختی") })
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text("مبلغ (تومان)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountText.isNotEmpty() && !amountValid,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = daysUntilDue,
                    onValueChange = { if (it.all { c -> c.isDigit() }) daysUntilDue = it },
                    label = { Text("روز تا سررسید") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reminderDays,
                    onValueChange = { if (it.all { c -> c.isDigit() }) reminderDays = it },
                    label = { Text("یادآوری چند روز قبل از سررسید") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedCounterparty?.name ?: "بدون طرف حساب",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("طرف حساب") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("بدون طرف حساب") }, onClick = { selectedCounterparty = null; expanded = false })
                        counterparties.forEach { cp ->
                            DropdownMenuItem(text = { Text(cp.name) }, onClick = { selectedCounterparty = cp; expanded = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountValid,
                onClick = {
                    val days = daysUntilDue.toIntOrNull() ?: 30
                    val dueDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
                    onConfirm(
                        Check(
                            type = type,
                            counterpartyId = selectedCounterparty?.id,
                            amountToman = amountText.toLong(),
                            dueDate = dueDate,
                            reminderDays = reminderDays.toIntOrNull() ?: 3,
                            description = description.ifBlank { null }
                        )
                    )
                }
            ) { Text("افزودن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
