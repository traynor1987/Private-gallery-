package uk.co.traynor.privategallery.home2
import uk.co.traynor.privategallery.R

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryDnd
import uk.co.traynor.privategallery.ui.NovaScreenPinningSettings
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
@Composable fun NovaPreview(data:PreviewData,pool:PreviewBrowserModel,mind:MindData,audio:MindAudio,widgets:WidgetData,searchHistory:NovaSearchHistory,video:NovaVideoModel,onPickImage:()->Unit,onOpen:(String)->Unit,filesDir:File,onNative:(String)->Unit,onLock:()->Unit,onPanic:()->Unit,externalRecentsRequest:Int=0,onRecentsConsumed:()->Unit={},onBackup:()->Unit={}){
 val state=data.state
 val dark=when(state.theme){"light"->false;"system"->isSystemInDarkTheme();else->true}
 MaterialTheme(colorScheme=if(dark)darkColors else lightColors){
  key(state.current){
   val localContext = LocalContext.current
   val application = ((localContext as? uk.co.traynor.privategallery.core.home2.SpaceContext)?.baseContext
    ?: localContext).applicationContext
   val sanctuaryStore = remember(application) {
    if (state.current == "primary") SanctuaryStore.get(application) else null
   }
   var dndStatus by remember { mutableStateOf("") }
   var guardianStatus by remember { mutableStateOf("Guardian off") }
   var sanctuary by remember{mutableStateOf(false)}
   var mindLaunch by remember{mutableIntStateOf(0)}
   var mindPage by remember{mutableStateOf("home")}
   var arcadeGame by remember{mutableStateOf<String?>(null)}
   var screen by remember{mutableStateOf("home")}
   val visual = remember { VisualNavigator() }
   var visualReturn by remember { mutableStateOf("home") }
   var inspectorReturn by remember{mutableStateOf("chat")}
   var recentsReturn by remember{mutableStateOf("home")}
   var shellSettings by remember{mutableStateOf(false)}
   var notifications by remember{mutableStateOf(false)}
   var edgePanel by remember{mutableStateOf(false)}
   var drawer by remember{mutableStateOf(false)}
   val openVisual: (String) -> Unit = { origin ->
    if (state.current == "primary") {
     visual.reset(); visualReturn=origin; drawer=false; screen="visual-foundation"
    }
   }
   var quick by remember{mutableStateOf(false)}
   var editing by remember{mutableStateOf(false)}
   var selected by remember{mutableStateOf<String?>(null)}
   var openFolder by remember{mutableStateOf<String?>(null)}
   var detailId by remember{mutableStateOf<String?>(null)}
   var custom by remember{mutableStateOf(false)}
   var browser by remember{mutableStateOf<StoreEntry?>(null)}
   var folderCreate by remember{mutableStateOf(false)}
   var rename by remember{mutableStateOf<String?>(null)}
   var addToFolder by remember{mutableStateOf<String?>(null)}
   var confirmUninstall by remember{mutableStateOf<String?>(null)}
   var reset by remember{mutableStateOf(false)}
   var selectedWidget by remember{mutableStateOf<String?>(null)}
   var widgetPage by remember{mutableIntStateOf(0)}


   val profile=data.profile
   LaunchedEffect(state.current){widgets.editingId=null;pool.currentProfile=state.current;audio.switchProfile(state.current);if(video.profile!=null&&video.profile!=state.current)video.close()}
   LaunchedEffect(screen){if(screen !in listOf("browser","chat"))pool.closeFullscreen();if(screen in listOf("chat","mind","store","wallpaper","photos","guide","video","search","widgets","backup","arcade","nova-calculator","lock-settings"))data.shell.touchInternal(state.current,screen)}
   LaunchedEffect(pool,state.current){while(true){pool.pollMedia();audio.poll();video.poll();delay(1000)}}
   val routeTransition=updateTransition(screen,label="preview-screen")
   val host=((localContext as? uk.co.traynor.privategallery.core.home2.SpaceContext)?.baseContext ?: localContext) as? android.app.Activity
   val pager=rememberPagerState(pageCount={data.profile.pages.size})
   val scope=rememberCoroutineScope()
   val currentPage=pager.currentPage.coerceIn(0,profile.pages.lastIndex)
   LaunchedEffect(profile.pages.size){if(pager.currentPage>profile.pages.lastIndex)pager.scrollToPage(profile.pages.lastIndex)}
   fun permitSanctuaryRoute(id: String): Boolean {
    val active = sanctuaryStore?.refresh() ?: return true
    val allowed = sanctuaryAllowsNovaRoute(active.novaApps, id)
    if (!allowed) android.widget.Toast.makeText(application,
     "Outside the current Sanctuary allowlist", android.widget.Toast.LENGTH_SHORT).show()
    return allowed
   }
   val launch:(String)->Unit=launch@{id->
    val destination = if (id.startsWith("recent-tab:")) {
     pool.tabs.firstOrNull{it.id==id.removePrefix("recent-tab:")&&it.profile==state.current}?.app?.id
      ?: return@launch
    } else id
    if (!permitSanctuaryRoute(destination)) return@launch
    drawer=false;quick=false;editing=false;edgePanel=false
    when{
     id.startsWith("resume-internal:")->{val route=id.removePrefix("resume-internal:");if(data.shell.internalRecents.any{it.first==state.current&&it.second==route}){if(route in nativeHomeApps)onNative(route)else screen=route}}
     id.startsWith("recent-tab:")->{pool.tabs.firstOrNull{it.id==id.removePrefix("recent-tab:")&&it.profile==state.current}?.let{pool.select(it);screen="home"}}
     id.startsWith("mind:")->{mindPage=id.removePrefix("mind:");mindLaunch++;screen="mind"}
     id.startsWith("store:")->{screen="store";detailId=id.removePrefix("store:")}
     id.startsWith("widget:")->{val slot=widgets.slots(state.current).firstOrNull{it.id==id.removePrefix("widget:")};if(slot!=null){scope.launch{pager.scrollToPage(slot.page.coerceIn(0,profile.pages.lastIndex))};selectedWidget=slot.id};screen="home"}
     else->when(id){
     "chat"->{screen="chat"}
     "widgets"->{widgetPage=currentPage;screen="widgets"}
     "search"->{screen=id}
     "video"->{onNative("photos")}
     "backup"->{screen="backup"}
     "nova-calculator"->{screen="nova-calculator"}
     "arcade","arcade-2048","arcade-sudoku","arcade-snake"->{arcadeGame=id.removePrefix("arcade-").takeIf{it!="arcade"};screen="arcade"}
     "lock-settings"->{onLock()}
     "shell-settings"->{shellSettings=true}
     "lock-now"->{onLock()}
     "panic"->{onPanic()}
     "mind","calm"->{mindPage=if(id=="calm")mind.preferred else "home";mindLaunch++;screen="mind";drawer=false}
     "sanctuary"->{sanctuary=sanctuaryStore==null;screen="sanctuary";drawer=false}
     "browser-v1"->{pool.open(standaloneBrowser,data.state.current).let{pool.setBrowserMode(it,true)};screen="home";drawer=false}
     "store"->{screen="store";drawer=false}
     "settings"->{editing=true;drawer=false}
     "wallpaper"->{screen="wallpaper";drawer=false}
     "photos"->{onNative("photos");drawer=false}
     "guide"->{screen="guide";drawer=false}
     else->if(id.startsWith("folder:"))openFolder=id else if(data.app(id)?.website!=null){pool.open(data.app(id)!!.website!!,data.state.current);drawer=false}else onNative(id)
    }}
   }
   LaunchedEffect(sanctuaryStore) {
    if (sanctuaryStore != null) SanctuaryGuardian.takeApprovedNova(application)?.let(launch)
   }
   val goHome:()->Unit={
    widgets.editingId=null;drawer=false;quick=false;editing=false;selected=null;openFolder=null;detailId=null;edgePanel=false;pool.closeFullscreen()
    host?.let{(it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(it.window.decorView.windowToken,0)}
    visual.reset()
    screen=if(sanctuary)"sanctuary"else "home"
   }
   val goBack:()->Unit={
    val ime=host?.window?.decorView?.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true
    if(ime)host?.let{(it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(it.window.decorView.windowToken,0)}else when{screen=="home"&&widgets.editingId!=null->widgets.editingId=null;selected!=null->selected=null;openFolder!=null->openFolder=null;detailId!=null->detailId=null;editing->editing=false;quick->quick=false;drawer->drawer=false;screen=="visual-foundation"->{if(!visual.back())screen=visualReturn};screen=="chat-inspector"->screen=inspectorReturn;screen=="chat-settings"->screen="chat";screen=="navigation-settings"||screen=="sanctuary-settings"->screen="home-settings";screen=="sanctuary-permissions"->screen="sanctuary";screen=="recents"->screen=if(recentsReturn in listOf("chat","mind","store","wallpaper","photos","guide","video","search","widgets","backup","arcade","nova-calculator","lock-settings")&&data.shell.internalRecents.none{it.first==state.current&&it.second==recentsReturn})"home"else PreviewShellRules.returnRoute(recentsReturn,pool.active?.profile==state.current);else->screen=if(sanctuary)"sanctuary"else "home"}
   }
   LaunchedEffect(screen, sanctuaryStore) {
    if (sanctuaryStore != null && screen in setOf("sanctuary", "sanctuary-settings", "sanctuary-permissions")) {
     while (true) {
      guardianStatus = SanctuaryGuardian.status(application)
      dndStatus = SanctuaryDnd.status(application).label
      delay(500)
     }
    }
   }
   LaunchedEffect(screen,pool.activeId){if(screen=="browser"&&pool.active?.profile!=state.current)screen="home"}
   val openRecents:()->Unit={drawer=false;quick=false;if(screen!="recents"){recentsReturn=screen;screen="recents"}}
   LaunchedEffect(externalRecentsRequest){if(externalRecentsRequest>0){openRecents();onRecentsConsumed()}}
   val resumeTab:(PreviewTab)->Unit={tab->if(permitSanctuaryRoute(tab.app.id)){pool.select(tab);screen="home";edgePanel=false}}
   BackHandler((widgets.editingId!=null||screen!="home"||drawer||quick||editing||selected!=null||openFolder!=null||detailId!=null)){goBack()}
   run {
   Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()){
     Box(Modifier.weight(1f).fillMaxWidth()){
      if(screen !in listOf("browser","chat"))Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
       routeTransition.AnimatedContent(transitionSpec={if(widgets.reduced)androidx.compose.animation.ContentTransform(androidx.compose.animation.EnterTransition.None,androidx.compose.animation.ExitTransition.None)else androidx.compose.animation.ContentTransform(androidx.compose.animation.fadeIn(),androidx.compose.animation.fadeOut())}){route->
        key(route){when(route){
         "home-settings"->Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-home-settings")){
          PreviewHeader("Nova Settings","Home & appearance",onBack=goHome)
          Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)){
     Text("Home tools",style=MaterialTheme.typography.titleMedium)
     ActionRow("Widget Centre",Icons.Default.Widgets){editing=false;widgetPage=currentPage;screen="widgets"}

     if(sanctuaryStore!=null)ActionRow("Sanctuary",Icons.Default.Security){screen="sanctuary-settings"}
     ActionRow("Lock Space",Icons.Default.Lock){onLock()}
     ActionRow("Panic Exit",Icons.Default.Security){onPanic()}
     ActionRow("Nova Backup",Icons.Default.Save){editing=false;onBackup()}
     ActionRow("Navigation & multitasking",Icons.Default.Tune){editing=false;shellSettings=true}
     ActionRow("Nova events",Icons.Default.NotificationsNone){editing=false;notifications=true}
     Row(verticalAlignment=Alignment.CenterVertically){Text("Original Home clock",Modifier.weight(1f));Switch(widgets.clockVisible(state.current),{widgets.clock(state.current,it)},modifier=Modifier.testTag("original-clock-visible"))}
     Text("Appearance & layout",style=MaterialTheme.typography.titleMedium);Text("${data.profileLabel} · Home ${currentPage+1}",color=MaterialTheme.colorScheme.primary)
     ActionRow("Choose wallpaper",Icons.Default.Wallpaper){editing=false;screen="wallpaper"}
     Text("Grid density · ${profile.columns}");Slider(profile.columns.toFloat(),{v->data.change{it.copy(columns=v.toInt())}},valueRange=3f..6f,steps=2,modifier=Modifier.testTag("grid-density"))
     Text("Icon size · ${profile.iconSize} dp");Slider(profile.iconSize.toFloat(),{v->data.change{it.copy(iconSize=v.toInt())}},valueRange=44f..76f)
     Text("Layouts adapt to screen width and text size.",style=MaterialTheme.typography.bodySmall)
     Text("Home pages",Modifier.padding(top=16.dp),style=MaterialTheme.typography.titleMedium)
     ActionRow("Add Home page (${profile.pages.size}/8)",Icons.Default.Add,enabled=profile.pages.size<8){data.addPage();scope.launch{pager.animateScrollToPage(data.profile.pages.lastIndex)}}
     if(profile.pages.size>1&&profile.pages[currentPage].isEmpty()&&widgets.slots(state.current).none{it.page==currentPage})ActionRow("Remove empty page",Icons.Default.Remove){data.removePage(currentPage)}
     if(currentPage>0)ActionRow("Move page left",Icons.Default.ChevronLeft){data.movePage(currentPage,-1);scope.launch{pager.scrollToPage(currentPage-1)}}
     if(currentPage<profile.pages.lastIndex)ActionRow("Move page right",Icons.Default.ChevronRight){data.movePage(currentPage,1);scope.launch{pager.scrollToPage(currentPage+1)}}
     ActionRow("Add apps from drawer",Icons.Default.Apps){editing=false;drawer=true}
     ActionRow("Create folder",Icons.Default.CreateNewFolder){folderCreate=true}
     ActionRow("Theme · ${state.theme}",Icons.Default.Palette){data.setTheme(uk.co.traynor.privategallery.core.ui.NativeThemePolicy.next(state.theme))}
     data.onSwitch?.takeIf{data.state.current=="hidden"}?.let{changeSpace->ActionRow("Return to Primary",Icons.Default.SwapHoriz){changeSpace()}}
     ActionRow("Restore Home defaults",Icons.Default.RestartAlt){reset=true}
          }
         }
         "navigation-settings"->NovaShellSettings(data){screen="home-settings"}
         "sanctuary-settings"->if(sanctuaryStore!=null) SanctuarySettings(
          sanctuaryStore,onEnded={
           SanctuaryGuardian.stop(application);SanctuaryDnd.reconcile(application);screen="home-settings"
          },onBack={screen="home-settings"},
         )
         "sanctuary-permissions"->if(sanctuaryStore!=null) SanctuaryPermissionScreen(
          application,guardianStatus,sanctuaryStore.current.collectAsState().value?.enableGuardian==true,
          onResume={SanctuaryGuardian.start(application);guardianStatus=SanctuaryGuardian.status(application)},
          onBack={screen="sanctuary"},
         )
         "widgets"->WidgetCentre(data,widgets,widgetPage,onBack=goHome)
         "search"->NovaSearch(data,widgets,pool,searchHistory,onBack=goHome,onResult=launch,onWeb={query->if(permitSanctuaryRoute("browser-v1")){val tab=pool.open(standaloneBrowser,state.current);pool.setBrowserMode(tab,true);pool.navigate(tab,"https://www.google.com/search?q="+java.net.URLEncoder.encode(query,"UTF-8"));screen="home"}})
         "video"->VideoScreen(video,goHome)
         "backup"->LaunchedEffect(Unit){onBackup();goHome()}
         "nova-calculator"->CalculatorScreen(screen==route,onHome=goHome)
         "arcade"->ArcadeScreen(data,arcadeGame,screen==route,onHome=goHome)
         "lock-settings"->LaunchedEffect(Unit){onLock()}
         "mind"->{
          MindScreen(mind,state.current,audio,mindPage,mindLaunch,onHome=goHome,onSanctuary={sanctuary=sanctuaryStore==null;screen="sanctuary"})
         }
         "chat","chat-inspector","chat-settings"->Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)){PreviewHeader("Nova Chat Lite · Beta","Not enabled in this integration",goHome);Text("Native Favourites integration remains disabled pending production session and privacy verification. Normal WhatsApp is available through Nova Store in Primary Space.")}
         "sanctuary"->if(sanctuaryStore==null) {
          SanctuaryScreen(data,audio,onMind={mindPage="home";mindLaunch++;screen="mind"},
           onCalm={mindPage=mind.preferred;mindLaunch++;screen="mind"},onLaunch=launch,
           onExit={sanctuary=false;screen="home"})
         } else {
          SanctuaryHome(
           store=sanctuaryStore,androidApps=remember(application){SanctuaryGuardian.installedApps(application)},
           novaApps=data.allApps().filter{it.id!="sanctuary"&&it.id!="calm"&&!it.id.startsWith("arcade-")}.associate{it.id to it.name},
           protectionStatus=guardianStatus,
           onStart={mode->sanctuaryStore.start(mode).also{started->
            if(started){
             SanctuaryGuardian.start(application)
             SanctuaryDnd.start(application)
             guardianStatus=SanctuaryGuardian.status(application)
             dndStatus=SanctuaryDnd.status(application).label
            }
           }},
           onPermissions={screen="sanctuary-permissions"},
           onLaunchAndroid={pkg->SanctuaryGuardian.launch(application,pkg)},
           onLaunchNova=launch,
           onBack={sanctuary=false;screen="home"},
           onPhone={SanctuaryGuardian.openPhone(application)},
           onExtended={SanctuaryDnd.reconcile(application)},
           dndStatus=dndStatus,
           onDndSettings={
            val allowed=application.getSystemService(android.app.NotificationManager::class.java)
             ?.isNotificationPolicyAccessGranted==true
            if(allowed||!SanctuaryDnd.openPolicyAccess(application))SanctuaryDnd.openAndroidControls(application)
           },
           pinningControls={
            val space = localContext as? uk.co.traynor.privategallery.core.home2.SpaceContext
            val sessionId = sanctuaryStore.current.collectAsState().value?.id
            val authority = remember(space, sessionId) {{
             space?.current()==true && sessionId!=null &&
              sanctuaryStore.current.value?.let{it.id==sessionId&&it.offerPinning}==true
            }}
            NovaScreenPinningSettings(authority, host as? androidx.activity.ComponentActivity)
           },
          )
         }
         "recents"->NovaRecents(data,pool,onHome=goHome,onResume=resumeTab,onInternal={id->if(permitSanctuaryRoute(id)){if(id in nativeHomeApps)onNative(id)else screen=id}},onCloseInternal={id->if(id=="browser-v1")onNative("close-browser");data.shell.closeInternal(state.current,id);if(id=="mind")audio.pause();if(id=="video")video.close()})
         "visual-foundation"->if(state.current=="primary")VisualFoundation(
          visual,onClose={screen=visualReturn},onRealApp={id->visual.reset();screen="home";launch(id)},
         )
         "store"->StoreScreen(data,onBack=goHome,onDetail={detailId=it.id},onCustom={custom=true},
          onVisualFoundation=if(state.current=="primary"){{openVisual("store")}}else null)
         "wallpaper"->LaunchedEffect(Unit){onPickImage();goHome()}
         "photos"->LaunchedEffect(Unit){onNative("photos")}
         "guide"->LaunchedEffect(Unit){onNative("native-settings")}
         else->Home(data,filesDir,pager,widgets,pool,audio,video,onWidgetEdit={selectedWidget=it},onResume=resumeTab,onLaunch=launch,onSelect={selected=it},onDrawer={drawer=true},onQuick={quick=true},onEdit={editing=true})
        }}
       }
      }
      if(!drawer)NovaEdgeHandle(data.shell){edgePanel=true}
      VideoMiniPlayer(video){if(permitSanctuaryRoute("video"))screen="video"}
     }
     if(audio.sound.isNotBlank())Surface(tonalElevation=3.dp){Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text("Calm · ${audio.sound}",Modifier.weight(1f));IconButton(onClick={if(audio.playing)audio.pause()else audio.resume()}){Icon(if(audio.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Toggle calm sound")};TextButton(onClick={launch("mind:sounds")}){Text("Open")}}}
     if(screen!="browser")NovaMiniPlayer(pool,state.current,onResume=resumeTab)
     NovaNavigation(data.shell,onRecents=openRecents,onHome=goHome,onBack=goBack)
    }
   }
   if(selectedWidget!=null)WidgetOptions(data,widgets,selectedWidget!!){selectedWidget=null}
   LaunchedEffect(shellSettings){if(shellSettings){shellSettings=false;screen="navigation-settings"}}
   LaunchedEffect(editing){if(editing){editing=false;screen="home-settings"}}
   if(data.shell.dim>0)Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=data.shell.dim)))
   if(notifications)NovaNotifications(data,onDismiss={notifications=false},onLaunch={id->notifications=false;launch(id)},media={NowPlaying(pool,audio,video,state.current,onResume=resumeTab,onVideo={if(permitSanctuaryRoute("video"))screen="video";notifications=false},onAudio={notifications=false;launch("mind:sounds")})})
   if(edgePanel)NovaEdgePanel(data,pool,onDismiss={edgePanel=false},onLaunch={id->edgePanel=false;launch(id)},onHome=goHome,onSettings={edgePanel=false;shellSettings=true})
   if(drawer)PanicHomeModalBottomSheet(onDismissRequest={drawer=false},modifier=Modifier.testTag("drawer-sheet")){
    var search by remember{mutableStateOf("")}
    Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal=20.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){
      Text("Your apps",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
      var options by remember{mutableStateOf(false)}
      Box{
       IconButton(onClick={options=true}){Icon(Icons.Default.MoreVert,"Choose app options")}
       PanicHomeDropdownMenu(options,{options=false}){
        data.drawerIds().forEach{id->DropdownMenuItem(text={Text(data.profile.folders.firstOrNull{it.id==id}?.name?:data.app(id)?.name?:id)},onClick={options=false;selected=id})}
       }
      }
      IconButton(onClick={drawer=false}){Icon(Icons.Default.Close,"Close app drawer")}
     }
     Text(data.profileLabel,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
     OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(vertical=16.dp).testTag("drawer-search"),placeholder={Text("Search apps and folders")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=Shape)
     if(state.current=="primary")VisualCatalogueEntry("drawer-visual-foundation"){openVisual("home")}
     val visualMatches=if(state.current=="primary"&&search.isNotBlank())visualModules.filter{it.title.contains(search.trim(),true)}else emptyList()
     AdaptiveAppGrid(data,data.drawerIds().filter{id->val f=profile.folders.firstOrNull{it.id==id};(f?.name?:data.app(id)?.name?:"").contains(search,true)},onLaunch=launch,onSelect={selected=it},modifier=Modifier.weight(1f),
      visualMatches=visualMatches,onVisual={id->openVisual("home");visual.open(id)})
     TextButton(onClick={folderCreate=true}){Icon(Icons.Default.CreateNewFolder,null);Text("Create folder",Modifier.padding(start=8.dp))}
    }
   }
   if(quick)NovaQuickPanel(data,pool,audio,video,widgets,filesDir,onDismiss={quick=false},onAction={id->
    quick=false
    when(id){"recents"->openRecents();"events"->notifications=true;"home-settings"->screen="home-settings";"widgets"->{widgetPage=currentPage;screen="widgets"};else->launch(id)}
   },onResume=resumeTab)
   val chosenId=selected
   if(chosenId!=null){
    val folder=profile.folders.firstOrNull{it.id==chosenId};val app=data.app(chosenId)
    PanicHomeModalBottomSheet(onDismissRequest={selected=null}){
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
   if(activeFolder!=null)PanicHomeModalBottomSheet(onDismissRequest={openFolder=null}){
    Column(Modifier.fillMaxWidth().heightIn(max=650.dp).padding(20.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Text(activeFolder.name,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);IconButton(onClick={rename=activeFolder.id}){Icon(Icons.Default.Edit,"Rename folder")};IconButton(onClick={openFolder=null}){Icon(Icons.Default.Close,"Close folder")}}
     AdaptiveAppGrid(data,activeFolder.apps,onLaunch={openFolder=null;launch(it)},onSelect={openFolder=null;selected=it},modifier=Modifier.heightIn(min=170.dp,max=420.dp))
     Text("Long-press an app for placement and order actions.",style=MaterialTheme.typography.bodySmall)
     TextButton(onClick={data.dissolveFolder(activeFolder.id);openFolder=null}){Text("Dissolve folder")}
    }
   }
   if(folderCreate)CreateFolderDialog(data,currentPage,onDismiss={folderCreate=false})
   if(addToFolder!=null)PanicHomeAlertDialog(onDismissRequest={addToFolder=null},title={Text("Choose folder")},text={Column{profile.folders.forEach{f->TextButton(onClick={data.folderAdd(f.id,addToFolder!!);addToFolder=null}){Text(f.name)}};TextButton(onClick={addToFolder=null;folderCreate=true}){Text("Create a new folder")}}},confirmButton={TextButton(onClick={addToFolder=null}){Text("Cancel")}})
   if(rename!=null){var text by remember(rename){mutableStateOf(profile.folders.firstOrNull{it.id==rename}?.name?:data.app(rename!!)?.name?:"")};PanicHomeAlertDialog(onDismissRequest={rename=null},title={Text("Rename")},text={OutlinedTextField(text,{text=it.take(40)},label={Text("Name")})},confirmButton={TextButton(onClick={data.rename(rename!!,text);rename=null},enabled=text.isNotBlank()){Text("Save")}},dismissButton={TextButton(onClick={rename=null}){Text("Cancel")}})}
   if(reset)PanicHomeAlertDialog(onDismissRequest={reset=false},title={Text("Restore this Home?")},text={Text("Reset this Space’s layout, wallpaper, folders and Dock. Installed shortcuts remain in the drawer.")},confirmButton={TextButton(onClick={data.reset();reset=false}){Text("Restore")}},dismissButton={TextButton(onClick={reset=false}){Text("Cancel")}})
   val detail=detailId?.let{id->data.catalogue.firstOrNull{it.id==id}?:profile.installed.firstOrNull{it.id==id}}
   if(detail!=null)AppDetails(data,detail,onDismiss={detailId=null},onOpen={if(permitSanctuaryRoute(it.id)){pool.open(it,data.state.current);detailId=null;screen="home"}},onUninstall={confirmUninstall=it},onAddHome={data.addHome(it,currentPage);detailId=null;screen="home"},onPin={data.pin(it)})
   if(custom)CustomWebsiteDialog(onDismiss={custom=false},onInstall={entry->data.install(entry);custom=false;detailId=data.entryKey(entry)})
   if(confirmUninstall!=null)PanicHomeAlertDialog(onDismissRequest={confirmUninstall=null},title={Text("Uninstall this shortcut?")},text={Text("Remove it from this Space, its folders, Home and Dock. Browser cookies are shared and are not selectively removed. Website accounts and external browser data remain managed by their provider/browser.")},confirmButton={TextButton(onClick={pool.closeApp(data.state.current,confirmUninstall!!);data.uninstall(confirmUninstall!!);confirmUninstall=null;detailId=null}){Text("Uninstall")}},dismissButton={TextButton(onClick={confirmUninstall=null}){Text("Cancel")}})
   pool.error?.let{message->PanicHomeAlertDialog(onDismissRequest={pool.error=null},title={Text("Browsing unavailable")},text={Text(message)},confirmButton={TextButton(onClick={pool.error=null}){Text("OK")}})}
   data.error?.let{message->PanicHomeAlertDialog(onDismissRequest={},title={Text("Unable to save")},text={Text(message)},confirmButton={TextButton(onClick={data.retrySave()}){Text("Retry")}})}
   }
  }
 }
}

