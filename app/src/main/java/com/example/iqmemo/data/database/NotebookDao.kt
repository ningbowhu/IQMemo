package com.example.iqmemo.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC, id DESC")
    suspend fun all(): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun get(id: Long): NotebookEntity?

    @Insert suspend fun insert(page: NotebookEntity): Long
    @Update suspend fun update(page: NotebookEntity): Int

    @Query("DELETE FROM notebooks WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>): Int
}
