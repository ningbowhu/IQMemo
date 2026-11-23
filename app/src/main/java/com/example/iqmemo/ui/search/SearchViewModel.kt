package com.example.iqmemo.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.iqmemo.data.repository.NoteRepository
import com.example.iqmemo.data.repository.NotebookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val query = savedState.getStateFlow("search_query", "")
    fun search(text: String) { savedState["search_query"] = text }

    // A read-only projection: notebook contents never enter the memo repository or sync.
    val results = combine(NoteRepository(application).allRecords,
        NotebookRepository(application).pages, query) { memos, pages, keyword ->
        if (keyword.isBlank()) emptyList() else {
            (memos.map { SearchEntry(SearchEntry.Kind.MEMO, it.note.id, "", it.note.content, it.note.updatedAt) } +
                pages.map { SearchEntry(SearchEntry.Kind.NOTEBOOK, it.id, it.title, it.content, it.updatedAt) })
                .filter { it.matches(keyword) }
                .sortedWith(compareByDescending<SearchEntry> { it.updatedAt }.thenBy { it.kind }.thenByDescending { it.id })
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
