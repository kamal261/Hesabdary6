package com.kamal.smsfinance.data

/** Pure helpers for presenting and resolving arbitrary-depth category trees. */
object CategoryTree {
    fun pathOf(categoryId: Long?, categories: List<Category>): String {
        if (categoryId == null) return "بدون دسته"
        val byId = categories.associateBy { it.id }
        val path = mutableListOf<String>()
        val visited = mutableSetOf<Long>()
        var current = byId[categoryId]
        while (current != null && visited.add(current.id)) {
            path += current.name
            current = current.parentId?.let { byId[it] }
        }
        return if (path.isEmpty()) "بدون دسته" else path.asReversed().joinToString(" / ")
    }

    fun kindLabel(kind: CategoryKind): String = when (kind) {
        CategoryKind.INCOME -> "درآمد"
        CategoryKind.EXPENSE -> "هزینه"
        CategoryKind.DEBT_COLLECTION -> "وصول طلب"
        CategoryKind.DEBT_PAYMENT -> "پرداخت بدهی"
    }
}
