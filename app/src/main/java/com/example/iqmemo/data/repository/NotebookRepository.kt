package com.example.iqmemo.data.repository

import android.app.Application
import androidx.room.withTransaction
import com.example.iqmemo.data.backup.BackupManager
import com.example.iqmemo.data.database.NoteDatabase
import com.example.iqmemo.data.database.NotebookEntity

class NotebookRepository(private val application: Application) {
    private val db = NoteDatabase.getInstance(application)
    private val dao = db.notebookDao()
    val pages = dao.observeAll()
    suspend fun get(id: Long) = dao.get(id)

    data class Saved(val page: NotebookEntity?, val separate: Boolean = false)

    /** An existing deleted id must never be turned into an insertion. */
    suspend fun save(id: Long, title: String, content: String, expected: NotebookEntity?): Saved {
        val result = db.withTransaction {
            if (id <= 0) {
                if (title.isBlank() && content.isBlank()) return@withTransaction Saved(null)
                val page = NotebookEntity(title = title, content = content)
                Saved(page.copy(id = dao.insert(page)))
            } else {
                val current = dao.get(id) ?: return@withTransaction Saved(null)
                if (current.title == title && current.content == content) return@withTransaction Saved(current)
                val separate = expected != null &&
                    (current.title != expected.title || current.content != expected.content)
                val next = current.copy(title = title, content = content,
                    updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1))
                if (separate) {
                    val copy = next.copy(id = 0)
                    Saved(copy.copy(id = dao.insert(copy)), true)
                } else {
                    dao.update(next)
                    Saved(next)
                }
            }
        }
        if (result.page != null) BackupManager.backupCurrent(application)
        return result
    }

    suspend fun delete(ids: List<Long>): Int {
        val count = dao.delete(ids.distinct())
        if (count > 0) BackupManager.backupCurrent(application)
        return count
    }
}
