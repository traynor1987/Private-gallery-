package uk.co.traynor.privategallery.ui

import android.graphics.Point
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.webkit.WebView
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
import java.util.concurrent.atomic.AtomicReference
import uk.co.traynor.privategallery.core.browser.v2.*
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog

class NovaWebScroll67Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun webViewScrollsAfterSwitchingRetainedAppsAndDismissingKeyboard() {
        lateinit var session: BrowserV2Session
        lateinit var first: WebView
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
                    }.apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
                        settings.javaScriptEnabled = true
                    }
                })
            show("first")
            first = checkNotNull(active)
            first.loadDataWithBaseURL("https://scroll-fixture.invalid/", """
                <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <body style="margin:0"><input id="editor" aria-label="Scroll fixture editor"
                    style="position:absolute;top:96px;left:24px;height:64px;font-size:18px;width:160px">
                <main style="height:400vh;width:220vw;background:linear-gradient(#ddd,#246)"></main></body>
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
            val editor = AtomicReference<Point>()
            compose.runOnIdle {
                assertSame(first, active)
                first.evaluateJavascript("""
                    (() => {
                        const viewport = window.visualViewport;
                        const editor = document.getElementById('editor');
                        editor.style.left = (viewport.pageLeft + 24) + 'px';
                        editor.style.top = (viewport.pageTop + 96) + 'px';
                    })()
                """.trimIndent()) {
                    first.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            first.evaluateJavascript("""
                                (() => {
                                    const viewport = window.visualViewport;
                                    const editor = document.getElementById('editor');
                                    const rect = editor.getBoundingClientRect();
                                    const x = rect.left + rect.width / 2;
                                    const y = rect.top + rect.height / 2;
                                    const scale = devicePixelRatio * viewport.scale;
                                    return [(x - viewport.offsetLeft) * scale,
                                            (y - viewport.offsetTop) * scale,
                                            document.elementFromPoint(x, y) === editor];
                                })()
                            """.trimIndent()) { result ->
                                val bounds = JSONArray(result)
                                val x = bounds.getDouble(0).toInt()
                                val y = bounds.getDouble(1).toInt()
                                assertTrue("Editor ($x, $y) is outside the active WebView ${first.width}x${first.height}",
                                    x in 0 until first.width && y in 0 until first.height)
                                assertTrue("The rendered editor must be the DOM hit target: $result", bounds.getBoolean(2))
                                val origin = IntArray(2)
                                first.getLocationInWindow(origin)
                                editor.set(Point(origin[0] + x, origin[1] + y))
                            }
                        }
                    })
                }
            }
            compose.waitUntil(5_000) { editor.get() != null }
            val point = checkNotNull(editor.get())
            val hostOrigin = host.fetchSemanticsNode().positionInWindow
            host.performTouchInput { click(androidx.compose.ui.geometry.Offset(point.x.toFloat(), point.y.toFloat()) - hostOrigin) }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun sendInput(command: String) {
                ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
            }
            val root = compose.activity.window.decorView
            compose.waitUntil(10_000) { compose.runOnIdle { root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true } }
            sendInput("input keyevent 4")
            compose.waitUntil(10_000) { compose.runOnIdle { root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == false } }
            val scrollBeforeSwipe = compose.runOnIdle { first.scrollY }
            host.performTouchInput { swipeUp() }
            compose.waitUntil(5_000) { compose.runOnIdle { first.scrollY > scrollBeforeSwipe } }
            host.performTouchInput { swipeLeft() }
            compose.waitUntil(5_000) { compose.runOnIdle { first.scrollX > 0 } }
        } finally {
            compose.runOnIdle { session.destroyAll() }
        }
    }
}
