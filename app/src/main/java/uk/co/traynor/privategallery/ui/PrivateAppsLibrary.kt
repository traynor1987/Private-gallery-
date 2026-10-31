package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import java.util.UUID
import uk.co.traynor.privategallery.core.privateapps.*

/** Shared library only: browsing remains in the existing authorised browser. No saved UI state. */
@Composable
internal fun PrivateAppsLibrary(controller:PrivateAppsController,onClose:()->Unit,initialEditId:String?=null,onUpdated:(PrivateAppCatalog.App)->Unit={},onRemoved:(String)->Unit={},initialUrl:String?=null,initialName:String?=null,onAppSettings:((String)->Unit)?=null) {
 val scope=rememberCoroutineScope()
 var apps by remember(controller){mutableStateOf<List<PrivateAppCatalog.App>>(emptyList())}
 var busy by remember(controller){mutableStateOf(true)}
 var message by remember(controller){mutableStateOf("")}
 var editor by remember(controller){mutableStateOf<PrivateAppCatalog.App?>(null)}
 var editing by remember(controller){mutableStateOf(false)}
 var removal by remember(controller){mutableStateOf<PrivateAppCatalog.App?>(null)}
 var revoked by remember(controller){mutableStateOf(false)}
 val alive=remember(controller){AtomicBoolean(true)}
 var query by remember(controller){mutableStateOf("")}
 var menuId by remember(controller){mutableStateOf<String?>(null)}
 var reorder by remember(controller){mutableStateOf(false)}
 var selectedId by remember(controller){mutableStateOf<String?>(null)}
 var settingsId by remember(controller){mutableStateOf<String?>(null)}
 var showInformation by remember(controller){mutableStateOf(false)}
 val close=remember(controller){onClose}
 fun current()=alive.get() && controller.isCurrent()
 fun clearPresentation(){apps=emptyList();editor=null;editing=false;removal=null;message="";query="";menuId=null;selectedId=null;reorder=false;settingsId=null;showInformation=false}
 fun dismiss(){if(alive.getAndSet(false)){clearPresentation();revoked=true;close()}}
 DisposableEffect(controller){onDispose{alive.set(false)}}
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 DisposableEffect(controller,lifecycle){
  val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)dismiss()}
  lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
 }
 fun revoke(){dismiss()}
 fun runChange(after:()->Unit={},action:()->PrivateAppCatalog) {
  if(!current()){revoke();return}
  busy=true;message=""
  scope.launch {
   val result=withContext(Dispatchers.IO){runCatching{action()}}
   if(!current()){revoke();return@launch}
   result.onSuccess{apps=it.apps;after()}.onFailure{message="Private apps could not be updated. Your saved data was kept."}
   busy=false
  }
 }
 LaunchedEffect(controller){
  val result=withContext(Dispatchers.IO){runCatching{controller.load()}}
  if(!current()){revoke();return@LaunchedEffect}
  result.onSuccess {apps=it.apps;initialEditId?.let {id ->editor=it.apps.firstOrNull {app->app.id()==id};editing=editor!=null};if(initialEditId==null && initialUrl!=null && PrivateAppDiscovery.safe(initialUrl,controller.owner==PrivateAppStore.Owner.HIDDEN_TOR))editing=true}
   .onFailure {message="Private apps could not be loaded."}
  busy=false
 }
 LaunchedEffect(controller){while(isActive && !revoked){delay(250);if(!current())revoke()}}
 if(revoked)return
 Dialog(onDismissRequest={if(!busy)dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {
  Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     Text("Private Apps",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
     TextButton(onClick={dismiss()},enabled=!busy){Text("Close")}
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     Text(when(controller.owner){PrivateAppStore.Owner.PRIMARY->"Primary Vault";PrivateAppStore.Owner.HIDDEN_VPN->"Hidden Vault · VPN";else->"Hidden Vault · Tor"},modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
     IconButton(onClick={showInformation=true}){Icon(Icons.Default.Info,"Browser session information")}
     FilledTonalButton(onClick={editor=null;editing=true},enabled=!busy && !reorder && apps.size<PrivateAppCatalog.MAX_APPS){Icon(Icons.Default.Add,null);Text("Add App")}
    }
    OutlinedTextField(query,{query=it.take(120);menuId=null},modifier=Modifier.fillMaxWidth().testTag("private-app-search"),
     enabled=!busy && !reorder,singleLine=true,label={Text("Search apps")},leadingIcon={Icon(Icons.Default.Search,null)})
    if(reorder)Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     Text(if(selectedId==null)"Tap an app, then tap its new position." else "Tap the new position for ${apps.firstOrNull{it.id()==selectedId}?.name().orEmpty()}.",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
     TextButton(enabled=!busy,onClick={reorder=false;selectedId=null}){Text("Done")}
    }
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(message.isNotEmpty())Text(message,color=MaterialTheme.colorScheme.error)
    val visibleApps=if(reorder || query.isBlank())apps else apps.filter{it.name().contains(query.trim(),ignoreCase=true)}
    if(!busy && visibleApps.isEmpty()) {
     Text(if(apps.isEmpty())"No private apps yet"else"No matching apps",style=MaterialTheme.typography.titleLarge)
     Text(if(apps.isEmpty())"Add a website or Private Web App."else"Try another app name.")
    }
    PrivateAppsDrawerGrid(apps,visibleApps,!busy,reorder,selectedId,menuId,
        onTap={app,index ->
         if(current() && !busy) {
          if(reorder) {
           val source=selectedId
           if(source==null)selectedId=app.id()
           else {selectedId=null;runChange{controller.moveTo(source,index)}}
          } else runCatching{controller.launchFromDrawer(app.id(),{current()}){if(current())dismiss()}}
           .onFailure{if(!current())revoke() else message="App could not open. Check the authorised connection or authenticate again."}
         }
        },onActions={id ->if(current() && !busy){menuId=id}},onDismissMenu={menuId=null},onAction={app,action ->
         menuId=null
         if(current() && !busy)apps.firstOrNull{it.id()==app.id()}?.let {latest ->
          when(action) {
           "Edit"->{editor=latest;editing=true}
           "Pin","Unpin"->runChange{controller.setPinned(latest.id(),action=="Pin")}
           "Reorder"->{query="";reorder=true;selectedId=null}
           "App Settings"->if(onAppSettings!=null){dismiss();if(controller.isCurrent())onAppSettings(latest.id())}else settingsId=latest.id()
           "Remove"->{removal=latest}
          }
         }
        },modifier=Modifier.weight(1f).fillMaxWidth())
   }
  }
 }
 if(showInformation)GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={showInformation=false},title={Text("Browser session")},
  text={Text(if(controller.owner==PrivateAppStore.Owner.PRIMARY)"Primary apps and browser tabs share cookies and website storage. Your existing browser-wide login-retention policy applies. App Close does not delete shared login data."else"Hidden apps share only this separate temporary browser profile. Closing the Hidden browser clears its logins. Tor settings stay unchanged.")},confirmButton={TextButton(onClick={showInformation=false}){Text("Done")}})
 settingsId?.let {id ->PrivateAppSettings(controller,id,{updated->apps=apps.map{if(it.id()==updated.id())updated else it};onUpdated(updated)},
  {message="Browser tools are available from an opened app."},{settingsId=null},onEdit={settingsId=null;editor=apps.firstOrNull{it.id()==id};editing=editor!=null},
  onRemoved={removed->apps=apps.filterNot{it.id()==removed};onRemoved(removed)})}

 if(editing) {
  var name by remember(editor){mutableStateOf(editor?.name() ?: initialName.orEmpty().take(120))}
  var url by remember(editor){mutableStateOf(editor?.startUrl() ?: initialUrl.orEmpty())}
  var invalid by remember(editor){mutableStateOf(false)}
  var customIcon by remember(editor){mutableStateOf<String?>(null)}
  var iconBusy by remember(editor){mutableStateOf(false)}
  var discovered by remember(editor){mutableStateOf<PrivateAppCatalog.App?>(null)}
  var discovering by remember(editor){mutableStateOf(false)}
  var discoveryMessage by remember(editor){mutableStateOf("")}
  val editorScope=rememberCoroutineScope()
  val editorIdentity=editor

  var checkedUrl by remember(editor){mutableStateOf(editor?.startUrl().orEmpty())}
  fun discoverWebsite() {
   if(discovering)return
   val target=url.trim()
   if(!PrivateAppDiscovery.safe(target,controller.owner==PrivateAppStore.Owner.HIDDEN_TOR)){invalid=true;return}
   discovering=true;discoveryMessage="";discovered=null;checkedUrl=""
   editorScope.launch {
    val result=withContext(Dispatchers.IO){runCatching{controller.discover(target)}}
    if(!current()){revoke();return@launch}
    if(editing && editor===editorIdentity && url.trim()==target) {
     result.onSuccess {app ->discovered=app;if(name.isBlank() || (editor==null && initialName!=null && name==initialName))name=app.name();checkedUrl=app.startUrl();url=app.startUrl();invalid=false
      discoveryMessage=app.discoveryReport()
     }.onFailure{checkedUrl=target;discoveryMessage="Discovery unavailable on the authorised connection. Metadata is public only; sign-in pages, redirects or a timeout can prevent compatibility checks. Save as a Private Website or check again."}
    }
    discovering=false
   }
  }
  // Offer the install choice without requiring the owner to know about manifest discovery.
  LaunchedEffect(url,discovering) {
   val target=url.trim()
   if(!discovering && target!=checkedUrl && PrivateAppDiscovery.safe(target,controller.owner==PrivateAppStore.Owner.HIDDEN_TOR)) {
    delay(700);discoverWebsite()
   }
  }
  GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={editing=false},title={Text(if(editor==null)"Add Private App" else "Edit Private App")},text={
   Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(name,{name=it.take(PrivateAppPolicy.MAX_NAME)},label={Text("App name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("private-app-name"))
    OutlinedTextField(url,{url=it.take(PrivateAppPolicy.MAX_URL);discovered=null;discoveryMessage="";iconBusy=false},label={Text("Website URL")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("private-app-url"))
    Text("Use an https:// website address. Onion addresses require Hidden Tor.",style=MaterialTheme.typography.bodySmall)
    TextButton(enabled=!discovering && !iconBusy,onClick={discoverWebsite()}){Text(if(discovering)"Discovering…" else "Discover website")}
    if(discovering)LinearProgressIndicator(Modifier.fillMaxWidth())
    // Explicit user selection wins over discovery. A changed URL retires in-flight picker results.
    key(editor,url) {
     PrivateAppIconEditor(controller,customIcon ?: discovered?.icon() ?: editor?.icon().takeIf{editor?.startUrl()==url.trim()}.orEmpty(),name,
      onChange={customIcon=it},onBusy={iconBusy=it},enabled=!discovering)
    }
    if(discoveryMessage.isNotEmpty())Text(discoveryMessage,style=MaterialTheme.typography.bodySmall)
    Text("Private Web Apps run inside Private Gallery, not as separate Android applications. Public metadata only; redirects and cross-origin resources are unsupported. Push, background execution and guaranteed offline operation are unavailable.",style=MaterialTheme.typography.bodySmall)
    if(invalid)Text("Enter a valid name and supported website URL.",color=MaterialTheme.colorScheme.error)
   }
  },confirmButton={TextButton(enabled=!discovering && !iconBusy && checkedUrl==url.trim(),onClick={
   val address=url.trim();val title=name.trim()
   if(!PrivateAppPolicy.validName(title) || !PrivateAppPolicy.safeUrl(address,controller.owner==PrivateAppStore.Owner.HIDDEN_TOR)){invalid=true;return@TextButton}
   val old=editor
   // A changed URL must not retain a manifest scope or icon from the old origin.
   val same=old?.startUrl()==address
   val found=discovered?.takeIf{it.startUrl()==address}
   val item=PrivateAppCatalog.App(old?.id() ?: UUID.randomUUID().toString(),title,address,found?.scope() ?: if(same)old!!.scope() else "",found?.display() ?: if(same)old!!.display() else "browser",customIcon ?: found?.icon() ?: if(same)old!!.icon() else "",old?.pinned() ?: false,old?.addressBar() ?: false,old?.requireAuthentication() ?: false,old?.appStyle() ?: true,found?.discoveryReport() ?: if(same)old!!.discoveryReport() else discoveryMessage.take(256))
   editing=false;runChange(after={onUpdated(item)}){controller.save(item)}
  }){Text(if(editor!=null)"Save" else if(discovered?.scope()?.isNotEmpty()==true)"Install as Private Web App" else "Add as Private Website")}},dismissButton={TextButton(onClick={editing=false}){Text("Cancel")}})
 }
 removal?.let {app ->GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={removal=null},title={Text("Remove ${app.name()}?")},text={Text("Removes the saved app entry. Shared browser login data is unchanged.")},confirmButton={TextButton(onClick={removal=null;runChange(after={onRemoved(app.id())}){controller.remove(app.id())}}){Text("Remove app")}},dismissButton={TextButton(onClick={removal=null}){Text("Cancel")}})}
}

@Composable
internal fun PrivateAppIcon(value:String,name:String,size:Int=48) {
 val bitmap=remember(value){PrivateAppIcons.decode(value)}
 DisposableEffect(bitmap){onDispose{bitmap?.recycle()}}
 if(bitmap!=null)Image(bitmap.asImageBitmap(),contentDescription=null,modifier=Modifier.size(size.dp),contentScale=ContentScale.Fit)
 else Box(Modifier.size(size.dp),contentAlignment=androidx.compose.ui.Alignment.Center){Text(name.take(1).uppercase(),style=MaterialTheme.typography.titleMedium)}
}
