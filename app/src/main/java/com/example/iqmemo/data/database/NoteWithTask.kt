package com.example.iqmemo.data.database

import androidx.room.Embedded

/** 一条笔记及其可选待办属性。task 为 null 时表示普通笔记。 */
data class NoteWithTask(
    @Embedded
    val note: NoteEntity,
    @Embedded(prefix = "task_")
    val task: TaskMetadataEntity?
)
