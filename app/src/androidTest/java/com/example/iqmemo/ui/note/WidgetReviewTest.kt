package com.example.iqmemo.ui.note

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iqmemo.MainActivity
import com.example.iqmemo.widget.MemoWidget
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only the isolated emulator preview: exercise Android's real launcher pin dialog. */
@RunWith(AndroidJUnit4::class)
class WidgetReviewTest {
    @Test fun requestRealLauncherCardForReview() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val main=ActivityScenario.launch(MainActivity::class.java)
        try {
            main.onActivity {
                val manager=AppWidgetManager.getInstance(app)
                assertTrue(manager.isRequestPinAppWidgetSupported)
                assertTrue(manager.requestPinAppWidget(ComponentName(app,MemoWidget::class.java),null,null))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            ReviewCapture.save("16-widget-add-dialog")
        } finally {main.close()}
    }
}
