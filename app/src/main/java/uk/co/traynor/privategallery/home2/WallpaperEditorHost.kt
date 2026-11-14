package uk.co.traynor.privategallery.home2

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.home2.*
import uk.co.traynor.privategallery.core.vault.VaultItem

/** The pending system request retains metadata only; every readable presentation is retired. */
private class WallpaperPickerTicket {var delivered=false;var uri:Uri?=null}

@Composable internal fun WallpaperEditorHost(owner:WallpaperOwner,activityResumed:Boolean,initialSourceId:String?=null,onClose:()->Unit) {
 val context=LocalContext.current
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 var resumed by remember(owner){mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))}
 var active by remember(owner){mutableStateOf<WallpaperCoordinator?>(null)}
 var image by remember(owner){mutableStateOf<WallpaperImage?>(null)}
 var draft by remember(owner){mutableStateOf(Wallpaper())}
 var grid by remember(owner){mutableStateOf(false)}
 var items by remember(owner){mutableStateOf<List<VaultItem>>(emptyList())}
 var busy by remember(owner){mutableStateOf(false)}
 var error by remember(owner){mutableStateOf<String?>(null)}
 var startupFailure by remember(owner){mutableStateOf(false)}
 var pending by remember(owner){mutableStateOf<WallpaperPickerTicket?>(null)}
 var delivered by remember(owner){mutableStateOf<WallpaperPickerTicket?>(null)}
 var operation by remember(owner){mutableStateOf<Job?>(null)}
 var sourceConsumed by remember(owner){mutableStateOf(false)}
 val closeCallback by rememberUpdatedState(onClose)
 fun retire(){operation?.cancel();operation=null;image?.close();image=null;active?.close();active=null;items=emptyList();grid=false;busy=false}
 fun dismiss(){retire();pending=null;delivered=null;owner.close();closeCallback()}
 fun adopt(coordinator:WallpaperCoordinator,candidate:WallpaperImage):Boolean {
  var old:WallpaperImage?=null
  val published=active===coordinator&&owner.current()&&coordinator.access.publishIfCurrent {
   old=image;image=candidate;draft=draft.copy(image=null,zoom=1f,x=0f,y=0f);grid=false
  }
  old?.close()
  return published
 }
 fun startOperation(block:suspend CoroutineScope.()->Unit) {
  operation?.cancel()
  busy=true
  error=null
  // Completion checks the registered job before releasing busy state.
  val next=scope.launch(start=CoroutineStart.LAZY,block=block)
  operation=next
  next.start()
 }
 fun select(coordinator:WallpaperCoordinator,id:String) {
  startOperation {
   var candidate:WallpaperImage?=null
   try {
    withContext(Dispatchers.IO){candidate=coordinator.image(id)}
    ensureActive()
    if(adopt(coordinator,checkNotNull(candidate)))candidate=null
   }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(active===coordinator&&coordinator.current())error="Image unavailable. Choose another image."}
   finally{candidate?.close();if(active===coordinator&&operation===currentCoroutineContext()[Job])busy=false}
  }
 }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->
  val ticket=pending
  if(ticket!=null&&!ticket.delivered){ticket.delivered=true;ticket.uri=uri;delivered=ticket}
 }
 DisposableEffect(owner,lifecycle) {
  val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
   if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)resumed=true
   if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE||event==androidx.lifecycle.Lifecycle.Event.ON_STOP){resumed=false;if(pending!=null)retire()else dismiss()}
  }
  lifecycle.addObserver(observer)
  onDispose{lifecycle.removeObserver(observer);retire();owner.close()}
 }
 DisposableEffect(active) {
  val captured=active
  val retired=captured?.access?.onRetire{Handler(Looper.getMainLooper()).post{if(active===captured){if(pending!=null)retire()else dismiss()}}}
  onDispose{retired?.close()}
 }
 LaunchedEffect(owner) {withContext(Dispatchers.Main.immediate) {
  while(isActive){delay(250);if(!owner.current()){dismiss();break};if(pending==null&&active?.current()==false){dismiss();break}}
 }}
 LaunchedEffect(owner,activityResumed,resumed,delivered) {withContext(Dispatchers.Main.immediate) {
  if(!activityResumed||!resumed)return@withContext
  if(startupFailure)return@withContext
  if(!owner.current()){dismiss();return@withContext}
  if(pending!=null&&delivered==null)return@withContext
  if(active!=null&&delivered==null)return@withContext
  retire()
  val coordinator=runCatching{owner.open(context)}.getOrNull()?:run{dismiss();return@withContext}
  active=coordinator
  val applied=try{coordinator.wallpaper()}catch(_:Exception){retire();startupFailure=true;return@withContext}
  coordinator.access.publishIfCurrent{draft=applied}
  val result=delivered;delivered=null;pending=null
  if(result!=null) {
   val uri=result.uri
   if(uri!=null){startOperation {
    var candidate:WallpaperImage?=null
    try{withContext(Dispatchers.IO){candidate=coordinator.systemImage(uri)};ensureActive();if(adopt(coordinator,checkNotNull(candidate)))candidate=null}
    catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(active===coordinator&&coordinator.current())error="Image could not be opened. Existing wallpaper is unchanged."}
    finally{candidate?.close();if(active===coordinator&&operation===currentCoroutineContext()[Job])busy=false}
   }}
  }else if(!sourceConsumed&&initialSourceId!=null){sourceConsumed=true;select(coordinator,initialSourceId)}
 }}
 val coordinator=active
 if(activityResumed&&resumed&&startupFailure)PanicHomeAlertDialog(onDismissRequest={dismiss()},title={Text("Wallpapers")},
  text={Text("Wallpapers could not open. Existing encrypted settings have been preserved.",Modifier.testTag("wallpaper-open-error"))},
  confirmButton={TextButton(onClick={dismiss()}){Text("Close")}})
 if(activityResumed&&resumed&&coordinator!=null&&coordinator.current()) {
  PanicHomeDialog(onDismissRequest={dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {
   CompositionLocalProvider(LocalContext provides coordinator.space) {
    Surface(Modifier.fillMaxSize()) {
     if(grid)WallpaperVaultGrid(coordinator,items,busy,error,onBack={grid=false},onSelect={select(coordinator,it)})
     else WallpaperScreen(draft,image?.bitmap,if(owner.owner==EvidenceCatalog.Owner.HIDDEN)"Hidden Space"else "Primary Space",context.filesDir,busy,error,
      onChange={draft=it},onBuiltin={value->image?.close();image=null;draft=Wallpaper(builtin=value)},
      onChooseVault={grid=true;startOperation {
       try{val listed=withContext(Dispatchers.IO){coordinator.images()};ensureActive();if(active===coordinator&&owner.current())coordinator.access.publishIfCurrent{items=listed}}
       catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(active===coordinator&&coordinator.current())error="Vault images could not be loaded."}
       finally{if(active===coordinator&&operation===currentCoroutineContext()[Job])busy=false}
      }},
      onPickSystem={if(owner.current()&&pending==null){val ticket=WallpaperPickerTicket();pending=ticket;retire();try{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}catch(_:Exception){ticket.delivered=true;delivered=ticket}}},
      onApply={startOperation {
       try{withContext(Dispatchers.IO){coordinator.apply(draft,image)};ensureActive();if(active===coordinator&&owner.current()&&coordinator.current())dismiss()}
       catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(active===coordinator&&coordinator.current())error="Wallpaper could not be saved. Existing wallpaper is unchanged."}
       finally{if(active===coordinator&&operation===currentCoroutineContext()[Job])busy=false}
      }},onBack={dismiss()},allowSources=initialSourceId==null)
    }
   }
  }
 }
}

