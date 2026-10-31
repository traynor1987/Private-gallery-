package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.vault.VaultPdfPreview

@Composable internal fun EvidenceExportDialog(controller:EvidenceController,incidentIds:List<String>,evidenceIds:List<String> = emptyList(),createStage:()->EvidenceOwnerSession.CipherStage,
 discardStage:(EvidenceOwnerSession.CipherStage)->Unit,onPrepared:(EvidenceOwnerSession.CipherStage,EvidenceExportPlan,EvidenceExportReceipt)->Boolean,
 onPlaintext:(EvidenceExportPlan)->Boolean,onClose:()->Unit) {
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 val focus=androidx.compose.ui.platform.LocalFocusManager.current
 var plan by remember(controller,incidentIds,evidenceIds){mutableStateOf<EvidenceExportPlan?>(null)}
 var password by remember(controller){mutableStateOf("")};var repeat by remember(controller){mutableStateOf("")}
 var busy by remember(controller){mutableStateOf(false)};var message by remember(controller){mutableStateOf("")}
 var plaintext by remember(controller){mutableStateOf(false)};var running by remember(controller){mutableStateOf<Job?>(null)}
 var staged by remember(controller){mutableStateOf<EvidenceOwnerSession.CipherStage?>(null)}
 fun dismiss(){running?.cancel();password="";repeat="";staged?.let(discardStage);staged=null;plan=null;onClose()}
 DisposableEffect(controller){val retired=controller.access.onRetire{android.os.Handler(android.os.Looper.getMainLooper()).post{dismiss()}};onDispose{retired.close();running?.cancel();staged?.let(discardStage)}}
 LaunchedEffect(controller,incidentIds,evidenceIds){
  try {
   val selection=withContext(Dispatchers.IO){val catalog=controller.access.store.read();val incidents=incidentIds.map{id->catalog.incidents().single{it.id()==id}};require(incidentIds.distinct().size==incidentIds.size&&incidentIds.size<=128);require(evidenceIds.distinct().size==evidenceIds.size);val ids=(evidenceIds+incidents.flatMap{it.evidenceIds()+it.events().flatMap{e->e.evidenceIds()}}).distinct();require(ids.size<=128&&(incidentIds.isNotEmpty()||ids.isNotEmpty()));EvidenceEgress.requireAllowed(catalog,ids,incidentIds,controller.access.reader());EvidenceExportPlan(catalog,incidentIds.toList(),ids,EvidenceEgress.digest(catalog),false)}
   withContext(Dispatchers.Main.immediate){controller.access.publishIfCurrent{plan=selection}}
  }catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){withContext(Dispatchers.Main.immediate){controller.access.publishIfCurrent{message="Export is blocked for vault-only, unavailable, changed or foreign-vault contributors. Verify the selected incident's evidence first."}}}
 }
 if(!controller.current())return
 Dialog(onDismissRequest={dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {
  Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(Modifier.fillMaxWidth()){Text("Export evidence",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);EvidenceSecurityControls();TextButton(onClick={dismiss()}){Text("Cancel")}}
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    if(message.isNotEmpty())item{Text(message)}
    item {Text("Default: password-encrypted .pgevidence container with a readable PDF report, structured index, transcripts and byte-identical originals. The PDF inside is not independently password protected.")
     Text("Open this container in Evidence Locker. Plaintext re-export requires another explicit confirmation. Hashes check matching bytes, not the truth of screenshot contents or legal certification.")}
    val selected=plan
    if(selected!=null) {
     items(selected.incidentIds,key={it}){id->val incident=selected.catalog.incidents().single{it.id()==id};Text("Incident: ${incident.title()} · ${incident.evidenceIds().size} linked records")}
     items(selected.evidenceIds,key={it}){id->val e=selected.catalog.evidence().single{it.id()==id};Text("Attached original: ${e.name()} · ${e.size()} bytes")}
     item {
      val attached=selected.evidenceIds.map{id->selected.catalog.evidence().single{it.id()==id}.sourceId()}.toSet()
      val retained=selected.incidentIds.flatMap{id->selected.catalog.incidents().single{it.id()==id}.lineage()}.distinctBy{it.sourceId()}.filter{it.sourceId() !in attached}
      if(retained.isNotEmpty())Text("${retained.size} retained contributing source fingerprint(s) will be listed as not attached; their live ownership, integrity identity and export policy are still checked.")
      OutlinedTextField(password,{password=it.take(1024)},enabled=!busy,label={Text("Export password (at least 12 characters)")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth())
      OutlinedTextField(repeat,{repeat=it.take(1024)},enabled=!busy,label={Text("Repeat password")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth())
      Text("Use a strong unique password; 20 generated characters are recommended. Spaces are preserved exactly. Remember it: this export has no password recovery.",style=MaterialTheme.typography.bodySmall)
      Button(enabled=!busy&&password.length>=12&&password==repeat,onClick={
       val chars=password.toCharArray();password="";repeat="";focus.clearFocus(force=true);busy=true;message="Preparing an encrypted export…"
       val stage=try{createStage()}catch(_:Throwable){chars.fill('\u0000');busy=false;message="Encrypted staging is unavailable.";return@Button}
       staged=stage
       running=scope.launch(start=CoroutineStart.UNDISPATCHED){var transferred=false
        try {
         val receipt=try{withContext(Dispatchers.IO){val reader=controller.access.reader{!isActive};check(EvidenceEgress.digest(controller.access.store.read())==selected.digest);EvidenceArchive.write(selected,reader,chars,stage.output).also{check(it.encrypted);controller.access.checkCurrent();check(EvidenceEgress.digest(controller.access.store.read())==selected.digest);EvidenceEgress.requireAllowed(selected.catalog,selected.evidenceIds,selected.incidentIds,reader);stage.seal()}}}finally{chars.fill('\u0000')}
         var admitted=false
         controller.access.publishIfCurrent{staged=null;admitted=true}
         if(admitted&&controller.current())transferred=onPrepared(stage,selected,receipt)
         if(!transferred)throw CancellationException("Export authority changed")
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){controller.access.publishIfCurrent{message="Encrypted export could not complete. No public plaintext copy was created."}}
        finally{chars.fill('\u0000');if(!transferred){discardStage(stage);if(staged===stage)staged=null};busy=false}
       }
      }){Text("Prepare encrypted export")}
      TextButton(enabled=!busy,onClick={password="";repeat="";plaintext=true}){Text("Choose explicit plaintext ZIP export…")}
     }
    }
   }
  }}
 }
 if(plaintext)GalleryAlertDialog(onDismissRequest={plaintext=false},title={EvidenceDialogTitle("Create unencrypted evidence outside the vault?")},text={Text("The chosen document provider will receive readable original files, OCR, notes, incident details and AI-labelled content. Cloud providers may sync it. Cancelled writes can leave partial copies that cannot be securely erased. Continue only if you intend this plaintext export.")},confirmButton={TextButton(onClick={val selected=plan;plaintext=false;if(selected!=null&&controller.current())onPlaintext(selected.copy(plaintextConfirmed=true))}){Text("Confirm plaintext export")}},dismissButton={TextButton(onClick={plaintext=false}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
}

private val evidenceArchiveRetirement=Executors.newCachedThreadPool{r->Thread(r,"evidence-archive-retirement").apply{isDaemon=true}}
private fun retireEvidenceArchive(archive:EvidenceOpenedArchive?){if(archive!=null)evidenceArchiveRetirement.execute{runCatching{archive.close()}}}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EvidenceArchiveDialog(controller:EvidenceController,source:Uri,destination:Uri?,expectedCipherDigest:String?,
 renderReport:(android.content.Context,ByteArray,Int,()->Boolean)->Pair<Bitmap,Int> = {context,bytes,index,current->VaultPdfPreview.page(context,bytes,index,current)},
 onPlaintextRequest:(String)->Unit,onClose:()->Unit) {
 val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 val focus=androidx.compose.ui.platform.LocalFocusManager.current
 val alive=remember(controller,source){AtomicBoolean(true)}
 val destinationCompleted=remember(controller,source,destination){AtomicBoolean(false)}
 var password by remember(controller,source){mutableStateOf("")};var opened by remember(controller,source){mutableStateOf<EvidenceOpenedArchive?>(null)}
 var bitmap by remember(controller,source){mutableStateOf<Bitmap?>(null)};var page by remember(controller,source){mutableIntStateOf(0)};var pages by remember(controller,source){mutableIntStateOf(1)}
 var busy by remember(controller,source){mutableStateOf(false)};var message by remember(controller,source){mutableStateOf("")};var confirmPlain by remember(controller,source){mutableStateOf(false)}
 var running by remember(controller,source){mutableStateOf<Job?>(null)};val document=remember(controller,source){java.util.concurrent.atomic.AtomicReference<EvidenceDocumentIo?>(null)}
 fun clear(){password="";bitmap?.let(::wipeReportBitmap);bitmap=null;retireEvidenceArchive(opened);opened=null;document.get()?.close();document.set(null)}
 fun dismiss(){alive.set(false);running?.cancel();if(destination!=null&&!destinationCompleted.get())EvidenceDocumentIo.requestPartialCleanup(context,destination,source);clear();onClose()}
 DisposableEffect(controller,source){val retired=controller.access.onRetire{alive.set(false);document.get()?.close();android.os.Handler(android.os.Looper.getMainLooper()).post{dismiss()}};onDispose{retired.close();alive.set(false);running?.cancel();if(destination!=null&&!destinationCompleted.get())EvidenceDocumentIo.requestPartialCleanup(context,destination,source);document.get()?.close();retireEvidenceArchive(opened);bitmap?.let(::wipeReportBitmap)}}
 LaunchedEffect(opened,page){val archive=opened?:return@LaunchedEffect;var pending:Bitmap?=null
  try {
   var count=1
   withContext(Dispatchers.IO){archive.withReport{report->val rendered=renderReport(context,report,page){alive.get()&&controller.current()&&isActive};pending=rendered.first;count=rendered.second}}
   withContext(Dispatchers.Main.immediate){val previous=bitmap;if(!alive.get()||!controller.access.publishIfCurrent{bitmap=pending;pending=null;pages=count})throw CancellationException("Report view retired");previous?.let(::wipeReportBitmap)}
  }catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){withContext(Dispatchers.Main.immediate){controller.access.publishIfCurrent{message="The authenticated report could not be rendered; no plaintext file was created."}}}finally{pending?.let(::wipeReportBitmap)}
 }
 if(!controller.current())return
 Dialog(onDismissRequest={dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {
  Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(Modifier.fillMaxWidth()){Text("Open encrypted evidence",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);EvidenceSecurityControls();TextButton(onClick={dismiss()}){Text("Close")}}
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   val archive=opened
   if(archive==null) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
    if(message.isNotEmpty())Text(message)
    Text(if(destination==null)"Enter the archive password. Report and originals are verified before anything is displayed."else"Re-enter the password after the document picker. No password, key or plaintext report was retained through that handoff.")
    OutlinedTextField(password,{password=it.take(1024)},enabled=!busy,label={Text("Archive password")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth())
    Button(enabled=!busy&&password.isNotEmpty(),onClick={val chars=password.toCharArray();password="";focus.clearFocus(force=true);busy=true;message="Verifying encrypted evidence…"
     running=scope.launch(start=CoroutineStart.UNDISPATCHED){var pending:EvidenceOpenedArchive?=null
      try {
       withContext(Dispatchers.IO){
        val operation=currentCoroutineContext();val io=EvidenceDocumentIo(context,controller.access){alive.get()&&operation.isActive};document.set(io)
        try{io.read(source){input->EvidenceArchive.open(context,controller.access.owner,input,chars){alive.get()&&controller.current()}.also{pending=it}};expectedCipherDigest?.let{check(pending!!.ciphertextSha256==it){"Archive changed since confirmation"}}}finally{io.close();document.set(null)}
       }
       if(!alive.get()||!controller.access.publishIfCurrent{opened=pending;pending=null;page=0;message=opened!!.integritySummary;if(destination!=null)confirmPlain=true})throw CancellationException("Archive authority retired")
      }catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){controller.access.publishIfCurrent{message="Could not authenticate or open this archive. Check the password, originating vault and complete file. No report was published."}}
      finally{chars.fill('\u0000');retireEvidenceArchive(pending);busy=false}
     }
    }){Text("Authenticate and open")}
    }
   }else {
    if(message.isNotEmpty()&&message!=archive.integritySummary)Text(message)
    Text(archive.integritySummary,style=MaterialTheme.typography.bodySmall)
    bitmap?.let{Image(it.asImageBitmap(),"Authenticated evidence PDF report",Modifier.weight(1f).fillMaxWidth(),contentScale=ContentScale.Fit)}?:Text("Rendering protected report…",Modifier.weight(1f))
    FlowRow {TextButton(enabled=page>0&&!busy,onClick={page--}){Text("Previous page")};Text("${page+1} / $pages");TextButton(enabled=page+1<pages&&!busy,onClick={page++}){Text("Next page")}}
    TextButton(enabled=!busy,onClick={confirmPlain=true}){Text("Export readable plaintext ZIP…")}
   }
  }}
 }
 if(confirmPlain)GalleryAlertDialog(onDismissRequest={confirmPlain=false},title={EvidenceDialogTitle("Confirm plaintext re-export")},text={Text("This exports the authenticated archive's readable PDF, structured metadata and originals outside the vault. It does not read or alter live vault originals. Partial provider copies cannot be securely erased. Explicitly continue?")},confirmButton={TextButton(onClick={confirmPlain=false;val archive=opened?:return@TextButton
  if(destination==null){val digest=archive.ciphertextSha256;clear();onPlaintextRequest(digest)}else {
   busy=true;running=scope.launch(start=CoroutineStart.UNDISPATCHED){try{withContext(Dispatchers.IO){val operation=currentCoroutineContext();val io=EvidenceDocumentIo(context,controller.access){alive.get()&&operation.isActive};document.set(io);try{io.write(destination,protectedSource=source){output->archive.writePlaintext(output,true){alive.get()&&controller.current()&&operation.isActive&&io.current()}}}finally{io.close();document.set(null)}};if(!controller.access.publishIfCurrent{destinationCompleted.set(true);message="Explicitly confirmed plaintext archive ZIP saved."}){EvidenceDocumentIo.requestPartialCleanup(context,destination,source);throw CancellationException("Destination authority retired")}}catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){controller.access.publishIfCurrent{message="Plaintext copy failed. Partial destination cleanup was requested; deletion cannot be guaranteed."}}finally{busy=false}}
  }
 }){Text("Confirm plaintext re-export")}},dismissButton={TextButton(onClick={confirmPlain=false}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
}
private fun wipeReportBitmap(bitmap:Bitmap){runCatching{if(bitmap.isMutable)bitmap.eraseColor(android.graphics.Color.TRANSPARENT)};runCatching{bitmap.recycle()}}
