package com.example.iqmemo.data.repository

import androidx.room.withTransaction
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import com.example.iqmemo.data.backup.BackupManager
import com.example.iqmemo.data.backup.BackupRecord
import com.example.iqmemo.data.backup.FullBackup
import com.example.iqmemo.data.database.NoteDatabase
import com.example.iqmemo.data.database.NoteEntity
import com.example.iqmemo.data.database.NoteWithTask
import com.example.iqmemo.data.database.TaskMetadataEntity
import com.example.iqmemo.widget.MemoWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class NoteRepository(private val application: Application) {

    private companion object {
        const val TAG = "NoteRepository"
    }

    private val db = NoteDatabase.getInstance(application)
    private val noteDao = db.noteDao()
    private suspend fun <T> mutate(action: suspend () -> T): T = db.withTransaction { action() }

    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotesAndDrafts()
    val allRecords: Flow<List<NoteWithTask>> = noteDao.getAllNotesAndTasks()

    private fun notifyWidget() {
        val manager = AppWidgetManager.getInstance(application)
        val ids = manager.getAppWidgetIds(
            ComponentName(application, MemoWidget::class.java)
        )
        ids.forEach { MemoWidget.updateWidget(application, manager, it) }
    }

    /** 每次写操作后调用，将当前所有 normal 条目备份到公共 Documents */
    private suspend fun triggerBackup() {
        try {
            BackupManager.backupCurrent(application)
        } catch (e: Exception) {
            // 不记录笔记内容，只保留可诊断的异常类型。
            Log.w(TAG, "Backup failed: ${e.javaClass.simpleName}")
        }
    }

    suspend fun insert(note: NoteEntity): Long {
        val id = mutate { noteDao.insert(note) }
        notifyWidget()
        if (note.status == "normal") triggerBackup()
        return id
    }

    suspend fun update(note: NoteEntity) {
        mutate { noteDao.update(note) }
        notifyWidget()
        if (note.status == "normal") triggerBackup()
    }

    suspend fun insertRecord(note: NoteEntity, task: TaskMetadataEntity?): Long {
        val id = mutate { noteDao.insertRecord(note, task) }
        notifyWidget()
        if (note.status == "normal") triggerBackup()
        return id
    }

    suspend fun saveEditor(expected: NoteEntity, content: String): Pair<NoteWithTask, Boolean>? {
        val result = db.withTransaction {
            val current = noteDao.getNoteWithTaskById(expected.id) ?: return@withTransaction null
            val separate = current.note.content != expected.content
            val id = if (separate) {
                noteDao.insertRecord(expected.copy(id=0, content=content, status="normal", updatedAt=System.currentTimeMillis()), current.task?.copy(noteId=0))
            } else {
                noteDao.updateRecord(current.note.copy(content=content, updatedAt=System.currentTimeMillis()), current.task)
                expected.id
            }
            Pair(requireNotNull(noteDao.getNoteWithTaskById(id)), separate)
        }
        notifyWidget(); triggerBackup()
        return result
    }

    suspend fun updateRecord(note: NoteEntity, task: TaskMetadataEntity?) {
        mutate { noteDao.updateRecord(note, task) }
        notifyWidget()
        if (note.status == "normal") triggerBackup()
    }

    suspend fun delete(note: NoteEntity) {
        deleteById(note.id)
    }

    /**
     * 按主键删除并复查结果。记录已经不存在也视为幂等成功；只有真正删除时才刷新
     * Widget 和轮换备份，避免界面在数据库尚未确认前报告成功。
     */
    suspend fun deleteById(id: Long): Boolean {
        val deleted = mutate { noteDao.deleteById(id) }
        val success = deleted > 0 || noteDao.getNoteById(id) == null
        if (deleted > 0) {
            notifyWidget()
            triggerBackup()
        }
        return success
    }

    suspend fun deleteByIds(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        val deleted = mutate { noteDao.deleteByIds(ids.distinct()) }
        if (deleted > 0) {
            notifyWidget()
            triggerBackup()
        }
        return deleted
    }

    suspend fun deleteAll() {
        mutate { noteDao.deleteAll() }
        notifyWidget()
        triggerBackup()
    }

    suspend fun getNoteById(id: Long): NoteEntity? = noteDao.getNoteById(id)
    suspend fun getNoteWithTaskById(id: Long): NoteWithTask? = noteDao.getNoteWithTaskById(id)
    suspend fun getLatestVoiceDraft(): NoteEntity? = noteDao.getLatestVoiceDraft()

    suspend fun setTaskCompleted(noteId: Long, completed: Boolean): Boolean {
        val changed = mutate { noteDao.setTaskCompleted(noteId, completed, System.currentTimeMillis()) }
        if (changed) {
            notifyWidget()
            triggerBackup()
        }
        return changed
    }

    suspend fun convertToTask(noteId: Long, dueEpochDay: Long?): Boolean {
        val changed = mutate { noteDao.convertToTask(noteId, dueEpochDay, System.currentTimeMillis()) }
        if (changed) {
            notifyWidget()
            triggerBackup()
        }
        return changed
    }

    suspend fun finalizeDraft(id: Long) {
        mutate { noteDao.finalizeDraft(id) }
        notifyWidget()
        triggerBackup()
    }

    suspend fun finalizeVoiceRecord(
        id: Long,
        content: String,
        duration: Int,
        task: TaskMetadataEntity?
    ): Boolean {
        val changed = mutate { noteDao.finalizeVoiceRecord(
            noteId = id,
            content = content,
            duration = duration,
            task = task,
            updatedAt = System.currentTimeMillis()
        ) }
        if (changed) {
            notifyWidget()
            triggerBackup()
        }
        return changed
    }

    suspend fun updateContent(id: Long, content: String) {
        mutate { noteDao.updateContent(id, content) }
        notifyWidget()
        triggerBackup()
    }

    suspend fun updateVoiceDraftContent(id: Long, content: String, duration: Int) {
        noteDao.updateVoiceDraftContent(id, content, duration)
        notifyWidget()
        // 录音中的 draft 高频更新不重写公共备份；完成录音时由 finalizeDraft 统一备份。
    }

    /**
     * 升级兼容：录音只会在 RecordingActivity 前台运行，因此应用冷启动时仍存在的 draft
     * 都属于旧版或异常终止遗留。空草稿删除，有内容草稿转为普通笔记，避免丢失识别文字。
     */
    suspend fun normalizeInterruptedDrafts(): Int {
        val deleted = noteDao.deleteBlankDrafts()
        val finalized = mutate { noteDao.finalizeInterruptedDrafts() }
        if (deleted + finalized > 0) {
            notifyWidget()
        }
        if (finalized > 0) {
            triggerBackup()
        }
        return deleted + finalized
    }

    /**
     * 仅供启动时还原使用：从公共 Documents 读取备份并批量插入。
     * 调用前应确认本地数据库为空。
     */
    suspend fun restoreFromBackup(): Int {
        val records = BackupManager.loadBackup(application) ?: return 0
        return importBackup(records)
    }

    /** 从用户明确选择的 JSON 文件恢复，按内容与时间等字段去重，不覆盖现有笔记。 */
    suspend fun restoreFromUri(uri: Uri): Int {
        val records = BackupManager.loadBackupFromUri(application, uri) ?: return -1
        return importBackup(records)
    }

    /** Merge both local sections atomically; failed imports roll back both sections. */
    internal suspend fun importBackup(records: FullBackup): Int {
        var restored = 0
        var notesAdded = 0
        db.withTransaction {
            val fingerprints = noteDao.getAllNormalNotesAndTasks().first()
                .mapTo(mutableSetOf()) { it.fingerprint() }
            records.notes.forEach { record ->
                if (fingerprints.add(record.fingerprint())) {
                    noteDao.insertRecord(record.note.copy(id = 0), record.task?.copy(noteId = 0))
                            notesAdded++
                    restored++
                }
            }
            val pages = db.notebookDao().all().mapTo(mutableSetOf()) {
                listOf(it.title, it.content, it.createdAt, it.updatedAt)
            }
            records.notebooks.forEach { page ->
                if (pages.add(listOf(page.title, page.content, page.createdAt, page.updatedAt))) {
                    db.notebookDao().insert(page.copy(id = 0))
                    restored++
                }
            }
        }
        if (notesAdded > 0) notifyWidget()
        if (restored > 0) {
            triggerBackup()
        }
        return restored
    }

    /** 导出当前全部 normal 笔记到用户选择的文件。 */
    suspend fun exportToUri(uri: Uri): Int {
        return BackupManager.saveBackupToUri(application, uri)
    }

    /** 检查本地 notes 表是否为空（用于判断是否需要从备份还原） */
    suspend fun isLocalEmpty(): Boolean = noteDao.getAllNotes().first().isEmpty() && db.notebookDao().all().isEmpty()

    private fun NoteEntity.fingerprint(): List<Any> = listOf(
        content,
        createdAt,
        updatedAt,
        source,
        status,
        voiceDuration
    )

    private fun NoteWithTask.fingerprint(): List<Any?> =
        note.fingerprint() + task.fingerprint()

    private fun BackupRecord.fingerprint(): List<Any?> =
        note.fingerprint() + task.fingerprint()

    private fun TaskMetadataEntity?.fingerprint(): List<Any?> = listOf(
        this != null,
        this?.isCompleted,
        this?.dueEpochDay,
        this?.dueMinuteOfDay,
        this?.completedAt,
        this?.reminderAt
    )
}
