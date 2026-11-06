package uk.co.traynor.privategallery.home2

import android.app.Activity
import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.home2.NativeAppsBridge
import uk.co.traynor.privategallery.core.home2.SpaceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Existing preview components, backed exclusively by a captured real Space authority. */
@Composable internal fun IntegratedHome(access:EvidenceAccess,visible:Boolean,onLaunch:(String)->Unit,onLock:()->Unit,onPanic:()->Unit,onSwitch:(()->Unit)?,apps:NativeAppsBridge?,nativeThemeFallback:String?=null,onThemeCommitted:(String)->Unit={},onThemeConnection:(PreviewData?)->Unit={}) {
 val base=LocalContext.current
 val context=remember(access){SpaceContext(base,access)}
 val scope=rememberCoroutineScope()
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
 var live by remember(access){mutableStateOf(true)}
 var message by remember(access){mutableStateOf<String?>(null)}
 val models=remember(access){runCatching {
  val previous=runCatching{uk.co.traynor.privategallery.core.home.HomePreferencesStore(access,true).read()}.getOrNull()
  val names=mapOf("GALLERY" to "photos","VAULT" to "vault","BROWSER" to "browser-v1","PRIVATE_APPS" to "private-apps","EVIDENCE_LOCKER" to "evidence","CAMERA" to "camera","COLLECTIONS" to "collections","RECENTLY_DELETED" to "trash","SETTINGS" to "native-settings","FAVOURITES" to "favourites")
  // Preserve existing real Home ordering when a prior owner layout exists. No Preview import.
  val hasPrior=java.io.File(access.root,"home1710/preferences.enc").isFile
  val legacyOrder=if(hasPrior)previous?.layout?.visible()?.mapNotNull{names[it.name]}else null
  val data=PreviewData(context,apps,onSwitch,legacyOrder,nativeThemeFallback){theme->if(context.current())onThemeCommitted(theme)}
  val widgets=WidgetData(context)
  val mind=MindData(context)
  val history=NovaSearchHistory(context)
  HomeModels(data,widgets,mind,history)
 }}
 val data=models.getOrNull()?.data
 DisposableEffect(access,data){
  if(data!=null&&context.current()){onThemeConnection(data);onThemeCommitted(data.state.theme)}
  onDispose{onThemeConnection(null)}
 }
 val application=base.applicationContext as Application
 val audio=remember(access){MindAudio(application){context.current()&&lifecycle.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)}}
 val video=remember(access){NovaVideoModel(application)}
 val pool=remember(access){PreviewBrowserModel(apps,onLaunch){context.current()}.also{it.currentProfile=context.spaceId}}
 DisposableEffect(access){
  val retire=access.onRetire{android.os.Handler(android.os.Looper.getMainLooper()).post{live=false;audio.release();video.close();pool.clear();context.close()}}
  onDispose{retire.close();audio.release();video.close();pool.clear();context.close()}
 }
 val pending=remember(access){java.util.concurrent.atomic.AtomicBoolean(false)}
 var chosenUri by remember(access){mutableStateOf<android.net.Uri?>(null)}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->
  if(pending.getAndSet(false)&&context.current())chosenUri=uri
 }
 LaunchedEffect(chosenUri,visible){
  val uri=chosenUri
  if(uri!=null&&visible&&context.current()){
   runCatching{
    val bytes=withContext(Dispatchers.IO){
     val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
     context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,bounds)}
     require(bounds.outWidth in 1..30000&&bounds.outHeight in 1..30000)
     val options=BitmapFactory.Options().apply{var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2;inSampleSize=sample}
     val bitmap=context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,options)}?:error("Image unavailable")
     try{java.io.ByteArrayOutputStream().use{out->check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,out));out.toByteArray()}}finally{bitmap.recycle()}
    }
    try{val id=withContext(Dispatchers.IO){context.writeWallpaper(bytes)};if(context.current())models.getOrThrow().data.let{data->data.change{it.copy(wallpaper=it.wallpaper.copy(image=id,imageRevision=System.currentTimeMillis()))};if(data.error==null)withContext(Dispatchers.IO){context.pruneWallpapers(setOfNotNull(data.profile.wallpaper.image))}}}finally{bytes.fill(0)}
   }.onFailure{if(context.current())message="Wallpaper could not be imported. Existing wallpaper is unchanged."}
   chosenUri=null
  }
 }
 LaunchedEffect(visible){if(visible&&context.current())models.getOrNull()?.data?.refreshInstalled()}
 DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE||event==androidx.lifecycle.Lifecycle.Event.ON_STOP){audio.pause();video.pause()}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
 if(!visible||!live||!context.current())return
 val value=models.getOrElse{
  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)){Text("Home could not open. Existing encrypted data has been preserved.");TextButton(onClick=onLock){Text("Lock Space")}}
  return
 }
 CompositionLocalProvider(LocalContext provides context){
  NovaPreview(value.data,pool,value.mind,audio,value.widgets,value.history,video,
   onPickImage={if(context.current()){pending.set(true);picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}},
   onOpen={},filesDir=context.filesDir,onNative={id->if(context.current()){value.data.shell.touchInternal(context.spaceId,id);onLaunch(id)}},onLock=onLock,onPanic=onPanic)
  message?.let{PanicHomeAlertDialog(onDismissRequest={message=null},title={Text("NovaOS")},text={Text(it)},confirmButton={TextButton(onClick={message=null}){Text("OK")}})}
 }
}
private data class HomeModels(val data:PreviewData,val widgets:WidgetData,val mind:MindData,val history:NovaSearchHistory)
