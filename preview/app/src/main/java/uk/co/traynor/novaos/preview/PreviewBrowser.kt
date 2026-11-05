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
data class PreviewExternalRequest(val tabId:String,val profile:String,val url:String)
class PreviewTab(val id:String,val profile:String,val app:StoreEntry,val wrapper:MutableContextWrapper,val view:WebView){
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
 val tabs=mutableStateListOf<PreviewTab>()
 var activeId by mutableStateOf<String?>(null)
 var notice by mutableStateOf<String?>(null)
 var upload:ValueCallback<Array<Uri>>?=null
 var pickFiles:(()->Unit)?=null
 var foreground=true
 var currentProfile="primary"
 var externalRequest by mutableStateOf<PreviewExternalRequest?>(null)
 private val last=mutableMapOf<String,String>()
 val active get()=tabs.firstOrNull{it.id==activeId}
 fun open(app:StoreEntry,profile:String):PreviewTab{
  val owner="$profile:${app.id}";val old=tabs.firstOrNull{it.id==last[owner]}?:tabs.firstOrNull{it.profile==profile&&it.app.id==app.id}
  val tab=old?:create(app,profile,true);select(tab);return tab
 }
 fun select(tab:PreviewTab){activeId=tab.id;last["${tab.profile}:${tab.app.id}"]=tab.id;syncLifecycle()}
 fun syncLifecycle(){tabs.forEach{if(PreviewAppPolicy.keepRunning(foreground,it.id==activeId,it.profile==currentProfile))it.view.onResume()else it.view.onPause()}}
 fun create(app:StoreEntry,profile:String,load:Boolean):PreviewTab{
  require(tabs.count{it.profile==profile&&it.app.id==app.id}<8){"Close a tab before adding more (8 per app)."}
  val wrapper=MutableContextWrapper(getApplication<Application>())
  val view=WebView(wrapper)
  val tab=PreviewTab(java.util.UUID.randomUUID().toString(),profile,app,wrapper,view)
  tabs.add(tab)
  configure(tab)
  if(load)navigate(tab,app.url)
  return tab
 }
 fun requestExternal(tab:PreviewTab,url:String){
  tab.error="This Google OAuth authorization request requires a supported external browser. Its cookies remain in that browser; they cannot be copied into this preview."
  externalRequest=PreviewExternalRequest(tab.id,tab.profile,url)
 }
 fun navigate(tab:PreviewTab,url:String){
  val safe=PreviewRules.https(url)
  if(PreviewAppPolicy.googleOAuthAuthorization(safe))requestExternal(tab,safe)else tab.view.loadUrl(safe)
 }
 /** Settings mirror secureBrowserWebView/Primary V1, without VPN, diagnostics,
  * vault downloads, credentials, JS bridges, altered UA or cookie migration. */
 private fun configure(tab:PreviewTab){
  val view=tab.view
  view.layoutParams=ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
  view.isFocusable=true;view.isFocusableInTouchMode=true
  view.settings.apply{
   javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=false
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
    if(!safe)tab.error="This link needs another application or unsupported sign-in flow. Use the browser menu for a confirmed external-browser option."
    return !safe
   }
   override fun onPageStarted(v:WebView,url:String,icon:Bitmap?){tab.url=url;tab.error=null;update()}
   override fun onPageFinished(v:WebView,url:String){update();CookieManager.getInstance().flush()}
   override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError){if(r.isForMainFrame)tab.error="Page could not load. Check your connection, retry, or choose the external browser option."}
   override fun onReceivedHttpError(v:WebView,r:WebResourceRequest,e:WebResourceResponse){if(r.isForMainFrame&&e.statusCode>=400)tab.error="Website returned ${e.statusCode}. Embedded sign-in or browser compatibility may be restricted. External browsing is available from the menu."}
   override fun onReceivedSslError(v:WebView,handler:SslErrorHandler,e:SslError){handler.cancel();tab.error="Certificate error. Connection rejected; certificate bypass is unavailable."}
   override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean{notice="Web renderer stopped. Return Home and reopen the app.";close(tab,rendererGone=true);return true}
  }
  view.webChromeClient=object:WebChromeClient(){
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
  view.setDownloadListener{_,_,_,_,_->tab.error="Preview downloads are not implemented. Open this page in an external browser from the menu to use its download controls."}
 }
 fun close(tab:PreviewTab,rendererGone:Boolean=false){
  (tab.view.parent as? ViewGroup)?.removeView(tab.view);if(!rendererGone)tab.view.stopLoading();tab.view.destroy();tabs.remove(tab)
  if(activeId==tab.id)activeId=tabs.lastOrNull{it.profile==tab.profile&&it.app.id==tab.app.id}?.id
  last.entries.removeAll{it.value==tab.id}
 }
 fun closeApp(profile:String,id:String){tabs.filter{it.profile==profile&&it.app.id==id}.toList().forEach{close(it)}}
 fun closeAll(){tabs.toList().forEach{close(it)}}
 fun detachContexts(){foreground=false;syncLifecycle();tabs.forEach{it.wrapper.baseContext=getApplication<Application>()}}
 override fun onCleared(){upload?.onReceiveValue(null);upload=null;closeAll()}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PreviewBrowser(pool:PreviewBrowserModel,onHome:()->Unit,onExternal:(String)->Unit,visible:Boolean=true){
 val tab=pool.active
 var settings by remember{mutableStateOf(false)}
 var showTabs by remember{mutableStateOf(false)};var menu by remember{mutableStateOf(false)}
 val context=LocalContext.current
 if(tab==null){Column(Modifier.fillMaxSize().safeDrawingPadding()){PreviewHeader("Web apps","NovaOS Preview",onHome);Text(pool.notice?:"No open app tabs.",Modifier.padding(24.dp));Button(onClick=onHome,Modifier.padding(24.dp)){Text("Return Home")}};return}
 var address by remember(tab.id,tab.url){mutableStateOf(tab.url)}
 LaunchedEffect(visible){if(!visible){menu=false;showTabs=false;settings=false}}
 BackHandler(visible){if(tab.view.canGoBack())tab.view.goBack()else onHome()}
 Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("preview-web-app").then(if(visible)Modifier else Modifier.clearAndSetSemantics{})){
 Column(Modifier.fillMaxSize()){
 if(!tab.appMode){
  Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onHome,modifier=Modifier.testTag("web-return-home")){Icon(Icons.Default.Home,"Return to Nova Home")}
   Column(Modifier.weight(1f)){Text(tab.app.name,style=MaterialTheme.typography.titleMedium,maxLines=1);Text("Ordinary web · NovaOS Preview",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   IconButton(onClick={showTabs=true}){BadgedBox(badge={Badge{Text(pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}.toString())}}){Icon(Icons.Default.Tab,"App tabs")}}
   Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Browser menu")};DropdownMenu(menu,{menu=false}){
    DropdownMenuItem(text={Text("App Mode")},onClick={menu=false;tab.appMode=true})
    DropdownMenuItem(text={Text("Open externally…")},onClick={menu=false;onExternal(tab.url)})
    DropdownMenuItem(text={Text("New app tab")},onClick={menu=false;if(pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}<8)pool.select(pool.create(tab.app,tab.profile,true))else showTabs=true})
    DropdownMenuItem(text={Text("Close app tab")},onClick={menu=false;pool.close(tab)})
   }}
  }
  Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick={tab.view.goBack()},enabled=tab.back){Icon(Icons.Default.ArrowBack,"Back")}
   OutlinedTextField(address,{address=it.take(2048)},Modifier.weight(1f),singleLine=true,textStyle=MaterialTheme.typography.bodySmall,shape=RoundedCornerShape(18.dp),trailingIcon={IconButton(onClick={runCatching{pool.navigate(tab,address)}.onFailure{tab.error=it.message}}){Icon(Icons.Default.ArrowForward,"Go")}})
   IconButton(onClick={if(tab.progress<100)tab.view.stopLoading()else tab.view.reload()}){Icon(if(tab.progress<100)Icons.Default.Close else Icons.Default.Refresh,if(tab.progress<100)"Stop"else "Reload")}
   IconButton(onClick={tab.view.goForward()},enabled=tab.forward){Icon(Icons.Default.ChevronRight,"Forward")}
  }
 }
  if(tab.progress in 1..99)LinearProgressIndicator(progress={tab.progress/100f},modifier=Modifier.fillMaxWidth())
  tab.error?.let{error->Surface(color=MaterialTheme.colorScheme.errorContainer){Column(Modifier.padding(12.dp)){Text(error,style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick={tab.error=null;tab.view.reload()}){Text("Retry")};TextButton(onClick={onExternal(tab.url)}){Text("External browser…")};TextButton(onClick={tab.error=null}){Text("Dismiss")}}}}}
  key(tab.id){AndroidView(factory={
   (tab.view.parent as? ViewGroup)?.removeView(tab.view);tab.wrapper.baseContext=context;pool.syncLifecycle();tab.view
  },modifier=Modifier.weight(1f).fillMaxWidth(),update={view->view.importantForAccessibility=if(visible)android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS},onReset=null,onRelease={view->(view.parent as? ViewGroup)?.removeView(view);view.onPause();tab.wrapper.baseContext=context.applicationContext})}
 }
 if(tab.appMode&&visible)Box(Modifier.align(Alignment.TopEnd).padding(8.dp)){
  FilledTonalIconButton(onClick={menu=true},modifier=Modifier.size(48.dp).testTag("web-app-menu")){Icon(Icons.Default.MoreVert,"App menu")}
  DropdownMenu(menu,{menu=false}){
   DropdownMenuItem(text={Text(tab.app.name+" · Preview")},onClick={menu=false;settings=true})
   DropdownMenuItem(text={Text("Home")},modifier=Modifier.testTag("web-return-home"),onClick={menu=false;onHome()})
   DropdownMenuItem(text={Text("Back")},enabled=tab.back,onClick={menu=false;tab.view.goBack()})
   DropdownMenuItem(text={Text("Refresh")},onClick={menu=false;tab.view.reload()})
   DropdownMenuItem(text={Text("App tabs")},onClick={menu=false;showTabs=true})
   DropdownMenuItem(text={Text("Open in Browser view")},onClick={menu=false;tab.appMode=false})
   DropdownMenuItem(text={Text("External browser…")},onClick={menu=false;onExternal(tab.url)})
   DropdownMenuItem(text={Text("App settings & compatibility")},onClick={menu=false;settings=true})
  }
 }
 }
 if(settings&&visible)AlertDialog(onDismissRequest={settings=false},title={Text(tab.app.name+" settings")},text={Text("Ordinary preview browsing. Cookies persist in this app’s private Android storage, shared across demo profiles; they are not an encrypted vault. Ordinary website sign-in uses this WebView’s own cookies when the website accepts it. Google may reject embedded browsers; OAuth authorization uses confirmed external browsing. External browser cookies cannot be copied here. Audio may continue under Nova Home while this preview stays foregrounded. Leaving NovaOS, switching profiles/tabs or website playback policy may pause it; Premium does not guarantee WebView background playback.")},confirmButton={TextButton(onClick={settings=false}){Text("Done")}})
 if(showTabs&&visible)ModalBottomSheet(onDismissRequest={showTabs=false}){
  Column(Modifier.fillMaxWidth().heightIn(max=500.dp).padding(20.dp)){
   Text("${tab.app.name} tabs",style=MaterialTheme.typography.headlineSmall)
   LazyColumn(Modifier.weight(1f,false)){items(pool.tabs.filter{it.profile==tab.profile&&it.app.id==tab.app.id},key={it.id}){item->Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={pool.select(item);showTabs=false},modifier=Modifier.weight(1f)){Text(item.title,maxLines=2)};IconButton(onClick={pool.close(item)}){Icon(Icons.Default.Close,"Close ${item.title}")}}}}
   Button(onClick={if(pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}<8){pool.select(pool.create(tab.app,tab.profile,true));showTabs=false}},enabled=pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}<8){Text("New app tab")}
  }
 }
}
