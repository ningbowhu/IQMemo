package com.example.iqmemo.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 可选的待办属性。没有对应记录的 NoteEntity 仍然是普通笔记。
 *
 * dueEpochDay 使用本地日期的 LocalDate.toEpochDay()，避免全天事项因时区切换偏移。
 * dueMinuteOfDay 为空表示只指定日期、不指定具体时间。
 */
@Entity(
    tableName = "task_metadata",
    primaryKeys = ["noteId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["dueEpochDay"]),
        Index(value = ["isCompleted"])
    ]
)
data class TaskMetadataEntity(
    val noteId: Long,
    val isCompleted: Boolean = false,
    val dueEpochDay: Long? = null,
    val dueMinuteOfDay: Int? = null,
    val completedAt: Long? = null,
    val reminderAt: Long? = null
)
