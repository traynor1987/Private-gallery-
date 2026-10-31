package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.grid.*
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
internal fun PrivateAppsLibrary(controller:PrivateAppsController,onClose:()->Unit,initialEditId:String?=null,onUpdated:(PrivateAppCatalog.App)->Unit={},onRemoved:(String)->Unit={}) {
 val scope=rememberCoroutineScope()
 var apps by remember(controller){mutableStateOf<List<PrivateAppCatalog.App>>(emptyList())}
 var busy by remember(controller){mutableStateOf(true)}
 var message by remember(controller){mutableStateOf("")}
 var editor by remember(controller){mutableStateOf<PrivateAppCatalog.App?>(null)}
 var editing by remember(controller){mutableStateOf(false)}
 var removal by remember(controller){mutableStateOf<PrivateAppCatalog.App?>(null)}
 var revoked by remember(controller){mutableStateOf(false)}
 val close by rememberUpdatedState(onClose)
 fun revoke(){apps=emptyList();editor=null;editing=false;removal=null;message="";if(!revoked){revoked=true;close()}}
 fun runChange(after:()->Unit={},action:()->PrivateAppCatalog) {
  if(!controller.isCurrent()){revoke();return}
  busy=true;message=""
  scope.launch {
   val result=withContext(Dispatchers.IO){runCatching{action()}}
   if(!controller.isCurrent()){revoke();return@launch}
   result.onSuccess{apps=it.apps;after()}.onFailure{message="Private apps could not be updated. Your saved data was kept."}
   busy=false
  }
 }
 LaunchedEffect(controller){
  val result=withContext(Dispatchers.IO){runCatching{controller.load()}}
  if(!controller.isCurrent()){revoke();return@LaunchedEffect}
  result.onSuccess {apps=it.apps;initialEditId?.let {id ->editor=it.apps.firstOrNull {app->app.id()==id};editing=editor!=null}}
   .onFailure {message="Private apps could not be loaded."}
  busy=false
 }
 LaunchedEffect(controller){while(isActive && !revoked){delay(250);if(!controller.isCurrent())revoke()}}
 if(revoked)return
 Dialog(onDismissRequest={if(!busy)close()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {
  Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().widthIn(max=960.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
     Text("Private Apps",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
     TextButton(onClick={close()}){Text("Close")}
    }
    Text(when(controller.owner){PrivateAppStore.Owner.PRIMARY->"Primary Vault";PrivateAppStore.Owner.HIDDEN_VPN->"Hidden Vault · VPN";else->"Hidden Vault · Tor"},style=MaterialTheme.typography.labelLarge)
    Text(if(controller.owner==PrivateAppStore.Owner.PRIMARY)"Websites use Primary browser storage and its existing login-retention setting." else "Hidden login sessions are cleared when this private browser closes. Tor settings stay unchanged.",style=MaterialTheme.typography.bodySmall)
    Button(onClick={editor=null;editing=true},enabled=!busy && apps.size<PrivateAppCatalog.MAX_APPS){Text("Add Private App")}
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(message.isNotEmpty())Text(message,color=MaterialTheme.colorScheme.error)
    if(!busy && apps.isEmpty()) {
     Text("No private apps yet",style=MaterialTheme.typography.titleLarge)
     Text("Add a website to keep it in this vault. No services are preinstalled or signed in.")
    }
    LazyVerticalGrid(columns=GridCells.Adaptive(160.dp),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     itemsIndexed(apps,key={_,app->app.id()}) {index,app ->
      Card(Modifier.fillMaxWidth().heightIn(min=144.dp)) {
       Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        PrivateAppIcon(app.icon(),app.name())
        Text(app.name(),style=MaterialTheme.typography.titleMedium)
        if(app.pinned())Text("Favourite",style=MaterialTheme.typography.labelMedium)
        TextButton(onClick={
         runCatching{controller.launch(app.id()){close()}}.onFailure{if(!controller.isCurrent())revoke() else message="App could not open. Check the authorised connection or authenticate again."}
        },enabled=!busy){Text("Open")}
        Row {
         TextButton(onClick={editor=app;editing=true},enabled=!busy,modifier=Modifier.semantics{contentDescription="Edit ${app.name()}"}){Text("Edit")}
         TextButton(onClick={removal=app},enabled=!busy,modifier=Modifier.semantics{contentDescription="Remove ${app.name()}"}){Text("Remove")}
        }
        TextButton(onClick={runChange{controller.setPinned(app.id(),!app.pinned())}},enabled=!busy,modifier=Modifier.semantics{contentDescription="${if(app.pinned())"Unpin" else "Pin"} ${app.name()} ${if(app.pinned())"from" else "as"} favourite"}){Text(if(app.pinned())"Unpin favourite" else "Pin favourite")}
        // These indices are the persisted order: pinning never silently sorts this grid.
        Column {
         TextButton(onClick={runChange{controller.move(app.id(),-1)}},enabled=!busy && index>0,modifier=Modifier.semantics{contentDescription="Move ${app.name()} up"}){Text("Move up")}
         TextButton(onClick={runChange{controller.move(app.id(),1)}},enabled=!busy && index<apps.lastIndex,modifier=Modifier.semantics{contentDescription="Move ${app.name()} down"}){Text("Move down")}
        }
       }
      }
     }
    }
   }
  }
 }
 if(editing) {
  var name by remember(editor){mutableStateOf(editor?.name().orEmpty())}
  var url by remember(editor){mutableStateOf(editor?.startUrl().orEmpty())}
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
      if(!controller.isCurrent()){revoke();return@launch}
      if(editing && editor===editorIdentity && url.trim()==target) {
       result.onSuccess {app ->discovered=app;if(name.isBlank())name=app.name();checkedUrl=app.startUrl();url=app.startUrl();invalid=false
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
