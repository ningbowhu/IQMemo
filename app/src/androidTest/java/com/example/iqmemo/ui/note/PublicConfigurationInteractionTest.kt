package com.example.iqmemo.ui.note

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iqmemo.MainActivity
import com.example.iqmemo.data.database.NoteDatabase
import com.example.iqmemo.speech.XfyunConfig
import com.example.iqmemo.ui.recording.RecordingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Public default build checks, using only synthetic data and no microphone capture. */
@RunWith(AndroidJUnit4::class)
class PublicConfigurationInteractionTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun waitForText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text).orEmpty().isNotEmpty()) return
            Thread.sleep(80)
        }
        fail("Expected public UI text: $text")
    }

    private fun clickText(text: String) {
        waitForText(text)
        val node = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(text).first()
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        assertTrue(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(400)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(app.getExternalFilesDir(null), "review-5.5.3").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Test fun menuHasNoBindingAndAboutShowsPublicVersion() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openOptionsMenu() }
            waitForText("关于速记")
            assertTrue(instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("工作台").isEmpty())
            capture("01-public-menu")
            clickText("关于速记")
            waitForText("5.5.3")
            waitForText("已移除工作台绑定和同步")
            capture("02-public-about")
        }
    }

    @Test fun unconfiguredSpeechExplainsSetupWithoutPermissionRequestOrDraft() = runBlocking {
        assertFalse("Default public artifact must not contain configured service credentials", XfyunConfig.isConfigured())
        val dao = NoteDatabase.getInstance(app).noteDao()
        val before = dao.getAllNotesAndDrafts().first().map { it.id }.toSet()
        val permissionBefore = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
        ActivityScenario.launch(RecordingActivity::class.java).use {
            waitForText("语音服务未配置")
            waitForText("开源版未内置讯飞密钥")
            capture("03-unconfigured-speech")
            clickText("确定")
        }
        assertEquals(permissionBefore, ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO))
        assertEquals(before, dao.getAllNotesAndDrafts().first().map { it.id }.toSet())
    }
}
