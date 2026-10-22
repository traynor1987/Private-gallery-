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
    val runtime=remember(session,mode,generation) {if(activity!=null && session.isUnlocked)runCatching {HiddenBrowserRuntime(activity,session,mode)}.getOrNull() else null}
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(runtime,lifecycle) {
        runtime?.start()
        val observer=LifecycleEventObserver {_,event ->if(event==Lifecycle.Event.ON_STOP)runtime?.close()}
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer);runtime?.close()}
    }
    fun restart(selected:HiddenConnectionPolicy.Mode=mode) {
        runtime?.close();preferences.edit().putString("mode",selected.name).apply();mode=selected;generation++
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Hidden Browser",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(vertical=12.dp))
            Text(if(runtime?.connected==true)mode.indicator else runtime?.status ?: "Locked",modifier=Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.labelMedium)
            IconButton(onClick={settings=true}) {Icon(Icons.Default.Settings,"Hidden browser settings")}
        }
        if(runtime==null)Text("Unlock Hidden Vault to browse.",Modifier.padding(16.dp))
        else {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp)) {
                OutlinedTextField(runtime.address,{runtime.address=it},Modifier.weight(1f),singleLine=true,label={Text("Address or search")})
                IconButton(onClick={runtime.navigate(runtime.address)},enabled=runtime.connected) {Icon(Icons.Default.ArrowForward,"Go")}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                IconButton(onClick={runtime.current?.goBack()},enabled=runtime.connected && runtime.current?.canGoBack()==true){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
                IconButton(onClick={runtime.current?.goForward()},enabled=runtime.connected && runtime.current?.canGoForward()==true){Icon(Icons.AutoMirrored.Filled.ArrowForward,"Forward")}
                IconButton(onClick={if(runtime.busy)runtime.current?.stopLoading() else runtime.current?.reload()},enabled=runtime.connected){Icon(if(runtime.busy)Icons.Default.Close else Icons.Default.Refresh,if(runtime.busy)"Stop" else "Refresh")}
                IconButton(onClick={runtime.newTab()},enabled=runtime.connected){Icon(Icons.Default.Add,"New tab")}
                TextButton(onClick={overview=true}){Text("Tabs ${runtime.tabs.size}")}
                IconButton(onClick={settings=true}){Icon(Icons.Default.Download,"Downloads")}
            }
            if(!runtime.connected)Column(Modifier.padding(16.dp)) {
                Text(runtime.detail)
                if(mode!=HiddenConnectionPolicy.Mode.VPN_TOR)Button(onClick={restart()}){Text("Retry connection")}
                TextButton(onClick={settings=true}){Text("Connection mode")}
            }
            if(runtime.notice.isNotBlank())Text(runtime.notice,Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f)) {
                runtime.current?.let {view ->key(view) {AndroidView(factory={(view.parent as? android.view.ViewGroup)?.removeView(view);view},modifier=Modifier.fillMaxSize())}}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            TextButton(onClick=onGallery){Text("Gallery")};TextButton(onClick=onVault){Text("Vault")}
            TextButton(onClick=onFavourite){Text("Favourite")};TextButton(onClick=onSettings){Text("Settings")}
        }
    }
    if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Hidden Browser Settings")},text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())) {
            Text("Connection Mode",style=MaterialTheme.typography.titleMedium)
            HiddenConnectionPolicy.Mode.entries.forEach {choice ->
                Row {RadioButton(selected=mode==choice,onClick={restart(choice)});TextButton(onClick={restart(choice)}){Text(choice.label)}}
            }
            Text(runtime?.detail.orEmpty(),style=MaterialTheme.typography.bodySmall)
            Text("VPN + Tor is unavailable until its route can be enforced. Modes never fall back automatically.")
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            Text("Privacy",style=MaterialTheme.typography.titleMedium)
            Text("JavaScript: disabled. Forms, embedded frames and automatic redirects are blocked. Some sites will not work. This is not Tor Browser and does not provide its fingerprinting protections.")
            Text("Tabs, history and cookies are session-only. Leaving this browser closes the session; Hidden Vault authentication follows your existing timeout.")
            TextButton(onClick={restart()}){Text("Clear history, cookies, site data and cache")}
            TextButton(onClick={restart()}){Text(if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY)"Retry / new Tor session" else "Retry connection")}
            Text("Downloads",style=MaterialTheme.typography.titleMedium)
            Text("Image/video URLs only. Saved directly to encrypted Hidden Vault. Manage and restore them from Vault.")
            TextButton(onClick={runtime?.download();settings=false},enabled=runtime?.connected==true){Text("Save current media to Hidden")}
            TextButton(onClick={settings=false;onVault()}){Text("Manage Hidden downloads")}
            Text("Connection diagnostics: ${runtime?.status ?: "Disconnected"}. No URLs or browsing history are logged.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick={settings=false}){Text("Done")}})
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
