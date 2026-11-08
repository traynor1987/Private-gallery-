package uk.co.traynor.privategallery.ui

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
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
                    }
                })
            show("first")
            first = checkNotNull(active)
            first.loadDataWithBaseURL("https://scroll-fixture.invalid/", """
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <body style="margin:0"><input style="height:48px;font-size:18px;width:90%">
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
            host.performTouchInput { swipeUp() }
            compose.waitUntil(5_000) { compose.runOnIdle { first.scrollY > 0 } }
            val parent = compose.runOnIdle { first.parent }
            compose.runOnIdle { show("second") }
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame(parent, first.parent)
                assertFalse(first.isEnabled)
                session.selectFromNovaHome(firstId)
                id = firstId
                active = session.activeWebView()
                first.scrollTo(0, 0)
            }
            host.performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 24f)) }
            val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            val root = compose.activity.window.decorView
            compose.waitUntil(10_000) { compose.runOnIdle { root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true } }
            automation.executeShellCommand("input keyevent 4").close()
            compose.waitUntil(10_000) { compose.runOnIdle { root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == false } }
            host.performTouchInput { swipeUp() }
            compose.waitUntil(5_000) { compose.runOnIdle { first.scrollY > 0 } }
            host.performTouchInput { swipeLeft() }
            compose.waitUntil(5_000) { compose.runOnIdle { first.scrollX > 0 } }
        } finally {
            compose.runOnIdle { session.destroyAll() }
        }
    }
}
