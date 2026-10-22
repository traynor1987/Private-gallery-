package uk.co.traynor.privategallery.core.browser.hidden

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import androidx.compose.runtime.*
import androidx.webkit.*
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession

/** No saved authority, URLs, cookies or tabs. Each generation owns a unique isolated profile. */
internal class HiddenBrowserRuntime(
    private val activity: MainActivity,
    private val session: HiddenSession,
    val mode: HiddenConnectionPolicy.Mode,
) : Closeable {
    @Volatile private var authority: HiddenSession.Authority? = null
    private val closed = AtomicBoolean(false)
    private val policy = HiddenConnectionPolicy()
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val profileName = "hidden16-"+UUID.randomUUID().toString()
    private var profile: Profile? = null
    private var tor: HiddenTorConnection? = null
    private var pinnedVpn: android.net.Network? = null
    @Volatile private var transport: HiddenHttpTransport? = null
    var status by mutableStateOf("Disconnected"); private set
    var detail by mutableStateOf(""); private set
    var connected by mutableStateOf(false); private set
    var current by mutableStateOf<WebView?>(null); private set
    val tabs = mutableStateListOf<WebView>()
    var address by mutableStateOf("")
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf(""); private set

    private fun authorized(): Boolean = !closed.get() && runCatching {session.beginOperation(authority).use {session.isCurrent(it)}}.getOrDefault(false)
    fun start() {
        try { startAuthorized() }
        catch(_:Exception) { disconnect("Connection failed","Isolated browser initialization failed. No browsing is permitted.") }
    }
    private fun startAuthorized() {
        // Capture after prior Compose owners have disposed/revoked their presentation epoch.
        authority=runCatching {session.authority()}.getOrNull()
        if(!authorized()) {status="Disconnected";return}
        activity.hiddenBrowserOwner?.close();activity.hiddenBrowserOwner=this
        if(mode==HiddenConnectionPolicy.Mode.VPN_TOR) {
            status="Connection failed";detail="VPN + Tor unavailable: Tor's outbound route through WireGuard has not been enforced and verified. No alternative route was started.";return
        }
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            status="Connection failed";detail="Hidden browsing requires an Android System WebView with separate-profile support.";return
        }
        // Retired profiles may remain after process death; never restore them into a new session.
        val store=ProfileStore.getInstance()
        store.allProfileNames.filter {it.startsWith("hidden16-")}.forEach {runCatching {store.deleteProfile(it)}}
        profile=store.getOrCreateProfile(profileName)
        profile!!.serviceWorkerController.serviceWorkerWebSettings.apply {
            blockNetworkLoads=true;allowContentAccess=false;allowFileAccess=false;cacheMode=WebSettings.LOAD_NO_CACHE
        }
        profile!!.serviceWorkerController.setServiceWorkerClient(object:android.webkit.ServiceWorkerClient() {
            override fun shouldInterceptRequest(request:WebResourceRequest):WebResourceResponse=HiddenHttpTransport.blocked()
        })
        val epoch=policy.begin(mode,true)
        status="Connecting"
        activity.prepareHiddenBrowserTransport(mode)
        scope.launch {
            try {
                withTimeout(90_000) {
                    if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY) {
                        while(!activity.hiddenWireGuardReleased()) {check(authorized());delay(250)}
                        tor=HiddenTorConnection(activity.applicationContext).also {it.start()}
                        var port=0
                        while(port==0) {check(authorized());port=withContext(Dispatchers.IO){tor?.port() ?: 0};if(port==0)delay(500)}
                        check(authorized())
                        transport=HiddenHttpTransport.tor(port) {authorized() && policy.current(epoch) && tor?.alive()==true}
                        check(policy.ready(epoch,true,false))
                        detail="Tor circuit ready · no VPN required"
                    } else {
                        var network=activity.hiddenOwnedVpnNetwork()
                        while(network==null) {check(authorized());delay(500);network=activity.hiddenOwnedVpnNetwork()}
                        val pinned=network
                        pinnedVpn=pinned
                        transport=HiddenHttpTransport.vpn(pinned) {authorized() && policy.current(epoch) && activity.hiddenOwnedVpnNetwork()==pinned}
                        check(policy.ready(epoch,false,true))
                        detail="WireGuard verified · sockets and DNS pinned to its Network · no Tor anonymity"
                    }
                    connected=true;status="Connected";newTab()
                }
                while(connected) {
                    delay(500)
                    if(!authorized()) {close();break}
                    val valid=if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY) withContext(Dispatchers.IO){tor?.port() ?: 0}>0 else pinnedVpn!=null && activity.hiddenOwnedVpnNetwork()==pinnedVpn
                    if(!valid) {disconnect("Connection failed","Transport lost. Requests stopped; retry explicitly.");break}
                }
            } catch(_:CancellationException) {if(!closed.get())disconnect("Connection failed","Connection timed out or cancelled. Retry explicitly.")}
            catch(_:Exception) {disconnect("Connection failed","Connection unavailable. Check transport settings and retry.")}
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    fun newTab() {
        if(!connected || !authorized() || tabs.size>=8)return
        val view=HiddenWebViewFactory.create(activity,profileName)
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean =
                !connected || !authorized() || request.method!="GET" || !HiddenConnectionPolicy.safeUrl(request.url.toString())
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse =
                if(authorized()) transport?.request(request.url.toString(),request.method) ?: HiddenHttpTransport.blocked() else HiddenHttpTransport.blocked()
            override fun onPageStarted(view:WebView,url:String?,icon:android.graphics.Bitmap?) {if(current===view){busy=true;address=url.orEmpty()}}
            override fun onPageFinished(view:WebView,url:String?) {if(current===view){busy=false;address=url.orEmpty()}}
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError) {handler.cancel()}
            override fun onReceivedHttpAuthRequest(view:WebView,handler:HttpAuthHandler,host:String,realm:String) {handler.cancel()}
            override fun onReceivedClientCertRequest(view:WebView,request:ClientCertRequest) {request.cancel()}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {close();return true}
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onPermissionRequest(request:PermissionRequest) {request.deny()}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback) {callback.invoke(origin,false,false)}
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {callback.onReceiveValue(null);return true}
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:android.os.Message)=false
        }
        view.setDownloadListener { url,_,_,_,_ ->download(url) }
        tabs.add(view);select(view)
    }
    fun select(view:WebView) {if(view in tabs){current=view;address=view.url.orEmpty()}}
    fun closeTab(view:WebView) {
        tabs.remove(view);(view.parent as? android.view.ViewGroup)?.removeView(view);view.stopLoading();view.destroy()
        if(current===view){current=tabs.lastOrNull();address=current?.url.orEmpty()}
    }
    fun navigate(input:String) {
        if(!connected || !authorized())return
        val value=input.trim()
        val url=when {
            value.contains("://") -> value
            value.contains('.') && !value.contains(' ') -> "https://$value"
            else -> "https://html.duckduckgo.com/html/?q="+java.net.URLEncoder.encode(value,"UTF-8")
        }
        if(!HiddenConnectionPolicy.safeUrl(url)){notice="Only public HTTP(S) addresses are supported.";return}
        if(current==null)newTab()
        current?.loadUrl(url);address=url
    }
    fun download(url:String=address) {
        val client=transport ?: return
        if(!authorized())return
        val repository=runCatching {HiddenRepository(activity.applicationContext,session)}.getOrNull() ?: return
        notice="Saving media into encrypted Hidden Vault…"
        scope.launch {
            val result=withContext(Dispatchers.IO) {runCatching {
                client.download(url) {mime,input ->
                    val item=repository.importMedia("Hidden download",mime,input)
                    repository.verify(item.id)
                }
            }}
            if(authorized())notice=if(result.isSuccess)"Saved into encrypted Hidden Vault. Open Vault to view or restore it." else "Download failed or unsupported. No public file was created."
        }
    }
    fun renew() {
        if(mode!=HiddenConnectionPolicy.Mode.TOR_ONLY || !connected)return
        // A new runtime clears old connections before requesting a new circuit via retry.
        notice="Use Retry / new circuit to close every tab and establish a new Tor session."
    }
    private fun disconnect(state:String,reason:String) {
        policy.disconnect();connected=false;status=state;detail=reason
        transport?.close();transport=null;tor?.close();tor=null
        tabs.toList().forEach(::closeTab)
    }
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        if(activity.hiddenBrowserOwner===this)activity.hiddenBrowserOwner=null
        scope.cancel();disconnect("Disconnected","Browsing stopped. Retry to create a fresh private session.")
        profile=null
        runCatching {if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))ProfileStore.getInstance().deleteProfile(profileName)}
    }
}