@Composable private fun Notice(text:String){Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.55f)){Text(text,Modifier.padding(14.dp),style=MaterialTheme.typography.bodySmall)}}
@Composable private fun ActionRow(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,enabled:Boolean=true,onClick:()->Unit){TextButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),contentPadding=PaddingValues(vertical=12.dp,horizontal=4.dp)){Icon(icon,null,Modifier.size(22.dp));Text(label,Modifier.weight(1f).padding(start=14.dp),textAlign=TextAlign.Start);Icon(Icons.Default.ChevronRight,null)}}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Home(data:PreviewData,filesDir:File,pager:PagerState,widgets:WidgetData,pool:PreviewBrowserModel,audio:MindAudio,video:NovaVideoModel,onWidgetEdit:(String)->Unit,onResume:(PreviewTab)->Unit,onLaunch:(String)->Unit,onSelect:(String)->Unit,onDrawer:()->Unit,onQuick:()->Unit,onEdit:()->Unit){
 val space=LocalContext.current as? uk.co.traynor.privategallery.core.home2.SpaceContext
 if(space?.current()==false)return
 val profile=data.profile;val scope=rememberCoroutineScope()
 // Lazy pager items can be remeasured after their enclosing Space has been retired.
 // Resolve the encrypted preference in the admitted parent, never from a retained item.
 val originalClockVisible=widgets.clockVisible(data.state.current)
 BoxWithConstraints(Modifier.fillMaxSize().testTag("preview-home")){
  val compactHeight=maxHeight<500.dp
  val landscapeClock=compactHeight&&maxWidth>=600.dp
  WallpaperSurface(profile.wallpaper,filesDir,Modifier.fillMaxSize())
  CompositionLocalProvider(LocalContentColor provides Color.White){
   Column(Modifier.fillMaxSize().safeDrawingPadding()){
    BoxWithConstraints(Modifier.fillMaxWidth()){
    val narrowStatus=maxWidth<420.dp
    Column {
    if(narrowStatus)Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.End){uk.co.traynor.privategallery.ui.status.NovaSystemStatus()}
    Row(Modifier.fillMaxWidth().padding(start=22.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){
     Image(painterResource(R.drawable.nova_identity),null,Modifier.size(28.dp));Column(Modifier.weight(1f).padding(start=9.dp)){Text("NOVAOS",fontSize=10.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);if(!compactHeight)Text(data.profileLabel,style=MaterialTheme.typography.labelMedium,color=Color.White.copy(alpha=.7f),maxLines=1,overflow=TextOverflow.Ellipsis)}
     if(!narrowStatus)uk.co.traynor.privategallery.ui.status.NovaSystemStatus(modifier=Modifier.padding(end=4.dp))
     if(compactHeight)IconButton(onClick=onDrawer,modifier=Modifier.testTag("open-apps")){Icon(Icons.Default.Apps,"Open apps")}
     IconButton(onClick={onLaunch("search")}){Icon(Icons.Default.Search,"Open Nova Search")}
     IconButton(onClick={onLaunch("calm")}){Icon(Icons.Default.Spa,"Calm Now")}
     IconButton(onClick=onQuick){Icon(Icons.Default.Tune,"Open Quick Panel")}
     Box{var menu by remember{mutableStateOf(false)};IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Home menu")};PanicHomeDropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Apps")},onClick={menu=false;onDrawer()});DropdownMenuItem(text={Text("Edit Home")},onClick={menu=false;onEdit()});DropdownMenuItem(text={Text("Widget Centre")},onClick={menu=false;onLaunch("widgets")});data.onSwitch?.takeIf{data.state.current=="hidden"}?.let{switch->DropdownMenuItem(text={Text("Return to Primary")},onClick={menu=false;switch()})}}}
    }
    }}
    widgets.recoveryNotice?.let{notice->Surface(color=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer){Column(Modifier.padding(12.dp)){Text(notice,style=MaterialTheme.typography.bodySmall);TextButton(onClick={widgets.dismissRecovery()}){Text("Dismiss recovery report")}}}}
    if(widgets.editingId!=null)Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text("Edit widgets",Modifier.weight(1f),color=Color.White);TextButton(onClick={widgets.editingId=null},modifier=Modifier.testTag("widget-edit-done")){Text("Done",color=Color.White)}}
    HorizontalPager(pager,Modifier.weight(1f).fillMaxWidth().testTag("home-pager").semantics{stateDescription="Home ${pager.currentPage+1}"},key={it}){page->
     if(space?.current()==false)return@HorizontalPager
     val content:@Composable (Modifier)->Unit={modifier->
      BoxWithConstraints(modifier){
       val pageHeight=maxHeight;val scroll=rememberScrollState()
       Column(Modifier.fillMaxSize().verticalScroll(scroll).heightIn(min=pageHeight).combinedClickable(onClick={},onLongClick=onEdit).padding(horizontal=20.dp)){
        HomeWidgets(data,widgets,pool,audio,video,page,if(compactHeight)160.dp else 340.dp,onLaunch,onResume,onWidgetEdit,onPage={destination->scope.launch{pager.scrollToPage(destination)}})
        if(profile.pages[page].isNotEmpty())AdaptiveAppGrid(data,profile.pages[page],onLaunch,onSelect,Modifier.fillMaxWidth(),onHome=true)
        Box(Modifier.weight(1f).heightIn(min=64.dp).fillMaxWidth().testTag("home-empty-gesture").emptyHomeSwipe({!scroll.canScrollBackward&&!scroll.canScrollForward},onQuick,onDrawer).combinedClickable(onClick={},onLongClick=onEdit),contentAlignment=Alignment.Center){if(profile.pages[page].isEmpty())Text("Room for your next idea\nLong-press to edit Home",textAlign=TextAlign.Center,color=Color.White.copy(alpha=.65f))}
       }
      }
     }
     if(landscapeClock&&page==0&&originalClockVisible){
      Row(Modifier.fillMaxSize()){
       HomeClock(Modifier.width(220.dp).padding(start=20.dp,top=4.dp,end=12.dp).testTag("home-original-clock"),true)
       content(Modifier.weight(1f).fillMaxHeight())
      }
     }else Column(Modifier.fillMaxSize()){
      if(page==0&&originalClockVisible)HomeClock(Modifier.padding(horizontal=20.dp).testTag("home-original-clock").padding(top=if(compactHeight)4.dp else 26.dp,bottom=if(compactHeight)8.dp else 28.dp),compactHeight)
      content(Modifier.weight(1f).fillMaxWidth())
     }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center){repeat(profile.pages.size){page->IconButton(onClick={scope.launch{if(widgets.reduced)pager.scrollToPage(page)else pager.animateScrollToPage(page)}},modifier=Modifier.size(if(compactHeight)32.dp else 40.dp)){Icon(if(page==pager.currentPage)Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,"Home page ${page+1}",Modifier.size(7.dp))}}}
    Surface(Modifier.padding(horizontal=18.dp).fillMaxWidth(),shape=RoundedCornerShape(30.dp),color=Color.White.copy(alpha=.12f),contentColor=Color.White){
     Row(Modifier.padding(horizontal=7.dp,vertical=if(compactHeight)6.dp else 12.dp),horizontalArrangement=Arrangement.SpaceEvenly){profile.dock.forEach{id->Box(Modifier.weight(1f)){AppTile(data,id,onClick={onLaunch(id)},onLongClick={onSelect(id)},showMenu=false,compact=true)}}}
    }
    if(!compactHeight)TextButton(onClick=onDrawer,modifier=Modifier.align(Alignment.CenterHorizontally).height(36.dp).testTag("open-apps")){Icon(Icons.Default.KeyboardArrowUp,null,Modifier.size(18.dp),tint=Color.White);Text("Apps",Modifier.padding(start=6.dp),color=Color.White,style=MaterialTheme.typography.labelLarge)}
   }
  }
 }
}

