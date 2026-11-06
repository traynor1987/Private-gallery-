package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun NovaNavigation(shell:PreviewShellData,onRecents:()->Unit,onHome:()->Unit,onBack:()->Unit){
 Surface(tonalElevation=4.dp){
  if(shell.gestures){
   var x by remember{mutableFloatStateOf(0f)};var y by remember{mutableFloatStateOf(0f)};var menu by remember{mutableStateOf(false)}
   Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
    Box(Modifier.width(200.dp).fillMaxHeight().testTag("nova-gesture-strip").semantics{contentDescription="Nova navigation: swipe up Home, swipe sideways Back, hold Recents";customActions=listOf(CustomAccessibilityAction("Home"){onHome();true},CustomAccessibilityAction("Back"){onBack();true},CustomAccessibilityAction("Recents"){onRecents();true})}.combinedClickable(onClick=onHome,onLongClick=onRecents).pointerInput(Unit){detectDragGestures(onDragStart={x=0f;y=0f},onDragEnd={if(y < -24)onHome()else if(abs(x)>40)onBack()}){change,drag->change.consume();x+=drag.x;y+=drag.y}},contentAlignment=Alignment.Center){Surface(Modifier.size(96.dp,4.dp),shape=RoundedCornerShape(2.dp),color=MaterialTheme.colorScheme.onSurfaceVariant){}}
    Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreHoriz,"Navigation alternatives")};PanicHomeDropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Recents")},onClick={menu=false;onRecents()});DropdownMenuItem(text={Text("Home")},onClick={menu=false;onHome()});DropdownMenuItem(text={Text("Back")},onClick={menu=false;onBack()})}}
   }
  }else Row(Modifier.fillMaxWidth().height(52.dp).testTag("nova-navigation"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly){
   PreviewShellRules.order(shell.reverse).split(',').forEach{action->
    val callback=when(action){"recents"->onRecents;"home"->onHome;else->onBack}
    IconButton(onClick=callback,modifier=Modifier.width(72.dp).testTag("nova-$action")){
     Icon(when(action){"recents"->Icons.Default.ViewWeek;"home"->Icons.Default.CropSquare;else->Icons.Default.ChevronLeft},action.replaceFirstChar{it.uppercase()},Modifier.size(23.dp))
    }
   }
  }
 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun NovaRecents(data:PreviewData,pool:PreviewBrowserModel,onHome:()->Unit,onResume:(PreviewTab)->Unit,onInternal:(String)->Unit,onCloseInternal:(String)->Unit){
 val apps=pool.recents(data.state.current)
 val internal=data.shell.internalRecents.filter{it.first==data.state.current}.map{it.second}
 var closeAll by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-recents-screen")){
  PreviewHeader("Recent apps","${data.profileLabel} · retained pages",onHome)
  if(internal.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
   internal.forEach{id->val app=data.app(id)?:return@forEach;var drag by remember(id){mutableFloatStateOf(0f)};Card(onClick={onInternal(id)},modifier=Modifier.width(200.dp).pointerInput(id){detectVerticalDragGestures(onDragEnd={if(drag < -100)onCloseInternal(id);drag=0f},onDragCancel={drag=0f}){change,amount->change.consume();drag+=amount}}){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){AppIcon(app,36.dp);Text(app.name,Modifier.weight(1f).padding(start=8.dp));IconButton(onClick={onCloseInternal(id)}){Icon(Icons.Default.Close,"Close ${app.name}")}};Text("Private preview · content hidden",style=MaterialTheme.typography.labelSmall);TextButton(onClick={onInternal(id)}){Text("Resume")}}}}
  }
  if(apps.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.ViewWeek,null,Modifier.size(56.dp));Text(if(internal.isEmpty())"Your open apps appear here."else "Your web app cards appear here.",Modifier.padding(20.dp));Button(onClick=onHome){Text("Return Home")}}}
  else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
   val width=minOf(maxWidth*.82f,420.dp);val pager=rememberPagerState(pageCount={apps.size})
   LaunchedEffect(apps.size){if(pager.currentPage>=apps.size)pager.scrollToPage((apps.size-1).coerceAtLeast(0))}
   HorizontalPager(pager,Modifier.fillMaxSize().testTag("recents-pager"),pageSize=PageSize.Fixed(width),contentPadding=PaddingValues(horizontal=((maxWidth-width)/2).coerceAtLeast(12.dp)),pageSpacing=18.dp,key={apps[it].app.id}){index->
    val tab=apps[index];var drag by remember(tab.app.id){mutableFloatStateOf(0f)}
    Card(onClick={onResume(tab)},modifier=Modifier.fillMaxWidth().fillMaxHeight(.85f).testTag("recent-${tab.app.id}").graphicsLayer{translationY=drag.coerceIn(-160f,40f)}.pointerInput(tab.app.id){detectVerticalDragGestures(onDragEnd={if(drag < -100)pool.closeApp(tab.profile,tab.app.id);drag=0f},onDragCancel={drag=0f}){change,amount->change.consume();drag+=amount}},shape=RoundedCornerShape(30.dp)){
     Column(Modifier.fillMaxSize().padding(22.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){AppIcon(LaunchApp(tab.app.id,tab.app.name,"website",tab.app),44.dp);Text(tab.app.name,Modifier.weight(1f).padding(start=12.dp),maxLines=2,overflow=TextOverflow.Ellipsis);IconButton(onClick={pool.closeApp(tab.profile,tab.app.id)},modifier=Modifier.testTag("close-recent-${tab.app.id}")){Icon(Icons.Default.Close,"Close ${tab.app.name}")}}
      Box(Modifier.weight(1f).fillMaxWidth().padding(vertical=20.dp).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.colorScheme.surface)),RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){AppIcon(LaunchApp(tab.app.id,tab.app.name,"website",tab.app),84.dp);Text(tab.title,Modifier.padding(top=18.dp),maxLines=3,overflow=TextOverflow.Ellipsis);Text("Page retained · safe placeholder",Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelMedium)}}
      Text("${pool.tabs.count{it.profile==tab.profile&&it.app.id==tab.app.id}} retained tab(s) · swipe up to close",style=MaterialTheme.typography.labelMedium)
      Button(onClick={onResume(tab)},modifier=Modifier.fillMaxWidth().padding(top=12.dp)){Text("Resume")}
     }
    }
   }
  }
  if(apps.isNotEmpty()||internal.isNotEmpty())TextButton(onClick={closeAll=true},modifier=Modifier.align(Alignment.CenterHorizontally).padding(12.dp).testTag("recents-close-all")){Text("Close all apps")}
 }
 if(closeAll)PanicHomeAlertDialog(onDismissRequest={closeAll=false},title={Text("Close this profile’s web apps?")},text={Text("Playback stops and live pages close. Installed apps, layout and browser cookies remain.")},confirmButton={TextButton(onClick={pool.tabs.filter{it.profile==data.state.current}.toList().forEach{pool.close(it)};internal.toList().forEach(onCloseInternal);closeAll=false},modifier=Modifier.testTag("confirm-close-all")){Text("Close all")}},dismissButton={TextButton(onClick={closeAll=false}){Text("Cancel")}})
}

