package com.example.iqmemo.ui.note

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iqmemo.MainActivity
import com.example.iqmemo.R
import com.example.iqmemo.databinding.ActivityMainBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabsGestureTest {
    @Test fun horizontalTouchSwitchesTabsAndVerticalScrollDoesNot() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val main=ActivityScenario.launch(MainActivity::class.java)
        fun check(id:Int) {main.onActivity { activity ->
            val binding=ActivityMainBinding.bind(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
            assertEquals(id,binding.sectionTabs.checkedButtonId)
        }}
        fun swipe(x1:Float,y1:Float,x2:Float,y2:Float) {
            val down=SystemClock.uptimeMillis()
            for(i in 0..12) {
                val action=when(i) {0->MotionEvent.ACTION_DOWN;12->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE}
                val event=MotionEvent.obtain(down,down+i*20L,action,x1+(x2-x1)*i/12,y1+(y2-y1)*i/12,0)
                instrumentation.uiAutomation.injectInputEvent(event,true)
                event.recycle()
            }
            instrumentation.waitForIdleSync()
        }
        try {
            var width=0;var height=0
            main.onActivity {width=it.resources.displayMetrics.widthPixels;height=it.resources.displayMetrics.heightPixels}
            check(R.id.tabMemos)
            swipe(width*.82f,height*.45f,width*.18f,height*.45f)
            check(R.id.tabNotebooks)
            swipe(width*.5f,height*.65f,width*.5f,height*.32f)
            check(R.id.tabNotebooks)
            swipe(width*.18f,height*.45f,width*.82f,height*.45f)
            check(R.id.tabMemos)
        } finally {main.close()}
    }
}