@Composable private fun HomeClock(modifier:Modifier=Modifier,compact:Boolean=false){
 var now by remember{mutableStateOf(ZonedDateTime.now())};LaunchedEffect(Unit){while(true){now=ZonedDateTime.now();delay(1000)}}
 Column(modifier){Text(now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),fontSize=if(compact)34.sp else 60.sp,fontWeight=FontWeight.Light,maxLines=1,overflow=TextOverflow.Ellipsis);Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),style=MaterialTheme.typography.bodyLarge,color=Color.White.copy(alpha=.8f))}
}

@Composable internal fun WallpaperSurface(w:Wallpaper,filesDir:File,modifier:Modifier=Modifier,previewBitmap:android.graphics.Bitmap?=null){
 val colors=when(w.builtin){"Midnight"->listOf(Color(0xFF080C21),Color(0xFF29315C),Color(0xFF341E55),Color(0xFF080D1A));"Dusk"->listOf(Color(0xFF3B2253),Color(0xFF7E4C76),Color(0xFFB16D6C),Color(0xFF26304B));else->listOf(Color(0xFF081A36),Color(0xFF324C92),Color(0xFF147F83),Color(0xFF091B2A))}
 val wallpaperContext=LocalContext.current as? uk.co.traynor.privategallery.core.home2.SpaceContext
 var decoded by remember(w.image,w.imageRevision,wallpaperContext){mutableStateOf<android.graphics.Bitmap?>(null)}
 DisposableEffect(decoded){val owned=decoded;onDispose{owned?.let(uk.co.traynor.privategallery.core.home2.WallpaperCoordinator::wipe)}}
 DisposableEffect(wallpaperContext){val retired=wallpaperContext?.onRetire{android.os.Handler(android.os.Looper.getMainLooper()).post{decoded=null}};onDispose{retired?.close()}}
 LaunchedEffect(w.image,w.imageRevision,wallpaperContext,previewBitmap){
  if(previewBitmap==null&&w.image!=null&&wallpaperContext!=null){
   var candidate:android.graphics.Bitmap?=null
   try{
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){wallpaperContext.readWallpaper(w.image)?.let{bytes->try{candidate=uk.co.traynor.privategallery.core.home2.StoredWallpaperImage.decodeStored(bytes)}finally{bytes.fill(0)}}}
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate){
     kotlinx.coroutines.currentCoroutineContext().ensureActive()
     wallpaperContext.publishIfCurrent{decoded=candidate;candidate=null}
    }
   }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){}finally{candidate?.let(uk.co.traynor.privategallery.core.home2.WallpaperCoordinator::wipe)}
  }
 }
 val image=(previewBitmap?:decoded)?.takeIf{!it.isRecycled}?.asImageBitmap()
 BoxWithConstraints(modifier.clipToBounds()){
  val density=LocalDensity.current
  val dx=with(density){maxWidth.toPx()}*(w.zoom-1f)*w.x/2;val dy=with(density){maxHeight.toPx()}*(w.zoom-1f)*w.y/2
  Box(Modifier.fillMaxSize().blur(w.blur.dp).background(Brush.linearGradient(colors)))
  if(image!=null)Image(image,null,Modifier.fillMaxSize().blur(w.blur.dp).graphicsLayer{scaleX=w.zoom;scaleY=w.zoom;translationX=dx;translationY=dy},contentScale=ContentScale.Crop,alignment=BiasAlignment(w.x,w.y))
  else Canvas(Modifier.fillMaxSize().blur(22.dp)){drawCircle(Color.White.copy(alpha=.055f),radius=size.width*.65f,center=androidx.compose.ui.geometry.Offset(size.width*1.02f,size.height*.3f));drawCircle(Color(0xFF83E7DA).copy(alpha=.08f),radius=size.width*.48f,center=androidx.compose.ui.geometry.Offset(0f,size.height*.8f))}
  Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=w.dim)))
 }
}

