package uk.co.traynor.novaos.preview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.io.File
import kotlin.math.abs

/** Installed only on empty, non-scrollable Home content; never an ancestor of a website/widget. */
internal fun Modifier.emptyHomeSwipe(empty:(Offset)->Boolean,onDown:()->Unit,onUp:()->Unit)=composed{
 val latestEmpty by rememberUpdatedState(empty);val down by rememberUpdatedState(onDown);val up by rememberUpdatedState(onUp)
 pointerInput(Unit){
 val threshold=48.dp.toPx()
 awaitEachGesture{
  val first=awaitFirstDown(requireUnconsumed=false)
  if(!latestEmpty(first.position))return@awaitEachGesture
  var dx=0f;var dy=0f
  do{val event=awaitPointerEvent(PointerEventPass.Initial);val change=event.changes.firstOrNull{it.id==first.id}?:break
   dx+=change.position.x-change.previousPosition.x;dy+=change.position.y-change.previousPosition.y
   if(abs(dy)>threshold&&abs(dy)>abs(dx)*1.5f){change.consume();if(dy>0)down()else up();break}
  }while(event.changes.any{it.pressed})
 }
 }
}
@Composable internal fun NovaQuickPanel(data:PreviewData,pool:PreviewBrowserModel,audio:MindAudio,video:NovaVideoModel,widgets:WidgetData,filesDir:File,onDismiss:()->Unit,onAction:(String)->Unit,onResume:(PreviewTab)->Unit){
 var expanded by rememberSaveable{mutableStateOf(false)}
 val entry=remember{Animatable(if(widgets.reduced)0f else -160f)}
 LaunchedEffect(Unit){entry.animateTo(0f,tween(if(widgets.reduced)0 else 220))}
 var panelHeight by remember{mutableFloatStateOf(300f)};var closing by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val close:()->Unit={if(!closing){closing=true;scope.launch{entry.animateTo(-panelHeight,tween(if(widgets.reduced)0 else 180));onDismiss()}}}
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  val threshold=with(LocalDensity.current){24.dp.toPx()}
  val window=(LocalView.current.parent as? DialogWindowProvider)?.window
  SideEffect{window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
  BackHandler(onBack=close)
  BoxWithConstraints(Modifier.fillMaxSize().testTag("quick-overlay")){
   Box(Modifier.fillMaxSize().clickable(onClickLabel="Dismiss Quick Panel",onClick=close)){
    WallpaperSurface(data.profile.wallpaper,filesDir,Modifier.fillMaxSize().blur(if(widgets.reduced)0.dp else 18.dp))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.38f)))
   }
   val height=maxHeight*.9f;val tileColumns=if(maxWidth>=600.dp)3 else 2
   Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp).widthIn(max=720.dp).fillMaxWidth().heightIn(max=height).animateContentSize(tween(if(widgets.reduced)0 else 180)).graphicsLayer{translationY=entry.value}.onSizeChanged{panelHeight=it.height.toFloat()}.testTag("nova-quick-panel"),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.97f),contentColor=MaterialTheme.colorScheme.onSurface,shadowElevation=12.dp){
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=20.dp)){
     var travel by remember{mutableFloatStateOf(0f)}
     Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("quick-drag-handle").pointerInput(Unit){detectVerticalDragGestures(onDragStart={travel=0f},onDragEnd={if(travel < -threshold)close()else if(travel>threshold)expanded=true}){change,amount->change.consume();travel+=amount}},verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text("Nova Quick Panel",style=MaterialTheme.typography.titleLarge);Text("Your NovaOS controls",style=MaterialTheme.typography.labelSmall)}
      IconButton(onClick={expanded=!expanded},modifier=Modifier.testTag("quick-expand")){Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expanded)"Compact panel"else "Expand panel")}
      IconButton(onClick=close){Icon(Icons.Default.Close,"Close Quick Panel")}
     }
     Surface(Modifier.fillMaxWidth().padding(vertical=10.dp),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer){Box(Modifier.padding(14.dp)){NowPlaying(pool,audio,video,data.state.current,onResume={onDismiss();onResume(it)},onVideo={onAction("video")},onAudio={onAction("mind:sounds")},compact=false)}}
     var diagnostics by rememberSaveable{mutableStateOf(false)}
     TextButton(onClick={diagnostics=!diagnostics},modifier=Modifier.testTag("media-diagnostics-toggle")){Icon(Icons.Default.Info,null,Modifier.size(18.dp));Text("Media interface details",Modifier.padding(start=8.dp))}
     if(diagnostics)Text(pool.mediaSessions.capabilitySummary,Modifier.fillMaxWidth().padding(bottom=12.dp).testTag("media-capability-report"),style=MaterialTheme.typography.bodySmall)
     val controls=listOf("theme" to "Theme · ${data.state.theme}","wallpaper" to "Wallpaper","sanctuary" to "Sanctuary","mind" to "Nova Mind","search" to "Nova Search","recents" to "Recent apps","home-settings" to "Nova Settings","events" to "Nova events (${data.shell.events(data.state.current).count{!it.read}})","widgets" to "Widget Centre","calm" to "Calm Now")
     val columns=tileColumns
     Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
      controls.take(if(expanded)controls.size else 4).chunked(columns).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
       row.forEach{(id,label)->FilledTonalButton(onClick={if(id=="theme")data.setTheme(if(data.state.theme=="dark")"light"else if(data.state.theme=="light")"system"else "dark")else onAction(id)},modifier=Modifier.weight(1f).heightIn(min=72.dp).testTag("quick-$id"),shape=RoundedCornerShape(20.dp),contentPadding=PaddingValues(horizontal=14.dp,vertical=12.dp)){
        Icon(when(id){"theme"->Icons.Default.Palette;"wallpaper"->Icons.Default.Wallpaper;"sanctuary"->Icons.Default.NightsStay;"mind","calm"->Icons.Default.Spa;"search"->Icons.Default.Search;"recents"->Icons.Default.ViewWeek;"events"->Icons.Default.NotificationsNone;"widgets"->Icons.Default.Widgets;else->Icons.Default.Settings},null,Modifier.size(22.dp));Spacer(Modifier.width(10.dp));Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,maxLines=2)
       }}
       repeat(columns-row.size){Spacer(Modifier.weight(1f))}
      }}
     }
     if(expanded){Text("NovaOS display dimming",Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelLarge);Slider(data.shell.dim,{data.shell.dim(it)},valueRange=0f.. .6f,modifier=Modifier.testTag("nova-dimming"));Text("Dims this preview only · Android brightness stays unchanged",style=MaterialTheme.typography.bodySmall)}
     TextButton(onClick={expanded=!expanded},Modifier.align(Alignment.CenterHorizontally)){Text(if(expanded)"Fewer controls"else "All controls")}
     Spacer(Modifier.height(10.dp))
    }
   }
  }
 }
}
