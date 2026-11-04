package uk.co.traynor.novaos.preview

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Aqua=Color(0xFF9AE9DD)
private val Violet=Color(0xFFC3B7FF)
private val Shape=RoundedCornerShape(28.dp)
private val darkColors=darkColorScheme(primary=Aqua,secondary=Violet,background=Color(0xFF0D1020),surface=Color(0xFF181C30),surfaceVariant=Color(0xFF23283F))
private val lightColors=lightColorScheme(primary=Color(0xFF285F65),secondary=Color(0xFF655295),background=Color(0xFFF2F4FA),surface=Color.White,surfaceVariant=Color(0xFFE5E9F3))

/** Adapted from recovered HomeDesktopScreen: pager, dock, gesture alternatives,
 * clock, procedural wallpapers and bounded adaptive grids. Preview data only. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable fun NovaPreview(data:PreviewData,pool:PreviewBrowserModel,onPickImage:()->Unit,onOpen:(String)->Unit,filesDir:File){
 val state=data.state
 val dark=when(state.theme){"light"->false;"system"->isSystemInDarkTheme();else->true}
 MaterialTheme(colorScheme=if(dark)darkColors else lightColors){
  key(state.current){
   var screen by rememberSaveable{mutableStateOf("home")}
   var drawer by rememberSaveable{mutableStateOf(false)}
   var quick by rememberSaveable{mutableStateOf(false)}
   var editing by rememberSaveable{mutableStateOf(false)}
   var selected by rememberSaveable{mutableStateOf<String?>(null)}
   var openFolder by rememberSaveable{mutableStateOf<String?>(null)}
   var detailId by rememberSaveable{mutableStateOf<String?>(null)}
   var custom by rememberSaveable{mutableStateOf(false)}
   var browser by remember{mutableStateOf<StoreEntry?>(null)}
   var folderCreate by rememberSaveable{mutableStateOf(false)}
   var rename by rememberSaveable{mutableStateOf<String?>(null)}
   var addToFolder by rememberSaveable{mutableStateOf<String?>(null)}
   var confirmUninstall by rememberSaveable{mutableStateOf<String?>(null)}
   var reset by rememberSaveable{mutableStateOf(false)}
   val profile=data.profile
   val pager=rememberPagerState(pageCount={data.profile.pages.size})
   val scope=rememberCoroutineScope()
   val currentPage=pager.currentPage.coerceIn(0,profile.pages.lastIndex)
   LaunchedEffect(profile.pages.size){if(pager.currentPage>profile.pages.lastIndex)pager.scrollToPage(profile.pages.lastIndex)}
   val launch:(String)->Unit={id->
    when(id){
     "store"->{screen="store";drawer=false}
     "settings"->{editing=true;drawer=false}
     "wallpaper"->{screen="wallpaper";drawer=false}
     "photos"->{screen="photos";drawer=false}
     "guide"->{screen="guide";drawer=false}
     else->if(id.startsWith("folder:"))openFolder=id else data.app(id)?.website?.let{pool.open(it,data.state.current);screen="browser";drawer=false}
    }
   }
   BackHandler(screen!="home"||drawer||quick||editing||selected!=null||openFolder!=null||detailId!=null){
    when {selected!=null->selected=null;openFolder!=null->openFolder=null;detailId!=null->detailId=null;editing->editing=false;quick->quick=false;drawer->drawer=false;else->screen="home"}
   }
   Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
    AnimatedContent(screen,label="preview-screen"){route->
     when(route){
      "browser"->PreviewBrowser(pool,onHome={screen="home"},onExternal={url->pool.active?.app?.let{browser=it.copy(url=url)}})
      "store"->StoreScreen(data,onBack={screen="home"},onDetail={detailId=it.id},onCustom={custom=true})
      "wallpaper"->WallpaperScreen(data,filesDir,onPickImage,onBack={screen="home"})
      "photos"->PhotosDemo(onBack={screen="home"},onWallpaper={screen="wallpaper"})
      "guide"->GuideScreen(onBack={screen="home"})
      else->Home(data,filesDir,pager,onLaunch=launch,onSelect={selected=it},onDrawer={drawer=true},onQuick={quick=true},onEdit={editing=true})
     }
    }
   }
   if(drawer)ModalBottomSheet(onDismissRequest={drawer=false},modifier=Modifier.testTag("drawer-sheet")){
    var search by rememberSaveable{mutableStateOf("")}
    Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal=20.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Text("Your apps",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);IconButton(onClick={drawer=false}){Icon(Icons.Default.Close,"Close app drawer")}}
     Text(data.profileLabel+" · demo profile",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
     OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(vertical=16.dp).testTag("drawer-search"),placeholder={Text("Search apps and folders")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=Shape)
     AdaptiveAppGrid(data,data.drawerIds().filter{id->val f=profile.folders.firstOrNull{it.id==id};(f?.name?:data.app(id)?.name?:"").contains(search,true)},onLaunch=launch,onSelect={selected=it},modifier=Modifier.weight(1f))
     TextButton(onClick={folderCreate=true}){Icon(Icons.Default.CreateNewFolder,null);Text("Create folder",Modifier.padding(start=8.dp))}
    }
   }
   if(quick)ModalBottomSheet(onDismissRequest={quick=false}){
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)){
     Text("Nova Quick Panel",style=MaterialTheme.typography.headlineMedium);Text("Controls for this preview only",color=MaterialTheme.colorScheme.onSurfaceVariant)
     Spacer(Modifier.height(18.dp))
     ActionRow("Theme · ${state.theme}",Icons.Default.Palette){data.setTheme(if(state.theme=="dark")"light"else if(state.theme=="light")"system"else "dark")}
     ActionRow("Wallpaper",Icons.Default.Wallpaper){quick=false;screen="wallpaper"}
     ActionRow("Grid and icon size",Icons.Default.GridView){quick=false;editing=true}
     ActionRow("Home settings",Icons.Default.Settings){quick=false;editing=true}
     ActionRow("Switch preview profile",Icons.Default.SwapHoriz){data.switchProfile()}
     Notice("${data.profileLabel}. Demo layout and shortcuts only. No vault, VPN or secure storage.")
    }
   }
   if(editing)ModalBottomSheet(onDismissRequest={editing=false}){
    Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).verticalScroll(rememberScrollState()).padding(24.dp)){
     Text("Make it yours",style=MaterialTheme.typography.headlineLarge);Text("${data.profileLabel} · Home ${currentPage+1}",color=MaterialTheme.colorScheme.primary)
     ActionRow("Choose wallpaper",Icons.Default.Wallpaper){editing=false;screen="wallpaper"}
     Text("Grid density · ${profile.columns}");Slider(profile.columns.toFloat(),{v->data.change{it.copy(columns=v.toInt())}},valueRange=3f..6f,steps=2,modifier=Modifier.testTag("grid-density"))
     Text("Icon size · ${profile.iconSize} dp");Slider(profile.iconSize.toFloat(),{v->data.change{it.copy(iconSize=v.toInt())}},valueRange=44f..76f)
     Text("Layouts adapt to screen width and text size.",style=MaterialTheme.typography.bodySmall)
     ActionRow("Add Home page (${profile.pages.size}/8)",Icons.Default.Add,enabled=profile.pages.size<8){data.addPage();scope.launch{pager.animateScrollToPage(data.profile.pages.lastIndex)}}
     if(profile.pages.size>1&&profile.pages[currentPage].isEmpty())ActionRow("Remove empty page",Icons.Default.Remove){data.removePage(currentPage)}
     if(currentPage>0)ActionRow("Move page left",Icons.Default.ChevronLeft){data.movePage(currentPage,-1);scope.launch{pager.scrollToPage(currentPage-1)}}
     if(currentPage<profile.pages.lastIndex)ActionRow("Move page right",Icons.Default.ChevronRight){data.movePage(currentPage,1);scope.launch{pager.scrollToPage(currentPage+1)}}
     ActionRow("Add apps from drawer",Icons.Default.Apps){editing=false;drawer=true}
     ActionRow("Create folder",Icons.Default.CreateNewFolder){folderCreate=true}
     ActionRow("Theme · ${state.theme}",Icons.Default.Palette){data.setTheme(if(state.theme=="dark")"light"else "dark")}
     ActionRow("Switch preview profile",Icons.Default.SwapHoriz){data.switchProfile()}
     ActionRow("Restore Home defaults",Icons.Default.RestartAlt){reset=true}
    }
   }
   val chosenId=selected
   if(chosenId!=null){
    val folder=profile.folders.firstOrNull{it.id==chosenId};val app=data.app(chosenId)
    ModalBottomSheet(onDismissRequest={selected=null}){
     Column(Modifier.fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState()).padding(24.dp)){
      Text(folder?.name?:app?.name?:"Shortcut",style=MaterialTheme.typography.headlineMedium)
      if(folder!=null)ActionRow("Open folder",Icons.Default.FolderOpen){selected=null;openFolder=chosenId}
      else ActionRow(if(app?.website!=null)"Open app"else "Open",Icons.Default.OpenInNew){selected=null;launch(chosenId)}
      ActionRow("Add to Home ${currentPage+1}",Icons.Default.AddToHomeScreen){data.addHome(chosenId,currentPage);selected=null;drawer=false}
      ActionRow("Remove from Home",Icons.Default.RemoveCircleOutline){data.removeHome(chosenId);selected=null}
      if(folder==null){
       ActionRow(if(chosenId in profile.dock)"Unpin from Dock"else "Pin to Dock",Icons.Default.PushPin){if(chosenId in profile.dock)data.unpin(chosenId)else data.pin(chosenId);selected=null}
       if(chosenId in profile.dock){ActionRow("Move left in Dock",Icons.Default.ChevronLeft){data.moveDock(chosenId,-1)};ActionRow("Move right in Dock",Icons.Default.ChevronRight){data.moveDock(chosenId,1)}}
       ActionRow("Add to folder",Icons.Default.Folder){addToFolder=chosenId;selected=null}
       profile.folders.firstOrNull{chosenId in it.apps}?.let{group->
        ActionRow("Remove from ${group.name}",Icons.Default.FolderOff){data.removeFolderMember(group.id,chosenId);selected=null}
        ActionRow("Move earlier in folder",Icons.Default.ArrowUpward){data.moveFolderMember(group.id,chosenId,-1)}
        ActionRow("Move later in folder",Icons.Default.ArrowDownward){data.moveFolderMember(group.id,chosenId,1)}
       }
      }
      ActionRow("Move earlier in drawer",Icons.Default.ArrowUpward){data.moveDrawer(chosenId,-1)}
      ActionRow("Move later in drawer",Icons.Default.ArrowDownward){data.moveDrawer(chosenId,1)}
      if(chosenId in profile.pages[currentPage]){ActionRow("Move earlier on Home",Icons.Default.ChevronLeft){data.moveHome(chosenId,currentPage,-1)};ActionRow("Move later on Home",Icons.Default.ChevronRight){data.moveHome(chosenId,currentPage,1)}}
      if(folder!=null||app?.website!=null)ActionRow("Rename shortcut",Icons.Default.Edit){rename=chosenId;selected=null}
      if(folder!=null)ActionRow("Dissolve folder",Icons.Default.FolderOff){data.dissolveFolder(chosenId);selected=null}
      if(app?.website!=null){ActionRow("Website information",Icons.Default.Info){detailId=app.website.id;selected=null};ActionRow("Uninstall shortcut",Icons.Default.DeleteOutline){confirmUninstall=chosenId;selected=null}}
     }
    }
   }
   val activeFolder=profile.folders.firstOrNull{it.id==openFolder}
   if(activeFolder!=null)ModalBottomSheet(onDismissRequest={openFolder=null}){
    Column(Modifier.fillMaxWidth().heightIn(max=650.dp).padding(20.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Text(activeFolder.name,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);IconButton(onClick={rename=activeFolder.id}){Icon(Icons.Default.Edit,"Rename folder")};IconButton(onClick={openFolder=null}){Icon(Icons.Default.Close,"Close folder")}}
     AdaptiveAppGrid(data,activeFolder.apps,onLaunch={openFolder=null;launch(it)},onSelect={openFolder=null;selected=it},modifier=Modifier.heightIn(min=170.dp,max=420.dp))
     Text("Long-press an app for placement and order actions.",style=MaterialTheme.typography.bodySmall)
     TextButton(onClick={data.dissolveFolder(activeFolder.id);openFolder=null}){Text("Dissolve folder")}
    }
   }
   if(folderCreate)CreateFolderDialog(data,currentPage,onDismiss={folderCreate=false})
   if(addToFolder!=null)AlertDialog(onDismissRequest={addToFolder=null},title={Text("Choose folder")},text={Column{profile.folders.forEach{f->TextButton(onClick={data.folderAdd(f.id,addToFolder!!);addToFolder=null}){Text(f.name)}};TextButton(onClick={addToFolder=null;folderCreate=true}){Text("Create a new folder")}}},confirmButton={TextButton(onClick={addToFolder=null}){Text("Cancel")}})
   if(rename!=null){var text by remember(rename){mutableStateOf(profile.folders.firstOrNull{it.id==rename}?.name?:data.app(rename!!)?.name?:"")};AlertDialog(onDismissRequest={rename=null},title={Text("Rename")},text={OutlinedTextField(text,{text=it.take(40)},label={Text("Name")})},confirmButton={TextButton(onClick={data.rename(rename!!,text);rename=null},enabled=text.isNotBlank()){Text("Save")}},dismissButton={TextButton(onClick={rename=null}){Text("Cancel")}})}
   if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("Restore this Home?")},text={Text("Reset this preview profile’s layout, wallpaper, folders and Dock. Installed shortcuts remain in the drawer.")},confirmButton={TextButton(onClick={data.reset();reset=false}){Text("Restore")}},dismissButton={TextButton(onClick={reset=false}){Text("Cancel")}})
   val detail=detailId?.let{id->data.catalogue.firstOrNull{it.id==id}?:profile.installed.firstOrNull{it.id==id}}
   if(detail!=null)AppDetails(data,detail,onDismiss={detailId=null},onOpen={pool.open(it,data.state.current);detailId=null;screen="browser"},onUninstall={confirmUninstall=it},onAddHome={data.addHome(it,currentPage);detailId=null;screen="home"},onPin={data.pin(it)})
   if(custom)CustomWebsiteDialog(onDismiss={custom=false},onInstall={entry->data.install(entry);custom=false;detailId=data.entryKey(entry)})
   if(confirmUninstall!=null)AlertDialog(onDismissRequest={confirmUninstall=null},title={Text("Uninstall this shortcut?")},text={Text("Remove it from this preview profile, its folders, Home and Dock. Preview browser cookies are shared and are not selectively removed. Website accounts and external browser data remain managed by their provider/browser.")},confirmButton={TextButton(onClick={pool.closeApp(data.state.current,confirmUninstall!!);data.uninstall(confirmUninstall!!);confirmUninstall=null;detailId=null}){Text("Uninstall")}},dismissButton={TextButton(onClick={confirmUninstall=null}){Text("Cancel")}})
   browser?.let{entry->AlertDialog(onDismissRequest={browser=null},title={Text("Open in your browser?")},text={Column{Text(entry.name,style=MaterialTheme.typography.titleLarge);Text(entry.url,Modifier.padding(vertical=12.dp));Text("This leaves NovaOS Preview. Your browser handles its own accounts, cookies and permissions. The external browser has its own sessions. Signing in there does not automatically sign in the preview container. Neither preview profile is encrypted or protected.")}},confirmButton={TextButton(onClick={browser=null;onOpen(entry.url)}){Text("Open browser")}},dismissButton={TextButton(onClick={browser=null}){Text("Cancel")}})}
   data.error?.let{message->AlertDialog(onDismissRequest={},title={Text("Unable to save")},text={Text(message)},confirmButton={TextButton(onClick={data.retrySave()}){Text("Retry")}})}
  }
 }
}

@Composable private fun Notice(text:String){Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.55f)){Text(text,Modifier.padding(14.dp),style=MaterialTheme.typography.bodySmall)}}
@Composable private fun ActionRow(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,enabled:Boolean=true,onClick:()->Unit){TextButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),contentPadding=PaddingValues(vertical=12.dp,horizontal=4.dp)){Icon(icon,null,Modifier.size(22.dp));Text(label,Modifier.weight(1f).padding(start=14.dp),textAlign=TextAlign.Start);Icon(Icons.Default.ChevronRight,null)}}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Home(data:PreviewData,filesDir:File,pager:PagerState,onLaunch:(String)->Unit,onSelect:(String)->Unit,onDrawer:()->Unit,onQuick:()->Unit,onEdit:()->Unit){
 val profile=data.profile;val scope=rememberCoroutineScope()
 BoxWithConstraints(Modifier.fillMaxSize().testTag("preview-home")){
  val compactHeight=maxHeight<500.dp
  WallpaperSurface(profile.wallpaper,filesDir,Modifier.fillMaxSize())
  CompositionLocalProvider(LocalContentColor provides Color.White){
   Column(Modifier.fillMaxSize().safeDrawingPadding()){
    Row(Modifier.fillMaxWidth().padding(start=22.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){
     Image(painterResource(R.drawable.nova_identity),null,Modifier.size(28.dp));Column(Modifier.weight(1f).padding(start=9.dp)){Text("NOVAOS PREVIEW",fontSize=10.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold);Text(data.profileLabel+" · demo",style=MaterialTheme.typography.labelMedium,color=Color.White.copy(alpha=.7f))}
     IconButton(onClick=onQuick){Icon(Icons.Default.Tune,"Open Quick Panel")}
     Box{var menu by remember{mutableStateOf(false)};IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Home menu")};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Apps")},onClick={menu=false;onDrawer()});DropdownMenuItem(text={Text("Edit Home")},onClick={menu=false;onEdit()});DropdownMenuItem(text={Text("Switch preview profile")},onClick={menu=false;data.switchProfile()})}}
    }
    HorizontalPager(pager,Modifier.weight(1f).fillMaxWidth(),key={it}){page->
     Column(Modifier.fillMaxSize().combinedClickable(onClick={},onLongClick=onEdit).pointerInput(Unit){var travel=0f;detectVerticalDragGestures(onDragStart={travel=0f},onDragEnd={if(travel < -70.dp.toPx())onDrawer();if(travel > 70.dp.toPx())onQuick()},onVerticalDrag={change,amount->change.consume();travel+=amount})}.padding(horizontal=20.dp)){
      if(page==0)HomeClock(Modifier.padding(top=if(compactHeight)4.dp else 26.dp,bottom=if(compactHeight)8.dp else 28.dp),compactHeight)
      if(profile.pages[page].isEmpty())Box(Modifier.weight(1f).fillMaxWidth().combinedClickable(onClick={},onLongClick=onEdit),contentAlignment=Alignment.Center){Text("Room for your next idea\nLong-press to edit Home",textAlign=TextAlign.Center,color=Color.White.copy(alpha=.65f))}
      else AdaptiveAppGrid(data,profile.pages[page],onLaunch,onSelect,Modifier.weight(1f),onHome=true)
     }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center){repeat(profile.pages.size){page->IconButton(onClick={scope.launch{pager.animateScrollToPage(page)}},modifier=Modifier.size(if(compactHeight)32.dp else 40.dp)){Icon(if(page==pager.currentPage)Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,"Home page ${page+1}",Modifier.size(7.dp))}}}
    Surface(Modifier.padding(horizontal=18.dp).fillMaxWidth(),shape=RoundedCornerShape(30.dp),color=Color.White.copy(alpha=.12f),contentColor=Color.White){
     Row(Modifier.padding(horizontal=7.dp,vertical=if(compactHeight)6.dp else 12.dp),horizontalArrangement=Arrangement.SpaceEvenly){profile.dock.forEach{id->Box(Modifier.weight(1f)){AppTile(data,id,onClick={onLaunch(id)},onLongClick={onSelect(id)},showMenu=false,compact=true)}}}
    }
    TextButton(onClick=onDrawer,modifier=Modifier.align(Alignment.CenterHorizontally).height(36.dp).testTag("open-apps")){Icon(Icons.Default.KeyboardArrowUp,null,Modifier.size(18.dp),tint=Color.White);Text("Apps",Modifier.padding(start=6.dp),color=Color.White,style=MaterialTheme.typography.labelLarge)}
   }
  }
 }
}

@Composable private fun HomeClock(modifier:Modifier=Modifier,compact:Boolean=false){
 var now by remember{mutableStateOf(ZonedDateTime.now())};LaunchedEffect(Unit){while(true){now=ZonedDateTime.now();delay(1000)}}
 Column(modifier){Text(now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),fontSize=if(compact)34.sp else 60.sp,fontWeight=FontWeight.Light,maxLines=1,overflow=TextOverflow.Ellipsis);Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),style=MaterialTheme.typography.bodyLarge,color=Color.White.copy(alpha=.8f))}
}

@Composable internal fun WallpaperSurface(w:Wallpaper,filesDir:File,modifier:Modifier=Modifier){
 val colors=when(w.builtin){"Midnight"->listOf(Color(0xFF080C21),Color(0xFF29315C),Color(0xFF341E55),Color(0xFF080D1A));"Dusk"->listOf(Color(0xFF3B2253),Color(0xFF7E4C76),Color(0xFFB16D6C),Color(0xFF26304B));else->listOf(Color(0xFF081A36),Color(0xFF324C92),Color(0xFF147F83),Color(0xFF091B2A))}
 val image=remember(w.image,File(filesDir,w.image?:"none").lastModified()){w.image?.let{BitmapFactory.decodeFile(File(filesDir,it).path)?.asImageBitmap()}}
 BoxWithConstraints(modifier.clipToBounds()){
  val density=LocalDensity.current
  val dx=with(density){maxWidth.toPx()}*(w.zoom-1f)*w.x/2;val dy=with(density){maxHeight.toPx()}*(w.zoom-1f)*w.y/2
  Box(Modifier.fillMaxSize().blur(w.blur.dp).background(Brush.linearGradient(colors)))
  if(image!=null)Image(image,null,Modifier.fillMaxSize().blur(w.blur.dp).graphicsLayer{scaleX=w.zoom;scaleY=w.zoom;translationX=dx;translationY=dy},contentScale=ContentScale.Crop,alignment=BiasAlignment(w.x,w.y))
  else Canvas(Modifier.fillMaxSize().blur(22.dp)){drawCircle(Color.White.copy(alpha=.055f),radius=size.width*.65f,center=androidx.compose.ui.geometry.Offset(size.width*1.02f,size.height*.3f));drawCircle(Color(0xFF83E7DA).copy(alpha=.08f),radius=size.width*.48f,center=androidx.compose.ui.geometry.Offset(0f,size.height*.8f))}
  Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=w.dim)))
 }
}

@Composable private fun AdaptiveAppGrid(data:PreviewData,ids:List<String>,onLaunch:(String)->Unit,onSelect:(String)->Unit,modifier:Modifier=Modifier,onHome:Boolean=false){
 BoxWithConstraints(modifier.fillMaxWidth()){
  val columns=PreviewRules.columns(maxWidth.value.toInt(),LocalDensity.current.fontScale,data.profile.columns)
  LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)){
   items(ids,key={it}){id->AppTile(data,id,onClick={onLaunch(id)},onLongClick={onSelect(id)},showMenu=true,onHome=onHome)}
  }
 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun AppTile(data:PreviewData,id:String,onClick:()->Unit,onLongClick:()->Unit,showMenu:Boolean=true,compact:Boolean=false,onHome:Boolean=false){
 val folder=data.profile.folders.firstOrNull{it.id==id};val app=data.app(id);if(folder==null&&app==null)return
 val name=folder?.name?:app!!.name
 Column(Modifier.fillMaxWidth().combinedClickable(onClick=onClick,onLongClick=onLongClick).padding(horizontal=2.dp).testTag("app-$id"),horizontalAlignment=Alignment.CenterHorizontally){
  BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
   val size=minOf((if(compact)52 else data.profile.iconSize).dp,(maxWidth-10.dp).coerceAtLeast(32.dp))
   if(folder!=null)Surface(Modifier.size(size),shape=RoundedCornerShape(19.dp),color=Color(0xFFB4C3E0).copy(alpha=.25f)){
    Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.SpaceEvenly){folder.apps.take(4).chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){row.forEach{member->val a=data.app(member);if(a!=null)AppIcon(a,20.dp)}}}}
   }else AppIcon(app!!,size)
  }
  if(!compact||showMenu)Text(name,Modifier.padding(top=7.dp).fillMaxWidth(),style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
  if(showMenu)IconButton(onClick=onLongClick,modifier=Modifier.height(28.dp).width(44.dp)){Icon(Icons.Default.MoreHoriz,"Options for $name",Modifier.size(18.dp),tint=if(onHome)Color.White.copy(alpha=.65f)else MaterialTheme.colorScheme.onSurfaceVariant)}
 }
}

@Composable internal fun AppIcon(app:LaunchApp,size:androidx.compose.ui.unit.Dp){
 val context=LocalContext.current
 val bitmap=remember(app.website?.icon){app.website?.icon?.takeIf{it.matches(Regex("icons/[a-z0-9-]+\\.png"))}?.let{runCatching{context.assets.open(it).use{stream->BitmapFactory.decodeStream(stream)?.asImageBitmap()}}.getOrNull()}}
 val colors=when(app.id){"store"->listOf(Color(0xFF6386FF),Color(0xFF8C4EE2));"photos"->listOf(Color(0xFFFFC28A),Color(0xFFEA778F));"settings"->listOf(Color(0xFF7E94B2),Color(0xFF4C617E));"wallpaper"->listOf(Color(0xFF80E6D1),Color(0xFF357EBA));"guide"->listOf(Color(0xFFC6B5FF),Color(0xFF7D6BBC));else->listOf(Color(0xFF536A9A),Color(0xFF343F66))}
 Box(Modifier.size(size).clip(RoundedCornerShape(size*.30f)).background(Brush.linearGradient(colors)),contentAlignment=Alignment.Center){
  if(bitmap!=null)Image(bitmap,null,Modifier.fillMaxSize().padding(size*.13f),contentScale=ContentScale.Fit)
  else if(app.website!=null)Text(app.name.take(2).uppercase(),fontSize=(size.value*.29f).sp,color=Color.White,fontWeight=FontWeight.SemiBold)
  else Icon(when(app.symbol){"store"->Icons.Default.ShoppingBag;"photos"->Icons.Default.PhotoLibrary;"wallpaper"->Icons.Default.Wallpaper;"settings"->Icons.Default.Settings;else->Icons.Default.AutoAwesome},null,Modifier.size(size*.49f),tint=Color.White)
 }
}
