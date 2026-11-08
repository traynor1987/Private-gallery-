package uk.co.traynor.privategallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.evidence.*

/** All transient selections/search/forms belong to this authorised composition, never saved state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceLocker(controller:EvidenceController,onClose:()->Unit,onImport:()->Unit,
 onAi:(List<String>)->Unit,onExport:(EvidenceExportSelection)->Unit,onOpenArchive:()->Unit,startImportReview:Boolean=false,onAiReport:(List<String>)->Unit=onAi,onReportExport:(String)->Unit={},onImportPhotos:()->Unit=onImport) {
 val state by controller.state.collectAsState()
 var page by remember(controller){mutableStateOf("Home")}
 var query by remember(controller){mutableStateOf("")}
 var from by remember(controller){mutableStateOf<LocalDate?>(null)};var through by remember(controller){mutableStateOf<LocalDate?>(null)}
 var dateFilters by remember(controller){mutableStateOf(false)}
 var newestFirst by remember(controller){mutableStateOf(false)}
 var archived by remember(controller){mutableStateOf(false)}
 var selected by remember(controller){mutableStateOf<Set<String>>(emptySet())}
 var originalsSelected by remember(controller){mutableStateOf<Set<String>>(emptySet())}
 var exportSelection by remember(controller){mutableStateOf<EvidenceExportSelection?>(null)}
 var evidenceId by remember(controller){mutableStateOf<String?>(null)}
 var incidentId by remember(controller){mutableStateOf<String?>(null)}
 var form by remember(controller){mutableStateOf<EvidenceForm?>(null)}
 var choice by remember(controller){mutableStateOf<String?>(if(startImportReview)"Import"else null)}
 var suggestionEdit by remember(controller){mutableStateOf<EvidenceCatalog.Suggestion?>(null)}
 var confirmation by remember(controller){mutableStateOf<Pair<String,()->Unit>?>(null)}
 var viewOriginal by remember(controller){mutableStateOf<String?>(null)}
 var viewReport by remember(controller){mutableStateOf<EvidenceCatalog.Analysis?>(null)}
 var retired by remember(controller){mutableStateOf(false)}
 var more by remember(controller){mutableStateOf(false)}
 fun close(){if(retired)return;viewReport=null;selected=emptySet();query="";from=null;through=null;form=null;choice=null;originalsSelected=emptySet();exportSelection=null;suggestionEdit=null;confirmation=null;viewOriginal=null;retired=true;controller.close();onClose()}
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
 val incidentSuggestions=remember(state.chronology,catalog) {
  val grouped=catalog?.incidents()?.flatMap{it.evidenceIds()}?.toSet().orEmpty()
  val live=catalog?.evidence()?.map{it.id()}?.toSet().orEmpty()
  val pending=state.chronology?.review.orEmpty()
   .filter{it.reason==EvidenceChronology68.ReviewReason.SUGGESTED_INCIDENT}.map{it.evidenceIds.toSet()}.toSet()
  state.chronology?.incidents.orEmpty().filter{suggestion->
   suggestion.evidenceIds.all{it in live && it !in grouped} && suggestion.evidenceIds.toSet() in pending
  }
 }
 val incident=catalog?.incidents()?.firstOrNull{it.id()==incidentId}
 val viewer=rememberEvidenceViewer69(evidenceId)
 var toolsExpanded by remember(page,evidenceId,incidentId){mutableStateOf(false)}
 fun change(label:String,action:(EvidenceCatalog)->EvidenceCatalog){if(controller.current())controller.mutate(label,action)}
 fun incidentForm(existing:EvidenceCatalog.Incident?=null){
  form=EvidenceForm(if(existing==null)"Create incident"else"Edit incident",listOf("Title","Description","Notes","Tags (comma separated)","Start (ISO date/time with offset, optional)","End (ISO date/time with offset, optional)"),
   listOf(existing?.title().orEmpty(),existing?.description().orEmpty(),existing?.notes().orEmpty(),existing?.tags()?.joinToString(", ").orEmpty(),existing?.startAt()?.takeIf{it>0}?.let{Instant.ofEpochMilli(it).toString()}.orEmpty(),existing?.endAt()?.takeIf{it>0}?.let{Instant.ofEpochMilli(it).toString()}.orEmpty())){v ->
   require(v[0].isNotBlank()){ "Incident title is required" };val id=existing?.id()?:UUID.randomUUID().toString()
   val updated=EvidenceCatalog.Incident(id,v[0],epoch(v[4]),epoch(v[5]),v[1],v[2],tags(v[3]),existing?.evidenceIds()?:emptyList(),existing?.events()?:emptyList(),existing?.manualOrder()?:false,existing?.archived()?:false,existing?.lineage()?:emptyList())
   change("Incident saved"){it.upsertIncident(updated,EvidenceController.now(),"OWNER_EDIT_INCIDENT")};incidentId=id;page="Timeline"
  }
 }
 val navigateBack:()->Unit={when{viewOriginal!=null->viewOriginal=null;evidenceId!=null->evidenceId=null;incidentId!=null->incidentId=null;page!="Home"->page="Home";else->close()}}
 EvidencePage(onBack=navigateBack) {
  Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().padding(16.dp).testTag("evidence-locker-68"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    val security=novaSecurityActions()
    NovaAppHeader("Evidence Locker",security?.space,
     onHome=security?.home,onBack=if(page!="Home"||evidenceId!=null||incidentId!=null)navigateBack else null,
     onLock=security?.lock,onPanic=security?.panic,
     actions={
      TextButton(onClick={more=true}){Text("More")}
      if(security==null)TextButton(onClick={close()}){Text("Close")}
     })
    Text(if(controller.access.owner==EvidenceCatalog.Owner.PRIMARY)"Primary · encrypted local evidence"else"Hidden · encrypted local evidence",style=MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {listOf("Home","Evidence","Timeline","Nova AI").forEach{target->FilterChip(selected=page==target,onClick={page=target;evidenceId=null;incidentId=null},label={Text(target)})}}
    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(catalog==null){Text("Opening encrypted catalogue…");return@Column}
    val busy=state.busy
    val zone=ZoneId.systemDefault()
    val timelineRows=if(page=="Timeline"&&record==null&&incident==null)remember(catalog,state.chronology,state.captureTimes,query,from,through,newestFirst,zone) {
     val screenshots=state.chronology?.screenshots.orEmpty().associateBy{it.evidenceId}
     val records=catalog.evidence().associateBy{it.id()}
     val captures=records.values.filter{it.mime().startsWith("image/") && (query.isBlank() ||
      it.name().contains(query,true) || it.transcript().contains(query,true))}
      .map{EvidenceTimelineItem.Screenshot(it,screenshots[it.id()]?.capture,state.captureTimes[it.id()],zone)}
     val messages=state.chronology?.messages.orEmpty().filter{message->
      message.locations.all{it.evidenceId in records} &&
       (query.isBlank()||message.text.contains(query,true)||message.sender?.contains(query,true)==true)
     }.map{EvidenceTimelineItem.Message(it,zone)}
     val order=compareBy<EvidenceTimelineItem>({it.date},{it.clock},{it.key})
     (captures+messages).filter{row->
      val date=row.date
      date==null||((from==null||!date.isBefore(from))&&(through==null||!date.isAfter(through)))
     }.sortedWith(compareBy<EvidenceTimelineItem>{it.date==null}
      .then(if(newestFirst)order.reversed() else order))
    }else emptyList()
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("evidence-list"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     state.message?.let{item{Text(it,style=MaterialTheme.typography.bodySmall)}}
     if(record!=null) {
      evidenceViewer69(viewer,controller,record,state.chronology,busy,state.integrity[record.id()],catalog.rejectedSuggestions(),
       onOpenOriginal={viewOriginal=record.id()},
       onEdit={form=EvidenceForm("Edit evidence",listOf("Name","Description","Notes","Tags (comma separated)","Participants (owner identified)"),
        listOf(record.name(),record.description(),record.notes(),record.tags().joinToString(", "),record.participants().joinToString(", "))){values->
        require(values[0].isNotBlank()){"Evidence name is required"}
        change("Evidence saved"){it.upsertEvidence(EvidenceController.annotated(record,values[0],values[1],values[2],tags(values[3]),tags(values[4])),EvidenceController.now(),"OWNER_EDIT_EVIDENCE")}
       }},
       onAssign={selected=setOf(record.id());choice="Assign"},
       onRotate={form=EvidenceForm("Rotate local extraction",listOf("Degrees: 0, 90, 180 or 270"),listOf("90")){values->
        val rotation=values[0].toInt()
        require(rotation in listOf(0,90,180,270))
        controller.extract(record.id(),rotation)
       }},
       onCorrect={extraction,initial->
        form=EvidenceForm("Correct transcript — original extraction retained",listOf("Owner-corrected text"),listOf(initial)){values->
         change("Correction saved separately"){it.reviseOcr(record.id(),extraction.id(),values[0],EvidenceController.now())}
        }
       },
      )
     } else if(incident!=null) {
      item {Text(incident.title(),style=MaterialTheme.typography.titleLarge);Text(incident.description());Text("Owner notes: ${incident.notes()}")
       Text("Range ${time(incident.startAt())} — ${time(incident.endAt())}")
       FlowRow {
        TextButton(enabled=!busy,onClick={incidentForm(incident)}){Text("Edit")}
        TextButton(enabled=!busy,onClick={change(if(incident.archived())"Restored"else"Archived"){it.archive(incident.id(),!incident.archived(),EvidenceController.now())}}){Text(if(incident.archived())"Restore"else"Archive")}
        TextButton(enabled=!busy&&incident.evidenceIds().isNotEmpty(),onClick={onAi(incident.evidenceIds())}){Text("AI analysis")};TextButton(enabled=!busy&&incident.evidenceIds().isNotEmpty(),onClick={onAiReport(incident.evidenceIds())}){Text("Behaviour report*")}
        TextButton(onClick={toolsExpanded=!toolsExpanded}){Text(if(toolsExpanded)"Hide incident tools" else "Incident tools")}
        if(toolsExpanded){
         TextButton(enabled=!busy,onClick={exportSelection=EvidenceExportSelection(listOf(incident.id()),emptyList())}){Text("Export")}
        TextButton(enabled=!busy,onClick={choice="Merge"}){Text("Merge")}
        TextButton(enabled=!busy&&selected.any{it in incident.evidenceIds()},onClick={choice="Move"}){Text("Move selected to another incident")}
        TextButton(enabled=!busy&&selected.any{it in incident.evidenceIds()},onClick={val ids=selected.filter{it in incident.evidenceIds()};confirmation="Remove these selected incident references? Originals, notes and contributing lineage remain preserved." to {change("Selected references removed from incident"){it.unlink(ids,incident.id(),EvidenceController.now())};selected=emptySet()}}){Text("Remove selected from incident")}

        TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={form=EvidenceForm("Split selected evidence into new incident",listOf("New title"),listOf("Split incident")){v->change("Incident split"){it.split(incident.id(),selected.filter{it in incident.evidenceIds()},UUID.randomUUID().toString(),v[0],EvidenceController.now())};selected=emptySet()}}){Text("Split selected")}
        TextButton(enabled=!busy,onClick={confirmation="Delete this incident and its annotations? Original evidence remains in the vault." to {change("Incident deleted; originals retained"){it.deleteIncident(incident.id(),EvidenceController.now())};incidentId=null}}){Text("Delete incident")}
        }
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
       item {
        EvidenceDashboardOverview(catalog.evidence().size,catalog.incidents().count{!it.archived()},busy,query,
         onQuery={query=it.take(1000)},onImport=onImport,
         onExisting={originalsSelected=emptySet();choice="Import"},onLibrary={page="Evidence"},onTimeline={page="Timeline"},
         onAi={query="";page="Nova AI"},onNewIncident={incidentForm()},onArchive=onOpenArchive,onRefresh={controller.refresh()},
         onConnect={choice="Connect collections"},onPhotos=onImportPhotos,onReview={choice="Review queue"})
       }
       item {
        EvidenceConnectedSummary(state,suggestionCount=incidentSuggestions.size,onRetry=controller::syncCollections,
         onPause=controller::cancelCollectionSync)
       }
       if(query.isBlank()) {
        item{Text("Recent evidence",style=MaterialTheme.typography.titleMedium)}
        items(catalog.evidence().sortedByDescending{it.importedAt()}.take(3),key={"recent-evidence-${it.id()}"}){entry->
         EvidenceRow(controller,entry,null,entry.id() in selected,
          {selected=if(entry.id() in selected)selected-entry.id()else selected+entry.id()},{evidenceId=entry.id()})
        }
       }
       if(query.isNotBlank()) {
        items(catalog.search(query,0L,0L,false),key={"search-${it.id()}"}){e->EvidenceRow(controller,e,state.integrity[e.id()],e.id() in selected,{selected=if(e.id() in selected)selected-e.id()else selected+e.id()},{evidenceId=e.id()})}
       } else {
        item {Text("Recent incidents",style=MaterialTheme.typography.titleMedium)}
        val recent=catalog.incidents().filter{!it.archived()}.asReversed().take(3)
        if(recent.isEmpty())item{Text("Create an incident to bring related evidence together.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(recent,key={"recent-${it.id()}"}){group->
         ElevatedCard(onClick={incidentId=group.id();selected=emptySet()},modifier=Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
           Text(group.title(),style=MaterialTheme.typography.titleMedium)
           Text("${group.evidenceIds().size} evidence records",style=MaterialTheme.typography.bodyMedium)
           if(group.description().isNotBlank())Text(group.description(),style=MaterialTheme.typography.bodySmall,maxLines=2)
          }
         }
        }
       }
       if(catalog.suggestions().isNotEmpty())item{Text("Group suggestions",style=MaterialTheme.typography.titleMedium)}
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
      "Nova AI" -> {
       item {
        EvidenceWorkflowHeading("Ask Nova AI","Choose the screenshots Nova may analyse")
        TextButton(onClick={page="Evidence"}){Text("Choose evidence")}
        TextButton(enabled=!busy,onClick={onAi(emptyList())}){Text("AI settings")}
        TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={onAiReport(selected.toList())}){Text("Ask about selected evidence")}
        Text("You can review the exact content before allowing any cloud request.",style=MaterialTheme.typography.bodySmall)
       }
       item{Text("Saved encrypted AI reports*",style=MaterialTheme.typography.titleLarge);Text(EvidenceAiReport.DISCLAIMER)}
       val reports=catalog.analyses().filter{it.task()==EvidenceAi.Task.EVIDENCE_REPORT.name}.sortedByDescending{it.at()}
       if(reports.isEmpty())item{Text("No saved reports. Select records in Evidence, choose Ask Nova AI, review the exact payload, then explicitly confirm any request.")}
       items(reports,key={it.id()}){analysis->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
        Text("AI evidence report* · ${time(analysis.at())}")
        Text("${analysis.evidenceIds().size} selected evidence references · ${analysis.model()}",style=MaterialTheme.typography.bodySmall)
        TextButton(enabled=!busy,onClick={viewReport=analysis}){Text("Open report ${analysis.id().take(8)}")}
       }}}
      }
      "Information" -> {
       item {EvidenceWorkflowHeading("About your evidence","Local originals · separate annotations")}
       item {Text("Originals stay encrypted in their originating vault. OCR, notes, inferred dates and AI advice remain distinct. A hash verifies matching bytes, not truth.")}
       item {Text("On-device OCR uses an English/Latin model. Images and PDF pages are supported; UTF8 text/CSV extracts directly. Review uncertain text and dates.")}
       item {Text("AI analysis is optional. Select records in Evidence, then review the exact provider payload, images, route and cost before explicitly consenting to send.")}
       item {Text("Exports require a separate content review. Readable exports leave the encrypted vault and require explicit confirmation.")}
      }
      "Evidence" -> {
       item {
        Text("Select evidence for an incident, export or optional AI analysis.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query,{query=it.take(1000)},label={Text("Search names, OCR, notes, tags, participants, incidents")},modifier=Modifier.fillMaxWidth().testTag("evidence-search"))
        TextButton(onClick={dateFilters=!dateFilters}){Text(if(dateFilters)"Hide filters" else "Filters")}
        if(dateFilters) {
         FlowRow {EvidenceDateField("From date",from){from=it};EvidenceDateField("Through date",through){through=it}}
         Text("Filters use the screenshot or import date.",style=MaterialTheme.typography.bodySmall)
         FilterChip(selected=archived,onClick={archived=!archived},label={Text("Include archived incidents")})
        }
        FlowRow {
         TextButton(enabled=!busy,onClick={originalsSelected=emptySet();choice="Import"}){Text("Add evidence")}
         TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={onAiReport(selected.toList())}){Text("Ask Nova AI")}
         TextButton(onClick={toolsExpanded=!toolsExpanded}){Text(if(toolsExpanded)"Hide evidence tools" else "Evidence tools")}
        }
        if(toolsExpanded)FlowRow {
         TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={choice="Assign"}){Text("Add selected to incident")}
         TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={choice="Move"}){Text("Move to incident")}
         TextButton(enabled=!busy,onClick={exportSelection=EvidenceExportSelection(emptyList(),selected.toList())}){Text("Export selection")}
         TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={onAi(selected.toList())}){Text("AI analysis")}
         TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={confirmation="Remove selected evidence records and annotations? Encrypted originals remain in the Vault." to {
          controller.removeEvidenceReferences(selected.toList());selected=emptySet()
         }}){Text("Remove references")}
        }
       }
       val dates=runCatching{evidenceLocalDateRange(from,through,ZoneId.systemDefault())}.getOrNull()
       if(dates==null)item{Text("Choose an end date on or after the start date.",color=MaterialTheme.colorScheme.error)}
       else items(catalog.search(query,dates.first,dates.second,archived),key={it.id()}){e->EvidenceRow(controller,e,state.integrity[e.id()],e.id() in selected,{selected=if(e.id() in selected)selected-e.id()else selected+e.id()},{evidenceId=e.id()})}
      }
      else -> {
       item {FlowRow {TextButton(enabled=!busy,onClick={incidentForm()}){Text("New incident")};TextButton(enabled=!busy,onClick={exportSelection=EvidenceExportSelection(emptyList(),selected.toList())}){Text("Export selection")};FilterChip(selected=archived,onClick={archived=!archived},label={Text("Include archived")})}
        OutlinedTextField(query,{query=it.take(1000)},label={Text("Search incidents, timeline and notes")},modifier=Modifier.fillMaxWidth())
        TextButton(onClick={dateFilters=!dateFilters}){Text(if(dateFilters)"Hide filters" else "Filters")}
        if(dateFilters){
         FlowRow {EvidenceDateField("From date",from){from=it};EvidenceDateField("Through date",through){through=it}}
         Text(state.conversationZone?.let{"Conversation time zone · ${it.id}"}?:"Conversation time zone · unknown",style=MaterialTheme.typography.bodySmall)
         if(state.conversationZone==null)TextButton(enabled=!busy,onClick={
          val zone=ZoneId.systemDefault()
          confirmation="Use ${zone.id} for conversation times in this Space when a screenshot does not show its time zone? Leave it unknown if the conversation may use another zone." to {controller.setConversationZone(zone)}
         }){Text("Use phone time zone")}
         else TextButton(enabled=!busy,onClick={controller.setConversationZone(null)}){Text("Clear conversation time zone")}
        }
        Text("Unknown dates remain visible and labelled uncertain.",style=MaterialTheme.typography.bodySmall)}
       val range=runCatching{evidenceLocalDateRange(from,through,ZoneId.systemDefault())}.getOrNull()
       if(range==null)item{Text("Choose an end date on or after the start date.",color=MaterialTheme.colorScheme.error)}
       if(page=="Timeline"){
        item {
         Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
          Text("Evidence timeline",style=MaterialTheme.typography.titleMedium)
          TextButton(onClick={newestFirst=!newestFirst}){Text(if(newestFirst)"Newest first" else "Oldest first")}
         }
         if(timelineRows.isEmpty())Text("No screenshots or reconstructed messages match this view.")
        }
        if(range!=null)items(timelineRows,key={it.key}){row->
         when(row){
          is EvidenceTimelineItem.Message->EvidenceMessageCard(row.message){evidenceId=it}
          is EvidenceTimelineItem.Screenshot->EvidenceScreenshotEvent(row,busy,{evidenceId=row.record.id()}){
           val id=row.record.id()
           form=EvidenceForm("Correct screenshot capture date",listOf("Capture date and time (ISO)"),
            listOf(row.epoch?.let{Instant.ofEpochMilli(it).toString()}.orEmpty())){values->
            controller.confirmCaptureTime(id,epoch(values[0]))
           }
          }
         }
        }
        if(incidentSuggestions.isNotEmpty())item{Text("Suggested conversations",style=MaterialTheme.typography.titleMedium)}
        items(incidentSuggestions,key={"overlap-${it.evidenceIds.joinToString()}"}){suggestion->
         OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
          Text(suggestion.title,style=MaterialTheme.typography.titleMedium);Text(suggestion.reason)
          FlowRow {
           TextButton(enabled=!busy,onClick={
            form=EvidenceForm("Approve suggested incident",listOf("Title"),listOf(suggestion.title)){v->
             val id=UUID.randomUUID().toString()
             change("Suggested incident approved"){current->
              require(v[0].isNotBlank()&&suggestion.evidenceIds.all{source->current.evidence().any{it.id()==source}})
              require(current.incidents().none{existing->existing.evidenceIds().any{it in suggestion.evidenceIds}})
              current.upsertIncident(EvidenceCatalog.Incident(id,v[0],0,0,suggestion.reason,"",emptyList(),
               suggestion.evidenceIds,emptyList(),false,false,emptyList()),EvidenceController.now(),"OWNER_APPROVE_OVERLAP_INCIDENT")
             }
            }
           }){Text("Review and approve")}
           TextButton(enabled=!busy,onClick={controller.skipReview(EvidenceChronology68.Review(
            EvidenceChronology68.ReviewReason.SUGGESTED_INCIDENT,suggestion.evidenceIds))}){Text("Reject suggestion")}
          }
         }}
        }
       }
       val groups=catalog.incidents().filter{g->range!=null&&(archived||!g.archived())&&(query.isBlank()||(listOf(g.title(),g.description(),g.notes(),g.tags().joinToString(" "))+g.events().flatMap{listOf(it.title(),it.notes())}).any{v->v.contains(query,true)})&&(g.startAt()==0L||((range.first==0L||(if(g.endAt()>0)g.endAt()else g.startAt())>=range.first)&&(range.second==0L||g.startAt()<=range.second)))}
       items(groups.sortedBy{if(it.startAt()>0)it.startAt()else Long.MAX_VALUE},key={it.id()}){group->ElevatedCard(Modifier.fillMaxWidth().clickable{incidentId=group.id();selected=emptySet()}){Column(Modifier.padding(12.dp)){Text(group.title(),style=MaterialTheme.typography.titleMedium);Text("${group.evidenceIds().size} evidence · ${time(group.startAt())}${if(group.archived())" · archived"else""}");Text(group.description());if(page=="Timeline")catalog.timeline(group.id()).filter{event->event.timestamp()==0L||range!=null&&(range.first==0L||event.timestamp()>=range.first)&&(range.second==0L||event.timestamp()<=range.second)}.forEach{Text("${time(it.timestamp())} · ${it.provenance()} · ${it.title()}")}}}}
      }
     }
    }
   }
  }
 }
 if(choice=="Import")EvidenceImportReview(controller){choice=null;originalsSelected=emptySet()}
 if(choice=="Connect collections")EvidenceConnectCollections(controller){choice=null}
 if(choice=="Review queue")EvidenceReviewQueue68(controller,onOpen={evidenceId=it;choice=null},onClose={choice=null})
 if(choice=="Sanctuary learning")EvidenceSanctuaryLearning68(controller,catalog?.evidence().orEmpty().filter{it.id() in selected}){choice=null}
 if(more)GalleryAlertDialog(onDismissRequest={more=false},title={EvidenceDialogTitle("Evidence tools")},text={
  Column {
   TextButton(enabled=selected.isNotEmpty()&&selected.size<=128,onClick={more=false;choice="Sanctuary learning"}){Text("Prepare Sanctuary learning")}
   TextButton(onClick={more=false;page="Information"}){Text("About Evidence Locker")}
   TextButton(onClick={more=false;onOpenArchive()}){Text("Open encrypted export")}
  }
 },confirmButton={TextButton(onClick={more=false}){Text("Done")}},
 properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn))
 exportSelection?.let{initial->if(catalog!=null)EvidenceExportSelectionDialog(catalog,initial,onClose={exportSelection=null},onContinue={selection->exportSelection=null;onExport(selection)})}
 choice?.takeIf{it !in setOf("Import","Connect collections","Review queue","Sanctuary learning")}?.let{kind->
  val options=catalog?.incidents()?.filter{!it.archived()&&(kind !in listOf("Merge","Move")||it.id()!=incidentId)}?.map{it.id() to it.title()}.orEmpty()
  GalleryAlertDialog(onDismissRequest={choice=null},title={EvidenceDialogTitle(when(kind){"Merge"->"Merge this incident into…";else->"Choose incident"})},text={LazyColumn(Modifier.heightIn(max=400.dp)){items(options,key={it.first}){(id,name)->TextButton(onClick={
   choice=null;when(kind){"Merge"->{val source=incidentId;confirmation="Merge this incident into $name? Source incident will be archived and source lineage retained." to {if(source!=null)change("Incidents merged"){it.merge(id,listOf(source),EvidenceController.now())};incidentId=id}};"Edit suggestion target"->{val suggestion=suggestionEdit;suggestionEdit=null;if(suggestion!=null)confirmation="Apply the edited suggestion to $name? Only evidence references will be linked." to {change("Owner-edited suggestion applied"){it.rejectSuggestion(suggestion.id(),EvidenceController.now()).assign(suggestion.evidenceIds(),id,false,EvidenceController.now())}}};else->{val ids=selected.toList();change("Evidence assigned"){it.assign(ids,id,kind=="Move",EvidenceController.now())};selected=emptySet()}}
  }){Text(name)}}}},confirmButton={TextButton(onClick={choice=null}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.Inherit))
 }
 form?.let{value->EvidenceFormDialog(value){form=null}}
 confirmation?.let{(text,action)->GalleryAlertDialog(onDismissRequest={confirmation=null},title={EvidenceDialogTitle("Confirm change")},text={Text(text)},confirmButton={TextButton(onClick={confirmation=null;if(controller.current())action()}){Text("Confirm")}},dismissButton={TextButton(onClick={confirmation=null}){Text("Cancel")}},properties=DialogProperties(securePolicy=SecureFlagPolicy.Inherit))}
 viewOriginal?.let{id->EvidenceOriginalView(controller,id){viewOriginal=null}}
 viewReport?.let{analysis->EvidenceReportScreen(controller,analysis,onReportExport){viewReport=null}}
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
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun EvidenceFormDialog(form:EvidenceForm,onClose:()->Unit) {
 val values=remember(form){form.initial.map{mutableStateOf(it)}};var error by remember(form){mutableStateOf(false)}
 EvidenceWorkflowPage(onDismissRequest=onClose,title={EvidenceDialogTitle(form.title)},text={
  Column {
   LazyColumn(Modifier.heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    items(form.labels.indices.toList()){index ->
     val label=form.labels[index]
     if(label.contains("ISO")) {
      EvidenceDateTimeField(label.substringBefore(" ("),runCatching{epoch(values[index].value)}.getOrDefault(0L)){millis ->
       values[index].value=if(millis==0L)"" else Instant.ofEpochMilli(millis).toString();error=false
      }
     } else if(label.startsWith("Provenance:")) {
      FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
       FilterChip(selected=values[index].value=="USER",onClick={values[index].value="USER"},label={Text("Owner confirmed")})
       FilterChip(selected=values[index].value=="INFERRED",onClick={values[index].value="INFERRED"},label={Text("Inferred")})
      }
     } else OutlinedTextField(values[index].value,{values[index].value=it.take(if(label.contains("text",true))100000 else 65536);error=false},
      label={Text(label)},modifier=Modifier.fillMaxWidth())
    }
   }
   if(error)Text("Check the values and try again.",color=MaterialTheme.colorScheme.error)
  }
 },confirmButton={TextButton(onClick={runCatching{form.save(values.map{it.value})}.onSuccess{onClose()}.onFailure{error=true}}){Text("Save")}},
 dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
private fun tags(value:String)=value.split(',').map{it.trim()}.filter{it.isNotEmpty()}.distinct()
private fun epoch(value:String)=if(value.isBlank())0L else Instant.parse(value.trim()).toEpochMilli().also{require(it>=0)}
private fun time(value:Long)=evidenceLocalTime(value)
private fun withEvents(i:EvidenceCatalog.Incident,events:List<EvidenceCatalog.Event>)=EvidenceCatalog.Incident(i.id(),i.title(),i.startAt(),i.endAt(),i.description(),i.notes(),i.tags(),i.evidenceIds(),events,i.manualOrder(),i.archived(),i.lineage())

/** Selection contains only explicitly chosen IDs; incident attachments are resolved by the export plan. */
internal data class EvidenceExportSelection(val incidentIds:List<String>,val evidenceIds:List<String>)