@Composable private fun WallpaperVaultGrid(coordinator:WallpaperCoordinator,items:List<VaultItem>,busy:Boolean,error:String?,onBack:()->Unit,onSelect:(String)->Unit) {
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("wallpaper-vault-grid")) {
  PreviewHeader("NovaOS Vault",if(coordinator.access.owner==EvidenceCatalog.Owner.HIDDEN)"Hidden Space"else "Primary Space",onBack)
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().testTag("wallpaper-vault-loading"))
  error?.let{Text(it,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)}
  if(!busy&&items.isEmpty()&&error==null)Text("No images in this Space.",Modifier.padding(24.dp))
  LazyVerticalGrid(GridCells.Adaptive(104.dp),contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   items(items,key={it.id}){item->WallpaperVaultTile(coordinator,item,!busy,onSelect)}
  }
 }
}
@Composable private fun WallpaperVaultTile(coordinator:WallpaperCoordinator,item:VaultItem,enabled:Boolean,onSelect:(String)->Unit) {
 var image by remember(coordinator,item.id){mutableStateOf<WallpaperImage?>(null)}
 DisposableEffect(coordinator,item.id){onDispose{image?.close();image=null}}
 LaunchedEffect(coordinator,item.id) {
  var candidate:WallpaperImage?=null
  try{
   withContext(Dispatchers.IO){candidate=coordinator.image(item.id,true)}
   withContext(Dispatchers.Main.immediate){ensureActive();coordinator.access.publishIfCurrent{image=candidate;candidate=null}}
  }
  catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){}finally{candidate?.close()}
 }
 Surface(Modifier.aspectRatio(1f).testTag("wallpaper-vault-item-${item.id}").clickable(enabled=enabled){onSelect(item.id)},shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
  image?.bitmap?.let{bitmap->if(!bitmap.isRecycled)Image(bitmap.asImageBitmap(),item.displayName,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
 }
}
