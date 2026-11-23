package com.example.iqmemo.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, TaskMetadataEntity::class, NotebookEntity::class],
    version = 7,
    exportSchema = true
)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun notebookDao(): NotebookDao

    companion object {
        private const val DATABASE_NAME = "iqmemo_db"

        @Volatile
        private var INSTANCE: NoteDatabase? = null

        /** 从 v2 升级到 v3：重建 notes 表，移除 title/isTodo/isCompleted/category，添加 source/status/voiceDuration */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建新表
                db.execSQL("""
                    CREATE TABLE notes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'manual',
                        status TEXT NOT NULL DEFAULT 'normal',
                        voiceDuration INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                // 迁移现有数据（content保留，其余旧字段丢弃）
                db.execSQL("""
                    INSERT INTO notes_new (id, content, createdAt, updatedAt, source, status, voiceDuration)
                    SELECT id, content, createdAt, updatedAt, 'manual', 'normal', 0 FROM notes
                """.trimIndent())
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")
            }
        }

        /**
         * v4 只新增可选待办属性表，不改写 notes，确保 5.0.6 的全部笔记原样保留。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_metadata (
                        noteId INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        dueEpochDay INTEGER,
                        dueMinuteOfDay INTEGER,
                        completedAt INTEGER,
                        reminderAt INTEGER,
                        PRIMARY KEY(noteId),
                        FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_metadata_dueEpochDay ON task_metadata(dueEpochDay)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_metadata_isCompleted ON task_metadata(isCompleted)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_links (id TEXT NOT NULL PRIMARY KEY, noteId INTEGER, revision INTEGER NOT NULL, localHash TEXT NOT NULL, conflict TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_links_noteId ON sync_links(noteId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (seq INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mutationId TEXT NOT NULL, syncId TEXT NOT NULL, operation TEXT NOT NULL, payload TEXT NOT NULL, baseRevision INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_mutationId ON sync_outbox(mutationId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notebooks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }

        /** Public local-only edition: remove obsolete binding/queue tables, preserve written content. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sync_links")
                db.execSQL("DROP TABLE IF EXISTS sync_outbox")
            }
        }

        fun getInstance(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
