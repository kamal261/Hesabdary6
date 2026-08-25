package com.kamal.smsfinance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.CategoryKind
import com.kamal.smsfinance.data.CategoryTree

private fun sideKinds(side: CategoryKind): Set<CategoryKind> = when (side) {
    CategoryKind.INCOME, CategoryKind.DEBT_COLLECTION -> setOf(CategoryKind.INCOME, CategoryKind.DEBT_COLLECTION)
    CategoryKind.EXPENSE, CategoryKind.DEBT_PAYMENT -> setOf(CategoryKind.EXPENSE, CategoryKind.DEBT_PAYMENT)
}

private fun sideOf(kind: CategoryKind): CategoryKind = when (kind) {
    CategoryKind.INCOME, CategoryKind.DEBT_COLLECTION -> CategoryKind.INCOME
    CategoryKind.EXPENSE, CategoryKind.DEBT_PAYMENT -> CategoryKind.EXPENSE
}

private fun sideLabel(side: CategoryKind): String = if (side == CategoryKind.INCOME) "درآمد" else "هزینه"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<Category>,
    usageCounts: Map<Long, Int>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    initialKind: CategoryKind? = null,
    allowSideToggle: Boolean = true,
    onSideChange: ((CategoryKind) -> Unit)? = null,
    onCreateCategory: ((name: String, kind: CategoryKind, parentId: Long?) -> Unit)? = null,
    noteText: String? = null,
    onNoteChange: ((String) -> Unit)? = null,
    onSaveNote: (() -> Unit)? = null
) {
    val initialSide = sideOf(initialKind ?: CategoryKind.EXPENSE)
    var selectedSide by remember(initialKind) { mutableStateOf(initialSide) }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf<CategoryKind?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var noteExpanded by remember(noteText) { mutableStateOf(!noteText.isNullOrBlank()) }

    LaunchedEffect(initialKind) {
        if (initialKind != null) selectedSide = sideOf(initialKind)
    }

    val allowedKinds = sideKinds(selectedSide)
    val byParent = remember(categories) { categories.groupBy { it.parentId } }
    val categoryPaths = remember(categories) { CategoryTree.pathsOf(categories) }

    fun pathOf(category: Category): String = categoryPaths[category.id] ?: "بدون دسته"

    val quickCategories = remember(categories, usageCounts, selectedSide) {
        val candidates = categories.filter { it.kind in allowedKinds && !it.isDefault }
        candidates.sortedWith(
            compareByDescending<Category> { usageCounts[it.id] ?: 0 }
                .thenBy { pathOf(it) }
        ).take(4)
    }

    val filtered = remember(categories, query, kindFilter, selectedSide) {
        categories.filter { cat ->
            cat.kind in allowedKinds &&
                (kindFilter == null || cat.kind == kindFilter) &&
                (query.isBlank() || pathOf(cat).contains(query, ignoreCase = true))
        }
    }

    fun flatten(parentId: Long?, depth: Int = 0, output: MutableList<Pair<Category, Int>>) {
        byParent[parentId].orEmpty()
            .filter { it.kind in allowedKinds }
            .sortedWith(compareByDescending<Category> { it.isDefault }.thenBy { it.name })
            .forEach { cat ->
                if (cat in filtered) output += cat to depth
                flatten(cat.id, depth + 1, output)
            }
    }

    val displayOrder = remember(filtered, categories, selectedSide, query, kindFilter) {
        val output = mutableListOf<Pair<Category, Int>>()
        if (query.isNotBlank() || kindFilter != null) {
            filtered.sortedBy { pathOf(it) }.forEach { output += it to 0 }
        } else {
            flatten(null, output = output)
        }
        output
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowSideToggle) {
            Text("۱. نوع تراکنش", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedSide == CategoryKind.EXPENSE,
                    onClick = {
                        selectedSide = CategoryKind.EXPENSE
                        kindFilter = null
                        onSideChange?.invoke(CategoryKind.EXPENSE)
                    },
                    label = { Text("هزینه") }
                )
                FilterChip(
                    selected = selectedSide == CategoryKind.INCOME,
                    onClick = {
                        selectedSide = CategoryKind.INCOME
                        kindFilter = null
                        onSideChange?.invoke(CategoryKind.INCOME)
                    },
                    label = { Text("درآمد") }
                )
            }
        } else {
            Text("۱. نوع تراکنش: ${sideLabel(selectedSide)}", style = MaterialTheme.typography.titleSmall)
        }

        Text("۲. زیرشاخه ${sideLabel(selectedSide)}", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("بدون دسته") }
            )
            quickCategories.forEach { cat ->
                FilterChip(
                    selected = selectedId == cat.id,
                    onClick = { onSelect(cat.id) },
                    label = { Text(cat.name) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "بستن فهرست" else "بیشتر (${filtered.size})")
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (onCreateCategory != null) {
                OutlinedButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ایجاد شاخه جدید")
                }
            }
        }

        if (expanded) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("جستجو در زیرشاخه‌ها") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = kindFilter == null, onClick = { kindFilter = null }, label = { Text("همه") })
                allowedKinds.forEach { kind ->
                    FilterChip(
                        selected = kindFilter == kind,
                        onClick = { kindFilter = kind },
                        label = { Text(CategoryTree.kindLabel(kind)) }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                displayOrder.forEach { (cat, depth) ->
                    FilterChip(
                        selected = selectedId == cat.id,
                        onClick = { onSelect(cat.id) },
                        label = {
                            Text(
                                if (query.isNotBlank() || kindFilter != null) pathOf(cat)
                                else "${"  ".repeat(depth)}${if (depth > 0) "↳ " else ""}${cat.name}"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (onNoteChange != null) {
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            if (!noteExpanded) {
                OutlinedButton(onClick = { noteExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("افزودن یادداشت به این تراکنش")
                }
            } else {
                OutlinedTextField(
                    value = noteText.orEmpty(),
                    onValueChange = onNoteChange,
                    label = { Text("یادداشت اختیاری") },
                    placeholder = { Text("مثلاً: بابت خرید خانه") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (onSaveNote != null) {
                    TextButton(
                        onClick = onSaveNote,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("ذخیره یادداشت") }
                }
            }
        }
    }

    if (showCreateDialog && onCreateCategory != null) {
        CreateCategoryFromPickerDialog(
            categories = categories,
            allowedKinds = allowedKinds.toList(),
            defaultKind = if (selectedSide == CategoryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, kind, parentId ->
                onCreateCategory(name, kind, parentId)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreateCategoryFromPickerDialog(
    categories: List<Category>,
    allowedKinds: List<CategoryKind>,
    defaultKind: CategoryKind,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryKind, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember(defaultKind) { mutableStateOf(defaultKind) }
        var parentId by remember(kind, categories) {
        mutableStateOf(categories.firstOrNull { it.isDefault && it.kind == kind }?.id)
    }
    val categoryPaths = remember(categories) { CategoryTree.pathsOf(categories) }
    fun pathOf(category: Category): String = categoryPaths[category.id] ?: "بدون دسته"
    val eligibleParents = remember(categories, kind) {
        categories.filter { it.kind == kind }.sortedBy { pathOf(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ایجاد شاخه جدید برای ${if (kind == CategoryKind.INCOME || kind == CategoryKind.DEBT_COLLECTION) "درآمد" else "هزینه"}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام زیرشاخه") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("نوع دقیق دسته", style = MaterialTheme.typography.titleSmall)
                allowedKinds.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = kind == option,
                            onClick = { kind = option; parentId = categories.firstOrNull { it.isDefault && it.kind == option }?.id }
                        )
                        Text(CategoryTree.kindLabel(option))
                    }
                }
                Text("زیرمجموعه کدام شاخه باشد؟", style = MaterialTheme.typography.titleSmall)
                Text(
                    "دسته والد یعنی این شاخه‌ی جدید داخل کدوم دسته‌ی بزرگ‌تر قرار بگیره. مثلاً " +
                        "«تاکسی» و «بنزین» هر دو می‌تونن زیرمجموعه‌ی «حمل‌ونقل» باشن — این‌طوری هم " +
                        "جدا از هم دیده می‌شن، هم توی گزارش‌ها زیر یک عنوان کلی جمع می‌شن. اگر مطمئن " +
                        "نیستید، «بدون والد» را انتخاب کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), kind, parentId) },
                enabled = name.isNotBlank()
            ) { Text("ایجاد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
