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
import androidx.compose.foundation.layout.*
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
    private val ended=AtomicBoolean()
    private val handler=Handler(Looper.getMainLooper())
    private var broker:IBinder?=null
    private val endpoint=Binder()
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
    private var desktop by mutableStateOf(false)
    private data class UploadItem(val id:String,val name:String)
    private val death=IBinder.DeathRecipient {handler.post {terminate(false)}}
    private val screenOff=object:BroadcastReceiver() {override fun onReceive(context:Context,intent:Intent) {terminate()}}
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
    private fun cancelWebsiteDialog() {websiteDialog?.result?.cancel();websiteDialog=null;promptText=""}
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
        try {
            broker=checkNotNull(intent.extras?.getBinder(HiddenVpnBrowserBroker.EXTRA));intent.replaceExtras(Bundle())
            broker!!.linkToDeath(death,0)
            call(HiddenVpnBrowserBroker.CLAIM,write={it.writeStrongBinder(endpoint)},read={parcel ->
                network=parcel.readTypedObject(Network.CREATOR);proxyPort=parcel.readInt();proxyPassword=checkNotNull(parcel.readString())
            })
            val manager=getSystemService(ConnectivityManager::class.java)
            val pinned=checkNotNull(network)
            val caps=checkNotNull(manager.getNetworkCapabilities(pinned))
            check(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && caps.ownerUid==Process.myUid())
            check(manager.bindProcessToNetwork(pinned) && manager.boundNetworkForProcess==pinned)
            // Must precede provider feature queries, CookieManager, WebView and every other webkit API.
            WebView.setDataDirectorySuffix("hidden_vpn")
            check(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) && WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA) && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))
            HiddenProfileCleanup.removeRetiredContainers()
            profile=ProfileStore.getInstance().getOrCreateProfile(profileName)
            manager.registerNetworkCallback(NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).addTransportType(NetworkCapabilities.TRANSPORT_VPN).build(),networkEvents)
            registerReceiver(screenOff,IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED)
            handler.post(heartbeat)
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
    private fun newTab() {
        if(!ready || !live() || views.size>=8)return
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
            override fun onPageStarted(view:WebView,url:String?,icon:android.graphics.Bitmap?) {if(websiteDialog?.view===view)cancelWebsiteDialog();if(uploadView===view)cancelUpload();if(current===view){downloadCandidate=null;busy=true;address=url.orEmpty()}}
            override fun onPageFinished(view:WebView,url:String?) {if(current===view){busy=false;address=url.orEmpty()};if(url!=null && HiddenConnectionPolicy.safeUrl(url,HiddenConnectionPolicy.Mode.VPN_ONLY))library.visit(url,view.title.orEmpty())}
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError) {handler.cancel()}
            override fun onReceivedHttpAuthRequest(view:WebView,handler:HttpAuthHandler,host:String,realm:String) {
                if(live() && host=="127.0.0.1" && realm=="Hidden VPN session")handler.proceed("hidden",proxyPassword) else handler.cancel()
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
            override fun onCreateWindow(view:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message)=false
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {
                pendingUpload?.onReceiveValue(null);pendingUpload=null
                if(!live() || params.mode !in setOf(FileChooserParams.MODE_OPEN,FileChooserParams.MODE_OPEN_MULTIPLE)) {callback.onReceiveValue(null);return true}
                uploadOrigin=webView.url.orEmpty()
                if(!HiddenConnectionPolicy.safeUrl(uploadOrigin,HiddenConnectionPolicy.Mode.VPN_ONLY)) {callback.onReceiveValue(null);return true}
                return try {
                    val array=JSONArray(call(HiddenVpnBrowserBroker.LIST,read={it.readString()}))
                    uploadItems=(0 until array.length()).map {array.getJSONObject(it)}.filter {BrowserUploadPolicy.accepts(it.getString("mime"),params.acceptTypes)}.map {UploadItem(it.getString("id"),it.getString("name"))}
                    selectedUploads=emptySet();multipleUploads=params.mode==FileChooserParams.MODE_OPEN_MULTIPLE;uploadView=webView
                    pendingUpload=callback;true
                } catch(_:Exception) {callback.onReceiveValue(null);true}
            }
        }
        view.setDownloadListener {url,_,_,_,_ ->downloadCandidate=url.takeIf {HiddenConnectionPolicy.safeUrl(it,HiddenConnectionPolicy.Mode.VPN_ONLY)};notice="Automatic downloads are paused. Use Save GET media for a reusable image/video URL. POST responses, blob files and one-use downloads cannot be replayed safely."}
        profile!!.serviceWorkerController.serviceWorkerWebSettings.apply {blockNetworkLoads=false;allowContentAccess=false;allowFileAccess=false;cacheMode=WebSettings.LOAD_NO_CACHE}
        profile!!.serviceWorkerController.setServiceWorkerClient(object:ServiceWorkerClient() {
            override fun shouldInterceptRequest(request:WebResourceRequest):WebResourceResponse? = if(!live() || !HiddenConnectionPolicy.safeUrl(request.url.toString(),HiddenConnectionPolicy.Mode.VPN_ONLY))HiddenHttpTransport.blocked() else null
        })
        check(live());view.settings.blockNetworkLoads=false;views.add(view);select(view);address=""
    }
    private fun navigate(value:String) {
        if(!ready || !live())return
        val input=value.trim()
        val url=when {input.contains("://")->input;input.contains('.') && !input.contains(' ')->"https://$input";else->"https://duckduckgo.com/?q="+java.net.URLEncoder.encode(input,"UTF-8")}
        if(!HiddenConnectionPolicy.safeUrl(url,HiddenConnectionPolicy.Mode.VPN_ONLY)){notice="Only public HTTP(S) addresses are supported. Onion sites require Tor Only.";return}
        current?.loadUrl(url)
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
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
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
        if(tabList)GalleryAlertDialog(onDismissRequest={tabList=false},title={Text("Hidden tabs")},text={Column {views.toList().forEachIndexed {i,view ->Row {TextButton(onClick={select(view);tabList=false},modifier=Modifier.weight(1f)){Text(view.title?.take(60) ?: "Tab ${i+1}")};TextButton(onClick={cancelWebsiteDialog();if(uploadView===view)cancelUpload();views.remove(view);view.stopLoading();view.destroy();if(current===view){current=views.lastOrNull();address=current?.url.orEmpty()}}){Text("Close")}}}},confirmButton={TextButton(onClick={tabList=false}){Text("Done")}})
        libraryKind?.let {kind ->GalleryAlertDialog(onDismissRequest={libraryKind=null},title={Text(kind)},text={Column {libraryItems.forEach {url ->TextButton(onClick={navigate(url);libraryKind=null}){Text(url)}}}},confirmButton={TextButton(onClick={libraryKind=null}){Text("Done")}})}
        pendingDownload?.let {url ->GalleryAlertDialog(onDismissRequest={pendingDownload=null},title={Text("Save this GET media URL?")},text={Text("A fresh GET request will save supported image/video media encrypted in Hidden Vault. This cannot save POST responses, blob URLs, segmented media or one-use download responses. No public file is created.")},confirmButton={TextButton(onClick={pendingDownload=null;try {check(live());val cookies=profile!!.cookieManager.getCookie(url).orEmpty();call(HiddenVpnBrowserBroker.DOWNLOAD,write={it.writeString(url);it.writeString(cookies);it.writeString(current?.settings?.userAgentString.orEmpty())},read={Unit});notice="GET download queued into Hidden Vault. Unsupported or failed media is not saved."} catch(_:Exception){notice="Download rejected or session ended."}}){Text("Save GET media")}},dismissButton={TextButton(onClick={pendingDownload=null}){Text("Cancel")}})}
        if(pendingUpload!=null && confirmUploads.isEmpty())GalleryAlertDialog(
            onDismissRequest={cancelUpload()},title={Text("Choose Hidden media to upload")},
            text={Column {
                Text("Vault-only items are excluded. Nothing is uploaded until you confirm.")
                if(uploadItems.isEmpty())Text("No eligible Hidden media matches this website's request.")
                uploadItems.forEach {item ->
                    if(multipleUploads)Row {
                        Checkbox(checked=item.id in selectedUploads,onCheckedChange={checked ->selectedUploads=if(checked)selectedUploads+item.id else selectedUploads-item.id})
                        Text(item.name,Modifier.weight(1f).padding(top=12.dp))
                    } else TextButton(onClick={confirmUploads=listOf(item)}){Text(item.name)}
                }
            }},
            confirmButton={if(multipleUploads)TextButton(enabled=selectedUploads.isNotEmpty(),onClick={confirmUploads=uploadItems.filter {it.id in selectedUploads}}){Text("Review upload")}},
            dismissButton={TextButton(onClick={cancelUpload()}){Text("Cancel")}})
        if(confirmUploads.isNotEmpty())GalleryAlertDialog(
            onDismissRequest={cancelUpload()},title={Text("Send media outside Hidden Vault?")},
            text={Text("Send ${confirmUploads.size} selected item(s) to ${Uri.parse(uploadOrigin).host}? The website can keep this media. This is an explicit copy outside your vault.")},
            confirmButton={TextButton(onClick={
                try {
                    check(live() && current===uploadView && uploadView?.url==uploadOrigin)
                    val uris=confirmUploads.map {item ->call(HiddenVpnBrowserBroker.UPLOAD,write={it.writeString(item.id)},read={Uri.parse(it.readString())})}.toTypedArray()
                    pendingUpload?.onReceiveValue(uris);pendingUpload=null;uploadItems=emptyList();confirmUploads=emptyList();selectedUploads=emptySet();uploadView=null
                } catch(_:Exception){cancelUpload();notice="Upload cancelled."}
            }){Text("Allow upload")}},dismissButton={TextButton(onClick={cancelUpload()}){Text("Cancel")}})
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
    private fun terminate(notify:Boolean=true) {
        if(!ended.compareAndSet(false,true))return
        ready=false;handler.removeCallbacksAndMessages(null);cancelWebsiteDialog();cancelUpload()
        views.toList().forEach {runCatching {it.settings.blockNetworkLoads=true;it.stopLoading();it.destroy()}};views.clear();current=null;library.clear()
        if(notify)runCatching {call(HiddenVpnBrowserBroker.END,read={Unit})}
        broker?.let {runCatching {it.unlinkToDeath(death,0)}}
        runCatching {getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkEvents)}
        runCatching {unregisterReceiver(screenOff)}
        // Keep the process bound even during clearing; fresh startup uses another unique Profile.
        profile?.let {runCatching {HiddenProfileCleanup.clear(it)}}
        finish()
        if(Application.getProcessName().endsWith(":hidden_vpn_browser"))Process.killProcess(Process.myPid())
    }
    override fun onStop() {super.onStop();terminate()}
    override fun onDestroy() {terminate();super.onDestroy()}
}
