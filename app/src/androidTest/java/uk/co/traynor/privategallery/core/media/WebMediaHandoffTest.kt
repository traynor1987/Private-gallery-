package uk.co.traynor.privategallery.core.media

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.v2.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebMediaHandoffTest {
    @Test fun explicitHtml5SourceIsOfferedButVpnDenialPreventsHandoff() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var session: BrowserV2Session
            val loaded = CountDownLatch(1)
            var allowed = true
            scenario.onActivity { activity ->
                session = BrowserV2Session(activity, BrowserVpnGate { allowed }, NoopBrowserV2Listener)
                session.bindListener(object : BrowserV2Session.Listener by NoopBrowserV2Listener {
                    override fun onSessionChanged() { if (!session.tabs.activeTab.loading && session.tabs.activeTab.url.isNotBlank()) loaded.countDown() }
                })
                val view = session.activeWebView()
                activity.setContentView(view)
                view.loadDataWithBaseURL("https://example.invalid/", "<html><video preload='none' src='https://example.invalid/synthetic.m3u8'></video></html>", "text/html", "UTF-8", null)
            }
            try {
                assertTrue(loaded.await(10, TimeUnit.SECONDS))
                val completed = CountDownLatch(1)
                var selected: Pair<String, String>? = null
                scenario.onActivity { session.requestPlayableMedia { selected = it; completed.countDown() } }
                assertTrue(completed.await(10, TimeUnit.SECONDS))
                assertEquals("https://example.invalid/synthetic.m3u8", selected?.first)
                assertEquals("application/x-mpegURL", selected?.second)
                scenario.onActivity {
                    allowed = false
                    var called = false
                    session.requestPlayableMedia { assertNull(it); called = true }
                    assertTrue(called)
                }
            } finally { scenario.onActivity { session.destroyAll() } }
        }
    }
}
