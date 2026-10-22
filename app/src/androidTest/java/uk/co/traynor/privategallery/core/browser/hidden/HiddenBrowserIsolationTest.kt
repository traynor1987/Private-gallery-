package uk.co.traynor.privategallery.core.browser.hidden

import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HiddenBrowserIsolationTest {
    @Test fun nativeResponsesRenderWithNetworkAndScriptingDisabled() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val page=CountDownLatch(1);val css=CountDownLatch(1)
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            val name="hidden16-test-"+UUID.randomUUID()
            lateinit var view:WebView
            scenario.onActivity {activity ->
                ProfileStore.getInstance().getOrCreateProfile(name)
                view=HiddenWebViewFactory.create(activity,name)
                view.webViewClient=object:WebViewClient() {
                    override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
                        if(request.url.path=="/style.css") {css.countDown();return WebResourceResponse("text/css","UTF-8",ByteArrayInputStream("body{color:blue}".toByteArray()))}
                        return WebResourceResponse("text/html","UTF-8",ByteArrayInputStream("<html><head><title>Safe fixture</title><link rel='stylesheet' href='/style.css'></head><body>Hidden<script>document.title='UNSAFE';fetch('https://leak.invalid')</script></body></html>".toByteArray()))
                    }
                    override fun onPageFinished(view:WebView,url:String) {page.countDown()}
                }
                activity.setContentView(view)
                view.loadUrl("https://hidden-fixture.invalid/page")
            }
            assertTrue("Page callback",page.await(15,TimeUnit.SECONDS))
            assertTrue("CSS must use interception while network disabled",css.await(5,TimeUnit.SECONDS))
            scenario.onActivity {
                assertTrue(view.settings.blockNetworkLoads);assertFalse(view.settings.javaScriptEnabled)
                assertFalse(view.settings.allowFileAccess);assertFalse(view.settings.allowContentAccess)
                assertFalse(view.settings.safeBrowsingEnabled);assertEquals("Safe fixture",view.title)
                view.destroy();assertTrue(ProfileStore.getInstance().deleteProfile(name))
            }
        }
    }
    @Test fun primaryAndHiddenProfilesKeepCookiesIndependentAndDoNotRestoreDeletedSessions() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            val name="hidden16-test-"+UUID.randomUUID();val url="https://isolation.invalid/"
            val wrote=CountDownLatch(1)
            scenario.onActivity {
                val profile=ProfileStore.getInstance().getOrCreateProfile(name)
                profile.cookieManager.setCookie(url,"hidden_secret=1") {wrote.countDown()}
            }
            assertTrue(wrote.await(5,TimeUnit.SECONDS))
            scenario.onActivity {activity ->
                val profile=ProfileStore.getInstance().getOrCreateProfile(name)
                assertTrue(profile.cookieManager.getCookie(url).contains("hidden_secret=1"))
                assertFalse(CookieManager.getInstance().getCookie(url).orEmpty().contains("hidden_secret"))
                val view=HiddenWebViewFactory.create(activity,name);view.destroy()
                assertTrue(ProfileStore.getInstance().deleteProfile(name))
                val fresh=ProfileStore.getInstance().getOrCreateProfile(name+"-fresh")
                assertNull(fresh.cookieManager.getCookie(url))
                assertTrue(ProfileStore.getInstance().deleteProfile(name+"-fresh"))
            }
        }
    }
}
