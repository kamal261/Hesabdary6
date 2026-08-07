package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.material3.tokens.ButtonDefaults
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
    onAdd: (name: String, kind: CategoryKind, parentId: Long?) -> Unit,
    onDelete: (Category) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    // Build parent -> children map
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }.groupBy { it.parentId!! }
    }

    // Top-level categories (no parent)
    val topLevelCategories = remember(categories) {
        categories.filter { it.parentId == null }
    }

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
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(topLevelCategories, key = { it.id }) { cat ->
                val hasChildren = childrenByParent[cat.id]?.isNotEmpty() == true
                var expanded by remember { mutableStateOf(false) }

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (hasChildren) {
                                        IconButton(onClick = { expanded = !expanded }) {
                                            Icon(
                                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = if (expanded) "بستن" else "باز کردن",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(kindLabel(cat.kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!cat.isDefault) {
                                IconButton(onClick = { onDelete(cat) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف")
                                }
                            }
                        }
                        // Show children inline if expanded
                        if (hasChildren && expanded) {
                            childrenByParent[cat.id]?.forEach { child ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp, 0.dp, 16.dp, 16.dp),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("└ ${child.name}", style = MaterialTheme.typography.titleMedium)
                                            Text(kindLabel(child.kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (!child.isDefault) {
                                            IconButton(onClick = { onDelete(child) }) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف")
                                            }
                                        }
                                    }
                                }
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
            categories = topLevelCategories,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, kind, parentId ->
                onAdd(name, kind, parentId)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryKind, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var parentId by remember { mutableStateOf<Long?>(null) }
    var showParentDropdown by remember { mutableStateOf(false) }

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
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("زیرمجموعه‌ی (اختیاری)", style = MaterialTheme.typography.bodyMedium)
                    // Parent category dropdown using ExposedDropdownMenuBox
                    androidx.compose.material3.ExposedDropdownMenuBox(
                        expanded = showParentDropdown,
                        onExpandedChange = { showParentDropdown = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedName = parentId?.let { pid -> categories.firstOrNull { it.id == pid }?.name } ?: "بدون والد (دسته اصلی)"
                        TextField(
                            value = selectedName,
                            onValueChange = {},
                            label = { Text("دسته والد") },
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showParentDropdown) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = showParentDropdown,
                            onDismissRequest = { showParentDropdown = false }
                        ) {
                            // "None" option
                            DropdownMenuItem(
                                text = { Text("بدون والد (دسته اصلی)") },
                                onClick = {
                                    parentId = null
                                    showParentDropdown = false
                                }
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        parentId = cat.id
                                        showParentDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, kind, parentId) }, enabled = name.isNotBlank()) {
                Text("افزودن")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}