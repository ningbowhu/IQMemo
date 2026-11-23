package com.example.iqmemo.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local-only pages, independent from memo queries and the desktop widget. */
@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
