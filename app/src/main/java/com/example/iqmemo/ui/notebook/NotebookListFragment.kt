package com.example.iqmemo.ui.notebook

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.iqmemo.R
import com.example.iqmemo.data.database.NotebookEntity
import com.example.iqmemo.data.repository.NotebookRepository
import com.example.iqmemo.databinding.FragmentNoteListBinding
import com.example.iqmemo.databinding.ItemNotebookBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotebookListFragment : Fragment() {
    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var adapter: PagesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val repository = NotebookRepository(requireContext().applicationContext as Application)
        binding.tvEmpty.setText(R.string.notebook_empty)
        binding.tvEmpty.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.color_notebook_secondary))
        adapter = PagesAdapter({ startActivity(NotebookEditActivity.intent(requireContext(), it.id)) }) { updateSelection() }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.btnCancelSelection.setOnClickListener { adapter.clearSelection() }
        binding.btnSelectAll.setOnClickListener { adapter.selectAll() }
        binding.btnDeleteSelected.setOnClickListener {
            val ids = adapter.selected.toList()
            if (ids.isNotEmpty()) {
                NotebookDeleteDialog.show(requireContext(), getString(R.string.notebook_delete_selected, ids.size)) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = runCatching { repository.delete(ids) }
                        if (result.isSuccess) adapter.clearSelection()
                        else Toast.makeText(requireContext(), R.string.delete_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.pages.collect { pages ->
                    adapter.submitList(pages)
                    binding.layoutEmpty.visibility = if (pages.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility = if (pages.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun updateSelection() {
        _binding?.let {
            it.layoutSelectionBar.visibility = if (adapter.selecting) View.VISIBLE else View.GONE
            it.tvSelectionCount.text = getString(R.string.selection_count, adapter.selected.size)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden && ::adapter.isInitialized) adapter.clearSelection()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private class PagesAdapter(val open: (NotebookEntity) -> Unit, val changed: () -> Unit) :
        ListAdapter<NotebookEntity, PagesAdapter.Holder>(object : DiffUtil.ItemCallback<NotebookEntity>() {
            override fun areItemsTheSame(a: NotebookEntity, b: NotebookEntity) = a.id == b.id
            override fun areContentsTheSame(a: NotebookEntity, b: NotebookEntity) = a == b
        }) {
        val selected = mutableSetOf<Long>()
        var selecting = false
        fun clearSelection() { selected.clear(); selecting = false; notifyDataSetChanged(); changed() }
        fun selectAll() { selected.addAll(currentList.map { it.id }); notifyDataSetChanged(); changed() }
        override fun onCurrentListChanged(previousList: MutableList<NotebookEntity>, currentList: MutableList<NotebookEntity>) {
            selected.retainAll(currentList.map { it.id }.toSet())
            changed()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemNotebookBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
        inner class Holder(private val binding: ItemNotebookBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(page: NotebookEntity) {
                val context = binding.root.context
                binding.tvTitle.text = page.title.ifBlank {
                    page.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                        ?: context.getString(R.string.notebook_untitled)
                }
                binding.tvSummary.text = page.content
                binding.tvSummary.visibility = if (page.content.isBlank()) View.GONE else View.VISIBLE
                binding.tvTime.text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(page.updatedAt))
                binding.cbSelect.visibility = if (selecting) View.VISIBLE else View.GONE
                binding.cbSelect.isChecked = page.id in selected
                binding.root.setOnClickListener {
                    if (selecting) {
                        if (!selected.add(page.id)) {
                            selected.remove(page.id)
                        }
                        notifyItemChanged(bindingAdapterPosition)
                        changed()
                    } else open(page)
                }
                binding.root.setOnLongClickListener {
                    selecting = true
                    selected.add(page.id)
                    notifyDataSetChanged()
                    changed()
                    true
                }
            }
        }
    }
}
