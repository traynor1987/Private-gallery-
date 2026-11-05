package uk.co.traynor.novaos.preview

import android.app.Application
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.*
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import uk.co.traynor.novaos.preview.browser.*

class PreviewApplication:Application(){
 override fun onCreate(){super.onCreate();WebView.setDataDirectorySuffix("nova_preview_v1")}
}
data class PreviewFullscreen(val tabId:String,val view:android.view.View,val callback:WebChromeClient.CustomViewCallback)
data class PreviewExternalRequest(val tabId:String,val profile:String,val url:String)
class PreviewTab(val id:String,val profile:String,val app:StoreEntry,val wrapper:MutableContextWrapper,val view:WebView){
 var friendsConcealed by mutableStateOf(false)
 var desktopApplied by mutableStateOf(false)
 var resumed=true
 var navigationRevision=0
 var touched by mutableLongStateOf(android.os.SystemClock.uptimeMillis())
 var mediaTitle by mutableStateOf("")
 var mediaState by mutableStateOf("none")
 var appMode by mutableStateOf(true)
 var title by mutableStateOf(app.name)
 var url by mutableStateOf(app.url)
 var progress by mutableIntStateOf(0)
 var back by mutableStateOf(false)
 var forward by mutableStateOf(false)
 var error by mutableStateOf<String?>(null)
}