@Composable internal fun NovaMiniPlayer(pool:PreviewBrowserModel,profile:String,onResume:(PreviewTab)->Unit){
 val tab=pool.player(profile)?:return
 Surface(Modifier.fillMaxWidth().testTag("nova-mini-player"),tonalElevation=3.dp){Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){AppIcon(LaunchApp(tab.app.id,tab.app.name,"website",tab.app),36.dp);Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(tab.mediaTitle.ifBlank{tab.app.name},maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge);Text("${tab.app.name} · website reports ${tab.mediaState}",style=MaterialTheme.typography.labelSmall)};IconButton(onClick={onResume(tab)}){Icon(Icons.Default.OpenInNew,"Return to player")}}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NovaNotifications(data:PreviewData,onDismiss:()->Unit,onLaunch:(String)->Unit,media:@Composable ()->Unit={}){
 val shell=data.shell;val events=shell.events(data.state.current)
 PanicHomeModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().fillMaxHeight(.8f).padding(20.dp).testTag("nova-notifications")){
  Row(verticalAlignment=Alignment.CenterVertically){Text("Nova events",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);TextButton(onClick={shell.clear(data.state.current)},enabled=events.isNotEmpty(),modifier=Modifier.testTag("clear-events")){Text("Clear all")};IconButton(onClick=onDismiss){Icon(Icons.Default.Close,"Close Nova events")}}
  Text("Internal Nova events only · no Android notifications",style=MaterialTheme.typography.labelMedium)
  media()
  shell.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(events.isEmpty())Text("No Nova events yet.",Modifier.padding(vertical=32.dp))
  LazyColumn{items(events,key={it.id}){event->ListItem(headlineContent={Text((if(event.read)""else "● ")+event.text)},modifier=Modifier.clickable{shell.read(data.state.current,event.id);if(event.target.isNotBlank())onLaunch(event.target)},supportingContent={Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(event.time)))},trailingContent={IconButton(onClick={shell.delete(data.state.current,event.id)}){Icon(Icons.Default.DeleteOutline,"Delete event")}})}}
 }}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NovaShellSettings(data:PreviewData,onDismiss:()->Unit){
 val s=data.shell
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-shell-settings")){
  PreviewHeader("Navigation & multitasking","NovaOS settings",onBack=onDismiss)
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)){
  Text("Navigation & multitasking",style=MaterialTheme.typography.headlineSmall)
  Text("Nova’s controls sit above Android’s system navigation.",Modifier.padding(vertical=12.dp))
  ListItem(headlineContent={Text("Nova gesture strip")},supportingContent={Text("Swipe up: Home · sideways: Back · hold: Recents")},trailingContent={Switch(s.gestures,{s.configure(gestures=it)},modifier=Modifier.testTag("gesture-mode"))})
  ListItem(headlineContent={Text("Back on the left")},supportingContent={Text("Swap Back and Recents")},trailingContent={Switch(s.reverse,{s.configure(reverse=it)},modifier=Modifier.testTag("reverse-nav"))})
  ListItem(headlineContent={Text("Fold Edge Panel")},supportingContent={Text("Optional inset handle; does not replace Android gestures")},trailingContent={Switch(s.edge,{s.configure(edge=it)},modifier=Modifier.testTag("edge-enabled"))})
  if(s.edge){
   ListItem(headlineContent={Text("Left edge")},trailingContent={Switch(s.left,{s.configure(left=it)})})
   Text("Handle height");Slider(s.edgePosition,{s.configure(position=it)},valueRange=.2f.. .8f)
   Text("Edge favourites · up to 6",Modifier.padding(top=12.dp),style=MaterialTheme.typography.titleMedium)
   data.allApps().forEach{app->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(app.id in s.favourites(data.state.current),{s.favourite(data.state.current,app.id,it)});Text(app.name,maxLines=1,overflow=TextOverflow.Ellipsis)}}
  }
  Text("Media information appears only when a website exposes public Media Session metadata. Android17 package-local sessions enable only advertised controls when source ownership is unambiguous. Otherwise return to the website player. No fabricated controls or Android notification access.",Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.bodySmall)
  s.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth().testTag("shell-settings-done")){Text("Done")}
 }}
}

