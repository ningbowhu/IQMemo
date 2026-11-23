package com.example.iqmemo.ui.note

import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

internal object ReviewCapture {
    fun save(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(400)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "review-${instrumentation.targetContext.packageManager.getPackageInfo(instrumentation.targetContext.packageName, 0).versionName}")
        folder.mkdirs()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
