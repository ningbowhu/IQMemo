package com.example.iqmemo.ui.notebook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iqmemo.IQMemoApplication
import com.example.iqmemo.data.database.NotebookEntity
import com.example.iqmemo.data.repository.NotebookRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A retained editor; pending writes outlive the Activity but never overwrite newer input. */
class NotebookEditorModel(application: Application) : AndroidViewModel(application) {
    companion object {
        // A reopened editor supersedes callbacks from the previous editor of the same draft.
        private val draftOwners = ConcurrentHashMap<String, String>()
    }
    data class State(val ready: Boolean = false, val id: Long = -1, val title: String = "",
        val content: String = "", val readOnly: Boolean = false, val phase: String = "idle",
        val updatedAt: Long = 0, val deleted: Boolean = false)

    private val repository = NotebookRepository(application)
    private val preferences = application.getSharedPreferences("notebook_editor_drafts", 0)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var initialized = false
    private var expected: NotebookEntity? = null
    private var generation = 0L
    private var draftKey = "new"
    private val session = UUID.randomUUID().toString()
    private var deleting = false
    private var delayed: Job? = null

    fun load(id: Long) {
        if (initialized) return
        initialized = true
        draftKey = if (id > 0) id.toString() else "new"
        draftOwners[draftKey] = session
        viewModelScope.launch {
            try {
                val page = if (id > 0) repository.get(id) else null
                if (id > 0 && page == null) {
                    clearDraft()
                    mutable.value = State(ready = true, deleted = true)
                    return@launch
                }
                val draft = runCatching { preferences.getString(draftKey, null)?.let(::JSONObject) }.getOrNull()
                // A successfully saved new page can leave a draft after process interruption.
                val recoveredId = draft?.optLong("id", -1) ?: -1
                val recovered = if (id <= 0 && recoveredId > 0) repository.get(recoveredId) else page
                if (id <= 0 && recoveredId > 0 && recovered == null) {
                    clearDraft()
                    mutable.value = State(ready = true)
                    return@launch
                }
                expected = recovered
                if (draft != null && draft.has("expectedTitle") && recovered != null) {
                    expected = recovered.copy(title = draft.getString("expectedTitle"), content = draft.getString("expectedContent"))
                }
                mutable.value = State(ready = true, id = recovered?.id ?: -1,
                    title = draft?.optString("title") ?: recovered?.title.orEmpty(),
                    content = draft?.optString("content") ?: recovered?.content.orEmpty(),
                    readOnly = recovered != null,
                    phase = if (draft != null) "recovered" else "idle", updatedAt = recovered?.updatedAt ?: 0)
                if (draft != null) delayed = viewModelScope.launch { delay(650); save() }
            } catch (_: Exception) { mutable.value = State(phase = "load_failed") }
        }
    }

    fun edit() { mutable.value = mutable.value.copy(readOnly = false) }

    fun change(title: String, content: String) {
        if (!mutable.value.ready || deleting || mutable.value.deleted) return
        if (title == mutable.value.title && content == mutable.value.content) return
        generation++
        mutable.value = mutable.value.copy(title = title, content = content, phase = "pending")
        stageDraft()
        delayed?.cancel()
        delayed = viewModelScope.launch { delay(650); save() }
    }

    private fun stageDraft() {
        if (draftOwners[draftKey] != session) return
        val s = mutable.value
        val json = JSONObject().put("id", s.id).put("title", s.title).put("content", s.content)
        expected?.let { json.put("expectedTitle", it.title).put("expectedContent", it.content) }
        preferences.edit().putString(draftKey, json.toString()).apply()
    }

    private fun clearDraft() {
        if (draftOwners[draftKey] == session) preferences.edit().remove(draftKey).apply()
    }

    fun flush() {
        delayed?.cancel()
        if (!mutable.value.ready || deleting || mutable.value.deleted) return
        viewModelScope.launch { save() }
    }

    private suspend fun save() = mutex.withLock {
        if (deleting || mutable.value.deleted || draftOwners[draftKey] != session) return@withLock
        val s = mutable.value
        if (!s.ready || (s.readOnly && s.phase != "recovered" && s.phase != "failed")) return@withLock
        if (s.id <= 0 && s.title.isBlank() && s.content.isBlank()) {
            clearDraft()
            mutable.value = s.copy(phase = "idle")
            return@withLock
        }
        if (s.title == expected?.title && s.content == expected?.content && s.phase != "failed") {
            clearDraft()
            mutable.value = s.copy(phase = "saved")
            return@withLock
        }
        val captured = generation
        mutable.value = s.copy(phase = "saving")
        try {
            val result = repository.save(s.id, s.title, s.content, expected)
            if (result.page == null) {
                clearDraft()
                mutable.value = mutable.value.copy(deleted = true)
                return@withLock
            }
            expected = result.page
            if (draftKey == "new" && draftOwners[draftKey] == session) {
                clearDraft()
                draftKey = result.page.id.toString()
                draftOwners[draftKey] = session
            }
            mutable.value = mutable.value.copy(id = result.page.id, updatedAt = result.page.updatedAt,
                phase = if (generation != captured) "pending" else if (result.separate) "separate" else "saved")
            if (generation == captured) clearDraft() else stageDraft()
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(phase = "failed")
            stageDraft()
        }
    }

    fun delete() {
        if (deleting) return
        deleting = true
        generation++
        delayed?.cancel()
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val id = mutable.value.id
                    if (id > 0) repository.delete(listOf(id))
                    clearDraft()
                    mutable.value = mutable.value.copy(deleted = true)
                } catch (_: Exception) {
                    deleting = false
                    mutable.value = mutable.value.copy(phase = "delete_failed")
                }
            }
        }
    }
}
