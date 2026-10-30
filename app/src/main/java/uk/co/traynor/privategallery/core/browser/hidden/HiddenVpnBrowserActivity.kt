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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import uk.co.traynor.privategallery.core.browser.BrowserAddressPolicy
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.BrowserBookmark
import uk.co.traynor.privategallery.core.browser.v2.BrowserHistoryEntry
import uk.co.traynor.privategallery.core.browser.v2.BrowserTab
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
    private var selectedTheme by mutableStateOf(uk.co.traynor.privategallery.core.ui.AppTheme.SYSTEM)
    private val policyRefreshQueued=AtomicBoolean()
    private val captureGuard by lazy {HiddenBrowserCaptureGuard(this,{ready && live()},{terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)})}
    private var broker:IBinder?=null
    private val endpoint=object:Binder() {
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            if(code!=HiddenVpnBrowserBroker.CHILD_SHUTDOWN && code!=HiddenVpnBrowserBroker.CHILD_SECURITY && code!=HiddenVpnBrowserBroker.CHILD_PRESENTATION)return super.onTransact(code,data,reply,flags)
            data.enforceInterface(HiddenVpnBrowserBroker.CHILD_DESCRIPTOR)
            check(Binder.getCallingUid()==Process.myUid())
            if(code==HiddenVpnBrowserBroker.CHILD_SECURITY) {
                val latch=java.util.concurrent.CountDownLatch(1);var javascript=true;var attached=false
                handler.post {javascript=current?.settings?.javaScriptEnabled ?: true;attached=ready && getSystemService(ConnectivityManager::class.java).boundNetworkForProcess==network;latch.countDown()}
                check(latch.await(3,java.util.concurrent.TimeUnit.SECONDS));reply?.writeNoException();reply?.writeInt(if(javascript)1 else 0);reply?.writeInt(if(attached)1 else 0);return true
            }
            if(code==HiddenVpnBrowserBroker.CHILD_PRESENTATION) {
                val latch=java.util.concurrent.CountDownLatch(1);var secure=true;var covered=true;var theme=uk.co.traynor.privategallery.core.ui.AppTheme.SYSTEM
                handler.post {secure=window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0;covered=captureGuard.covered;theme=selectedTheme;latch.countDown()}
                check(latch.await(3,java.util.concurrent.TimeUnit.SECONDS));reply?.writeNoException();reply?.writeInt(if(secure)1 else 0);reply?.writeInt(if(covered)1 else 0);reply?.writeString(uk.co.traynor.privategallery.core.ui.ThemePreference.encode(theme));return true
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
    private var libraryTitles by mutableStateOf<List<String>>(emptyList())
    private var libraryTimes by mutableStateOf<List<Long>>(emptyList())
    private var saveHistory by mutableStateOf(true)
    private data class DownloadSelection(val view:WebView,val url:String,val document:String,val revision:Int)
    private var pendingDownload by mutableStateOf<DownloadSelection?>(null)
    private var downloadCandidate:DownloadSelection?=null
    private var activeDownloadOwner:DownloadSelection?=null
    private fun cancelOwnedDownload() {
        if(activeDownloadOwner==null)return
        activeDownloadOwner=null
        runCatching {call(HiddenVpnBrowserBroker.DOWNLOAD_CANCEL,read={Unit})}
    }
    private var downloadRunning by mutableStateOf(false)
    private var downloadBytes by mutableStateOf(0L)
    private var downloadTotal by mutableStateOf(-1L)
    private fun downloadSelection(view:WebView,url:String):DownloadSelection? =
        if(current===view && HiddenConnectionPolicy.safeUrl(url,mode))DownloadSelection(view,url,view.url.orEmpty(),navigationRevision) else null
    private fun downloadSelectionCurrent(value:DownloadSelection)=ready && current===value.view && value.view in views &&
        value.view.url.orEmpty()==value.document && value.revision==navigationRevision && live()
    private var pendingUpload by mutableStateOf<ValueCallback<Array<Uri>>?>(null)
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
    private var observedDownloadStatus by mutableStateOf("")
    private data class WebsiteAuth(val view:WebView,val host:String,val realm:String,val handler:HttpAuthHandler)
    private var websiteAuth by mutableStateOf<WebsiteAuth?>(null)
    private var authUser by mutableStateOf("")
    private var authPassword by mutableStateOf("")
    private data class Resubmit(val view:WebView,val reject:Message,val accept:Message)
    private var resubmit by mutableStateOf<Resubmit?>(null)
    private var desktop by mutableStateOf(false)
    private val desktopTabs=mutableMapOf<WebView,Boolean>()
    private val failures=mutableStateMapOf<WebView,String>()
    private val loadingTabs=mutableMapOf<WebView,Boolean>()
    private val pageDeadlines=mutableMapOf<WebView,HiddenPageLoadDeadline>()
    private var pageLoadProgress by mutableStateOf(100)
    private var navigationRevision by mutableIntStateOf(0)
    private var addressFocused by mutableStateOf(false)
    private var clearAddressFocus:(()->Unit)?=null
    private var searchEngine by mutableStateOf(BrowserSearchEngine.DUCKDUCKGO)
    private var findOpen by mutableStateOf(false)
    private var findQuery by mutableStateOf("")
    private var findResult by mutableStateOf("")
    private var clearHistoryConfirm by mutableStateOf(false)
    private var downloadsOpen by mutableStateOf(false)
    private var requestedMode by mutableStateOf<HiddenConnectionPolicy.Mode?>(null)

    private val thumbnailCache=object:android.util.LruCache<String,android.graphics.Bitmap>(8*1024*1024) {override fun sizeOf(key:String,value:android.graphics.Bitmap)=value.allocationByteCount}
    private val death=IBinder.DeathRecipient {Process.killProcess(Process.myPid())}
    private val screenOff=object:BroadcastReceiver() {override fun onReceive(context:Context,intent:Intent) {terminate(reason=HiddenVpnBrowserBroker.SCREEN_OFF)}}
    private val networkEvents=object:ConnectivityManager.NetworkCallback() {
        override fun onLost(lost:Network) {if(lost==network)handler.post {terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)}}
        override fun onCapabilitiesChanged(changed:Network,caps:NetworkCapabilities) {
            if(changed==network && (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || caps.ownerUid!=Process.myUid()))handler.post {terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)}
        }
    }
    private val heartbeat=object:Runnable {override fun run() {
        if(!live()){terminate(reason=HiddenVpnBrowserBroker.BACKGROUND);return}
        pageDeadlines.forEach { (view,deadline) ->
            if(deadline.expired(SystemClock.elapsedRealtime())) {
                failures[view]="This website did not display a page within 60 seconds. Check the address or retry using the same private connection."
                loadingTabs[view]=false;view.stopLoading()
                if(current===view){busy=false;pageLoadProgress=100}
            }
        }
        handler.postDelayed(this,250)
    }}
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
    private fun library(action:Int=0,url:String="",enabled:Boolean=false,pageTitle:String=current?.takeIf {it.url==url}?.title.orEmpty()):org.json.JSONObject? = runCatching {
        call(HiddenVpnBrowserBroker.LIBRARY,write={it.writeInt(action);it.writeString(url);if(action==4)it.writeInt(if(enabled)1 else 0);if(action==1 || action==2){
            val title=pageTitle.codePoints().toArray().filter {point -> !Character.isISOControl(point) && point !in 0xD800..0xDFFF}.toIntArray()
            var text=String(title,0,title.size);if(text.length>256){text=text.take(256);if(text.lastOrNull()?.isHighSurrogate()==true)text=text.dropLast(1)}
            it.writeString(text);it.writeLong(System.currentTimeMillis())
        }},read={org.json.JSONObject(checkNotNull(it.readString()))})
    }.onFailure {notice="Hidden library unavailable."}.getOrNull()?.also {saveHistory=it.getBoolean("saveHistory")}
    private fun showLibrary(kind:String) {
        val state=library() ?: return
        val items=state.getJSONArray(if(kind=="Bookmarks")"bookmarks" else "history")
        libraryItems=(0 until items.length()).map {items.getString(it)}
        val prefix=if(kind=="Bookmarks")"bookmark" else "history"
        libraryTitles=libraryItems.indices.map {state.optJSONArray(prefix+"Titles")?.optString(it).orEmpty()}
        libraryTimes=libraryItems.indices.map {state.optJSONArray(prefix+"Times")?.optLong(it,0) ?: 0L}
        libraryKind=kind
    }
    private fun live():Boolean = !ended.get() && runCatching {call(HiddenVpnBrowserBroker.CHECK,read={parcel ->
        val allowed=parcel.readInt()==1
        val status=parcel.readString().orEmpty()
        val captureAllowed=parcel.readInt()==1
        val theme=uk.co.traynor.privategallery.core.ui.ThemePreference.decode(parcel.readString())
        val downloading=parcel.readInt()==1;val received=parcel.readLong();val total=parcel.readLong()
        handler.post {if(!ended.get()){downloadRunning=downloading;downloadBytes=received;downloadTotal=total}}
        if(Looper.myLooper()==Looper.getMainLooper()){captureGuard.updatePolicy(captureAllowed);selectedTheme=theme}
        else if(policyRefreshQueued.compareAndSet(false,true))handler.post {
            policyRefreshQueued.set(false)
            // Re-read on Main, rather than applying an older worker's policy after a newer one.
            if(!ended.get() && !live())terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)
        }
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
    private fun select(view:WebView) {cancelOwnedDownload();pendingDownload=null;downloadCandidate=null;current?.clearMatches();findOpen=false;findQuery="";findResult="";cancelWebsiteDialog();cancelUpload();current=view;address=view.url.orEmpty();desktop=desktopTabs[view] ?: false;pageLoadProgress=view.progress;busy=loadingTabs[view]==true;navigationRevision++}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        captureGuard.start()
        if(savedInstanceState!=null || !Application.getProcessName().endsWith(":hidden_vpn_browser")) {terminate(false);return}
        // A second Activity can briefly overlap a retiring one. Reject before even claiming
        // authority, and especially before rebinding the old process to a different Network.
        if(!processAdmission.enter()) {terminate(false);return}
        try {
            broker=checkNotNull(intent.extras?.getBinder(HiddenVpnBrowserBroker.EXTRA));intent.replaceExtras(Bundle())
            broker!!.linkToDeath(death,0)
            call(HiddenVpnBrowserBroker.CLAIM,write={it.writeStrongBinder(endpoint)},read={parcel ->
                network=parcel.readTypedObject(Network.CREATOR);proxyPort=parcel.readInt();proxyPassword=checkNotNull(parcel.readString());storageId=checkNotNull(parcel.readString());mode=HiddenConnectionPolicy.Mode.parse(parcel.readString());check(mode!=HiddenConnectionPolicy.Mode.VPN_TOR);captureGuard.updatePolicy(parcel.readInt()==1);selectedTheme=uk.co.traynor.privategallery.core.ui.ThemePreference.decode(parcel.readString())
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
            onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {override fun handleOnBackPressed(){
                when {addressFocused -> clearAddressFocus?.invoke(); websiteDialog!=null || websiteAuth!=null || resubmit!=null -> cancelWebsiteDialog();pendingUpload!=null -> cancelUpload();findOpen -> {findOpen=false;current?.clearMatches()};current?.canGoBack()==true -> current?.let {failures.remove(it);it.goBack()};else -> terminate()}
            }})
            setContent {PrivateGalleryTheme(selectedTheme) {val captureRevision=captureGuard.revision;SideEffect{captureGuard.compositionCommitted(captureRevision)};Browser()}}
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
        desktopTabs[view]=false
        desktop=false
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
            // Failure recovery belongs to explicit navigation; late provider starts must not erase Retry.
            override fun onPageStarted(view:WebView,url:String?,icon:android.graphics.Bitmap?) {if(activeDownloadOwner?.view===view)cancelOwnedDownload();if(view !in failures)pageDeadlines.getOrPut(view){HiddenPageLoadDeadline()}.begin(SystemClock.elapsedRealtime());if(websiteDialog?.view===view || websiteAuth?.view===view || resubmit?.view===view)cancelWebsiteDialog();if(uploadView===view)cancelUpload();loadingTabs[view]=view !in failures;if(current===view){pendingDownload=null;downloadCandidate=null;busy=view !in failures;pageLoadProgress=0;if(!addressFocused)address=url.orEmpty();navigationRevision++}}
            override fun onPageCommitVisible(view:WebView,url:String?) {pageDeadlines[view]?.visible()}
            override fun onPageFinished(view:WebView,url:String?) {pageDeadlines[view]?.visible();loadingTabs[view]=false;if(current===view){busy=false;pageLoadProgress=100;if(!addressFocused)address=url.orEmpty();navigationRevision++};if(url!=null && view !in failures && HiddenConnectionPolicy.safeUrl(url,mode)){library(1,url,pageTitle=view.title.orEmpty())}}
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError) {handler.cancel();pageDeadlines[view]?.visible();loadingTabs[view]=false;failures[view]="The secure connection could not be verified. No certificate exception was allowed.";if(current===view){busy=false;pageLoadProgress=100}}
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
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {if(request.isForMainFrame){if(uk.co.traynor.privategallery.BuildConfig.DEBUG)android.util.Log.i("HiddenBrowser","Main-frame failure mode=${mode.name} code=${error.errorCode}");pageDeadlines[view]?.visible();loadingTabs[view]=false;failures[view]=if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY)"Website could not load through this Tor session." else "Website could not load through this VPN session.";if(current===view){busy=false;pageLoadProgress=100}}}
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {if(request.isForMainFrame){pageDeadlines[view]?.visible();loadingTabs[view]=false;failures[view]="Website returned HTTP ${response.statusCode}.";if(current===view){busy=false;pageLoadProgress=100}}}
            override fun doUpdateVisitedHistory(view:WebView,url:String?,isReload:Boolean) {if(current===view)navigationRevision++}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {terminate(reason=HiddenVpnBrowserBroker.BACKGROUND);return true}
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onProgressChanged(view:WebView,newProgress:Int) {if(newProgress>=100)loadingTabs[view]=false;if(current===view){pageLoadProgress=newProgress;busy=loadingTabs[view]==true && view !in failures;navigationRevision++}}
            override fun onConsoleMessage(message:ConsoleMessage)=true
            override fun onJsAlert(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,url,"alert",message,result)
            override fun onJsConfirm(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,url,"confirm",message,result)
            override fun onJsPrompt(view:WebView,url:String,message:String,defaultValue:String,result:JsPromptResult)=showWebsiteDialog(view,url,"prompt",message,result,defaultValue)
            override fun onJsBeforeUnload(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,url,"leave",message,result)
            override fun onPermissionRequest(request:PermissionRequest) {
                request.deny()
                if(live() && current===view)notice="Camera, microphone and other device permissions are blocked in Hidden Browser. Read-only uploads require your separate confirmation."
            }
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback) {
                callback.invoke(origin,false,false)
                if(live() && current===view)notice="Location sharing is blocked in Hidden Browser. No location permission was granted."
            }
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean {
                if(!isUserGesture || !live() || current!==view || views.size>=8)return false
                val transport=resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup=newTab() ?: return false
                transport.webView=popup;resultMsg.sendToTarget();return true
            }
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {
                if(privateAppsOpen){callback.onReceiveValue(null);return true}
                cancelUpload()
                if(uk.co.traynor.privategallery.BuildConfig.DEBUG)android.util.Log.i("NativeChooser1762","callback mode=${params.mode} permission=${params.permissionMode} acceptCount=${params.acceptTypes.size} current=${current===webView} live=${live()}")
                if(!live() || current!==webView) {callback.onReceiveValue(null);return true}
                if(params.mode !in setOf(FileChooserParams.MODE_OPEN,FileChooserParams.MODE_OPEN_MULTIPLE) || params.permissionMode!=FileChooserParams.PERMISSION_MODE_READ) {
                    notice="Only read-only media uploads are supported. Folder, save and file-edit requests are blocked."
                    callback.onReceiveValue(null);return true
                }
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
        view.setDownloadListener {url,_,_,_,_ ->downloadCandidate=downloadSelection(view,url);pendingDownload=downloadCandidate;notice="Automatic downloads are paused. Confirm Save GET file for a reusable supported media/document URL. POST responses, blob files and one-use downloads cannot be replayed safely."}
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
        if(input.isEmpty())return
        val url=runCatching {HiddenConnectionPolicy.resolveAddress(input,mode) {query ->BrowserAddressPolicy.destinationFor(query,searchEngine).url}}
            .getOrNull()
        if(url==null){notice="Only public HTTP(S) addresses are supported. Onion sites require Tor Only.";return}
        clearAddressFocus?.invoke();(current ?: newTab())?.let {failures.remove(it);it.loadUrl(url)}
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
                        try {
                            require(bytes.size<=300_000)
                            val options=android.graphics.BitmapFactory.Options().apply {inMutable=true;inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888}
                            owned=checkNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,options))
                            check(checkNotNull(owned).isMutable)
                        } finally {bytes.fill(0)}
                    } finally {uploadPreviewSlots.release()}
                }
                if(uploadGeneration.get()!=generation || uploadQuery!=query || pendingUpload==null || item !in uploadItems || ended.get())return@launch
                if(!live())throw IllegalStateException("Preview session ended")
                val bitmap=checkNotNull(owned);thumbnailCache.put(cacheKey,bitmap);owned=null;callback(Result.success(bitmap))
            } catch(_:Exception) {if(uploadGeneration.get()==generation && uploadQuery==query && pendingUpload!=null)callback(Result.failure(IllegalStateException("Preview unavailable")))}
            finally {owned?.let {if(!it.isRecycled){try {if(it.isMutable)it.eraseColor(android.graphics.Color.TRANSPARENT)}finally{it.recycle()}}}}
        }
    }
    private fun cancelUpload() {
        uploadGeneration.incrementAndGet();uploadLoadJob?.cancel();uploadLoadJob=null
        val callback=pendingUpload;pendingUpload=null
        uploadLoading=false;uploadLoadError=null;uploadQuery="";uploadRestricted=0;uploadTypes=0;uploadTotal=0
        uploadItems=emptyList();confirmUploads=emptyList();selectedUploads=emptySet();uploadView=null
        callback?.onReceiveValue(null)
    }
    private var privateAppAuthentication by mutableStateOf<uk.co.traynor.privategallery.ui.PrivateAppAuthenticationRequest?>(null)
    private fun requestPrivateAppAuthentication(completed:(Boolean)->Unit) {
        check(ready && live())
        privateAppAuthentication?.done?.invoke(false)
        val view=current;val revision=navigationRevision
        // PIN derivation checks this fence on IO. Never call a WebView getter off Main:
        // every navigation/reload and selection already advances navigationRevision.
        val requestCurrent={ready && current===view && navigationRevision==revision && live()}
        lateinit var request:uk.co.traynor.privategallery.ui.PrivateAppAuthenticationRequest
        request=uk.co.traynor.privategallery.ui.PrivateAppAuthenticationRequest(true,requestCurrent,
            {pin ->try {
                check(ready && live())
                call(HiddenVpnBrowserBroker.VERIFY_PRIVATE_APP_PIN,write={it.writeString(pin.concatToString())},read={it.readInt()==1})
            } finally {pin.fill('\u0000')}},
            {accepted ->if(privateAppAuthentication===request) {privateAppAuthentication=null;completed(accepted && requestCurrent())}})
        privateAppAuthentication=request
    }

    private class PrivateAppIconSelection(val generation:Long,val result:(ByteArray?)->Unit)
    private var privateAppIconSelection by mutableStateOf<PrivateAppIconSelection?>(null)
    private fun requestPrivateAppIcon(result:(ByteArray?)->Unit) {
        check(ready && live() && privateAppsOpen)
        cancelUpload();cancelWebsiteDialog()
        finishPrivateAppIcon(null)
        privateAppIconSelection=PrivateAppIconSelection(uploadGeneration.incrementAndGet(),result)
    }
    private fun finishPrivateAppIcon(bytes:ByteArray?) {
        val selection=privateAppIconSelection;privateAppIconSelection=null
        if(selection==null){bytes?.fill(0);return}
        uploadGeneration.incrementAndGet()
        if(bytes==null || (ready && live()))selection.result(bytes) else {bytes.fill(0);selection.result(null)}
    }
    private var privateAppsOpen by mutableStateOf(false)
    private val privateAppTabs=mutableStateMapOf<WebView,uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App>()
    private var privateAppSettingsId by mutableStateOf<String?>(null)
    private var privateAppSwitcher by mutableStateOf(false)
    private var pendingScreenshot by mutableStateOf<(() -> Unit)?>(null)
    private fun requestScreenshot() {
        val view=current ?: return
        val revision=navigationRevision
        val owner=broker
        val authorized={broker===owner && current===view && navigationRevision==revision && ready && live() &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)}
        pendingScreenshot={
            view.postDelayed({
                BrowserVaultScreenshot.capture(window,view,true,authorized) {result ->
                    result.onFailure {notice=uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotPolicy.REFUSAL}
                        .onSuccess {source ->
                            notice="Saving screenshot encrypted in Hidden Vault…"
                            lifecycleScope.launch(Dispatchers.IO) {
                                val pipes=ParcelFileDescriptor.createReliablePipe()
                                try {
                                    check(authorized())
                                    // Transfer the reliable read endpoint silently. A normal local
                                    // close publishes a premature peer status and can make the
                                    // receiver observe a dead peer instead of the writer's completion.
                                    call(HiddenVpnBrowserBroker.SCREENSHOT,write={it.writeInt(1);it.writeTypedObject(pipes[0],android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)},read={Unit})
                                    ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use {output ->source.openStream().use {input ->
                                        val buffer=ByteArray(16*1024)
                                        try {while(true){check(authorized());val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count)}}finally{buffer.fill(0)}
                                    }}
                                } catch(_:Exception){
                                    runCatching {pipes[1].closeWithError("Screenshot cancelled")}
                                    handler.post {if(!ended.get())notice="Screenshot save cancelled or unavailable."}
                                }
                                finally {pipes.forEach {runCatching {it.close()}};source.onConsumed()}
                            }.invokeOnCompletion {source.onConsumed()}
                        }
                }
            },150)
        }
    }

    private fun openPrivateApps() { if(ready && live()){cancelUpload();cancelWebsiteDialog();privateAppsOpen=true} }

    private val privateAppsController by lazy {
        val owner=if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY)uk.co.traynor.privategallery.core.privateapps.PrivateAppStore.Owner.HIDDEN_TOR else uk.co.traynor.privategallery.core.privateapps.PrivateAppStore.Owner.HIDDEN_VPN
        uk.co.traynor.privategallery.core.privateapps.PrivateAppsController(owner,{ready && live()},
            {action,bytes,id ->call(HiddenVpnBrowserBroker.PRIVATE_APPS,write={parcel ->
                parcel.writeInt(action);parcel.writeInt(bytes?.size ?: -1);if(bytes!=null)parcel.writeByteArray(bytes);parcel.writeString(id)
            },read={parcel ->
                val size=parcel.readInt();require(size in 0..uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.MAX_BYTES)
                val data=ByteArray(size)
                try{parcel.readByteArray(data);uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.decode(data,mode==HiddenConnectionPolicy.Mode.TOR_ONLY)}finally{data.fill(0)}
            })},
            {url ->check(ready && live());check(HiddenConnectionPolicy.safeUrl(url,mode));cancelUpload();cancelWebsiteDialog();checkNotNull(newTab());navigate(url)},
            {url ->call(HiddenVpnBrowserBroker.DISCOVER_PRIVATE_APP,write={it.writeString(url)},read={parcel ->
                val size=parcel.readInt();require(size in 0..uk.co.traynor.privategallery.core.privateapps.PrivateAppCommands.MAX_COMMAND_BYTES)
                val bytes=ByteArray(size)
                try {parcel.readByteArray(bytes);uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.decode(bytes,mode==HiddenConnectionPolicy.Mode.TOR_ONLY).apps.single()}finally{bytes.fill(0)}
            })},onLaunchApp={app ->
                check(ready && live());check(HiddenConnectionPolicy.safeUrl(app.startUrl(),mode))
                cancelUpload();cancelWebsiteDialog()
                val view=checkNotNull(newTab())
                privateAppTabs[view]=app
                navigate(app.startUrl())
            },authenticate=::requestPrivateAppAuthentication,
            clearProfile={check(ready && live());terminate()},requestIcon=::requestPrivateAppIcon)
    }
    @Composable private fun Browser() {
        privateAppAuthentication?.let {uk.co.traynor.privategallery.ui.PrivateAppAuthentication(it)}
        val app=current?.let {privateAppTabs[it]}
        pendingScreenshot?.let {capture ->
            GalleryAlertDialog(onDismissRequest={pendingScreenshot=null},title={Text("Screenshot to Hidden Vault?")},
                text={Text("Save only the visible website area encrypted in Hidden Vault. Capture must be allowed in Hidden settings. Video, embedded and protected content cannot be captured. Tor pages with JavaScript disabled are unsupported.")},
                confirmButton={TextButton(onClick={pendingScreenshot=null;capture()}){Text("Save screenshot")}},
                dismissButton={TextButton(onClick={pendingScreenshot=null}){Text("Cancel")}})
        }
        if(privateAppsOpen)PrivateAppsLibrary(privateAppsController,{privateAppsOpen=false;finishPrivateAppIcon(null)})
        privateAppIconSelection?.let {selection ->key(selection) {
            HiddenPrivateAppIconPicker(selection.generation,
                isCurrent={privateAppIconSelection===selection && uploadGeneration.get()==selection.generation && ready && live()},
                loadPage={query,offset ->checkNotNull(call(HiddenVpnBrowserBroker.LIST,write={parcel ->
                    parcel.writeLong(selection.generation);parcel.writeString(query);parcel.writeInt(offset);parcel.writeStringArray(arrayOf("image/*"))
                },read={it.readString()})).also{require(it.length<=200_000)}},
                loadPreview={query,id ->checkNotNull(call(HiddenVpnBrowserBroker.PREVIEW,write={parcel ->
                    parcel.writeLong(selection.generation);parcel.writeString(query);parcel.writeString(id)
                },read={it.createByteArray()}))},onResult={bytes ->
                    if(privateAppIconSelection===selection)finishPrivateAppIcon(bytes) else bytes?.fill(0)
                })
        }}
        privateAppSettingsId?.let { id ->PrivateAppSettings(privateAppsController,id,{updated ->
            privateAppTabs.keys.toList().forEach { view ->if(privateAppTabs[view]?.id()==updated.id())privateAppTabs[view]=updated }
        },{library();menu=true},{privateAppSettingsId=null}) }
        if(privateAppSwitcher)PrivateAppSwitcher(views.mapNotNull {view ->privateAppTabs[view]?.let {System.identityHashCode(view).toString() to it}},System.identityHashCode(current).toString(),
            {id ->views.firstOrNull {System.identityHashCode(it).toString()==id}?.let {select(it)}},::openPrivateApps,{privateAppSwitcher=false})
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
                Text("Hidden Browser · ${mode.indicator}",Modifier.weight(1f).padding(vertical=16.dp),style=MaterialTheme.typography.titleMedium)
                if(uk.co.traynor.privategallery.ui.PrivacyShieldPreferences(this@HiddenVpnBrowserActivity).let {it.enabled && it.quickLock})TextButton(onClick={panic()}){Text("Quick lock")}
                if(app==null)TextButton(onClick=::openPrivateApps,enabled=ready){Text("Private Apps")}
                TextButton(onClick={terminate()}){Text("Done")}
            }
            val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
            SideEffect {clearAddressFocus={focus.clearFocus();keyboard?.hide();addressFocused=false}}
            if(app!=null)PrivateAppControls(app,current?.url.orEmpty(),"Hidden Vault · ${mode.indicator}",ready,
                current?.canGoBack()==true,current?.canGoForward()==true,busy,
                {current?.let {failures.remove(it);it.goBack()}},{current?.let {failures.remove(it);it.goForward()}},
                {current?.let {if(busy){pageDeadlines[it]?.visible();it.stopLoading();loadingTabs[it]=false;busy=false;pageLoadProgress=100}else {failures.remove(it);it.reload()}}},
                {cancelUpload();cancelWebsiteDialog();navigate(app.startUrl())},::openPrivateApps,
                {cancelUpload();cancelWebsiteDialog();privateAppSwitcher=true},
                {cancelUpload();cancelWebsiteDialog();privateAppSettingsId=app.id()},onScreenshot=::requestScreenshot)
            if(app==null || app.addressBar())BrowserAddressBar(address,{address=it},ready,busy,{navigate(address)},{current?.let {if(busy){pageDeadlines[it]?.visible();it.stopLoading();loadingTabs[it]=false;busy=false;pageLoadProgress=100}else {failures.remove(it);it.reload()}}},{addressFocused=it})
            if(busy) {
                LinearProgressIndicator(progress={pageLoadProgress/100f},modifier=Modifier.fillMaxWidth())
                Text("Loading website…",Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.bodySmall)
            }
            Text(notice,Modifier.heightIn(max=88.dp).verticalScroll(rememberScrollState()).padding(8.dp),style=MaterialTheme.typography.bodySmall)
            if(findOpen)HiddenFindBar(findQuery,findResult,{findQuery=it;current?.findAllAsync(it)},{current?.findNext(it)},{findOpen=false;current?.clearMatches()})
            Box(Modifier.weight(1f).fillMaxWidth()) {
                @Suppress("UNUSED_VARIABLE") val pageRevision=navigationRevision
                if(!ready)Text("Establishing private browser…",Modifier.padding(24.dp))
                current?.takeUnless {!busy && it.url.isNullOrBlank()}?.let {view ->key(view) {AndroidView(factory={
                    // Keep clipping in the native hierarchy. A Compose graphics-layer clip can
                    // leave WebView virtual descendants with zero-height bounds after Fold resize.
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    android.widget.FrameLayout(this@HiddenVpnBrowserActivity).apply {
                        clipChildren=true;clipToPadding=true
                        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            if(right-left != oldRight-oldLeft || bottom-top != oldBottom-oldTop) {
                                // Publish native descendant geometry only after Compose has applied
                                // the new Fold bounds; configuration callbacks precede this layout.
                                view.post {
                                    if(live() && view === current && view.isAttachedToWindow) {
                                        view.requestLayout();view.invalidate()
                                        view.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                                    }
                                }
                            }
                        }
                        addView(view,android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },modifier=Modifier.fillMaxSize())};failures[view]?.let {detail ->BrowserPageError(detail,{failures.remove(view);view.reload()},{library();menu=true})}}
                if(ready && (current==null || (!busy && current !in failures && current?.url.isNullOrBlank())))HiddenNativeStartPage(mode==HiddenConnectionPolicy.Mode.TOR_ONLY)
            }
            @Suppress("UNUSED_VARIABLE") val revision=navigationRevision
            if(app!=null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                TextButton(onClick={cancelUpload();cancelWebsiteDialog();tabList=true}){Text("All tabs")}
                TextButton(onClick={cancelUpload();cancelWebsiteDialog();library();menu=true}){Text("Browser tools")}
            } else BrowserNavigationToolbar(current?.canGoBack()==true,current?.canGoForward()==true,views.size,{current?.let {failures.remove(it);it.goBack()}},{current?.let {failures.remove(it);it.goForward()}},{showLibrary("Bookmarks")},{tabList=true},{library();menu=true},{library();menu=true})
        }

        if(menu)BrowserPanel("Browser settings","Hidden ${mode.indicator}",{menu=false}) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                TextButton(onClick={menu=false;requestScreenshot()},enabled=ready){Text("Screenshot to Hidden Vault")}
                HiddenNativeSettingsContent(mode==HiddenConnectionPolicy.Mode.TOR_ONLY,mode.indicator,saveHistory,desktop,
            bookmark={current?.url?.let {library(2,it)};menu=false},bookmarks={showLibrary("Bookmarks");menu=false},history={showLibrary("History");menu=false},
            setHistory={library(4,enabled=it)},clearHistory={clearHistoryConfirm=true},toggleDesktop={desktop=!desktop;current?.let {desktopTabs[it]=desktop};current?.settings?.apply {useWideViewPort=desktop;loadWithOverviewMode=desktop;userAgentString=BrowserSecurityPolicy.userAgent(if(desktop)BrowserUserAgentMode.DESKTOP else BrowserUserAgentMode.MOBILE,WebSettings.getDefaultUserAgent(this@HiddenVpnBrowserActivity))};current?.reload()},
            download={pendingDownload=downloadCandidate?.takeIf {downloadSelectionCurrent(it)} ?: current?.let {view ->view.url?.let {downloadSelection(view,it)}};menu=false},find={findOpen=true;menu=false;current?.setFindListener {active,count,done ->if(done)findResult=if(count==0)"No matches" else "${active+1} of $count matches"}},downloads={downloadsOpen=true;menu=false},searchEngine=searchEngine,setSearchEngine={searchEngine=it},setMode={requestedMode=it},clearClose={if(library(6)!=null)terminate()})}
        }
        requestedMode?.let {selected ->GalleryAlertDialog(onDismissRequest={requestedMode=null},title={Text("Switch to ${selected.label}?")},
            text={Text("All tabs will close and transfers will stop. ${selected.label} must establish and verify a new connection before you can browse. VPN + Tor is unavailable.")},
            confirmButton={TextButton(onClick={requestedMode=null;terminate(switchMode=selected)}){Text("Close tabs and switch")}},
            dismissButton={TextButton(onClick={requestedMode=null}){Text("Keep current mode")}})}
        if(tabList) {
            val snapshot=views.toList()
            BrowserTabsPanel(snapshot.map {BrowserTab(id=System.identityHashCode(it).toString(),url=it.url.orEmpty(),title=it.title.orEmpty())},System.identityHashCode(current).toString(),
                onSelect={id ->snapshot.firstOrNull {System.identityHashCode(it).toString()==id}?.let {select(it)};tabList=false},
                onRemove={id ->snapshot.firstOrNull {System.identityHashCode(it).toString()==id}?.let {view ->cancelWebsiteDialog();cancelUpload();views.remove(view);privateAppTabs.remove(view);desktopTabs.remove(view);failures.remove(view);loadingTabs.remove(view);pageDeadlines.remove(view);view.stopLoading();view.destroy();if(current===view){current=null;views.lastOrNull()?.let {select(it)};if(current==null){address="";busy=false}}}},
                onNew={newTab();tabList=false},onClose={tabList=false})
        }
        libraryKind?.let {kind ->
            val open:(String)->Unit={url ->navigate(url);libraryKind=null}
            if(kind=="Bookmarks")BrowserBookmarksPanel(libraryItems.mapIndexed {index,url ->BrowserBookmark(url,libraryTitles.getOrNull(index).orEmpty().ifBlank {browserHost(url)},url,libraryTimes.getOrElse(index){0})},open,{url ->if(library(3,url)!=null)showLibrary(kind)},{libraryKind=null})
            else BrowserHistoryPanel(libraryItems.mapIndexed {index,url ->BrowserHistoryEntry(url,libraryTitles.getOrNull(index).orEmpty().ifBlank {browserHost(url)},url,libraryTimes.getOrElse(index){0})},saveHistory,open,{if(library(5)!=null)showLibrary(kind)},{libraryKind=null})
        }
        if(clearHistoryConfirm)GalleryAlertDialog(onDismissRequest={clearHistoryConfirm=false},title={Text("Clear browsing history?")},text={Text("Removes saved visits in this Hidden mode. Bookmarks and open tabs are kept.")},confirmButton={TextButton(onClick={library(5);clearHistoryConfirm=false}){Text("Clear")}},dismissButton={TextButton(onClick={clearHistoryConfirm=false}){Text("Cancel")}})
        if(downloadsOpen)HiddenDownloadsDialog(observedDownloadStatus+(if(downloadRunning) "\n$downloadBytes bytes received"+(if(downloadTotal>=0)" of $downloadTotal" else "") else ""),
            {downloadsOpen=false;manageDownloads()},{downloadsOpen=false},
            if(downloadRunning)({runCatching {check(live());call(HiddenVpnBrowserBroker.DOWNLOAD_CANCEL,read={Unit})};Unit})else null)

        pendingDownload?.let {selection ->GalleryAlertDialog(properties=androidx.compose.ui.window.DialogProperties(securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn),onDismissRequest={pendingDownload=null},
            title={Text("Save this GET file URL?")},text={Text("A fresh GET request will save supported media, PDF or plain text encrypted in Hidden Vault. This cannot save POST responses, blob URLs, segmented media or one-use download responses. No public file is created.")},
            confirmButton={TextButton(onClick={pendingDownload=null;try {
                check(downloadSelectionCurrent(selection));val cookies=profile!!.cookieManager.getCookie(selection.url).orEmpty()
                call(HiddenVpnBrowserBroker.DOWNLOAD,write={it.writeString(selection.url);it.writeString(cookies);it.writeString(selection.view.settings.userAgentString.orEmpty())},read={Unit})
                activeDownloadOwner=selection
                notice="GET download queued into Hidden Vault. Changing tabs or navigating cancels this download; unsupported or failed files are not saved."
            } catch(_:Exception){notice="Download rejected: its tab, document or session changed."}}){Text("Save GET file")}},dismissButton={TextButton(onClick={pendingDownload=null}){Text("Cancel")}})}
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
    private fun manageDownloads() { terminate(manageDownloads=true) }
    private fun terminate(notify:Boolean=true,reason:Int=HiddenVpnBrowserBroker.RETURN,manageDownloads:Boolean=false,switchMode:HiddenConnectionPolicy.Mode?=null) {
        if(!ended.compareAndSet(false,true))return
        val stoppedAt=SystemClock.elapsedRealtime()
        privateAppAuthentication?.done?.invoke(false);privateAppAuthentication=null
        ready=false;handler.removeCallbacksAndMessages(null);cancelWebsiteDialog();cancelUpload();finishPrivateAppIcon(null)
        thumbnailCache.snapshot().values.forEach {if(it.isMutable && !it.isRecycled)it.eraseColor(android.graphics.Color.TRANSPARENT)}
        thumbnailCache.evictAll()
        pendingScreenshot=null
        pendingDownload=null;downloadCandidate=null;activeDownloadOwner=null
        privateAppsOpen=false;privateAppSettingsId=null;privateAppSwitcher=false;privateAppTabs.clear()
        views.toList().forEach {runCatching {it.settings.blockNetworkLoads=true;it.stopLoading();it.destroy()}};views.clear();pageDeadlines.clear();current=null;libraryItems=emptyList();libraryKind=null
        profile?.serviceWorkerController?.serviceWorkerWebSettings?.blockNetworkLoads=true
        // Parent revokes transport before a single asynchronous deletion operation is started.
        if(notify)runCatching {call(HiddenVpnBrowserBroker.END,write={it.writeInt(reason);it.writeLong(stoppedAt);it.writeInt(if(manageDownloads)1 else 0);it.writeString(switchMode?.name)},read={Unit})}
        if(reason!=HiddenVpnBrowserBroker.RETURN) {
            // Remove the Activity record before killing its single-use process. Otherwise a
            // rejected cold launch is repeatedly recreated as the still-pending top Activity.
            try {finish()} finally {Process.killProcess(Process.myPid())}
            return
        }
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
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Retain the authenticated WebView/profile across Fold changes, but refresh its
        // legacy View rendering after the host configuration/Compose bounds change.
        if(!live())return
        current?.let {view ->view.requestLayout();view.invalidate()}
    }
    override fun onResume(){super.onResume();captureGuard.resume()}
    override fun onPause(){captureGuard.pause();super.onPause()}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);captureGuard.focusChanged(hasFocus)}
    override fun onUserLeaveHint(){captureGuard.userLeaving();super.onUserLeaveHint()}
    override fun onStop() {super.onStop();terminate(reason=HiddenVpnBrowserBroker.BACKGROUND)}
    override fun onDestroy() {captureGuard.close();if(!ended.get())terminate(reason=HiddenVpnBrowserBroker.BACKGROUND);super.onDestroy()}
}
