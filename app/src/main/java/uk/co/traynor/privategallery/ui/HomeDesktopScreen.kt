package uk.co.traynor.privategallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut
import uk.co.traynor.privategallery.core.home.NovaNavigation
import uk.co.traynor.privategallery.core.home.desktop.HomeDesktop
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * INACTIVE preparation, deliberately unreferenced by MainActivity.
 * Host must key composition by captured authenticated session identity+epoch and
 * dispose it immediately on revoke. State/callbacks are never authentication.
 * No media, disk, WebView, VPN, screenshot policy or session APIs are used here.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable internal fun HomeDesktopScreen(
    desktop: HomeDesktop,
    onChange: (HomeDesktop) -> Unit,
    onLaunch: (Shortcut) -> Unit,
    onLock: () -> Unit,
    onPanic: () -> Unit,
    onClassic: () -> Unit,
    onExit: () -> Unit,
    quickActions: List<HomeQuickAction>,
) {
    var drawer by remember { mutableStateOf(false) }
    var quickPanel by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Shortcut?>(null) }
    val pager = rememberPagerState(pageCount = { desktop.pages().size })
    val scope = rememberCoroutineScope()
    val currentPage = desktop.pageIndex(pager.currentPage)
    LaunchedEffect(desktop.pages().size) {
        if (pager.currentPage != currentPage) pager.scrollToPage(currentPage)
    }
    val folder = desktop.pages().flatten().firstOrNull { it.id() == folderId && it.isFolder }
    val launch: (Shortcut) -> Unit = { if (desktop.available(it)) onLaunch(it) }
    BackHandler {
        when {
            restore -> restore = false
            selected != null -> selected = null
            folderId != null -> folderId = null
            edit -> edit = false
            quickPanel -> quickPanel = false
            drawer -> { drawer = false; query = "" }
            else -> onExit()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("nova-home2")) {
        val fontScale = LocalDensity.current.fontScale
        val columns = minOf(desktop.columns(), NovaNavigation.columns(maxWidth.value.toInt(), fontScale)).coerceAtLeast(1)
        HomeWallpaper(desktop)
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically) {
                    Text(if (desktop.space() == HomeDesktop.Space.HIDDEN) "Hidden Space" else "Primary Space",
                        Modifier.weight(1f), style=MaterialTheme.typography.labelLarge)
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick={ menu=true }) { Icon(Icons.Default.MoreVert,"Home actions") }
                        DropdownMenu(menu,{menu=false}) {
                            DropdownMenuItem(text={Text("Apps")},onClick={drawer=true;menu=false})
                            DropdownMenuItem(text={Text("Quick Panel")},onClick={quickPanel=true;menu=false})
                            DropdownMenuItem(text={Text("Edit Home")},onClick={edit=true;menu=false})
                            DropdownMenuItem(text={Text("Classic Home")},onClick={menu=false;onClassic()})
                            DropdownMenuItem(text={Text("Lock Space")},onClick={menu=false;onLock()})
                            DropdownMenuItem(text={Text("Panic Exit")},onClick={menu=false;onPanic()})
                        }
                    }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                    Column(Modifier.fillMaxSize().combinedClickable(onClick={},onLongClick={edit=true})
                        .pointerInput(Unit) {
                            var distance=0f
                            detectVerticalDragGestures(onDragStart={distance=0f},onDragEnd={
                                if(distance < -80.dp.toPx()) drawer=true
                                if(distance > 80.dp.toPx()) quickPanel=true
                            },onVerticalDrag={change,amount->change.consume();distance+=amount})
                        }.padding(horizontal=20.dp)) {
                        if(page==0) HomeClock(Modifier.padding(top=28.dp,bottom=28.dp))
                        LazyVerticalGrid(GridCells.Fixed(columns),Modifier.weight(1f).fillMaxWidth(),
                            contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            items(desktop.visiblePage(desktop.pageIndex(page)),key={it.id()}) { item ->
                                if(item.isFolder) {
                                    Column(Modifier.fillMaxWidth().combinedClickable(onClick={folderId=item.id()},onLongClick={folderId=item.id()})
                                        .padding(horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                        Surface(shape=RoundedCornerShape(22.dp),color=Color.White.copy(alpha=.16f)) {
                                            Column(Modifier.size(desktop.iconSize().dp).padding(9.dp),verticalArrangement=Arrangement.SpaceEvenly) {
                                                item.apps().take(4).chunked(2).forEach { row ->
                                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                                        row.forEach { app -> Icon(novaAppSymbol(app),null,Modifier.size(18.dp),tint=Color.White) }
                                                    }
                                                }
                                            }
                                        }
                                        HomeLabel(item.label())
                                    }
                                } else HomeShortcut(item.app(),desktop.iconSize(),onClick={launch(item.app())},onLongClick={selected=item.app()})
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=8.dp),horizontalArrangement=Arrangement.Center) {
                    repeat(desktop.pages().size) { page ->
                        TextButton(onClick={scope.launch { if(desktop.reducedMotion()) pager.scrollToPage(page) else pager.animateScrollToPage(page) }},
                            contentPadding=PaddingValues(0.dp),modifier=Modifier.size(48.dp)) {
                            Icon(if(page==currentPage)Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,
                                "Home page ${page+1}",Modifier.size(8.dp),tint=Color.White)
                        }
                    }
                }
                Surface(Modifier.padding(horizontal=16.dp).fillMaxWidth(),shape=RoundedCornerShape(32.dp),color=Color.White.copy(alpha=.10f)) {
                    Row(Modifier.padding(horizontal=8.dp,vertical=16.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                        desktop.visibleDock().forEach { app ->
                            Box(Modifier.weight(1f)) { HomeShortcut(app,minOf(desktop.iconSize(),64),onClick={launch(app)},onLongClick={drawer=false;query="";folderId=null;selected=app}) }
                        }
                    }
                }
                TextButton(onClick={drawer=true},modifier=Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Default.KeyboardArrowUp,null,tint=Color.White);Text("Apps",color=Color.White)
                }
            }
        }
    }
    if(drawer) ModalBottomSheet(onDismissRequest={drawer=false;query=""},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding().padding(horizontal=20.dp)) {
            Text("Apps",style=MaterialTheme.typography.headlineMedium)
            OutlinedTextField(query,{query=it.take(256)},Modifier.fillMaxWidth().padding(vertical=16.dp),singleLine=true,
                placeholder={Text("Search applications")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(24.dp))
            BoxWithConstraints(Modifier.weight(1f)) {
                val columns=NovaNavigation.columns(maxWidth.value.toInt(),LocalDensity.current.fontScale)
                val apps=desktop.drawer(query)
                if(apps.isEmpty()) Text("No matching applications",Modifier.align(Alignment.Center))
                LazyVerticalGrid(GridCells.Fixed(columns),verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                    items(apps,key={it.name}) { app -> HomeShortcut(app,desktop.iconSize(),onClick={launch(app)},onLongClick={drawer=false;query="";folderId=null;selected=app}) }
                }
            }
        }
    }
    if(quickPanel) ModalBottomSheet(onDismissRequest={quickPanel=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Nova Quick Panel",style=MaterialTheme.typography.headlineSmall)
            Text("Controls for this NovaOS Space",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={quickPanel=false;onLock()},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.Lock,null);Spacer(Modifier.width(8.dp));Text("Lock Space")}
            OutlinedButton(onClick={quickPanel=false;onPanic()},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.PowerSettingsNew,null);Spacer(Modifier.width(8.dp));Text("Panic Exit")}
            quickActions.forEach { action ->
                OutlinedButton(onClick={quickPanel=false;action.onClick()},modifier=Modifier.fillMaxWidth(),enabled=action.enabled) {Text(action.label)}
            }
            TextButton(onClick={quickPanel=false;edit=true}) {Text("Home settings")}
        }
    }
    if(folder!=null) ModalBottomSheet(onDismissRequest={folderId=null}) {
        Column(Modifier.padding(20.dp)) {
            Text(folder.label(),style=MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=24.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                folder.apps().filter{desktop.available(it)}.forEach { app -> HomeShortcut(app,desktop.iconSize(),onClick={launch(app)},onLongClick={drawer=false;query="";folderId=null;selected=app}) }
            }
            var name by remember(folder.id()) { mutableStateOf(folder.label()) }
            OutlinedTextField(name,{name=it.take(64)},label={Text("Folder name")},singleLine=true)
            TextButton(onClick={runCatching{onChange(desktop.renameFolder(folder.id(),name))}.onSuccess{folderId=null}},enabled=name.isNotBlank()&&name.codePointCount(0,name.length)<=32&&!name.any{it.isISOControl()}) {Text("Rename")}
        }
    }
    selected?.let { app ->
        ModalBottomSheet(onDismissRequest={selected=null}) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(app.label,style=MaterialTheme.typography.headlineSmall)
                TextButton(onClick={selected=null;launch(app)}) {Text("Open")}
                TextButton(onClick={onChange(desktop.place(app,currentPage,0));selected=null}) {Text("Move to this Home page")}
                if(desktop.dock().contains(app)||desktop.dock().size<5)
                    TextButton(onClick={onChange(desktop.dock(app,0));selected=null}) {Text("Move to first dock position")}
                desktop.drawer("").filter{it!=app}.forEach { other ->
                    TextButton(onClick={onChange(desktop.folder("folder-${java.util.UUID.randomUUID().toString().take(8)}","Folder",listOf(app,other),currentPage,0));selected=null}) {Text("Create folder with ${other.label}")}
                }
                TextButton(onClick={onChange(desktop.remove(app));selected=null}) {Text("Remove Home shortcut")}
                TextButton(onClick={onChange(desktop.hide(app));selected=null}) {Text("Hide application")}
            }
        }
    }
    if(edit) ModalBottomSheet(onDismissRequest={edit=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Edit Home",style=MaterialTheme.typography.headlineMedium)
            Text("Wallpaper",style=MaterialTheme.typography.titleMedium)
            HomeDesktop.Wallpaper.values().forEach { wallpaper ->
                FilterChip(selected=desktop.wallpaper()==wallpaper,onClick={onChange(desktop.appearance(wallpaper,desktop.dim(),desktop.blur(),desktop.columns(),desktop.iconSize(),desktop.reducedMotion()))},label={Text(wallpaper.name.lowercase().replaceFirstChar{it.uppercase()})})
            }
            Text("Dimming · ${desktop.dim()}%")
            Slider(desktop.dim().toFloat(),{onChange(desktop.appearance(desktop.wallpaper(),it.toInt(),desktop.blur(),desktop.columns(),desktop.iconSize(),desktop.reducedMotion()))},valueRange=0f..80f)
            Text("Wallpaper blur · ${desktop.blur()}")
            Slider(desktop.blur().toFloat(),{onChange(desktop.appearance(desktop.wallpaper(),desktop.dim(),it.toInt(),desktop.columns(),desktop.iconSize(),desktop.reducedMotion()))},valueRange=0f..30f)
            Text("Grid · ${desktop.columns()} columns")
            Slider(desktop.columns().toFloat(),{onChange(desktop.appearance(desktop.wallpaper(),desktop.dim(),desktop.blur(),it.toInt(),desktop.iconSize(),desktop.reducedMotion()))},valueRange=3f..8f,steps=4)
            Text("Icon size · ${desktop.iconSize()}")
            Slider(desktop.iconSize().toFloat(),{onChange(desktop.appearance(desktop.wallpaper(),desktop.dim(),desktop.blur(),desktop.columns(),it.toInt(),desktop.reducedMotion()))},valueRange=48f..88f)
            Row(verticalAlignment=Alignment.CenterVertically) {Text("Reduce motion",Modifier.weight(1f));Switch(desktop.reducedMotion(),{onChange(desktop.appearance(desktop.wallpaper(),desktop.dim(),desktop.blur(),desktop.columns(),desktop.iconSize(),it))})}
            TextButton(onClick={onChange(desktop.addPage())},enabled=desktop.pages().size<8) {Text("Add Home page")}
            if(desktop.pages().size>1&&desktop.pages()[currentPage].isEmpty()) TextButton(onClick={onChange(desktop.removePage(currentPage))}) {Text("Remove empty page")}
            if(currentPage>0) TextButton(onClick={onChange(desktop.movePage(currentPage,currentPage-1))}) {Text("Move this page left")}
            desktop.hidden().filter{NovaNavigation.available(it,desktop.space()==HomeDesktop.Space.HIDDEN)}.forEach { app ->
                TextButton(onClick={onChange(desktop.show(app))}) {Text("Show ${app.label}")}
            }
            TextButton(onClick={restore=true}) {Text("Restore Home defaults")}
            TextButton(onClick={onClassic()}) {Text("Use Classic Home")}
        }
    }
    if(restore) GalleryAlertDialog(onDismissRequest={restore=false},
        title={Text("Restore this Home?")},
        text={Text("Reset this Space’s pages, folders, dock and wallpaper appearance. Your apps and encrypted media stay intact.")},
        confirmButton={TextButton(onClick={onChange(HomeDesktop.defaults(desktop.space()));restore=false}){Text("Restore")}},
        dismissButton={TextButton(onClick={restore=false}){Text("Cancel")}})
}

