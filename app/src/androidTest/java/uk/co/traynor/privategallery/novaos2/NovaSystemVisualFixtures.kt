package uk.co.traynor.privategallery.novaos2

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Test data only. Collects the real, compositor-rendered, non-sensitive fixture screen. */
internal fun captureNovaSystem(name: String, gestureContrast: ((Bitmap) -> Double)? = null) {
    require(Regex("[a-z0-9-]{1,70}").matches(name))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    check(instrumentation.targetContext.packageName.endsWith(".hiddenprototype"))
    instrumentation.waitForIdleSync()
    val directory = File(instrumentation.targetContext.cacheDir, "novaos2-captures").apply { mkdirs() }
    var screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        // SystemUI samples and tints the native gesture handle asynchronously.
        val deadline = SystemClock.elapsedRealtime() + 3_000
        var contrast = gestureContrast?.invoke(screenshot)
        while (contrast != null && contrast < 3.0 && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
            val next = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            screenshot.recycle()
            screenshot = next
            contrast = gestureContrast?.invoke(screenshot)
        }
        File(directory, "$name.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        check(contrast == null || contrast >= 3.0) { "Gesture handle contrast for $name was $contrast, expected at least 3:1" }
        if (contrast != null) println("NovaOS gesture handle contrast $name=$contrast")
    } finally { screenshot.recycle() }
}
