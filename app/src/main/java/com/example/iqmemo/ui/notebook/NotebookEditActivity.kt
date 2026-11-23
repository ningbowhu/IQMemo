package com.example.iqmemo.ui.notebook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.iqmemo.R
import com.example.iqmemo.databinding.ActivityNotebookEditBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotebookEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotebookEditBinding
    private val model: NotebookEditorModel by viewModels()
    private var bindingText = false
    private var rendered = false

    companion object {
        fun intent(context: Context, id: Long = -1) = Intent(context, NotebookEditActivity::class.java).putExtra("notebook_id", id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding = ActivityNotebookEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvError.setOnClickListener {
            if (model.state.value.phase == "failed") model.flush()
        }
        binding.etTitle.doAfterTextChanged { changed() }
        binding.etContent.doAfterTextChanged { changed() }
        listOf(binding.etTitle, binding.etContent).forEach { field ->
            field.setOnClickListener {
                if (model.state.value.readOnly) {
                    // Hide synchronously before the IME opens; Flow rendering can follow later.
                    binding.deleteBar.visibility = View.GONE
                    model.edit()
                    enableEditing(true)
                    field.requestFocus()
                    field.setSelection(field.text.length)
                    field.post { ViewCompat.getWindowInsetsController(field)?.show(WindowInsetsCompat.Type.ime()) }
                }
            }
        }
        binding.btnDelete.setOnClickListener {
            NotebookDeleteDialog.show(this, getString(R.string.notebook_delete_message)) { model.delete() }
        }
        model.load(intent.getLongExtra("notebook_id", -1))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (state.deleted) { finish(); return@collect }
                    bindingText = true
                    if (binding.etTitle.text.toString() != state.title) binding.etTitle.setText(state.title)
                    if (binding.etContent.text.toString() != state.content) binding.etContent.setText(state.content)
                    bindingText = false
                    binding.etTitle.isEnabled = state.ready
                    binding.etContent.isEnabled = state.ready
                    enableEditing(state.ready && !state.readOnly)
                    binding.deleteBar.visibility = if (state.id > 0 && state.readOnly) View.VISIBLE else View.GONE
                    binding.tvTime.text = if (state.updatedAt > 0) getString(R.string.updated_time,
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(state.updatedAt))) else ""
                    val error = when (state.phase) {
                        "failed" -> R.string.notebook_save_failed
                        "delete_failed" -> R.string.delete_failed
                        "load_failed" -> R.string.note_load_failed
                        "recovered" -> R.string.notebook_recovery_message
                        "separate" -> R.string.notebook_separate
                        else -> 0
                    }
                    binding.tvError.visibility = if (error != 0) View.VISIBLE else View.GONE
                    binding.tvError.isClickable = state.phase == "failed"
                    if (error != 0) binding.tvError.setText(error)
                    if (state.ready && !rendered) {
                        rendered = true
                        if (!state.readOnly) binding.etContent.post {
                            binding.etContent.requestFocus()
                            ViewCompat.getWindowInsetsController(binding.etContent)?.show(WindowInsetsCompat.Type.ime())
                        }
                    }
                }
            }
        }
    }

    private fun enableEditing(enabled: Boolean) {
        listOf(binding.etTitle, binding.etContent).forEach { field ->
            field.isFocusable = enabled
            field.isFocusableInTouchMode = enabled
            field.isCursorVisible = enabled
            field.showSoftInputOnFocus = enabled
        }
    }

    private fun changed() {
        if (!bindingText) model.change(binding.etTitle.text.toString(), binding.etContent.text.toString())
    }

    override fun onPause() { super.onPause(); model.flush() }
}
