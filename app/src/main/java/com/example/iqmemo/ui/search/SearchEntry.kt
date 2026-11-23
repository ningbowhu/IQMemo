package com.example.iqmemo.ui.search

data class SearchEntry(val kind: Kind, val id: Long, val title: String,
    val content: String, val updatedAt: Long) {
    enum class Kind { MEMO, NOTEBOOK }

    fun matches(query: String): Boolean = query.trim().let { keyword ->
        keyword.isNotEmpty() && (title.contains(keyword, ignoreCase = true) ||
            content.contains(keyword, ignoreCase = true))
    }

    /** Show the matching passage even when the keyword is far below the first line. */
    fun excerpt(query: String): String {
        val index = content.indexOf(query.trim(), ignoreCase = true)
        val start = if (index < 0) 0 else (index - 24).coerceAtLeast(0)
        return (if (start > 0) "…" else "") + content.substring(start).take(180).replace('\n', ' ')
    }
}
