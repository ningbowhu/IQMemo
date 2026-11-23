package com.example.iqmemo.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    companion object {
        private const val NOTE_WITH_TASK_COLUMNS = """
            notes.id AS id,
            notes.content AS content,
            notes.createdAt AS createdAt,
            notes.updatedAt AS updatedAt,
            notes.source AS source,
            notes.status AS status,
            notes.voiceDuration AS voiceDuration,
            task_metadata.noteId AS task_noteId,
            task_metadata.isCompleted AS task_isCompleted,
            task_metadata.dueEpochDay AS task_dueEpochDay,
            task_metadata.dueMinuteOfDay AS task_dueMinuteOfDay,
            task_metadata.completedAt AS task_completedAt,
            task_metadata.reminderAt AS task_reminderAt
        """
    }

    @Query("SELECT * FROM notes WHERE status = 'normal' ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE status = 'draft' ORDER BY updatedAt DESC")
    fun getAllDrafts(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotesAndDrafts(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT $NOTE_WITH_TASK_COLUMNS
        FROM notes
        LEFT JOIN task_metadata ON task_metadata.noteId = notes.id
        ORDER BY notes.updatedAt DESC
        """
    )
    fun getAllNotesAndTasks(): Flow<List<NoteWithTask>>

    @Query(
        """
        SELECT $NOTE_WITH_TASK_COLUMNS
        FROM notes
        LEFT JOIN task_metadata ON task_metadata.noteId = notes.id
        WHERE notes.status = 'normal'
        ORDER BY notes.updatedAt DESC
        """
    )
    fun getAllNormalNotesAndTasks(): Flow<List<NoteWithTask>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query(
        """
        SELECT $NOTE_WITH_TASK_COLUMNS
        FROM notes
        LEFT JOIN task_metadata ON task_metadata.noteId = notes.id
        WHERE notes.id = :id
        LIMIT 1
        """
    )
    suspend fun getNoteWithTaskById(id: Long): NoteWithTask?

    @Query("SELECT * FROM notes WHERE status = 'draft' AND source = 'voice' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestVoiceDraft(): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskMetadataEntity)

    @Query("DELETE FROM task_metadata WHERE noteId = :noteId")
    suspend fun deleteTask(noteId: Long): Int

    @Query("SELECT * FROM task_metadata WHERE noteId = :noteId LIMIT 1")
    suspend fun getTaskByNoteId(noteId: Long): TaskMetadataEntity?

    @Query(
        """
        UPDATE task_metadata
        SET isCompleted = :completed, completedAt = :completedAt
        WHERE noteId = :noteId
        """
    )
    suspend fun updateTaskCompletion(noteId: Long, completed: Boolean, completedAt: Long?): Int

    @Query("UPDATE notes SET updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun touchNote(noteId: Long, updatedAt: Long)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("UPDATE notes SET status = 'normal', updatedAt = :updatedAt WHERE id = :id")
    suspend fun finalizeDraft(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET content = :content, voiceDuration = :duration, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateVoiceDraftContent(id: Long, content: String, duration: Int, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE notes
        SET content = :content,
            voiceDuration = :duration,
            status = 'normal',
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun finalizeVoiceDraftContent(
        id: Long,
        content: String,
        duration: Int,
        updatedAt: Long
    ): Int

    @Query("DELETE FROM notes WHERE status = 'draft' AND TRIM(content) = ''")
    suspend fun deleteBlankDrafts(): Int

    @Query("UPDATE notes SET status = 'normal', updatedAt = :updatedAt WHERE status = 'draft' AND TRIM(content) != ''")
    suspend fun finalizeInterruptedDrafts(updatedAt: Long = System.currentTimeMillis()): Int

    @Transaction
    suspend fun insertRecord(note: NoteEntity, task: TaskMetadataEntity?): Long {
        val noteId = insert(note)
        task?.let { upsertTask(it.copy(noteId = noteId)) }
        return noteId
    }

    @Transaction
    suspend fun updateRecord(note: NoteEntity, task: TaskMetadataEntity?) {
        update(note)
        if (task == null) {
            deleteTask(note.id)
        } else {
            upsertTask(task.copy(noteId = note.id))
        }
    }

    @Transaction
    suspend fun setTaskCompleted(noteId: Long, completed: Boolean, updatedAt: Long): Boolean {
        val changed = updateTaskCompletion(
            noteId = noteId,
            completed = completed,
            completedAt = updatedAt.takeIf { completed }
        )
        if (changed > 0) touchNote(noteId, updatedAt)
        return changed > 0
    }

    @Transaction
    suspend fun convertToTask(noteId: Long, dueEpochDay: Long?, updatedAt: Long): Boolean {
        if (getNoteById(noteId) == null) return false
        upsertTask(
            TaskMetadataEntity(
                noteId = noteId,
                dueEpochDay = dueEpochDay
            )
        )
        touchNote(noteId, updatedAt)
        return true
    }

    @Transaction
    suspend fun finalizeVoiceRecord(
        noteId: Long,
        content: String,
        duration: Int,
        task: TaskMetadataEntity?,
        updatedAt: Long
    ): Boolean {
        val changed = finalizeVoiceDraftContent(noteId, content, duration, updatedAt)
        if (changed == 0) return false
        if (task == null) {
            deleteTask(noteId)
        } else {
            upsertTask(task.copy(noteId = noteId))
        }
        return true
    }
}
