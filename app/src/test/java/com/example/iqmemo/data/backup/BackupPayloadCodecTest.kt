package com.example.iqmemo.data.backup

import com.example.iqmemo.data.database.NoteEntity
import com.example.iqmemo.data.database.NoteWithTask
import com.example.iqmemo.data.database.TaskMetadataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadCodecTest {

    @Test
    fun `legacy 5_0_6 array restores as normal note without task`() {
        val decoded = BackupPayloadCodec.decode(
            """
            [{
              "id": 8,
              "content": "旧版笔记",
              "createdAt": 100,
              "updatedAt": 200,
              "source": "manual",
              "status": "normal",
              "voiceDuration": 0
            }]
            """.trimIndent()
        )

        assertNotNull(decoded)
        assertEquals(1, decoded!!.size)
        assertEquals("旧版笔记", decoded.single().note.content)
        assertNull(decoded.single().task)
    }

    @Test
    fun `task metadata survives encode and decode`() {
        val record = NoteWithTask(
            note = NoteEntity(
                id = 4,
                content = "提交项目方案",
                createdAt = 10,
                updatedAt = 20,
                source = "manual",
                status = "normal"
            ),
            task = TaskMetadataEntity(
                noteId = 4,
                isCompleted = true,
                dueEpochDay = 20_000,
                dueMinuteOfDay = 15 * 60,
                completedAt = 30
            )
        )

        val decoded = BackupPayloadCodec.decode(
            BackupPayloadCodec.encode(listOf(record)).toString(Charsets.UTF_8)
        )

        assertNotNull(decoded)
        assertEquals("提交项目方案", decoded!!.single().note.content)
        assertTrue(decoded.single().task!!.isCompleted)
        assertEquals(20_000L, decoded.single().task!!.dueEpochDay)
        assertEquals(900, decoded.single().task!!.dueMinuteOfDay)
        assertEquals(30L, decoded.single().task!!.completedAt)
    }

    @Test
    fun `historical todo fields become undated task`() {
        val decoded = BackupPayloadCodec.decode(
            """
            [{
              "content": "历史待办",
              "status": "normal",
              "isTodo": true,
              "isCompleted": false
            }]
            """.trimIndent()
        )

        assertNotNull(decoded)
        assertNotNull(decoded!!.single().task)
        assertFalse(decoded.single().task!!.isCompleted)
        assertNull(decoded.single().task!!.dueEpochDay)
    }

    @Test
    fun `one malformed element rejects whole backup`() {
        assertNull(BackupPayloadCodec.decode("[{\"content\":\"正常\"}, 3]"))
    }
}