/** Preview-only owner for retained V1 WebViews. No production browser/session APIs. */
class PreviewBrowserModel(application:Application):AndroidViewModel(application){
 var presentationBlocked=false
 var fullscreen by mutableStateOf<PreviewFullscreen?>(null);private set
 fun closeFullscreen(){val old=fullscreen;fullscreen=null;(old?.view?.parent as? ViewGroup)?.removeView(old.view);if(old!=null)runCatching{old.callback.onCustomViewHidden()}}
 val library=BrowserLibrary(application)
 val tabs=mutableStateListOf<PreviewTab>()
 var activeId by mutableStateOf<String?>(null)
 var notice by mutableStateOf<String?>(null)
 var upload:ValueCallback<Array<Uri>>?=null
 var pickFiles:(()->Unit)?=null
 val mediaSessions=PreviewMediaSessions(application)
 var excludedMediaToken:android.media.session.MediaSession.Token?=null
 var foreground=true
 var currentProfile="primary"
 var downloadRequest by mutableStateOf<Pair<PreviewTab,String>?>(null)
 var externalRequest by mutableStateOf<PreviewExternalRequest?>(null)
 private val last=mutableMapOf<String,String>()
 val active get()=tabs.firstOrNull{it.id==activeId}
 fun open(app:StoreEntry,profile:String):PreviewTab{
  val owner="$profile:${app.id}";val old=tabs.firstOrNull{it.id==last[owner]}?:tabs.firstOrNull{it.profile==profile&&it.app.id==app.id}
  val tab=old?:create(app,profile,true);select(tab);return tab
 }
 fun select(tab:PreviewTab){if(fullscreen?.tabId!=tab.id)closeFullscreen();tab.touched=android.os.SystemClock.uptimeMillis();activeId=tab.id;last["${tab.profile}:${tab.app.id}"]=tab.id;syncLifecycle()}
 fun syncLifecycle(){if(!foreground||tabs.none{it.id==mediaSessions.source?.tabId&&it.profile==currentProfile})mediaSessions.clear();tabs.forEach{val run=it.profile==currentProfile&&PreviewShellRules.keepRunning(foreground,it.id==activeId,it.mediaState=="playing");if(run!=it.resumed){if(run)it.view.onResume()else it.view.onPause();it.resumed=run}}}
 fun create(app:StoreEntry,profile:String,load:Boolean):PreviewTab{
  require(tabs.count{it.profile==profile&&it.app.id==app.id}<8){"Close a tab before adding more (8 per app)."}
  val wrapper=MutableContextWrapper(getApplication<Application>())
  val view=WebView(wrapper)
  val tab=PreviewTab(java.util.UUID.randomUUID().toString(),profile,app,wrapper,view)
  tab.appMode=app.id!="browser-v1"&&!getApplication<Application>().getSharedPreferences("nova_shell_v1",0).getBoolean("browser:$profile:${app.id}",false)
  tabs.add(tab)
  configure(tab)
  if(load)navigate(tab,app.url)
  syncLifecycle()
  return tab
 }
 fun setBrowserMode(tab:PreviewTab,browser:Boolean){
  getApplication<Application>().getSharedPreferences("nova_shell_v1",0).edit().putBoolean("browser:${tab.profile}:${tab.app.id}",browser).apply()
  tabs.filter{it.profile==tab.profile&&it.app.id==tab.app.id}.forEach{it.appMode=!browser}
 }
 fun recents(profile:String)=tabs.filter{it.profile==profile}.groupBy{it.app.id}.values.map{it.maxBy{tab->tab.touched}}.sortedByDescending{it.touched}
 fun player(profile:String)=tabs.filter{!friendsBlocked(it)&&it.profile==profile&&PreviewShellRules.mediaState(it.mediaState)}.maxWithOrNull(compareBy<PreviewTab>{PreviewShellRules.mediaPriority(it.mediaState)}.thenBy{it.touched})
 fun pollMedia(){
  if(!foreground){mediaSessions.clear();return}
  if(tabs.any{friendsBlocked(it)})mediaSessions.clear()else mediaSessions.refresh(tabs.toList(),currentProfile,foreground,excludedMediaToken)
  tabs.filter{it.profile==currentProfile}.toList().forEach{tab->
   if(friendsBlocked(tab)){tab.mediaState="none";tab.mediaTitle="";return@forEach}
   if(android.net.Uri.parse(tab.url).host=="accounts.google.com"){tab.mediaState="none";tab.mediaTitle="";syncLifecycle();return@forEach}
   // Read public Media Session metadata only. Never read DOM/forms/cookies or install action handlers.
   val revision=tab.navigationRevision
   tab.view.evaluateJavascript("""(()=>{try{const m=navigator.mediaSession;if(!m)return null;const t=m.metadata?.title;return JSON.stringify({state:['playing','paused'].includes(m.playbackState)?m.playbackState:'none',title:typeof t==='string'&&t.length<=512?t:''});}catch(e){return null;}})()"""){raw->
    if(tab !in tabs||friendsBlocked(tab)||tab.profile!=currentProfile||!foreground||tab.navigationRevision!=revision)return@evaluateJavascript
    if(raw==null||raw.length>2000){tab.mediaState="none";tab.mediaTitle="";syncLifecycle();return@evaluateJavascript}
    val value=runCatching{org.json.JSONTokener(raw).nextValue()}.getOrNull()
    val meta=if(value is String&&value.length<1000)runCatching{org.json.JSONObject(value)}.getOrNull()else null
    val state=meta?.optString("state")?:"none"
    tab.mediaState=if(PreviewShellRules.mediaState(state))state else "none"
    tab.mediaTitle=meta?.optString("title")?.takeIf{it.length<=512}.orEmpty()
    syncLifecycle()
   }
  }
 }
 fun requestExternal(tab:PreviewTab,url:String){
  tab.error="This Google OAuth authorization request requires a supported external browser. Its cookies remain in that browser; they cannot be copied into this preview."
  externalRequest=PreviewExternalRequest(tab.id,tab.profile,url)
 }
 private fun browserFriendsRequested(profile:String)=library.flag("friends-site:$profile:web.whatsapp.com")||library.flag("friends-required:$profile:browser-v1")
 fun friendsBlocked(tab:PreviewTab)=if(tab.app.id=="browser-v1")tab.friendsConcealed||(browserFriendsRequested(tab.profile)&&(ChatInspectorRules.origin(tab.url)||ChatInspectorRules.origin(tab.app.url)))else library.flag("friends-required:${tab.profile}:${tab.app.id}")
 private fun concealIfRequired(tab:PreviewTab,url:String=tab.url){
  if(tab.app.id=="browser-v1"&&browserFriendsRequested(tab.profile)&&ChatInspectorRules.origin(url))tab.friendsConcealed=true
  if(friendsBlocked(tab)){tab.view.visibility=android.view.View.INVISIBLE;tab.view.importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
 }
 fun requireFriends(tab:PreviewTab,required:Boolean){
  if(required)closeFullscreen()
  val affected=if(tab.app.id=="browser-v1"){
   library.setFlag("friends-site:${tab.profile}:web.whatsapp.com",required);library.setFlag("friends-required:${tab.profile}:browser-v1",false)
   tabs.filter{it.profile==tab.profile&&it.app.id=="browser-v1"&&(it.id==tab.id||it.friendsConcealed||ChatInspectorRules.origin(it.url)||ChatInspectorRules.origin(it.app.url))}
  }else {library.setFlag("friends-required:${tab.profile}:${tab.app.id}",required);tabs.filter{it.profile==tab.profile&&it.app.id==tab.app.id}}
  affected.forEach{if(it.app.id=="browser-v1")it.friendsConcealed=required;it.view.visibility=if(required)android.view.View.INVISIBLE else android.view.View.VISIBLE;it.view.importantForAccessibility=if(required)android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO}
  if(required)mediaSessions.clear()
 }
 fun desktopSite(tab:PreviewTab,url:String=tab.url)=CorrectionRules.desktopAllowed(url)&&library.flag("desktop:${tab.profile}:${CorrectionRules.site(url)}")
 fun applyDesktop(tab:PreviewTab,url:String){
  val enabled=desktopSite(tab,url)
  val original=WebSettings.getDefaultUserAgent(getApplication())
  val wanted=if(enabled)CorrectionRules.desktopUa(original)else original
  val changed=tab.view.settings.userAgentString!=wanted||tab.desktopApplied!=enabled
  if(tab.view.settings.userAgentString!=wanted)tab.view.settings.userAgentString=wanted
  if(changed){tab.view.settings.useWideViewPort=if(enabled)true else BrowserViewportPolicy.useWideViewport
  tab.view.settings.loadWithOverviewMode=if(enabled)true else BrowserViewportPolicy.loadWithOverview
  tab.view.setInitialScale(BrowserViewportPolicy.initialScale)}
  tab.desktopApplied=enabled
 }
 fun setDesktop(tab:PreviewTab,enabled:Boolean){
  val host=CorrectionRules.site(tab.url)
  if(host.isEmpty()||(enabled&&!CorrectionRules.desktopAllowed(tab.url))){tab.error="Desktop requests are unavailable on this authentication-provider page.";return}
  library.setFlag("desktop:${tab.profile}:$host",enabled)
  if(desktopSite(tab)!=enabled){tab.error="Unable to save this website preference.";return}
  reloadDesktop(tab)
 }
 fun back(tab:PreviewTab){val list=tab.view.copyBackForwardList();if(list.currentIndex>0){concealIfRequired(tab,list.getItemAtIndex(list.currentIndex-1).url);applyDesktop(tab,list.getItemAtIndex(list.currentIndex-1).url);tab.view.goBack()}}
 fun forward(tab:PreviewTab){val list=tab.view.copyBackForwardList();if(list.currentIndex<list.size-1){concealIfRequired(tab,list.getItemAtIndex(list.currentIndex+1).url);applyDesktop(tab,list.getItemAtIndex(list.currentIndex+1).url);tab.view.goForward()}}
 fun reloadDesktop(tab:PreviewTab){
  val url=if(desktopSite(tab)&&CorrectionRules.site(tab.url)=="web.whatsapp.com"&&Uri.parse(tab.url).path.orEmpty().startsWith("/mobile"))"https://web.whatsapp.com/"else tab.url
  applyDesktop(tab,url)
  if(url!=tab.url)navigate(tab,url)else tab.view.reload()
 }
 fun navigate(tab:PreviewTab,url:String){
  val safe=PreviewRules.https(url)
  concealIfRequired(tab,safe)
  if(PreviewAppPolicy.googleOAuthAuthorization(safe))requestExternal(tab,safe)else {applyDesktop(tab,safe);tab.view.loadUrl(safe)}
 }
 /** Settings mirror secureBrowserWebView/Primary V1, without VPN, diagnostics,
  * vault downloads, credentials, JS bridges, altered UA or cookie migration. */
 private fun configure(tab:PreviewTab){
  val view=tab.view
  concealIfRequired(tab,tab.app.url)
  view.layoutParams=ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
  view.isFocusable=true;view.isFocusableInTouchMode=true
  view.settings.apply{
   javaScriptEnabled=library.flag("js:${tab.profile}:${tab.app.id}",true);domStorageEnabled=true;databaseEnabled=false
   allowFileAccess=BrowserWebSecurityPolicy.fileAccessEnabled;allowContentAccess=BrowserWebSecurityPolicy.contentAccessEnabled
   allowFileAccessFromFileURLs=false;allowUniversalAccessFromFileURLs=false
   javaScriptCanOpenWindowsAutomatically=BrowserWebSecurityPolicy.automaticWindowOpeningEnabled
   setSupportMultipleWindows(BrowserWebSecurityPolicy.multipleWindowsEnabled)
   mediaPlaybackRequiresUserGesture=true;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
   useWideViewPort=BrowserViewportPolicy.useWideViewport;loadWithOverviewMode=BrowserViewportPolicy.loadWithOverview;textZoom=BrowserViewportPolicy.textZoomPercent;safeBrowsingEnabled=true
  }
  view.setInitialScale(BrowserViewportPolicy.initialScale)
  CookieManager.getInstance().apply{setAcceptCookie(true);setAcceptThirdPartyCookies(view,BrowserWebSecurityPolicy.thirdPartyCookiesEnabled)}
  fun update(){tab.back=view.canGoBack();tab.forward=view.canGoForward();tab.url=view.url?.takeIf{BrowserNavigationPolicy.isWebUrl(it)}?:tab.url}
  view.webViewClient=object:WebViewClient(){
   override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean{
    if(r.isForMainFrame&&PreviewAppPolicy.googleOAuthAuthorization(r.url.toString())){requestExternal(tab,r.url.toString());return true}
    val safe=BrowserNavigationPolicy.isWebUrl(r.url.toString())
    if(r.isForMainFrame&&safe){concealIfRequired(tab,r.url.toString());if(r.method=="GET")applyDesktop(tab,r.url.toString())}
    if(!safe)tab.error="This link needs another application or unsupported sign-in flow. Use the browser menu for a confirmed external-browser option."
    return !safe
   }
   override fun onPageStarted(v:WebView,url:String,icon:Bitmap?){concealIfRequired(tab,url);tab.navigationRevision++;tab.mediaTitle="";tab.mediaState="none";tab.url=url;tab.error=null;update()}
   override fun onPageFinished(v:WebView,url:String){if(v.progress>=100)applyDesktop(tab,url);update();library.add("history",tab.profile,tab.title,url);CookieManager.getInstance().flush()}
   override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError){if(r.isForMainFrame)tab.error="Page could not load. Check your connection, retry, or choose the external browser option."}
   override fun onReceivedHttpError(v:WebView,r:WebResourceRequest,e:WebResourceResponse){if(r.isForMainFrame&&e.statusCode>=400)tab.error="Website returned ${e.statusCode}. Embedded sign-in or browser compatibility may be restricted. External browsing is available from the menu."}
   override fun onReceivedSslError(v:WebView,handler:SslErrorHandler,e:SslError){handler.cancel();tab.error="Certificate error. Connection rejected; certificate bypass is unavailable."}
   override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean{notice="Web renderer stopped. Return Home and reopen the app.";close(tab,rendererGone=true);return true}
  }
  view.webChromeClient=object:WebChromeClient(){
   override fun onShowCustomView(custom:android.view.View,callback:CustomViewCallback){if(friendsBlocked(tab)||!foreground||presentationBlocked||tab.profile!=currentProfile||tab.id!=activeId){callback.onCustomViewHidden();return};closeFullscreen();fullscreen=PreviewFullscreen(tab.id,custom,callback)}
   override fun onHideCustomView(){if(fullscreen?.tabId==tab.id)closeFullscreen()}
   override fun onProgressChanged(v:WebView,value:Int){tab.progress=value;update()}
   override fun onReceivedTitle(v:WebView,title:String?){tab.title=title?.take(160)?:tab.app.name}
   override fun onCreateWindow(v:WebView,isDialog:Boolean,isUserGesture:Boolean,result:Message):Boolean{
    // Primary V1 defaults remain single-window. If WebView supplies a native
    // child request, retain its initiating app owner and constrain navigation.
    val transport=result.obj as? WebView.WebViewTransport?:return false
    if(tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}>=8)return false
    val child=create(tab.app,tab.profile,false);transport.webView=child.view;result.sendToTarget();select(child);return true
   }
   override fun onCloseWindow(window:WebView){tabs.firstOrNull{it.view===window}?.let{close(it)}}
   override fun onPermissionRequest(request:PermissionRequest){request.deny()}
   override fun onShowFileChooser(v:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean{
    if(pickFiles==null)return false
    upload?.onReceiveValue(null);upload=callback;pickFiles?.invoke();return true
   }
  }
  view.setDownloadListener{url,_,_,_,_->if(runCatching{PreviewRules.https(url)}.isSuccess)downloadRequest=tab to url else tab.error="This download type is unsupported. Public HTTPS files can be downloaded; blob/data links cannot."}
 }
 fun close(tab:PreviewTab,rendererGone:Boolean=false){
  if(mediaSessions.source?.tabId==tab.id)mediaSessions.clear()
  if(fullscreen?.tabId==tab.id)closeFullscreen()
  (tab.view.parent as? ViewGroup)?.removeView(tab.view);if(!rendererGone)tab.view.stopLoading();tab.view.destroy();tabs.remove(tab)
  if(activeId==tab.id)activeId=(tabs.lastOrNull{it.profile==tab.profile&&it.app.id==tab.app.id}?:tabs.filter{it.profile==tab.profile}.maxByOrNull{it.touched})?.id
  last.entries.removeAll{it.value==tab.id};syncLifecycle()
 }
 fun closeApp(profile:String,id:String){tabs.filter{it.profile==profile&&it.app.id==id}.toList().forEach{close(it)}}
 fun closeAll(){tabs.toList().forEach{close(it)}}
 fun detachActivityContexts(){tabs.forEach{it.wrapper.baseContext=getApplication<Application>()}}
 fun detachContexts(){foreground=false;syncLifecycle();detachActivityContexts()}
 override fun onCleared(){upload?.onReceiveValue(null);upload=null;closeAll()}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PreviewBrowser(pool:PreviewBrowserModel,onHome:()->Unit,onExternal:(String)->Unit,visible:Boolean=true,onInspect:(()->Unit)?=null){
 val tab=pool.active
 var settings by remember{mutableStateOf(false)}
 var libraryPage by remember{mutableStateOf<String?>(null)}
 var showTabs by remember{mutableStateOf(false)};var menu by remember{mutableStateOf(false)}
 val context=LocalContext.current
 if(tab==null){Column(Modifier.fillMaxSize().safeDrawingPadding()){PreviewHeader("Web apps","NovaOS Preview",onHome);Text(pool.notice?:"No open app tabs.",Modifier.padding(24.dp));Button(onClick=onHome,Modifier.padding(24.dp)){Text("Return Home")}};return}
 val friendsBlocked=pool.friendsBlocked(tab)
 var address by remember(tab.id,tab.url){mutableStateOf(tab.url)}
 LaunchedEffect(friendsBlocked){if(friendsBlocked){menu=false;showTabs=false;settings=false;libraryPage=null;pool.closeFullscreen()}}
 LaunchedEffect(visible){if(!visible){menu=false;showTabs=false;settings=false}}
 BackHandler(visible){if(tab.view.canGoBack())pool.back(tab)else onHome()}
 Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("preview-web-app").then(if(visible)Modifier else Modifier.clearAndSetSemantics{})){
 Column(Modifier.fillMaxSize()){
 if(!tab.appMode){
  Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onHome,modifier=Modifier.testTag("web-return-home")){Icon(Icons.Default.Home,"Return to Nova Home")}
   Column(Modifier.weight(1f)){Text(tab.app.name,style=MaterialTheme.typography.titleMedium,maxLines=1);Text("Ordinary web · NovaOS Preview",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   IconButton(onClick={showTabs=true}){BadgedBox(badge={Badge{Text(pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}.toString())}}){Icon(Icons.Default.Tab,"App tabs")}}
   Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Browser menu")};DropdownMenu(menu,{menu=false}){
    DropdownMenuItem(text={Text("History")},onClick={menu=false;libraryPage="history"})
    DropdownMenuItem(text={Text("Bookmarks")},onClick={menu=false;libraryPage="bookmarks"})
    DropdownMenuItem(text={Text("Bookmark this page")},onClick={menu=false;pool.library.add("bookmarks",tab.profile,tab.title,tab.url)})
    DropdownMenuItem(text={Text("Downloads")},onClick={menu=false;libraryPage="downloads"})
    DropdownMenuItem(text={Text("Desktop site · "+if(pool.desktopSite(tab))"On"else "Off")},modifier=Modifier.testTag("desktop-menu"),onClick={menu=false;pool.setDesktop(tab,!pool.desktopSite(tab))})
    DropdownMenuItem(text={Text("Settings")},onClick={menu=false;settings=true})
    DropdownMenuItem(text={Text("App Mode")},onClick={menu=false;pool.setBrowserMode(tab,false)})
    DropdownMenuItem(text={Text("Open externally…")},onClick={menu=false;onExternal(tab.url)})
    DropdownMenuItem(text={Text("New app tab")},onClick={menu=false;if(pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}<8)pool.select(pool.create(tab.app,tab.profile,true))else showTabs=true})
    DropdownMenuItem(text={Text("Close app tab")},onClick={menu=false;pool.close(tab)})
   }}
  }
  Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick={pool.back(tab)},enabled=tab.back){Icon(Icons.Default.ArrowBack,"Back")}
   OutlinedTextField(address,{address=it.take(2048)},Modifier.weight(1f),singleLine=true,textStyle=MaterialTheme.typography.bodySmall,shape=RoundedCornerShape(18.dp),trailingIcon={IconButton(onClick={runCatching{pool.navigate(tab,address)}.onFailure{tab.error=it.message}}){Icon(Icons.Default.ArrowForward,"Go")}})
   IconButton(onClick={if(tab.progress<100)tab.view.stopLoading()else tab.view.reload()}){Icon(if(tab.progress<100)Icons.Default.Close else Icons.Default.Refresh,if(tab.progress<100)"Stop"else "Reload")}
   IconButton(onClick={pool.forward(tab)},enabled=tab.forward){Icon(Icons.Default.ChevronRight,"Forward")}
  }
 }
  if(tab.progress in 1..99)LinearProgressIndicator(progress={tab.progress/100f},modifier=Modifier.fillMaxWidth())
  tab.error?.let{error->Surface(color=MaterialTheme.colorScheme.errorContainer){Column(Modifier.padding(12.dp)){Text(error,style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick={tab.error=null;tab.view.reload()}){Text("Retry")};TextButton(onClick={onExternal(tab.url)}){Text("External browser…")};TextButton(onClick={tab.error=null}){Text("Dismiss")}}}}}
  Box(Modifier.weight(1f).fillMaxWidth()){
   pool.tabs.filter{it.profile==tab.profile}.sortedBy{it.id==tab.id}.forEach{retained->key(retained.id){AndroidView(factory={
    (retained.view.parent as? ViewGroup)?.removeView(retained.view);retained.wrapper.baseContext=context;pool.syncLifecycle();retained.view
   },modifier=Modifier.fillMaxSize(),update={view->view.visibility=if(pool.friendsBlocked(retained))android.view.View.INVISIBLE else android.view.View.VISIBLE;view.importantForAccessibility=if(visible&&retained.id==tab.id&&!pool.friendsBlocked(retained))android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS},onReset=null,onRelease={view->(view.parent as? ViewGroup)?.removeView(view);retained.wrapper.baseContext=context.applicationContext})}}
  }
 }
 if(friendsBlocked&&visible)Surface(Modifier.fillMaxSize().testTag("friends-compatibility-error"),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Text("Friends Mode unavailable",style=MaterialTheme.typography.headlineSmall)
  Text("This preview has no verified WhatsApp allowlist adapter. Chat content is concealed here; no filtering, message deletion, replies or credential interception is performed.")
  Text("Normal WhatsApp browsing is unfiltered. This screen cannot certify authenticated-chat compatibility.",style=MaterialTheme.typography.bodySmall)
  Button(onClick=onHome){Text("Return Home")}
  TextButton(onClick={pool.requireFriends(tab,false)}){Text("Use normal unfiltered browsing")}
 }}
 if(tab.appMode&&visible&&!friendsBlocked)Box(Modifier.align(Alignment.TopEnd).padding(8.dp)){
  FilledTonalIconButton(onClick={menu=true},modifier=Modifier.size(48.dp).testTag("web-app-menu")){Icon(Icons.Default.MoreVert,"App menu")}
  DropdownMenu(menu,{menu=false}){
   DropdownMenuItem(text={Text(tab.app.name+" · Preview")},onClick={menu=false;settings=true})
   DropdownMenuItem(text={Text("Home")},modifier=Modifier.testTag("web-return-home"),onClick={menu=false;onHome()})
   DropdownMenuItem(text={Text("Back")},enabled=tab.back,onClick={menu=false;pool.back(tab)})
   DropdownMenuItem(text={Text("Refresh")},onClick={menu=false;tab.view.reload()})
   DropdownMenuItem(text={Text("App tabs")},onClick={menu=false;showTabs=true})
   DropdownMenuItem(text={Text("Open in Browser V1")},onClick={menu=false;val browser=pool.open(standaloneBrowser,tab.profile);pool.setBrowserMode(browser,true);pool.navigate(browser,tab.url)})
   DropdownMenuItem(text={Text("External browser…")},onClick={menu=false;onExternal(tab.url)})
   DropdownMenuItem(text={Text("Desktop site · "+if(pool.desktopSite(tab))"On"else "Off")},modifier=Modifier.testTag("desktop-menu"),onClick={menu=false;pool.setDesktop(tab,!pool.desktopSite(tab))})
   DropdownMenuItem(text={Text("App settings & compatibility")},onClick={menu=false;settings=true})
  }
 }
 }
 if(settings&&visible&&!friendsBlocked)ModalBottomSheet(onDismissRequest={settings=false}){
  Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp)){
   Text("${tab.app.name} settings",style=MaterialTheme.typography.headlineSmall)
   Row(verticalAlignment=Alignment.CenterVertically){Text("Request Desktop Site",Modifier.weight(1f));Switch(pool.desktopSite(tab),{pool.setDesktop(tab,it)},enabled=CorrectionRules.desktopAllowed(tab.url),modifier=Modifier.testTag("desktop-site-switch"))}
   Text("Saved for ${CorrectionRules.site(tab.url)} in this preview profile. Changing it reloads this page; other websites keep their own settings.",style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={pool.reloadDesktop(tab)},modifier=Modifier.testTag("desktop-reload")){Text(if(pool.desktopSite(tab))"Reload in Desktop Mode"else "Reload in Mobile Mode")}
   if(ChatInspectorRules.origin(tab.url)&&onInspect!=null)TextButton(onClick={settings=false;onInspect()},modifier=Modifier.testTag("browser-chat-inspector")){Text("Nova Chat Compatibility Inspector")}
   if(CorrectionRules.site(tab.url).endsWith("whatsapp.com"))TextButton(onClick={pool.requireFriends(tab,true);settings=false},modifier=Modifier.testTag("friends-status")){Text("Friends Mode compatibility status")}
   if(CorrectionRules.site(tab.url).endsWith("whatsapp.com"))Text("Friends Mode is not implemented in this preview. Normal WhatsApp browsing is unfiltered. No allowlist protection or authenticated-chat compatibility is claimed.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
   val key="js:${tab.profile}:${tab.app.id}"
   Row(verticalAlignment=Alignment.CenterVertically){Text("JavaScript",Modifier.weight(1f));Switch(pool.library.flag(key,true),{enabled->pool.library.setFlag(key,enabled);pool.tabs.filter{it.profile==tab.profile&&it.app.id==tab.app.id}.forEach{it.view.settings.javaScriptEnabled=enabled};tab.view.reload()})}
   if(tab.app.category=="Games"){
    listOf("landscape" to "Prefer landscape","fullscreen" to "Fullscreen (hide status bar)").forEach{(flag,label)->val k="$flag:${tab.profile}:${tab.app.id}";Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(pool.library.flag(k),{pool.library.setFlag(k,it)})}}
    Text("Touch, keyboard and gamepad input depend on the game. Landscape requests may be overridden by Android on the Fold inner screen. Nova and Android navigation remain available. Offline support is not promised.")
   }
   Text("Website fullscreen uses its documented WebView callback. Website mini-player and Android PiP are unavailable when provider eligibility cannot be established; use the website’s supported player. Nova Video supports user-selected local files. Ordinary preview browsing. Cookies and saved website progress persist locally, shared across demonstration profiles; this is not an encrypted vault. Embedded logins depend on the provider. No browser restrictions are bypassed.",Modifier.padding(vertical=16.dp))
   TextButton(onClick={pool.library.clear("history",tab.profile)}){Text("Clear browsing history")}
   Button(onClick={settings=false},Modifier.fillMaxWidth()){Text("Done")}
  }
 }
 if(libraryPage!=null&&visible&&!friendsBlocked)ModalBottomSheet(onDismissRequest={libraryPage=null}){
  val kind=libraryPage!!
  Column(Modifier.fillMaxWidth().heightIn(max=650.dp).padding(24.dp)){
   Text(kind.replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.headlineSmall)
   if(kind=="downloads")Text("Public HTTPS downloads are saved to Android Downloads. Open Files to view them. Sign-in cookies are not forwarded to the download service.",Modifier.padding(vertical=12.dp))
   val links=pool.library.links(kind,tab.profile)
   if(links.isEmpty())Text("Nothing here yet.",Modifier.padding(vertical=24.dp))
   LazyColumn(Modifier.weight(1f,false)){items(links,key={it.url}){link->ListItem(headlineContent={Text(link.title,maxLines=2)},supportingContent={Text(link.url,maxLines=2)},modifier=Modifier.clickable{if(kind!="downloads"){pool.navigate(tab,link.url);libraryPage=null}})}}
   TextButton(onClick={pool.library.clear(kind,tab.profile)}){Text("Clear list")}
   Button(onClick={libraryPage=null}){Text("Done")}
  }
 }
 pool.downloadRequest?.takeIf{it.first.profile==tab.profile&&visible}?.let{(owner,url)->AlertDialog(onDismissRequest={pool.downloadRequest=null},title={Text("Download file?")},text={Text("Save a public HTTPS file to Android Downloads. This uses Android’s download service without website login cookies.\n\n"+Uri.parse(url).host)},confirmButton={TextButton(onClick={pool.library.download(owner.profile,url,owner.title);pool.downloadRequest=null}){Text("Download")}},dismissButton={TextButton(onClick={pool.downloadRequest=null}){Text("Cancel")}})}
 pool.library.error?.takeIf{visible}?.let{message->AlertDialog(onDismissRequest={pool.library.error=null},title={Text("Browser")},text={Text(message)},confirmButton={TextButton(onClick={pool.library.error=null}){Text("Done")}})}
 if(showTabs&&visible&&!friendsBlocked)ModalBottomSheet(onDismissRequest={showTabs=false}){
  Column(Modifier.fillMaxWidth().heightIn(max=500.dp).padding(20.dp)){
   Text("${tab.app.name} tabs",style=MaterialTheme.typography.headlineSmall)
   LazyColumn(Modifier.weight(1f,false)){items(pool.tabs.filter{it.profile==tab.profile&&it.app.id==tab.app.id},key={it.id}){item->Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={pool.select(item);showTabs=false},modifier=Modifier.weight(1f)){Text(item.title,maxLines=2)};IconButton(onClick={pool.close(item)}){Icon(Icons.Default.Close,"Close ${item.title}")}}}}
   Button(onClick={if(pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}<8){pool.select(pool.create(tab.app,tab.profile,true));showTabs=false}},enabled=pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}<8){Text("New app tab")}
  }
 }
}
