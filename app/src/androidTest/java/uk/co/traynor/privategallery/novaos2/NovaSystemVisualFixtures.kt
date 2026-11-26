package uk.co.traynor.privategallery.novaos2

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Test data only. Collects the real, compositor-rendered, non-sensitive fixture screen. */
internal fun captureNovaSystem(name: String) {
    require(Regex("[a-z0-9-]{1,70}").matches(name))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    check(instrumentation.targetContext.packageName.endsWith(".hiddenprototype"))
    instrumentation.waitForIdleSync()
    val directory = File(instrumentation.targetContext.cacheDir, "novaos2-captures").apply { mkdirs() }
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        File(directory, "$name.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally { screenshot.recycle() }
}
