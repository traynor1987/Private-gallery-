package uk.co.traynor.privategallery.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.privateapps.*

/** Chrome only: all actions delegate to the same authenticated tab and browser policy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrivateAppControls(
    app: PrivateAppCatalog.App, url: String, mode: String, enabled: Boolean,
    canGoBack: Boolean, canGoForward: Boolean, loading: Boolean,
    onBack: () -> Unit, onForward: () -> Unit, onRefresh: () -> Unit,
    onHome: () -> Unit, onLibrary: () -> Unit, onSwitch: () -> Unit,
    onSettings: () -> Unit,
    onScreenshot: (() -> Unit)? = null,
    onExpanded: (Boolean) -> Unit = {},
    onBrowserTools: () -> Unit = {},
) {
    var expanded by remember(app.id()) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("private-app-controls")) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            PrivateAppIcon(app.icon(),app.name(),24)
            Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
                Text(app.name(),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(mode,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick=onBack,enabled=enabled && canGoBack) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"App back")}
            IconButton(onClick=onRefresh,enabled=enabled) {Icon(if(loading)Icons.Default.Close else Icons.Default.Refresh,if(loading)"Stop app loading" else "Refresh app")}
            IconButton(onClick={expanded=!expanded;onExpanded(expanded)}) {Icon(Icons.Default.MoreVert,if(expanded)"Collapse app controls" else "Expand app controls")}
        }
        // Origin changes and transport failures must remain visible even with controls collapsed.
        val outside=url.isNotBlank() && !PrivateAppPolicy.inScope(url,app.scope().ifBlank {app.startUrl()})
        if(outside || !enabled || expanded) {
            val origin=runCatching {Uri.parse(url).let {"${it.scheme}://${it.host.orEmpty()}${if(it.port>=0)":${it.port}" else ""}"}}.getOrDefault("")
            Text(origin.ifBlank {"Opening app…"},Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(outside)Text("Browsing outside this app's saved scope",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
        if(expanded) {
            // Wrap at cover-screen widths and large font sizes; no horizontally clipped actions.
            FlowRow(Modifier.fillMaxWidth().padding(horizontal=4.dp)) {
                IconButton(onClick=onForward,enabled=enabled && canGoForward){Icon(Icons.AutoMirrored.Filled.ArrowForward,"App forward")}
                IconButton(onClick=onHome,enabled=enabled){Icon(Icons.Default.Home,"App home")}
                IconButton(onClick=onSwitch){Icon(Icons.Default.SwapHoriz,"Switch private app")}
                IconButton(onClick=onLibrary){Icon(Icons.Default.Apps,"Private Apps library")}
                IconButton(onClick=onSettings,enabled=enabled){Icon(Icons.Default.Settings,"Private app settings")}
                if(onScreenshot!=null)IconButton(onClick=onScreenshot,enabled=enabled){Icon(Icons.Default.Screenshot,"Screenshot to Vault")}
                IconButton(onClick=onBrowserTools){Icon(Icons.Default.Build,"Browser tools")}
            }
        }
    }
}

/** Settings never imply per-app cookie isolation: profile actions name their full shared scope. */
@Composable
internal fun PrivateAppSettings(
    controller: PrivateAppsController, appId: String, onUpdated: (PrivateAppCatalog.App) -> Unit,
    onBrowserSettings: () -> Unit, onClose: () -> Unit,
    onEdit: () -> Unit = {},
    onRemoved: (String) -> Unit = {},
) {
    val scope=rememberCoroutineScope()
    var app by remember(controller,appId) { mutableStateOf<PrivateAppCatalog.App?>(null) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var retainedLogin by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(controller,appId) {
        val result=withContext(Dispatchers.IO) { runCatching { controller.load().apps.single { it.id()==appId } } }
        if(!controller.isCurrent()) { close();return@LaunchedEffect }
        result.onSuccess { app=it; if(controller.supportsRetention())retainedLogin=controller.keepsSignedIn() }
            .onFailure { message="This saved app is unavailable. Return to the library." }
        busy=false
    }
    LaunchedEffect(controller) { while(isActive) { delay(250);if(!controller.isCurrent()) { app=null;confirmation=null;close();break } } }
    fun update(operation:()->PrivateAppCatalog) {
        busy=true;message=""
        scope.launch {
            val result=withContext(Dispatchers.IO) { runCatching { operation().apps.single { it.id()==appId } } }
            if(!controller.isCurrent()){app=null;close();return@launch}
            result.onSuccess { app=it;onUpdated(it) }.onFailure { message="Settings could not be saved." }
            busy=false
        }
    }
    GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn), onDismissRequest={close()},
        title={Text("Private app settings")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            app?.let { item ->
                Text(item.name(), style=MaterialTheme.typography.titleMedium)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("App-style presentation",Modifier.weight(1f))
                    Switch(modifier=Modifier.testTag("private-app-style-toggle"),checked=item.appStyle(),enabled=!busy,
                        onCheckedChange={value ->update {controller.setAppStyle(appId,value)}})
                }
                Text("In app style the address bar is available when controls are expanded. Browser style keeps the usual browser tools.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={onEdit()}){Text("Edit name, icon and start page")}
                if(item.discoveryReport().isNotBlank())Text(item.discoveryReport(),style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Show address bar",Modifier.weight(1f))
                    Switch(modifier=Modifier.testTag("private-app-address-toggle"),checked=item.addressBar(),enabled=!busy,
                        onCheckedChange={value ->update {controller.setAddressBar(appId,value)}})
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Require PIN when opening this saved app",Modifier.weight(1f))
                    Switch(modifier=Modifier.testTag("private-app-auth-toggle"),checked=item.requireAuthentication(),enabled=!busy,
                        onCheckedChange={value ->update {controller.setRequireAuthentication(appId,value)}})
                }
                Text(if(controller.owner==PrivateAppStore.Owner.PRIMARY)"Uses your existing Primary PIN. This protects opening the saved app, not direct navigation to its website."
                    else "Uses the separate Hidden PIN. This protects opening the saved app, not direct navigation to its website.",style=MaterialTheme.typography.bodySmall)
                TextButton(enabled=!busy,onClick={confirmation="reset"}) {Text("Reset saved app settings")}
                TextButton(enabled=!busy,onClick={confirmation="remove"}) {Text("Remove saved app")}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
            Text(if(controller.owner==PrivateAppStore.Owner.PRIMARY)
                "All Primary apps and browser tabs share cookies and website storage. Login retention and clearing affect the whole Primary browser profile."
                else "All apps and tabs in this Hidden browser share a temporary profile. Closing this browser clears every Hidden login in this session. Hidden logins cannot be retained.",style=MaterialTheme.typography.bodySmall)
            if(controller.supportsRetention())Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Keep all Primary browser logins on lock",Modifier.weight(1f))
                Switch(modifier=Modifier.testTag("private-app-retention-toggle"),checked=retainedLogin,enabled=!busy,
                    onCheckedChange={confirmation=if(it)"retain" else "forget"})
            }
            if(controller.canClearProfile())TextButton(enabled=!busy,onClick={confirmation="clear"}) {Text("Clear shared browser session and data")}
            else Text("Complete shared profile clearing requires a compatible Android WebView.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={close();onBrowserSettings()}) { Text("Browser tools and settings") }
        }},confirmButton={TextButton(onClick={close()}){Text("Done")}})
    confirmation?.let { action ->
        val description=when(action) {
            "clear" -> if(controller.owner==PrivateAppStore.Owner.PRIMARY)
                "This signs out ALL Primary apps and tabs and clears their shared cookies, website storage, cache. All tabs close and Primary locks. Saved app definitions, encrypted history, bookmarks and Vault files remain."
                else "This closes ALL tabs and clears the entire current Hidden browser profile, including every app's cookies and website storage. Saved app definitions, encrypted history, bookmarks and Vault files remain. Reopen Hidden Browser to start a fresh session."
            "retain" -> "Keep website login data for ALL Primary apps and browser tabs after locking. This changes the existing browser-wide clear-on-lock setting; websites may still expire their own sessions."
            "forget" -> "Clear shared website login data for ALL Primary apps and tabs whenever Primary locks. This changes the existing browser-wide setting."
            "reset" -> "Remove this saved app's icon and pin, and show its address bar. Its URL, name and PIN requirement remain. Shared website cookies and storage are unchanged."
            else -> "Remove this encrypted saved app definition. Shared website cookies, storage, bookmarks and downloaded Vault files remain."
        }
        GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={confirmation=null},
            title={Text("Confirm change")},text={Text(description)},confirmButton={TextButton(onClick={
                confirmation=null
                when(action) {
                    "reset" ->update {controller.resetSettings(appId)}
                    "remove" -> {busy=true;scope.launch {
                        val result=withContext(Dispatchers.IO){runCatching {controller.remove(appId)}}
                        if(!controller.isCurrent())close() else if(result.isSuccess){onRemoved(appId);close()} else {busy=false;message="Saved app could not be removed."}
                    }}
                    else ->runCatching {
                        if(action=="clear") {controller.clearSharedProfile();close()}
                        else {controller.setKeepSignedIn(action=="retain");retainedLogin=controller.keepsSignedIn()}
                    }.onFailure {message="Browser settings could not be changed."}
                }
            }){Text("Confirm")}},dismissButton={TextButton(onClick={confirmation=null}){Text("Cancel")}})
    }
}

