package uk.co.traynor.privategallery.novaos2

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Test data only. Collects the real, compositor-rendered, non-sensitive fixture screen. */
internal fun captureNovaSystem(name: String, compose: AndroidComposeTestRule<*, ComponentActivity>) {
    require(Regex("[a-z0-9-]{1,70}").matches(name))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    check(instrumentation.targetContext.packageName.endsWith(".hiddenprototype"))
    val committed = CountDownLatch(1)
    compose.runOnIdle {
        val view = compose.activity.window.decorView
        check(view.isHardwareAccelerated)
        view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
        view.invalidate()
    }
    check(committed.await(5, TimeUnit.SECONDS)) { "Fixture never committed its rendered window" }
    val directory = File(instrumentation.targetContext.cacheDir, "novaos2-captures").apply { mkdirs() }
    val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
    try {
        File(directory, "$name.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally { screenshot.recycle() }
}
