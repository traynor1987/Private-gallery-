package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.util.UUID
import uk.co.traynor.privategallery.core.evidence.*

/** Plaintext drafts belong to this capability only; closing/locking disposes them without registration. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EvidenceImportReview(controller:EvidenceController,onClose:()->Unit) {
 val state by controller.state.collectAsState()
 var selected by remember(controller){mutableStateOf<Set<String>>(emptySet())}
 var draft by remember(controller){mutableStateOf<EvidenceCatalog?>(null)}
 var step by remember(controller){mutableIntStateOf(0)}
 var busy by remember(controller){mutableStateOf(false)}
 var message by remember(controller){mutableStateOf("")}
 var transcripts by remember(controller){mutableStateOf<Map<String,String>>(emptyMap())}
 var groups by remember(controller){mutableStateOf<Map<String,String>>(emptyMap())}
 val scope=rememberCoroutineScope()
 LaunchedEffect(state.reviewImportIds){if(step==0&&state.reviewImportIds.isNotEmpty())selected=state.reviewImportIds.toSet()}
 fun back(){if(!busy){if(step>0)step-- else onClose()}}
 EvidencePage(::back){Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(when(step){0->"1 · Review selected originals";1->"2 · Offline OCR review";else->"3 · Review incident groups"},style=MaterialTheme.typography.headlineSmall)
  Text("${if(controller.access.owner==EvidenceCatalog.Owner.HIDDEN)"Hidden"else"Primary"} encrypted vault · Nothing is sent to cloud AI.")
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(message.isNotEmpty())Text(message,color=MaterialTheme.colorScheme.error)
  LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
   when(step){
    0->{item{Text("Choose up to 128 originals. Uncheck accidental selections. OCR and grouping are reviewed before evidence registration; imported originals remain encrypted if you cancel.")}
     items(state.originals.filter{original->state.catalog?.evidence()?.none{it.sourceId()==original.item.id}!=false},key={it.item.id}){original->
      EvidenceSelectionRow("Register original ${original.item.displayName}",original.item.displayName,"${original.item.mimeType} · ${original.item.plaintextSize} bytes",original.item.id in selected,original.item.id in selected||selected.size<128){checked->selected=if(checked)selected+original.item.id else selected-original.item.id}
     }}
    1->items(draft?.evidence().orEmpty(),key={it.id()}){record->
     Text(record.name(),style=MaterialTheme.typography.titleMedium)
     Text("Original SHA-256: ${record.sha256()}",style=MaterialTheme.typography.labelSmall)
     Text("Source capture: ${record.metadata().captureAt()} · imported: ${record.importedAt()}. OCR claims remain unverified.")
     record.ocr().flatMap{it.times()}.forEach{Text("${it.text()} · ${it.provenance()}",style=MaterialTheme.typography.labelSmall)}
     OutlinedTextField(transcripts[record.id()].orEmpty(),{transcripts=transcripts+(record.id() to it.take(100000))},Modifier.fillMaxWidth(),label={Text("Review / correct extracted text")})
    }
    else->{item{Text("Matching group names merge records into one incident. Change one record's group name to split it. Leave blank to save without an incident. Suggested groups are unverified; review each one.")}
     items(draft?.evidence().orEmpty(),key={it.id()}){record->Text(record.name());OutlinedTextField(groups[record.id()].orEmpty(),{groups=groups+(record.id() to it.take(1024))},Modifier.fillMaxWidth(),label={Text("Approved incident group")})}}
   }
  }
  FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   TextButton(onClick={onClose()}){Text("Cancel review")}
   TextButton(enabled=!busy,onClick=::back){Text("Back")}
   Button(enabled=!busy&&(step!=0||selected.isNotEmpty()),onClick={
    if(step==0){busy=true;message="";scope.launch{
     try{val prepared=controller.prepareImport(selected.toList());if(controller.current()){draft=prepared;transcripts=prepared.evidence().associate{it.id() to it.transcript()};step=1}}
     catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){message="Could not verify or extract every original. Nothing was registered. Reduce the selection or review the sources."}finally{busy=false}
    }}else if(step==1){
     var reviewed=checkNotNull(draft)
     reviewed.evidence().forEach{record->record.ocr().lastOrNull()?.let{ocr->reviewed=reviewed.reviseOcr(record.id(),ocr.id(),transcripts[record.id()].orEmpty(),EvidenceController.now())}}
     draft=reviewed;val suggestions=reviewed.suggestions();val suggested=mutableMapOf<String,String>()
     suggestions.forEachIndexed{index,suggestion->suggestion.evidenceIds().forEach{id->if(id !in suggested)suggested[id]="Suggested group ${index+1}"}}
     groups=reviewed.evidence().associate{it.id() to suggested[it.id()].orEmpty()};step=2
    }else{
     var reviewed=checkNotNull(draft)
     groups.entries.filter{it.value.isNotBlank()}.groupBy{it.value.trim()}.forEach{(title,entries)->
      reviewed=reviewed.upsertIncident(EvidenceCatalog.Incident(UUID.randomUUID().toString(),title,0,0,"Owner-reviewed import grouping","",emptyList(),entries.map{it.key},emptyList(),false,false,emptyList()),EvidenceController.now(),"OWNER_APPROVE_GROUP")
     }
     controller.saveReviewedImport(reviewed);onClose()
    }
   }){Text(when(step){0->"Run offline OCR";1->"Review groups";else->"Save approved evidence"})}
  }
 }}
}