@Composable private fun AdaptiveAppGrid(data:PreviewData,ids:List<String>,onLaunch:(String)->Unit,onSelect:(String)->Unit,modifier:Modifier=Modifier,onHome:Boolean=false,onEmptyDown:()->Unit={},onEmptyUp:()->Unit={},visualMatches:List<VisualModule> = emptyList(),onVisual:(String)->Unit={}){
 BoxWithConstraints(modifier.fillMaxWidth()){
  val columns=PreviewRules.columns(maxWidth.value.toInt(),LocalDensity.current.fontScale,data.profile.columns)
  if(onHome){
   Column(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    ids.chunked(columns).forEach{row->Row(Modifier.fillMaxWidth()){
     row.forEach{id->Box(Modifier.weight(1f)){AppTile(data,id,onClick={onLaunch(id)},onLongClick={onSelect(id)},showMenu=true,onHome=true)}}
     repeat(columns-row.size){Spacer(Modifier.weight(1f))}
    }}
   }
  }else{
   LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)){
    items(ids,key={it}){id->AppTile(data,id,onClick={onLaunch(id)},onLongClick={onSelect(id)},showMenu=true,onHome=false)}
    items(visualMatches,key={"visual:${it.id}"}){module->VisualDrawerTile(module){onVisual(module.id)}}
   }
  }
 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun AppTile(data:PreviewData,id:String,onClick:()->Unit,onLongClick:()->Unit,showMenu:Boolean=true,compact:Boolean=false,onHome:Boolean=false){
 val folder=data.profile.folders.firstOrNull{it.id==id};val app=data.app(id);if(folder==null&&app==null)return
 val name=folder?.name?:app!!.name
 Column(Modifier.fillMaxWidth().semantics{customActions=listOf(CustomAccessibilityAction("Options for $name"){onLongClick();true})}.combinedClickable(onClick=onClick,onLongClick=onLongClick).padding(horizontal=2.dp).testTag("app-$id"),horizontalAlignment=Alignment.CenterHorizontally){
  BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
   val size=minOf((if(compact)52 else data.profile.iconSize).dp,(maxWidth-10.dp).coerceAtLeast(32.dp))
   if(folder!=null)Surface(Modifier.size(size),shape=RoundedCornerShape(19.dp),color=Color(0xFFB4C3E0).copy(alpha=.25f)){
    Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.SpaceEvenly){
     repeat(2){row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){repeat(2){col->
      val member=folder.apps.getOrNull(row*2+col);val a=member?.let{data.app(it)};val cell=(size-24.dp)/2
      if(a!=null)AppIcon(a,cell)else Spacer(Modifier.size(cell))
     }}}
    }
   }else AppIcon(app!!,size)
  }
  if(!compact||showMenu)Text(name,Modifier.padding(top=7.dp).fillMaxWidth(),style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
 }
}

