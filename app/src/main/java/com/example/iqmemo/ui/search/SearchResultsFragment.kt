package com.example.iqmemo.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.iqmemo.R
import com.example.iqmemo.databinding.FragmentNoteListBinding
import com.example.iqmemo.databinding.ItemNotebookBinding
import com.example.iqmemo.ui.note.NoteEditActivity
import com.example.iqmemo.ui.notebook.NotebookEditActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SearchResultsFragment : Fragment() {
    private val model: SearchViewModel by activityViewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentNoteListBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, state: Bundle?) {
        val binding = FragmentNoteListBinding.bind(view)
        val adapter = ResultsAdapter { entry ->
            when (entry.kind) {
                SearchEntry.Kind.MEMO -> NoteEditActivity.start(requireContext(), entry.id)
                SearchEntry.Kind.NOTEBOOK -> startActivity(NotebookEditActivity.intent(requireContext(), entry.id))
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setPadding(0, binding.recyclerView.paddingTop, 0, 16)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(model.results, model.query) { results, query -> results to query }.collect { (results, query) ->
                    adapter.query = query
                    adapter.submitList(results)
                    binding.tvEmpty.setText(if (query.isBlank()) R.string.search_prompt else R.string.search_no_results)
                    binding.layoutEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private class ResultsAdapter(val open: (SearchEntry) -> Unit) : ListAdapter<SearchEntry, ResultsAdapter.Holder>(
        object : DiffUtil.ItemCallback<SearchEntry>() {
            override fun areItemsTheSame(a: SearchEntry, b: SearchEntry) = a.id == b.id && a.kind == b.kind
            override fun areContentsTheSame(a: SearchEntry, b: SearchEntry) = a == b
        }) {
        var query: String = ""
            set(value) { if (field != value) { field = value; notifyDataSetChanged() } }
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(
            ItemNotebookBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
        inner class Holder(private val binding: ItemNotebookBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(entry: SearchEntry) {
                val context = binding.root.context
                binding.tvTitle.text = entry.title.ifBlank {
                    entry.content.lineSequence().firstOrNull { it.isNotBlank() } ?: context.getString(R.string.empty_content)
                }
                binding.tvSummary.text = entry.excerpt(query)
                binding.tvSummary.visibility = if (entry.content.isBlank()) View.GONE else View.VISIBLE
                val section = context.getString(if (entry.kind == SearchEntry.Kind.MEMO) R.string.tab_memos else R.string.tab_notebooks)
                binding.tvTime.text = context.getString(R.string.search_result_origin, section,
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.updatedAt)))
                binding.root.setOnClickListener { open(entry) }
            }
        }
    }
}
