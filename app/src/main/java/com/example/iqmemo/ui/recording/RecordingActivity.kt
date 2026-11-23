package com.example.iqmemo.ui.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.iqmemo.IQMemoApplication
import com.example.iqmemo.R
import com.example.iqmemo.data.repository.NoteRepository
import com.example.iqmemo.databinding.ActivityRecordingBinding
import com.example.iqmemo.speech.XfyunConfig
import com.example.iqmemo.speech.SpeechPreferences
import com.example.iqmemo.speech.SpeechLanguage
import com.example.iqmemo.speech.SpeechTextSettlement
import com.example.iqmemo.speech.SpeechAutoFinishPolicy
import com.example.iqmemo.speech.XfyunSpeechManager
import com.example.iqmemo.ui.note.NoteViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.widget.Toast
import java.util.Locale

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var repository: NoteRepository
    private val appScope get() = (application as IQMemoApplication).appScope

    private val language by lazy { SpeechPreferences.language(this) }
    private var noteId: Long = -1L
    private var draftCreation: Deferred<Long>? = null
    private val saveMutex = Mutex()
    @Volatile private var saveRevision = 0L
    @Volatile private var recordFinalized = false
    private val confirmedText = StringBuffer()
    @Volatile private var currentPartial: String = ""

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, RecordingActivity::class.java)

        fun start(context: Context) {
            context.startActivity(createIntent(context))
        }


    }

    private var recordingStarted = false
    private var engineRunning = false
    @Volatile private var isExiting = false
    private var reconnectDelayMs = 300L

    // 用固定 Handler + Runnable 管理重启，避免多个 pending restart 同时存在
    private val restartHandler = Handler(Looper.getMainLooper())
    private val restartRunnable = Runnable {
        if (!isExiting) {
            // 重连前先把当前可见的 partial 提交到 confirmedText，防止新 session 覆盖后文字丢失
            val partial = currentPartial
            if (partial.isNotBlank()) {
                val sep = if (confirmedText.isNotEmpty()) "，" else ""
                confirmedText.append(sep).append(partial)
                currentPartial = ""
                runOnUiThread { refreshDisplayText() }
                saveToDb(confirmedText.toString())
            }
            speechManager.reconnectSession()
        }
    }

    private var durationSeconds = 0
    private val durationHandler = Handler(Looper.getMainLooper())
    private val durationRunnable = object : Runnable {
        override fun run() {
            durationSeconds++
            updateDurationDisplay()
            durationHandler.postDelayed(this, 1000)
        }
    }

    private var heartbeatJob: Job? = null

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            exitRecording()
        }
    }

    private val speechManager: XfyunSpeechManager by lazy {
        XfyunSpeechManager(
            context = this,
            onResult = { text -> runOnUiThread {
                if (!recordFinalized) {
                    // Final replaces this session's partial; it must not be appended twice.
                    currentPartial = text
                    refreshDisplayText()
                    if (SpeechAutoFinishPolicy.shouldFinish(text) && !isExiting) exitRecording()
                }
            } },
            onError = { message, salvageText -> runOnUiThread {
                if (!recordFinalized && !isExiting) {
                    // Keep this as the current session until a reconnect actually begins.
                    // Finishing before the scheduled reconnect must not append it twice.
                    currentPartial = salvageText
                    refreshDisplayText()
                    saveCurrentWithPartial()
                    binding.tvStatus.text = "$message，正在重连…"
                    autoRestartListening()
                }
            } },
            onReadyForSpeech = { runOnUiThread {
                if (!isExiting) {
                    reconnectDelayMs = 300L
                    binding.tvStatus.text = getString(R.string.voice_listening)
                }
            } },
            onPartialResult = { partial -> runOnUiThread {
                if (!recordFinalized) {
                    currentPartial = partial
                    refreshDisplayText()
                    if (!isExiting) saveCurrentWithPartial()
                }
            } },
            onCaptureError = { message -> runOnUiThread {
                if (!isExiting) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    exitRecording()
                }
            } },
            language = language
        )
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            prepareRecording()
        } else {
            showPermissionDeniedAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvSpeechLanguage.text = getString(
            R.string.recording_language,
            getString(if (language == SpeechLanguage.ENGLISH) R.string.language_english else R.string.language_chinese)
        )
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        repository = NoteRepository(application)

        binding.btnCancel.setOnClickListener { exitRecording() }
        binding.btnFinish.setOnClickListener { exitRecording() }

        onBackPressedDispatcher.addCallback(this) {
            exitRecording()
        }

        checkPermissionAndStart()
    }

    private fun checkPermissionAndStart() {
        if (!XfyunConfig.isConfigured()) {
            binding.tvStatus.text = "语音未启用"
            AlertDialog.Builder(this)
                .setTitle("语音服务未配置")
                .setMessage("开源版未内置讯飞密钥，文字记录可直接使用。开发者可在本地配置自己的讯飞服务后重新构建；配置方法见 GitHub 仓库说明。")
                .setPositiveButton("确定") { _, _ -> finish() }
                .show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            prepareRecording()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun prepareRecording() {
        if (recordingStarted || isExiting) return
        recordingStarted = true
        // Creation survives an immediate Back/Home. The exit path awaits this same ID.
        val creation = appScope.async { viewModel.createVoiceDraft() }
        draftCreation = creation
        lifecycleScope.launch {
            val id = runCatching { creation.await() }.getOrElse {
                if (!isExiting) {
                    Toast.makeText(this@RecordingActivity, R.string.voice_save_failed, Toast.LENGTH_LONG).show()
                    finish()
                }
                return@launch
            }
            noteId = id
            if (!isExiting) startRecordingEngine()
        }
    }

    private fun startRecordingEngine() {
        if (engineRunning) return
        requestAudioFocus()
        speechManager.init()
        if (!speechManager.startListening()) {
            abandonAudioFocus()
            showPermissionDeniedAndFinish()
            return
        }
        engineRunning = true
        durationHandler.removeCallbacks(durationRunnable)
        durationHandler.postDelayed(durationRunnable, 1000)
        startHeartbeat()
        binding.tvStatus.text = getString(R.string.voice_connecting)
    }

    private fun autoRestartListening() {
        // 仅用于可恢复错误；正常句末由 onResult 自动保存并退出。
        // 取消上一个未执行的重启，再重新调度，防止多个 restart 堆积。
        restartHandler.removeCallbacks(restartRunnable)
        restartHandler.postDelayed(restartRunnable, reconnectDelayMs)
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(5_000L)
    }

    private fun refreshDisplayText() {
        val confirmed = confirmedText.toString()
        val partial = currentPartial
        val display = if (partial.isNotBlank()) {
            confirmed + (if (confirmed.isNotEmpty()) "…" else "") + partial
        } else {
            confirmed
        }
        binding.tvRecognizedText.text = display
        binding.scrollView.post {
            binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun saveToDb(content: String) {
        val id = noteId
        if (id == -1L || isExiting || recordFinalized) return
        val revision = ++saveRevision
        val duration = durationSeconds
        appScope.launch {
            saveMutex.withLock {
                if (revision != saveRevision || isExiting || recordFinalized) return@withLock
                repository.updateVoiceDraftContent(id, content, duration)
            }
        }
    }

    private fun saveCurrentWithPartial() {
        val confirmed = confirmedText.toString()
        val partial = currentPartial
        val sep = if (confirmed.isNotEmpty() && partial.isNotBlank()) "，" else ""
        val full = if (partial.isNotBlank()) confirmed + sep + partial else confirmed
        saveToDb(full)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = lifecycleScope.launch {
            while (isActive) {
                delay(2000)
                saveCurrentWithPartial()
            }
        }
    }

    private fun exitRecording() {
        if (isExiting) return
        isExiting = true
        engineRunning = false
        ++saveRevision
        restartHandler.removeCallbacks(restartRunnable)
        durationHandler.removeCallbacks(durationRunnable)
        heartbeatJob?.cancel()
        binding.btnCancel.isEnabled = false
        binding.btnFinish.isEnabled = false
        binding.tvStatus.text = getString(R.string.voice_finishing)
        // Application scope survives Home/Activity destruction while the final reply settles.
        appScope.launch {
            val finalSession = speechManager.finishListening()
            val full = SpeechTextSettlement.combine(confirmedText.toString(), finalSession)
            val saved = runCatching {
                val id = draftCreation?.await() ?: noteId
                saveMutex.withLock {
                    recordFinalized = true
                    if (id != -1L) {
                        val committed = if (full.isBlank()) repository.deleteById(id)
                        else repository.finalizeVoiceRecord(id, full, durationSeconds, null)
                        check(committed) { "Voice note commit failed" }
                    }
                }
            }.isSuccess
            runOnUiThread {
                abandonAudioFocus()
                if (saved) {
                    if (speechManager.finalResultTimedOut && full.isNotBlank()) {
                        Toast.makeText(this@RecordingActivity, R.string.voice_final_timeout, Toast.LENGTH_LONG).show()
                    }
                    finish()
                } else {
                    // Keep the draft and the visible text if persistence fails.
                    currentPartial = finalSession
                    refreshDisplayText()
                    binding.tvStatus.text = getString(R.string.voice_save_failed)
                    Toast.makeText(this@RecordingActivity, R.string.voice_save_failed, Toast.LENGTH_LONG).show()
                    recordFinalized = false
                    isExiting = false
                    binding.btnCancel.isEnabled = true
                    binding.btnFinish.isEnabled = true
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isExiting && recordingStarted) {
            // 离开录音场景即保存并转正；无文字时删除空占位。
            exitRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        restartHandler.removeCallbacks(restartRunnable)
        durationHandler.removeCallbacks(durationRunnable)
        heartbeatJob?.cancel()
        if (!isExiting) speechManager.destroy()
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun updateDurationDisplay() {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        binding.tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun showPermissionDeniedAndFinish() {
        AlertDialog.Builder(this)
            .setTitle("需要录音权限")
            .setMessage("请在系统设置中授予录音权限后再使用语音备忘功能。")
            .setPositiveButton("确定") { _, _ -> finish() }
            .show()
    }
}
