package uk.co.traynor.privategallery.core.browser.hidden

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicInteger

/** Shared production/test interception. Never delegates a request to WebView networking. */
internal open class HiddenNavigationClient(
    private val transport:()->HiddenHttpTransport?,
    private val authorized:()->Boolean,
    private val mode:HiddenConnectionPolicy.Mode,
):WebViewClient() {
    private data class Navigation(val generation:Long,val target:String?)
    private val gate=Any()
    @Volatile private var navigation=Navigation(0,null)
    private val redirectHops=AtomicInteger()
    private fun canonical(url:String):String?=url.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()?.toString()
    fun invalidate()=synchronized(gate) {navigation=Navigation(navigation.generation+1,null);redirectHops.set(0)}
    fun expect(url:String)=synchronized(gate) {navigation=Navigation(navigation.generation+1,canonical(url));redirectHops.set(0)}
    fun navigate(view:WebView,url:String) {
        expect(url)
        if(authorized() && HiddenConnectionPolicy.safeUrl(url,mode))view.loadUrl(url)
    }
    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
        if(!authorized() || request.method!="GET" || !HiddenConnectionPolicy.safeUrl(request.url.toString(),mode)) {
            onUnsupportedNavigation();return true
        }
        if(request.isForMainFrame)expect(request.url.toString())
        return false
    }
    protected open fun onUnsupportedNavigation() {}
    override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
        if(!authorized())return HiddenHttpTransport.blocked()
        val intent=navigation
        val source=canonical(request.url.toString())
        if(request.isForMainFrame && (source==null || source!=intent.target))return HiddenHttpTransport.blocked()
        val client=transport() ?: return HiddenHttpTransport.localError(503,"transport","Private transport is unavailable. Retry the connection.")
        return client.request(request.url.toString(),request.method,request.requestHeaders,
            onMainRedirect=if(!request.isForMainFrame)null else { target,hops ->
                synchronized(gate) {
                    if(!authorized() || navigation!==intent || source!=intent.target || redirectHops.addAndGet(hops)>20)false
                    else view.post {
                        synchronized(gate) {
                            if(authorized() && navigation===intent && source==intent.target) {
                                navigation=Navigation(intent.generation,canonical(target))
                                view.loadUrl(target)
                            }
                        }
                    }
                }
            },resource=!request.isForMainFrame)
    }
}
