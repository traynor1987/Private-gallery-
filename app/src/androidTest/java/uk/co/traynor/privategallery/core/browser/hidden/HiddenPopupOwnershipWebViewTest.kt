package uk.co.traynor.privategallery.core.browser.hidden

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.DownloadListener
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real WebView identity fixture; does not claim a Google authorization flow. */
@RunWith(AndroidJUnit4::class)
class HiddenPopupOwnershipWebViewTest {
    @Test fun closeRestoresOpenerWhileLateAndRevokedCloseCannotChangeSelection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=ApplicationProvider.getApplicationContext<android.content.Context>()
            val opener=WebView(context);val popup=WebView(context);val other=WebView(context)
            try {
                val ownership=HiddenPopupOwnership<WebView>()
                ownership.add(opener);ownership.add(other);ownership.addPopup(popup,opener)
                assertSame(opener,ownership.close(popup,popup,true))
                assertNull(ownership.close(popup,popup,true))
                ownership.addPopup(popup,opener)
                assertNull(ownership.close(popup,other,true))
                ownership.addPopup(popup,opener)
                assertNull(ownership.close(popup,popup,false))
                ownership.clear()
                assertNull(ownership.close(popup,popup,true))
            } finally {popup.destroy();opener.destroy();other.destroy()}
        }
    }
    @Test fun retainedCallbacksFromClosedPopupCannotMutateSelectedOtherWebView() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=ApplicationProvider.getApplicationContext<android.content.Context>()
            val retired=WebView(context);val selected=WebView(context)
            try {
                val owners=HiddenCallbackOwners<WebView>()
                owners.add(retired);owners.add(selected)
                var mutations=0
                var authorityChecks=0
                val allowed={view:WebView ->owners.owns(view){authorityChecks++;true}}
                val retainedClient=object:WebViewClient() {
                    override fun onPageFinished(view:WebView,url:String?){if(allowed(view))mutations++}
                    override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {if(allowed(view))mutations++;return true}
                }
                val retainedDownload=DownloadListener {_,_,_,_,_ ->if(allowed(retired))mutations++}
                retired.webViewClient=retainedClient
                retired.setDownloadListener(retainedDownload)
                owners.removeAll(listOf(retired))
                retired.webViewClient=WebViewClient();retired.setDownloadListener(null)
                retainedClient.onPageFinished(retired,"https://example.invalid/")
                retainedClient.onRenderProcessGone(retired,object:RenderProcessGoneDetail(){
                    override fun didCrash()=true
                    override fun rendererPriorityAtExit()=0
                })
                retainedDownload.onDownloadStart("https://example.invalid/",null,null,null,0)
                assertEquals(0,mutations);assertEquals(0,authorityChecks)
                assertTrue(allowed(selected))
                owners.clear();assertFalse(allowed(selected))
            } finally {retired.destroy();selected.destroy()}
        }
    }

}
