package com.example.iqmemo.ui.note

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iqmemo.R
import com.example.iqmemo.data.database.NoteWithTask
import com.example.iqmemo.databinding.FragmentNoteListBinding
import com.example.iqmemo.ui.adapter.NoteListAdapter
import com.example.iqmemo.ui.recording.RecordingActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteListFragment : Fragment() {

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteViewModel by activityViewModels()
    private lateinit var adapter: NoteListAdapter
    private var records: List<NoteWithTask> = emptyList()

    private val openBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val restored = viewModel.restoreFromUri(uri)
            val message = when {
                restored > 0 -> getString(R.string.restore_success, restored)
                restored == 0 -> getString(R.string.restore_no_new_notes)
                else -> getString(R.string.restore_failed)
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private val createBackupFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { viewModel.exportToUri(uri) }
            val message = result.fold(
                onSuccess = { getString(R.string.backup_success, it) },
                onFailure = { getString(R.string.backup_failed) }
            )
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSelectionBar()
        observeNotes()
    }

    fun openBackupCreator() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!viewModel.hasBackupData()) {
                Toast.makeText(requireContext(), R.string.backup_no_notes, Toast.LENGTH_LONG).show()
                return@launch
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            createBackupFile.launch("IQMemo_backup_manual_v3_$timestamp.json")
        }
    }

    fun openPlainTextRecorder() {
        NoteEditActivity.start(requireContext())
    }

    fun openPlainVoiceRecorder() {
        RecordingActivity.start(requireContext())
    }

    fun openBackupPicker() {
        viewLifecycleOwner.lifecycleScope.launch {
        if (viewModel.hasBackupData()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_backup)
                .setMessage(R.string.restore_merge_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_restore) { _, _ -> launchBackupPicker() }
                .show()
        } else {
            launchBackupPicker()
        }
        }
    }

    private fun launchBackupPicker() {
        openBackupFile.launch(arrayOf("application/json", "text/plain"))
    }

    private fun setupRecyclerView() {
        adapter = NoteListAdapter(
            onItemClick = { record ->
                NoteEditActivity.start(requireContext(), record.note.id)
            },
            onItemLongClick = { true },
            onSelectionChanged = { count -> updateSelectionBar(count) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NoteListFragment.adapter
        }
    }

    private fun setupSelectionBar() {
        binding.btnCancelSelection.setOnClickListener {
            adapter.exitSelectionMode()
        }

        binding.btnSelectAll.setOnClickListener { adapter.selectAll() }

        binding.btnDeleteSelected.setOnClickListener {
            val ids = adapter.getSelectedIds()
            if (ids.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_notes_title)
                .setMessage(getString(R.string.delete_selected_message, ids.size))
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val deleted = runCatching { viewModel.deleteByIds(ids) }.getOrDefault(-1)
                        if (deleted == ids.distinct().size) {
                            adapter.exitSelectionMode()
                        } else {
                            Toast.makeText(
                                requireContext(), R.string.delete_failed, Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun observeNotes() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allRecords.collect { latest ->
                records = latest
                // Old task metadata remains in storage/backup; every note is shown equally.
                adapter.submitList(latest)
                binding.layoutEmpty.visibility = if (latest.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (latest.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun updateSelectionBar(selectedCount: Int) {
        val selecting = adapter.isSelectionMode
        binding.layoutSelectionBar.visibility = if (selecting) View.VISIBLE else View.GONE
        binding.tvSelectionCount.text = getString(R.string.selection_count, selectedCount)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden && ::adapter.isInitialized) adapter.exitSelectionMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
