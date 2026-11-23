package com.example.iqmemo.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.iqmemo.data.database.NoteWithTask
import com.example.iqmemo.data.database.NoteDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 将普通笔记备份到公共 Documents/IQMemoOpenSource。
 *
 * 写入采用“临时文件 -> 校验 -> 旧版降级为 previous -> 临时文件转正”的事务流程，
 * 并通过进程级 Mutex 防止多个实时保存任务同时替换同一份备份。
 */
object BackupManager {

    private const val LEGACY_BACKUP_FILE_NAME = "IQMemo_backup.json"
    private const val BACKUP_RELATIVE_PATH = "Documents/IQMemoOpenSource"
    private const val BACKUP_PREFERENCES = "iqmemo_backup"
    private const val BACKUP_GENERATION_KEY = "generation"

    private val writeMutex = Mutex()
    private var lastSuccessfulHash: String? = null

    /** Read BOTH sections inside the writer mutex and one DB transaction; no stale partial snapshot. */
    suspend fun backupCurrent(context: Context): Boolean = try {
        writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val data = snapshotBytes(context)
            val hash = sha256(data)
            if (hash == lastSuccessfulHash) return@withContext

            saveViaMediaStore(context, data)
            lastSuccessfulHash = hash
        }
        }
        true
    } catch (e: Exception) {
        android.util.Log.w("BackupManager", "Backup failed: ${e.javaClass.simpleName}")
        false
    }

    private suspend fun snapshotBytes(context: Context): ByteArray {
        val db = NoteDatabase.getInstance(context)
        return db.withTransaction {
            FullBackupCodec.encode(db.noteDao().getAllNormalNotesAndTasks().first(), db.notebookDao().all())
        }
    }

    private fun saveViaMediaStore(context: Context, data: ByteArray) {
        val resolver = context.contentResolver
        val names = backupNames(context)
        deleteByName(context, names.pending)

        var tempUri: Uri? = null
        var previousContainsLastGoodBackup = false

        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, names.pending)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$BACKUP_RELATIVE_PATH/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            tempUri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: throw IOException("无法创建临时备份文件")

            val output = resolver.openOutputStream(tempUri, "wt")
                ?: throw IOException("无法打开临时备份文件")
            output.use {
                it.write(data)
                it.flush()
                if (it is FileOutputStream) it.fd.sync()
            }
            verifyBytes(context, tempUri, data)

            val currentUri = findByName(context, names.current)
            if (currentUri != null) {
                deleteByName(context, names.previous)
                rename(context, currentUri, names.previous, pending = false)
                previousContainsLastGoodBackup = true
            }

            rename(context, tempUri, names.current, pending = false)
            verifyBytes(context, tempUri, data)
            tempUri = null
        } catch (error: Exception) {
            tempUri?.let { resolver.delete(it, null, null) }

            // 新版转正失败时，尽力把上一份有效备份恢复为固定文件名。
            if (previousContainsLastGoodBackup && findByName(context, names.current) == null) {
                findByName(context, names.previous)?.let {
                    runCatching { rename(context, it, names.current, pending = false) }
                }
            }
            throw error
        }
    }

    suspend fun loadBackup(context: Context): FullBackup? = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val names = backupNames(context)
            FullBackupCodec.decode(readByName(context, names.current))
                ?: FullBackupCodec.decode(readByName(context, names.previous))
                ?: FullBackupCodec.decode(readByName(context, names.current.replace("backup_v3_", "backup_")))
                ?: FullBackupCodec.decode(readByName(context, names.previous.replace("backup_v3_", "backup_")))
                ?: FullBackupCodec.decode(readByName(context, LEGACY_BACKUP_FILE_NAME))
        }
    }

    /** 读取用户通过系统文件选择器明确授权的备份文件。 */
    suspend fun loadBackupFromUri(context: Context, uri: Uri): FullBackup? =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
                FullBackupCodec.decode(text)
            }
        }

    /** 将普通笔记写入用户通过系统创建文件界面授权的目标 Uri，并执行写后校验。 */
    suspend fun saveBackupToUri(context: Context, uri: Uri): Int =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val data = snapshotBytes(context)
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("无法打开用户选择的备份文件")
                output.use {
                    it.write(data)
                    it.flush()
                    if (it is FileOutputStream) it.fd.sync()
                }
                verifyBytes(context, uri, data)
                val snapshot = requireNotNull(FullBackupCodec.decode(data.toString(Charsets.UTF_8)))
                snapshot.notes.size + snapshot.notebooks.size
            }
        }

    private fun verifyBytes(context: Context, uri: Uri, expected: ByteArray) {
        val actual = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取写入后的备份文件")
        if (!actual.contentEquals(expected)) throw IOException("备份写后校验失败")
        requireNotNull(FullBackupCodec.decode(actual.toString(Charsets.UTF_8))) { "备份格式无效" }
    }

    private fun readByName(context: Context, name: String): String? {
        val uri = findByName(context, name) ?: return null
        return context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun findByName(context: Context, name: String): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val args = arrayOf("$BACKUP_RELATIVE_PATH/", name)

        resolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return Uri.withAppendedPath(collection, cursor.getLong(0).toString())
            }
        }
        return null
    }

    private fun deleteByName(context: Context, name: String) {
        context.contentResolver.delete(
            MediaStore.Files.getContentUri("external"),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("$BACKUP_RELATIVE_PATH/", name)
        )
    }

    private fun rename(context: Context, uri: Uri, name: String, pending: Boolean) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.IS_PENDING, if (pending) 1 else 0)
        }
        if (context.contentResolver.update(uri, values, null, null) != 1) {
            throw IOException("无法完成备份文件替换")
        }
    }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }

    private data class BackupNames(
        val current: String,
        val previous: String,
        val pending: String
    )

    private fun backupNames(context: Context): BackupNames {
        val preferences = context.getSharedPreferences(BACKUP_PREFERENCES, Context.MODE_PRIVATE)
        val generation = preferences.getString(BACKUP_GENERATION_KEY, null) ?: run {
            val created = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            preferences.edit().putString(BACKUP_GENERATION_KEY, created).commit()
            created
        }
        return BackupNames(
            current = "IQMemo_backup_v3_$generation.json",
            previous = "IQMemo_backup_v3_$generation.previous.json",
            pending = "IQMemo_backup_v3_$generation.pending.json"
        )
    }
}
