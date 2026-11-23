package com.example.iqmemo.ui.note

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iqmemo.data.database.NoteEntity
import com.example.iqmemo.data.database.NoteWithTask
import com.example.iqmemo.data.repository.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(application)

    val allNotes: StateFlow<List<NoteEntity>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allRecords: StateFlow<List<NoteWithTask>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insert(content: String, source: String = "manual") = viewModelScope.launch {
        repository.insert(NoteEntity(content = content, source = source))
    }

    fun update(note: NoteEntity) = viewModelScope.launch {
        repository.update(note.copy(updatedAt = System.currentTimeMillis()))
    }

    fun delete(note: NoteEntity) = viewModelScope.launch {
        repository.delete(note)
    }

    suspend fun deleteByIds(ids: List<Long>): Int = repository.deleteByIds(ids)

    suspend fun setTaskCompleted(noteId: Long, completed: Boolean): Boolean =
        repository.setTaskCompleted(noteId, completed)

    suspend fun convertToTask(noteId: Long, dueEpochDay: Long?): Boolean =
        repository.convertToTask(noteId, dueEpochDay)

    fun deleteAll() = viewModelScope.launch {
        repository.deleteAll()
    }

    suspend fun createVoiceDraft(): Long {
        return repository.insert(
            NoteEntity(source = "voice", status = "draft")
        )
    }

    suspend fun getLatestVoiceDraft(): NoteEntity? = repository.getLatestVoiceDraft()

    suspend fun updateVoiceDraftContent(id: Long, content: String, duration: Int) {
        repository.updateVoiceDraftContent(id, content, duration)
    }

    suspend fun finalizeDraft(id: Long) {
        repository.finalizeDraft(id)
    }

    suspend fun deleteDraft(id: Long) {
        val note = repository.getNoteById(id) ?: return
        repository.delete(note)
    }

    suspend fun restoreFromUri(uri: Uri): Int = repository.restoreFromUri(uri)

    suspend fun exportToUri(uri: Uri): Int = repository.exportToUri(uri)
    suspend fun hasBackupData(): Boolean = !repository.isLocalEmpty()
}
