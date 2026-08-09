package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Which of the four core buckets a category belongs to; custom categories can use any kind. */
enum class CategoryKind { INCOME, EXPENSE, DEBT_COLLECTION, DEBT_PAYMENT }

// SmsFinance file version: 2 — added parentId for one level of subcategories (e.g. "هزینه" ->
// "خواروبار" -> "سوپرمارکت"). Deliberately one level, not an arbitrary-depth tree: a parent
// category with a non-null parentId of its own is not supported by the UI or by
// CategoryDao.getSubcategories -- this keeps the picker and the categories screen simple
// (matches the product's "سادگی بر امکانات" principle) while still letting the user group
// related categories. No DB-level foreign key constraint is declared on purpose (see the
// migration in AppDatabase.kt for why); TransactionRepository.deleteCategory enforces the
// invariant instead by re-parenting any children to top-level before deleting.
@Entity(tableName = "categories", indices = [Index("parentId")])
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val isDefault: Boolean = false,
    val parentId: Long? = null
)

object DefaultCategories {
    val seed = listOf(
        Category(name = "درآمد", kind = CategoryKind.INCOME, isDefault = true),
        Category(name = "هزینه", kind = CategoryKind.EXPENSE, isDefault = true),
        Category(name = "وصول طلب", kind = CategoryKind.DEBT_COLLECTION, isDefault = true),
        Category(name = "پرداخت بدهی", kind = CategoryKind.DEBT_PAYMENT, isDefault = true)
    )
}
