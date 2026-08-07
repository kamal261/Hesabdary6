// SmsFinance file version: 3 — hierarchical categories (parentId support). Top-level
// categories (parentId == null) shown as main chips. Tapping a parent expands inline
// to show its children. Selection always returns the leaf category id.
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
import androidx.compose.ui.Alignment
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.CategoryKind

private fun kindLabel(kind: CategoryKind): String = when (kind) {
    CategoryKind.INCOME -> "درآمد"
    CategoryKind.EXPENSE -> "هزینه"
    CategoryKind.DEBT_COLLECTION -> "وصول طلب"
    CategoryKind.DEBT_PAYMENT -> "پرداخت بدهی"
}

/**
 * Category picker with hierarchical support (parent/child). Top-level categories
 * (parentId == null) are shown as primary chips. When a parent has children,
 * it shows an expand indicator and tapping it reveals children inline.
 * Selection always returns the selected leaf category id.
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
    var expandedParents by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Build parent -> children map
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }.groupBy { it.parentId!! }
    }

    // Top-level categories (no parent)
    val topLevelCategories = remember(categories) {
        categories.filter { it.parentId == null }
    }

    val topFour = remember(topLevelCategories, usageCounts) {
        val used = topLevelCategories.filter { (usageCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { usageCounts[it.id] ?: 0 }
        if (used.size >= 4) {
            used.take(4)
        } else {
            val defaults = topLevelCategories.filter { it.isDefault && it !in used }
            (used + defaults).distinct().take(4)
        }
    }

    val fullList = remember(topLevelCategories, query, kindFilter) {
        topLevelCategories.filter { cat ->
            (kindFilter == null || cat.kind == kindFilter) &&
                (query.isBlank() || cat.name.contains(query, ignoreCase = true))
        }
    }

    Column(modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("بدون دسته") })
            topFour.forEach { cat ->
                val hasChildren = childrenByParent[cat.id]?.isNotEmpty() == true
                val isExpanded = expandedParents.contains(cat.id)
                FilterChip(
                    selected = selectedId == cat.id,
                    onClick = {
                        if (hasChildren) {
                            expandedParents = if (isExpanded) expandedParents - cat.id else expandedParents + cat.id
                        } else {
                            onSelect(cat.id)
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(cat.name)
                            if (hasChildren) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
                // Show children inline if expanded
                if (hasChildren && isExpanded) {
                    childrenByParent[cat.id]?.forEach { child ->
                        FilterChip(
                            selected = selectedId == child.id,
                            onClick = { onSelect(child.id) },
                            label = {
                                Row(modifier = Modifier.padding(start = 16.dp)) {
                                    Text("└ ${child.name}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        )
                    }
                }
            }
        }

        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "بستن لیست کامل" else "بیشتر (${topLevelCategories.size} دسته اصلی)")
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fullList.forEach { cat ->
                    val hasChildren = childrenByParent[cat.id]?.isNotEmpty() == true
                    val isExpanded = expandedParents.contains(cat.id)
                    FilterChip(
                        selected = selectedId == cat.id,
                        onClick = {
                            if (hasChildren) {
                                expandedParents = if (isExpanded) expandedParents - cat.id else expandedParents + cat.id
                            } else {
                                onSelect(cat.id)
                            }
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(cat.name)
                                if (hasChildren) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                    // Show children inline if expanded
                    if (hasChildren && isExpanded) {
                        childrenByParent[cat.id]?.forEach { child ->
                            FilterChip(
                                selected = selectedId == child.id,
                                onClick = { onSelect(child.id) },
                                label = {
                                    Row(modifier = Modifier.padding(start = 16.dp)) {
                                        Text("└ ${child.name}", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
