package uk.co.traynor.privategallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.evidence.*

/** All transient selections/search/forms belong to this authorised composition, never saved state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceLocker(controller:EvidenceController,onClose:()->Unit,onImport:()->Unit,
 onAi:(List<String>)->Unit,onExport:(EvidenceExportSelection)->Unit,onOpenArchive:()->Unit) {
 val state by controller.state.collectAsState()
 var page by remember(controller){mutableStateOf("Home")}
 var query by remember(controller){mutableStateOf("")}
 var from by remember(controller){mutableStateOf("")};var through by remember(controller){mutableStateOf("")}
 var archived by remember(controller){mutableStateOf(false)}
 var selected by remember(controller){mutableStateOf<Set<String>>(emptySet())}
 var originalsSelected by remember(controller){mutableStateOf<Set<String>>(emptySet())}
 var exportSelection by remember(controller){mutableStateOf<EvidenceExportSelection?>(null)}
 var evidenceId by remember(controller){mutableStateOf<String?>(null)}
 var incidentId by remember(controller){mutableStateOf<String?>(null)}
 var form by remember(controller){mutableStateOf<EvidenceForm?>(null)}
 var choice by remember(controller){mutableStateOf<String?>(null)}
 var suggestionEdit by remember(controller){mutableStateOf<EvidenceCatalog.Suggestion?>(null)}
 var confirmation by remember(controller){mutableStateOf<Pair<String,()->Unit>?>(null)}
 var viewOriginal by remember(controller){mutableStateOf<String?>(null)}
 var retired by remember(controller){mutableStateOf(false)}
 fun close(){if(retired)return;selected=emptySet();query="";from="";through="";form=null;choice=null;originalsSelected=emptySet();exportSelection=null;suggestionEdit=null;confirmation=null;viewOriginal=null;retired=true;controller.close();onClose()}
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 DisposableEffect(controller,lifecycle){
  val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)close()}
  val retirement=controller.access.onRetire{android.os.Handler(android.os.Looper.getMainLooper()).post{close()}}
  lifecycle.addObserver(observer);onDispose{retirement.close();lifecycle.removeObserver(observer);controller.close()}
 }
 LaunchedEffect(evidenceId){if(evidenceId!=null)withContext(Dispatchers.Main.immediate){controller.refresh()}}
 LaunchedEffect(controller){while(isActive){val live=withContext(Dispatchers.Main.immediate){if(!controller.current()){close();false}else true};if(!live)break;delay(100)}}
 if(retired||!controller.current())return
 val catalog=state.catalog
 val record=catalog?.evidence()?.firstOrNull{it.id()==evidenceId}
 val incident=catalog?.incidents()?.firstOrNull{it.id()==incidentId}
 fun change(label:String,action:(EvidenceCatalog)->EvidenceCatalog){if(controller.current())controller.mutate(label,action)}
 fun incidentForm(existing:EvidenceCatalog.Incident?=null){
  form=EvidenceForm(if(existing==null)"Create incident"else"Edit incident",listOf("Title","Description","Notes","Tags (comma separated)","Start (ISO date/time with offset, optional)","End (ISO date/time with offset, optional)"),
   listOf(existing?.title().orEmpty(),existing?.description().orEmpty(),existing?.notes().orEmpty(),existing?.tags()?.joinToString(", ").orEmpty(),existing?.startAt()?.takeIf{it>0}?.let{Instant.ofEpochMilli(it).toString()}.orEmpty(),existing?.endAt()?.takeIf{it>0}?.let{Instant.ofEpochMilli(it).toString()}.orEmpty())){v ->
   require(v[0].isNotBlank()){ "Incident title is required" };val id=existing?.id()?:UUID.randomUUID().toString()
   val updated=EvidenceCatalog.Incident(id,v[0],epoch(v[4]),epoch(v[5]),v[1],v[2],tags(v[3]),existing?.evidenceIds()?:emptyList(),existing?.events()?:emptyList(),existing?.manualOrder()?:false,existing?.archived()?:false,existing?.lineage()?:emptyList())
   change("Incident saved"){it.upsertIncident(updated,EvidenceController.now(),"OWNER_EDIT_INCIDENT")};incidentId=id;page="Incidents"
  }
 }
 BackHandler {when{viewOriginal!=null->viewOriginal=null;evidenceId!=null->evidenceId=null;incidentId!=null->incidentId=null;page!="Home"->page="Home";else->close()}}
 Dialog(onDismissRequest={close()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {
  Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth()) {Text("Evidence Locker",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);EvidenceSecurityControls();TextButton(onClick={close()}){Text("Close")}}
    Text(if(controller.access.owner==EvidenceCatalog.Owner.PRIMARY)"Primary · encrypted local evidence"else"Hidden · encrypted local evidence",style=MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {listOf("Home","Library","Incidents","Timeline").forEach{target->FilterChip(selected=page==target,onClick={page=target;evidenceId=null;incidentId=null},label={Text(target)})}}
    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(catalog==null){Text("Opening encrypted catalogue…");return@Column}
    val busy=state.busy
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("evidence-list"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     state.message?.let{item{Text(it,style=MaterialTheme.typography.bodySmall)}}
     if(record!=null) {
      item {Text(record.name(),style=MaterialTheme.typography.titleLarge);Text("Original reference · ${record.mime()} · ${record.size()} bytes")
       Text("SHA-256 ${record.sha256()}",style=MaterialTheme.typography.bodySmall)
       Text("Imported ${time(record.importedAt())}; vault added ${time(record.metadata().dateAddedAt())}; capture ${time(record.metadata().captureAt())}",style=MaterialTheme.typography.bodySmall)
       Text("Source claims: ${record.metadata().sourceName()} · ${record.metadata().width()} × ${record.metadata().height()}. Dates and metadata are claims, not proof.",style=MaterialTheme.typography.bodySmall)
       FlowRow {
        TextButton(enabled=!busy,onClick={viewOriginal=record.id()}){Text("View original")}
        TextButton(enabled=!busy,onClick={controller.verify(record.id())}){Text("Verify integrity")}
        TextButton(enabled=!busy,onClick={controller.extract(record.id())}){Text("Extract text")}
        TextButton(enabled=!busy,onClick={form=EvidenceForm("Rotate local extraction",listOf("Degrees: 0, 90, 180 or 270"),listOf("90")){v->val rotation=v[0].toInt();require(rotation in listOf(0,90,180,270));controller.extract(record.id(),rotation)}}){Text("Rotate extraction")}
        TextButton(enabled=!busy,onClick={form=EvidenceForm("Edit evidence",listOf("Name","Description","Notes","Tags (comma separated)","Participants (owner identified)"),listOf(record.name(),record.description(),record.notes(),record.tags().joinToString(", "),record.participants().joinToString(", "))){v->require(v[0].isNotBlank()){ "Evidence name is required" };change("Evidence saved"){it.upsertEvidence(EvidenceController.annotated(record,v[0],v[1],v[2],tags(v[3]),tags(v[4])),EvidenceController.now(),"OWNER_EDIT_EVIDENCE")}}}){Text("Edit")}
        TextButton(onClick={selected=setOf(record.id());choice="Assign"}){Text("Add to incident")}
       }
       state.integrity[record.id()]?.let{Text(it)}
       Text(record.description());Text("Owner notes: ${record.notes()}")
       record.metadata().embedded().forEach{(key,value)->Text("$key: $value",style=MaterialTheme.typography.bodySmall)}
      }
      items(record.ocr(),key={it.id()}){extraction ->
       ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
        Text("Unverified extracted text · ${extraction.engine()}");Text("Engine score ${if(extraction.confidence()<0)"unavailable"else"${extraction.confidence()}/100 (not factual certainty)"} · ${extraction.pageCount()} pages${if(extraction.partial())" · PARTIAL"else""}",style=MaterialTheme.typography.bodySmall)
        Text(extraction.text())
        extraction.times().forEach{hint->Text("Page ${hint.pageIndex()+1}: ${hint.text()} · ${hint.provenance()}",style=MaterialTheme.typography.bodySmall)}
        TextButton(enabled=!busy,onClick={form=EvidenceForm("Correct transcript — original extraction retained",listOf("Owner-corrected text"),listOf(record.corrections().lastOrNull{it.ocrId()==extraction.id()}?.text()?:extraction.text())){v->change("Correction saved separately"){it.reviseOcr(record.id(),extraction.id(),v[0],EvidenceController.now())}}}){Text("Review / correct")}
        record.corrections().filter{it.ocrId()==extraction.id()}.forEach{Text("Owner correction (${time(it.createdAt())}): ${it.text()}")}
       }}
      }
     } else if(incident!=null) {
      item {Text(incident.title(),style=MaterialTheme.typography.titleLarge);Text(incident.description());Text("Owner notes: ${incident.notes()}")
       Text("Range ${time(incident.startAt())} — ${time(incident.endAt())}")
       FlowRow {
        TextButton(enabled=!busy,onClick={incidentForm(incident)}){Text("Edit")}
        TextButton(enabled=!busy,onClick={change(if(incident.archived())"Restored"else"Archived"){it.archive(incident.id(),!incident.archived(),EvidenceController.now())}}){Text(if(incident.archived())"Restore"else"Archive")}
        TextButton(enabled=!busy,onClick={exportSelection=EvidenceExportSelection(listOf(incident.id()),emptyList())}){Text("Export")}
        TextButton(enabled=!busy&&incident.evidenceIds().isNotEmpty(),onClick={onAi(incident.evidenceIds())}){Text("AI analysis")}
        TextButton(enabled=!busy,onClick={choice="Merge"}){Text("Merge")}
        TextButton(enabled=!busy&&selected.any{it in incident.evidenceIds()},onClick={choice="Move"}){Text("Move selected to another incident")}
        TextButton(enabled=!busy&&selected.any{it in incident.evidenceIds()},onClick={val ids=selected.filter{it in incident.evidenceIds()};confirmation="Remove these selected incident references? Originals, notes and contributing lineage remain preserved." to {change("Selected references removed from incident"){it.unlink(ids,incident.id(),EvidenceController.now())};selected=emptySet()}}){Text("Remove selected from incident")}

        TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={form=EvidenceForm("Split selected evidence into new incident",listOf("New title"),listOf("Split incident")){v->change("Incident split"){it.split(incident.id(),selected.filter{it in incident.evidenceIds()},UUID.randomUUID().toString(),v[0],EvidenceController.now())};selected=emptySet()}}){Text("Split selected")}
        TextButton(enabled=!busy,onClick={confirmation="Delete this incident and its annotations? Original evidence remains in the vault." to {change("Incident deleted; originals retained"){it.deleteIncident(incident.id(),EvidenceController.now())};incidentId=null}}){Text("Delete incident")}
        TextButton(enabled=!busy,onClick={form=EvidenceForm("Add timeline entry",listOf("Title","Owner notes","Timestamp (ISO with offset; blank is uncertain)","Provenance: USER or INFERRED"),listOf("","","","USER")){v->
         require(v[3] in listOf("USER","INFERRED")){"Manual timestamps must remain owner-entered or inferred"}
         val event=EvidenceCatalog.Event(UUID.randomUUID().toString(),selected.filter{it in incident.evidenceIds()},epoch(v[2]),v[3],v[0],v[1])
         change("Timeline entry saved"){current->val fresh=current.incidents().single{it.id()==incident.id()};current.upsertIncident(withEvents(fresh,fresh.events()+event),EvidenceController.now(),"OWNER_ADD_EVENT")}
        }}){Text("Add event")}
       }
      }
      items(incident.evidenceIds(),key={it}){id->catalog.evidence().firstOrNull{it.id()==id}?.let{e->EvidenceRow(controller,e,state.integrity[e.id()],id in selected,{selected=if(id in selected)selected-id else selected+id},{evidenceId=id})
       TextButton(enabled=!busy,onClick={change("Reference removed from incident"){it.unlink(listOf(id),incident.id(),EvidenceController.now())}}){Text("Remove from incident")}
       e.ocr().flatMap{it.times()}.take(32).forEach{hint->
        TextButton(enabled=!busy,onClick={form=EvidenceForm("Review extracted timeline claim — unverified",listOf("Title","Owner notes","Timestamp (ISO offset; blank keeps uncertain)"),listOf(hint.text(),hint.provenance(),if(hint.epochMillis()>0)Instant.ofEpochMilli(hint.epochMillis()).toString()else "")){v->
         val entered=epoch(v[2]);val provenance=if(entered==hint.epochMillis())"EXTRACTED"else"USER"
         val event=EvidenceCatalog.Event(UUID.randomUUID().toString(),listOf(id),entered,provenance,v[0],"OCR page ${hint.pageIndex()+1}: ${hint.text()} · ${hint.provenance()}\nOwner review: ${v[1]}")
         change("Owner-reviewed extracted event saved"){c->val fresh=c.incidents().single{it.id()==incident.id()};c.upsertIncident(withEvents(fresh,fresh.events()+event),EvidenceController.now(),"OWNER_ACCEPT_EXTRACTED_TIME")}
        }}){Text("Review time: ${hint.text()}")}
       }
      }}
      items(catalog.timeline(incident.id()),key={it.id()}){event->
       ElevatedCard(Modifier.fillMaxWidth().testTag("evidence-event-${event.id()}")){Column(Modifier.padding(12.dp)){Text(event.title());Text("${time(event.timestamp())} · ${event.provenance()}");Text(event.notes())
        FlowRow {
         TextButton(enabled=!busy,onClick={form=EvidenceForm("Edit timeline entry",listOf("Title","Owner notes","Timestamp (ISO offset; blank is uncertain)","Provenance: USER or INFERRED"),listOf(event.title(),event.notes(),if(event.timestamp()>0)Instant.ofEpochMilli(event.timestamp()).toString()else "",if(event.provenance()=="INFERRED")"INFERRED"else"USER")){v->
          require(v[3] in listOf("USER","INFERRED")){"Edited timestamps must remain owner-entered or inferred"}
          val edited=EvidenceCatalog.Event(event.id(),event.evidenceIds(),epoch(v[2]),v[3],v[0],v[1]+"\nEdited from ${event.provenance()} annotation")
          change("Timeline entry edited"){c->val fresh=c.incidents().single{it.id()==incident.id()};c.upsertIncident(withEvents(fresh,fresh.events().map{if(it.id()==event.id())edited else it}),EvidenceController.now(),"OWNER_EDIT_EVENT")}
         }}){Text("Edit event")}
         TextButton(enabled=!busy,onClick={confirmation="Remove this timeline annotation? Original evidence remains unchanged." to {change("Timeline annotation removed"){c->val fresh=c.incidents().single{it.id()==incident.id()};c.upsertIncident(withEvents(fresh,fresh.events().filterNot{it.id()==event.id()}),EvidenceController.now(),"OWNER_REMOVE_EVENT")}}}){Text("Remove event")}
        }
        val displayed=catalog.timeline(incident.id());val position=displayed.indexOfFirst{it.id()==event.id()}
        FlowRow {TextButton(enabled=!busy&&position>0,onClick={change("Manual timeline order saved"){current->val fresh=current.timeline(incident.id());val at=fresh.indexOfFirst{it.id()==event.id()};if(at>0)current.moveEvent(incident.id(),event.id(),at-1,EvidenceController.now())else current}}){Text("Move earlier")}
         TextButton(enabled=!busy&&position>=0&&position<displayed.lastIndex,onClick={change("Manual timeline order saved"){current->val fresh=current.timeline(incident.id());val at=fresh.indexOfFirst{it.id()==event.id()};if(at>=0&&at<fresh.lastIndex)current.moveEvent(incident.id(),event.id(),at+1,EvidenceController.now())else current}}){Text("Move later")}}
       }}
      }
      items(incident.analysisIds().mapNotNull{id->catalog.analyses().firstOrNull{it.id()==id}},key={it.id()}){analysis->EvidenceSavedAdvisory(analysis,incident)}
     } else when(page) {
      "Home" -> {
       item {Text("${catalog.evidence().size} evidence records · ${catalog.incidents().count{!it.archived()}} active incidents",style=MaterialTheme.typography.titleMedium)
        Text("Originals stay encrypted in their originating vault. OCR, notes, inferred dates and AI advice remain distinct. A hash verifies matching bytes, not truth.");Text("On-device OCR uses an English/Latin model. Images and PDF pages are supported; UTF8 text/CSV extracts directly. Review uncertain text and dates.",style=MaterialTheme.typography.bodySmall)
        FlowRow {Button(enabled=!busy,onClick={originalsSelected=emptySet();choice="Import"}){Text("Add existing evidence")};TextButton(enabled=!busy,onClick=onImport){Text("Import document / image")};TextButton(enabled=!busy,onClick={incidentForm()}){Text("New incident")};TextButton(enabled=!busy,onClick=onOpenArchive){Text("Open encrypted export")};TextButton(enabled=!busy,onClick={controller.refresh()}){Text("Refresh originals")}}
       }
       items(catalog.suggestions(),key={it.id()}){suggestion->ElevatedCard(Modifier.fillMaxWidth().testTag("evidence-suggestion-${suggestion.id()}")){Column(Modifier.padding(12.dp)){
        Text("Suggested grouping · unverified");Text(suggestion.title());Text(suggestion.reasons().joinToString("; "))
        if(suggestion.incidentId().isNotEmpty())Text("Suggested target: ${catalog.incidents().firstOrNull{it.id()==suggestion.incidentId()}?.title().orEmpty()}")
        FlowRow {
         TextButton(enabled=!busy,onClick={
          if(suggestion.kind()=="ASSIGN")confirmation="Link ${suggestion.evidenceIds().size} related evidence records to the displayed incident? Nothing is merged automatically." to {change("Owner-approved references linked"){it.acceptSuggestion(suggestion,UUID.randomUUID().toString(),"",EvidenceController.now())}}
          else form=EvidenceForm("Review grouping suggestion",listOf("New incident title"),listOf(suggestion.title())){v->change("Owner-approved grouping saved"){it.acceptSuggestion(suggestion,UUID.randomUUID().toString(),v[0],EvidenceController.now())}}
         }){Text("Review / accept")}
         TextButton(enabled=!busy,onClick={suggestionEdit=suggestion;choice="Edit suggestion target"}){Text("Edit target")}
         TextButton(enabled=!busy,onClick={change("Suggestion rejected"){it.rejectSuggestion(suggestion.id(),EvidenceController.now())}}){Text("Reject")}
        }
       }}}
      }
      "Library" -> {
       item {
        OutlinedTextField(query,{query=it.take(1000)},label={Text("Search names, OCR, notes, tags, participants, incidents")},modifier=Modifier.fillMaxWidth().testTag("evidence-search"))
        FlowRow {OutlinedTextField(from,{from=it.take(10)},label={Text("From UTC date YYYY-MM-DD")});OutlinedTextField(through,{through=it.take(10)},label={Text("Through UTC date")})}
        Text("Date filters use original capture or import claims, not inferred message dates.",style=MaterialTheme.typography.bodySmall)
        FilterChip(selected=archived,onClick={archived=!archived},label={Text("Include archived incidents")})
        FlowRow {TextButton(enabled=!busy,onClick={originalsSelected=emptySet();choice="Import"}){Text("Add evidence")};TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={choice="Assign"}){Text("Add selected to incident")};TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={choice="Move"}){Text("Move to incident")};TextButton(enabled=!busy,onClick={exportSelection=EvidenceExportSelection(emptyList(),selected.toList())}){Text("Export selection")};TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={onAi(selected.toList())}){Text("AI analysis")};TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={confirmation="Remove selected evidence records and annotations? Encrypted originals and Recently Deleted remain unchanged." to {val ids=selected.toList();change("Evidence references removed; originals retained"){c->ids.fold(c){a,id->a.removeEvidence(id,EvidenceController.now())}};selected=emptySet()}}){Text("Remove references")}}
       }
       val dates=runCatching{dateRange(from,through)}.getOrNull()
       if(dates==null)item{Text("Enter valid UTC dates, or leave them blank.",color=MaterialTheme.colorScheme.error)}
       else items(catalog.search(query,dates.first,dates.second,archived),key={it.id()}){e->EvidenceRow(controller,e,state.integrity[e.id()],e.id() in selected,{selected=if(e.id() in selected)selected-e.id()else selected+e.id()},{evidenceId=e.id()})}
      }
      else -> {
       item {FlowRow {TextButton(enabled=!busy,onClick={incidentForm()}){Text("New incident")};TextButton(enabled=!busy,onClick={exportSelection=EvidenceExportSelection(emptyList(),selected.toList())}){Text("Export selection")};FilterChip(selected=archived,onClick={archived=!archived},label={Text("Include archived")})}
        OutlinedTextField(query,{query=it.take(1000)},label={Text("Search incidents, timeline and notes")},modifier=Modifier.fillMaxWidth())
        FlowRow {OutlinedTextField(from,{from=it.take(10)},label={Text("From UTC date YYYY-MM-DD")});OutlinedTextField(through,{through=it.take(10)},label={Text("Through UTC date")})};Text("Unknown dates remain visible and labelled uncertain.",style=MaterialTheme.typography.bodySmall)}
       val range=runCatching{dateRange(from,through)}.getOrNull()
       if(range==null)item{Text("Enter valid UTC dates, or leave blank.",color=MaterialTheme.colorScheme.error)}
       val groups=catalog.incidents().filter{g->range!=null&&(archived||!g.archived())&&(query.isBlank()||(listOf(g.title(),g.description(),g.notes(),g.tags().joinToString(" "))+g.events().flatMap{listOf(it.title(),it.notes())}).any{v->v.contains(query,true)})&&(g.startAt()==0L||((range.first==0L||(if(g.endAt()>0)g.endAt()else g.startAt())>=range.first)&&(range.second==0L||g.startAt()<=range.second)))}
       items(groups.sortedBy{if(it.startAt()>0)it.startAt()else Long.MAX_VALUE},key={it.id()}){group->ElevatedCard(Modifier.fillMaxWidth().clickable{incidentId=group.id();selected=emptySet()}){Column(Modifier.padding(12.dp)){Text(group.title(),style=MaterialTheme.typography.titleMedium);Text("${group.evidenceIds().size} evidence · ${time(group.startAt())}${if(group.archived())" · archived"else""}");Text(group.description());if(page=="Timeline")catalog.timeline(group.id()).filter{event->event.timestamp()==0L||range!=null&&(range.first==0L||event.timestamp()>=range.first)&&(range.second==0L||event.timestamp()<=range.second)}.forEach{Text("${time(it.timestamp())} · ${it.provenance()} · ${it.title()}")}}}}
      }
     }
    }
   }
  }
 }
 if(choice=="Import")GalleryAlertDialog(onDismissRequest={choice=null;originalsSelected=emptySet()},title={EvidenceDialogTitle("Select existing encrypted originals")},text={Column{
  Text("Review up to 128 originals to register in this vault. Originals remain unchanged. Extraction follows registration; interrupted batches can contain committed records.")
  LazyColumn(Modifier.heightIn(max=400.dp)){items(state.originals,key={it.item.id}){original->
   val id=original.item.id
   Row(Modifier.fillMaxWidth().clickable{originalsSelected=if(id in originalsSelected)originalsSelected-id else if(originalsSelected.size<128)originalsSelected+id else originalsSelected}){
    Checkbox(id in originalsSelected,{checked->originalsSelected=if(!checked)originalsSelected-id else if(originalsSelected.size<128)originalsSelected+id else originalsSelected},modifier=Modifier.semantics{contentDescription="Register original ${original.item.displayName}"})
    Column{Text(original.item.displayName);Text("${original.item.mimeType} · ${original.item.plaintextSize} bytes",style=MaterialTheme.typography.bodySmall)}
   }
  }}
  Text("${originalsSelected.size} / 128 selected")
 }},confirmButton={TextButton(enabled=originalsSelected.isNotEmpty()&&!state.busy,onClick={val ids=originalsSelected.toList();choice=null;originalsSelected=emptySet();controller.registerMany(ids)}){Text("Register selected originals")}},dismissButton={TextButton(onClick={choice=null;originalsSelected=emptySet()}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
 exportSelection?.let{initial->if(catalog!=null)EvidenceExportSelectionDialog(catalog,initial,onClose={exportSelection=null},onContinue={selection->exportSelection=null;onExport(selection)})}
 choice?.takeIf{it!="Import"}?.let{kind->
  val options=catalog?.incidents()?.filter{!it.archived()&&(kind !in listOf("Merge","Move")||it.id()!=incidentId)}?.map{it.id() to it.title()}.orEmpty()
  GalleryAlertDialog(onDismissRequest={choice=null},title={EvidenceDialogTitle(when(kind){"Merge"->"Merge this incident into…";else->"Choose incident"})},text={LazyColumn(Modifier.heightIn(max=400.dp)){items(options,key={it.first}){(id,name)->TextButton(onClick={
   choice=null;when(kind){"Merge"->{val source=incidentId;confirmation="Merge this incident into $name? Source incident will be archived and source lineage retained." to {if(source!=null)change("Incidents merged"){it.merge(id,listOf(source),EvidenceController.now())};incidentId=id}};"Edit suggestion target"->{val suggestion=suggestionEdit;suggestionEdit=null;if(suggestion!=null)confirmation="Apply the edited suggestion to $name? Only evidence references will be linked." to {change("Owner-edited suggestion applied"){it.rejectSuggestion(suggestion.id(),EvidenceController.now()).assign(suggestion.evidenceIds(),id,false,EvidenceController.now())}}};else->{val ids=selected.toList();change("Evidence assigned"){it.assign(ids,id,kind=="Move",EvidenceController.now())};selected=emptySet()}}
  }){Text(name)}}}},confirmButton={TextButton(onClick={choice=null}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
 }
 form?.let{value->EvidenceFormDialog(value){form=null}}
 confirmation?.let{(text,action)->GalleryAlertDialog(onDismissRequest={confirmation=null},title={EvidenceDialogTitle("Confirm change")},text={Text(text)},confirmButton={TextButton(onClick={confirmation=null;if(controller.current())action()}){Text("Confirm")}},dismissButton={TextButton(onClick={confirmation=null}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))}
 viewOriginal?.let{id->EvidenceOriginalView(controller,id){viewOriginal=null}}
}

@Composable private fun EvidenceRow(controller:EvidenceController,e:EvidenceCatalog.Evidence,integrity:String?,selected:Boolean,onSelect:()->Unit,onOpen:()->Unit) {
 ElevatedCard(Modifier.fillMaxWidth()) {
  Column(Modifier.padding(8.dp)) {
   Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
    Checkbox(selected,{onSelect()},modifier=Modifier.semantics{contentDescription="Select evidence ${e.name()}"})
    EvidenceThumbnail(controller,e.id())
    Column(Modifier.weight(1f).clickable(onClick=onOpen).padding(8.dp)) {
     Text(e.name(),style=MaterialTheme.typography.titleMedium)
     Text(if(e.ocr().isEmpty())"No extracted text"else"Unverified transcript",style=MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick=onOpen){Text("Open")}
   }
   if(e.tags().isNotEmpty())Text(e.tags().joinToString(" · "))
   integrity?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
  }
 }
}
private class EvidenceForm(val title:String,val labels:List<String>,val initial:List<String>,val save:(List<String>)->Unit)
@Composable private fun EvidenceFormDialog(form:EvidenceForm,onClose:()->Unit) {
 val values=remember(form){form.initial.map{mutableStateOf(it)}};var error by remember(form){mutableStateOf(false)}
 GalleryAlertDialog(onDismissRequest=onClose,title={EvidenceDialogTitle(form.title)},text={Column{LazyColumn(Modifier.heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(form.labels.indices.toList()){index->OutlinedTextField(values[index].value,{values[index].value=it.take(if(form.labels[index].contains("text",true))100000 else 65536);error=false},label={Text(form.labels[index])},modifier=Modifier.fillMaxWidth())}};if(error)Text("Check values, lengths and timestamp format.",color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={runCatching{form.save(values.map{it.value})}.onSuccess{onClose()}.onFailure{error=true}}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
}
private fun tags(value:String)=value.split(',').map{it.trim()}.filter{it.isNotEmpty()}.distinct()
private fun epoch(value:String)=if(value.isBlank())0L else Instant.parse(value.trim()).toEpochMilli().also{require(it>=0)}
private fun time(value:Long)=if(value==0L)"Unknown / uncertain"else Instant.ofEpochMilli(value).toString()
private fun dateRange(from:String,through:String):Pair<Long,Long>{val a=if(from.isBlank())0 else LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();val b=if(through.isBlank())0 else LocalDate.parse(through).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()-1;require(a>=0&&b>=0&&(a==0L||b==0L||b>=a));return a to b}
private fun withEvents(i:EvidenceCatalog.Incident,events:List<EvidenceCatalog.Event>)=EvidenceCatalog.Incident(i.id(),i.title(),i.startAt(),i.endAt(),i.description(),i.notes(),i.tags(),i.evidenceIds(),events,i.manualOrder(),i.archived(),i.lineage())

/** Selection contains only explicitly chosen IDs; incident attachments are resolved by the export plan. */
internal data class EvidenceExportSelection(val incidentIds:List<String>,val evidenceIds:List<String>)

