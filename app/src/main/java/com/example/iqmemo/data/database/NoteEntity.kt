package com.example.iqmemo.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "manual",      // manual / voice
    val status: String = "normal",      // normal / draft
    val voiceDuration: Int = 0          // 录音时长（秒），仅voice类型有效
)
