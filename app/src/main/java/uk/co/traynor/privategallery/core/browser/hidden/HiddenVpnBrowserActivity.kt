package uk.co.traynor.privategallery.core.browser.hidden

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

/** A process lifetime is exactly one authenticated immutable VPN lease. Never restores a URL. */
class HiddenVpnBrowserActivity:ComponentActivity() {
    companion object {private val processAdmission=HiddenVpnProcessAdmission()}
    private val ended=AtomicBoolean()
    private val handler=Handler(Looper.getMainLooper())
    private var broker:IBinder?=null
    private val endpoint=object:Binder() {
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            if(code!=HiddenVpnBrowserBroker.CHILD_SHUTDOWN)return super.onTransact(code,data,reply,flags)
            data.enforceInterface(HiddenVpnBrowserBroker.CHILD_DESCRIPTOR)
            check(Binder.getCallingUid()==Process.myUid())
            handler.post {terminate(false)}
            return true
        }
    }
    private var storageLease:HiddenVpnStorageCleanup.Lease?=null
    private var storageId=""
    private val shutdownFinished=AtomicBoolean()
    private var network:Network?=null
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
    private val library=HiddenSessionLibrary()
    private var pendingDownload by mutableStateOf<String?>(null)
    private var downloadCandidate:String?=null
    private var pendingUpload:ValueCallback<Array<Uri>>?=null
    private var uploadOrigin=""
    private var uploadItems by mutableStateOf<List<UploadItem>>(emptyList())
    private var confirmUploads by mutableStateOf<List<UploadItem>>(emptyList())
    private var selectedUploads by mutableStateOf<Set<String>>(emptySet())
    private var multipleUploads=false
    private var uploadView:WebView?=null
    private data class WebsiteDialog(val view:WebView,val kind:String,val message:String,val result:JsResult,val default:String="")
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
    private data class UploadItem(val id:String,val name:String,val mime:String)
    private val thumbnailCache=object:android.util.LruCache<String,android.graphics.Bitmap>(8*1024*1024) {override fun sizeOf(key:String,value:android.graphics.Bitmap)=value.allocationByteCount}
    private val death=IBinder.DeathRecipient {handler.post {terminate(false)}}
    private val screenOff=object:BroadcastReceiver() {override fun onReceive(context:Context,intent:Intent) {terminate(reason=HiddenVpnBrowserBroker.SCREEN_OFF)}}
    private val networkEvents=object:ConnectivityManager.NetworkCallback() {
        override fun onLost(lost:Network) {if(lost==network)handler.post {terminate()}}
        override fun onCapabilitiesChanged(changed:Network,caps:NetworkCapabilities) {
            if(changed==network && (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || caps.ownerUid!=Process.myUid()))handler.post {terminate()}
        }
    }
    private val heartbeat=object:Runnable {override fun run() {if(!live()){terminate(false);return};handler.postDelayed(this,250)}}
    private fun <T> call(code:Int,write:(Parcel)->Unit={},read:(Parcel)->T):T {
        val request=Parcel.obtain();val reply=Parcel.obtain()
        try {request.writeInterfaceToken(HiddenVpnBrowserBroker.DESCRIPTOR);write(request);check(checkNotNull(broker).transact(code,request,reply,0));reply.readException();return read(reply)} finally {request.recycle();reply.recycle()}
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
    private fun showWebsiteDialog(view:WebView,kind:String,message:String,result:JsResult,default:String=""):Boolean {
        cancelWebsiteDialog()
        if(!live() || current!==view) {result.cancel();return true}
        websiteDialog=WebsiteDialog(view,kind,message.take(8000),result,default);promptText=default.take(8000);return true
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
                network=parcel.readTypedObject(Network.CREATOR);proxyPort=parcel.readInt();proxyPassword=checkNotNull(parcel.readString());storageId=checkNotNull(parcel.readString())
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
                if(!live()){terminate(false);return@setProxyOverride}
                ready=true;notice="VPN only · session data stays separate from Primary";newTab()
            }
        } catch(_:Exception) {terminate()}
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab():WebView? {
        if(!ready || !live() || views.size>=8)return null
        val view=WebView(this)
        WebViewCompat.setProfile(view,profileName)
        view.settings.apply {
            blockNetworkLoads=true;javaScriptEnabled=true;domStorageEnabled=true
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
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean = !live() || !HiddenConnectionPolicy.safeUrl(request.url.toString(),HiddenConnectionPolicy.Mode.VPN_ONLY)
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? =
                if(!live() || !HiddenConnectionPolicy.safeUrl(request.url.toString(),HiddenConnectionPolicy.Mode.VPN_ONLY))HiddenHttpTransport.blocked() else null
            override fun onPageStarted(view:WebView,url:String?,icon:android.graphics.Bitmap?) {if(websiteDialog?.view===view || websiteAuth?.view===view || resubmit?.view===view)cancelWebsiteDialog();if(uploadView===view)cancelUpload();if(current===view){downloadCandidate=null;busy=true;address=url.orEmpty()}}
            override fun onPageFinished(view:WebView,url:String?) {if(current===view){busy=false;address=url.orEmpty()};if(url!=null && HiddenConnectionPolicy.safeUrl(url,HiddenConnectionPolicy.Mode.VPN_ONLY))library.visit(url,view.title.orEmpty())}
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
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {terminate();return true}
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage)=true
            override fun onJsAlert(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,"alert",message,result)
            override fun onJsConfirm(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,"confirm",message,result)
            override fun onJsPrompt(view:WebView,url:String,message:String,defaultValue:String,result:JsPromptResult)=showWebsiteDialog(view,"prompt",message,result,defaultValue)
            override fun onJsBeforeUnload(view:WebView,url:String,message:String,result:JsResult)=showWebsiteDialog(view,"leave",message,result)
            override fun onPermissionRequest(request:PermissionRequest) {request.deny()}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback) {callback.invoke(origin,false,false)}
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message):Boolean {
                if(!isUserGesture || !live() || current!==view || views.size>=8)return false
                val transport=resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup=newTab() ?: return false
                transport.webView=popup;resultMsg.sendToTarget();return true
            }
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {
                pendingUpload?.onReceiveValue(null);pendingUpload=null
                if(!live() || params.mode !in setOf(FileChooserParams.MODE_OPEN,FileChooserParams.MODE_OPEN_MULTIPLE)) {callback.onReceiveValue(null);return true}
                uploadOrigin=webView.url.orEmpty()
                if(!HiddenConnectionPolicy.safeUrl(uploadOrigin,HiddenConnectionPolicy.Mode.VPN_ONLY)) {callback.onReceiveValue(null);return true}
                return try {
                    val array=JSONArray(call(HiddenVpnBrowserBroker.LIST,read={it.readString()}))
                    uploadItems=(0 until array.length()).map {array.getJSONObject(it)}.filter {BrowserUploadPolicy.accepts(it.getString("mime"),params.acceptTypes)}.map {UploadItem(it.getString("id"),it.getString("name"),it.getString("mime"))}
                    selectedUploads=emptySet();multipleUploads=params.mode==FileChooserParams.MODE_OPEN_MULTIPLE;uploadView=webView
                    pendingUpload=callback;true
                } catch(_:Exception) {callback.onReceiveValue(null);true}
            }
        }
        view.setDownloadListener {url,_,_,_,_ ->downloadCandidate=url.takeIf {HiddenConnectionPolicy.safeUrl(it,HiddenConnectionPolicy.Mode.VPN_ONLY)};pendingDownload=downloadCandidate;notice="Automatic downloads are paused. Use Save GET media for a reusable image/video URL. POST responses, blob files and one-use downloads cannot be replayed safely."}
        profile!!.serviceWorkerController.serviceWorkerWebSettings.apply {blockNetworkLoads=false;allowContentAccess=false;allowFileAccess=false;cacheMode=WebSettings.LOAD_NO_CACHE}
        profile!!.serviceWorkerController.setServiceWorkerClient(object:ServiceWorkerClient() {
            override fun shouldInterceptRequest(request:WebResourceRequest):WebResourceResponse? = if(!live() || !HiddenConnectionPolicy.safeUrl(request.url.toString(),HiddenConnectionPolicy.Mode.VPN_ONLY))HiddenHttpTransport.blocked() else null
        })
        check(live());view.settings.blockNetworkLoads=false;views.add(view);select(view);address=""
        return view
    }
    private fun navigate(value:String) {
        if(!ready || !live())return
        val input=value.trim()
        val url=when {input.contains("://")->input;input.contains('.') && !input.contains(' ')->"https://$input";else->"https://duckduckgo.com/?q="+java.net.URLEncoder.encode(input,"UTF-8")}
        if(!HiddenConnectionPolicy.safeUrl(url,HiddenConnectionPolicy.Mode.VPN_ONLY)){notice="Only public HTTP(S) addresses are supported. Onion sites require Tor Only.";return}
        current?.loadUrl(url)
    }
    private fun loadUploadPreview(item:UploadItem,callback:(Result<android.graphics.Bitmap>)->Unit) {
        thumbnailCache.get(item.id)?.takeUnless {it.isRecycled}?.let {callback(Result.success(it));return}
        lifecycleScope.launch {
            var owned:android.graphics.Bitmap?=null
            try {
                owned=withContext(Dispatchers.IO) {
                    val bytes=checkNotNull(call(HiddenVpnBrowserBroker.PREVIEW,write={it.writeString(item.id)},read={it.createByteArray()}))
                    try {require(bytes.size<=300_000);checkNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size))} finally {bytes.fill(0)}
                }
                if(!live() || pendingUpload==null || item !in uploadItems)throw IllegalStateException("Preview session ended")
                val bitmap=checkNotNull(owned);thumbnailCache.put(item.id,bitmap);owned=null;callback(Result.success(bitmap))
            } catch(_:Exception) {callback(Result.failure(IllegalStateException("Preview unavailable")))}
            finally {owned?.let {if(!it.isRecycled){it.eraseColor(android.graphics.Color.TRANSPARENT);it.recycle()}}}
        }
    }
    private fun cancelUpload() {pendingUpload?.onReceiveValue(null);pendingUpload=null;uploadItems=emptyList();confirmUploads=emptyList();selectedUploads=emptySet();uploadView=null}
    @Composable private fun Browser() {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
                Text("Hidden Browser · VPN",Modifier.weight(1f).padding(vertical=16.dp),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={menu=true}){Text("Settings")};TextButton(onClick={terminate()}){Text("Done")}
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
        if(menu)GalleryAlertDialog(onDismissRequest={menu=false},title={Text("Hidden VPN settings")},text={Column {
            Text("JavaScript, forms and POST run through the owned VPN. Tor remains separate with JavaScript off. Closing this window ends all tabs and session data.")
            TextButton(onClick={current?.let {library.bookmark(it.url.orEmpty(),it.title.orEmpty())};menu=false}){Text("Bookmark page")}
            TextButton(onClick={libraryKind="Bookmarks";libraryItems=library.bookmarks();menu=false}){Text("Bookmarks")}
            TextButton(onClick={libraryKind="History";libraryItems=library.history();menu=false}){Text("History")}
            TextButton(onClick={desktop=!desktop;current?.settings?.apply {useWideViewPort=desktop;loadWithOverviewMode=desktop;userAgentString=BrowserSecurityPolicy.userAgent(if(desktop)BrowserUserAgentMode.DESKTOP else BrowserUserAgentMode.MOBILE,WebSettings.getDefaultUserAgent(this@HiddenVpnBrowserActivity))};current?.reload()}){Text(if(desktop)"Desktop layout on" else "Desktop layout off")}
            TextButton(onClick={pendingDownload=downloadCandidate ?: current?.url;menu=false}){Text("Save GET media to Hidden Vault")}
            TextButton(onClick={terminate()}){Text("Clear session and close")}
        }},confirmButton={TextButton(onClick={menu=false}){Text("Done")}})
        if (tabList) GalleryAlertDialog(
            onDismissRequest = { tabList = false },
            title = { Text("Hidden tabs") },
            text = {
                Column {
                    views.toList().forEachIndexed { i, view ->
                        Row {
                            TextButton(onClick = { cancelWebsiteDialog(); cancelUpload(); current = view; address = view.url.orEmpty(); tabList = false }, modifier = Modifier.weight(1f)) {
                                Text(view.title?.take(60) ?: "Tab ${i + 1}")
                            }
                            TextButton(onClick = {
                                cancelWebsiteDialog(); cancelUpload(); views.remove(view); view.stopLoading(); view.destroy()
                                if (current === view) current = views.lastOrNull()
                            }) { Text("Close") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tabList = false }) { Text("Done") } },
        )
        libraryKind?.let {kind ->GalleryAlertDialog(onDismissRequest={libraryKind=null},title={Text(kind)},text={Column {libraryItems.forEach {url ->TextButton(onClick={navigate(url);libraryKind=null}){Text(url)}}}},confirmButton={TextButton(onClick={libraryKind=null}){Text("Done")}})}
        pendingDownload?.let {url ->GalleryAlertDialog(onDismissRequest={pendingDownload=null},title={Text("Save this GET media URL?")},text={Text("A fresh GET request will save supported image/video media encrypted in Hidden Vault. This cannot save POST responses, blob URLs, segmented media or one-use download responses. No public file is created.")},confirmButton={TextButton(onClick={pendingDownload=null;try {check(live());val cookies=profile!!.cookieManager.getCookie(url).orEmpty();call(HiddenVpnBrowserBroker.DOWNLOAD,write={it.writeString(url);it.writeString(cookies);it.writeString(current?.settings?.userAgentString.orEmpty())},read={Unit});notice="GET download queued into Hidden Vault. Unsupported or failed media is not saved."} catch(_:Exception){notice="Download rejected or session ended."}}){Text("Save GET media")}},dismissButton={TextButton(onClick={pendingDownload=null}){Text("Cancel")}})}
        if(pendingUpload!=null && confirmUploads.isEmpty())GalleryAlertDialog(
            scrollBody=false,
            onDismissRequest={cancelUpload()},title={Text("Choose from Hidden Vault")},
            text={LazyVerticalGrid(columns=GridCells.Adaptive(88.dp),modifier=Modifier.heightIn(max=360.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item(span={GridItemSpan(maxLineSpan)}) {
                    Column {
                        Text("Vault-only items cannot be uploaded. Only this authenticated Hidden Vault is shown.")
                        Text(if(multipleUploads)"Select up to four compatible items, then review the upload." else "Select one compatible item. Nothing is sent before confirmation.")
                        if(uploadItems.isEmpty())Text("No eligible Hidden media matches this website's accepted file types.")
                    }
                }
                items(uploadItems,key={it.id}) {item ->
                    uk.co.traynor.privategallery.ui.GalleryMediaSelectionTile(item.id,item.name,item.mime.startsWith("video/"),
                        item.id in selectedUploads,true,{callback ->loadUploadPreview(item,callback)}) {
                        if(multipleUploads)selectedUploads=if(item.id in selectedUploads)selectedUploads-item.id else (selectedUploads+item.id).take(4).toSet()
                        else confirmUploads=listOf(item)
                    }
                }
            }},
            confirmButton={if(multipleUploads)TextButton(enabled=selectedUploads.isNotEmpty(),onClick={confirmUploads=uploadItems.filter {it.id in selectedUploads}}){Text("Review upload")}},
            dismissButton={TextButton(onClick={cancelUpload()}){Text("Cancel")}})
        if(confirmUploads.isNotEmpty())GalleryAlertDialog(
            onDismissRequest={cancelUpload()},title={Text("Send media outside Hidden Vault?")},
            text={Text("Allow the page at ${Uri.parse(uploadOrigin).host} to access ${confirmUploads.size} selected item(s)? It can keep this media and send it to other destinations. This is an explicit copy outside your vault.")},
            confirmButton={TextButton(onClick={
                try {
                    check(live() && current===uploadView && uploadView?.url==uploadOrigin)
                    val uris=confirmUploads.map {item ->call(HiddenVpnBrowserBroker.UPLOAD,write={it.writeString(item.id)},read={Uri.parse(it.readString())})}.toTypedArray()
                    pendingUpload?.onReceiveValue(uris);pendingUpload=null;uploadItems=emptyList();confirmUploads=emptyList();selectedUploads=emptySet();uploadView=null
                } catch(_:Exception){cancelUpload();notice="Upload cancelled."}
            }){Text("Allow upload")}},dismissButton={TextButton(onClick={cancelUpload()}){Text("Cancel")}})
        websiteAuth?.let {auth ->GalleryAlertDialog(
            onDismissRequest={cancelWebsiteDialog()},title={Text("Sign in to ${auth.host}")},
            text={Column {Text(auth.realm);OutlinedTextField(authUser,{authUser=it.take(4096)},label={Text("Username")});OutlinedTextField(authPassword,{authPassword=it.take(4096)},label={Text("Password")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())}},
            confirmButton={TextButton(onClick={if(live() && current===auth.view && Uri.parse(auth.view.url).host==auth.host){auth.handler.proceed(authUser,authPassword);websiteAuth=null;authUser="";authPassword=""}else cancelWebsiteDialog()}){Text("Sign in")}},dismissButton={TextButton(onClick={cancelWebsiteDialog()}){Text("Cancel")}})}
        resubmit?.let {request ->GalleryAlertDialog(
            onDismissRequest={cancelWebsiteDialog()},title={Text("Resend form data?")},text={Text("Reloading this page can repeat a previous action, upload or purchase.")},
            confirmButton={TextButton(onClick={if(live() && current===request.view){resubmit=null;request.accept.sendToTarget()}else cancelWebsiteDialog()}){Text("Resend")}},dismissButton={TextButton(onClick={cancelWebsiteDialog()}){Text("Cancel")}})}
        websiteDialog?.let {dialog ->GalleryAlertDialog(
            onDismissRequest={cancelWebsiteDialog()},
            title={Text(if(dialog.kind=="leave")"Leave this website?" else "Website message · ${Uri.parse(dialog.view.url).host.orEmpty()}")},
            text={Column {Text(dialog.message);if(dialog.kind=="prompt")OutlinedTextField(promptText,{promptText=it.take(8000)},label={Text("Response")})}},
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
        views.toList().forEach {runCatching {it.settings.blockNetworkLoads=true;it.stopLoading();it.destroy()}};views.clear();current=null;library.clear()
        profile?.serviceWorkerController?.serviceWorkerWebSettings?.blockNetworkLoads=true
        // Parent revokes transport before a single asynchronous deletion operation is started.
        if(notify)runCatching {call(HiddenVpnBrowserBroker.END,write={it.writeInt(reason);it.writeLong(stoppedAt)},read={Unit})}
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
