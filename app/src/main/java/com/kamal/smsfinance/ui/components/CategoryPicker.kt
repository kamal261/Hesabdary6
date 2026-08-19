package com.kamal.smsfinance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.CategoryKind

private fun kindLabel(kind: CategoryKind): String = when (kind) {
    CategoryKind.INCOME -> "درآمد"
    CategoryKind.EXPENSE -> "هزینه"
    CategoryKind.DEBT_COLLECTION -> "وصول طلب"
    CategoryKind.DEBT_PAYMENT -> "پرداخت بدهی"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<Category>,
    usageCounts: Map<Long, Int>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf<CategoryKind?>(null) }

    val byId = remember(categories) { categories.associateBy { it.id } }
    val byParent = remember(categories) { categories.groupBy { it.parentId } }
    val topFour = remember(categories, usageCounts) {
        val used = categories.filter { (usageCounts[it.id] ?: 0) > 0 }.sortedByDescending { usageCounts[it.id] ?: 0 }
        val defaults = categories.filter { it.isDefault && it !in used }
        (used + defaults).distinct().take(4)
    }

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

    val filtered = remember(categories, query, kindFilter) {
        categories.filter { cat ->
            (kindFilter == null || cat.kind == kindFilter) &&
                (query.isBlank() || pathOf(cat).contains(query, ignoreCase = true))
        }
    }

    fun flatten(parentId: Long?, depth: Int = 0, output: MutableList<Pair<Category, Int>>) {
        byParent[parentId].orEmpty().sortedWith(compareByDescending<Category> { it.isDefault }.thenBy { it.name }).forEach { cat ->
            if (cat in filtered) output += cat to depth
            flatten(cat.id, depth + 1, output)
        }
    }

    val displayOrder = remember(filtered, categories) {
        val output = mutableListOf<Pair<Category, Int>>()
        if (query.isNotBlank() || kindFilter != null) {
            filtered.sortedBy { pathOf(it) }.forEach { output += it to 0 }
        } else {
            flatten(null, output = output)
        }
        output
    }

    Column(modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("بدون دسته") })
            topFour.forEach { cat ->
                FilterChip(selected = selectedId == cat.id, onClick = { onSelect(cat.id) }, label = { Text(pathOf(cat).substringAfterLast(" / ")) })
            }
        }

        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "بستن فهرست دسته‌ها" else "انتخاب از همه دسته‌ها (${categories.size})")
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (expanded) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("مثلاً سیگار یا خرید خانه") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = kindFilter == null, onClick = { kindFilter = null }, label = { Text("همه") })
                CategoryKind.values().forEach { k ->
                    FilterChip(selected = kindFilter == k, onClick = { kindFilter = k }, label = { Text(kindLabel(k)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                displayOrder.forEach { (cat, depth) ->
                    FilterChip(
                        selected = selectedId == cat.id,
                        onClick = { onSelect(cat.id) },
                        label = {
                            Text(
                                if (query.isNotBlank() || kindFilter != null) pathOf(cat)
                                else "${"  ".repeat(depth)}${if (depth > 0) "↳ " else ""}${cat.name}"
                            )
                        }
                    )
                }
            }
        }
    }
}
