package com.kamal.smsfinance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Which of the four core buckets a category belongs to; custom categories can use any kind. */
enum class CategoryKind { INCOME, EXPENSE, DEBT_COLLECTION, DEBT_PAYMENT }

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val isDefault: Boolean = false
)

object DefaultCategories {
    val seed = listOf(
        Category(name = "درآمد", kind = CategoryKind.INCOME, isDefault = true),
        Category(name = "هزینه", kind = CategoryKind.EXPENSE, isDefault = true),
        Category(name = "وصول طلب", kind = CategoryKind.DEBT_COLLECTION, isDefault = true),
        Category(name = "پرداخت بدهی", kind = CategoryKind.DEBT_PAYMENT, isDefault = true)
    )
}
