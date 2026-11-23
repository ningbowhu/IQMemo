package com.example.iqmemo.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One microphone producer and one paced sender; no audio files or content logs. */
class XfyunSpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String, String) -> Unit,
    private val onReadyForSpeech: (() -> Unit)? = null,
    private val onPartialResult: ((String) -> Unit)? = null,
    private val onCaptureError: ((String) -> Unit)? = null,
    private val language: SpeechLanguage = SpeechLanguage.CHINESE
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 1280 // 40 ms, PCM16 mono
        private const val GAP_BUF_CAP = 500 // bounded 20 seconds; never silently discard words
        private const val HOST = "iat-api.xfyun.cn"
        private const val PATH = "/v2/iat"
        fun isConfigured(): Boolean = XfyunConfig.isConfigured()
    }

    private val lock = Any()
    @Volatile var isListening = false
        private set
    @Volatile private var capturing = false
    @Volatile private var draining = false
    @Volatile private var captureStopped = true
    private var version = 0
    private var socket: WebSocket? = null
    private var activeSocket: WebSocket? = null
    private var firstSent = false
    private var endSent = false
    private var recorder: AudioRecord? = null
    private var producer: Thread? = null
    private var sender: Thread? = null
    private val frames = LinkedBlockingDeque<ByteArray>(GAP_BUF_CAP)
    private val accumulator = XfyunResultAccumulator()
    private var latestText = ""
    private var hasFinal = false
    private var finalWaiter: CompletableDeferred<String>? = null
    @Volatile var finalResultTimedOut = false
        private set
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    fun init() = destroy()

    fun startListening(): Boolean {
        if (capturing) return true
        synchronized(lock) {
            accumulator.clear()
            latestText = ""
            hasFinal = false
            finalWaiter = null
            finalResultTimedOut = false
            frames.clear()
            draining = false
            captureStopped = false
        }
        if (!startMic()) return false
        sender = Thread({ sendLoop() }, "iqmemo-audio-sender").also { it.start() }
        connect()
        return true
    }

    fun reconnectSession() {
        if (!capturing || draining) return
        val old = synchronized(lock) {
            ++version // invalidate old onClosed before closing its socket
            activeSocket = null
            isListening = false
            accumulator.clear()
            latestText = ""
            hasFinal = false
            socket.also { socket = null }
        }
        old?.cancel()
        connect()
    }

    /** Stop capture, flush queued PCM, then allow the service to finish its corrections. */
    suspend fun finishListening(): String {
        val waiter = synchronized(lock) {
            draining = true
            finalWaiter ?: CompletableDeferred<String>().also { finalWaiter = it }
        }
        stopMic()
        if (synchronized(lock) { hasFinal || (activeSocket == null && frames.isEmpty()) }) {
            waiter.complete(synchronized(lock) { latestText })
        }
        val reply = withTimeoutOrNull(3_000L) { waiter.await() }
        finalResultTimedOut = reply == null
        val final = reply ?: synchronized(lock) { accumulator.snapshot().ifBlank { latestText } }
        stopListening()
        return final
    }

    fun stopListening() {
        val old = synchronized(lock) {
            ++version
            activeSocket = null
            isListening = false
            draining = false
            finalWaiter?.complete(accumulator.snapshot().ifBlank { latestText })
            socket.also { socket = null }
        }
        stopMic()
        sender?.interrupt()
        sender = null
        frames.clear()
        old?.cancel()
    }

    fun destroy() = stopListening()

    private fun startMic(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) return false
        val minimum = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimum <= 0) return false
        // Let the device use its speech capture profile; fall back if unsupported.
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val audio = try {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, FRAME_SIZE * 8))
            } catch (_: Exception) { continue }
            try {
                check(audio.state == AudioRecord.STATE_INITIALIZED)
                audio.startRecording()
                check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                recorder = audio
                capturing = true
                producer = Thread({ readLoop(audio) }, "iqmemo-microphone").also { it.start() }
                return true
            } catch (_: Exception) { audio.release() }
        }
        return false
    }

    private fun readLoop(audio: AudioRecord) {
        try {
            val frame = ByteArray(FRAME_SIZE)
            var used = 0
            while (capturing) {
                val count = audio.read(frame, used, FRAME_SIZE - used, AudioRecord.READ_BLOCKING)
                if (count < 0) {
                    if (capturing) captureFailed("麦克风读取中断，已保留识别文字，请重新录入")
                    break
                }
                if (count == 0) continue
                used += count
                if (used == FRAME_SIZE) {
                    if (!frames.offerLast(frame.copyOf())) {
                        captureFailed("网络过慢，语音缓冲已满；已保留识别文字，请重新录入")
                        break
                    }
                    used = 0
                }
            }
            // Preserve the last incomplete PCM frame; discard only an incomplete sample byte.
            if (used >= 2 && !frames.offerLast(frame.copyOf(used - used % 2))) {
                captureFailed("语音尾段无法入队，已保留识别文字")
            }
        } catch (_: Exception) {
            if (capturing) captureFailed("麦克风不可用，已保留识别文字，请重新录入")
        } finally {
            // Only the producer can confirm that its final partial frame has been queued.
            captureStopped = true
        }
    }

    private fun captureFailed(message: String) {
        capturing = false
        onCaptureError?.invoke(message)
    }

    private fun stopMic() {
        capturing = false
        val audio = recorder
        try { audio?.stop() } catch (_: Exception) { }
        val thread = producer
        if (thread != Thread.currentThread()) {
            try { thread?.join(500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        try { audio?.release() } catch (_: Exception) { }
        producer = null
        recorder = null
        if (thread == null || !thread.isAlive) captureStopped = true
    }

    private fun sendLoop() {
        try {
            while (!Thread.currentThread().isInterrupted) {
                var sent = false
                var failedVersion: Int? = null
                synchronized(lock) {
                    val ws = activeSocket
                    if (ws != null && !endSent) {
                        val audio = frames.peekFirst()
                        if (audio != null) {
                            val packet = if (!firstSent) firstFrame(audio) else audioFrame(audio)
                            if (ws.send(packet)) {
                                frames.pollFirst()
                                firstSent = true
                                sent = true
                            } else {
                                failedVersion = version
                            }
                        } else if (draining && captureStopped) {
                            if (!firstSent) {
                                finalWaiter?.complete(latestText)
                            } else if (ws.send(endFrame())) {
                                endSent = true
                            } else {
                                failedVersion = version
                            }
                        }
                    }
                }
                failedVersion?.let { terminate(it, "语音发送失败，请检查网络") }
                // Backlogged startup/reconnect frames must not be burst into the ASR endpoint.
                Thread.sleep(if (sent) 40 else 10)
            }
        } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private fun connect() {
        val mine = synchronized(lock) {
            firstSent = false
            endSent = false
            ++version
        }
        val request = Request.Builder().url(authUrl()).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val accepted = synchronized(lock) {
                    if (mine != version || (!capturing && !draining)) false else {
                        activeSocket = webSocket
                        isListening = true
                        true
                    }
                }
                if (accepted) onReadyForSpeech?.invoke() else webSocket.cancel()
            }
            override fun onMessage(webSocket: WebSocket, text: String) = receive(mine, text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Never log exception messages: OkHttp errors may contain signed URLs.
                terminate(mine, "网络连接失败，请检查网络")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                terminate(mine, "语音连接中断，请重新连接")
            }
        })
        synchronized(lock) {
            if (mine == version) socket = ws else ws.cancel()
        }
    }

    private fun receive(mine: Int, message: String) {
        var partial: String? = null
        var complete = false
        var error: String? = null
        synchronized(lock) {
            if (mine != version) return
            try {
                val json = JSONObject(message)
                if (json.optInt("code", -1) != 0) {
                    val code = json.optInt("code")
                    if (code in setOf(11200, 10105, 10106, 10107, 11201)) {
                        error = "所选语音服务未开通或配置不可用（$code），请在菜单切换识别语言或检查服务"
                    } else {
                        error = "语音服务返回错误（$code），请检查服务配置或网络"
                    }
                } else {
                    val data = json.optJSONObject("data") ?: return
                    data.optJSONObject("result")?.let {
                        // Empty corrections are meaningful; do not resurrect removed partial words.
                        latestText = accumulator.apply(XfyunResultSliceParser.parse(it))
                        partial = latestText
                    }
                    complete = data.optInt("status") == 2
                }
            } catch (_: Exception) { error = "语音结果格式异常，已保留识别文字" }
        }
        partial?.let { onPartialResult?.invoke(it) }
        if (complete || error != null) terminate(mine, error)
    }

    private fun terminate(mine: Int, error: String?) {
        val outcome = synchronized(lock) {
            if (mine != version) return
            ++version // one terminal delivery; later close/failure callbacks are stale
            activeSocket = null
            isListening = false
            val text = latestText
            hasFinal = error == null
            accumulator.clear()
            val old = socket
            socket = null
            finalWaiter?.complete(text)
            Triple(text, old, error)
        }
        outcome.second?.close(1000, "session finished")
        if (outcome.third == null && outcome.first.isNotBlank()) onResult(outcome.first)
        else {
            val message = outcome.third ?: "未识别到语音内容"
            if (message.startsWith("所选语音服务")) onCaptureError?.invoke(message)
            else onError(message, outcome.first)
        }
    }

    private fun firstFrame(audio: ByteArray): String = JSONObject().apply {
        put("common", JSONObject().put("app_id", XfyunConfig.APP_ID))
        put("business", XfyunRequestOptions.business(language))
        put("data", packet(0, audio))
    }.toString()
    private fun audioFrame(audio: ByteArray) = JSONObject().put("data", packet(1, audio)).toString()
    private fun endFrame() = JSONObject().put("data", packet(2, byteArrayOf())).toString()
    private fun packet(status: Int, audio: ByteArray) = JSONObject().apply {
        put("status", status)
        put("format", "audio/L16;rate=16000")
        put("encoding", "raw")
        put("audio", Base64.encodeToString(audio, Base64.NO_WRAP))
    }
    private fun authUrl(): String {
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(XfyunConfig.API_SECRET.toByteArray(), "HmacSHA256"))
        val signature = Base64.encodeToString(mac.doFinal(
            "host: $HOST\ndate: $date\nGET $PATH HTTP/1.1".toByteArray()), Base64.NO_WRAP)
        val origin = "api_key=\"${XfyunConfig.API_KEY}\", algorithm=\"hmac-sha256\", " +
            "headers=\"host date request-line\", signature=\"$signature\""
        val auth = Base64.encodeToString(origin.toByteArray(), Base64.NO_WRAP)
        return "wss://$HOST$PATH?authorization=${java.net.URLEncoder.encode(auth, "UTF-8")}" +
            "&date=${java.net.URLEncoder.encode(date, "UTF-8")}&host=$HOST"
    }
}
