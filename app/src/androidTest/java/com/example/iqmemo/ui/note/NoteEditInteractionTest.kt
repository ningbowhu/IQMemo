package com.example.iqmemo.ui.note

import android.content.Context
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import com.example.iqmemo.data.database.NoteDatabase
import com.example.iqmemo.data.database.NoteEntity
import com.example.iqmemo.databinding.ActivityNoteEditBinding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditInteractionTest {
    @Test fun existingNoteOpensReadOnlyAndDeleteIsVisibleBeforeEditing() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = NoteDatabase.getInstance(context).noteDao()
        val noteId = dao.insert(NoteEntity(content = "synthetic interaction fixture"))
        var scenario: ActivityScenario<NoteEditActivity>? = null
        try {
            scenario = ActivityScenario.launch(NoteEditActivity.createIntent(context, noteId))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val binding = ActivityNoteEditBinding.bind(content.getChildAt(0))
                assertTrue(binding.etContent.isEnabled)
                assertFalse(binding.etContent.isFocusable)
                assertFalse(binding.etContent.isCursorVisible)
                assertEquals(android.view.View.VISIBLE, binding.btnDelete.visibility)
                val density = activity.resources.displayMetrics.density
                assertTrue(binding.btnDelete.width / density >= 176f)
                assertTrue(binding.btnDelete.height / density >= 52f)
                val buttonCenterX = IntArray(2).also(binding.btnDelete::getLocationOnScreen)[0] + binding.btnDelete.width / 2f
                val rootCenterX = IntArray(2).also(binding.root::getLocationOnScreen)[0] + binding.root.width / 2f
                assertTrue(kotlin.math.abs(buttonCenterX - rootCenterX) <= density * 8f)

                val windowInsets = WindowInsetsCompat.toWindowInsetsCompat(
                    activity.window.decorView.rootWindowInsets
                )
                assertFalse(windowInsets.isVisible(Type.ime()))

                binding.etContent.performClick()
                assertEquals(android.view.View.GONE, binding.btnDelete.visibility)
                assertEquals(android.view.View.GONE, binding.deleteBar.visibility)
                assertTrue(binding.etContent.isFocusable)
                assertTrue(binding.etContent.hasFocus())
                assertTrue(binding.etContent.isCursorVisible)
                assertTrue(binding.etContent.showSoftInputOnFocus)
            }
            val deadline = android.os.SystemClock.uptimeMillis() + 3000
            var keyboardVisible = false
            while (!keyboardVisible && android.os.SystemClock.uptimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val insets = WindowInsetsCompat.toWindowInsetsCompat(activity.window.decorView.rootWindowInsets)
                    keyboardVisible = insets.isVisible(Type.ime())
                }
                if (!keyboardVisible) android.os.SystemClock.sleep(100)
            }
            assertTrue("IME should open after tapping the note body", keyboardVisible)
            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val binding = ActivityNoteEditBinding.bind(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                assertEquals(android.view.View.GONE, binding.deleteBar.visibility)
                assertEquals(android.view.View.GONE, binding.btnDelete.visibility)
            }
            } finally {
                scenario?.close()
                dao.deleteById(noteId)
            }
        }
    }
}
