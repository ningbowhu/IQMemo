package com.example.iqmemo.ui.note

import android.app.Application
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iqmemo.MainActivity
import com.example.iqmemo.R
import com.example.iqmemo.data.database.*
import com.example.iqmemo.databinding.ActivityMainBinding
import com.example.iqmemo.databinding.ActivityNotebookEditBinding
import com.example.iqmemo.databinding.ActivityNoteEditBinding
import com.example.iqmemo.ui.notebook.NotebookEditActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookReviewScreensTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = NoteDatabase.getInstance(app)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun mainBinding(a: MainActivity) = ActivityMainBinding.bind(a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
    private fun editBinding(a: NotebookEditActivity) = ActivityNotebookEditBinding.bind(a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
    private fun waitFor(condition: () -> Boolean) {
        val end=android.os.SystemClock.uptimeMillis()+8000
        while(android.os.SystemClock.uptimeMillis()<end) { instrumentation.waitForIdleSync(); if(condition())return;Thread.sleep(100) }
        assertTrue(condition())
    }
    private fun clickText(text: String) {
        waitFor {
            val nodes=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text).orEmpty()
            nodes.any { node -> var target:AccessibilityNodeInfo?=node
                while(target!=null && !target.isClickable) target=target.parent
                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true }
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun captureEveryChangedScreenFromRunningApplication() = runBlocking {
        val before = db.notebookDao().all().map { it.id }.toSet()
        val memo = db.noteDao().insert(NoteEntity(content="周三前整理需求清单，确认下一步安排。"))
        val memo2 = db.noteDao().insert(NoteEntity(content="随手记录的想法，文字与语音便签都放在这里。",source="voice"))
        var main=ActivityScenario.launch(MainActivity::class.java)
        try {
            main.onActivity { assertEquals(R.id.tabMemos,mainBinding(it).sectionTabs.checkedButtonId) }
            ReviewCapture.save("01-memos-home")
            main.onActivity { mainBinding(it).tabNotebooks.performClick() }
            ReviewCapture.save("02-notebook-empty")
            main.close()

            val editor=ActivityScenario.launch<NotebookEditActivity>(NotebookEditActivity.intent(app))
            try {
                waitFor { var ready=false;editor.onActivity {ready=editBinding(it).etContent.isEnabled};ready }
                ReviewCapture.save("03-new-notebook-keyboard")
                editor.onActivity {editBinding(it).apply {
                    etTitle.setText("项目讨论记录")
                    etContent.setText("今天讨论了记事本的使用方式。\n\n标题可以不填，正文可以连续记录。内容自动保存在手机，并纳入本地备份。\n\n这里适合整理一段想法、会议记录和后续安排。")
                } }
                waitFor {runBlocking {db.notebookDao().all().any {it.title=="项目讨论记录" && it.content.contains("今天讨论")}}}
                ReviewCapture.save("04-notebook-edit-keyboard")
                editor.onActivity { ViewCompat.getWindowInsetsController(editBinding(it).etContent)?.hide(WindowInsetsCompat.Type.ime()) }
                ReviewCapture.save("05-notebook-edit-saved")
            } finally {editor.close()}
            val page=db.notebookDao().all().first {it.id !in before}
            db.notebookDao().insert(NotebookEntity(content="没有填写标题的记事\n列表会使用正文首行作为显示名称，真实标题仍然留空。"))
            main=ActivityScenario.launch(MainActivity::class.java)
            main.onActivity {mainBinding(it).tabNotebooks.performClick()}
            ReviewCapture.save("06-notebook-list")
            main.close()
            val viewer=ActivityScenario.launch<NotebookEditActivity>(NotebookEditActivity.intent(app,page.id))
            try {
                waitFor {var ready=false;viewer.onActivity {ready=editBinding(it).etContent.isEnabled};ready}
                viewer.onActivity {assertFalse(editBinding(it).etContent.isFocusable)}
                ReviewCapture.save("07-notebook-view")
                viewer.onActivity {editBinding(it).btnDelete.performClick()}
                ReviewCapture.save("08-notebook-delete-confirm")
                clickText("取消")
            } finally {viewer.close()}
            val memoViewer=ActivityScenario.launch<NoteEditActivity>(NoteEditActivity.createIntent(app,memo))
            try {
                waitFor {var ready=false;memoViewer.onActivity {activity ->
                    val binding=ActivityNoteEditBinding.bind(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                    ready=binding.etContent.isEnabled
                };ready}
                ReviewCapture.save("18-memo-view-delete")
                memoViewer.onActivity {activity ->
                    val binding=ActivityNoteEditBinding.bind(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                    binding.etContent.performClick()
                    assertEquals(View.GONE,binding.btnDelete.visibility)
                    assertEquals(View.GONE,binding.deleteBar.visibility)
                }
                ReviewCapture.save("19-memo-edit-no-delete-keyboard")
            } finally {memoViewer.close()}
            main=ActivityScenario.launch(MainActivity::class.java)
            main.onActivity {mainBinding(it).tabNotebooks.performClick()}
            Thread.sleep(300)
            main.onActivity { activity ->
                // Both fragments retain their views; choose the visible one.
                val visible=mutableListOf<RecyclerView>()
                fun walk(v:View) {if(v is RecyclerView && v.isShown)visible.add(v);if(v is ViewGroup)(0 until v.childCount).forEach{walk(v.getChildAt(it))}}
                walk(mainBinding(activity).fragmentContainer)
                requireNotNull(visible.single().findViewHolderForAdapterPosition(0)).itemView.performLongClick()
            }
            ReviewCapture.save("09-notebook-selection")
            main.onActivity {activity ->
                activity.supportFragmentManager.findFragmentByTag("notebooks")!!.requireView()
                    .findViewById<View>(R.id.btnDeleteSelected).performClick()
            }
            ReviewCapture.save("20-notebook-batch-delete-confirm")
            clickText("取消")
            // Switching away cancels selection and preserves the separate lists.
            main.onActivity {mainBinding(it).tabMemos.performClick(); mainBinding(it).tabNotebooks.performClick();it.openOptionsMenu()}
            ReviewCapture.save("10-notebook-menu")
            clickText("备份全部数据")
            waitFor { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.google.android.documentsui" }
            Thread.sleep(700)
            ReviewCapture.save("11-backup-system-picker")
            // Back first hides the system picker's keyboard; a second press closes it.
            repeat(3) {
                if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != app.packageName) {
                    instrumentation.uiAutomation.executeShellCommand("input keyevent 4").use { descriptor ->
                        java.io.FileInputStream(descriptor.fileDescriptor).readBytes()
                    }
                    Thread.sleep(500)
                }
            }
            waitFor { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == app.packageName }
            main.onActivity {it.openOptionsMenu()}
            clickText("恢复备份")
            ReviewCapture.save("12-restore-confirm")
            clickText("取消")
        } finally {
            main.close()
            if (InstrumentationRegistry.getArguments().getString("keepReviewData") != "true") {
            db.notebookDao().delete(db.notebookDao().all().filter{it.id !in before}.map{it.id})
            db.noteDao().deleteById(memo);db.noteDao().deleteById(memo2)
            }
        }
    }
}
