package com.example.iqmemo

import android.app.Application
import com.example.iqmemo.data.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class IQMemoApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var initialization: Job
        private set

    override fun onCreate() {
        super.onCreate()
        initialization = appScope.launch {
            try {
                val repository = NoteRepository(this@IQMemoApplication)
                repository.normalizeInterruptedDrafts()
                if (repository.isLocalEmpty()) repository.restoreFromBackup()
            } catch (_: Exception) {
                // A failed local backup restore must not prevent startup.
            }
        }
    }
}
