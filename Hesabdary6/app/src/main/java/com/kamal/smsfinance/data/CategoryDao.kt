package com.kamal.smsfinance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name ASC")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name ASC")
    suspend fun getAllOnce(): List<Category>

    @Query("SELECT * FROM categories WHERE parentId IS NULL ORDER BY isDefault DESC, name ASC")
    fun getTopLevel(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY name ASC")
    fun getSubcategories(parentId: Long): Flow<List<Category>>

    /** Top-level-only re-parent guard: a category whose OWN parentId is non-null can't be
     * chosen as a parent for another category (keeps nesting to exactly one level). */
    @Query("SELECT * FROM categories WHERE parentId IS NULL ORDER BY isDefault DESC, name ASC")
    suspend fun getTopLevelOnce(): List<Category>

    /** Used before deleting a category: its children (if any) must be re-parented to
     * top-level first, since there's no DB-level ON DELETE behavior for parentId. */
    @Query("UPDATE categories SET parentId = NULL WHERE parentId = :parentId")
    suspend fun clearParent(parentId: Long)

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
