package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.browser.hidden.*
import uk.co.traynor.privategallery.core.hidden.HiddenSession

@Composable
internal fun HiddenBrowserHome(session:HiddenSession,onGallery:()->Unit,onVault:()->Unit,onFavourite:()->Unit,onSettings:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val activity=context as? MainActivity
    val preferences=remember {context.getSharedPreferences("hidden-browser-v16",android.content.Context.MODE_PRIVATE)}
    var mode by remember {mutableStateOf(HiddenConnectionPolicy.Mode.parse(preferences.getString("mode",null)))}
    var generation by remember {mutableIntStateOf(0)}
    var settings by remember {mutableStateOf(false)}
    var overview by remember {mutableStateOf(false)}
    val lifecycle=LocalLifecycleOwner.current
    var foreground by remember {mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val runtime=remember(session,mode,generation,foreground) {
        if(foreground && activity!=null && session.isUnlocked)runCatching {HiddenBrowserRuntime(activity,session,mode,reconnecting=generation>0)}.getOrNull() else null
    }
    val currentRuntime by rememberUpdatedState(runtime)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_STOP){currentRuntime?.close();foreground=false}
            if(event==Lifecycle.Event.ON_START){
                // State writes can coalesce during a fast stop/start before Compose redraws.
                // A monotonically new generation must retire the closed owner even then.
                generation++;foreground=true
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer)}
    }
    DisposableEffect(runtime) {runtime?.start();onDispose {runtime?.close()}}
    fun restart(selected:HiddenConnectionPolicy.Mode=mode) {
        runtime?.close();preferences.edit().putString("mode",selected.name).apply();mode=selected;generation++
    }
    val background=MaterialTheme.colorScheme.background.toArgb()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min=40.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Hidden Browser",style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
            TextButton(onClick={settings=true},contentPadding=PaddingValues(horizontal=8.dp)) {
                Text("${if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY) "Tor" else mode.indicator} — ${runtime?.status ?: "Disconnected"}",style=MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick={settings=true}) {Icon(Icons.Default.Settings,"Hidden browser settings")}
        }
        if(runtime==null)Text("Unlock to start a private browsing session.",Modifier.padding(16.dp))
        else {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(runtime.address,{runtime.address=it},Modifier.weight(1f),singleLine=true,
                    placeholder={Text("Search or enter address")},
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),
                    keyboardActions=KeyboardActions(onGo={runtime.navigate(runtime.address)}),
                    trailingIcon={IconButton(onClick={runtime.navigate(runtime.address)},enabled=runtime.connected){Icon(Icons.AutoMirrored.Filled.ArrowForward,"Go")}})
            }
            Row(Modifier.fillMaxWidth().height(48.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={runtime.back()},enabled=runtime.connected && runtime.current?.canGoBack()==true){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
                IconButton(onClick={runtime.forward()},enabled=runtime.connected && runtime.current?.canGoForward()==true){Icon(Icons.AutoMirrored.Filled.ArrowForward,"Forward")}
                IconButton(onClick={runtime.refreshOrStop()},enabled=runtime.connected){Icon(if(runtime.busy)Icons.Default.Close else Icons.Default.Refresh,if(runtime.busy)"Stop" else "Refresh")}
                IconButton(onClick={runtime.newTab()},enabled=runtime.connected){Icon(Icons.Default.Add,"New tab")}
                TextButton(onClick={overview=true}){Text("Tabs ${runtime.tabs.size}")}
                IconButton(onClick={runtime.download()},enabled=runtime.connected && !runtime.downloading){Icon(Icons.Default.Download,"Save media to Hidden")}
            }
            if(runtime.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!runtime.connected) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(runtime.status=="Connecting" || runtime.status=="Reconnecting")runtime.detail else "Connection failed",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                if(mode!=HiddenConnectionPolicy.Mode.VPN_TOR)TextButton(onClick={restart()}){Text("Retry")}
            }
            if(runtime.notice.isNotBlank())Text(runtime.notice,Modifier.padding(horizontal=12.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
            if(runtime.downloading) {
                if(runtime.downloadTotal>0)LinearProgressIndicator(progress={ (runtime.downloadedBytes.toFloat()/runtime.downloadTotal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                Text("${runtime.downloadedBytes / 1024} KB received" + if(runtime.downloadTotal>0)" / ${runtime.downloadTotal / 1024} KB · verifying before save" else " · total unknown",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val view=runtime.current
                if(runtime.connected && view!=null && runtime.showingPage)key(view) {
                    AndroidView(factory={(view.parent as? android.view.ViewGroup)?.removeView(view);view},update={it.setBackgroundColor(background)},modifier=Modifier.fillMaxSize())
                } else Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Public,contentDescription=null,modifier=Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if(runtime.connected)"Private browsing" else if(mode==HiddenConnectionPolicy.Mode.VPN_TOR)"Mode unavailable" else "Private connection",style=MaterialTheme.typography.titleMedium)
                    Text(if(runtime.connected)"Search or enter a website above." else "Open connection settings for status and diagnostics.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        // The shared app shell supplies the original bottom navigation and Fold insets.
    }
    if(settings) {
        var privacy by remember {mutableStateOf(false)}
        var diagnostics by remember {mutableStateOf(false)}
        AlertDialog(onDismissRequest={settings=false},title={Text("Browser settings")},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
                Text("Connection mode",style=MaterialTheme.typography.labelLarge)
                HiddenConnectionPolicy.Mode.entries.forEach {choice ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=mode==choice,onClick={restart(choice)})
                        TextButton(onClick={restart(choice)},contentPadding=PaddingValues(0.dp)) {Text(if(choice==HiddenConnectionPolicy.Mode.VPN_TOR)"VPN + Tor · unavailable" else choice.label)}
                    }
                }
                Text(runtime?.status ?: "Disconnected",style=MaterialTheme.typography.labelLarge)
                if(mode!=HiddenConnectionPolicy.Mode.VPN_TOR)TextButton(onClick={restart()}){Text("Retry connection")}
                TextButton(onClick={restart()}){Text("Clear browsing data")}
                TextButton(onClick={settings=false;onVault()}){Text("Manage Hidden downloads")}
                TextButton(onClick={privacy=!privacy}){Text("Privacy information")}
                if(privacy)Text("Browsing uses only the selected transport. VPN + Tor is unavailable. JavaScript, forms and frames are blocked; safe GET redirects stay on the selected route. Some websites will not work. This is not Tor Browser. Tabs and cookies are session-only. Browser profile data is app-private; downloads are encrypted in Hidden Vault. A fixed HTTPS check to example.com verifies connectivity over the selected route. Physical DNS-leak verification remains required.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={diagnostics=!diagnostics}){Text("Connection diagnostics")}
                if(diagnostics)Text(runtime?.detail ?: "No active session. No addresses or browsing data are logged.",style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(onClick={settings=false}){Text("Done")}})
    }
    if(overview)AlertDialog(onDismissRequest={overview=false},title={Text("Hidden tabs")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        runtime?.tabs?.toList()?.forEachIndexed {index,view ->Row {
            TextButton(onClick={runtime.select(view);overview=false},modifier=Modifier.weight(1f)){Text(view.title?.take(60) ?: "Tab ${index+1}")}
            IconButton(onClick={runtime.closeTab(view)}){Icon(Icons.Default.Close,"Close tab ${index+1}")}
        }}
    }},confirmButton={TextButton(onClick={overview=false}){Text("Done")}})
}

@Composable
internal fun HiddenBrowserPreferences() {
    val context=LocalContext.current
    val preferences=remember {context.getSharedPreferences("hidden-browser-v16",android.content.Context.MODE_PRIVATE)}
    var mode by remember {mutableStateOf(HiddenConnectionPolicy.Mode.parse(preferences.getString("mode",null)))}
    Column {
        Text("Hidden Browser",style=MaterialTheme.typography.titleMedium)
        Text("Connection Mode")
        HiddenConnectionPolicy.Mode.entries.forEach {choice ->Row {
            RadioButton(selected=mode==choice,onClick={preferences.edit().putString("mode",choice.name).apply();mode=choice})
            Text(choice.label,Modifier.padding(top=12.dp))
        }}
        Text("VPN + Tor is currently unavailable. Tor Only is the default. VPN Only does not provide Tor anonymity.")
        Text("JavaScript is disabled; this restricted browser does not provide Tor Browser fingerprinting protection. Cookies, history and tabs are cleared when browsing ends. Manage encrypted downloads in Hidden Vault. Connection retry and data clearing are inside Hidden Browser Settings.")
    }
}
