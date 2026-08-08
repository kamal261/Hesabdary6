// SmsFinance file version: 3 — the expanded "بیشتر" list now groups subcategories under their
// parent (indented, "↳ نام") instead of listing every category flat -- otherwise a subcategory
// and its parent looked like two unrelated entries with no indication they're related.
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

/**
 * Category picker built around the "reduce decisions" north star: the four
 * categories the user actually uses most (computed live from transaction
 * counts, never stored) are shown immediately as one-tap chips. Everything
 * else lives behind a "بیشتر" toggle with search + kind filter, so picking a
 * rare category is still possible without cluttering the common case.
 */
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

    val topFour = remember(categories, usageCounts) {
        val used = categories.filter { (usageCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { usageCounts[it.id] ?: 0 }
        if (used.size >= 4) {
            used.take(4)
        } else {
            val defaults = categories.filter { it.isDefault && it !in used }
            (used + defaults).distinct().take(4)
        }
    }

    val filtered = remember(categories, query, kindFilter) {
        categories.filter { cat ->
            (kindFilter == null || cat.kind == kindFilter) &&
                (query.isBlank() || cat.name.contains(query, ignoreCase = true))
        }
    }
    // Grouped for display: top-level categories (or any category whose parent got filtered
    // out by the kind/search filters) first, each immediately followed by its matching
    // children -- so "↳ سوپرمارکت" always renders right under "خواروبار", not scattered.
    val displayOrder = remember(filtered) {
        val byParent = filtered.filter { it.parentId != null }.groupBy { it.parentId }
        val roots = filtered.filter { it.parentId == null || filtered.none { p -> p.id == it.parentId } }
        roots.flatMap { root -> listOf(root to false) + (byParent[root.id].orEmpty().map { it to true }) }
    }

    Column(modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("بدون دسته") })
            topFour.forEach { cat ->
                FilterChip(selected = selectedId == cat.id, onClick = { onSelect(cat.id) }, label = { Text(cat.name) })
            }
        }

        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "بستن لیست کامل" else "بیشتر (${categories.size} دسته)")
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
                placeholder = { Text("جستجوی دسته...") },
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
                // FlowRow per row keeps chips wrapping correctly while still grouping
                // parent/child visually via a leading indent on children.
                displayOrder.chunked(3).forEach { rowItems ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { (cat, isChild) ->
                            if (isChild) Spacer(Modifier.width(12.dp))
                            FilterChip(
                                selected = selectedId == cat.id,
                                onClick = { onSelect(cat.id) },
                                label = { Text((if (isChild) "↳ " else "") + cat.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}