@Composable private fun EvidenceExportSelectionDialog(catalog:EvidenceCatalog,initial:EvidenceExportSelection,onClose:()->Unit,onContinue:(EvidenceExportSelection)->Unit) {
 var incidents by remember(initial){mutableStateOf(initial.incidentIds.toSet())}
 var evidence by remember(initial){mutableStateOf(initial.evidenceIds.toSet())}
 val attached=(evidence+catalog.incidents().filter{it.id() in incidents}.flatMap{it.evidenceIds()+it.events().flatMap{e->e.evidenceIds()}}).toSet()
 val valid=(incidents.isNotEmpty()||evidence.isNotEmpty())&&incidents.size<=128&&attached.size<=128
 GalleryAlertDialog(onDismissRequest=onClose,title={EvidenceDialogTitle("Select export contents")},text={Column{
  Text("Choose incidents and standalone evidence explicitly. Up to 128 incidents and 128 attached originals; no selection is shortened automatically.")
  LazyColumn(Modifier.heightIn(max=400.dp)){
   items(catalog.incidents(),key={"incident-${it.id()}"}){incident->Row{
    Checkbox(incident.id() in incidents,{checked->incidents=if(checked)incidents+incident.id() else incidents-incident.id()},modifier=Modifier.semantics{contentDescription="Export incident ${incident.title()}"})
    Text(incident.title())
   }}
   items(catalog.evidence(),key={"evidence-${it.id()}"}){record->Row{
    Checkbox(record.id() in evidence,{checked->evidence=if(checked)evidence+record.id() else evidence-record.id()},modifier=Modifier.semantics{contentDescription="Export evidence ${record.name()}"})
    Text(record.name())
   }}
  }
  Text("${incidents.size} incidents · ${evidence.size} explicit records · ${attached.size} attached originals")
  if(!valid)Text("Choose contents within the limits to continue.")
 }},confirmButton={TextButton(enabled=valid,onClick={onContinue(EvidenceExportSelection(incidents.toList(),evidence.toList()))}){Text("Review export")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
}

@Composable private fun EvidenceSavedAdvisory(analysis:EvidenceCatalog.Analysis,incident:EvidenceCatalog.Incident) {
 val details=remember(analysis){runCatching{EvidenceAi.details(analysis)}.getOrNull()}
 Column(Modifier.fillMaxWidth().testTag("evidence-advisory-${analysis.id()}")){
  Text("AI-generated advisory · unverified · ${analysis.model()}",style=MaterialTheme.typography.titleMedium)
  Text(details?.summary?:analysis.text())
  Text("Receipt ${analysis.id()} · ${analysis.inputTokens()} input / ${analysis.outputTokens()} output tokens · input digest ${analysis.inputDigest()}",style=MaterialTheme.typography.bodySmall)
  details?.let{body->
   Text("Status ${body.status}${if(body.refusal)" · provider refusal"else""} · cached input ${body.cachedInputTokens}",style=MaterialTheme.typography.bodySmall)
   body.suggestions.forEach{Text("Advisory grouping: ${it.title} · ${it.reasons.joinToString("; ")}");EvidenceAdvisoryReferences(analysis,incident,it.evidenceIds)}
   body.events.forEach{Text("Advisory event: ${it.title} · ${it.dateTimeQuote?:"Unknown / uncertain"} · ${it.provenance} · ${it.uncertainty}");EvidenceAdvisoryReferences(analysis,incident,it.evidenceIds)}
  }
  EvidenceAdvisoryReferences(analysis,incident,analysis.sourceReferences().map{it.evidenceId()})
 }
}

@Composable private fun EvidenceAdvisoryReferences(analysis:EvidenceCatalog.Analysis,incident:EvidenceCatalog.Incident,ids:List<String>) {
 ids.forEach{id->
  val reference=analysis.sourceReferences().singleOrNull{it.evidenceId()==id}
  if(reference==null)Text("Historical advisory source $id · fingerprint unavailable in legacy record",style=MaterialTheme.typography.bodySmall)
  else {
   val source=reference.fingerprint()
   Text("${if(reference.evidenceId() in incident.evidenceIds())"Attached advisory source"else"Historical / unattached advisory source"} ${reference.evidenceId()} · source ${source.sourceId()} · ${source.mime()} · ${source.size()} bytes · SHA-256 ${source.sha256()}${if(source.vaultOnly())" · vault-only"else""}",style=MaterialTheme.typography.bodySmall)
  }
 }
}
