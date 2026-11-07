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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.backup.*
import uk.co.traynor.privategallery.core.crypto.RecoveryKey
import uk.co.traynor.privategallery.core.home2.WallpaperOwner

private class BackupPickerTicket(val restore:Boolean,val tree:Boolean=false){var delivered=false;var uri:Uri?=null}
private data class BackupNotice(val message: String, val failed: Boolean = false)

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
 var automaticTree by remember(owner){mutableStateOf<Uri?>(null)}
 var automaticArmed by remember(owner){mutableStateOf(false)}
 var automaticDays by remember(owner){mutableIntStateOf(1)}
 var restoring by remember(owner){mutableStateOf(false)}
 var recovery by remember(owner){mutableStateOf("")}
 var saved by remember(owner){mutableStateOf(false)}
 var plan by remember(owner){mutableStateOf<BackupCoordinator.RestorePlan?>(null)}
 var confirmSettings by remember(owner){mutableStateOf(false)}
 var busy by remember(owner){mutableStateOf(false)}
 var notice by remember(owner){mutableStateOf<BackupNotice?>(null)}
 var history by remember(owner){mutableStateOf<BackupCoordinator.HistorySnapshot?>(null)}
 var storage by remember(owner){mutableStateOf<Long?>(null)}
 var job by remember(owner){mutableStateOf<Job?>(null)}
 var charging by remember(owner){mutableStateOf(true)}
 var wifi by remember(owner){mutableStateOf(true)}
 var schedule by remember(owner){mutableStateOf("")}
 val closeCallback by rememberUpdatedState(onClose)
 fun retire(){job?.cancel();job=null;plan?.close();plan=null;active?.close();active=null;recovery="";selected=null;automaticTree=null;saved=false;notice=null;history=null;storage=null;busy=false}
 fun dismiss(){retire();pending=null;delivered=null;owner.close();closeCallback()}
 fun operation(secret:CharArray?=null,refreshStorage:Boolean=false,block:suspend (BackupCoordinator)->String){
  val captured=active?:return;if(busy)return;busy=true;notice=null
  job=scope.launch {
   try{
    val (message,snapshot,updatedStorage)=withContext(Dispatchers.IO){
     val message=block(captured)
     Triple(message,captured.historySnapshot(),if(refreshStorage)runCatching{captured.storageBytes()}.getOrNull()else null)
    }
    ensureActive()
    captured.access.publishIfCurrent{if(active===captured){notice=BackupNotice(message);history=snapshot;if(refreshStorage)storage=updatedStorage;busy=false}}
   }
   catch(cancelled:CancellationException){throw cancelled}
   catch(_:Exception){
    val failedHistory=withContext(Dispatchers.IO){runCatching{captured.historySnapshot()}.getOrNull()}
    if(active===captured&&captured.current())captured.access.publishIfCurrent{
     notice=BackupNotice("The operation failed or was interrupted. Existing media is preserved. The destination document may be incomplete.",failed=true)
     history=failedHistory;busy=false
    }
   }
  }
  job?.invokeOnCompletion{secret?.fill('\u0000')}
 }
 val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->pending?.takeIf{!it.delivered&&!it.restore&&!it.tree}?.let{it.delivered=true;it.uri=uri;delivered=it}}
 val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->pending?.takeIf{!it.delivered&&it.restore&&!it.tree}?.let{it.delivered=true;it.uri=uri;delivered=it}}
 val tree=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->pending?.takeIf{!it.delivered&&it.tree}?.let{it.delivered=true;it.uri=uri;delivered=it}}
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
  if(coordinator.owner=="primary"){
   val preferences=context.getSharedPreferences("nova-backup-due",0)
   if(delivered==null){
    charging=preferences.getBoolean("charging",true);wifi=preferences.getBoolean("wifi",true)
    automaticDays=preferences.getInt("days",1).takeIf{it==1||it==7}?:1
   }
   automaticArmed=runCatching{AutomaticBackupSettings.armed(coordinator.space)}.getOrDefault(false)
   schedule=BackupSchedule.status(context)
  }
  val ticket=delivered;pending=null;delivered=null
  if(ticket?.uri!=null){
   if(ticket.tree)automaticTree=ticket.uri else {selected=ticket.uri;restoring=ticket.restore}
   if(!ticket.restore){val key=RecoveryKey.generate();try{recovery=key.concatToString()}finally{key.fill('\u0000')}}
  }
 }
 LaunchedEffect(active){
  val coordinator=active?:return@LaunchedEffect
  val currentJob=job
  val snapshot=try {
   withContext(Dispatchers.IO){coordinator.historySnapshot() to coordinator.storageBytes()}
  }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
  withContext(Dispatchers.Main.immediate){
   if(active===coordinator&&job===currentJob)coordinator.access.publishIfCurrent{
    if(snapshot==null)notice=BackupNotice("Encrypted storage could not be read. Existing data is preserved.",failed=true)
    else {history=snapshot.first;storage=snapshot.second}
   }
  }
 }
 if(!visible||!resumed||active==null)return
 val coordinator=active!!
 PanicHomeDialog(
  onDismissRequest={dismiss()},
  properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn),
 ){
  BackupDashboardTheme{
  Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
   Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-backup"),horizontalAlignment=Alignment.CenterHorizontally){
    BackupTopBar(busy,onClose={dismiss()})
    if(busy){
     LinearProgressIndicator(Modifier.fillMaxWidth().testTag("backup-progress"))
     Text("Working securely…",Modifier.padding(6.dp),style=MaterialTheme.typography.labelMedium)
    }
    Column(
     Modifier.widthIn(max=840.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(top=8.dp,bottom=20.dp),
     verticalArrangement=Arrangement.spacedBy(14.dp),
    ){
     BackupOverview(
      history,busy,
      onCreate={plan?.close();plan=null;pending=BackupPickerTicket(false);create.launch("nova-backup.novabak")},
      onRestore={plan?.close();plan=null;pending=BackupPickerTicket(true);open.launch(arrayOf("application/octet-stream","*/*"))},
     )
     notice?.let{BackupOperationNotice(it.message,it.failed)}
     if(selected!=null&&plan==null&&(restoring||recovery.isNotEmpty())){
      BackupSection(if(restoring)"Unlock archive"else "Protect your recovery key"){
       if(restoring){
        Text("Enter the recovery key saved when you created this archive.",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(recovery,{recovery=it.take(1024)},label={Text("Recovery key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy&&recovery.isNotBlank(),onClick={
         val secret=recovery.toCharArray();recovery="";val uri=selected!!
         operation(secret){captured->
          val candidate=captured.preview(SafBackupProvider(context,uri,captured.access),secret)
          withContext(Dispatchers.Main.immediate){
           if(active===captured&&captured.access.publishIfCurrent{plan=candidate})Unit else candidate.close()
          }
          "Archive authenticated. Review the restore choices below."
         }
        }){Text("Validate and preview")}
       }else{
        Text("Save this recovery key outside this device. It is required on a replacement device and cannot be recovered by NovaOS.")
        SelectionContainer{Text(recovery,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.secondary)}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(saved,{saved=it},enabled=!busy);Text("I saved the recovery key")}
        Button(enabled=!busy&&saved&&recovery.isNotBlank(),onClick={
         val secret=recovery.toCharArray();recovery="";val uri=selected!!
         operation(secret){captured->
          val receipt=captured.export(SafBackupProvider(context,uri,captured.access),secret)
          "Destination verified · ${receipt.items} media items · ${backupSizeDisplay(receipt.bytes)}\nSHA-256 ${receipt.sha256}"
         }
        }){Text("Encrypt and verify destination")}
       }
      }
     }
     plan?.let{candidate->
      var restoreFavourite by remember(candidate){mutableStateOf(false)}
      BackupSection("Restore preview"){
       BackupDetail("Media in this archive",backupSizeDisplay(candidate.bytes))
       BackupDetail("Created",backupDate(candidate.created))
       Text("${candidate.media} media items · ${candidate.collections} collections · ${candidate.settings} settings records · ${candidate.apps} saved apps")
       Text(
        "Media and collections are added as new copies with fresh identifiers, including duplicate filenames. Existing items are preserved. Backed-up Recently Deleted items return as active copies, preventing expiry from deleting recovered data.",
        style=MaterialTheme.typography.bodyMedium,
       )
       Text(
        "Restore saved app definitions before settings to reconnect their Home shortcuts. Missing shortcuts are omitted. URL queries and fragments are excluded, so saved entrypoints may differ.",
        style=MaterialTheme.typography.bodyMedium,
       )
       Text(
        "Settings replacement is confirmed separately. Existing favourites, gallery sort and retention are preserved. Repeating a media restore creates another copy.",
        style=MaterialTheme.typography.bodyMedium,
       )
       if(candidate.names.isNotEmpty()){
        var namesExpanded by remember(candidate){mutableStateOf(false)}
        TextButton(onClick={namesExpanded=!namesExpanded}){Text(if(namesExpanded)"Hide archive items"else "Show archive items")}
        if(namesExpanded)candidate.names.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
       }
       Row(verticalAlignment=Alignment.CenterVertically){
        Checkbox(restoreFavourite,{restoreFavourite=it},enabled=!busy)
        Text("Use the archived favourite collection only if this Space has none")
       }
       Button(enabled=!busy&&(candidate.media>0||candidate.collections>0),onClick={
        operation(refreshStorage=true){captured->"Restored ${captured.restoreMedia(candidate,restoreFavourite)} media copies and their collections. Existing originals preserved."}
       }){Text("Add media and collections")}
       OutlinedButton(enabled=!busy&&candidate.apps>0,onClick={
        operation{captured->"Added ${captured.restoreApps(candidate)} app definitions with fresh identifiers. Sign in again when you open them; existing apps were preserved."}
       }){Text("Add saved app definitions")}
       OutlinedButton(enabled=!busy&&candidate.settings>0,onClick={confirmSettings=true}){Text("Replace supported settings…")}
      }
     }
     BackupOverviewDetails(history,storage)
     if(coordinator.owner=="primary"){
      BackupSection("Automatic local backup"){
       Text(if(automaticArmed)"Enabled"else "Off",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
       Text(
        if(automaticArmed)"$schedule\nA due backup runs when Home is foreground, this Space is unlocked and the chosen conditions are met. Lock, Panic or background cancels active work. No archive is created while locked."
        else "Choose a folder and save its recovery key to enable scheduled backups. The folder and key are kept encrypted in this Space and excluded from portable backups.",
        style=MaterialTheme.typography.bodyMedium,
       )
       Row(verticalAlignment=Alignment.CenterVertically){Checkbox(charging,{charging=it},enabled=!busy,modifier=Modifier.testTag("backup-charging"));Text("Only while charging")}
       Row(verticalAlignment=Alignment.CenterVertically){Checkbox(wifi,{wifi=it},enabled=!busy,modifier=Modifier.testTag("backup-wifi"));Text("Only on Wi-Fi")}
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf("Daily" to 1,"Weekly" to 7).forEach{(label,days)->FilterChip(automaticDays==days,{automaticDays=days},label={Text(label)},enabled=!busy)}
       }
       OutlinedButton(enabled=!busy,onClick={plan?.close();plan=null;pending=BackupPickerTicket(false,true);tree.launch(null)}){Text("Choose automatic backup folder")}
       if(automaticTree!=null){
        Text("Save this recovery key outside this device. It protects future automatic backups in the selected folder.")
        SelectionContainer{Text(recovery,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.secondary)}
        Row(verticalAlignment=Alignment.CenterVertically){
         Checkbox(saved,{saved=it},enabled=!busy,modifier=Modifier.testTag("automatic-key-saved"));Text("I saved the automatic backup recovery key")
        }
        Button(enabled=!busy&&saved&&recovery.isNotBlank(),onClick={
         val secret=recovery.toCharArray();recovery="";val destination=automaticTree!!;val days=automaticDays;val charge=charging;val network=wifi
         operation(secret){captured->
          AutomaticBackupSettings.arm(context,captured.space,destination,secret,days,charge,network)
          withContext(Dispatchers.Main.immediate){captured.access.publishIfCurrent{automaticTree=null;saved=false;automaticArmed=true;schedule=BackupSchedule.status(context)}}
          "Automatic backups enabled. Keep the recovery key safe; future files are verified after writing."
         }
        }){Text("Enable automatic backup")}
       }
       if(automaticArmed){
        OutlinedButton(enabled=!busy,onClick={
         val days=automaticDays;val charge=charging;val network=wifi
         operation{captured->
          checkNotNull(AutomaticBackupSettings.read(captured.space)).use{config->AutomaticBackupSettings.arm(context,captured.space,config.tree,config.recovery.copyOf(),days,charge,network)}
          withContext(Dispatchers.Main.immediate){captured.access.publishIfCurrent{schedule=BackupSchedule.status(context)}}
          "Automatic schedule updated."
         }
        }){Text("Save schedule restrictions")}
        TextButton(enabled=!busy,onClick={
         operation{captured->
          AutomaticBackupSettings.disable(context,captured.space)
          withContext(Dispatchers.Main.immediate){captured.access.publishIfCurrent{automaticArmed=false;schedule=BackupSchedule.status(context)}}
          "Automatic backups disabled."
         }
        }){Text("Turn automatic backup off")}
       }
      }
     }
     BackupAdvancedDetails(coordinator.owner,history?.events.orEmpty())
    }
   }
  }
  if(confirmSettings)PanicHomeAlertDialog(
   onDismissRequest={confirmSettings=false},
   title={Text("Replace supported settings?")},
   text={
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text("The archive replaces the included Home, wallpaper, navigation, notes and app preferences in this Space.")
     Text("Media and authentication settings are preserved. This Space locks after the restore so each screen reloads the restored settings.")
    }
   },
   confirmButton={
    TextButton(onClick={
     confirmSettings=false
     val candidate=plan?:return@TextButton
     operation{captured->
      captured.restoreSettings(candidate)
      withContext(Dispatchers.Main.immediate){dismiss();onSettingsRestored()}
      "Supported settings restored."
     }
    }){Text("Replace settings")}
   },
   dismissButton={TextButton(onClick={confirmSettings=false}){Text("Cancel")}},
  )
  }
 }
}