internal data class HomeQuickAction(val label:String,val enabled:Boolean=true,val onClick:()->Unit)

@Composable private fun HomeWallpaper(desktop:HomeDesktop) {
    val colors=when(desktop.wallpaper()) {
        HomeDesktop.Wallpaper.AURORA -> listOf(Color(0xFF081A36),Color(0xFF324C92),Color(0xFF147F83),Color(0xFF091B2A))
        HomeDesktop.Wallpaper.MIDNIGHT -> listOf(Color(0xFF080C21),Color(0xFF29315C),Color(0xFF341E55),Color(0xFF080D1A))
        HomeDesktop.Wallpaper.DUSK -> listOf(Color(0xFF3B2253),Color(0xFF7E4C76),Color(0xFFB16D6C),Color(0xFF26304B))
    }
    Box(Modifier.fillMaxSize().blur(desktop.blur().dp).background(Brush.linearGradient(colors)))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=desktop.dim()/100f)))
}

@Composable private fun HomeClock(modifier:Modifier=Modifier) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while(true) {now=ZonedDateTime.now();delay(1000)} }
    Column(modifier) {
        Text(now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),fontSize=56.sp,style=MaterialTheme.typography.displayLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),style=MaterialTheme.typography.bodyLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun HomeShortcut(app:Shortcut,size:Int,onClick:()->Unit,onLongClick:()->Unit) {
    Column(Modifier.widthIn(min=48.dp,max=140.dp).fillMaxWidth().combinedClickable(onClick=onClick,onLongClick=onLongClick).padding(horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            NovaAppIcon(app,minOf(size.dp,(maxWidth-8.dp).coerceAtLeast(32.dp)))
        }
        HomeLabel(app.label)
    }
}

@Composable private fun HomeLabel(label:String) {
    Text(label,Modifier.padding(top=8.dp),style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
}
