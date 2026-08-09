// SmsFinance file version: 2 — one level of subcategories: categories are grouped by parent
// and rendered indented underneath it; AddCategoryDialog lets you pick a top-level category of
// the same kind as the parent (or none, for a top-level category). Deleting a category with
// children is allowed -- TransactionRepository.deleteCategory re-parents them to top-level
// first, so this screen just shows a heads-up dialog rather than blocking the delete.
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
    onAdd: (name: String, kind: CategoryKind, parentId: Long?) -> Unit,
    onDelete: (Category) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }

    val topLevel = remember(categories) { categories.filter { it.parentId == null } }
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }.groupBy { it.parentId }
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
            items(topLevel, key = { it.id }) { cat ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryRow(
                        category = cat,
                        childCount = childrenByParent[cat.id]?.size ?: 0,
                        onDelete = { pendingDelete = cat }
                    )
                    childrenByParent[cat.id]?.forEach { child ->
                        CategoryRow(
                            category = child,
                            childCount = 0,
                            indented = true,
                            onDelete = { pendingDelete = child }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            topLevelCategories = topLevel,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, kind, parentId ->
                onAdd(name, kind, parentId)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { cat ->
        val childCount = childrenByParent[cat.id]?.size ?: 0
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف «${cat.name}»؟") },
            text = {
                Text(
                    if (childCount > 0)
                        "این دسته $childCount زیرشاخه دارد که پس از حذف، به سطح اصلی منتقل می‌شوند."
                    else
                        "این عمل قابل بازگشت نیست."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(cat); pendingDelete = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    childCount: Int,
    indented: Boolean = false,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(start = if (indented) 24.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    (if (indented) "↳ " else "") + category.name,
                    style = if (indented) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium
                )
                Text(
                    kindLabel(category.kind) + if (childCount > 0) " · $childCount زیرشاخه" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!category.isDefault) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف")
                }
            }
        }
    }
}

private fun kindLabel(kind: CategoryKind): String = when (kind) {
    CategoryKind.INCOME -> "درآمد"
    CategoryKind.EXPENSE -> "هزینه"
    CategoryKind.DEBT_COLLECTION -> "وصول طلب"
    CategoryKind.DEBT_PAYMENT -> "پرداخت بدهی"
}

@Composable
private fun AddCategoryDialog(
    topLevelCategories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryKind, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var parentId by remember { mutableStateOf<Long?>(null) }

    // Only top-level categories of the currently-selected kind can be a parent -- keeps
    // nesting to exactly one level and keeps a subcategory's kind consistent with its parent's.
    val eligibleParents = remember(topLevelCategories, kind) { topLevelCategories.filter { it.kind == kind } }

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
                            RadioButton(selected = kind == k, onClick = { kind = k; parentId = null })
                            Text(kindLabel(k))
                        }
                    }
                }
                if (eligibleParents.isNotEmpty()) {
                    Column {
                        Text("زیرشاخه‌ی کدام دسته؟ (اختیاری)", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = parentId == null, onClick = { parentId = null })
                            Text("بدون والد (دسته مستقل)")
                        }
                        eligibleParents.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = parentId == p.id, onClick = { parentId = p.id })
                                Text(p.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), kind, parentId) },
                enabled = name.isNotBlank()
            ) {
                Text("افزودن")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
