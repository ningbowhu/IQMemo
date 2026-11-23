package com.example.iqmemo.ui.note

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.recyclerview.widget.RecyclerView
import com.example.iqmemo.MainActivity
import com.example.iqmemo.R
import com.example.iqmemo.data.database.*
import com.example.iqmemo.databinding.*
import com.example.iqmemo.ui.notebook.NotebookEditActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UnifiedSearchInteractionTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = NoteDatabase.getInstance(app)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (check()) return
            Thread.sleep(80)
        }
        assertTrue("Condition did not settle", check())
    }
    private inline fun <reified T : Activity> current(): T? {
        var found: T? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<T>().firstOrNull()
        }
        return found
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(app.getExternalFilesDir(null), "review-5.5.1").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun mainBinding(activity: MainActivity) = ActivityMainBinding.bind(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
    private fun toolbar(a: MainActivity): Toolbar = a.findViewById(R.id.toolbar)
    private fun resultCount(main: ActivityScenario<MainActivity>): Int {
        var count = -1
        main.onActivity { a ->
            val view = a.supportFragmentManager.findFragmentByTag("search")?.view
            count = view?.findViewById<RecyclerView>(R.id.recyclerView)?.adapter?.itemCount ?: -1
        }
        return count
    }
    private fun typeQuery(value: String) {
        val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
        val input = root.findAccessibilityNodeInfosByViewId(app.packageName + ":id/search_src_text").first()
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
    }
    private fun clickResult(text: String) {
        val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
        var node = root.findAccessibilityNodeInfosByText(text).first { it.text?.toString() == text }
        while (!node.isClickable) node = requireNotNull(node.parent)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test fun liveSearchBothSectionsDeepTextNavigationRestoreAndEditorScreens() = runBlocking {
        val memo = db.noteDao().insert(NoteEntity(content = "项目会议备忘：周三领取资料，下午复核预算。"))
        val page = db.notebookDao().insert(NotebookEntity(title = "出差安排", content = "第一行是背景说明。\n周三领取资料，带上会议文件。"))
        val deep = db.notebookDao().insert(NotebookEntity(title = "长文检索", content = "合成段落。".repeat(80) + "检索目标" + "后文".repeat(80)))
        val main = ActivityScenario.launch(MainActivity::class.java)
        try {
            var toolbarHeight = 0
            main.onActivity { a ->
                val b = mainBinding(a)
                toolbarHeight = b.toolbar.height
                assertEquals((48 * a.resources.displayMetrics.density).toInt(), toolbarHeight)
                assertTrue(b.toolbar.menu.findItem(R.id.action_search).isVisible)
            }
            capture("01-memos-search-icon")
            main.onActivity { mainBinding(it).sectionTabs.check(R.id.tabNotebooks) }
            capture("02-notebooks-search-icon")
            main.onActivity { a -> toolbar(a).menu.findItem(R.id.action_search).expandActionView() }
            waitFor { runCatching { typeQuery("领取资料") }.isSuccess }
            waitFor { resultCount(main) == 2 }
            main.onActivity { a -> assertEquals(toolbarHeight, toolbar(a).height) }
            capture("03-live-search-both-sections")
            main.recreate()
            waitFor { resultCount(main) == 2 }
            main.onActivity { a ->
                assertEquals("领取资料", (toolbar(a).menu.findItem(R.id.action_search).actionView as SearchView).query.toString())
            }
            clickResult("出差安排")
            waitFor { current<NotebookEditActivity>() != null }
            val notebook = requireNotNull(current<NotebookEditActivity>())
            waitFor {
                var ready = false
                instrumentation.runOnMainSync { ready = ActivityNotebookEditBinding.bind(notebook.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)).etContent.isEnabled }
                ready
            }
            instrumentation.runOnMainSync {
                val b = ActivityNotebookEditBinding.bind(notebook.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                assertEquals("出差安排", b.etTitle.text.toString())
                assertEquals(View.VISIBLE, b.deleteBar.visibility)
            }
            capture("04-notebook-search-result-view")
            instrumentation.runOnMainSync {
                val b = ActivityNotebookEditBinding.bind(notebook.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                b.etContent.performClick()
                assertEquals(View.GONE, b.deleteBar.visibility)
            }
            waitFor { var ime = false; instrumentation.runOnMainSync { ime = ViewCompat.getRootWindowInsets(notebook.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }; ime }
            capture("05-notebook-edit-no-delete")
            instrumentation.runOnMainSync { notebook.finish() }
            waitFor { current<MainActivity>() != null }
            waitFor { resultCount(main) == 2 }
            clickResult("项目会议备忘：周三领取资料，下午复核预算。")
            waitFor { current<NoteEditActivity>() != null }
            val editor = requireNotNull(current<NoteEditActivity>())
            waitFor { var ready = false; instrumentation.runOnMainSync { ready = ActivityNoteEditBinding.bind(editor.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)).etContent.isEnabled }; ready }
            capture("06-memo-search-result-view")
            instrumentation.runOnMainSync {
                val b = ActivityNoteEditBinding.bind(editor.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                b.etContent.performClick()
                assertEquals(View.GONE, b.deleteBar.visibility)
                b.etContent.append(" 已补充。")
            }
            waitFor { var ime = false; instrumentation.runOnMainSync { ime = ViewCompat.getRootWindowInsets(editor.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }; ime }
            capture("07-memo-edit-no-delete")
            waitFor { runBlocking { db.noteDao().getNoteById(memo)?.content?.endsWith("已补充。") == true } }
            instrumentation.runOnMainSync { ViewCompat.getWindowInsetsController(editor.window.decorView)?.hide(WindowInsetsCompat.Type.ime()) }
            Thread.sleep(400)
            instrumentation.runOnMainSync {
                val b = ActivityNoteEditBinding.bind(editor.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                assertEquals(View.GONE, b.deleteBar.visibility)
                editor.finish()
            }
            waitFor { current<MainActivity>() != null }
            main.onActivity { a -> (toolbar(a).menu.findItem(R.id.action_search).actionView as SearchView).requestFocus() }
            typeQuery("检索目标")
            waitFor { resultCount(main) == 1 }
            capture("08-deep-body-match")
            typeQuery("没有这段内容")
            waitFor { resultCount(main) == 0 }
            capture("09-search-no-results")
            typeQuery("")
            waitFor { resultCount(main) == 0 }
            capture("10-search-cleared")
            main.onActivity { a -> toolbar(a).menu.findItem(R.id.action_search).collapseActionView() }
            main.onActivity { a ->
                val b = mainBinding(a)
                assertEquals(R.id.tabNotebooks, b.sectionTabs.checkedButtonId)
                b.btnNewNotebook.performClick()
            }
            waitFor { current<NotebookEditActivity>() != null }
            val newPage = requireNotNull(current<NotebookEditActivity>())
            waitFor { var ready=false; instrumentation.runOnMainSync {ready=ActivityNotebookEditBinding.bind(newPage.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)).etContent.isEnabled};ready }
            instrumentation.runOnMainSync {
                val b = ActivityNotebookEditBinding.bind(newPage.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                assertEquals("标题", b.etTitle.hint.toString())
                assertEquals(View.GONE, b.deleteBar.visibility)
            }
            capture("11-new-notebook-title")
            instrumentation.runOnMainSync { newPage.finish() }
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().filter { it !is MainActivity }.forEach { it.finish() }
            }
            main.close()
            db.noteDao().deleteById(memo)
            db.notebookDao().delete(listOf(page, deep))
            // Keep the synthetic public backup consistent after fixture cleanup.
            com.example.iqmemo.data.backup.BackupManager.backupCurrent(app)
        }
    }
}
