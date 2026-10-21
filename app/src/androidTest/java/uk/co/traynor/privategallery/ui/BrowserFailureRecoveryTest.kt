package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.core.browser.v2.*

class BrowserFailureRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun mainFrameFailureSurvivesFinishedCallbackButNewNavigationClearsIt() {
        compose.runOnIdle {
            val session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener)
            val id = session.tabs.activeTab.id
            session.onPageError(id,true,-2)
            assertTrue(session.pageFailure(id)!!.contains("host_lookup (-2)"))
            session.onPageState(id,"https://example.invalid","",false,false,false)
            assertNotNull(session.pageFailure(id))
            session.onPageState(id,"https://example.invalid","",true,false,false)
            assertNull(session.pageFailure(id))
            session.onPageError(id,false,-6)
            assertNull(session.pageFailure(id))
            session.onTlsRejected(id)
            assertTrue(session.pageFailure(id)!!.contains("certificate verification failed"))
            session.destroyAll()
            assertNull(session.pageFailure(id))
        }
    }
    @Test fun destroyedRendererCannotFailItsReplacement() {
        compose.runOnIdle {
            var allowed=true
            val session=BrowserV2Session(compose.activity,BrowserVpnGate {allowed},NoopBrowserV2Listener)
            val old=session.activeWebView()
            val staleClient=old.webViewClient
            allowed=false;session.enforceNetworkPolicy();allowed=true
            val replacement=session.activeWebView()
            assertNotSame(old,replacement)
            val request=object:android.webkit.WebResourceRequest {
                override fun getUrl()=android.net.Uri.parse("https://example.invalid")
                override fun isForMainFrame()=true
                override fun isRedirect()=false
                override fun hasGesture()=false
                override fun getMethod()="GET"
                override fun getRequestHeaders()=emptyMap<String,String>()
            }
            val error=object:android.webkit.WebResourceError() {
                override fun getErrorCode()=-2
                override fun getDescription():CharSequence="Synthetic DNS failure"
            }
            staleClient.onReceivedError(old,request,error)
            assertNull(session.pageFailure(session.tabs.activeTab.id))
            replacement.webViewClient.onReceivedError(replacement,request,error)
            assertNotNull(session.pageFailure(session.tabs.activeTab.id))
            session.destroyAll()
        }
    }
    @Test fun stalledNavigationGetsBoundedPersistentError() {
        lateinit var session:BrowserV2Session
        compose.runOnIdle {
            session=BrowserV2Session(compose.activity,BrowserVpnGate {true},NoopBrowserV2Listener)
            session.activeWebView()
            session.onPageState(session.tabs.activeTab.id,"https://example.invalid","",true,false,false)
        }
        try {
            compose.waitUntil(55_000) { session.pageFailure(session.tabs.activeTab.id)?.contains("timeout -8") == true }
            compose.runOnIdle {assertFalse(session.tabs.activeTab.loading)}
        } finally {compose.runOnIdle {session.destroyAll()}}
    }
    @Test fun httpFailureIsPersistentAndIsolatedToItsTab() {
        compose.runOnIdle {
            val session = BrowserV2Session(compose.activity, BrowserVpnGate { false }, NoopBrowserV2Listener)
            val first = session.tabs.activeTab.id
            session.onHttpError(first,true,503)
            val second = session.tabs.newTab().id
            assertNull(session.pageFailure(second))
            assertTrue(session.pageFailure(first)!!.contains("503"))
            session.destroyAll()
        }
    }
}
