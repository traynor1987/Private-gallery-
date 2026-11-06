package uk.co.traynor.privategallery.ui

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

/** Isolated native-window checks. Main/App Mode/retained WebView coverage lives in integration tests. */
class NovaBlackScreen61Test {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 5_000
        var met = false
        while (!met && SystemClock.uptimeMillis() < until) {
            instrumentation.waitForIdleSync()
            main { met = condition() }
            if (!met) SystemClock.sleep(20)
        }
        assertTrue(message, met)
    }

    private class Fixture(val activity: ComponentActivity) {
        var clicks = 0
        var scrolls = 0
        var authorized = true
        val content = FrameLayout(activity).apply { setBackgroundColor(Color.MAGENTA) }
        val button = Button(activity).apply {
            text = "Underlying page action"
            isFocusableInTouchMode = true
            setOnClickListener { clicks++ }
            setOnGenericMotionListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_SCROLL) scrolls++; true }
        }
        val controller: NovaBlackScreenController
        init {
            content.addView(button, FrameLayout.LayoutParams(-1, -1))
            activity.setContentView(content)
            content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            button.requestFocus()
            controller = NovaBlackScreenController(activity)
        }
        fun cover(): View? = (activity.window.decorView as ViewGroup).let { decor ->
            (0 until decor.childCount).map { decor.getChildAt(it) }
                .firstOrNull { it.contentDescription == "Wake NovaOS" }
        }
        fun accessibilityGate(): View {
            var view: View = content
            while (view.parent is View && view.parent !== activity.window.decorView) view = view.parent as View
            return view
        }
        fun request() = controller.request { authorized }
    }

    private fun withFixture(test: (ActivityScenario<ComponentActivity>, Fixture) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var fixture: Fixture
            scenario.onActivity { fixture = Fixture(it) }
            try {
                await("Original Activity window receives focus") { fixture.activity.window.decorView.hasWindowFocus() }
                test(scenario, fixture)
            } finally { main { fixture.controller.close() } }
        }
    }

    @Test fun fullWindowPureBlackPixelsAndExactBrightnessBarAccessibilityFocusRestore() = withFixture { _, f ->
        for (brightness in listOf(-1f, 0.43f)) {
            main {
                f.activity.window.attributes = f.activity.window.attributes.apply { screenBrightness = brightness }
                val bars = WindowCompat.getInsetsController(f.activity.window, f.activity.window.decorView)
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                bars.show(WindowInsetsCompat.Type.statusBars())
                bars.hide(WindowInsetsCompat.Type.navigationBars())
                f.button.requestFocus()
            }
            await("Actual mixed system-bar state is established") {
                val insets = f.activity.window.decorView.rootWindowInsets
                insets != null && insets.isVisible(WindowInsets.Type.statusBars()) && !insets.isVisible(WindowInsets.Type.navigationBars())
            }
            lateinit var oldFocus: View
            var oldImportance = 0
            main {
                oldFocus = checkNotNull(f.activity.currentFocus)
                oldImportance = f.accessibilityGate().importantForAccessibility
                assertTrue(f.request())
                assertTrue(f.controller.active)
                assertEquals(0f, f.activity.window.attributes.screenBrightness, 0f)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, f.accessibilityGate().importantForAccessibility)
                assertTrue(f.cover()!!.keepScreenOn)
            }
            await("Cover fills decor and both bars are hidden") {
                val decor = f.activity.window.decorView
                val cover = f.cover()
                cover != null && cover.width == decor.width && cover.height == decor.height && cover.left == 0 && cover.top == 0 &&
                    decor.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) == false &&
                    decor.rootWindowInsets?.isVisible(WindowInsets.Type.navigationBars()) == false
            }
            val black = capture(f.activity)
            try {
                for (y in 0 until black.height) for (x in 0 until black.width) {
                    assertEquals("Opaque black window pixel $x,$y", Color.BLACK, black.getPixel(x, y))
                }
                saveBlackEvidence(f, black, "black-${if (brightness < 0) "default" else "custom"}")
            } finally { black.recycle() }
            main {
                val cover = f.cover()!!
                val node = cover.createAccessibilityNodeInfo()
                assertEquals("Wake NovaOS", node.contentDescription)
                assertTrue(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label == "Wake NovaOS" })
                assertTrue(cover.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                assertFalse(f.controller.active)
                assertFalse(cover.keepScreenOn)
                assertNull(f.cover())
                assertEquals(brightness, f.activity.window.attributes.screenBrightness, 0f)
                assertEquals(oldImportance, f.accessibilityGate().importantForAccessibility)
                assertSame(oldFocus, f.activity.currentFocus)
                assertEquals(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
                    WindowCompat.getInsetsController(f.activity.window, f.activity.window.decorView).systemBarsBehavior)
            }
            await("Exact mixed visibility is restored") {
                val insets = f.activity.window.decorView.rootWindowInsets
                insets.isVisible(WindowInsets.Type.statusBars()) && !insets.isVisible(WindowInsets.Type.navigationBars())
            }
        }
    }

    @Test fun visibleRealImeIsHiddenBeforeBlackScreenOwnsTouchesAndIsNotRestored() = withFixture { _, f ->
        lateinit var editor: EditText
        main {
            editor = EditText(f.activity).apply {
                hint = "Synthetic IME fixture"
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                setBackgroundColor(Color.MAGENTA)
            }
            f.content.addView(editor, FrameLayout.LayoutParams(-1, 180))
            assertTrue(editor.requestFocus())
            WindowCompat.getInsetsController(f.activity.window, f.activity.window.decorView).show(WindowInsetsCompat.Type.ime())
        }
        await("Real Android IME must actually be visible before activation") {
            editor.hasFocus() && f.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        }
        saveGlobalImeEvidence(f, "global-before-ime", expectedImeVisible = true)
        main { assertTrue(f.request()) }
        await("Black Screen hides the actual IME and fills the original Activity window") {
            val decor = f.activity.window.decorView
            val cover = f.cover()
            decor.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false &&
                decor.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) == false &&
                decor.rootWindowInsets?.isVisible(WindowInsets.Type.navigationBars()) == false &&
                cover != null && cover.width == decor.width && cover.height == decor.height
        }
        saveGlobalImeEvidence(f, "global-after-ime", expectedImeVisible = false)
        tap(f)
        main {
            assertTrue(f.controller.active)
            assertFalse(editor.hasFocus())
            assertEquals("Cover touch cannot type into synthetic EditText", "", editor.text.toString())
            assertEquals("Cover touch cannot click underlying action", 0, f.clicks)
            assertTrue(f.cover()!!.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            assertFalse(f.controller.active)
        }
        await("Wake does not automatically restore the IME") {
            f.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false
        }
    }

    @Test fun singleAndMultitouchNeverClickUnderlayAndDoubleTapWakes() = withFixture { _, f ->
        main { assertTrue(f.request()) }
        await("Cover is laid out") { (f.cover()?.width ?: 0) > 0 }
        tap(f)
        mouseWheel(f)
        main { assertTrue(f.controller.active); assertEquals(0, f.clicks); assertEquals("Mouse wheel cannot reach the underlay", 0, f.scrolls) }
        // Wait beyond the platform double-tap timeout before a distinct two-tap gesture.
        SystemClock.sleep(android.view.ViewConfiguration.getDoubleTapTimeout().toLong() + 30)
        multiTouch(f)
        main { assertEquals(0, f.clicks); assertTrue(f.controller.active) }
        tap(f)
        SystemClock.sleep(70)
        tap(f) { main { assertTrue("Double tap keeps the cover until its second UP", f.controller.active) } }
        await("Double tap wakes") { !f.controller.active }
        main { assertEquals("Wake gesture tail does not click page", 0, f.clicks) }
    }

    @Test fun backEscapeAndEnterWakeWithoutUnderlyingActions() = withFixture { _, f ->
        for (key in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_ENTER)) {
            main { assertTrue(f.request()) }
            await("Cover owns focus") { f.cover()?.hasFocus() == true }
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(KeyEvent(action, key), true))
            }
            await("Wake key releases cover") { !f.controller.active }
            main { assertEquals(0, f.clicks) }
        }
    }

    @Test fun dialogFocusLossRestoresAndPendingRequestActivatesOnlyAfterOriginalFocusReturns() = withFixture { _, f ->
        lateinit var dialog: Dialog
        main {
            f.activity.window.attributes = f.activity.window.attributes.apply { screenBrightness = 0.37f }
            assertTrue(f.request())
            dialog = Dialog(f.activity).apply { setContentView(Button(f.activity).apply { text = "Native menu fixture" }); show() }
        }
        try {
            await("Foreign window focus cancels active presentation") { !f.controller.active && !f.activity.window.decorView.hasWindowFocus() }
            main {
                assertEquals(0.37f, f.activity.window.attributes.screenBrightness, 0f)
                assertTrue(f.request())
                assertFalse(f.controller.active)
                assertNull(f.cover())
                dialog.dismiss()
            }
            await("Pending request activates on actual original-window focus") { f.controller.active }
            main { f.controller.dismiss(); dialog.show() }
            await("Dialog owns focus again") { !f.activity.window.decorView.hasWindowFocus() }
            main { assertTrue(f.request()); f.authorized = false; dialog.dismiss() }
            await("Original window regains focus") { f.activity.window.decorView.hasWindowFocus() }
            main { assertFalse(f.controller.active); assertNull(f.cover()); assertEquals(0.37f, f.activity.window.attributes.screenBrightness, 0f) }
        } finally { main { dialog.dismiss() } }
    }

    @Test fun pauseAndRecreationCannotCarryActivationForward() = withFixture { scenario, f ->
        main { assertTrue(f.request()) }
        scenario.moveToState(Lifecycle.State.CREATED)
        main { assertFalse(f.controller.active); assertNull(f.cover()); assertEquals(-1f, f.activity.window.attributes.screenBrightness, 0f) }
        scenario.moveToState(Lifecycle.State.RESUMED)
        await("Focus returns after resume") { f.activity.window.decorView.hasWindowFocus() }
        main { assertFalse(f.controller.active); assertTrue(f.request()) }
        scenario.recreate()
        main { assertFalse(f.controller.active); assertFalse(f.request()) }
        scenario.onActivity { replacement ->
            assertEquals(-1f, replacement.window.attributes.screenBrightness, 0f)
            assertNull(replacement.window.decorView.findViewWithTag<View>("nova-black-screen"))
        }
    }

    @Test fun revokedAccessibilityAndTouchNeverRestoreUnderlyingFocus() = withFixture { _, f ->
        main {
            assertTrue(f.request())
            val detached = checkNotNull(f.cover())
            f.authorized = false
            assertTrue(detached.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            assertTrue("Revoked content stays covered until privacy owner dismisses", f.controller.active)
            assertNotNull(f.cover())
            assertFalse(f.cover()!!.keepScreenOn)
            assertFalse(f.button.hasFocus())
            assertEquals(-1f, f.activity.window.attributes.screenBrightness, 0f)
            assertFalse(f.request())
            f.controller.dismiss()
            assertFalse(f.controller.active)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, f.content.importantForAccessibility)
            f.authorized = true
            assertTrue(f.request())
            val fresh = checkNotNull(f.cover())
            assertNotSame(detached, fresh)
            assertFalse(detached.isAttachedToWindow)
            assertTrue(detached.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            assertTrue("Detached accessibility callback cannot wake the new activation", f.controller.active)
            assertTrue(detached.performClick())
            assertTrue("Detached click cannot wake the new activation", f.controller.active)
            for (key in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK)) {
                detached.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                detached.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                assertTrue("Detached key callback cannot wake the new activation", f.controller.active)
            }
            assertSame(fresh, f.cover())
            assertTrue(fresh.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            assertFalse("Fresh cover retains its own wake authority", f.controller.active)
            assertTrue(f.request())
            f.authorized = false
        }
        tap(f)
        main { assertTrue(f.controller.active); assertEquals(0, f.clicks); assertFalse(f.button.hasFocus()); f.controller.dismiss() }
    }

    @Test fun eventDrivenRevocationHoldsCoverAndSecureCallbackCanDismissReentrantly() = withFixture { _, f ->
        lateinit var controller: NovaBlackScreenController
        lateinit var privacy: View
        var revocations = 0
        main {
            f.controller.close()
            val priorDecorImportance = f.activity.window.decorView.importantForAccessibility
            controller = NovaBlackScreenController(f.activity) {
                revocations++
                f.activity.window.decorView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                privacy = View(f.activity).apply { setBackgroundColor(Color.BLACK); isClickable = true }
                (f.activity.window.decorView as ViewGroup).addView(privacy, ViewGroup.LayoutParams(-1, -1))
                controller.dismiss()
            }
            assertTrue(controller.request { f.authorized })
            f.authorized = false
            controller.refreshAuthority()
            assertEquals(1, revocations)
            assertFalse(controller.active)
            assertSame(privacy, (f.activity.window.decorView as ViewGroup).let { it.getChildAt(it.childCount - 1) })
            assertEquals(-1f, f.activity.window.attributes.screenBrightness, 0f)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, f.content.importantForAccessibility)
            assertEquals("Controller does not override Main decor privacy gate", View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, f.activity.window.decorView.importantForAccessibility)
            controller.refreshAuthority()
            assertEquals(1, revocations)
            controller.close()
            (privacy.parent as ViewGroup).removeView(privacy)
            f.activity.window.decorView.importantForAccessibility = priorDecorImportance
        }
    }

    @Test fun eventDrivenRefreshCancelsPendingAuthorityWithoutActivation() = withFixture { _, f ->
        lateinit var dialog: Dialog
        main { dialog = Dialog(f.activity).apply { setContentView(Button(f.activity)); show() } }
        try {
            await("Native menu owns focus") { !f.activity.window.decorView.hasWindowFocus() }
            main {
                assertTrue(f.request())
                f.authorized = false
                f.controller.refreshAuthority()
                dialog.dismiss()
            }
            await("Original focus returns") { f.activity.window.decorView.hasWindowFocus() }
            main { assertFalse(f.controller.active); assertNull(f.cover()) }
        } finally { main { dialog.dismiss() } }
    }

    @Test fun matchingParentCoverTracksActualWindowResize() = withFixture { _, f ->
        main { assertTrue(f.request()) }
        await("Initial cover fills window") { f.cover()?.width == f.activity.window.decorView.width }
        var width = 0
        var height = 0
        main { width = f.activity.window.decorView.width; height = f.activity.window.decorView.height; f.activity.window.setLayout(width * 3 / 4, height * 3 / 4) }
        await("Cover follows resized window") {
            val decor = f.activity.window.decorView
            val cover = f.cover()
            decor.width < width && decor.height < height && cover != null && cover.width == decor.width && cover.height == decor.height
        }
        main { assertTrue(f.controller.active); f.activity.window.setLayout(-1, -1) }
    }

    /** Only the isolated native fixture is captured: no MainActivity, WebView, auth or owner data. */
    private fun saveBlackEvidence(f: Fixture, black: Bitmap, name: String) {
        val output = evidenceDirectory(f)
        File(output, "$name.png").outputStream().use {
            assertTrue(black.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        lateinit var metadata: JSONObject
        main {
            val decor = f.activity.window.decorView
            val cover = checkNotNull(f.cover())
            val insets = checkNotNull(decor.rootWindowInsets)
            metadata = JSONObject()
                .put("fixture", "synthetic_component_magenta_underlay")
                .put("capture", "actual_activity_window_pixelcopy")
                .put("pixelCopyResult", PixelCopy.SUCCESS)
                .put("width", black.width).put("height", black.height)
                .put("allPixelsOpaqueBlack", true)
                .put("coverLeft", cover.left).put("coverTop", cover.top)
                .put("coverWidth", cover.width).put("coverHeight", cover.height)
                .put("windowWidth", decor.width).put("windowHeight", decor.height)
                .put("windowBrightness", f.activity.window.attributes.screenBrightness)
                .put("statusVisible", insets.isVisible(WindowInsets.Type.statusBars()))
                .put("navigationVisible", insets.isVisible(WindowInsets.Type.navigationBars()))
                .put("imeVisible", insets.isVisible(WindowInsets.Type.ime()))
                .put("viewKeepScreenOn", cover.keepScreenOn)
        }
        File(output, "$name.json").writeText(metadata.toString(2))
    }

    private fun evidenceDirectory(f: Fixture): File {
        val additionalOutput = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(checkNotNull(f.activity.externalMediaDirs.firstOrNull()), "additional_test_output").absolutePath
        return File(additionalOutput, "nova-black61").apply { check(isDirectory || mkdirs()) }
    }

    private fun saveGlobalImeEvidence(f: Fixture, name: String, expectedImeVisible: Boolean) {
        // Global screenshots are restricted to this empty synthetic EditText in the debug identity.
        assertEquals(ComponentActivity::class.java, f.activity.javaClass)
        assertTrue(f.activity.packageName.endsWith(".hiddenprototype"))
        lateinit var metadata: JSONObject
        main {
            val insets = checkNotNull(f.activity.window.decorView.rootWindowInsets)
            assertEquals(expectedImeVisible, insets.isVisible(WindowInsets.Type.ime()))
            metadata = JSONObject()
                .put("fixture", "synthetic_component_empty_edittext_no_suggestions")
                .put("capture", "actual_android_display_uiautomation")
                .put("imeVisible", insets.isVisible(WindowInsets.Type.ime()))
                .put("statusVisible", insets.isVisible(WindowInsets.Type.statusBars()))
                .put("navigationVisible", insets.isVisible(WindowInsets.Type.navigationBars()))
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Real display screenshot must be available for IME proof" }
        try {
            metadata.put("width", bitmap.width).put("height", bitmap.height)
            File(evidenceDirectory(f), "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(evidenceDirectory(f), "$name.json").writeText(metadata.toString(2))
        } finally { bitmap.recycle() }
    }

    private fun capture(activity: ComponentActivity): Bitmap {
        lateinit var bitmap: Bitmap
        val done = CountDownLatch(1)
        var result = -1
        main {
            bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(activity.window, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue("Real window PixelCopy completes", done.await(5, TimeUnit.SECONDS))
        assertEquals("Real window PixelCopy succeeds", PixelCopy.SUCCESS, result)
        return bitmap
    }

    private fun center(f: Fixture): Pair<Float, Float> {
        var point = 0f to 0f
        main {
            val rect = Rect(); assertTrue(f.activity.window.decorView.getGlobalVisibleRect(rect))
            point = rect.exactCenterX() to rect.exactCenterY()
        }
        return point
    }
    private fun tap(f: Fixture, afterDown: () -> Unit = {}) {
        val (x, y) = center(f); val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) afterDown()
        }
        instrumentation.waitForIdleSync()
    }
    private fun mouseWheel(f: Fixture) {
        val (x, y) = center(f); val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; setAxisValue(MotionEvent.AXIS_VSCROLL, 1f) }),
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_MOUSE, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        instrumentation.waitForIdleSync()
    }

    private fun multiTouch(f: Fixture) {
        val (x, y) = center(f); val down = SystemClock.uptimeMillis()
        for ((action, count) in listOf(MotionEvent.ACTION_DOWN to 1,
            (MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 2,
            (MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 2,
            MotionEvent.ACTION_UP to 1)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count,
                Array(count) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } },
                Array(count) { index -> MotionEvent.PointerCoords().apply { this.x = x + index * 12; this.y = y; pressure = 1f; size = 1f } },
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
}
