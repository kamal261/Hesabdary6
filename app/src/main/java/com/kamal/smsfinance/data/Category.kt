package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Which of the four core buckets a category belongs to; custom categories can use any kind. */
enum class CategoryKind { INCOME, EXPENSE, DEBT_COLLECTION, DEBT_PAYMENT }

// SmsFinance file version: 3 — parentId forms an arbitrary-depth category tree. The database
// keeps the relationship lightweight so the app can build and validate paths in Kotlin, while
// the UI exposes the path to the user instead of hiding the hierarchy. No DB-level foreign key
// constraint is declared on purpose; TransactionRepository.deleteCategory preserves children
// by promoting direct children to the root before deleting a parent.
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
