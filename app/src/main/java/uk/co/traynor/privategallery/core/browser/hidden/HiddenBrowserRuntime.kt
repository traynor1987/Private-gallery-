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
    private val reconnecting:Boolean=false,
) : Closeable {
    companion object { const val STARTUP_TIMEOUT_MS=120_000L }
    @Volatile private var authority: HiddenSession.Authority? = null
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val policy = HiddenConnectionPolicy()
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val profileName = "hidden16-"+UUID.randomUUID().toString()
    private var profile: Profile? = null
    private var tor: HiddenTorConnection? = null
    private var pinnedVpn: android.net.Network? = null
    private var interactiveBroker: HiddenVpnBrowserBroker? = null
    val interactiveActivityActive: Boolean get() = interactiveBroker?.active == true
    var interactiveActivityLaunched=false; private set
    @Volatile private var transport: HiddenHttpTransport? = null
    var status by mutableStateOf("Disconnected"); private set
    var detail by mutableStateOf(""); private set
    var connected by mutableStateOf(false); private set
    var current by mutableStateOf<WebView?>(null); private set
    val tabs = mutableStateListOf<WebView>()
    private val library = HiddenSessionLibrary()
    private val navigationClients=mutableMapOf<WebView,HiddenNavigationClient>()
    var showingPage by mutableStateOf(false); private set
    var downloading by mutableStateOf(false); private set
    var downloadedBytes by mutableLongStateOf(0); private set
    var downloadTotal by mutableLongStateOf(-1); private set
    var address by mutableStateOf("")
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf(""); private set
    var desktopSite by mutableStateOf(false); private set

    private fun authorized(): Boolean = !closed.get() && runCatching {session.beginOperation(authority).use {session.isCurrent(it)}}.getOrDefault(false)
    fun start() {
        if(closed.get() || !started.compareAndSet(false,true))return
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
        if(mode==HiddenConnectionPolicy.Mode.VPN_ONLY && !HiddenVpnActivationGate.INTERACTIVE_VERIFIED) {
            status="Connection failed";detail="Interactive VPN browsing is awaiting real VPN routing, DNS, revocation tests and independent security review. No browser traffic was enabled.";return
        }
        if(mode==HiddenConnectionPolicy.Mode.VPN_ONLY && android.os.Build.VERSION.SDK_INT<30) {
            status="Connection failed";detail="VPN Only needs Android 11 or newer to verify the app-owned Network. No connection started.";return
        }
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) || !WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            status="Connection failed";detail="Hidden browsing requires an Android System WebView with separate-profile and profile data-clearing support.";return
        }
        // Retired profiles may remain after process death; never restore them into a new session.
        val store=ProfileStore.getInstance()
        HiddenProfileCleanup.removeRetiredContainers()
        detail="Creating isolated browser profile"
        profile=store.getOrCreateProfile(profileName)
        profile!!.serviceWorkerController.serviceWorkerWebSettings.apply {
            blockNetworkLoads=true;allowContentAccess=false;allowFileAccess=false;cacheMode=WebSettings.LOAD_NO_CACHE
        }
        profile!!.serviceWorkerController.setServiceWorkerClient(object:android.webkit.ServiceWorkerClient() {
            override fun shouldInterceptRequest(request:WebResourceRequest):WebResourceResponse=HiddenHttpTransport.blocked()
        })
        val epoch=policy.begin(mode,true)
        status=if(reconnecting)"Reconnecting" else "Connecting"
        activity.prepareHiddenBrowserTransport(mode)
        scope.launch {
            try {
                withTimeout(STARTUP_TIMEOUT_MS) {
                    if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY) {
                        detail="Releasing the app WireGuard tunnel"
                        while(!activity.hiddenWireGuardReleased()) {check(authorized());delay(250)}
                        tor=HiddenTorConnection(activity.applicationContext).also {it.start()}
                        detail="Starting Tor"
                        var port=0
                        while(port==0) {
                            check(authorized())
                            val progress=withContext(Dispatchers.IO){tor?.bootstrapProgress() ?: 0}
                            detail="Tor bootstrap $progress%"
                            port=withContext(Dispatchers.IO){tor?.port() ?: 0}
                            if(port==0)delay(500)
                        }
                        check(authorized())
                        transport=HiddenHttpTransport.tor(port) {authorized() && policy.canEstablish(epoch) && tor?.alive()==true}
                        detail="Verifying HTTPS over Tor"
                        withContext(Dispatchers.IO) {checkNotNull(transport).verifyReady()}
                        check(policy.ready(epoch,true,false))
                        detail="Tor circuit and HTTPS transport verified"
                    } else {
                        detail="Waiting for the app WireGuard tunnel and peer handshake"
                        var network=activity.hiddenOwnedVpnNetwork()
                        while(network==null) {check(authorized());delay(500);network=activity.hiddenOwnedVpnNetwork()}
                        val pinned=network
                        pinnedVpn=pinned
                        transport=HiddenHttpTransport.vpn(pinned) {authorized() && policy.canEstablish(epoch) && activity.hiddenOwnedVpnNetwork()==pinned}
                        detail="Verifying HTTPS and DNS over the pinned WireGuard Network"
                        withContext(Dispatchers.IO) {checkNotNull(transport).verifyReady()}
                        check(policy.ready(epoch,false,true))
                        detail="WireGuard verified · sockets and DNS pinned to its Network · no Tor anonymity"
                    }
                    check(authorized());connected=true;status="Connected"
                    if(mode==HiddenConnectionPolicy.Mode.VPN_ONLY) {
                        val pinned=checkNotNull(pinnedVpn)
                        interactiveBroker=HiddenVpnBrowserBroker(activity,session,pinned,
                            {authorized() && policy.canEstablish(epoch) && activity.hiddenOwnedVpnNetwork()==pinned},
                            {if(!closed.get())close()},
                            onPresentationEnded={reason,at ->activity.hiddenVpnBrowserPresentationEnded(reason,at)})
                        interactiveActivityLaunched=true;interactiveBroker!!.launch()
                    } else newTab()
                }
                while(connected) {
                    delay(500)
                    if(!authorized()) {close();break}
                    val valid=if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY) withContext(Dispatchers.IO){tor?.port() ?: 0}>0 else pinnedVpn!=null && activity.hiddenOwnedVpnNetwork()==pinnedVpn
                    if(!valid) {disconnect("Connection failed","Transport lost. Requests stopped; retry explicitly.");break}
                }
            } catch(_:CancellationException) {if(!closed.get())disconnect("Connection failed","Timed out during: $detail. Retry explicitly.")}
            catch(_:Exception) {disconnect("Connection failed","Failed during: $detail. Check connection settings and retry.")}
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    fun newTab() {
        if(mode==HiddenConnectionPolicy.Mode.VPN_ONLY || !connected || !authorized() || tabs.size>=8)return
        val view=HiddenWebViewFactory.create(activity,profileName)
        view.settings.useWideViewPort=desktopSite;view.settings.loadWithOverviewMode=desktopSite
        val navigation=object:HiddenNavigationClient({transport},{connected && authorized() && view in tabs},mode) {
            override fun onUnsupportedNavigation() {notice="Unsupported address or browser capability. Only authorised GET navigation is available."}
            override fun onPageStarted(view:WebView,url:String?,icon:android.graphics.Bitmap?) {if(current===view){busy=true;address=url.orEmpty()}}
            override fun onPageFinished(view:WebView,url:String?) {
                if(current===view && connected && authorized()) {
                    busy=false;address=url.orEmpty()
                    if(url!=null && HiddenConnectionPolicy.safeUrl(url,mode))library.visit(url,view.title.orEmpty())
                }
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(request.isForMainFrame && current===view){busy=false;notice="Page could not load over the private connection. Check the address or retry."}
            }
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                if(request.isForMainFrame && current===view){busy=false;notice=when(response.responseHeaders?.entries?.firstOrNull {it.key.equals(HiddenHttpTransport.RESULT_HEADER,true)}?.value) {
                    "policy" -> "Local privacy policy blocked this address or session."
                    "unsupported" -> "Unsupported browser capability or redirect."
                    "transport" -> "Private transport failed. Retry the connection."
                    else -> "Website returned HTTP ${response.statusCode}."
                }
                if(response.responseHeaders?.entries?.firstOrNull {it.key.equals(HiddenHttpTransport.RESULT_HEADER,true)}?.value=="transport")scope.launch {
                    if(authorized())disconnect("Connection failed","Page transport failed (${response.responseHeaders?.entries?.firstOrNull {it.key.equals(HiddenHttpTransport.FAILURE_HEADER,true)}?.value ?: "connection"}). No alternate route was used; retry explicitly.")
                }}
            }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError) {handler.cancel()}
            override fun onReceivedHttpAuthRequest(view:WebView,handler:HttpAuthHandler,host:String,realm:String) {handler.cancel()}
            override fun onReceivedClientCertRequest(view:WebView,request:ClientCertRequest) {request.cancel()}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {close();status="Connection failed";this@HiddenBrowserRuntime.detail="Browser renderer stopped. Retry creates a fresh isolated session.";return true}
        }
        navigationClients[view]=navigation
        view.webViewClient=navigation
        view.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage):Boolean = true // Do not emit page URLs/messages to logcat.
            override fun onPermissionRequest(request:PermissionRequest) {request.deny()}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback) {callback.invoke(origin,false,false)}
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {callback.onReceiveValue(null);return true}
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:android.os.Message)=false
        }
        view.setDownloadListener { url,_,_,_,_ ->download(url) }
        tabs.add(view);select(view)
    }
    fun select(view:WebView) {if(view in tabs){current=view;address=view.url.orEmpty();showingPage=address.isNotBlank() && address!="about:blank"}}
    fun closeTab(view:WebView) {
        navigationClients.remove(view)?.invalidate()
        tabs.remove(view);(view.parent as? android.view.ViewGroup)?.removeView(view);view.stopLoading();view.destroy()
        if(current===view){current=tabs.lastOrNull();address=current?.url.orEmpty();showingPage=address.isNotBlank() && address!="about:blank"}
    }
    fun navigate(input:String) {
        if(!connected || !authorized())return
        val value=input.trim()
        val url=when {
            value.contains("://") -> value
            value.contains('.') && !value.contains(' ') ->
                (if(runCatching {java.net.URI("https://$value").host.orEmpty().endsWith(".onion",true)}.getOrDefault(false))"http://" else "https://")+value
            else -> "https://html.duckduckgo.com/html/?q="+java.net.URLEncoder.encode(value,"UTF-8")
        }
        if(!HiddenConnectionPolicy.safeUrl(url,mode)){notice="Unsupported address. Onion services require Tor Only and a valid v3 address.";return}
        if(current==null)newTab()
        notice="";showingPage=true;current?.let {navigationClients[it]?.navigate(it,url)};address=url
    }
    fun bookmarkCurrent():Boolean {
        val url=current?.url ?: return false
        if(!connected || !authorized() || !HiddenConnectionPolicy.safeUrl(url,mode))return false
        library.bookmark(url,current?.title.orEmpty());return true
    }
    fun bookmarks():List<String> = if(connected && authorized()) library.bookmarks() else emptyList()
    fun history():List<String> = if(connected && authorized()) library.history() else emptyList()
    fun removeBookmark(url:String) {if(connected && authorized())library.removeBookmark(url)}
    fun back() {current?.let {view ->
        val history=view.copyBackForwardList();history.getItemAtIndex(history.currentIndex-1)?.url?.let {navigationClients[view]?.expect(it)}
        view.goBack()
    }}
    fun forward() {current?.let {view ->
        val history=view.copyBackForwardList();history.getItemAtIndex(history.currentIndex+1)?.url?.let {navigationClients[view]?.expect(it)}
        view.goForward()
    }}
    fun refreshOrStop() {current?.let {if(busy){navigationClients[it]?.invalidate();it.stopLoading();busy=false}else{navigationClients[it]?.expect(it.url.orEmpty());it.reload()}}}
    fun download(url:String=address) {
        val client=transport ?: return
        if(!authorized() || downloading)return
        val repository=runCatching {HiddenRepository(activity.applicationContext,session)}.getOrNull() ?: return
        downloading=true;downloadedBytes=0;downloadTotal=-1
        notice="Downloading through the private connection…"
        scope.launch {
            val result=withContext(Dispatchers.IO) {runCatching {
                var lastUpdate=0L
                client.download(url,progress={bytes,total ->
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(now-lastUpdate>=250 || bytes==total) {
                        lastUpdate=now
                        scope.launch {if(authorized()){downloadedBytes=bytes;downloadTotal=total}}
                    }
                }) {mime,input ->
                    val item=repository.importMedia("Hidden download",mime,input,
                        validateDownloadedMedia=hiddenDownloadedMediaValidator(mime,{ !authorized() }))
                    repository.verify(item.id)
                }
            }}
            downloading=false
            if(authorized())notice=if(result.isSuccess)"Saved into encrypted Hidden Vault. Open Vault to view or restore it." else "Download failed or unsupported. No public file was created."
        }
    }
    fun setDesktopSite(enabled:Boolean) {
        if(!authorized())return
        desktopSite=enabled
        tabs.forEach {it.settings.useWideViewPort=enabled;it.settings.loadWithOverviewMode=enabled}
        // Tor keeps its fixed transport user agent and JS-off renderer; only the viewport changes.
        current?.reload()
    }
    fun renew() {
        if(mode!=HiddenConnectionPolicy.Mode.TOR_ONLY || !connected)return
        // A new runtime clears old connections before requesting a new circuit via retry.
        notice="Use Retry / new circuit to close every tab and establish a new Tor session."
    }
    fun connectionPermissionDenied() {
        if(closed.get())return
        close();status="Connection failed";detail="VPN permission was declined. Retry when ready to approve the Android VPN request."
    }
    fun connectionPreparationFailed() {
        if(closed.get())return
        close()
        status="Connection failed"
        detail="WireGuard configuration could not be loaded. Unlock Primary and check the existing VPN profile, then retry."
    }
    private fun disconnect(state:String,reason:String) {
        policy.disconnect();connected=false;downloading=false;showingPage=false;status=state;detail=reason
        library.clear()
        interactiveBroker?.close();interactiveBroker=null
        transport?.close();transport=null;tor?.close();tor=null
        tabs.toList().forEach(::closeTab)
    }
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        if(activity.hiddenBrowserOwner===this)activity.hiddenBrowserOwner=null
        scope.cancel();disconnect("Disconnected","Browsing stopped. Retry to create a fresh private session.")
        // Android forbids deleting a loaded Profile until a new app process. Clear its
        // browsing data now; never reuse it, and delete its container at next startup.
        profile?.let {retired ->runCatching {HiddenProfileCleanup.clear(retired)}}
        profile=null
    }
}
