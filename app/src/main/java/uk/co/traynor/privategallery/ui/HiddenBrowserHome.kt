package uk.co.traynor.privategallery.ui

import uk.co.traynor.privategallery.ui.GalleryAlertDialog as AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val focusManager=LocalFocusManager.current
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
    var libraryKind by remember(runtime) { mutableStateOf<String?>(null) }
    var libraryItems by remember(runtime) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(runtime, runtime?.connected, session.isUnlocked) {
        if (runtime?.connected != true || !session.isUnlocked) { libraryKind=null;libraryItems=emptyList() }
    }
    val currentRuntime by rememberUpdatedState(runtime)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_STOP && currentRuntime?.interactiveActivityActive!=true){currentRuntime?.close();foreground=false}
            if(event==Lifecycle.Event.ON_START){
                // State writes can coalesce during a fast stop/start before Compose redraws.
                // A monotonically new generation must retire the closed owner even then.
                if(currentRuntime?.interactiveActivityLaunched!=true)generation++;foreground=true
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
    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(start=16.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hidden Browser",style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY) "Tor" else mode.indicator} · ${runtime?.status ?: "Disconnected"}",
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick={focusManager.clearFocus();settings=true},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Settings,"Hidden browser settings")}
        }
        if(runtime==null)Text("Unlock to start a private browsing session.",Modifier.padding(16.dp))
        else {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                TextField(runtime.address,{runtime.address=it},Modifier.weight(1f),singleLine=true,
                    shape=RoundedCornerShape(16.dp),
                    colors=TextFieldDefaults.colors(focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),
                    leadingIcon={Icon(Icons.Default.Language,"Web address")},
                    placeholder={Text("Search or enter address")},
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),
                    keyboardActions=KeyboardActions(onGo={focusManager.clearFocus();runtime.navigate(runtime.address)}),
                    trailingIcon={IconButton(onClick={focusManager.clearFocus();runtime.navigate(runtime.address)},enabled=runtime.connected){Icon(Icons.AutoMirrored.Filled.ArrowForward,"Go")}})
            }
            if(runtime.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!runtime.connected) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(mode==HiddenConnectionPolicy.Mode.VPN_TOR) "VPN + Tor is unavailable" else if(runtime.status=="Connecting" || runtime.status=="Reconnecting") "Establishing private connection…" else "Connection failed · open settings for details",
                    Modifier.weight(1f).semantics { liveRegion=LiveRegionMode.Polite },style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(mode!=HiddenConnectionPolicy.Mode.VPN_TOR)TextButton(onClick={restart()}){Text("Retry")}
            }
            if(runtime.notice.isNotBlank())Text(runtime.notice,Modifier.heightIn(max=80.dp).verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
            if(runtime.downloading) {
                if(runtime.downloadTotal>0)LinearProgressIndicator(progress={ (runtime.downloadedBytes.toFloat()/runtime.downloadTotal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                Text("${runtime.downloadedBytes / 1024} KB received" + if(runtime.downloadTotal>0)" / ${runtime.downloadTotal / 1024} KB · verifying before save" else " · total unknown",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val view=runtime.current
                if(runtime.connected && view!=null && runtime.showingPage)key(view) {
                    AndroidView(factory={(view.parent as? android.view.ViewGroup)?.removeView(view);view},update={it.setBackgroundColor(background)},modifier=Modifier.fillMaxSize())
                } else Column(Modifier.align(Alignment.Center).widthIn(max=400.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Public,contentDescription=null,modifier=Modifier.size(40.dp))
                    Text(if(runtime.connected)"Private browsing" else if(mode==HiddenConnectionPolicy.Mode.VPN_TOR)"Mode unavailable" else "Private connection",style=MaterialTheme.typography.titleMedium)
                    Text(if(runtime.connected)"Search or enter a website above." else "Open connection settings for status and diagnostics.",style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                    if(!runtime.connected)TextButton(onClick={settings=true}) { Text("Connection settings") }
                }
            }
            GalleryBrowserToolbar {
                    IconButton(modifier=Modifier.size(48.dp),onClick={runtime.back()},enabled=runtime.connected && runtime.current?.canGoBack()==true){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
                    IconButton(modifier=Modifier.size(48.dp),onClick={runtime.forward()},enabled=runtime.connected && runtime.current?.canGoForward()==true){Icon(Icons.AutoMirrored.Filled.ArrowForward,"Forward")}
                    IconButton(modifier=Modifier.size(48.dp),onClick={runtime.refreshOrStop()},enabled=runtime.connected){Icon(if(runtime.busy)Icons.Default.Close else Icons.Default.Refresh,if(runtime.busy)"Stop" else "Refresh")}
                    IconButton(modifier=Modifier.size(48.dp),onClick={runtime.newTab()},enabled=runtime.connected){Icon(Icons.Default.Add,"New tab")}
                    TextButton(onClick={focusManager.clearFocus();overview=true},modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=4.dp)){Text("Tabs ${runtime.tabs.size}",maxLines=1)}
                    IconButton(modifier=Modifier.size(48.dp),onClick={runtime.download()},enabled=runtime.connected && !runtime.downloading){Icon(Icons.Default.Download,"Save media to Hidden")}
            }
        }
        // The shared app shell supplies the original bottom navigation and Fold insets.
    }
    if(settings) {
        var privacy by remember {mutableStateOf(false)}
        var diagnostics by remember {mutableStateOf(false)}
        AlertDialog(scrollBody = false, modifier=Modifier.semantics { testTag="hidden-browser-settings" },onDismissRequest={settings=false},title={Text("Browser settings")},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Connection mode",style=MaterialTheme.typography.labelLarge)
                HiddenConnectionPolicy.Mode.entries.forEach {choice ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=mode==choice,onClick={restart(choice)},enabled=choice!=HiddenConnectionPolicy.Mode.VPN_TOR)
                        TextButton(onClick={restart(choice)},enabled=choice!=HiddenConnectionPolicy.Mode.VPN_TOR,contentPadding=PaddingValues(0.dp)) {Text(if(choice==HiddenConnectionPolicy.Mode.VPN_TOR)"VPN + Tor · unavailable" else choice.label)}
                    }
                }
                Text(runtime?.status ?: "Disconnected",style=MaterialTheme.typography.labelLarge)
                if(mode!=HiddenConnectionPolicy.Mode.VPN_TOR)TextButton(onClick={restart()}){Text("Retry connection")}
                TextButton(onClick={if(runtime?.clearBrowsingData()==true)restart()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Clear browsing data")}
                TextButton(enabled=runtime?.connected==true,onClick={
                    runtime?.bookmarkCurrent();settings=false
                }){Text("Bookmark current page")}
                TextButton(enabled=runtime?.connected==true,onClick={
                    libraryItems=runtime?.bookmarks().orEmpty();libraryKind="Bookmarks";settings=false
                }){Text("Bookmarks")}
                TextButton(enabled=runtime?.connected==true,onClick={
                    libraryItems=runtime?.history().orEmpty();libraryKind="History";settings=false
                }){Text("History")}
                TextButton(enabled=runtime?.connected==true,onClick={runtime?.let {it.updateDesktopSite(!it.desktopSite)}}) {Text(if(runtime?.desktopSite==true)"Mobile layout" else "Desktop layout")}
                var saveHistory by remember(runtime,settings) {mutableStateOf(runtime?.savesHistory()==true)}
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(
                    value=saveHistory,role=Role.Switch,onValueChange={enabled ->
                        runtime?.saveHistory(enabled);saveHistory=runtime?.savesHistory()==true
                    }),verticalAlignment=Alignment.CenterVertically) {
                    Switch(checked=saveHistory,onCheckedChange=null)
                    Text("Save Hidden history in this mode",Modifier.padding(start=8.dp))
                }
                TextButton(onClick={runtime?.clearHistory()}){Text("Clear Hidden history")}
                Text("Bookmarks and optional history are encrypted in Hidden Vault, separately for each mode. Clearing browsing data keeps bookmarks.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={settings=false;onVault()}){Text("Manage Hidden downloads")}
                TextButton(onClick={privacy=!privacy}){Text("Privacy information")}
                if(privacy)Text("Browsing uses only the selected transport. VPN + Tor is unavailable. VPN Only opens a separate interactive browser with JavaScript and forms. Tor Only uses the separate native interface with JavaScript off and forms/uploads through an isolated Tor route, once verification gates permit activation. Some websites will not work. This is not Tor Browser. Tabs and cookies are session-only. Browser profile data is app-private; downloads are encrypted in Hidden Vault. A fixed HTTPS check to example.com verifies connectivity over the selected route. Physical DNS-leak verification remains required.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={diagnostics=!diagnostics}){Text("Connection diagnostics")}
                if(diagnostics)Text(runtime?.detail ?: "No active session. No addresses or browsing data are logged.",style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(onClick={settings=false}){Text("Done")}})
    }
    if(runtime?.connected==true && session.isUnlocked) libraryKind?.let { kind ->
        AlertDialog(scrollBody = false, onDismissRequest={libraryKind=null;libraryItems=emptyList()},
            title={Text("Hidden $kind")},
            text={HiddenNativeLibraryContent(libraryItems,kind=="Bookmarks",
                {url ->runtime?.navigate(url);libraryKind=null;libraryItems=emptyList()},
                {url ->runtime?.removeBookmark(url);libraryItems=runtime?.bookmarks().orEmpty()})},confirmButton={TextButton(onClick={libraryKind=null;libraryItems=emptyList()}){Text("Done")}})
    }
    if(overview)AlertDialog(scrollBody = false, onDismissRequest={overview=false},title={Text("Hidden tabs")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
        if(runtime?.tabs.isNullOrEmpty())Text("No open tabs. Connect, then open a new tab to start browsing.",style=MaterialTheme.typography.bodyMedium)
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
            RadioButton(selected=mode==choice,onClick={preferences.edit().putString("mode",choice.name).apply();mode=choice},enabled=choice!=HiddenConnectionPolicy.Mode.VPN_TOR)
            TextButton(onClick={preferences.edit().putString("mode",choice.name).apply();mode=choice},enabled=choice!=HiddenConnectionPolicy.Mode.VPN_TOR) {
                Text(if(choice==HiddenConnectionPolicy.Mode.VPN_TOR) "VPN + Tor · unavailable" else choice.label)
            }
        }}
        Text("VPN + Tor is currently unavailable. Tor Only is the default. VPN Only does not provide Tor anonymity.")
        Text("JavaScript is disabled in Tor Only. Both modes use an independent native browser once their verification gates pass. Neither mode provides Tor Browser fingerprinting protection. Cookies and tabs are cleared when browsing ends. Optional history and bookmarks remain encrypted until explicitly removed. Manage encrypted downloads in Hidden Vault. Connection retry and data clearing are inside Hidden Browser Settings.")
    }
}
