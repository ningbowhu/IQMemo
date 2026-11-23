package com.example.iqmemo.data.backup

import com.example.iqmemo.data.database.NotebookEntity
import com.example.iqmemo.data.database.NoteWithTask
import org.json.JSONArray
import org.json.JSONObject

data class FullBackup(val notes: List<BackupRecord>, val notebooks: List<NotebookEntity>)

/** V3 object format fails closed in older array-only apps, avoiding accidental uploads. */
object FullBackupCodec {
    fun encode(notes: List<NoteWithTask>, notebooks: List<NotebookEntity>): ByteArray {
        val pages = JSONArray()
        notebooks.forEach { p -> pages.put(JSONObject().put("title", p.title).put("content", p.content)
            .put("createdAt", p.createdAt).put("updatedAt", p.updatedAt)) }
        return JSONObject().put("formatVersion", 3)
            .put("notes", JSONArray(BackupPayloadCodec.encode(notes).toString(Charsets.UTF_8)))
            .put("notebooks", pages).toString(2).toByteArray(Charsets.UTF_8)
    }

    fun decode(text: String?): FullBackup? {
        if (text == null) return null
        return try {
            if (text.trimStart().startsWith("[")) {
                BackupPayloadCodec.decode(text)?.let { FullBackup(it, emptyList()) }
            } else {
                val root = JSONObject(text)
                require(root.getInt("formatVersion") == 3)
                val notes = requireNotNull(BackupPayloadCodec.decode(root.getJSONArray("notes").toString()))
                val array = root.getJSONArray("notebooks")
                val notebooks = (0 until array.length()).map { index ->
                    val p = array.getJSONObject(index)
                    require(p.get("title") is String && p.get("content") is String)
                    NotebookEntity(title = p.getString("title"), content = p.getString("content"),
                        createdAt = p.getLong("createdAt"), updatedAt = p.getLong("updatedAt"))
                }
                FullBackup(notes, notebooks)
            }
        } catch (_: Exception) { null }
    }
}
