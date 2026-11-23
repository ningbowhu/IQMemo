package com.example.iqmemo.data.backup

import com.example.iqmemo.data.database.NoteEntity
import com.example.iqmemo.data.database.NoteWithTask
import com.example.iqmemo.data.database.TaskMetadataEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupRecord(
    val note: NoteEntity,
    val task: TaskMetadataEntity?
)

/**
 * 纯数据 JSON 编解码器，保持顶层数组兼容 5.0.6，并以可选 task 对象扩展待办信息。
 */
object BackupPayloadCodec {
    private const val RECORD_FORMAT_VERSION = 2

    fun encode(records: List<NoteWithTask>): ByteArray {
        val jsonArray = JSONArray()
        records.forEach { record ->
            val note = record.note
            jsonArray.put(JSONObject().apply {
                put("id", note.id)
                put("content", note.content)
                put("createdAt", note.createdAt)
                put("updatedAt", note.updatedAt)
                put("source", note.source)
                put("status", note.status)
                put("voiceDuration", note.voiceDuration)
                put("recordFormatVersion", RECORD_FORMAT_VERSION)
                record.task?.let { task ->
                    put("task", JSONObject().apply {
                        put("completed", task.isCompleted)
                        putNullable("dueEpochDay", task.dueEpochDay)
                        putNullable("dueMinuteOfDay", task.dueMinuteOfDay)
                        putNullable("completedAt", task.completedAt)
                        putNullable("reminderAt", task.reminderAt)
                    })
                }
            })
        }
        return jsonArray.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun decode(jsonText: String?): List<BackupRecord>? {
        if (jsonText == null) return null
        return try {
            val array = JSONArray(jsonText)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    val note = NoteEntity(
                        content = obj.optString("content"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        source = obj.optString("source", "manual"),
                        status = obj.optString("status", "normal"),
                        voiceDuration = obj.optInt("voiceDuration", 0)
                    )
                    if (note.status == "normal") {
                        add(BackupRecord(note = note, task = parseTask(obj)))
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTask(noteObject: JSONObject): TaskMetadataEntity? {
        val taskObject = noteObject.optJSONObject("task")
        if (taskObject != null) {
            val dueEpochDay = taskObject.optNullableLong("dueEpochDay")
            val dueMinute = taskObject.optNullableInt("dueMinuteOfDay")
                ?.takeIf { dueEpochDay != null && it in 0..1439 }
            val completed = taskObject.optBoolean("completed", false)
            return TaskMetadataEntity(
                noteId = 0,
                isCompleted = completed,
                dueEpochDay = dueEpochDay,
                dueMinuteOfDay = dueMinute,
                completedAt = taskObject.optNullableLong("completedAt"),
                reminderAt = taskObject.optNullableLong("reminderAt")
            )
        }

        // 兼容早期历史 JSON 中可能存在的旧 isTodo/isCompleted 字段。
        if (noteObject.optBoolean("isTodo", false)) {
            val completed = noteObject.optBoolean("isCompleted", false)
            return TaskMetadataEntity(
                noteId = 0,
                isCompleted = completed,
                completedAt = null
            )
        }
        return null
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null
}
