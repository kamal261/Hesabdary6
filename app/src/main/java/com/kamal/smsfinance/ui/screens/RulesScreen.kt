package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.Counterparty
import com.kamal.smsfinance.data.RuleAction
import com.kamal.smsfinance.data.SmartRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    rules: List<SmartRule>,
    categories: List<Category>,
    counterparties: List<Counterparty>,
    onAdd: (pattern: String, categoryId: Long?, counterpartyId: Long?, action: RuleAction) -> Unit,
    onDelete: (SmartRule) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val counterpartyById = remember(counterparties) { counterparties.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قوانین هوشمند") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن قانون")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "هر پیامک بانکی که شامل «عبارت تشخیص» یکی از این قوانین باشد، خودکار به دسته/طرف‌حساب مربوطه اختصاص می‌یابد — یا اگر نوع قانون «نادیده‌گرفتن» باشد، اصلاً ثبت نمی‌شود. این قوانین هیچ مبلغ یا تراکنش موجودی را حذف/تغییر نمی‌دهند.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            if (rules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("هنوز قانونی تعریف نشده است", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("«${rule.pattern}»", style = MaterialTheme.typography.titleMedium)
                                    if (rule.action == RuleAction.IGNORE) {
                                        Text(
                                            "نادیده گرفته می‌شود — اصلاً ثبت نمی‌شود",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        val categoryName = rule.categoryId?.let { categoryById[it]?.name }
                                        val counterpartyName = rule.counterpartyId?.let { counterpartyById[it]?.name }
                                        Text(
                                            listOfNotNull(
                                                categoryName?.let { "دسته: $it" },
                                                counterpartyName?.let { "طرف حساب: $it" }
                                            ).joinToString("  •  ").ifEmpty { "بدون تخصیص" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onDelete(rule) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف قانون")
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            categories = categories,
            counterparties = counterparties,
            onDismiss = { showAddDialog = false },
            onConfirm = { pattern, categoryId, counterpartyId, action ->
                onAdd(pattern, categoryId, counterpartyId, action)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    categories: List<Category>,
    counterparties: List<Counterparty>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Long?, RuleAction) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(RuleAction.CATEGORIZE) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedCounterparty by remember { mutableStateOf<Counterparty?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var counterpartyExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قانون جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("عبارت تشخیص (مثلاً نام فروشگاه)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("این قانون چه کاری انجام دهد؟", style = MaterialTheme.typography.labelLarge)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = action == RuleAction.CATEGORIZE, onClick = { action = RuleAction.CATEGORIZE })
                        Text("خودکار دسته‌بندی کن")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = action == RuleAction.IGNORE, onClick = { action = RuleAction.IGNORE })
                        Text("این پیامک را نادیده بگیر (اصلاً ثبت نشود)")
                    }
                }

                if (action == RuleAction.IGNORE) {
                    Text(
                        "پیامک‌هایی که این عبارت را داشته باشند، از این پس نه به‌عنوان تراکنش و نه در فهرست «نیاز به بررسی» ذخیره نمی‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "بدون دسته",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("دسته‌بندی") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                            DropdownMenuItem(text = { Text("بدون دسته") }, onClick = { selectedCategory = null; categoryExpanded = false })
                            categories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = { selectedCategory = cat; categoryExpanded = false })
                            }
                        }
                    }

                    ExposedDropdownMenuBox(expanded = counterpartyExpanded, onExpandedChange = { counterpartyExpanded = it }) {
                        OutlinedTextField(
                            value = selectedCounterparty?.name ?: "بدون طرف حساب",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("طرف حساب") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = counterpartyExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = counterpartyExpanded, onDismissRequest = { counterpartyExpanded = false }) {
                            DropdownMenuItem(text = { Text("بدون طرف حساب") }, onClick = { selectedCounterparty = null; counterpartyExpanded = false })
                            counterparties.forEach { cp ->
                                DropdownMenuItem(text = { Text(cp.name) }, onClick = { selectedCounterparty = cp; counterpartyExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val (categoryId, counterpartyId) = if (action == RuleAction.IGNORE) {
                        null to null
                    } else {
                        selectedCategory?.id to selectedCounterparty?.id
                    }
                    onConfirm(pattern, categoryId, counterpartyId, action)
                },
                enabled = pattern.isNotBlank()
            ) { Text("افزودن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
