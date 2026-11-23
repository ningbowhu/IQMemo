package com.example.iqmemo.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.iqmemo.R
import com.example.iqmemo.data.database.NoteWithTask
import com.example.iqmemo.databinding.ItemNoteBinding

class NoteListAdapter(
    private val onItemClick: (NoteWithTask) -> Unit,
    private val onItemLongClick: (NoteWithTask) -> Boolean,
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : ListAdapter<NoteWithTask, NoteListAdapter.NoteViewHolder>(NoteDiffCallback()) {

    private val selectedIds = mutableSetOf<Long>()
    var isSelectionMode = false
        private set

    fun selectAll() {
        selectedIds.clear()
        currentList.forEach { selectedIds.add(it.note.id) }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun enterSelectionMode(noteId: Long) {
        isSelectionMode = true
        selectedIds.add(noteId)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedIds(): List<Long> = selectedIds.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding, parent.context)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemNoteBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: NoteWithTask) {
            val note = record.note
            binding.tvContent.text = note.content.ifBlank { context.getString(R.string.empty_content) }
            binding.tvDraftTag.visibility =
                if (note.status == "draft" || record.task?.isCompleted == true) View.VISIBLE else View.GONE
            binding.tvDraftTag.text = if (record.task?.isCompleted == true) context.getString(R.string.sync_completed) else context.getString(R.string.draft)

            binding.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.cbSelect.isChecked = selectedIds.contains(note.id)
            binding.cbSelect.isClickable = false

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    if (!selectedIds.add(note.id)) selectedIds.remove(note.id)
                    binding.cbSelect.isChecked = selectedIds.contains(note.id)
                    onSelectionChanged?.invoke(selectedIds.size)
                } else {
                    onItemClick(record)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode(note.id)
                    true
                } else {
                    onItemLongClick(record)
                }
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<NoteWithTask>() {
        override fun areItemsTheSame(oldItem: NoteWithTask, newItem: NoteWithTask): Boolean =
            oldItem.note.id == newItem.note.id

        override fun areContentsTheSame(oldItem: NoteWithTask, newItem: NoteWithTask): Boolean =
            oldItem == newItem
    }
}
