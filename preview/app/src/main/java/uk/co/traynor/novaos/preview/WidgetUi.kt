package uk.co.traynor.novaos.preview

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable internal fun WidgetCentre(data:PreviewData,widgets:WidgetData,page:Int,onBack:()->Unit){
 var query by rememberSaveable{mutableStateOf("")};var target by rememberSaveable{mutableIntStateOf(page)}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("widget-centre")){
  PreviewHeader("Widget Centre","Nova widgets · local to this preview",onBack)
  Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("widget-search"),label={Text("Find a widget")},singleLine=true)
   Text("Add to Home page ${target+1}");Row(Modifier.horizontalScroll(rememberScrollState())){data.profile.pages.indices.forEach{i->FilterChip(target==i,{target=i},label={Text("${i+1}")})}}
   widgetSpecs.filter{it.name.contains(query,true)||it.description.contains(query,true)}.forEach{spec->
    var size by remember(spec.type){mutableStateOf(spec.sizes.first())}
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Icon(widgetIcon(spec.type),null);Text(spec.name,Modifier.padding(start=12.dp),style=MaterialTheme.typography.titleLarge)}
     Text(spec.description,style=MaterialTheme.typography.bodyMedium)
     Surface(Modifier.fillMaxWidth().height(86.dp),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer){Box(contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(widgetIcon(spec.type),null);Text(if(spec.type=="clock")"Clock & date"else spec.name,Modifier.padding(top=6.dp));Text("Layout preview · ${size.first} × ${size.second}",style=MaterialTheme.typography.labelSmall)}}}
     Row(Modifier.horizontalScroll(rememberScrollState())){spec.sizes.forEach{s->FilterChip(size==s,{size=s},label={Text("${s.first} × ${s.second}")})}}
     Button(onClick={if(widgets.add(data.state.current,spec.type,size.first,size.second,target))onBack()},modifier=Modifier.fillMaxWidth().testTag("add-widget-${spec.type}")){Text("Add to Home")}
    }}
   }
   widgets.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   Text("Widgets occupy their own snapped grid above shortcuts. Your existing app arrangement and Dock stay intact. Scroll the widget area when more room is needed.",style=MaterialTheme.typography.bodySmall)
  }
 }
}
internal fun widgetIcon(type:String)=when(type){"clock"->Icons.Default.Schedule;"media"->Icons.Default.GraphicEq;"recents"->Icons.Default.ViewWeek;"mind"->Icons.Default.Spa;"note"->Icons.Default.EditNote;"search"->Icons.Default.Search;else->Icons.Default.Widgets}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun HomeWidgets(data:PreviewData,widgets:WidgetData,pool:PreviewBrowserModel,audio:MindAudio,video:NovaVideoModel,page:Int,maxHeight:androidx.compose.ui.unit.Dp,onLaunch:(String)->Unit,onResume:(PreviewTab)->Unit,onEdit:(String)->Unit){
 val slots=widgets.slots(data.state.current).filter{it.page==page};if(slots.isEmpty()){if(widgets.undo!=null)TextButton(onClick={widgets.undoRemove()}){Text("Undo widget removal")};return}
 val density=LocalDensity.current
 BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=maxHeight).verticalScroll(rememberScrollState()).testTag("home-widgets")){
  val cell=maxWidth/4;val cellPx=with(density){cell.toPx()};val row=156.dp*density.fontScale.coerceAtLeast(1f);val rowPx=with(density){row.toPx()}
  Box(Modifier.fillMaxWidth().height(row*(slots.maxOf{it.y+it.h}))){
   slots.forEach{slot->key(slot.id){
    var dx by remember{mutableFloatStateOf(0f)};var dy by remember{mutableFloatStateOf(0f)}
    Surface(Modifier.offset(cell*slot.x,row*slot.y).width(cell*slot.w).height(row*slot.h).padding(4.dp).graphicsLayer{translationX=dx;translationY=dy}.testTag("widget-${slot.id}"),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.94f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=2.dp){
     Box(Modifier.fillMaxSize()){
      Column(Modifier.fillMaxSize().padding(10.dp)){
       Row(verticalAlignment=Alignment.CenterVertically){
        Icon(widgetIcon(slot.items[WidgetRules.active(slot.active,slot.items.size)].type),"Hold and drag widget",Modifier.size(16.dp).pointerInput(slot.id,slot.x,slot.y,slot.w,slot.h,slot.page,cellPx,rowPx){detectDragGesturesAfterLongPress(onDragStart={dx=0f;dy=0f},onDragEnd={widgets.place(data.state.current,slot.id,(slot.x+dx/cellPx).roundToInt(),(slot.y+dy/rowPx).roundToInt(),slot.w,slot.h,slot.page);dx=0f;dy=0f},onDragCancel={dx=0f;dy=0f}){change,drag->change.consume();dx+=drag.x;dy+=drag.y}})
        Spacer(Modifier.weight(1f));IconButton(onClick={onEdit(slot.id)},modifier=Modifier.size(28.dp).testTag("edit-widget-${slot.id}")){Icon(Icons.Default.MoreHoriz,"Widget options",Modifier.size(18.dp))}
       }
       val pager=rememberPagerState(initialPage=slot.active,pageCount={slot.items.size})
       LaunchedEffect(slot.active){if(pager.currentPage!=slot.active)pager.scrollToPage(slot.active)}
       LaunchedEffect(pager.currentPage){if(pager.currentPage!=slot.active)widgets.active(data.state.current,slot.id,pager.currentPage)}
       HorizontalPager(pager,Modifier.weight(1f).fillMaxWidth().testTag("widget-stack-${slot.id}"),userScrollEnabled=slot.items.size>1,key={slot.items[it].id}){index->WidgetBody(slot.items[index],slot,data,widgets,pool,audio,video,onLaunch,onResume)}
       if(slot.items.size>1)Row(Modifier.align(Alignment.CenterHorizontally),horizontalArrangement=Arrangement.spacedBy(4.dp)){repeat(slot.items.size){i->Box(Modifier.size(5.dp).background(if(i==pager.currentPage)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,RoundedCornerShape(3.dp)))}}
      }
      var rw by remember{mutableFloatStateOf(0f)};var rh by remember{mutableFloatStateOf(0f)}
      Icon(Icons.Default.OpenInFull,"Drag to resize widget",Modifier.align(Alignment.BottomEnd).padding(3.dp).size(22.dp).pointerInput(slot.id,slot.x,slot.y,slot.w,slot.h,slot.page,cellPx,rowPx){detectDragGestures(onDragStart={rw=0f;rh=0f},onDragEnd={widgets.place(data.state.current,slot.id,slot.x,slot.y,(slot.w+rw/cellPx).roundToInt().coerceIn(1,4),(slot.h+rh/rowPx).roundToInt().coerceIn(1,4),slot.page)}){change,drag->change.consume();rw+=drag.x;rh+=drag.y}},tint=MaterialTheme.colorScheme.outline)
     }
    }
   }}
  }
 }
 widgets.undo?.let{Row(verticalAlignment=Alignment.CenterVertically){Text("Widget removed",Modifier.weight(1f),color=Color.White);TextButton(onClick={widgets.undoRemove()}){Text("Undo")}}}
}

