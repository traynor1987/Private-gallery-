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
@Composable
internal fun PrivateAppControls(
    app: PrivateAppCatalog.App, url: String, mode: String, enabled: Boolean,
    canGoBack: Boolean, canGoForward: Boolean, loading: Boolean,
    onBack: () -> Unit, onForward: () -> Unit, onRefresh: () -> Unit,
    onHome: () -> Unit, onLibrary: () -> Unit, onSwitch: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("private-app-controls")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
            PrivateAppIcon(app.icon(), app.name())
            Column(Modifier.weight(1f).padding(start=12.dp)) {
                Text(app.name(), style=MaterialTheme.typography.titleMedium, maxLines=1, overflow=TextOverflow.Ellipsis)
                Text(mode, style=MaterialTheme.typography.labelSmall)
                val origin=runCatching { Uri.parse(url).let { "${it.scheme}://${it.host.orEmpty()}${if(it.port>=0)":${it.port}" else ""}" } }.getOrDefault("")
                Text(origin.ifBlank { "Opening app…" }, style=MaterialTheme.typography.bodySmall, maxLines=1, overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick=onSettings, enabled=enabled) { Icon(Icons.Default.Settings, "Private app settings") }
        }
        // Leaving a manifest's scope is allowed by the existing browser, but remains visible.
        if (url.isNotBlank() && !PrivateAppPolicy.inScope(url, app.scope().ifBlank { app.startUrl() }))
            Text("Browsing outside this app's saved scope", Modifier.padding(horizontal=12.dp), style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly) {
            IconButton(onClick=onBack, enabled=enabled && canGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"App back") }
            IconButton(onClick=onForward, enabled=enabled && canGoForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward,"App forward") }
            IconButton(onClick=onRefresh, enabled=enabled) { Icon(if(loading)Icons.Default.Close else Icons.Default.Refresh,if(loading)"Stop app loading" else "Refresh app") }
            IconButton(onClick=onHome, enabled=enabled) { Icon(Icons.Default.Home,"App home") }
            IconButton(onClick=onLibrary) { Icon(Icons.Default.Apps,"Private Apps library") }
            IconButton(onClick=onSwitch) { Icon(Icons.Default.SwapHoriz,"Switch private app") }
        }
    }
}

/** Encrypted definition preferences only; this deliberately offers no per-app cookie controls. */
@Composable
internal fun PrivateAppSettings(
    controller: PrivateAppsController, appId: String, onUpdated: (PrivateAppCatalog.App) -> Unit,
    onBrowserSettings: () -> Unit, onClose: () -> Unit,
) {
    val scope=rememberCoroutineScope()
    var app by remember(controller,appId) { mutableStateOf<PrivateAppCatalog.App?>(null) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(controller,appId) {
        val result=withContext(Dispatchers.IO) { runCatching { controller.load().apps.single { it.id()==appId } } }
        if(!controller.isCurrent()) { close();return@LaunchedEffect }
        result.onSuccess { app=it }.onFailure { message="This saved app is unavailable. Return to the library." }
        busy=false
    }
    LaunchedEffect(controller) { while(isActive) { delay(250);if(!controller.isCurrent()) { app=null;close();break } } }
    GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn), onDismissRequest={close()},
        title={Text("Private app settings")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            app?.let { item ->
                Text(item.name(), style=MaterialTheme.typography.titleMedium)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Show address bar",Modifier.weight(1f))
                    Switch(modifier=Modifier.testTag("private-app-address-toggle"),checked=item.addressBar(),enabled=!busy,onCheckedChange={value ->
                        busy=true;message=""
                        scope.launch {
                            val result=withContext(Dispatchers.IO) { runCatching {
                                controller.setAddressBar(appId,value).apps.single { it.id()==appId }
                            } }
                            if(!controller.isCurrent()){app=null;close();return@launch}
                            result.onSuccess { app=it;onUpdated(it) }.onFailure { message="Settings could not be saved." }
                            busy=false
                        }
                    })
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
            Text(if(controller.owner==PrivateAppStore.Owner.PRIMARY)
                "Apps share Primary browser cookies and storage. Login retention follows the browser setting."
                else "Apps share this Hidden browser's temporary cookies and storage. All Hidden logins are cleared when the browser closes.",style=MaterialTheme.typography.bodySmall)
            Text("Fresh authentication on each open and isolated per-app login storage are not available. Website uploads and downloads use the existing browser and Vault controls.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={close();onBrowserSettings()}) { Text("Browser tools and settings") }
        }},confirmButton={TextButton(onClick={close()}){Text("Done")}})
}

@Composable
internal fun PrivateAppSwitcher(apps: List<Pair<String,PrivateAppCatalog.App>>, currentId: String, onSelect:(String)->Unit, onLibrary:()->Unit, onClose:()->Unit) {
    GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest=onClose,
        title={Text("Switch private app")},text={Column() {
            if(apps.isEmpty())Text("No private apps are open.")
            apps.forEach { (id,app) -> TextButton(onClick={onSelect(id);onClose()},modifier=Modifier.fillMaxWidth()) {
                Text(app.name()+if(id==currentId)" · Current" else "")
            } }
        }},confirmButton={TextButton(onClick={onClose();onLibrary()}){Text("Open from library")}},dismissButton={TextButton(onClick=onClose){Text("Close")}})
}
