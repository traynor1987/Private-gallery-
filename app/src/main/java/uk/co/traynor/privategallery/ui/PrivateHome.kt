package uk.co.traynor.privategallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import uk.co.traynor.privategallery.core.home.*

@OptIn(ExperimentalFoundationApi::class,ExperimentalLayoutApi::class)
@Composable internal fun PrivateHome(
 hidden:Boolean,preferences:HomePreferences,onChange:(HomePreferences)->Unit,
 initialCustomisation:Boolean=false,
 search:suspend (String)->List<HomeSearchResult>,
 onLaunch:(HomeLayout.Shortcut)->Unit,onLock:()->Unit,onPanic:()->Unit,onExit:()->Unit,
) {
 var query by remember{mutableStateOf("")}
 var menu by remember{mutableStateOf<HomeLayout.Shortcut?>(null)}
 var customise by remember{mutableStateOf(initialCustomisation)}
 var results by remember{mutableStateOf<List<HomeSearchResult>>(emptyList())}
 var searching by remember{mutableStateOf(false)}
 LaunchedEffect(query){results=emptyList();if(query.isNotBlank()){searching=true;kotlinx.coroutines.delay(200);results=runCatching{search(query)}.getOrDefault(emptyList());searching=false}}

 BackHandler{when{menu!=null->menu=null;query.isNotBlank()->query="";customise->customise=false;else->onExit()}}
 val visible=preferences.layout.search(query).sortedByDescending{it in preferences.layout.favourites()}
 Surface(Modifier.fillMaxSize().testTag("private-home1710"),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){
   Text("Private Home",style=MaterialTheme.typography.headlineMedium)
   Text(if(hidden)"Hidden Vault" else "Primary Vault",style=MaterialTheme.typography.labelLarge)
  };IconButton(onClick={customise=!customise}){Icon(Icons.Default.MoreVert,"Customise Home")}
   IconButton(onClick=onLock){Icon(Icons.Default.Lock,"Lock vault")}
   IconButton(onClick=onPanic){Icon(Icons.Default.Close,"Panic Exit")}}
  OutlinedTextField(query,{query=it.take(256)},Modifier.fillMaxWidth(),label={Text("Search this vault")},singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)})
  if(query.isBlank()&&preferences.recent.isNotEmpty()){
   Text("Recently opened",style=MaterialTheme.typography.labelLarge)
   FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){preferences.recent.filter{it in preferences.layout.visible()}.take(4).forEach{item->AssistChip(onClick={onLaunch(item)},label={Text(item.label)})}}
  }
  if(customise)Column(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())){
   Text("Appearance & Navigation",style=MaterialTheme.typography.titleMedium)
   FlowRow{FilterChip(preferences.startupHome,{onChange(preferences.copy(startupHome=true))},{Text("Start: Private Home")});Spacer(Modifier.width(8.dp));FilterChip(!preferences.startupHome,{onChange(preferences.copy(startupHome=false))},{Text("Gallery")})}
   FlowRow{FilterChip(preferences.compact,{onChange(preferences.copy(compact=!preferences.compact))},{Text("Compact")});Spacer(Modifier.width(8.dp));FilterChip(preferences.largeIcons,{onChange(preferences.copy(largeIcons=!preferences.largeIcons))},{Text("Large icons")})}
   TextButton(onClick={onChange(preferences.copy(layout=HomeLayout.defaults(),recent=emptyList()))}){Text("Restore default layout")}
   HomeLayout.Shortcut.values().filter{it !in preferences.layout.visible()}.forEach{item->TextButton(onClick={onChange(preferences.copy(layout=preferences.layout.show(item)))}){Text("Show ${item.label}")}}
  }
  LazyVerticalGrid(GridCells.Adaptive(if(preferences.compact)88.dp else 108.dp),Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=24.dp)){
   if(searching)item(span={GridItemSpan(maxLineSpan)}){LinearProgressIndicator(Modifier.fillMaxWidth())}
   items(results.indices.toList(),key={"result-$it"},span={GridItemSpan(maxLineSpan)}){index->val result=results[index]
    Surface(onClick={onLaunch(result.shortcut)},shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.padding(12.dp)){Text(result.label);Text(result.detail,style=MaterialTheme.typography.labelSmall)}}
   }
   items(visible,key={it.name}){item->Column(horizontalAlignment=Alignment.CenterHorizontally){
    Box {Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.secondaryContainer,
     modifier=Modifier.size(if(preferences.largeIcons)76.dp else 64.dp).combinedClickable(onClick={onLaunch(item)},onLongClick={menu=item})){
      Box(contentAlignment=Alignment.Center){Icon(when(item){
       HomeLayout.Shortcut.GALLERY->Icons.Default.PhotoLibrary;HomeLayout.Shortcut.BROWSER->Icons.Default.Language
       HomeLayout.Shortcut.PRIVATE_APPS->Icons.Default.Apps;HomeLayout.Shortcut.EVIDENCE_LOCKER->Icons.Default.Folder
       HomeLayout.Shortcut.CAMERA->Icons.Default.CameraAlt;HomeLayout.Shortcut.RECENTLY_DELETED->Icons.Default.Delete
       HomeLayout.Shortcut.COLLECTIONS->Icons.Default.Collections;HomeLayout.Shortcut.SETTINGS->Icons.Default.Settings
      },item.label,Modifier.size(32.dp))}}
     DropdownMenu(menu==item,{menu=null}){
      DropdownMenuItem(text={Text(if(item in preferences.layout.favourites())"Unpin" else "Pin favourite")},onClick={val layout=if(item in preferences.layout.favourites())preferences.layout.unpin(item)else preferences.layout.pin(item);onChange(preferences.copy(layout=layout));menu=null})
      DropdownMenuItem(text={Text("Hide shortcut")},onClick={onChange(preferences.copy(layout=preferences.layout.hide(item)));menu=null})
      visible.forEachIndexed{index,target->DropdownMenuItem(text={Text("Place before ${target.label}")},onClick={onChange(preferences.copy(layout=preferences.layout.before(item,target)));menu=null})}
     }}
    Spacer(Modifier.height(8.dp));Text(item.label,textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge)
   }}
  }
 }}
}
