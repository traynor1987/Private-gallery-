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
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) && WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))
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
            val cleared=CountDownLatch(1)
            scenario.onActivity {
                assertTrue(view.settings.blockNetworkLoads);assertFalse(view.settings.javaScriptEnabled)
                assertFalse(view.settings.allowFileAccess);assertFalse(view.settings.allowContentAccess)
                assertFalse(view.settings.safeBrowsingEnabled);assertEquals("Safe fixture",view.title)
                view.destroy();HiddenProfileCleanup.clear(ProfileStore.getInstance().getOrCreateProfile(name)) {cleared.countDown()}
            }
            assertTrue("Profile data cleanup callback",cleared.await(10,TimeUnit.SECONDS))
        }
    }
    @Test fun primaryAndHiddenProfilesKeepCookiesIndependentAndClearOnlyHiddenData() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) && WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            val name="hidden16-test-"+UUID.randomUUID();val url="https://isolation.invalid/"
            val wrote=CountDownLatch(2)
            scenario.onActivity {
                CookieManager.getInstance().setCookie(url,"primary_retained=1") {wrote.countDown()}
                val profile=ProfileStore.getInstance().getOrCreateProfile(name)
                profile.cookieManager.setCookie(url,"hidden_secret=1") {wrote.countDown()}
            }
            assertTrue(wrote.await(5,TimeUnit.SECONDS))
            val cleared=CountDownLatch(1)
            scenario.onActivity {activity ->
                val profile=ProfileStore.getInstance().getOrCreateProfile(name)
                assertTrue(profile.cookieManager.getCookie(url).contains("hidden_secret=1"))
                assertFalse(CookieManager.getInstance().getCookie(url).orEmpty().contains("hidden_secret"))
                val view=HiddenWebViewFactory.create(activity,name);view.destroy()
                HiddenProfileCleanup.clear(profile) {cleared.countDown()}
            }
            assertTrue("Hidden data cleared",cleared.await(10,TimeUnit.SECONDS))
            scenario.onActivity {
                assertNull(ProfileStore.getInstance().getOrCreateProfile(name).cookieManager.getCookie(url))
                assertTrue(CookieManager.getInstance().getCookie(url).orEmpty().contains("primary_retained=1"))
                val fresh=ProfileStore.getInstance().getOrCreateProfile(name+"-fresh")
                assertNull(fresh.cookieManager.getCookie(url))
                HiddenProfileCleanup.clear(fresh)
                CookieManager.getInstance().setCookie(url,"primary_retained=; Max-Age=0")
            }
        }
    }
}
