package uk.co.traynor.privategallery.ui

import android.graphics.Point
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import uk.co.traynor.privategallery.core.browser.v2.*
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog

class NovaWebScroll67Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun webViewScrollsAfterSwitchingRetainedAppsAndDismissingKeyboard() {
        lateinit var session: BrowserV2Session
        lateinit var first: WebView
        val touchTrace = ArrayDeque<String>()
        var id by mutableStateOf("")
        var active by mutableStateOf<WebView?>(null)
        fun show(name: String) {
            session.openRetainedPrivateApp(PrivateAppCatalog.App(UUID.randomUUID().toString(),
                name, "https://scroll-fixture.invalid/$name", "", "standalone", "", false, false, false))
            id = session.tabs.activeTab.id
            active = session.activeWebView()
        }
        compose.runOnIdle {
            session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener,
                webViewFactory = BrowserV2WebViewFactory { context, _, _, _ ->
                    object : WebView(context) {
                        override fun loadUrl(url: String) = Unit
                        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                            val handled = super.onTouchEvent(event)
                            if (touchTrace.size == 48) touchTrace.removeFirst()
                            touchTrace.addLast("renderer=${System.identityHashCode(this)} action=${event.actionMasked} x=${event.x.toInt()} y=${event.y.toInt()} scroll=$scrollX,$scrollY viewport=${width}x$height handled=$handled")
                            return handled
                        }
                    }.apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
                        settings.javaScriptEnabled = true
                    }
                })
            show("first")
            first = checkNotNull(active)
            first.loadDataWithBaseURL("https://scroll-fixture.invalid/", """
                <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>
                  body { margin:0; overflow-anchor:none }
                  main { position:relative; height:9000px; width:3000px; background:linear-gradient(#ddd,#246) }
                  input { position:absolute; left:180px; top:1600px; height:64px; width:160px; font-size:18px }
                </style>
                <main><input aria-label="Scroll fixture editor"></main>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        val firstId = id
        compose.setContent {
            RetainedPrivateAppWebViews(session, id, active, covered = false,
                modifier = androidx.compose.ui.Modifier.imePadding())
        }
        try {
            val host = compose.onNodeWithTag("browser-v2-webview-host")
            compose.waitUntil(10_000) { compose.runOnIdle { first.canScrollVertically(1) } }
            host.performTouchInput {
                down(center)
                val destination = center.copy(y = centerY / 2)
                for (step in 1..6) {
                    moveTo(0, center.copy(y = centerY - (centerY - destination.y) * step / 6), delayMillis = 35)
                }
                // End at rest so a fling cannot move the editor after the app switch.
                moveTo(0, destination, delayMillis = 250)
                up()
            }
            compose.waitUntil(5_000) { compose.runOnIdle { first.scrollY > 0 } }
            val parent = compose.runOnIdle { first.parent }
            compose.runOnIdle { show("second") }
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame(parent, first.parent)
                assertFalse(first.isEnabled)
                val bounds = IntArray(2)
                first.getLocationInWindow(bounds)
                assertTrue("The inactive renderer must stay out of the page touch area", bounds[0] + first.width <= 0)
                session.selectFromNovaHome(firstId)
                id = firstId
                active = session.activeWebView()
            }
            compose.waitUntil(5_000) {
                compose.runOnIdle {
                    first.hasWindowFocus() && first.isEnabled && first.isFocusableInTouchMode &&
                        first.getGlobalVisibleRect(Rect())
                }
            }
            // Keyboard geometry must be independent of the preceding gesture's fling and
            // the retained visual viewport's offset. Prepare the fixture without focusing it.
            val positioned = AtomicBoolean(false)
            compose.runOnIdle {
                assertSame(first, active)
                assertTrue("The retained page must still be scrolled", first.scrollY > 0)
                first.evaluateJavascript(
                    "document.querySelector('input').scrollIntoView({block:'center',inline:'nearest',behavior:'instant'})",
                ) { positioned.set(true) }
            }
            compose.waitUntil(5_000) { positioned.get() }
            val editor = AtomicReference<Point>()
            compose.runOnIdle {
                assertSame(first, active)
                first.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        first.evaluateJavascript("""
                            (() => {
                                const viewport = window.visualViewport;
                                const scale = devicePixelRatio * viewport.scale;
                                for (const input of document.querySelectorAll('input')) {
                                    const rect = input.getBoundingClientRect();
                                    const x = rect.left + rect.width / 2;
                                    const y = rect.top + rect.height / 2;
                                    const visualX = x - viewport.offsetLeft;
                                    const visualY = y - viewport.offsetTop;
                                    if (visualX < 24 || visualX > viewport.width - 24 ||
                                        visualY < 96 || visualY > viewport.height - 96) continue;
                                    if (document.elementFromPoint(x, y) !== input) continue;
                                    return [visualX * scale, visualY * scale];
                                }
                                return [null, null, viewport.pageTop, viewport.offsetTop, viewport.height];
                            })()
                        """.trimIndent()) { result ->
                            val bounds = JSONArray(result)
                            assertFalse("No visible DOM editor in the settled viewport: $result", bounds.isNull(0))
                            val x = bounds.getDouble(0).toInt()
                            val y = bounds.getDouble(1).toInt()
                            assertTrue("Editor ($x, $y) is outside the active WebView ${first.width}x${first.height}",
                                x in 0 until first.width && y in 0 until first.height)
                            val origin = IntArray(2)
                            first.getLocationOnScreen(origin)
                            editor.set(Point(origin[0] + x, origin[1] + y))
                        }
                    }
                })
            }
            compose.waitUntil(5_000) { editor.get() != null }
            val point = checkNotNull(editor.get())
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun sendInput(command: String) {
                ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
            }
            val viewportBeforeIme = compose.runOnIdle { Point(first.width, first.height) }
            sendInput("input tap ${point.x} ${point.y}")
            val editorFocused = AtomicBoolean()
            val focusProbePending = AtomicBoolean()
            try {
                compose.waitUntil(5_000) {
                    if (!editorFocused.get() && focusProbePending.compareAndSet(false, true)) {
                        compose.runOnIdle {
                            first.evaluateJavascript("document.activeElement instanceof HTMLInputElement") { result ->
                                editorFocused.set(result == "true")
                                focusProbePending.set(false)
                            }
                        }
                    }
                    editorFocused.get()
                }
            } catch (failure: ComposeTimeoutException) {
                val observed = compose.runOnIdle { touchTrace.joinToString("; ") }
                throw AssertionError("Tap at screen position $point did not focus the retained WebView editor. $observed", failure)
            }
            compose.runOnIdle {
                val ime = first.context.getSystemService(InputMethodManager::class.java)
                ime.showSoftInput(first, InputMethodManager.SHOW_IMPLICIT)
            }
            val root = compose.activity.window.decorView
            compose.waitUntil(10_000) { compose.runOnIdle { root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true } }
            sendInput("input keyevent 4")
            compose.waitUntil(10_000) { compose.runOnIdle { root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == false } }
            compose.waitUntil(10_000) {
                compose.runOnIdle { first.width == viewportBeforeIme.x && first.height == viewportBeforeIme.y }
            }
            val resizedFrame = AtomicBoolean(false)
            compose.runOnIdle {
                first.postVisualStateCallback(2, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) { resizedFrame.set(true) }
                })
            }
            compose.waitUntil(5_000) { resizedFrame.get() }
            fun viewportDiagnostic() = compose.runOnIdle {
                "scroll=${first.scrollX},${first.scrollY} viewport=${first.width}x${first.height} contentHeight=${first.contentHeight} " +
                    "canScrollDown=${first.canScrollVertically(1)} focused=${first.hasFocus()} windowFocus=${first.hasWindowFocus()} " +
                    "enabled=${first.isEnabled} ime=${root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())} " +
                    "touches=${touchTrace.joinToString("; ")}"
            }
            val beforeSwipe = viewportDiagnostic()
            val scrollBeforeSwipe = compose.runOnIdle { first.scrollY }
            host.performTouchInput { swipeUp() }
            try {
                compose.waitUntil(5_000) { compose.runOnIdle { first.scrollY > scrollBeforeSwipe } }
            } catch (failure: ComposeTimeoutException) {
                throw AssertionError("Retained WebView did not scroll after IME dismissal. Before: $beforeSwipe After: ${viewportDiagnostic()}", failure)
            }
            println("Retained WebView scroll diagnostic: before=$beforeSwipe after=${viewportDiagnostic()}")
            host.performTouchInput { swipeLeft() }
            compose.waitUntil(5_000) { compose.runOnIdle { first.scrollX > 0 } }
        } finally {
            compose.runOnIdle { session.destroyAll() }
        }
    }
}
