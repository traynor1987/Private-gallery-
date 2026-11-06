package uk.co.traynor.privategallery.home2

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.home2.NativeAppsBridge
import uk.co.traynor.privategallery.core.home2.SpaceContext

/** Existing preview components, backed exclusively by a captured real Space authority. */
@Composable internal fun IntegratedHome(access:EvidenceAccess,visible:Boolean,onLaunch:(String)->Unit,onLock:()->Unit,onPanic:()->Unit,onSwitch:(()->Unit)?,apps:NativeAppsBridge?,nativeThemeFallback:String?=null,onThemeCommitted:(String)->Unit={},onThemeConnection:(PreviewData?)->Unit={},wallpaperOwnerFactory:(()->uk.co.traynor.privategallery.core.home2.WallpaperOwner?)?=null,externalRecentsRequest:Int=0,onRecentsConsumed:()->Unit={}) {
 val base=LocalContext.current
 val context=remember(access){SpaceContext(base,access)}
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
 var backupOwner by remember{mutableStateOf<uk.co.traynor.privategallery.core.home2.WallpaperOwner?>(null)}
 backupOwner?.let{owner->BackupHost(owner,visible,onClose={backupOwner=null},onSettingsRestored=onLock)}
 var wallpaperOwner by remember{mutableStateOf<uk.co.traynor.privategallery.core.home2.WallpaperOwner?>(null)}
 wallpaperOwner?.let{owner->WallpaperEditorHost(owner,visible,onClose={wallpaperOwner=null;if(context.current())models.getOrNull()?.data?.refreshWallpaper()})}
 LaunchedEffect(visible){if(visible&&context.current())models.getOrNull()?.data?.let{it.refreshInstalled();it.refreshWallpaper()}}
 DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE||event==androidx.lifecycle.Lifecycle.Event.ON_STOP){audio.pause();video.pause()}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
 NovaHomeStatusBarPolicy(visible&&live&&context.current())
 if(!visible||!live||!context.current())return
 val value=models.getOrElse{
  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)){Text("Home could not open. Existing encrypted data has been preserved.");TextButton(onClick=onLock){Text("Lock Space")}}
  return
 }
 CompositionLocalProvider(LocalContext provides context){
  NovaPreview(value.data,pool,value.mind,audio,value.widgets,value.history,video,
   onBackup={if(context.current())backupOwner=if(wallpaperOwnerFactory!=null)wallpaperOwnerFactory.invoke()else uk.co.traynor.privategallery.core.home2.WallpaperOwner(access.owner,{context.current()},{access},false)},
   onPickImage={if(context.current())wallpaperOwner=if(wallpaperOwnerFactory!=null)wallpaperOwnerFactory.invoke()else uk.co.traynor.privategallery.core.home2.WallpaperOwner(access.owner,{context.current()},{access},false)},
   onOpen={},filesDir=context.filesDir,onNative={id->if(context.current()){value.data.shell.touchInternal(context.spaceId,id);onLaunch(id)}},onLock=onLock,onPanic=onPanic,externalRecentsRequest=externalRecentsRequest,onRecentsConsumed=onRecentsConsumed)
  message?.let{PanicHomeAlertDialog(onDismissRequest={message=null},title={Text("NovaOS")},text={Text(it)},confirmButton={TextButton(onClick={message=null}){Text("OK")}})}
 }
}
private data class HomeModels(val data:PreviewData,val widgets:WidgetData,val mind:MindData,val history:NovaSearchHistory)