@Composable internal fun NovaEdgeHandle(shell:PreviewShellData,onOpen:()->Unit){
 if(shell.edge)Box(Modifier.fillMaxSize(),contentAlignment=BiasAlignment(if(shell.left)-1f else 1f,shell.edgePosition*2-1)){
  Box(Modifier.padding(horizontal=24.dp).size(36.dp,64.dp).testTag("nova-edge-handle").clickable(onClickLabel="Open Nova Edge Panel",onClick=onOpen),contentAlignment=Alignment.Center){Surface(Modifier.size(4.dp,44.dp),shape=RoundedCornerShape(4.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.8f)){}}
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NovaEdgePanel(data:PreviewData,pool:PreviewBrowserModel,onDismiss:()->Unit,onLaunch:(String)->Unit,onHome:()->Unit,onSettings:()->Unit){
 PanicHomeModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().heightIn(max=600.dp).verticalScroll(rememberScrollState()).padding(24.dp).testTag("nova-edge-panel")){
  Row(verticalAlignment=Alignment.CenterVertically){Text("Nova Edge",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);IconButton(onClick=onDismiss){Icon(Icons.Default.Close,"Close Edge Panel")}}
  Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={onLaunch("calm")}){Text("Calm Now")};TextButton(onClick={onLaunch("mind")}){Text("Nova Mind")};TextButton(onClick={onLaunch("browser-v1")}){Text("Browser V1")};TextButton(onClick=onHome,modifier=Modifier.testTag("edge-home")){Text("Home")};TextButton(onClick={onLaunch("store")},modifier=Modifier.testTag("edge-store")){Text("Nova Store")};TextButton(onClick=onSettings){Text("Settings")}}
  Text("Favourites",style=MaterialTheme.typography.titleMedium)
  val favourites=data.shell.favourites(data.state.current).mapNotNull{data.app(it)}
  if(favourites.isEmpty())Text("Choose favourite apps in Navigation settings.",Modifier.padding(vertical=16.dp))
  favourites.forEach{app->TextButton(onClick={onLaunch(app.id)},modifier=Modifier.fillMaxWidth()){AppIcon(app,36.dp);Text(app.name,Modifier.weight(1f).padding(start=12.dp))}}
  Text("Recent web apps",Modifier.padding(top=16.dp),style=MaterialTheme.typography.titleMedium)
  pool.recents(data.state.current).take(6).forEach{tab->TextButton(onClick={onLaunch(tab.app.id)},modifier=Modifier.fillMaxWidth()){AppIcon(LaunchApp(tab.app.id,tab.app.name,"website",tab.app),36.dp);Text(tab.app.name,Modifier.weight(1f).padding(start=12.dp))}}
 }}
}