/** The request captures one original authority. Neither rotation, backgrounding nor another unlock grants it validity. */
internal class PrivateAppAuthenticationRequest(
    val hidden:Boolean, val current:()->Boolean, val verify:(CharArray)->Boolean, val done:(Boolean)->Unit,
)

@Composable
internal fun PrivateAppAuthentication(request:PrivateAppAuthenticationRequest) {
    var pin by remember(request) {mutableStateOf("")}
    var busy by remember(request) {mutableStateOf(false)}
    var failed by remember(request) {mutableStateOf(false)}
    val inputOwner=remember(request) {java.util.concurrent.atomic.AtomicReference<CharArray?>(null)}
    val scope=rememberCoroutineScope()
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(request,lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) {pin="";request.done(false)}
        }
        lifecycle.addObserver(observer)
        onDispose {pin="";inputOwner.getAndSet(null)?.fill('\u0000');lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(request) {while(isActive) {delay(100);if(!request.current()){pin="";request.done(false);break}}}
    GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={pin="";request.done(false)},
        title={Text("Unlock saved app")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(request.hidden)"Enter your separate Hidden PIN." else "Enter your Primary PIN.")
            OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(if(request.hidden)12 else 128)},enabled=!busy,
                label={Text("PIN")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword))
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed)Text("Authentication failed or is temporarily unavailable.",color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(enabled=!busy && pin.length>=6,onClick={
            val input=pin.toCharArray();inputOwner.set(input);pin="";busy=true
            scope.launch(Dispatchers.Main.immediate) {
                try {
                    // Presentation authority may inspect native views: validate it only on Main.
                    // The verifier independently fences its credential/key or broker authority on IO.
                    val accepted=if(!request.current())false else withContext(Dispatchers.IO) {
                        try {request.verify(input)}catch(_:Exception){false}finally{input.fill('\u0000')}
                    }
                    busy=false
                    if(!request.current())request.done(false) else if(accepted)request.done(true) else failed=true
                } finally {input.fill('\u0000');inputOwner.compareAndSet(input,null)}
            }
        }){Text("Open app")}},dismissButton={TextButton(onClick={pin="";request.done(false)}){Text("Cancel")}})
}

