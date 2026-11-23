package com.example.iqmemo.data.database

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.iqmemo.data.backup.FullBackup
import com.example.iqmemo.data.backup.BackupRecord
import com.example.iqmemo.data.repository.NoteRepository
import com.example.iqmemo.data.repository.NotebookRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookStorageTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val db = NoteDatabase.getInstance(application)
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun localCrudNeverWritesMemosAndDeletedEditorCannotResurrect() = runBlocking {
        val repository = NotebookRepository(application)
        val notes = count("notes")
        assertNull(repository.save(-1, "", "  ", null).page)
        val saved = repository.save(-1, "仅标题", "", null).page!!
        try {
            val edited = repository.save(saved.id, "", "无标题正文\n第二行", saved).page!!
            assertEquals("", edited.title)
            assertEquals("无标题正文\n第二行", repository.get(saved.id)!!.content)
            val blank = repository.save(saved.id, "", "", edited).page!!
            assertNotNull(repository.get(saved.id))
            assertEquals("", blank.content)
            repository.delete(listOf(saved.id))
            assertNull(repository.save(saved.id, "迟到标题", "不能复活", blank).page)
            assertEquals(notes, count("notes"))
        } finally { db.notebookDao().delete(listOf(saved.id)) }
    }

    @Test fun restoringBothSectionsIsIdempotentAndNotebookOnlyRestoreDoesNotChangeMemos() = runBlocking {
        val repo = NoteRepository(application)
        val page = NotebookEntity(title="恢复标题", content="本地正文", createdAt=161, updatedAt=162)
        val memoCount = count("notes")
        try {
            assertEquals(1, repo.importBackup(FullBackup(emptyList(), listOf(page))))
            assertEquals(0, repo.importBackup(FullBackup(emptyList(), listOf(page))))
            assertEquals(memoCount, count("notes"))
            val full = FullBackup(listOf(BackupRecord(NoteEntity(content="恢复便签",createdAt=171,updatedAt=172),null)), listOf(page))
            assertEquals(1, repo.importBackup(full))
            assertEquals(0, repo.importBackup(full))
            assertEquals("恢复标题", db.notebookDao().all().first { it.createdAt==161L }.title)
        } finally {
            db.notebookDao().delete(db.notebookDao().all().filter { it.createdAt==161L }.map { it.id })
            db.noteDao().getAllNotes().first().filter { it.createdAt==171L }.forEach { db.noteDao().deleteById(it.id) }
        }
    }

    @Test fun partialRestoreFailureRollsBackBothSections() = runBlocking {
        val notes = count("notes"); val pages = count("notebooks")
        db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER notebook_test_abort BEFORE INSERT ON notebooks BEGIN SELECT RAISE(ABORT,'synthetic storage failure'); END")
        try {
            val result = runCatching { NoteRepository(application).importBackup(FullBackup(
                listOf(BackupRecord(NoteEntity(content="回滚便签"),null)), listOf(NotebookEntity(title="回滚记事")))) }
            assertTrue(result.isFailure)
            assertEquals(notes,count("notes")); assertEquals(pages,count("notebooks"))
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS notebook_test_abort") }
    }
}
