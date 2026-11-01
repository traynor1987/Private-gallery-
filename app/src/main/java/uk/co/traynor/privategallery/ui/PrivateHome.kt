package uk.co.traynor.privategallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.R
import uk.co.traynor.privategallery.core.home.*

@OptIn(ExperimentalFoundationApi::class,ExperimentalLayoutApi::class)
@Composable internal fun PrivateHome(
 hidden:Boolean,preferences:HomePreferences,onChange:(HomePreferences)->Unit,
 initialCustomisation:Boolean=false,search:suspend (String)->List<HomeSearchResult>,
 onLaunch:(HomeLayout.Shortcut)->Unit,onLock:()->Unit,onPanic:()->Unit,onExit:()->Unit,
 onSwitchSpace:(()->Unit)?=null,
) {
 var query by remember{mutableStateOf("")}
 var menu by remember{mutableStateOf<HomeLayout.Shortcut?>(null)}
 var customise by remember{mutableStateOf(initialCustomisation)}
 var restore by remember{mutableStateOf(false)}
 var results by remember{mutableStateOf<List<HomeSearchResult>>(emptyList())}
 var searching by remember{mutableStateOf(false)}
 val space=if(hidden)"Hidden Space"else"Primary Space"
 LaunchedEffect(query){
  results=emptyList();searching=query.isNotBlank()
  if(query.isNotBlank()){kotlinx.coroutines.delay(200);results=try{search(query)}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){emptyList()};searching=false}
 }
 BackHandler{when{menu!=null->menu=null;restore->restore=false;query.isNotBlank()->query="";customise->customise=false;else->onExit()}}
 val visible=preferences.layout.search(query).sortedByDescending{it in preferences.layout.favourites()}
 val palette=MaterialTheme.colorScheme
 Box(Modifier.fillMaxSize().testTag("private-home1710").background(Brush.verticalGradient(listOf(palette.primaryContainer.copy(alpha=.40f),palette.background,palette.background)))) {
  Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().widthIn(max=1120.dp).align(Alignment.TopCenter)) {
   if(customise) {
    NovaAppHeader("Personalise Home",space,onBack={customise=false},onLock=onLock,onPanic=onPanic)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
     Text("Make this Space yours",style=MaterialTheme.typography.headlineSmall)
     Text("Your layout is saved privately in $space.",color=palette.onSurfaceVariant)
     GalleryCard {Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Compact spacing");Text("Fit apps closer together",style=MaterialTheme.typography.bodySmall,color=palette.onSurfaceVariant)};Switch(preferences.compact,{onChange(preferences.copy(compact=it))})}}
     GalleryCard {Row(verticalAlignment=Alignment.CenterVertically){Text("Larger app icons",Modifier.weight(1f));Switch(preferences.largeIcons,{onChange(preferences.copy(largeIcons=it))})}}
     val hiddenApps=HomeLayout.Shortcut.values().filter{it !in preferences.layout.visible()}
     if(hiddenApps.isNotEmpty())GalleryCard {
      Text("Hidden shortcuts",style=MaterialTheme.typography.titleMedium)
      hiddenApps.forEach {app->Row(verticalAlignment=Alignment.CenterVertically){NovaAppIcon(app,36.dp);Text(app.label,Modifier.weight(1f).padding(horizontal=12.dp));TextButton(onClick={onChange(preferences.copy(layout=preferences.layout.show(app)))}){Text("Show")}}}
     }
     TextButton(onClick={restore=true}){Icon(Icons.Default.Restore,null);Spacer(Modifier.width(8.dp));Text("Restore default layout")}
    }
   } else {
    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=8.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
     Image(painterResource(R.drawable.nova_identity),null,Modifier.size(36.dp))
     Column(Modifier.weight(1f).clickable(enabled=onSwitchSpace!=null,onClickLabel="Switch Space"){onSwitchSpace?.invoke()}.padding(start=8.dp)) {
      Text("NovaOS",style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
      Text(space,style=MaterialTheme.typography.labelMedium,color=palette.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
     }
     IconButton(onClick=onLock){Icon(Icons.Default.Lock,"Lock Space")}
     IconButton(onClick=onPanic){Icon(Icons.Default.PowerSettingsNew,"Panic Exit",tint=palette.primary)}
    }
    BoxWithConstraints(Modifier.weight(1f).padding(horizontal=16.dp)) {
     val columns=NovaNavigation.columns(maxWidth.value.toInt(),LocalDensity.current.fontScale)
     LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().testTag("nova-app-grid"),
      horizontalArrangement=Arrangement.spacedBy(0.dp),verticalArrangement=Arrangement.spacedBy(if(preferences.compact)4.dp else 12.dp),
      contentPadding=PaddingValues(top=8.dp,bottom=24.dp)) {
      item(span={GridItemSpan(maxLineSpan)}) {
       OutlinedTextField(query,{query=it.take(256)},Modifier.fillMaxWidth().padding(bottom=4.dp),
        placeholder={Text("Search this Space")},singleLine=true,shape=RoundedCornerShape(20.dp),
        leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotBlank())IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Clear search")}else IconButton(onClick={customise=true}){Icon(Icons.Default.MoreVert,"Personalise Home")}},
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=palette.surface.copy(alpha=.72f),focusedContainerColor=palette.surface))
      }
      if(query.isBlank()&&preferences.recent.any{it in preferences.layout.visible()})item(span={GridItemSpan(maxLineSpan)}) {
       Column(Modifier.padding(vertical=4.dp)) {
        Text("Recent",style=MaterialTheme.typography.labelMedium,color=palette.onSurfaceVariant,modifier=Modifier.padding(start=4.dp,bottom=4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
         preferences.recent.filter{it in preferences.layout.visible()}.take(4).forEach {app->
          AssistChip(onClick={onLaunch(app)},label={Text(app.label)},leadingIcon={Icon(novaAppSymbol(app),null,Modifier.size(16.dp))})
         }
        }
       }
      }
      if(searching)item(span={GridItemSpan(maxLineSpan)}){LinearProgressIndicator(Modifier.fillMaxWidth())}
      items(results.indices.toList(),key={"result-$it"},span={GridItemSpan(maxLineSpan)}) {index->val result=results[index]
       Surface(onClick={onLaunch(result.shortcut)},shape=RoundedCornerShape(18.dp),color=palette.surfaceContainerLow) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){NovaAppIcon(result.shortcut,36.dp);Column(Modifier.weight(1f).padding(start=12.dp)){Text(result.label,maxLines=2,overflow=TextOverflow.Ellipsis);Text(result.detail,style=MaterialTheme.typography.labelSmall,color=palette.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)};Icon(Icons.Default.ChevronRight,null,tint=palette.onSurfaceVariant)}
       }
      }
      if(query.isNotBlank()&&!searching&&results.isEmpty()&&visible.isEmpty())item(span={GridItemSpan(maxLineSpan)}) {
       Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.SearchOff,null,tint=palette.onSurfaceVariant);Text("No matches in this Space",style=MaterialTheme.typography.titleMedium);Text("Try an app, filename, collection or note.",textAlign=TextAlign.Center,color=palette.onSurfaceVariant)}
      }
      items(visible,key={it.name}) {app->
       Column(Modifier.fillMaxWidth().testTag("nova-app-${app.name}").combinedClickable(onClick={onLaunch(app)},onLongClick={menu=app}).padding(bottom=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(if(preferences.largeIcons)96.dp else 84.dp)) {
         Box(Modifier.align(Alignment.BottomCenter)) {
          NovaAppIcon(app,if(preferences.largeIcons)76.dp else 64.dp)
          if(app in preferences.layout.favourites())Surface(Modifier.align(Alignment.BottomEnd).size(18.dp),shape=RoundedCornerShape(9.dp),color=palette.surface){Icon(Icons.Default.Star,null,tint=palette.primary,modifier=Modifier.padding(3.dp))}
         }
         Box(Modifier.align(Alignment.TopEnd)) {
          IconButton(onClick={menu=app},modifier=Modifier.size(48.dp)){Icon(Icons.Default.MoreHoriz,"Actions for ${app.label}",Modifier.size(16.dp),tint=palette.onSurfaceVariant)}
          DropdownMenu(menu==app,{menu=null},modifier=Modifier.heightIn(max=360.dp)) {
           DropdownMenuItem(text={Text(if(app in preferences.layout.favourites())"Unpin"else"Pin favourite")},leadingIcon={Icon(Icons.Default.PushPin,null)},onClick={val layout=if(app in preferences.layout.favourites())preferences.layout.unpin(app)else preferences.layout.pin(app);onChange(preferences.copy(layout=layout));menu=null})
           DropdownMenuItem(text={Text("Hide shortcut")},leadingIcon={Icon(Icons.Default.VisibilityOff,null)},onClick={onChange(preferences.copy(layout=preferences.layout.hide(app)));menu=null})
           HorizontalDivider()
           visible.filter{it!=app}.forEach {target->DropdownMenuItem(text={Text("Place before ${target.label}")},onClick={onChange(preferences.copy(layout=preferences.layout.before(app,target)));menu=null})}
          }
         }
        }
        Text(app.label,Modifier.fillMaxWidth().padding(top=8.dp,start=2.dp,end=2.dp),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
       }
      }
     }
    }
   }
  }
 }
 if(restore)GalleryAlertDialog(onDismissRequest={restore=false},title={Text("Restore this Home?")},text={Text("App order, pins and hidden shortcuts return to defaults in this Space. Your apps and files stay intact.")},confirmButton={TextButton(onClick={onChange(preferences.copy(layout=HomeLayout.defaults(),recent=emptyList()));restore=false}){Text("Restore")}},dismissButton={TextButton(onClick={restore=false}){Text("Cancel")}})
}
