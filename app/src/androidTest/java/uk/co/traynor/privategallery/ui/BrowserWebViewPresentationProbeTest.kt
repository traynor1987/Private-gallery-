package uk.co.traynor.privategallery.ui

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.v2.BrowserWebViewPresentationProbe

class BrowserWebViewPresentationProbeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun probeDistinguishesUpdateFromRealAttachmentAndRecordsOnlyStructure() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        lateinit var view: WebView
        lateinit var container: FrameLayout
        lateinit var probe: BrowserWebViewPresentationProbe
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                view = WebView(context).apply {
                    tag = "private-tag-must-not-escape"
                    contentDescription = "private-description-must-not-escape"
                }
                probe = BrowserWebViewPresentationProbe(view) { event, details -> events += event to details }
                probe.recordState("HOST_CREATED")
                container = FrameLayout(context).apply {
                    addView(FrameLayout(context).apply {
                        addView(view, ViewGroup.LayoutParams(-1, -1))
                    }, ViewGroup.LayoutParams(-1, -1))
                }
                container
            })
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(events.any { it.first == "WEBVIEW_INITIAL_STATE" && it.second["window_attached"] == "false" })
            assertTrue(events.any { it.first == "WEBVIEW_ATTACHED" && it.second["window_attached"] == "true" })
            assertTrue(events.any { it.first == "WEBVIEW_BOUNDS" && (it.second["width"]?.toInt() ?: 0) > 0 })
            val nextParent = FrameLayout(compose.activity)
            container.addView(nextParent, ViewGroup.LayoutParams(-1, -1))
            (view.parent as ViewGroup).removeView(view)
            nextParent.addView(view, ViewGroup.LayoutParams(-1, -1))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(events.any { it.first == "WEBVIEW_REPARENTED" })
            assertTrue(events.any { it.first == "WEBVIEW_DETACHED" })
            assertTrue(events.any { it.first == "WEBVIEW_VISIBILITY" && it.second["alpha"] == "1.0" })
            assertFalse(events.toString().contains("private-tag"))
            assertFalse(events.toString().contains("private-description"))
            probe.dispose()
            (view.parent as ViewGroup).removeView(view)
            view.destroy()
        }
    }
}
