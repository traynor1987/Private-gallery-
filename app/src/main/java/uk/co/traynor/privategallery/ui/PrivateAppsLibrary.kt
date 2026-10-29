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
internal fun PrivateAppsLibrary(controller:PrivateAppsController,onClose:()->Unit) {
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
 fun runChange(action:()->PrivateAppCatalog) {
  if(!controller.isCurrent()){revoke();return}
  busy=true;message=""
  scope.launch {
   val result=withContext(Dispatchers.IO){runCatching{action()}}
   if(!controller.isCurrent()){revoke();return@launch}
   result.onSuccess{apps=it.apps}.onFailure{message="Private apps could not be updated. Your saved data was kept."}
   busy=false
  }
 }
 LaunchedEffect(controller){runChange{controller.load()}}
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
     items(apps,key={it.id()}) {app ->
      Card(Modifier.fillMaxWidth().heightIn(min=144.dp)) {
       Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        PrivateAppIcon(app.icon(),app.name())
        Text(app.name(),style=MaterialTheme.typography.titleMedium)
        TextButton(onClick={
         runCatching{controller.launch(app.id())}.onSuccess{close()}.onFailure{if(!controller.isCurrent())revoke() else message="App could not open. Check the connection; apps requiring fresh authentication are not supported yet."}
        },enabled=!busy){Text("Open")}
        Row {
         TextButton(onClick={editor=app;editing=true},enabled=!busy,modifier=Modifier.semantics{contentDescription="Edit ${app.name()}"}){Text("Edit")}
         TextButton(onClick={removal=app},enabled=!busy,modifier=Modifier.semantics{contentDescription="Remove ${app.name()}"}){Text("Remove")}
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
  var discovered by remember(editor){mutableStateOf<PrivateAppCatalog.App?>(null)}
  var discovering by remember(editor){mutableStateOf(false)}
  var discoveryMessage by remember(editor){mutableStateOf("")}
  val editorScope=rememberCoroutineScope()
  val editorIdentity=editor

  GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={editing=false},title={Text(if(editor==null)"Add Private App" else "Edit Private App")},text={
   Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(name,{name=it.take(PrivateAppPolicy.MAX_NAME)},label={Text("App name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("private-app-name"))
    OutlinedTextField(url,{url=it.take(PrivateAppPolicy.MAX_URL);discovered=null;discoveryMessage=""},label={Text("Website URL")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("private-app-url"))
    Text("Use an https:// website address. Onion addresses require Hidden Tor.",style=MaterialTheme.typography.bodySmall)
    TextButton(enabled=!discovering,onClick={
     val target=url.trim()
     if(!PrivateAppDiscovery.safe(target,controller.owner==PrivateAppStore.Owner.HIDDEN_TOR)){invalid=true;return@TextButton}
     discovering=true;discoveryMessage="";discovered=null
     editorScope.launch {
      val result=withContext(Dispatchers.IO){runCatching{controller.discover(target)}}
      if(!controller.isCurrent()){revoke();return@launch}
      if(editing && editor===editorIdentity && url.trim()==target) {
       result.onSuccess {app ->discovered=app;name=app.name();url=app.startUrl();invalid=false
        discoveryMessage=if(app.scope().isNotEmpty())"Compatible web app metadata found." else "No compatible manifest found. Add as a private website."
       }.onFailure{discoveryMessage="Discovery unavailable. Check the authorised connection, or save the website manually."}
      }
      discovering=false
     }
    }){Text(if(discovering)"Discovering…" else "Discover website")}
    if(discovering)LinearProgressIndicator(Modifier.fillMaxWidth())
    discovered?.let {PrivateAppIcon(it.icon(),it.name())}
    if(discoveryMessage.isNotEmpty())Text(discoveryMessage,style=MaterialTheme.typography.bodySmall)
    Text("Public metadata only; redirects and cross-origin resources are unsupported. No background push, offline guarantee or service-worker installation. Display mode is metadata; browser controls remain available.",style=MaterialTheme.typography.bodySmall)
    if(invalid)Text("Enter a valid name and supported website URL.",color=MaterialTheme.colorScheme.error)
   }
  },confirmButton={TextButton(enabled=!discovering,onClick={
   val address=url.trim();val title=name.trim()
   if(!PrivateAppPolicy.validName(title) || !PrivateAppPolicy.safeUrl(address,controller.owner==PrivateAppStore.Owner.HIDDEN_TOR)){invalid=true;return@TextButton}
   val old=editor
   // A changed URL must not retain a manifest scope or icon from the old origin.
   val same=old?.startUrl()==address
   val found=discovered?.takeIf{it.startUrl()==address}
   val item=PrivateAppCatalog.App(old?.id() ?: UUID.randomUUID().toString(),title,address,found?.scope() ?: if(same)old!!.scope() else "",found?.display() ?: if(same)old!!.display() else "browser",found?.icon() ?: if(same)old!!.icon() else "",old?.pinned() ?: false,true,old?.requireAuthentication() ?: false)
   editing=false;runChange{controller.save(item)}
  }){Text(if(editor!=null || discovered==null)"Save" else if(discovered!!.scope().isNotEmpty())"Add as Private Web App" else "Add as Private Website")}},dismissButton={TextButton(onClick={editing=false}){Text("Cancel")}})
 }
 removal?.let {app ->GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={removal=null},title={Text("Remove ${app.name()}?")},text={Text("Removes the saved app entry. Shared browser login data is unchanged.")},confirmButton={TextButton(onClick={removal=null;runChange{controller.remove(app.id())}}){Text("Remove app")}},dismissButton={TextButton(onClick={removal=null}){Text("Cancel")}})}
}

@Composable
internal fun PrivateAppIcon(value:String,name:String) {
 val bitmap=remember(value){PrivateAppIcons.decode(value)}
 DisposableEffect(bitmap){onDispose{bitmap?.recycle()}}
 if(bitmap!=null)Image(bitmap.asImageBitmap(),contentDescription=null,modifier=Modifier.size(48.dp),contentScale=ContentScale.Fit)
 else Text(name.take(1).uppercase(),style=MaterialTheme.typography.headlineMedium)
}
