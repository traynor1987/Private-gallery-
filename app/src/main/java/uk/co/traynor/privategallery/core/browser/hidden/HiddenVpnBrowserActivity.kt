package uk.co.traynor.privategallery.core.browser.hidden

import uk.co.traynor.privategallery.ui.*
import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.*
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.*
import org.json.JSONArray
import uk.co.traynor.privategallery.ui.GalleryAlertDialog
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
import uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy
import uk.co.traynor.privategallery.core.browser.v2.BrowserSecurityPolicy
import uk.co.traynor.privategallery.core.browser.v2.BrowserUserAgentMode
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private typealias UploadItem = HiddenNativeUploadItem

/** A process lifetime is exactly one authenticated immutable VPN lease. Never restores a URL. */
class HiddenVpnBrowserActivity:ComponentActivity() {
    companion object {private val processAdmission=HiddenVpnProcessAdmission()}
    private val ended=AtomicBoolean()
    private val handler=Handler(Looper.getMainLooper())
    private var broker:IBinder?=null
    private val endpoint=object:Binder() {
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            if(code!=HiddenVpnBrowserBroker.CHILD_SHUTDOWN && code!=HiddenVpnBrowserBroker.CHILD_SECURITY)return super.onTransact(code,data,reply,flags)
            data.enforceInterface(HiddenVpnBrowserBroker.CHILD_DESCRIPTOR)
            check(Binder.getCallingUid()==Process.myUid())
            if(code==HiddenVpnBrowserBroker.CHILD_SECURITY) {
                val latch=java.util.concurrent.CountDownLatch(1);var javascript=true;var attached=false
                handler.post {javascript=current?.settings?.javaScriptEnabled ?: true;attached=ready && getSystemService(ConnectivityManager::class.java).boundNetworkForProcess==network;latch.countDown()}
                check(latch.await(3,java.util.concurrent.TimeUnit.SECONDS));reply?.writeNoException();reply?.writeInt(if(javascript)1 else 0);reply?.writeInt(if(attached)1 else 0);return true
            }
            handler.post {terminate(false)}
            return true
        }
    }
    private var storageLease:HiddenVpnStorageCleanup.Lease?=null
    private var storageId=""
    private val shutdownFinished=AtomicBoolean()
    private var network:Network?=null
    private var mode=HiddenConnectionPolicy.Mode.VPN_ONLY
    private var proxyPort=0
    private var proxyPassword=""
    private var profile:Profile?=null
    private val profileName="hidden16-"+UUID.randomUUID()
    private val views=mutableStateListOf<WebView>()
    private var current by mutableStateOf<WebView?>(null)
    private var address by mutableStateOf("")
    private var notice by mutableStateOf("Preparing VPN-only browser…")
    private var busy by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var menu by mutableStateOf(false)
    private var tabList by mutableStateOf(false)
    private var libraryKind by mutableStateOf<String?>(null)
    private var libraryItems by mutableStateOf<List<String>>(emptyList())
    private var saveHistory by mutableStateOf(true)
    private var pendingDownload by mutableStateOf<String?>(null)
    private var downloadCandidate:String?=null
    private var pendingUpload:ValueCallback<Array<Uri>>?=null
    private val uploadGeneration=java.util.concurrent.atomic.AtomicLong()
    private var uploadLoadJob:kotlinx.coroutines.Job?=null
    private var uploadLoading by mutableStateOf(false)
    private var uploadLoadError by mutableStateOf<String?>(null)
    private var uploadRestricted by mutableStateOf(0)
    private var uploadTypes by mutableStateOf(0)
    private var uploadTotal by mutableStateOf(0)
    private var uploadQuery=""
    private val uploadPreviewSlots=java.util.concurrent.Semaphore(2)
    private var uploadOrigin=""
    private var uploadItems by mutableStateOf<List<UploadItem>>(emptyList())
    private var confirmUploads by mutableStateOf<List<UploadItem>>(emptyList())
    private var selectedUploads by mutableStateOf<Set<String>>(emptySet())
    private var multipleUploads=false
    private var uploadView:WebView?=null
    private data class WebsiteDialog(val view:WebView,val kind:String,val message:String,val result:JsResult,val default:String="",val origin:String="")
    private var websiteDialog by mutableStateOf<WebsiteDialog?>(null)
    private var promptText by mutableStateOf("")
    private var observedDownloadStatus=""
    private data class WebsiteAuth(val view:WebView,val host:String,val realm:String,val handler:HttpAuthHandler)
    private var websiteAuth by mutableStateOf<WebsiteAuth?>(null)
    private var authUser by mutableStateOf("")
    private var authPassword by mutableStateOf("")
    private data class Resubmit(val view:WebView,val reject:Message,val accept:Message)
    private var resubmit by mutableStateOf<Resubmit?>(null)
    private var desktop by mutableStateOf(false)
    private val thumbnailCache=object:android.util.LruCache<String,android.graphics.Bitmap>(8*1024*1024) {override fun sizeOf(key:String,value:android.graphics.Bitmap)=value.allocationByteCount}
    private val death=IBinder.DeathRecipient {Process.killProcess(Process.myPid())}
    private val screenOff=object:BroadcastReceiver() {override fun onReceive(context:Context,intent:Intent) {terminate(reason=HiddenVpnBrowserBroker.SCREEN_OFF)}}
    private val networkEvents=object:ConnectivityManager.NetworkCallback() {
        override fun onLost(lost:Network) {if(lost==network)handler.post {terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)}}
        override fun onCapabilitiesChanged(changed:Network,caps:NetworkCapabilities) {
            if(changed==network && (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || caps.ownerUid!=Process.myUid()))handler.post {terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)}
        }
    }
    private val heartbeat=object:Runnable {override fun run() {if(!live()){terminate(reason=HiddenVpnBrowserBroker.BACKGROUND);return};handler.postDelayed(this,250)}}
    private val panicTouch=uk.co.traynor.privategallery.core.ui.PanicTouchGate()
    private fun panic() {
        val cover=android.view.View(this).apply {setBackgroundColor(android.graphics.Color.BLACK);isClickable=true;importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
        (window.decorView as android.view.ViewGroup).addView(cover,android.view.ViewGroup.LayoutParams(-1,-1));cover.bringToFront()
        runCatching {call(HiddenVpnBrowserBroker.PANIC,read={Unit})}
        terminate()
    }
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean {
        val settings=uk.co.traynor.privategallery.ui.PrivacyShieldPreferences(this)
        val exploring=(getSystemService(ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager)?.isTouchExplorationEnabled==true
        val action=panicTouch.dispatch(event.actionMasked,event.pointerCount,settings.enabled && settings.shortcut,exploring)
        if(action==2)return true
        if(action==1){val cancel=android.view.MotionEvent.obtain(event);cancel.action=android.view.MotionEvent.ACTION_CANCEL;super.dispatchTouchEvent(cancel);cancel.recycle();panic();return true}
        return super.dispatchTouchEvent(event)
    }
    private fun <T> call(code:Int,write:(Parcel)->Unit={},read:(Parcel)->T):T {
        val request=Parcel.obtain();val reply=Parcel.obtain()
        try {request.writeInterfaceToken(HiddenVpnBrowserBroker.DESCRIPTOR);write(request);check(checkNotNull(broker).transact(code,request,reply,0));reply.readException();return read(reply)} finally {request.recycle();reply.recycle()}
    }
    /** URLs reach disk only through the parent-held, revocable Hidden capability. */
    private fun library(action:Int=0,url:String="",enabled:Boolean=false):org.json.JSONObject? = runCatching {
        call(HiddenVpnBrowserBroker.LIBRARY,write={it.writeInt(action);it.writeString(url);if(action==4)it.writeInt(if(enabled)1 else 0)},read={org.json.JSONObject(checkNotNull(it.readString()))})
    }.onFailure {notice="Hidden library unavailable."}.getOrNull()?.also {saveHistory=it.getBoolean("saveHistory")}
    private fun showLibrary(kind:String) {
        val state=library() ?: return
        val items=state.getJSONArray(if(kind=="Bookmarks")"bookmarks" else "history")
        libraryItems=(0 until items.length()).map {items.getString(it)};libraryKind=kind
    }
    private fun live():Boolean = !ended.get() && runCatching {call(HiddenVpnBrowserBroker.CHECK,read={parcel ->
        val allowed=parcel.readInt()==1
        val status=parcel.readString().orEmpty()
        if(status.isNotEmpty())handler.post {if(!ended.get() && status!=observedDownloadStatus){observedDownloadStatus=status;notice=status}}
        allowed
    })}.getOrDefault(false)
    private fun cancelWebsiteDialog() {
        websiteDialog?.result?.cancel();websiteDialog=null;promptText=""
        websiteAuth?.handler?.cancel();websiteAuth=null;authUser="";authPassword=""
        resubmit?.reject?.sendToTarget();resubmit=null
    }
    private fun showWebsiteDialog(view:WebView,url:String,kind:String,message:String,result:JsResult,default:String=""):Boolean {
        cancelWebsiteDialog()
        if(!live() || current!==view || !HiddenConnectionPolicy.safeUrl(url,mode)) {result.cancel();return true}
        val source=Uri.parse(url);val origin="${source.scheme}://${source.authority}".take(300)
        websiteDialog=WebsiteDialog(view,kind,message.take(8000),result,default,origin);promptText=default.take(8000);return true
    }
    private fun select(view:WebView) {cancelWebsiteDialog();cancelUpload();current=view;address=view.url.orEmpty()}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if(savedInstanceState!=null || !Application.getProcessName().endsWith(":hidden_vpn_browser")) {terminate(false);return}
        // A second Activity can briefly overlap a retiring one. Reject before even claiming
        // authority, and especially before rebinding the old process to a different Network.
        if(!processAdmission.enter()) {terminate(false);return}
        try {
            broker=checkNotNull(intent.extras?.getBinder(HiddenVpnBrowserBroker.EXTRA));intent.replaceExtras(Bundle())
            broker!!.linkToDeath(death,0)
            call(HiddenVpnBrowserBroker.CLAIM,write={it.writeStrongBinder(endpoint)},read={parcel ->
                network=parcel.readTypedObject(Network.CREATOR);proxyPort=parcel.readInt();proxyPassword=checkNotNull(parcel.readString());storageId=checkNotNull(parcel.readString());mode=HiddenConnectionPolicy.Mode.parse(parcel.readString());check(mode!=HiddenConnectionPolicy.Mode.VPN_TOR)
            })
            val manager=getSystemService(ConnectivityManager::class.java)
            val pinned=checkNotNull(network)
            val caps=checkNotNull(manager.getNetworkCapabilities(pinned))
            check(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && caps.ownerUid==Process.myUid())
            check(manager.bindProcessToNetwork(pinned) && manager.boundNetworkForProcess==pinned)
            // Startup feature queries do not initialize WebView. No fallback to provider-owned roots.
            check(WebViewFeature.isStartupFeatureSupported(this,WebViewFeature.STARTUP_FEATURE_SET_DIRECTORY_BASE_PATHS))
            check(WebViewFeature.isStartupFeatureSupported(this,WebViewFeature.STARTUP_FEATURE_SET_DATA_DIRECTORY_SUFFIX))
            val lease=HiddenVpnStorageCleanup.holdChild(this,storageId);storageLease=lease
            ProcessGlobalConfig.apply(ProcessGlobalConfig().setDirectoryBasePaths(this,lease.data,lease.cache).setDataDirectorySuffix(this,"hidden_vpn"))
            check(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) && WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA) && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))
            HiddenProfileCleanup.removeRetiredContainers()
            profile=ProfileStore.getInstance().getOrCreateProfile(profileName)
            manager.registerNetworkCallback(NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).addTransportType(NetworkCapabilities.TRANSPORT_VPN).build(),networkEvents)
            registerReceiver(screenOff,IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED)
            handler.post(heartbeat)
            onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {override fun handleOnBackPressed(){terminate()}})
            val theme=uk.co.traynor.privategallery.core.ui.ThemePreference.decode(getSharedPreferences("private-gallery-settings",MODE_PRIVATE).getString("app-theme",null))
            setContent {PrivateGalleryTheme(theme) {Browser()}}
            // No WebView exists until process binding and proxy completion have both succeeded.
            val config=ProxyConfig.Builder().addProxyRule("http://127.0.0.1:$proxyPort").removeImplicitRules().build()
            ProxyController.getInstance().setProxyOverride(config,{command ->handler.post(command)}) {
                if(!live()){terminate(reason=HiddenVpnBrowserBroker.BACKGROUND);return@setProxyOverride}
                ready=true;notice=if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY)"Tor only · JavaScript off · isolated DNS/network boundary" else "VPN only · session data stays separate from Primary";newTab()
            }
        } catch(_:Exception) {terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)}
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab():WebView? {
        if(!ready || !live() || views.size>=8)return null
        val view=WebView(this)
        WebViewCompat.setProfile(view,profileName)
        view.settings.apply {
            blockNetworkLoads=true;javaScriptEnabled=mode==HiddenConnectionPolicy.Mode.VPN_ONLY;domStorageEnabled=true
            allowContentAccess=true // Only this app's narrowly scoped upload provider is ever supplied.
            allowFileAccess=false;allowFileAccessFromFileURLs=false;allowUniversalAccessFromFileURLs=false
            cacheMode=WebSettings.LOAD_NO_CACHE;saveFormData=false;setGeolocationEnabled(false)
            userAgentString=BrowserSecurityPolicy.userAgent(if(desktop)BrowserUserAgentMode.DESKTOP else BrowserUserAgentMode.MOBILE,WebSettings.getDefaultUserAgent(this@HiddenVpnBrowserActivity))
            useWideViewPort=desktop;loadWithOverviewMode=desktop
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;safeBrowsingEnabled=false
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(true);mediaPlaybackRequiresUserGesture=true
        }
        view.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean = !live() || !HiddenConnectionPolicy.safeUrl(request.url.toString(),mode)
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? =
                if(!live() || !HiddenConnectionPolicy.safeUrl(request.url.toString(),mode))HiddenHttpTransport.blocked() else null
            override fun onPageStarted(view:WebView,url:String?,icon:android.graphics.Bitmap?) {if(websiteDialog?.view===view || websiteAuth?.view===view || resubmit?.view===view)cancelWebsiteDialog();if(uploadView===view)cancelUpload();if(current===view){downloadCandidate=null;busy=true;address=url.orEmpty()}}
            override fun onPageFinished(view:WebView,url:String?) {if(current===view){busy=false;address=url.orEmpty()};if(url!=null && HiddenConnectionPolicy.safeUrl(url,mode))library(1,url)}
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError) {handler.cancel()}
            override fun onReceivedHttpAuthRequest(view:WebView,handler:HttpAuthHandler,host:String,realm:String) {
                if(live() && host=="127.0.0.1" && realm=="Hidden VPN session")handler.proceed("hidden",proxyPassword)
                else if(live() && current===view && Uri.parse(view.url).host==host && Uri.parse(view.url).scheme=="https") {
                    cancelWebsiteDialog();websiteAuth=WebsiteAuth(view,host.take(200),realm.take(200),handler)
                } else {handler.cancel();notice="Website authentication was cancelled. Only the visible HTTPS website can request credentials."}
            }
            override fun onFormResubmission(view:WebView,dontResend:Message,resend:Message) {
                cancelWebsiteDialog()
                if(live() && current===view)resubmit=Resubmit(view,dontResend,resend) else dontResend.sendToTarget()
            }
            override fun onReceivedClientCertRequest(view:WebView,request:ClientCertRequest) {request.cancel()}
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {if(request.isForMainFrame){busy=false;notice="Website could not load through this VPN session."}}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {terminate(reason=HiddenVpnBrowserBroker.BACKGROUND);return true}
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage)=true
            override fun onJsAlert(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,url,"alert",message,result)
            override fun onJsConfirm(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,url,"confirm",message,result)
            override fun onJsPrompt(view:WebView,url:String,message:String,defaultValue:String,result:JsPromptResult)=showWebsiteDialog(view,url,"prompt",message,result,defaultValue)
            override fun onJsBeforeUnload(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,url,"leave",message,result)
            override fun onPermissionRequest(request:PermissionRequest) {request.deny()}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback) {callback.invoke(origin,false,false)}
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean {
                if(!isUserGesture || !live() || current!==view || views.size>=8)return false
                val transport=resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup=newTab() ?: return false
                transport.webView=popup;resultMsg.sendToTarget();return true
            }
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {
                cancelUpload()
                if(!live() || current!==webView || params.mode !in setOf(FileChooserParams.MODE_OPEN,FileChooserParams.MODE_OPEN_MULTIPLE)) {callback.onReceiveValue(null);return true}
                val origin=webView.url.orEmpty()
                if(!HiddenConnectionPolicy.safeUrl(origin,mode)) {callback.onReceiveValue(null);return true}
                val generation=uploadGeneration.incrementAndGet()
                val accepts=params.acceptTypes.copyOf()
                uploadOrigin=origin;uploadView=webView;pendingUpload=callback
                multipleUploads=params.mode==FileChooserParams.MODE_OPEN_MULTIPLE
                uploadLoading=true;uploadLoadError=null
                uploadLoadJob=lifecycleScope.launch {
                    val loaded=mutableListOf<UploadItem>()
                    var query="";var offset=0
                    try {
                        do {
                            val page=withContext(Dispatchers.IO) {
                                check(uploadGeneration.get()==generation && !ended.get())
                                val text=checkNotNull(call(HiddenVpnBrowserBroker.LIST,write={parcel ->
                                    parcel.writeLong(generation);parcel.writeString(query);parcel.writeInt(offset);parcel.writeStringArray(accepts)
                                },read={it.readString()}))
                                require(text.length<=200_000);org.json.JSONObject(text)
                            }
                            if(!ownsUpload(generation,callback,webView,origin))return@launch
                            require(page.getLong("generation")==generation)
                            val returnedQuery=page.getString("query")
                            require(returnedQuery.length<=64 && (query.isEmpty() || query==returnedQuery))
                            query=returnedQuery
                            val items=page.getJSONArray("items")
                            require(items.length()<=HiddenUploadPickerQuery.PAGE_SIZE)
                            repeat(items.length()) {index ->val item=items.getJSONObject(index)
                                val id=item.getString("id");val name=item.getString("name");val mime=item.getString("mime")
                                require(id.length<=64 && name.length<=200 && mime.length<=128)
                                loaded.add(UploadItem(id,name,mime))
                            }
                            val next=page.getInt("next");val total=page.getInt("total")
                            require(next==offset+items.length() && next<=total && (next==total || next>offset))
                            offset=next;uploadTotal=total;uploadRestricted=page.getInt("restricted");uploadTypes=page.getInt("types")
                            uploadQuery=query;uploadItems=loaded.toList()
                        } while(offset<uploadTotal)
                        if(ownsUpload(generation,callback,webView,origin))uploadLoading=false
                    } catch(_:Exception) {
                        if(ownsUpload(generation,callback,webView,origin)) {uploadLoading=false;uploadItems=emptyList();uploadQuery="";uploadLoadError="Hidden media could not be loaded. Cancel and reopen the chooser to retry."}
                    }
                }
                return true
            }
        }
        view.setDownloadListener {url,_,_,_,_ ->downloadCandidate=url.takeIf {HiddenConnectionPolicy.safeUrl(it,mode)};pendingDownload=downloadCandidate;notice="Automatic downloads are paused. Use Save GET media for a reusable image/video URL. POST responses, blob files and one-use downloads cannot be replayed safely."}
        profile!!.serviceWorkerController.serviceWorkerWebSettings.apply {blockNetworkLoads=mode==HiddenConnectionPolicy.Mode.TOR_ONLY;allowContentAccess=false;allowFileAccess=false;cacheMode=WebSettings.LOAD_NO_CACHE}
        profile!!.serviceWorkerController.setServiceWorkerClient(object:ServiceWorkerClient() {
            override fun shouldInterceptRequest(request:WebResourceRequest):WebResourceResponse? = if(!live() || !HiddenConnectionPolicy.safeUrl(request.url.toString(),mode))HiddenHttpTransport.blocked() else null
        })
        check(live());view.settings.blockNetworkLoads=false;views.add(view);select(view);address=""
        return view
    }
    private fun navigate(value:String) {
        if(!ready || !live())return
        val input=value.trim()
        val url=when {input.contains("://")->input;input.contains('.') && !input.contains(' ')->(if(input.substringBefore('/').endsWith(".onion",true))"http://" else "https://")+input;else->"https://duckduckgo.com/?q="+java.net.URLEncoder.encode(input,"UTF-8")}
        if(!HiddenConnectionPolicy.safeUrl(url,mode)){notice="Only public HTTP(S) addresses are supported. Onion sites require Tor Only.";return}
        current?.loadUrl(url)
    }
    private fun ownsUpload(generation:Long,callback:ValueCallback<Array<Uri>>,view:WebView,origin:String):Boolean =
        !ended.get() && uploadGeneration.get()==generation && pendingUpload===callback && uploadView===view && current===view && view.url==origin
    private fun loadUploadPreview(item:UploadItem,callback:(Result<android.graphics.Bitmap>)->Unit) {
        val generation=uploadGeneration.get();val query=uploadQuery
        if(query.isEmpty() || uploadLoading || pendingUpload==null || ended.get())return
        val cacheKey="$generation:${item.id}"
        thumbnailCache.get(cacheKey)?.takeUnless {it.isRecycled}?.let {callback(Result.success(it));return}
        lifecycleScope.launch {
            var owned:android.graphics.Bitmap?=null
            try {
                withContext(Dispatchers.IO) {
                    uploadPreviewSlots.acquire()
                    try {
                        check(uploadGeneration.get()==generation && !ended.get())
                        val bytes=checkNotNull(call(HiddenVpnBrowserBroker.PREVIEW,write={it.writeLong(generation);it.writeString(query);it.writeString(item.id)},read={it.createByteArray()}))
                        try {require(bytes.size<=300_000);owned=checkNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size))} finally {bytes.fill(0)}
                    } finally {uploadPreviewSlots.release()}
                }
                if(uploadGeneration.get()!=generation || uploadQuery!=query || pendingUpload==null || item !in uploadItems || ended.get())return@launch
                if(!live())throw IllegalStateException("Preview session ended")
                val bitmap=checkNotNull(owned);thumbnailCache.put(cacheKey,bitmap);owned=null;callback(Result.success(bitmap))
            } catch(_:Exception) {if(uploadGeneration.get()==generation && uploadQuery==query && pendingUpload!=null)callback(Result.failure(IllegalStateException("Preview unavailable")))}
            finally {owned?.let {if(!it.isRecycled){it.eraseColor(android.graphics.Color.TRANSPARENT);it.recycle()}}}
        }
    }
    private fun cancelUpload() {
        uploadGeneration.incrementAndGet();uploadLoadJob?.cancel();uploadLoadJob=null
        val callback=pendingUpload;pendingUpload=null
        uploadLoading=false;uploadLoadError=null;uploadQuery="";uploadRestricted=0;uploadTypes=0;uploadTotal=0
        uploadItems=emptyList();confirmUploads=emptyList();selectedUploads=emptySet();uploadView=null
        callback?.onReceiveValue(null)
    }
    @Composable private fun Browser() {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
                Text("Hidden Browser · ${mode.indicator}",Modifier.weight(1f).padding(vertical=16.dp),style=MaterialTheme.typography.titleMedium)
                if(uk.co.traynor.privategallery.ui.PrivacyShieldPreferences(this@HiddenVpnBrowserActivity).let {it.enabled && it.quickLock})TextButton(onClick={panic()}){Text("Quick lock")}
                TextButton(onClick={library();menu=true}){Text("Settings")};TextButton(onClick={terminate()}){Text("Done")}
            }
            OutlinedTextField(address,{address=it},Modifier.fillMaxWidth().padding(horizontal=8.dp),singleLine=true,placeholder={Text("Search or enter address")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),keyboardActions=KeyboardActions(onGo={navigate(address)}),trailingIcon={TextButton(onClick={navigate(address)},enabled=ready){Text("Go")}})
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(notice,Modifier.heightIn(max=88.dp).verticalScroll(rememberScrollState()).padding(8.dp),style=MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f).fillMaxWidth()) {current?.let {view ->key(view) {AndroidView(factory={(view.parent as? android.view.ViewGroup)?.removeView(view);view},modifier=Modifier.fillMaxSize())}}}
            uk.co.traynor.privategallery.ui.GalleryBrowserToolbar {
                TextButton(onClick={current?.goBack()},enabled=current?.canGoBack()==true){Text("Back")}
                TextButton(onClick={current?.goForward()},enabled=current?.canGoForward()==true){Text("Next")}
                TextButton(onClick={if(busy)current?.stopLoading() else current?.reload()}){Text(if(busy)"Stop" else "Reload")}
                TextButton(onClick={tabList=true}){Text("Tabs ${views.size}")}
                TextButton(onClick={newTab()},enabled=views.size<8){Text("New")}
            }
        }
        if(menu)GalleryAlertDialog(onDismissRequest={menu=false},title={Text("Hidden ${mode.indicator} settings")},text={HiddenNativeSettingsContent(mode==HiddenConnectionPolicy.Mode.TOR_ONLY,mode.indicator,saveHistory,desktop,
            bookmark={current?.url?.let {library(2,it)};menu=false},bookmarks={showLibrary("Bookmarks");menu=false},history={showLibrary("History");menu=false},
            setHistory={library(4,enabled=it)},clearHistory={library(5)},toggleDesktop={desktop=!desktop;current?.settings?.apply {useWideViewPort=desktop;loadWithOverviewMode=desktop;userAgentString=BrowserSecurityPolicy.userAgent(if(desktop)BrowserUserAgentMode.DESKTOP else BrowserUserAgentMode.MOBILE,WebSettings.getDefaultUserAgent(this@HiddenVpnBrowserActivity))};current?.reload()},
            download={pendingDownload=downloadCandidate ?: current?.url;menu=false},clearClose={if(library(6)!=null)terminate()})},confirmButton={TextButton(onClick={menu=false}){Text("Done")}})
        if (tabList) GalleryAlertDialog(scrollBody=false,
            onDismissRequest = { tabList = false },
            title = { Text("Hidden tabs") },
            text = {
                val snapshot=views.toList()
                HiddenNativeTabsContent(snapshot.mapIndexed {i,view ->view.title ?: "Tab ${i+1}"},
                    select={i ->val view=snapshot[i];cancelWebsiteDialog();cancelUpload();current=view;address=view.url.orEmpty();busy=view.progress<100;tabList=false},
                    close={i ->val view=snapshot[i];cancelWebsiteDialog();cancelUpload();views.remove(view);view.stopLoading();view.destroy();if(current===view){current=views.lastOrNull();address=current?.url.orEmpty();busy=(current?.progress ?: 100)<100}},selectedIndex=snapshot.indexOf(current))
            },
            confirmButton = { TextButton(onClick = { tabList = false }) { Text("Done") } },
        )
        libraryKind?.let {kind ->GalleryAlertDialog(scrollBody=false,onDismissRequest={libraryKind=null},title={Text(kind)},text={
            HiddenNativeLibraryContent(libraryItems,kind=="Bookmarks",{url ->navigate(url);libraryKind=null},{url ->if(library(3,url)!=null)showLibrary(kind)})
        },confirmButton={TextButton(onClick={libraryKind=null}){Text("Done")}})}

        pendingDownload?.let {url ->GalleryAlertDialog(onDismissRequest={pendingDownload=null},title={Text("Save this GET media URL?")},text={Text("A fresh GET request will save supported image/video media encrypted in Hidden Vault. This cannot save POST responses, blob URLs, segmented media or one-use download responses. No public file is created.")},confirmButton={TextButton(onClick={pendingDownload=null;try {check(live());val cookies=profile!!.cookieManager.getCookie(url).orEmpty();call(HiddenVpnBrowserBroker.DOWNLOAD,write={it.writeString(url);it.writeString(cookies);it.writeString(current?.settings?.userAgentString.orEmpty())},read={Unit});notice="GET download queued into Hidden Vault. Unsupported or failed media is not saved."} catch(_:Exception){notice="Download rejected or session ended."}}){Text("Save GET media")}},dismissButton={TextButton(onClick={pendingDownload=null}){Text("Cancel")}})}
        if(pendingUpload!=null && confirmUploads.isEmpty())GalleryAlertDialog(
            scrollBody=false,
            onDismissRequest={cancelUpload()},title={Text("Choose from Hidden Vault")},
            text={HiddenNativeUploadPickerGrid(uploadItems,uploadLoading,multipleUploads,uploadRestricted,uploadTypes,uploadTotal,uploadLoadError,selectedUploads,
                preview={item,callback ->loadUploadPreview(item,callback)},choose={item ->
                    if(multipleUploads)selectedUploads=if(item.id in selectedUploads)selectedUploads-item.id else (selectedUploads+item.id).take(4).toSet()
                    else confirmUploads=listOf(item)
                })},
            confirmButton={if(multipleUploads)TextButton(enabled=!uploadLoading && selectedUploads.isNotEmpty(),onClick={confirmUploads=uploadItems.filter {it.id in selectedUploads}}){Text("Review upload")}},
            dismissButton={TextButton(onClick={cancelUpload()}){Text("Cancel")}})
        if(confirmUploads.isNotEmpty())GalleryAlertDialog(
            onDismissRequest={cancelUpload()},title={Text("Send media outside Hidden Vault?")},
            text={HiddenNativeUploadConsentContent(Uri.parse(uploadOrigin).host,confirmUploads.size)},
            confirmButton={TextButton(onClick={
                try {
                    check(live() && current===uploadView && uploadView?.url==uploadOrigin)
                    val uris=confirmUploads.map {item ->call(HiddenVpnBrowserBroker.UPLOAD,write={it.writeString(item.id)},read={Uri.parse(it.readString())})}.toTypedArray()
                    val completed=pendingUpload;pendingUpload=null;cancelUpload();completed?.onReceiveValue(uris)
                } catch(_:Exception){cancelUpload();notice="Upload cancelled."}
            }){Text("Allow upload")}},dismissButton={TextButton(onClick={cancelUpload()}){Text("Cancel")}})
        websiteAuth?.let {auth ->GalleryAlertDialog(
            onDismissRequest={cancelWebsiteDialog()},title={Text("Sign in to ${auth.host}")},
            text={HiddenNativeAuthContent(auth.realm,authUser,authPassword,{authUser=it},{authPassword=it})},
            confirmButton={TextButton(onClick={if(live() && current===auth.view && Uri.parse(auth.view.url).host==auth.host){auth.handler.proceed(authUser,authPassword);websiteAuth=null;authUser="";authPassword=""}else cancelWebsiteDialog()}){Text("Sign in")}},dismissButton={TextButton(onClick={cancelWebsiteDialog()}){Text("Cancel")}})}
        resubmit?.let {request ->GalleryAlertDialog(
            onDismissRequest={cancelWebsiteDialog()},title={Text("Resend form data?")},text={HiddenNativeResubmitContent()},
            confirmButton={TextButton(onClick={if(live() && current===request.view){resubmit=null;request.accept.sendToTarget()}else cancelWebsiteDialog()}){Text("Resend")}},dismissButton={TextButton(onClick={cancelWebsiteDialog()}){Text("Cancel")}})}
        websiteDialog?.let {dialog ->GalleryAlertDialog(
            onDismissRequest={cancelWebsiteDialog()},
            title={Text(if(dialog.kind=="leave")"Leave this website?" else "Website message")},
            text={HiddenNativeWebsiteContent(dialog.origin,dialog.message,dialog.kind=="prompt",promptText,{promptText=it})},
            confirmButton={TextButton(onClick={
                if(live() && current===dialog.view) {
                    if(dialog.result is JsPromptResult)dialog.result.confirm(promptText) else dialog.result.confirm()
                    websiteDialog=null;promptText=""
                } else cancelWebsiteDialog()
            }){Text(if(dialog.kind=="leave")"Leave" else "OK")}},
            dismissButton={if(dialog.kind!="alert")TextButton(onClick={cancelWebsiteDialog()}){Text("Cancel")}})}

    }
    private fun terminate(notify:Boolean=true,reason:Int=HiddenVpnBrowserBroker.RETURN) {
        if(!ended.compareAndSet(false,true))return
        val stoppedAt=SystemClock.elapsedRealtime()
        ready=false;handler.removeCallbacksAndMessages(null);cancelWebsiteDialog();cancelUpload()
        thumbnailCache.snapshot().values.forEach {if(it.isMutable && !it.isRecycled)it.eraseColor(android.graphics.Color.TRANSPARENT)}
        thumbnailCache.evictAll()
        views.toList().forEach {runCatching {it.settings.blockNetworkLoads=true;it.stopLoading();it.destroy()}};views.clear();current=null;libraryItems=emptyList();libraryKind=null
        profile?.serviceWorkerController?.serviceWorkerWebSettings?.blockNetworkLoads=true
        // Parent revokes transport before a single asynchronous deletion operation is started.
        if(notify)runCatching {call(HiddenVpnBrowserBroker.END,write={it.writeInt(reason);it.writeLong(stoppedAt)},read={Unit})}
        if(reason!=HiddenVpnBrowserBroker.RETURN) {Process.killProcess(Process.myPid());return}
        runCatching {getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkEvents)}
        runCatching {unregisterReceiver(screenOff)}
        finish() // Returning Main can resume while this process remains pinned and clears offline.
        handler.postDelayed({finishShutdown(false)},3_000)
        val retired=profile
        if(retired==null)finishShutdown(true)
        else try {HiddenProfileCleanup.clear(retired) {handler.post {finishShutdown(true)}}} catch(_:Exception) {finishShutdown(false)}
    }
    private fun finishShutdown(cleared:Boolean) {
        if(!shutdownFinished.compareAndSet(false,true))return
        handler.removeCallbacksAndMessages(null)
        runCatching {call(HiddenVpnBrowserBroker.CLEANED,write={it.writeInt(if(cleared)1 else 0)},read={Unit})}
        broker?.let {runCatching {it.unlinkToDeath(death,0)}}
        // Do not release the child storage lock until process death. Parent job observes the
        // kernel lock release before deleting the whole explicitly owned data/cache roots.
        if(Application.getProcessName().endsWith(":hidden_vpn_browser"))Process.killProcess(Process.myPid())
    }
    override fun onStop() {super.onStop();terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)}
    override fun onDestroy() {if(!ended.get())terminate(reason=HiddenVpnBrowserBroker.BACKGROUND);super.onDestroy()}
}
