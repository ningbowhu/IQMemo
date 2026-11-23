package com.example.iqmemo.ui.note

import android.app.Application
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iqmemo.data.database.NoteDatabase
import com.example.iqmemo.data.database.NotebookEntity
import com.example.iqmemo.databinding.ActivityNotebookEditBinding
import com.example.iqmemo.ui.notebook.NotebookEditActivity
import com.example.iqmemo.ui.notebook.NotebookEditorModel
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookInteractionTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = NoteDatabase.getInstance(app)
    @org.junit.Before fun awaitInitialRestore() = runBlocking {
        (app as com.example.iqmemo.IQMemoApplication).initialization.join()
    }
    private fun binding(activity: NotebookEditActivity) = ActivityNotebookEditBinding.bind(
        activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
    private fun waitFor(check: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis()+8000
        while (android.os.SystemClock.uptimeMillis()<deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if(check()) return
            Thread.sleep(100)
        }
        assertTrue("Condition did not settle",check())
    }
    private fun waitReady(scenario: ActivityScenario<NotebookEditActivity>) {
        waitFor { var ready=false; scenario.onActivity { ready=binding(it).etContent.isEnabled }; ready }
    }

    @Test fun rapidEditsPauseRecreateAndReopenPreserveOnePageAndOptionalTitle() = runBlocking {
        val before=db.notebookDao().all().map{it.id}.toSet()
        val scenario=ActivityScenario.launch<NotebookEditActivity>(NotebookEditActivity.intent(app))
        try {
            waitReady(scenario)
            scenario.onActivity { binding(it).apply { etTitle.setText("可选标题");etContent.setText("第一稿");etContent.setText("快速修改后的正文") } }
            scenario.recreate()
            waitReady(scenario)
            scenario.onActivity { assertEquals("可选标题",binding(it).etTitle.text.toString());assertEquals("快速修改后的正文",binding(it).etContent.text.toString()) }
            waitFor { runBlocking { db.notebookDao().all().any { it.id !in before && it.content=="快速修改后的正文" } } }
            scenario.close()
            val created=db.notebookDao().all().filter { it.id !in before }
            assertEquals(1,created.size)
            val page=created.single()
            val reopen=ActivityScenario.launch<NotebookEditActivity>(NotebookEditActivity.intent(app,page.id))
            try {
                waitReady(reopen)
                reopen.onActivity { binding(it).apply {
                    assertFalse(etContent.isFocusable)
                    assertEquals(android.view.View.VISIBLE,deleteBar.visibility)
                    etContent.performClick()
                } }
                // Let the read-only -> editable state render before synthetic typing.
                waitFor {var hidden=false;reopen.onActivity {hidden=binding(it).deleteBar.visibility==android.view.View.GONE && binding(it).etContent.isFocusable};hidden}
                reopen.onActivity { activity ->
                    binding(activity).apply {etTitle.setText("");etContent.setText("无标题正文")}
                    val state=androidx.lifecycle.ViewModelProvider(activity)[NotebookEditorModel::class.java].state.value
                    assertEquals("Immediate editor state must match synthetic typing", "无标题正文", state.content)
                }
                try {
                    waitFor { runBlocking { db.notebookDao().get(page.id)?.content=="无标题正文" } }
                } catch (error: AssertionError) {
                    var state=""
                    reopen.onActivity { activity -> state=androidx.lifecycle.ViewModelProvider(activity)[NotebookEditorModel::class.java].state.value.toString() }
                    throw AssertionError("Synthetic fixture did not persist: expectedId=${page.id}, state=$state, pages=${db.notebookDao().all()}",error)
                }
                assertEquals("",db.notebookDao().get(page.id)!!.title)
            } finally {reopen.close()}
        } finally {scenario.close();db.notebookDao().delete(db.notebookDao().all().filter{it.id !in before}.map{it.id})}
    }

    @Test fun saveFailureAllowsExitAndRecoversDraftForRetry() = runBlocking {
        val id=db.notebookDao().insert(NotebookEntity(title="旧标题",content="旧正文"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER notebook_editor_abort BEFORE UPDATE ON notebooks BEGIN SELECT RAISE(ABORT,'synthetic failure'); END")
        var scenario: ActivityScenario<NotebookEditActivity>?=null
        try {
            scenario=ActivityScenario.launch(NotebookEditActivity.intent(app,id))
            waitReady(scenario)
            scenario.onActivity { binding(it).apply { etContent.performClick();etTitle.setText("故障后标题");etContent.setText("故障后草稿") } }
            waitFor { var failed=false;scenario!!.onActivity {failed=binding(it).tvError.text.toString().contains("保存失败")};failed }
            assertEquals("旧正文",db.notebookDao().get(id)!!.content)
            ReviewCapture.save("14-save-failure")
            scenario.close()
            // Finishing must be possible even when the write continues to fail.
            Thread.sleep(300)
            scenario=ActivityScenario.launch(NotebookEditActivity.intent(app,id))
            waitReady(scenario)
            scenario.onActivity {
                assertEquals("故障后草稿",binding(it).etContent.text.toString())
                assertFalse(binding(it).etContent.isFocusable)
                assertEquals(android.view.View.VISIBLE,binding(it).deleteBar.visibility)
            }
            ReviewCapture.save("15-draft-recovery")
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER notebook_editor_abort")
            scenario.onActivity { binding(it).tvError.performClick() }
            waitFor { runBlocking { db.notebookDao().get(id)?.content=="故障后草稿" } }
            assertEquals("故障后标题",db.notebookDao().get(id)!!.title)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS notebook_editor_abort")
            scenario?.close();db.notebookDao().delete(listOf(id))
        }
    }

    @Test fun reopeningSupersedesOlderPendingEditorAndKeepsOnlyNewestDraft() = runBlocking {
        val id=db.notebookDao().insert(NotebookEntity(title="原标题",content="原正文"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        lateinit var older:NotebookEditorModel
        lateinit var newer:NotebookEditorModel
        try {
            instrumentation.runOnMainSync {older=NotebookEditorModel(app);older.load(id)}
            waitFor {older.state.value.ready}
            instrumentation.runOnMainSync {
                older.edit();older.change("旧会话", "旧会话待保存")
                newer=NotebookEditorModel(app);newer.load(id)
            }
            waitFor {newer.state.value.ready}
            instrumentation.runOnMainSync {
                newer.edit();newer.change("新会话", "新会话最新正文")
                older.flush();newer.flush()
            }
            waitFor {runBlocking {db.notebookDao().get(id)?.content=="新会话最新正文"}}
            Thread.sleep(800)
            assertEquals("新会话最新正文",db.notebookDao().get(id)!!.content)
            assertEquals("新会话",db.notebookDao().get(id)!!.title)
        } finally {db.notebookDao().delete(listOf(id))}
    }

    @Test fun deleteDialogSplitsActionsEquallyCancelPreservesAndConfirmDeletes() = runBlocking {
        val id=db.notebookDao().insert(NotebookEntity(title="删除布局测试",content="合成正文"))
        val scenario=ActivityScenario.launch<NotebookEditActivity>(NotebookEditActivity.intent(app,id))
        fun action(text:String):AccessibilityNodeInfo {
            val root=InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            return requireNotNull(root).findAccessibilityNodeInfosByText(text).first {it.text?.toString()==text && it.isClickable}
        }
        try {
            waitReady(scenario)
            scenario.onActivity {binding(it).btnDelete.performClick()}
            waitFor {runCatching{action("取消")}.isSuccess}
            val cancel=Rect().also {action("取消").getBoundsInScreen(it)}
            val delete=Rect().also {action("删除").getBoundsInScreen(it)}
            assertTrue(cancel.centerX()<delete.centerX())
            assertTrue(kotlin.math.abs(cancel.width()-delete.width())<=1)
            assertTrue(cancel.width()>200)
            action("取消").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            assertNotNull(db.notebookDao().get(id))
            scenario.onActivity {binding(it).btnDelete.performClick()}
            waitFor {runCatching{action("删除")}.isSuccess}
            action("删除").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor {runBlocking {db.notebookDao().get(id)==null}}
            Thread.sleep(300)
            assertNull(db.notebookDao().get(id))
        } finally {scenario.close();db.notebookDao().delete(listOf(id))}
    }
}
