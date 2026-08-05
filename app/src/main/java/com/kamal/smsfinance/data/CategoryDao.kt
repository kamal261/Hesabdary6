package com.kamal.smsfinance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name ASC")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name ASC")
    suspend fun getAllOnce(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int
}
