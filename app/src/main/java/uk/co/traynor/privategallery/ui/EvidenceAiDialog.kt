package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.Image
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
import java.util.UUID
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.evidence.*

/** Opening this screen or preparing a preview never sends evidence to the provider. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EvidenceAiDialog(controller:EvidenceController,selectedIds:List<String>,allowSystemRoute:Boolean,
 createCoordinator:()->EvidenceRouteConnection,onRequestPermission:(String)->Unit,onClose:()->Unit,providedCredentials:EvidenceCredentials?=null,initialMode:String?=null,initialTask:EvidenceAi.Task?=null,onExportReport:(String)->Unit={},onRequestPermissionWithTask:((String,EvidenceAi.Task)->Unit)?=null) {
 val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 val state by controller.state.collectAsState()
 var model by remember(controller){mutableStateOf(EvidenceAi.Model.LUNA)}
 var task by remember(controller,initialTask){mutableStateOf(initialTask?:EvidenceAi.Task.FACTUAL_SUMMARY)}
 var question by remember(controller){mutableStateOf("")}
 var cloudPreviews by remember(controller,selectedIds){mutableStateOf(selectedIds.isNotEmpty())}
 var includedIds by remember(controller,selectedIds){mutableStateOf(selectedIds.toSet())}
 var originalId by remember(controller){mutableStateOf<String?>(null)}
 var mode by remember(controller,initialMode){mutableStateOf(initialMode?.takeIf{it=="VPN"||it=="Tor"&&controller.access.owner==EvidenceCatalog.Owner.HIDDEN||it=="System"&&allowSystemRoute}?:if(allowSystemRoute)"System"else"VPN")}
 var images by remember(controller){mutableStateOf<Set<String>>(emptySet())}
 var preview by remember(controller){mutableStateOf<EvidenceAiPreview?>(null)}
 var result by remember(controller){mutableStateOf<EvidenceCatalog.Analysis?>(null)}
 var coordinator by remember(controller){mutableStateOf<EvidenceRouteConnection?>(null)}
 var running by remember(controller){mutableStateOf<Job?>(null)}
 var busy by remember(controller){mutableStateOf(false)}
 var keyText by remember(controller){mutableStateOf("")}
 var configured by remember(controller){mutableStateOf(false)}
 var message by remember(controller){mutableStateOf("")}
 var applyChoice by remember(controller){mutableStateOf<String?>(null)}
 var confirmation by remember(controller){mutableStateOf<Pair<String,()->Unit>?>(null)}
 var newTitle by remember(controller){mutableStateOf<String?>(null)}
 var creationIds by remember(controller){mutableStateOf<List<String>?>(null)}
 var eventDraft by remember(controller){mutableStateOf<EvidenceAi.StructuredEvent?>(null)}
 var reviewedEvent by remember(controller){mutableStateOf<AiEventReview?>(null)}
 val credentials=remember(controller,providedCredentials){providedCredentials?:EvidenceCredentials(context,controller.access){coordinator?.close()}}
 fun retirePreview(){preview?.close();preview=null;coordinator?.close();coordinator=null}
 fun dismiss(){originalId=null;running?.cancel();retirePreview();result=null;keyText="";question="";message="";applyChoice=null;confirmation=null;newTitle=null;creationIds=null;eventDraft=null;reviewedEvent=null;onClose()}
 DisposableEffect(controller){val registration=controller.access.onRetire{android.os.Handler(android.os.Looper.getMainLooper()).post{dismiss()}};onDispose{registration.close();running?.cancel();preview?.close();coordinator?.close()}}
 LaunchedEffect(controller,credentials){
  val present=withContext(Dispatchers.IO){runCatching{credentials.isConfigured()}.getOrDefault(false)}
  // A composition effect's continuation interceptor is not itself a Main guarantee.
  withContext(Dispatchers.Main.immediate){controller.access.publishIfCurrent{configured=present}}
 }
 fun action(work:suspend ()->Unit){
  if(busy||!controller.current())return
  busy=true;message=""
  running=scope.launch(start=CoroutineStart.UNDISPATCHED){try{work()}catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){controller.access.publishIfCurrent{message="Analysis unavailable or changed since preview. Your local Evidence Locker remains available; prepare a new preview or check the authorised transport and key."}}finally{withContext(NonCancellable+Dispatchers.Main.immediate){controller.access.publishIfCurrent{busy=false}}}}
 }
 LaunchedEffect(preview,state.catalog?.revision()) {
  val candidate=preview?:return@LaunchedEffect
  val catalog=state.catalog?:return@LaunchedEffect
  val digest=withContext(Dispatchers.IO){EvidenceEgress.digest(catalog)}
  withContext(Dispatchers.Main.immediate){if(preview===candidate&&digest!=candidate.catalogDigest){retirePreview();message="Evidence changed. Prepare a new exact preview before sending."}}
 }
 LaunchedEffect(preview) {
  val candidate=preview?:return@LaunchedEffect
  while(isActive){val live=withContext(Dispatchers.IO){candidate.current()};withContext(Dispatchers.Main.immediate){if(preview===candidate&&!live){retirePreview();message="Transport or authority retired. Prepare a new preview."}};if(!live)break;delay(200)}
 }
 fun saved(analysis:EvidenceCatalog.Analysis)=state.catalog?.analyses()?.any{it.id()==analysis.id()}==true
 fun apply(analysis:EvidenceCatalog.Analysis,incidentId:String?,title:String?,timeline:Boolean,ids:List<String> = analysis.evidenceIds(),eventTitle:String="AI advisory — uncertain ordering",eventNotes:String="",eventTime:Long=0){
  require(ids.isNotEmpty()&&ids.distinct().size==ids.size&&ids.all{it in analysis.evidenceIds()})
  controller.mutate("Owner-approved AI advisory applied separately") {catalog ->
   var current=if(catalog.analyses().any{it.id()==analysis.id()})catalog else catalog.saveAnalysis(analysis,EvidenceController.now())
   val stored=current.analyses().single{it.id()==analysis.id()}
   val original=if(incidentId==null)EvidenceCatalog.Incident(UUID.randomUUID().toString(),title.orEmpty(),0,0,"","",emptyList(),ids,emptyList(),false,false,emptyList())else current.incidents().single{it.id()==incidentId}
   val reference="AI-generated advisory ${stored.id()} (${stored.model()}); unverified, owner-approved reference. Original evidence unchanged."
   val event=if(timeline)listOf(EvidenceCatalog.Event(UUID.randomUUID().toString(),ids,eventTime,if(eventTime>0)"USER"else"AI_INFERRED",eventTitle,reference+"\n"+eventNotes))else emptyList()
   val updated=EvidenceCatalog.Incident(original.id(),original.title(),original.startAt(),original.endAt(),original.description(),original.notes()+"\n"+reference,original.tags(),original.evidenceIds(),original.events()+event,original.manualOrder(),original.archived(),(original.lineage()+stored.lineage()).distinct())
   current.upsertIncident(updated,EvidenceController.now(),if(timeline)"OWNER_APPLY_AI_TIMELINE"else"OWNER_APPLY_AI_SUMMARY").associateAnalysis(stored.id(),original.id(),EvidenceController.now())
  }
 }
 val details=remember(result){result?.let{runCatching{EvidenceAi.details(it)}.getOrNull()}}
 if(!controller.current())return
 EvidencePage(onBack={dismiss()},sensitive=true) {
  Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth()){Text("Optional AI analysis",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);EvidenceSecurityControls();TextButton(onClick={dismiss()}){Text(if(busy)"Cancel / close"else"Close")}}
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     if(message.isNotEmpty())item{Text(message)}
     val prepared=preview;val analysis=result
     if(analysis!=null) {
      item {Text("AI-generated advisory · not verified fact",style=MaterialTheme.typography.titleMedium);Text("${analysis.model()} · ${analysis.inputTokens()} input / ${analysis.outputTokens()} output tokens");if(details?.report==null)Text(details?.summary?:analysis.text());details?.let{Text("Status ${it.status}${if(it.refusal)" · provider refusal"else""} · cached input ${it.cachedInputTokens}",style=MaterialTheme.typography.bodySmall)}
       FlowRow {
        TextButton(enabled=!busy&&!saved(analysis),onClick={controller.mutate("AI advisory saved separately"){it.saveAnalysis(analysis,EvidenceController.now())}}){Text(if(saved(analysis))"Saved"else if(details?.report!=null)"Save encrypted report"else"Save advisory")}
        if(details?.report==null)TextButton(enabled=!busy&&details?.refusal!=true,onClick={confirmation="Create an incident from these selected references with a labelled AI advisory? No original will change." to {creationIds=analysis.evidenceIds();newTitle="AI-assisted incident"}}){Text("Create incident")}
        if(details?.report==null)TextButton(enabled=!busy&&details?.refusal!=true,onClick={applyChoice="Summary"}){Text("Apply to incident")}
        if(details?.report==null)TextButton(enabled=!busy&&details?.refusal!=true,onClick={applyChoice="Timeline"}){Text("Add uncertain timeline advisory")}
        if(details?.report!=null)TextButton(enabled=!busy&&saved(analysis),onClick={if(controller.current())onExportReport(analysis.id())}){Text("Export report only")}
        TextButton(enabled=!busy,onClick={result=null;retirePreview()}){Text("New analysis")}
       }
      }
      details?.report?.let{report->item{EvidenceBehaviourReportContent(report,
       {id->controller.current()&&evidenceReportSourceAllowed(controller.state.value.catalog,analysis,id)},
       {id->if(controller.current()&&evidenceReportSourceAllowed(controller.state.value.catalog,analysis,id))originalId=id})}}
      details?.let{parsed->
       items(parsed.suggestions){suggestion->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
        Text("AI suggestion · ${suggestion.kind}");Text(suggestion.title);Text(suggestion.reasons.joinToString("; "));Text("${suggestion.evidenceIds.size} selected source references · advisory only")
        TextButton(enabled=!busy,onClick={creationIds=suggestion.evidenceIds;newTitle=suggestion.title}){Text("Review group as new incident")}
       }}}
       items(parsed.events){event->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
        Text("AI-extracted event · unverified");Text(event.title);Text("Visible date/time quote: ${event.dateTimeQuote?:"Unavailable"}");Text("${event.provenance} · ${event.uncertainty}")
        TextButton(enabled=!busy,onClick={eventDraft=event}){Text("Review timeline event")}
       }}}
      }
     }else if(prepared!=null) {
      item {Text("Review before sending",style=MaterialTheme.typography.titleLarge);Text("${prepared.owner} · ${prepared.routeLabel}\nModel ${prepared.model.id} · ${prepared.task.label}\n${prepared.selectedIds.size} selected records / ${prepared.images.size} explicit images")
       Text("Estimated maximum-output cost: USD ${"%.4f".format(java.util.Locale.US,prepared.estimatedUsd)} · estimate only")
       Text(prepared.disclosure)
       Text("Exact transmitted text (including instructions)",style=MaterialTheme.typography.titleMedium);Text(prepared.exactText)
       Text("Rates USD per million tokens: input ${prepared.model.inputUsdPerMillion}, output ${prepared.model.outputUsdPerMillion}, cache write ${prepared.model.cacheWriteUsdPerMillion}. ${prepared.pricingRevision}",style=MaterialTheme.typography.bodySmall)
       Text("Payload SHA-256 ${prepared.bodyDigest}",style=MaterialTheme.typography.bodySmall)
      }
      items(prepared.images,key={it.evidenceId}){image->Text("Explicit image ${image.evidenceId}: ${image.width} × ${image.height}, ${image.encodedBytes} JPEG bytes");EvidenceAiImagePreview(image,controller)}
      item {Button(enabled=!busy&&configured,onClick={action{
        preview=null;message="Sending only the explicitly confirmed evidence…"
        var pending:EvidenceCatalog.Analysis?=null
        try{pending=EvidenceAi.analyze(prepared,credentials){EvidenceEgress.digest(controller.access.store.read())}
         controller.access.publishIfCurrent{result=pending;preview=null}
        }finally{prepared.close();coordinator?.close();coordinator=null;preview=null}
       }}){Text("Confirm and send selected evidence")}
       TextButton(enabled=!busy,onClick={retirePreview()}){Text("Cancel preview")}
       if(!configured)Text("Set up your API key below before sending.")
      }
     }else {
      item {EvidenceWorkflowHeading("Choose your analysis","Select only what you intend to transmit. Default: text only. Nothing uploads until the final confirmation.")
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(if(cloudPreviews)"Allow cloud previews"else"Local only",Modifier.weight(1f))
        Switch(checked=cloudPreviews,onCheckedChange={cloudPreviews=it},enabled=!busy)
       }
       Text("OCR and timeline organisation run locally. Allowing a cloud preview still requires your explicit confirmation for each upload.")
       Text("Transport",style=MaterialTheme.typography.titleMedium)
       FlowRow {listOfNotNull(if(allowSystemRoute)"System"else null,"VPN",if(controller.access.owner==EvidenceCatalog.Owner.HIDDEN)"Tor"else null).forEach{candidate->FilterChip(selected=mode==candidate,enabled=!busy,onClick={mode=candidate},label={Text(candidate)})}}
       TextButton(enabled=!busy&&mode!="System",onClick={if(controller.current()){val requested=mode;val requestedTask=task;dismiss();if(onRequestPermissionWithTask!=null)onRequestPermissionWithTask(requested,requestedTask)else onRequestPermission(requested)}}){Text("Set up Android VPN / isolation permission")}
       FlowRow {EvidenceAi.Model.entries.forEach{candidate->FilterChip(selected=model==candidate,enabled=!busy,onClick={model=candidate},label={Text(candidate.id)})}}
       FlowRow {EvidenceAi.Task.entries.forEach{candidate->FilterChip(selected=task==candidate,enabled=!busy,onClick={task=candidate},label={Text(candidate.label)})}}
       if(task==EvidenceAi.Task.EVIDENCE_REPORT) {
        OutlinedTextField(question,{question=it.take(EvidenceAiReport.MAX_QUESTION_CHARACTERS)},enabled=!busy&&cloudPreviews,
         label={Text("Ask Nova AI about the selected evidence")},placeholder={Text("Which messages requested an immediate reply?")},
         supportingText={Text("Optional. Your question appears in the exact cloud preview.")},maxLines=4,modifier=Modifier.fillMaxWidth())
       }else{
        TextButton(enabled=!busy&&cloudPreviews,onClick={task=EvidenceAi.Task.EVIDENCE_REPORT}){Text("Ask a question with sources")}
       }
       Text("Review selected evidence",style=MaterialTheme.typography.titleMedium)
      }
      items(selectedIds,key={it}){id->state.catalog?.evidence()?.firstOrNull{it.id()==id}?.let{record->Column {
       Row {Checkbox(id in includedIds,{checked->includedIds=if(checked)includedIds+id else includedIds-id;if(!checked)images=images-id},enabled=!busy);Text("Include ${record.name()}",Modifier.weight(1f))}
       if(id in includedIds&&record.mime().startsWith("image/"))Row {Checkbox(id in images,{checked->images=if(checked)images+id else images-id},enabled=!busy);Text("Also send the displayed image (optional)")}
      }}}
      item{
       Text("${includedIds.size} records selected. Maximum 32 records and 8 explicit images. Local OCR text is included. Images are off by default.")
       if(selectedIds.isEmpty())Text("Select evidence in Evidence Locker to start a cited analysis.")
      }
      item {Button(enabled=!busy&&cloudPreviews&&includedIds.size in 1..32&&images.size<=8,onClick={action{
        val connected=createCoordinator();coordinator=connected
        var pending:EvidenceAiPreview?=null
        try {
         val route=connected.connect(mode)
         val catalog=withContext(Dispatchers.IO){controller.access.store.read()}
         val requestedQuestion=question.trim().takeIf{task==EvidenceAi.Task.EVIDENCE_REPORT&&it.isNotEmpty()}
         pending=EvidenceAi.prepare(catalog,selectedIds.filter{it in includedIds},images.toList(),model,task,controller.access.reader(),route,requestedQuestion)
         if(!controller.access.publishIfCurrent{preview=pending;pending=null})throw CancellationException("Evidence preview retired")
        }finally{pending?.close();if(preview==null){connected.close();coordinator=null}}
       }}){Text("Prepare exact upload preview")}}
     }
     item {HorizontalDivider();Text("Nova AI settings",style=MaterialTheme.typography.titleMedium)
      Text("Cloud analysis uses OpenAI. OpenAI API billing is separate from your ChatGPT subscription.",style=MaterialTheme.typography.bodySmall)
      Text("Owner API key · ${if(controller.access.owner==EvidenceCatalog.Owner.PRIMARY)"Primary"else"Hidden"}",style=MaterialTheme.typography.titleMedium)
      Text("${if(configured)"Configured"else"Not configured"}. The key is encrypted on this device with Android Keystore protection, separately for this vault. A compromised device may still expose a locally stored key.",style=MaterialTheme.typography.bodySmall)
      OutlinedTextField(keyText,{keyText=it.take(8192)},enabled=!busy,label={Text("OpenAI API key")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth())
      FlowRow {TextButton(enabled=!busy&&keyText.isNotEmpty(),onClick={val chars=keyText.toCharArray();keyText="";action{try{withContext(Dispatchers.IO){credentials.setup(chars)};controller.access.publishIfCurrent{configured=true;message="API key stored for this vault"}}finally{chars.fill('\u0000')}}}){Text("Save key")}
       TextButton(enabled=!busy&&configured,onClick={confirmation="Remove this vault's API key and cancel any outstanding analysis?" to {action{withContext(Dispatchers.IO){credentials.remove()};retirePreview();controller.access.publishIfCurrent{configured=false;message="API key removed"}}}}){Text("Remove key")}
       if(prepared==null&&analysis==null)TextButton(enabled=!busy&&configured,onClick={
        val routeMode=mode;val testModel=model
        confirmation="Test ${testModel.id} over $routeMode? This sends a fixed test prompt to OpenAI and may incur a small API charge. No evidence or question is sent." to {action{
         val connected=createCoordinator();coordinator=connected
         try{
          EvidenceAi.testConnection(connected.connect(routeMode),credentials,testModel)
          controller.access.publishIfCurrent{message="OpenAI accepted the connection test for ${testModel.id}."}
         }catch(failure:EvidenceAiFailure){
          controller.access.publishIfCurrent{message=failure.message.orEmpty()}
         }finally{connected.close();coordinator=null}
        }}
       }){Text("Test connection")}
      }
     }
    }
   }
  }
 }
 originalId?.let{id->val analysis=result;if(analysis!=null&&controller.current()&&evidenceReportSourceAllowed(state.catalog,analysis,id))EvidenceOriginalView(controller,id){originalId=null}else SideEffect{originalId=null}}
 applyChoice?.let{kind->val analysis=result
  GalleryAlertDialog(onDismissRequest={applyChoice=null},title={EvidenceDialogTitle("Choose incident for labelled AI advisory")},text={LazyColumn(Modifier.heightIn(max=400.dp)){items(state.catalog?.incidents()?.filter{!it.archived()}.orEmpty(),key={it.id()}){incident->TextButton(onClick={applyChoice=null;if(analysis!=null)confirmation="Apply this AI-generated advisory to ${incident.title()}? It remains unverified and separately labelled." to {val event=reviewedEvent;reviewedEvent=null;if(kind=="Event"&&event!=null)apply(analysis,incident.id(),null,true,event.ids,event.title,event.notes,event.at)else apply(analysis,incident.id(),null,kind=="Timeline")}}){Text(incident.title())}}}},confirmButton={TextButton(onClick={applyChoice=null}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
 }
 newTitle?.let{title->GalleryAlertDialog(onDismissRequest={newTitle=null},title={EvidenceDialogTitle("Review incident title")},text={OutlinedTextField(title,{newTitle=it.take(1024)},label={Text("Title")})},confirmButton={TextButton(enabled=title.isNotBlank(),onClick={val analysis=result;val ids=creationIds;newTitle=null;creationIds=null;if(analysis!=null)apply(analysis,null,title,false,ids?:analysis.evidenceIds())}){Text("Create")}},dismissButton={TextButton(onClick={newTitle=null}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))}
 eventDraft?.let{event->
  var title by remember(event){mutableStateOf(event.title)};var date by remember(event){mutableLongStateOf(0L)};var notes by remember(event){mutableStateOf("Visible date/time: ${event.dateTimeQuote?:"Unavailable"}\n${event.provenance}\nUncertainty: ${event.uncertainty}")}
  GalleryAlertDialog(onDismissRequest={eventDraft=null},title={EvidenceDialogTitle("Review AI-extracted event")},text={LazyColumn(Modifier.heightIn(max=360.dp)){item{
   Text("Leave the event time unknown unless you can confirm it. A date you choose is recorded as your confirmation, and the AI advisory stays labelled.")
   OutlinedTextField(title,{title=it.take(1024)},label={Text("Event title")})
   EvidenceDateTimeField("Confirmed event time, optional",date){date=it.coerceAtLeast(0L)}
   OutlinedTextField(notes,{notes=it.take(12000)},label={Text("Provenance and owner review")})
  }}},confirmButton={TextButton(enabled=title.isNotBlank(),onClick={reviewedEvent=AiEventReview(event.evidenceIds,title,notes,date);eventDraft=null;applyChoice="Event"}){Text("Choose incident")}},dismissButton={TextButton(onClick={eventDraft=null}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
 }
 confirmation?.let{(text,confirmed)->GalleryAlertDialog(onDismissRequest={confirmation=null},title={EvidenceDialogTitle("Confirm")},text={Text(text)},confirmButton={TextButton(onClick={confirmation=null;if(controller.current())confirmed()}){Text("Confirm")}},dismissButton={TextButton(onClick={confirmation=null}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))}
}

@Composable private fun EvidenceAiImagePreview(image:EvidenceAiImage,controller:EvidenceController){
 var bitmap by remember(image){mutableStateOf<android.graphics.Bitmap?>(null)}
 DisposableEffect(image){onDispose{bitmap?.let{if(it.isMutable)it.eraseColor(android.graphics.Color.TRANSPARENT);it.recycle()};bitmap=null}}
 LaunchedEffect(image){var pending:android.graphics.Bitmap?=null;var owned:ByteArray?=null;try{val bytes=image.copyThumbnail();owned=bytes;withContext(Dispatchers.IO){pending=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply{inMutable=true})};withContext(Dispatchers.Main.immediate){if(!controller.access.publishIfCurrent{bitmap=pending;pending=null})throw CancellationException("Retired image preview")}}catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){}finally{owned?.fill(0);pending?.let{if(it.isMutable)it.eraseColor(android.graphics.Color.TRANSPARENT);it.recycle()}}}
 bitmap?.takeIf{controller.current()}?.let{Image(it.asImageBitmap(),"Exact selected image transmitted to OpenAI",Modifier.fillMaxWidth().heightIn(max=256.dp),contentScale=ContentScale.Fit)}
}

private data class AiEventReview(val ids:List<String>,val title:String,val notes:String,val at:Long)