@Composable private fun WidgetBody(item:WidgetItem,slot:WidgetSlot,data:PreviewData,widgets:WidgetData,pool:PreviewBrowserModel,audio:MindAudio,video:NovaVideoModel,onLaunch:(String)->Unit,onResume:(PreviewTab)->Unit){
 when(item.type){
  "clock"->{var now by remember{mutableStateOf(LocalDateTime.now())};LaunchedEffect(Unit){while(true){now=LocalDateTime.now();delay(1000)}};Column{Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=if(slot.w>=4)36.sp else 27.sp);Text(now.format(DateTimeFormatter.ofPattern("EEE, d MMM")),style=MaterialTheme.typography.labelSmall)}}
  "media"->NowPlaying(pool,audio,video,data.state.current,onResume,{onLaunch("video")},{onLaunch("mind:sounds")},compact=slot.w<4||slot.h==1)
  "recents"->{Column(Modifier.verticalScroll(rememberScrollState())){Text("Recent apps",style=MaterialTheme.typography.labelMedium);val recent=pool.recents(data.state.current).take(if(slot.h==1)1 else 3);if(recent.isEmpty())Text("Open an app to begin",style=MaterialTheme.typography.bodySmall);data.shell.internalRecents.filter{it.first==data.state.current}.take(2).forEach{(_,id)->data.app(id)?.let{app->TextButton(onClick={onLaunch("resume-internal:"+id)},contentPadding=PaddingValues(0.dp)){Text(app.name,maxLines=1,overflow=TextOverflow.Ellipsis)}}};recent.forEach{tab->TextButton(onClick={onResume(tab)},contentPadding=PaddingValues(0.dp)){Text(tab.app.name,maxLines=1,overflow=TextOverflow.Ellipsis)}}}}
  "mind"->{Column{Text("Your space. Your pace.",style=MaterialTheme.typography.labelSmall);TextButton(onClick={onLaunch("calm")},contentPadding=PaddingValues(0.dp)){Icon(Icons.Default.Spa,null,Modifier.size(18.dp));Text("Calm Now",Modifier.padding(start=6.dp))}}}
  "search"->TextButton(onClick={onLaunch("search")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Search,null);Text("Nova Search",maxLines=1)}
  "note"->{var text by remember(item.id,item.config){mutableStateOf(item.config)};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){OutlinedTextField(text,{text=it.take(2000)},Modifier.fillMaxWidth(),placeholder={Text("Quick note")},textStyle=MaterialTheme.typography.bodySmall,minLines=1,maxLines=if(slot.h>=4)8 else 2);TextButton(onClick={widgets.note(data.state.current,slot.id,item.id,text)},contentPadding=PaddingValues(0.dp)){Text("Save locally")}}}
  else->Column{Text("Widget unavailable",style=MaterialTheme.typography.labelLarge);Text("Its position is preserved.",style=MaterialTheme.typography.bodySmall)}
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WidgetOptions(data:PreviewData,widgets:WidgetData,id:String,onDismiss:()->Unit){
 val slot=widgets.slots(data.state.current).firstOrNull{it.id==id}?:run{onDismiss();return}
 ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(24.dp).testTag("widget-options")){
  Text(if(slot.items.size>1)"Widget stack"else "Widget options",style=MaterialTheme.typography.headlineSmall)
  Text("Hold and drag the widget’s top-left icon to move. Drag its corner to resize.",Modifier.padding(vertical=12.dp))
  Row{listOf(-1 to 0,0 to -1,0 to 1,1 to 0).forEach{(x,y)->IconButton(onClick={widgets.place(data.state.current,id,slot.x+x,slot.y+y,slot.w,slot.h,slot.page)}){Icon(when{ x<0->Icons.Default.ArrowBack;x>0->Icons.Default.ArrowForward;y<0->Icons.Default.ArrowUpward;else->Icons.Default.ArrowDownward},"Move widget")}}}
  Text("Home page");Row(Modifier.horizontalScroll(rememberScrollState())){data.profile.pages.indices.forEach{page->FilterChip(slot.page==page,{val r=WidgetRules.firstFree(slot.w,slot.h,widgets.slots(data.state.current).filter{it.id!=id&&it.page==page}.map{it.rect()});if(r!=null)widgets.place(data.state.current,id,r[0],r[1],slot.w,slot.h,page)},label={Text("${page+1}")})}}
  Text("Size");Row(Modifier.horizontalScroll(rememberScrollState())){listOf(2 to 1,2 to 2,4 to 1,4 to 2,4 to 4).filter{size->slot.items.all{item->widgetSpecs.firstOrNull{it.type==item.type}?.sizes?.contains(size)!=false}}.forEach{(w,h)->FilterChip(slot.w==w&&slot.h==h,{widgets.place(data.state.current,id,slot.x,slot.y,w,h,slot.page)},label={Text("$w × $h")})}}
  if(slot.items.size>1){Text("Stack order",Modifier.padding(top=16.dp));slot.items.forEachIndexed{i,item->Row(verticalAlignment=Alignment.CenterVertically){Text(widgetSpecs.firstOrNull{it.type==item.type}?.name?:"Unavailable",Modifier.weight(1f));IconButton(onClick={widgets.reorder(data.state.current,id,i,-1)}){Icon(Icons.Default.ArrowUpward,"Move earlier")};IconButton(onClick={widgets.reorder(data.state.current,id,i,1)}){Icon(Icons.Default.ArrowDownward,"Move later")};IconButton(onClick={widgets.removeItem(data.state.current,id,i)}){Icon(Icons.Default.Close,"Remove from stack")}}}}
  val others=widgets.slots(data.state.current).filter{it.id!=id&&it.w==slot.w&&it.h==slot.h&&it.items.size+slot.items.size<=8}
  if(others.isNotEmpty()){Text("Add compatible widget to this stack",Modifier.padding(top=16.dp));others.forEach{other->TextButton(onClick={widgets.stack(data.state.current,id,other.id)}){Text(other.items.joinToString(" + "){item->widgetSpecs.firstOrNull{it.type==item.type}?.name?:"Unavailable"})}}}
  widgets.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  TextButton(onClick={widgets.remove(data.state.current,id);onDismiss()},Modifier.fillMaxWidth()){Text(if(slot.items.size>1)"Delete stack"else "Remove widget")}
  Button(onClick=onDismiss,Modifier.fillMaxWidth()){Text("Done")}
 }}
}
