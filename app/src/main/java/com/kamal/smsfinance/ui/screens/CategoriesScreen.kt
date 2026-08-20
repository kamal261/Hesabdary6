package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val byParent = remember(categories) { categories.groupBy { it.parentId } }

    fun flatten(parentId: Long?, depth: Int = 0, result: MutableList<Pair<Category, Int>>) {
        byParent[parentId].orEmpty().sortedWith(compareByDescending<Category> { it.isDefault }.thenBy { it.name }).forEach { cat ->
            result += cat to depth
            flatten(cat.id, depth + 1, result)
        }
    }
    val displayCategories = remember(categories) {
        mutableListOf<Pair<Category, Int>>().also { flatten(null, result = it) }
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
            items(displayCategories, key = { it.first.id }) { (cat, depth) ->
                CategoryRow(
                    category = cat,
                    childCount = byParent[cat.id]?.size ?: 0,
                    depth = depth,
                    onDelete = { pendingDelete = cat }
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, kind, parentId ->
                onAdd(name, kind, parentId)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { cat ->
        val childCount = byParent[cat.id]?.size ?: 0
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف «${cat.name}»؟") },
            text = {
                Text(
                    if (childCount > 0)
                        "این دسته $childCount زیرشاخه مستقیم دارد. زیرشاخه‌ها حفظ می‌شوند و به سطح اصلی منتقل خواهند شد."
                    else "این عمل قابل بازگشت نیست."
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
    depth: Int,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(start = (depth * 20).dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    (if (depth > 0) "↳ " else "") + category.name,
                    style = if (depth > 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium
                )
                Text(
                    kindLabel(category.kind) + if (childCount > 0) " · $childCount زیرشاخه مستقیم" else "",
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
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryKind, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var parentId by remember { mutableStateOf<Long?>(null) }
    val byId = remember(categories) { categories.associateBy { it.id } }

    fun pathOf(category: Category): String {
        val path = mutableListOf<String>()
        val visited = mutableSetOf<Long>()
        var current: Category? = category
        while (current != null && visited.add(current.id)) {
            path += current.name
            current = current.parentId?.let { byId[it] }
        }
        return path.asReversed().joinToString(" / ")
    }

    val eligibleParents = remember(categories, kind) { categories.filter { it.kind == kind }.sortedBy { pathOf(it) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دسته‌بندی جدید") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                Column {
                    Text("زیرمجموعه کدام دسته باشد؟ (اختیاری)", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = parentId == null, onClick = { parentId = null })
                        Text("بدون والد")
                    }
                    eligibleParents.forEach { parent ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = parentId == parent.id, onClick = { parentId = parent.id })
                            Text(pathOf(parent))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), kind, parentId) },
                enabled = name.isNotBlank()
            ) { Text("افزودن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
