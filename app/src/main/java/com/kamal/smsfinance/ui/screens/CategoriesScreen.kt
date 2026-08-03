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
import com.kamal.smsfinance.data.CategoryKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    categories: List<Category>,
    onAdd: (name: String, kind: CategoryKind) -> Unit,
    onDelete: (Category) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت دسته‌بندی‌ها") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن دسته")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { cat ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(cat.name, style = MaterialTheme.typography.titleMedium)
                            Text(kindLabel(cat.kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!cat.isDefault) {
                            IconButton(onClick = { onDelete(cat) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, kind ->
                onAdd(name, kind)
                showAddDialog = false
            }
        )
    }
}

private fun kindLabel(kind: CategoryKind): String = when (kind) {
    CategoryKind.INCOME -> "درآمد"
    CategoryKind.EXPENSE -> "هزینه"
    CategoryKind.DEBT_COLLECTION -> "وصول طلب"
    CategoryKind.DEBT_PAYMENT -> "پرداخت بدهی"
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, CategoryKind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دسته‌بندی جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام دسته") },
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text("نوع", style = MaterialTheme.typography.bodyMedium)
                    CategoryKind.values().forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = kind == k, onClick = { kind = k })
                            Text(kindLabel(k))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, kind) }, enabled = name.isNotBlank()) {
                Text("افزودن")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
