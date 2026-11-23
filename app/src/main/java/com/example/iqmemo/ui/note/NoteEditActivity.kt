package com.example.iqmemo.ui.note

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.iqmemo.IQMemoApplication
import com.example.iqmemo.R
import com.example.iqmemo.data.database.NoteEntity
import com.example.iqmemo.data.database.TaskMetadataEntity
import com.example.iqmemo.data.repository.NoteRepository
import com.example.iqmemo.databinding.ActivityNoteEditBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditBinding
    private var noteId: Long = -1L
    private var originalNote: NoteEntity? = null
    private var originalTask: TaskMetadataEntity? = null
    private var loadingExisting = false
    private var existingNoteReadOnly = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private var autoSaveJob: Job? = null
    private val saveMutex = Mutex()
    @Volatile private var saveGeneration = 0L
    @Volatile private var explicitDeleteInProgress = false

    companion object {
        private const val EXTRA_NOTE_ID = "note_id"
        private const val STATE_EDITING_EXISTING = "editing_existing_note"

        fun createIntent(context: Context, noteId: Long = -1L): Intent =
            Intent(context, NoteEditActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }

        fun start(context: Context, noteId: Long = -1L) {
            context.startActivity(createIntent(context, noteId))
        }


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStatusBarInset()

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        existingNoteReadOnly = noteId != -1L &&
            savedInstanceState?.getBoolean(STATE_EDITING_EXISTING) != true
        if (noteId != -1L && existingNoteReadOnly) {
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            binding.root.isFocusableInTouchMode = true
            binding.root.requestFocus()
            binding.etContent.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                isCursorVisible = false
                showSoftInputOnFocus = false
            }
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        if (noteId != -1L) {
            loadExistingNote()
        }
        updateDeleteVisibility()

        binding.etContent.doAfterTextChanged {
            if (!loadingExisting && !explicitDeleteInProgress) {
                autoSaveJob?.cancel()
                autoSaveJob = lifecycleScope.launch { delay(650); performAutoSave() }
            }
        }
        binding.etContent.setOnClickListener {
            if (existingNoteReadOnly) enterEditMode()
        }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_EDITING_EXISTING, !existingNoteReadOnly)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        startAutoSave()
    }

    override fun onStop() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        super.onStop()
    }

    private fun applyStatusBarInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun loadExistingNote() {
        loadingExisting = true
        binding.etContent.isEnabled = false
        lifecycleScope.launch {
            val record = runCatching {
                NoteRepository(application).getNoteWithTaskById(noteId)
            }.getOrNull()
            if (record == null) {
                Toast.makeText(this@NoteEditActivity, R.string.note_load_failed, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            originalNote = record.note
            originalTask = record.task
            binding.etContent.setText(record.note.content)
            loadingExisting = false
            binding.etContent.isEnabled = true

            binding.layoutTimeInfo.visibility = View.VISIBLE
            binding.tvCreateTime.text = getString(
                R.string.created_time, dateFormat.format(Date(record.note.createdAt))
            )
            binding.tvUpdateTime.text = getString(
                R.string.updated_time, dateFormat.format(Date(record.note.updatedAt))
            )
        }
    }

    private fun enterEditMode() {
        if (!existingNoteReadOnly) return
        existingNoteReadOnly = false
        updateDeleteVisibility()
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        binding.etContent.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = true
            showSoftInputOnFocus = true
            requestFocus()
            setSelection(text?.length ?: 0)
            post {
                ViewCompat.getWindowInsetsController(this)?.show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    private fun updateDeleteVisibility() {
        val visible = noteId != -1L && existingNoteReadOnly
        binding.deleteBar.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnDelete.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun startAutoSave() {
        // Saving is triggered by edits and onPause; idle pages never schedule writes.
    }

    override fun onPause() {
        super.onPause()
        if (explicitDeleteInProgress) return
        queueSave(currentContent(), deleteWhenBlank = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSaveJob?.cancel()
    }

    private fun performAutoSave() {
        val content = currentContent()
        if (content.isBlank()) return
        queueSave(content, deleteWhenBlank = false)
    }

    private fun currentContent(): String = binding.etContent.text?.toString().orEmpty()

    private fun queueSave(content: String, deleteWhenBlank: Boolean) {
        val generation = ++saveGeneration
        if (loadingExisting || explicitDeleteInProgress) return
        val appScope = (application as IQMemoApplication).appScope
        appScope.launch {
            saveMutex.withLock {
                if (generation != saveGeneration) return@withLock
                val repo = NoteRepository(application)

                if (content.isBlank()) {
                    if (deleteWhenBlank && noteId != -1L) {
                        val existing = originalNote ?: repo.getNoteById(noteId)
                        existing?.let { repo.delete(it) }
                        originalNote = null
                        originalTask = null
                        noteId = -1L
                    }
                    return@withLock
                }

                if (noteId == -1L) {
                    val note = NoteEntity(content = content, source = "manual", status = "normal")
                    noteId = repo.insertRecord(note, null)
                    val saved = repo.getNoteWithTaskById(noteId)
                    originalNote = saved?.note
                    originalTask = saved?.task
                    showAutoSaved()
                } else {
                    val existing = originalNote ?: repo.getNoteById(noteId) ?: return@withLock
                    if (content != existing.content) {
                        val result = repo.saveEditor(existing, content)
                        if (result == null) {
                            explicitDeleteInProgress = true
                            ++saveGeneration
                            runOnUiThread {
                                Toast.makeText(this@NoteEditActivity, "该便签已删除，未重新创建", Toast.LENGTH_LONG).show()
                                finish()
                            }
                            return@withLock
                        }
                        val (saved, separate) = result
                        noteId = saved.note.id
                        originalNote = saved.note
                        originalTask = saved.task
                        if (separate) runOnUiThread {
                            Toast.makeText(this@NoteEditActivity, "原记录已有变化，本机稿已另存", Toast.LENGTH_LONG).show()
                        }
                        showAutoSaved()
                    }
                }
            }
        }
    }

    private fun showAutoSaved() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            binding.tvAutoSaveHint.visibility = View.VISIBLE
            binding.tvAutoSaveHint.postDelayed({
                binding.tvAutoSaveHint.visibility = View.INVISIBLE
            }, 1500)
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (explicitDeleteInProgress) return@setPositiveButton
                explicitDeleteInProgress = true
                autoSaveJob?.cancel()
                ++saveGeneration
                val targetNoteId = noteId
                val appScope = (application as IQMemoApplication).appScope
                appScope.launch {
                    val deleted = runCatching {
                        saveMutex.withLock {
                            if (targetNoteId == -1L) return@withLock true
                            NoteRepository(application).deleteById(targetNoteId)
                        }
                    }.getOrDefault(false)

                    if (deleted) {
                        originalNote = null
                        originalTask = null
                        noteId = -1L
                        runOnUiThread { finish() }
                    } else {
                        explicitDeleteInProgress = false
                        runOnUiThread {
                            startAutoSave()
                            Toast.makeText(
                                this@NoteEditActivity, R.string.delete_failed, Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
