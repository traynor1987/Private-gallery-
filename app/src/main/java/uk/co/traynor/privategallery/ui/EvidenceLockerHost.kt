package uk.co.traynor.privategallery.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.evidence.*

/** The only state surviving an explicit system picker is opaque IDs/digests/URI and sealed ciphertext. */
private class EvidencePickerTicket(
 val kind:String,val incidentIds:List<String> = emptyList(),val evidenceIds:List<String> = emptyList(),
 val digest:String="",val stage:EvidenceOwnerSession.CipherStage?=null,val receipt:EvidenceExportReceipt?=null,
 val source:Uri?=null,val cipherDigest:String="",val mode:String="VPN",
) {
 val delivered=AtomicBoolean();var uri:Uri?=null;var accepted=false
}
private class EvidenceArchiveRequest(val source:Uri,val destination:Uri?=null,val cipherDigest:String?=null)

@Composable internal fun EvidenceLockerHost(owner:EvidenceOwnerSession,activityResumed:Boolean,
 preparePermission:(Context)->android.content.Intent?={android.net.VpnService.prepare(it)},onClose:()->Unit) {
 val context=LocalContext.current.applicationContext
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 var resumed by remember(owner){mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))}
 var controller by remember(owner){mutableStateOf<EvidenceController?>(null)}
 var pending by remember(owner){mutableStateOf<EvidencePickerTicket?>(null)}
 var delivered by remember(owner){mutableStateOf<EvidencePickerTicket?>(null)}
 var aiIds by remember(owner){mutableStateOf<List<String>?>(null)}
 var aiMode by remember(owner){mutableStateOf<String?>(null)}
 var exportIds by remember(owner){mutableStateOf<EvidenceExportSelection?>(null)}
 var archive by remember(owner){mutableStateOf<EvidenceArchiveRequest?>(null)}
 var job by remember(owner){mutableStateOf<Job?>(null)}
 var transferring by remember(owner){mutableStateOf(false)}
 var notice by remember(owner){mutableStateOf<String?>(null)}
 fun discardDestination(ticket:EvidencePickerTicket?){if(ticket?.kind in listOf("Encrypted","Plain","Reexport"))ticket?.uri?.let{EvidenceDocumentIo.requestPartialCleanup(context,it,ticket.source)}}
 fun dismiss(){discardDestination(pending);discardDestination(delivered);job?.cancel();controller?.let{owner.retire(it)};controller=null;aiIds=null;exportIds=null;archive=null;pending?.stage?.let{owner.discard(it)};pending=null;delivered=null;owner.close();onClose()}
 fun received(uri:Uri?,accepted:Boolean){val ticket=pending?:return;if(!ticket.delivered.compareAndSet(false,true))return;ticket.uri=uri;ticket.accepted=accepted;delivered=ticket}
 val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->received(uri,uri!=null)}
 val outputPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->received(uri,uri!=null)}
 val permissionPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->received(null,result.resultCode==Activity.RESULT_OK)}
 fun handoff(ticket:EvidencePickerTicket,launch:()->Unit):Boolean {
  if(!activityResumed||!resumed||!owner.sameAuthentication()||controller?.current()!=true||pending!=null)return false
  pending=ticket;delivered=null;aiIds=null;exportIds=null;archive=null
  controller?.let{owner.retire(it)};controller=null
  try{launch()}catch(_:Throwable){ticket.stage?.let{owner.discard(it)};ticket.accepted=false;delivered=ticket;notice="The system document or permission screen could not open."}
  return true
 }
 DisposableEffect(owner,lifecycle){
  val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
   if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)resumed=true
   if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE||event==androidx.lifecycle.Lifecycle.Event.ON_STOP){
    resumed=false;job?.cancel();controller?.let{owner.retire(it)};controller=null;aiIds=null;exportIds=null;archive=null
    if(pending==null)dismiss()
   }
  }
  lifecycle.addObserver(observer)
  onDispose{lifecycle.removeObserver(observer);discardDestination(pending);discardDestination(delivered);job?.cancel();controller?.close();pending?.stage?.close();owner.close()}
 }
 LaunchedEffect(owner,activityResumed,resumed,delivered){withContext(Dispatchers.Main.immediate){
  if(!activityResumed||!resumed)return@withContext
  if(!owner.sameAuthentication()){dismiss();return@withContext}
  if(pending!=null&&delivered==null)return@withContext
  if(controller!=null&&delivered==null)return@withContext
  controller?.let{owner.retire(it)};controller=null
  val fresh=owner.newController(context)?:return@withContext
  controller=fresh
  val ticket=delivered?:return@withContext
  delivered=null;pending=null
  if(!ticket.accepted){ticket.stage?.let{owner.discard(it)};notice=notice?:"Cancelled. No evidence record or original was removed.";return@withContext}
  when(ticket.kind){
   "Import"->ticket.uri?.let{fresh.importAndRegister(it)}
   "Open"->ticket.uri?.let{archive=EvidenceArchiveRequest(it)}
   "Permission"->{aiMode=ticket.mode;aiIds=ticket.evidenceIds}
   "Reexport"->ticket.uri?.let{destination->archive=EvidenceArchiveRequest(checkNotNull(ticket.source),destination,ticket.cipherDigest)}
   "Encrypted","Plain"->{
    val destination=ticket.uri?:return@withContext
    transferring=true
    job=scope.launch(start=CoroutineStart.UNDISPATCHED){
     try {
      withContext(Dispatchers.IO){
       val reader=fresh.access.reader{!isActive};val catalog=fresh.access.store.read()
       check(EvidenceEgress.digest(catalog)==ticket.digest){"Export selection changed"}
       EvidenceEgress.requireAllowed(catalog,ticket.evidenceIds,ticket.incidentIds,reader)
       // Authentication must finish before even acquiring/truncating a public destination.
       val snapshot=if(ticket.kind=="Plain")EvidenceArchive.preparePlaintext(context,
        EvidenceExportPlan(catalog,ticket.incidentIds,ticket.evidenceIds,ticket.digest,true),reader)else null
       try { evidenceDestination(context,fresh,destination){output->
        if(ticket.kind=="Encrypted") {
         val stage=checkNotNull(ticket.stage);check(stage.current());val expected=checkNotNull(ticket.receipt);check(expected.encrypted)
         val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);var count=0L
         try{stage.file.inputStream().use{input->while(true){fresh.access.checkCurrent();val n=input.read(buffer);if(n<0)break;count+=n;check(count<=expected.bytes);output.write(buffer,0,n);digest.update(buffer,0,n)}};check(count==expected.bytes&&digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}==expected.sha256)}finally{buffer.fill(0)}
        }else {
         checkNotNull(snapshot).write(output)
        }
        fresh.access.checkCurrent();check(EvidenceEgress.digest(fresh.access.store.read())==ticket.digest)
        EvidenceEgress.requireAllowed(catalog,ticket.evidenceIds,ticket.incidentIds,reader)
       }}finally{snapshot?.close()}
      }
      fresh.access.publishIfCurrent{notice=if(ticket.kind=="Encrypted")"Password-encrypted .pgevidence export saved. Open it in Evidence Locker to read its PDF and structured contents."else"Explicitly confirmed plaintext ZIP export saved to the chosen document destination."}
     }catch(cancelled:CancellationException){EvidenceDocumentIo.requestPartialCleanup(context,destination);throw cancelled}catch(_:Throwable){EvidenceDocumentIo.requestPartialCleanup(context,destination);fresh.access.publishIfCurrent{notice="Export could not complete. Partial destination cleanup was requested; provider deletion cannot be guaranteed."}}
     finally{ticket.stage?.let{owner.discard(it)};transferring=false}
    }
   }
  }
 }}
 CompositionLocalProvider(LocalEvidenceSecurityActions provides remember(owner){EvidenceSecurityActions(owner::lockVault,owner::panicExit,owner::hideContent)}) {
 val active=controller
 if(active!=null&&!transferring&&active.current()) {
  EvidenceLocker(active,onClose={if(controller===active){owner.retire(active);controller=null;if(pending==null)dismiss()}},
   onImport={handoff(EvidencePickerTicket("Import")){importPicker.launch(arrayOf("image/*","application/pdf","text/plain","text/csv"))}},
   onAi={ids->aiIds=ids;aiMode=null},onExport={ids->exportIds=ids},
   onOpenArchive={handoff(EvidencePickerTicket("Open")){importPicker.launch(arrayOf("application/octet-stream","*/*"))}})
  aiIds?.let{ids->EvidenceAiDialog(active,ids,owner.allowsSystem(),{owner.routes(active)},onRequestPermission={mode->
   val intent=preparePermission(context)
   if(intent==null){aiMode=mode;notice="Android VPN permission is already granted. Prepare a fresh preview on the chosen secure transport.";aiIds=ids}
   else handoff(EvidencePickerTicket("Permission",evidenceIds=ids,mode=mode)){permissionPicker.launch(intent)}
  },onClose={aiIds=null},initialMode=aiMode)}
  exportIds?.let{ids->EvidenceExportDialog(active,ids.incidentIds,evidenceIds=ids.evidenceIds,createStage={owner.newCipherStage()},discardStage={owner.discard(it)},onPrepared={stage,plan,receipt->
   handoff(EvidencePickerTicket("Encrypted",plan.incidentIds.toList(),plan.evidenceIds.toList(),plan.digest,stage,receipt)){outputPicker.launch("private-gallery-evidence.pgevidence")}
  },onPlaintext={plan->handoff(EvidencePickerTicket("Plain",plan.incidentIds.toList(),plan.evidenceIds.toList(),plan.digest)){outputPicker.launch("private-gallery-evidence.zip")}},onClose={exportIds=null})}
  archive?.let{request->EvidenceArchiveDialog(active,request.source,request.destination,request.cipherDigest,onPlaintextRequest={cipherDigest->
   handoff(EvidencePickerTicket("Reexport",source=request.source,cipherDigest=cipherDigest)){outputPicker.launch("private-gallery-evidence.zip")}
  },onClose={archive=null})}
 }
 if(activityResumed&&resumed&&pending!=null&&delivered==null)GalleryAlertDialog(onDismissRequest={dismiss()},title={EvidenceDialogTitle("Waiting for system picker")},text={Text("Return from Android’s picker to continue, or cancel. Passwords and readable reports are not carried through it.")},confirmButton={TextButton(onClick={dismiss()}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
 if(transferring)GalleryAlertDialog(onDismissRequest={job?.cancel();dismiss()},title={EvidenceDialogTitle("Saving evidence export")},text={LinearProgressIndicator()},confirmButton={TextButton(onClick={job?.cancel();dismiss()}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
 notice?.let{text->GalleryAlertDialog(onDismissRequest={notice=null},title={EvidenceDialogTitle("Evidence Locker")},text={Text(text)},confirmButton={TextButton(onClick={notice=null}){Text("Done")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))}
 }
}

/** Output is never held across a picker. Closing a capability cancels provider IO and denies success. */
internal fun <T> evidenceDestination(context:Context,controller:EvidenceController,uri:Uri,write:(java.io.OutputStream)->T):T =
 EvidenceDocumentIo(context,controller.access).use{it.write(uri,consume=write)}