@Composable internal fun AppIcon(app:LaunchApp,size:androidx.compose.ui.unit.Dp){
 val context=LocalContext.current
 val bitmap=remember(app.website?.icon){PreviewIconCache.load(context,app.website?.icon)}
 val colors=when(app.id){"store"->listOf(Color(0xFF6386FF),Color(0xFF8C4EE2));"photos"->listOf(Color(0xFFFFC28A),Color(0xFFEA778F));"settings"->listOf(Color(0xFF7E94B2),Color(0xFF4C617E));"wallpaper"->listOf(Color(0xFF80E6D1),Color(0xFF357EBA));"guide"->listOf(Color(0xFFC6B5FF),Color(0xFF7D6BBC));else->listOf(Color(0xFF536A9A),Color(0xFF343F66))}
 Box(Modifier.size(size).clip(RoundedCornerShape(size*.30f)).background(Brush.linearGradient(colors)),contentAlignment=Alignment.Center){
  if(bitmap!=null)Image(bitmap,null,Modifier.fillMaxSize().padding(size*.13f),contentScale=ContentScale.Fit,filterQuality=androidx.compose.ui.graphics.FilterQuality.High)
  else if(app.website!=null)Text(app.name.take(2).uppercase(),fontSize=(size.value*.29f).sp,color=Color.White,fontWeight=FontWeight.SemiBold)
  else Icon(when(app.symbol){"chat"->Icons.Default.ChatBubbleOutline;"widgets"->Icons.Default.Widgets;"search"->Icons.Default.Search;"video"->Icons.Default.PlayCircle;"backup"->Icons.Default.Save;"arcade"->Icons.Default.SportsEsports;"calculator"->Icons.Default.Calculate;"lock"->Icons.Default.Lock;"mind"->Icons.Default.Spa;"browser"->Icons.Default.Language;"store"->Icons.Default.ShoppingBag;"photos"->Icons.Default.PhotoLibrary;"wallpaper"->Icons.Default.Wallpaper;"settings"->Icons.Default.Settings;"camera"->Icons.Default.PhotoCamera;"evidence"->Icons.Default.FolderSpecial;"edit"->Icons.Default.Edit;"apps"->Icons.Default.Apps;"trash"->Icons.Default.DeleteOutline;"folder"->Icons.Default.Folder;"heart"->Icons.Default.Favorite;else->Icons.Default.AutoAwesome},null,Modifier.size(size*.49f),tint=Color.White)
 }
}

private val nativeHomeApps=setOf("photos","vault","camera","evidence","editor","ai-edit","collections","trash","favourites","native-settings","private-apps","browser-v1")
