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
import androidx.compose.ui.platform.testTag
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
 val activity=context as? MainActivity
 // Launcher identity selects transport. A legacy preference can never turn Browser into Tor.
 val mode=activity?.novaHiddenBrowserMode ?: HiddenConnectionPolicy.Mode.VPN_ONLY
 var generation by remember(session,mode){mutableIntStateOf(0)}
 var settings by remember{mutableStateOf(false)}
 val lifecycle=LocalLifecycleOwner.current
 var foreground by remember{mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
 val runtime=remember(session,mode,generation,foreground) {
  if(foreground&&activity!=null&&session.isUnlocked)runCatching{HiddenBrowserRuntime(activity,session,mode,reconnecting=generation>0)}.getOrNull()else null
 }
 val current by rememberUpdatedState(runtime)
 DisposableEffect(lifecycle) {
  val observer=LifecycleEventObserver{_,event->
   if(event==Lifecycle.Event.ON_STOP&&current?.interactiveActivityActive!=true){current?.close();foreground=false}
   if(event==Lifecycle.Event.ON_START){if(current?.interactiveActivityLaunched!=true)generation++;foreground=true}
  }
  lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}
 }
 DisposableEffect(runtime){runtime?.start();onDispose{runtime?.close()}}
 fun restart(){runtime?.close();generation++}
 val tor=mode==HiddenConnectionPolicy.Mode.TOR_ONLY
 Surface(modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
  Column(Modifier.fillMaxSize().imePadding()) {
   NovaAppHeader(if(activity?.homePrivateAppsRequested==true)"Private Apps"else if(tor)"Tor Browser"else"Browser","Hidden Space",
    onHome={runtime?.close();activity?.returnPrivateHome()},onLock={activity?.lockNovaSpace()},onPanic={activity?.panicExit()})
   BrowserAddressBar("",{},false,runtime?.connected!=true,{},{},{})
   NovaBrowserStatus(tor,false,runtime?.detail.orEmpty(),{settings=true})
   Box(Modifier.weight(1f)) {NovaBrowserConnectionSurface(tor,runtime?.status?:"Disconnected",runtime?.detail.orEmpty(),::restart,{settings=true})}
   BrowserNavigationToolbar(false,false,0,{},{},{settings=true},{settings=true},{settings=true},{settings=true})
  }
 }
 if(settings)BrowserPanel("Connection settings",if(tor)"Tor Browser"else"Browser",{settings=false}) {
  Column(Modifier.testTag("hidden-browser-settings").verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(if(tor)"Tor only"else"VPN only",style=MaterialTheme.typography.titleMedium)
   Text(runtime?.status?:"Disconnected")
   Button(onClick={settings=false;restart()}){Text("Retry connection")}
   OutlinedButton(onClick={settings=false;runtime?.close();activity?.returnPrivateHome()}){Text("Clear Session")}
   TextButton(onClick=onVault){Text("Manage Hidden downloads")}
   TextButton(onClick=onSettings){Text("Workspace settings")}
   Text(runtime?.detail.orEmpty(),style=MaterialTheme.typography.bodySmall)
   Text(if(tor)"Tor starts with JavaScript and saved history off. Tabs and cookies end with this session. Tor Browser fingerprinting protections are not provided."else"Interactive websites use the verified VPN only. Tabs and cookies remain independent of Primary and end with this session.",style=MaterialTheme.typography.bodySmall)
  }
 }
}

@Composable
internal fun HiddenBrowserPreferences() {
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Private browsers",style=MaterialTheme.typography.titleMedium)
  Text("Browser uses VPN only. Open Tor Browser from Hidden Home for a separate Tor session.")
  Text("Both applications keep independent bookmarks and history in encrypted Hidden storage. Tabs and cookies are session-only. Neither application falls back to the direct network.",style=MaterialTheme.typography.bodySmall)
 }
}
