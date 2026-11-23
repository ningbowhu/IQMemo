package com.example.iqmemo.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDatabaseMigrationTest {

    private companion object {
        const val TEST_DATABASE = "iqmemo-migration-test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NoteDatabase::class.java
    )

    @Test
    fun migrate3To4_preservesNotesAndCreatesEmptyTaskTable() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL(
                """
                INSERT INTO notes
                    (id, content, createdAt, updatedAt, source, status, voiceDuration)
                VALUES
                    (7, '升级前笔记', 100, 200, 'manual', 'normal', 0)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            NoteDatabase.MIGRATION_3_4
        )

        assertEquals("升级前笔记", migrated.singleString("SELECT content FROM notes WHERE id = 7"))
        assertEquals(0L, migrated.singleLong("SELECT COUNT(*) FROM task_metadata"))
        migrated.close()
    }

    @Test
    fun migrate4To5_preservesContentAndCompletion() {
        helper.createDatabase("iqmemo-sync-migration", 4).apply {
            execSQL("INSERT INTO notes (id,content,createdAt,updatedAt,source,status,voiceDuration) VALUES (11,'原便签与空行',100,200,'manual','normal',0)")
            execSQL("INSERT INTO task_metadata (noteId,isCompleted) VALUES (11,1)")
            close()
        }
        val migrated=helper.runMigrationsAndValidate("iqmemo-sync-migration",5,true,NoteDatabase.MIGRATION_4_5)
        assertEquals("原便签与空行",migrated.singleString("SELECT content FROM notes WHERE id=11"))
        assertEquals(1L,migrated.singleLong("SELECT isCompleted FROM task_metadata WHERE noteId=11"))
        assertEquals(0L,migrated.singleLong("SELECT COUNT(*) FROM sync_outbox"))
        migrated.close()
    }

    private fun SupportSQLiteDatabase.singleString(query: String): String =
        this.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    @Test fun migrate5To6_preservesMemosTasksAndFrozenSyncQueue() {
        helper.createDatabase("iqmemo-notebook-migration", 5).apply {
            execSQL("INSERT INTO notes (id,content,createdAt,updatedAt,source,status,voiceDuration) VALUES (11,'原便签',100,200,'manual','normal',0)")
            execSQL("INSERT INTO task_metadata (noteId,isCompleted) VALUES (11,1)")
            execSQL("INSERT INTO sync_links (id,noteId,revision,localHash,conflict) VALUES ('sync-a',11,7,'hash','')")
            execSQL("INSERT INTO sync_outbox (mutationId,syncId,operation,payload,baseRevision) VALUES ('frozen-a','sync-a','put','{}',7)")
            close()
        }
        val db = helper.runMigrationsAndValidate("iqmemo-notebook-migration", 6, true, NoteDatabase.MIGRATION_5_6)
        assertEquals("原便签", db.singleString("SELECT content FROM notes WHERE id=11"))
        assertEquals(1L, db.singleLong("SELECT isCompleted FROM task_metadata WHERE noteId=11"))
        assertEquals("frozen-a", db.singleString("SELECT mutationId FROM sync_outbox"))
        assertEquals(7L, db.singleLong("SELECT baseRevision FROM sync_outbox"))
        assertEquals(0L, db.singleLong("SELECT COUNT(*) FROM notebooks"))
        db.close()
    }

    @Test fun migrate6To7_removesBindingsAndPreservesBothLocalSections() {
        helper.createDatabase("iqmemo-public-local-migration", 6).apply {
            execSQL("INSERT INTO notes (id,content,createdAt,updatedAt,source,status,voiceDuration) VALUES (11,'Local memo',100,200,'manual','normal',0)")
            execSQL("INSERT INTO notebooks (id,title,content,createdAt,updatedAt) VALUES (12,'Local title','Local page',100,200)")
            execSQL("INSERT INTO sync_links (id,noteId,revision,localHash,conflict) VALUES ('synthetic-link',11,7,'synthetic','')")
            execSQL("INSERT INTO sync_outbox (mutationId,syncId,operation,payload,baseRevision) VALUES ('synthetic-mutation','synthetic-link','put','{}',7)")
            close()
        }
        val db = helper.runMigrationsAndValidate("iqmemo-public-local-migration", 7, true, NoteDatabase.MIGRATION_6_7)
        assertEquals("Local memo", db.singleString("SELECT content FROM notes WHERE id=11"))
        assertEquals("Local page", db.singleString("SELECT content FROM notebooks WHERE id=12"))
        assertEquals(0L, db.singleLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('sync_links','sync_outbox')"))
        db.close()
    }

    private fun SupportSQLiteDatabase.singleLong(query: String): Long =
        this.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