@Composable private fun EvidenceExportSelectionDialog(catalog:EvidenceCatalog,initial:EvidenceExportSelection,onClose:()->Unit,onContinue:(EvidenceExportSelection)->Unit) {
 var incidents by remember(initial){mutableStateOf(initial.incidentIds.toSet())}
 var evidence by remember(initial){mutableStateOf(initial.evidenceIds.toSet())}
 val attached=(evidence+catalog.incidents().filter{it.id() in incidents}.flatMap{it.evidenceIds()+it.events().flatMap{e->e.evidenceIds()}}).toSet()
 val valid=(incidents.isNotEmpty()||evidence.isNotEmpty())&&incidents.size<=128&&attached.size<=128
 EvidenceWorkflowPage(onDismissRequest=onClose,title={EvidenceDialogTitle("Select export contents")},text={
  LazyColumn(Modifier.heightIn(max=400.dp)){
   item{Text("Choose incidents and standalone evidence explicitly. Up to 128 incidents and 128 attached originals; no selection is shortened automatically.")}
   items(catalog.incidents(),key={"incident-${it.id()}"}){incident->
    EvidenceSelectionRow("Export incident ${incident.title()}",incident.title(),null,incident.id() in incidents){checked->incidents=if(checked)incidents+incident.id() else incidents-incident.id()}
   }
   items(catalog.evidence(),key={"evidence-${it.id()}"}){record->
    EvidenceSelectionRow("Export evidence ${record.name()}",record.name(),null,record.id() in evidence){checked->evidence=if(checked)evidence+record.id() else evidence-record.id()}
   }
   item{Text("${incidents.size} incidents · ${evidence.size} explicit records · ${attached.size} attached originals")
    if(!valid)Text("Choose contents within the limits to continue.")}
  }
 },confirmButton={TextButton(enabled=valid,onClick={onContinue(EvidenceExportSelection(incidents.toList(),evidence.toList()))}){Text("Review export")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

/** One touch/selection owner per row; the checkbox is the visual indicator. */
@Composable internal fun EvidenceSelectionRow(label:String,title:String,detail:String?,selected:Boolean,enabled:Boolean=true,onChange:(Boolean)->Unit) {
 Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
  .toggleable(value=selected,enabled=enabled,role=Role.Checkbox,onValueChange=onChange)
  .semantics{contentDescription=label}.padding(vertical=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
  Checkbox(selected,onCheckedChange=null,enabled=enabled,modifier=Modifier.size(48.dp))
  Column(Modifier.weight(1f)){Text(title);detail?.let{Text(it,style=MaterialTheme.typography.bodySmall)}}
 }
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
