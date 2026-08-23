package com.kamal.smsfinance.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTreeTest {
    private val categories = listOf(
        Category(id = 1, name = "هزینه", kind = CategoryKind.EXPENSE, isDefault = true),
        Category(id = 2, name = "خرید خانه", kind = CategoryKind.EXPENSE, parentId = 1),
        Category(id = 3, name = "سوپرمارکت", kind = CategoryKind.EXPENSE, parentId = 2),
        Category(id = 4, name = "درآمد", kind = CategoryKind.INCOME, isDefault = true)
    )

    @Test
    fun path_contains_all_parent_levels() {
        assertEquals("هزینه / خرید خانه / سوپرمارکت", CategoryTree.pathOf(3, categories))
    }

    @Test
    fun precomputed_paths_match_single_path_lookup() {
        val paths = CategoryTree.pathsOf(categories)
        assertEquals("هزینه / خرید خانه / سوپرمارکت", paths[3])
        assertEquals("درآمد", paths[4])
    }

    @Test
    fun missing_category_is_explicitly_uncategorized() {
        assertEquals("بدون دسته", CategoryTree.pathOf(999, categories))
        assertEquals("بدون دسته", CategoryTree.pathOf(null, categories))
    }

    @Test
    fun cycle_does_not_loop_forever() {
        val cyclic = listOf(
            Category(id = 10, name = "الف", kind = CategoryKind.EXPENSE, parentId = 11),
            Category(id = 11, name = "ب", kind = CategoryKind.EXPENSE, parentId = 10)
        )
        assertEquals("ب / الف", CategoryTree.pathOf(10, cyclic))
    }
}
