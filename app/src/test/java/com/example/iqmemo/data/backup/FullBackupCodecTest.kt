package com.example.iqmemo.data.backup

import com.example.iqmemo.data.database.*
import org.junit.Assert.*
import org.junit.Test

class FullBackupCodecTest {
    @Test fun legacyArrayKeepsTaskAndCreatesNoNotebooks() {
        val decoded = FullBackupCodec.decode("""[{"content":"旧便签","isTodo":true,"isCompleted":true}]""")!!
        assertEquals("旧便签", decoded.notes.single().note.content)
        assertTrue(decoded.notes.single().task!!.isCompleted)
        assertTrue(decoded.notebooks.isEmpty())
    }

    @Test fun bothSectionsRoundTripWithoutTurningNotebooksIntoMemos() {
        val bytes = FullBackupCodec.encode(listOf(NoteWithTask(NoteEntity(content="上传便签"), null)), listOf(
            NotebookEntity(id=9, title="标题 😀", content="正文\n第二行", createdAt=100, updatedAt=200),
            NotebookEntity(title="", content="没有标题", createdAt=300, updatedAt=400),
            NotebookEntity(title="仅标题", content="", createdAt=500, updatedAt=600)))
        val text = bytes.toString(Charsets.UTF_8)
        val decoded = FullBackupCodec.decode(text)!!
        assertEquals(listOf("上传便签"), decoded.notes.map { it.note.content })
        assertEquals(listOf("标题 😀", "", "仅标题"), decoded.notebooks.map { it.title })
        assertEquals("正文\n第二行", decoded.notebooks[0].content)
        assertEquals(100L, decoded.notebooks[0].createdAt)
        assertEquals(200L, decoded.notebooks[0].updatedAt)
        assertEquals(0L, decoded.notebooks[0].id)
        assertNull("Older apps must reject rather than import local pages as syncable memos", BackupPayloadCodec.decode(text))
    }

    @Test fun malformedPageRejectsWholeDocument() {
        assertNull(FullBackupCodec.decode("""{"formatVersion":3,"notes":[{"content":"valid"}],"notebooks":[{"title":"bad","content":4,"createdAt":1,"updatedAt":2}]}"""))
        assertNull(FullBackupCodec.decode("""{"formatVersion":3,"notes":[],"notebooks":[{"title":"bad"}]}"""))
    }

    @Test fun unknownFormatAndTruncatedInputAreRejected() {
        assertNull(FullBackupCodec.decode("""{"formatVersion":4,"notes":[],"notebooks":[]}"""))
        assertNull(FullBackupCodec.decode("""{"formatVersion":3,"notes":[],"notebooks":null}"""))
        assertNull(FullBackupCodec.decode("{"))
    }
}
