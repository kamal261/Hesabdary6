package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Counterparty
import com.kamal.smsfinance.data.CounterpartyType
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterpartiesScreen(
    counterparties: List<Counterparty>,
    balanceOf: (Long) -> Long,
    onAdd: (name: String, type: CounterpartyType, phone: String?, address: String?, description: String?) -> Unit,
    onOpenProfile: (Counterparty) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf<CounterpartyType?>(null) }

    val filtered = remember(counterparties, filterType) {
        if (filterType == null) counterparties else counterparties.filter { it.type == filterType }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن طرف حساب")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text("همه") })
                FilterChip(selected = filterType == CounterpartyType.CUSTOMER, onClick = { filterType = CounterpartyType.CUSTOMER }, label = { Text("مشتری‌ها") })
                FilterChip(selected = filterType == CounterpartyType.WORKER, onClick = { filterType = CounterpartyType.WORKER }, label = { Text("عامل‌ها") })
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "هنوز طرف حسابی اضافه نشده است",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { cp ->
                        val balance = balanceOf(cp.id)
                        CounterpartyCard(cp, balance, onClick = { onOpenProfile(cp) })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCounterpartyDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, phone, address, desc ->
                onAdd(name, type, phone, address, desc)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun CounterpartyCard(cp: Counterparty, balance: Long, onClick: () -> Unit) {
    val typeLabel = if (cp.type == CounterpartyType.CUSTOMER) "مشتری" else "عامل / کارگر"
    val balanceColor = if (balance >= 0) GreenIncome else RedExpense

    ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(cp.name, style = MaterialTheme.typography.titleMedium)
                Text(typeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${"%,d".format(kotlin.math.abs(balance))} ت",
                    color = balanceColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (balance >= 0) "طلب از او" else "بدهی به او",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddCounterpartyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, CounterpartyType, String?, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CounterpartyType.CUSTOMER) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("طرف حساب جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == CounterpartyType.CUSTOMER, onClick = { type = CounterpartyType.CUSTOMER }, label = { Text("مشتری") })
                    FilterChip(selected = type == CounterpartyType.WORKER, onClick = { type = CounterpartyType.WORKER }, label = { Text("عامل / کارگر") })
                }
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("تلفن (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("آدرس (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, type, phone.ifBlank { null }, address.ifBlank { null }, description.ifBlank { null })
                },
                enabled = name.isNotBlank()
            ) { Text("افزودن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
