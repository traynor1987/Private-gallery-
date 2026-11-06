package uk.co.traynor.privategallery.home2

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.backup.*
import uk.co.traynor.privategallery.core.crypto.RecoveryKey
import uk.co.traynor.privategallery.core.home2.WallpaperOwner

private class BackupPickerTicket(val restore:Boolean){var delivered=false;var uri:Uri?=null}

/** Pending SAF requests retain only a ticket and URI. Fresh same-auth authority is acquired on resume. */
@Composable internal fun BackupHost(owner:WallpaperOwner,visible:Boolean,onClose:()->Unit,onSettingsRestored:()->Unit={}){
 val context=LocalContext.current
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 var resumed by remember(owner){mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))}
 var active by remember(owner){mutableStateOf<BackupCoordinator?>(null)}
 var pending by remember(owner){mutableStateOf<BackupPickerTicket?>(null)}
 var delivered by remember(owner){mutableStateOf<BackupPickerTicket?>(null)}
 var selected by remember(owner){mutableStateOf<Uri?>(null)}
 var restoring by remember(owner){mutableStateOf(false)}
 var recovery by remember(owner){mutableStateOf("")}
 var saved by remember(owner){mutableStateOf(false)}
 var plan by remember(owner){mutableStateOf<BackupCoordinator.RestorePlan?>(null)}
 var confirmSettings by remember(owner){mutableStateOf(false)}
 var busy by remember(owner){mutableStateOf(false)}
 var message by remember(owner){mutableStateOf<String?>(null)}
 var history by remember(owner){mutableStateOf("")}
 var storage by remember(owner){mutableStateOf(0L)}
 var job by remember(owner){mutableStateOf<Job?>(null)}
 var charging by remember(owner){mutableStateOf(true)}
 var wifi by remember(owner){mutableStateOf(true)}
 var schedule by remember(owner){mutableStateOf("")}
 val closeCallback by rememberUpdatedState(onClose)
 fun retire(){job?.cancel();job=null;plan?.close();plan=null;active?.close();active=null;recovery="";selected=null;saved=false;message=null;history="";busy=false}
 fun dismiss(){retire();pending=null;delivered=null;owner.close();closeCallback()}
 fun operation(secret:CharArray?=null,block:suspend (BackupCoordinator)->String){
  val captured=active?:return;if(busy)return;busy=true;message=null
  job=scope.launch {
   try{val result=withContext(Dispatchers.IO){block(captured) to captured.history()};ensureActive();captured.access.publishIfCurrent{if(active===captured){message=result.first;history=result.second;busy=false}}}
   catch(cancelled:CancellationException){throw cancelled}
   catch(_:Exception){val failedHistory=withContext(Dispatchers.IO){runCatching{captured.history()}.getOrDefault("")};if(active===captured&&captured.current())captured.access.publishIfCurrent{message="Operation failed or was interrupted. Existing media is preserved. A destination document may be incomplete; do not rely on it.";history=failedHistory;busy=false}}
  }
  job?.invokeOnCompletion{secret?.fill('\u0000')}
 }
 val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->pending?.takeIf{!it.delivered&&!it.restore}?.let{it.delivered=true;it.uri=uri;delivered=it}}
 val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->pending?.takeIf{!it.delivered&&it.restore}?.let{it.delivered=true;it.uri=uri;delivered=it}}
 DisposableEffect(owner,lifecycle){
  val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
   if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)resumed=true
   if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE||event==androidx.lifecycle.Lifecycle.Event.ON_STOP){resumed=false;if(pending!=null)retire()else dismiss()}
  };lifecycle.addObserver(observer)
  onDispose{lifecycle.removeObserver(observer);retire();owner.close()}
 }
 DisposableEffect(active){
  val captured=active
  val retirement=captured?.access?.onRetire{android.os.Handler(android.os.Looper.getMainLooper()).post{if(active===captured){if(pending!=null)retire()else dismiss()}}}
  onDispose{retirement?.close()}
 }
 LaunchedEffect(owner){while(isActive){delay(250);if(!owner.current()){dismiss();break}}}
 LaunchedEffect(owner,visible,resumed,delivered){
  if(!visible||!resumed||!owner.current())return@LaunchedEffect
  if(pending!=null&&delivered==null)return@LaunchedEffect
  if(active!=null&&delivered==null)return@LaunchedEffect
  retire();val coordinator=runCatching{owner.openBackup(context)}.getOrNull()?:run{dismiss();return@LaunchedEffect};active=coordinator
  try {val values=withContext(Dispatchers.IO){coordinator.history() to coordinator.storageBytes()};coordinator.access.publishIfCurrent{history=values.first;storage=values.second}}
  catch(_:Exception){message="Encrypted storage could not be read. Existing data is preserved."}
  if(coordinator.owner=="primary")schedule=BackupSchedule.status(context)
  val ticket=delivered;pending=null;delivered=null
  if(ticket?.uri!=null){selected=ticket.uri;restoring=ticket.restore;if(!restoring){val key=RecoveryKey.generate();try{recovery=key.concatToString()}finally{key.fill('\u0000')}}}
 }
 if(!visible||!resumed||active==null)return
 val coordinator=active!!
 PanicHomeDialog(onDismissRequest={dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)){
  Surface(Modifier.fillMaxSize()){
   Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()).testTag("nova-backup"),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Row(Modifier.fillMaxWidth()){Text("Nova Backup",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));TextButton(onClick={dismiss()}){Text(if(busy)"Cancel"else "Close")}}
    Text("Encrypted, portable backup of this Space",style=MaterialTheme.typography.titleMedium)
    Text(history)
    Text("Media storage: $storage bytes. Included: encrypted active and Recently Deleted images, videos and documents; collection names/covers/membership/pins, media dates, ${BackupPolicy.mediaEditDescription(coordinator.owner)}; Home layout/wallpaper, navigation, Quick Notes, Mind, saved app definitions, supported app saves and preferences.")
    Text(BackupPolicy.unsupported,style=MaterialTheme.typography.bodySmall)
    backupProviders.forEach{option->Text(option.label+if(option.enabled)" · available"else " · disabled",style=MaterialTheme.typography.bodySmall)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(enabled=!busy,onClick={plan?.close();plan=null;pending=BackupPickerTicket(false);create.launch("nova-backup.novabak")}){Text("Create backup")}
     OutlinedButton(enabled=!busy,onClick={plan?.close();plan=null;pending=BackupPickerTicket(true);open.launch(arrayOf("application/octet-stream","*/*"))}){Text("Restore")}
    }
    if(selected!=null&&plan==null){
     if(restoring){
      OutlinedTextField(recovery,{recovery=it.take(1024)},label={Text("Recovery key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
      Button(enabled=!busy&&recovery.isNotBlank(),onClick={val secret=recovery.toCharArray();recovery="";val uri=selected!!;operation(secret){captured->val candidate=captured.preview(SafBackupProvider(context,uri,captured.access),secret);withContext(Dispatchers.Main.immediate){if(active===captured&&captured.access.publishIfCurrent{plan=candidate})Unit else candidate.close()};"Archive authenticated. Review the restore choices below."}}){Text("Validate and preview")}
     }else{
      Text("Save this recovery key outside this device. It is required on a replacement device and cannot be recovered by NovaOS.")
      SelectionContainer{Text(recovery,style=MaterialTheme.typography.bodyLarge)}
      Row{Checkbox(saved,{saved=it},enabled=!busy);Text("I saved the recovery key")}
      Button(enabled=!busy&&saved&&recovery.isNotBlank(),onClick={val secret=recovery.toCharArray();recovery="";val uri=selected!!;operation(secret){captured->val receipt=captured.export(SafBackupProvider(context,uri,captured.access),secret);"Verified actual destination bytes: ${receipt.items} media items, ${receipt.bytes} bytes. SHA-256 ${receipt.sha256}"}}){Text("Encrypt and verify destination")}
     }
    }
    plan?.let{candidate->
     var restoreFavourite by remember(candidate){mutableStateOf(false)}
     Text("Restore preview · ${candidate.media} media items · ${candidate.collections} collections · ${candidate.bytes} bytes · ${candidate.settings} settings records · ${candidate.apps} saved apps",style=MaterialTheme.typography.titleMedium)
     Text("Created ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(candidate.created))}")
     candidate.names.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
     Text("Media and collections are added as new copies with fresh identifiers, including duplicate filenames. Existing items are preserved. Backed-up Recently Deleted items return as active copies, preventing expiry from deleting recovered data. Settings replacement is a separate confirmed transaction. Restore saved app definitions before settings to reconnect their Home shortcuts; missing app shortcuts are omitted. URL queries/fragments are excluded, so saved entrypoints may differ. Existing favourite, gallery sort and retention choices are preserved. The checkbox may fill an unset favourite. Repeating media restore creates another copy.")
     Row{Checkbox(restoreFavourite,{restoreFavourite=it},enabled=!busy);Text("Use the archived favourite collection only if this Space has none") }
     Button(enabled=!busy&&(candidate.media>0||candidate.collections>0),onClick={operation{captured->"Restored ${captured.restoreMedia(candidate,restoreFavourite)} media copies and their collections. Existing originals preserved."}}){Text("Add media and collections")}
     OutlinedButton(enabled=!busy&&candidate.apps>0,onClick={operation{captured->"Added ${captured.restoreApps(candidate)} app definitions with fresh identifiers. Sign in again when you open them; existing apps were preserved."}}){Text("Add saved app definitions")}
     OutlinedButton(enabled=!busy&&candidate.settings>0,onClick={confirmSettings=true}){Text("Replace supported settings…")}
    }
    if(coordinator.owner=="primary"){
     HorizontalDivider();Text("Scheduled due checks",style=MaterialTheme.typography.titleMedium)
     Text("$schedule\nSensitive backups require an unlocked foreground session and your recovery key. No automatic archive is created while locked.")
     Row{Checkbox(charging,{charging=it},enabled=!busy);Text("Only while charging")};Row{Checkbox(wifi,{wifi=it},enabled=!busy);Text("Only on Wi-Fi")}
     Row{listOf("Off" to 0,"Daily" to 1,"Weekly" to 7).forEach{(label,days)->TextButton(enabled=!busy,onClick={runCatching{BackupSchedule.configure(context,days,charging,wifi)}.onSuccess{schedule=BackupSchedule.status(context)}.onFailure{message="Android could not save the schedule."}}){Text(label)}}}
    }
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    message?.let{Text(it)}
   }
  }
 }
 if(confirmSettings)PanicHomeAlertDialog(onDismissRequest={confirmSettings=false},title={Text("Replace supported settings?")},text={Text("This replaces the included Home, wallpaper, navigation, notes and app preferences in this Space. Media and authentication settings are preserved. This Space locks after a successful restore so every screen reloads the restored settings.")},confirmButton={TextButton(onClick={confirmSettings=false;val candidate=plan?:return@TextButton;operation{captured->captured.restoreSettings(candidate);withContext(Dispatchers.Main.immediate){dismiss();onSettingsRestored()};"Supported settings restored."}}){Text("Replace settings")}},dismissButton={TextButton(onClick={confirmSettings=false}){Text("Cancel")}})
}
