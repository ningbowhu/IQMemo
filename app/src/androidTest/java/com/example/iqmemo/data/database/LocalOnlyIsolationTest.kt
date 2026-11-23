package com.example.iqmemo.data.database

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.iqmemo.data.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalOnlyIsolationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun publicRuntimeHasNoBindingClassesPermissionOrTables() {
        assertTrue(runCatching { Class.forName("com.example.iqmemo.sync.WorkbenchSync") }.isFailure)
        assertTrue(runCatching { Class.forName("com.example.iqmemo.sync.SyncVault") }.isFailure)
        val permissions = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        val db = NoteDatabase.getInstance(app).openHelper.readableDatabase
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('sync_links','sync_outbox')").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
    }

    @Test fun localMemoCrudAndDeletionDoNotNeedNetworkOrBinding() = runBlocking {
        val repository = NoteRepository(app)
        val ids = mutableListOf<Long>()
        try {
            val id = repository.insertRecord(NoteEntity(content="Synthetic local-only memo"), null)
            ids.add(id)
            val before = repository.getNoteById(id)!!
            assertEquals("Synthetic local-only memo", before.content)
            val saved = repository.saveEditor(before, "Synthetic edited memo")!!
            assertFalse(saved.second)
            assertEquals("Synthetic edited memo", repository.getNoteById(id)!!.content)
            assertTrue(repository.deleteById(id))
            assertNull(repository.saveEditor(before, "Late text must not revive a deletion"))
            ids.add(repository.insertRecord(NoteEntity(content="Synthetic batch A"), null))
            ids.add(repository.insertRecord(NoteEntity(content="Synthetic batch B"), null))
            assertEquals(2, repository.deleteByIds(ids.takeLast(2)))
            assertTrue(ids.all { repository.getNoteById(it) == null })
        } finally { repository.deleteByIds(ids) }
    }
}