@Composable
internal fun PrivateAppSwitcher(apps: List<Pair<String,PrivateAppCatalog.App>>, currentId: String, onSelect:(String)->Unit, onLibrary:()->Unit, onClose:()->Unit, onCloseSession:((String)->Unit)?=null) {
    GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest=onClose,
        title={Text("Switch private app")},text={Column() {
            if(apps.isEmpty())Text("No private apps are open.")
            apps.forEach { (id,app) -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                PrivateAppIcon(app.icon(),app.name(),32)
                TextButton(onClick={onSelect(id);onClose()},modifier=Modifier.weight(1f)) {
                    Text(app.name()+if(id==currentId)" · Current" else " · Open")
                }
                if(onCloseSession!=null)IconButton(onClick={onCloseSession(id)},modifier=Modifier.testTag("close-private-app-$id")) {Icon(Icons.Default.Close,"Close session for ${app.name()}")}
            } }
        }},confirmButton={TextButton(onClick={onClose();onLibrary()}){Text("Open from library")}},dismissButton={TextButton(onClick=onClose){Text("Close")}})
}

/** Branded transient UI only; no snapshots, new WebView or network requests. */
@Composable
internal fun PrivateAppLoading(app:PrivateAppCatalog.App) {
    Surface(Modifier.fillMaxSize().testTag("private-app-loading"),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            PrivateAppIcon(app.icon(),app.name())
            Text("Opening ${app.name()}…",Modifier.padding(12.dp),style=MaterialTheme.typography.titleMedium)
            CircularProgressIndicator()
        }
    }
}
