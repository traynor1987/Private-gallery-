package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.evidence.*

/** All classifications are qualified app-owned labels; model text never becomes navigation. */
@Composable internal fun EvidenceBehaviourReportContent(
 report:EvidenceAiReport.Report,canOpen:(String)->Boolean,onOpenSource:(String)->Unit
) {
 val currentCanOpen by rememberUpdatedState(canOpen)
 val currentOpen by rememberUpdatedState(onOpenSource)
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium) {
   Text(EvidenceAiReport.DISCLAIMER,Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)
  }
  Text(if(report.outcome=="INSUFFICIENT_EVIDENCE")"Insufficient Evidence*"else"Potential pattern in selected evidence*",style=MaterialTheme.typography.titleLarge)
  Text("AI assessment*: ${report.assessment}*")
  Text("Confidence*: ${report.confidence}*")
  report.alternatives.forEach{Text("Alternative interpretation*: $it*")}
  Text("Potential event counts*",style=MaterialTheme.typography.titleMedium)
  EvidenceAiReport.Category.entries.forEach{category->Text("${category.label}*: ${report.counts[category]?:0}*")}
  Text("Categories overlap: one deduplicated potential event may contribute to several counts*. Counts are interpretations, not established incidents.")
  Text("Repeated or overlapping excerpts merged*: ${report.duplicateCount}*")
  Text("Chronological factual excerpts",style=MaterialTheme.typography.titleMedium)
  if(report.chronology.isEmpty())Text("No anchored chronological entries. Missing dates and messages are not reconstructed.")
  report.chronology.forEach{entry->
   Text("Timestamp claim: ${entry.timestampQuote?:"Unknown"}")
   Text("Direct selected evidence: ${entry.fact}")
   Text("Contextual inference*: ${entry.context}*")
   Text("AI interpretation*: ${entry.interpretation}*")
  }
  report.instances.forEachIndexed{index,event->
   ElevatedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Text("Potential event ${index+1}*",style=MaterialTheme.typography.titleMedium)
    Text("${event.assessment}*")
    Text("Categories*: ${event.categories.joinToString{it.label+"*"}}")
    Text("Confidence*: ${event.confidence}* · Intent assessment*: ${event.intent}*")
    event.alternatives.forEach{Text("Alternative interpretation*: $it*")}
    event.references.forEach{reference->
     Text("Selected ${reference.field} passage: “${reference.passage}”")
     Text("Timestamp claim: ${reference.timestampQuote?:"Unknown"}")
     val id=reference.evidenceId
     TextButton(enabled=canOpen(id),modifier=Modifier.testTag("report-source-$id"),onClick={
      if(currentCanOpen(id))currentOpen(id)
     }){Text("Open supporting evidence ${id.take(8)}")}
     if(!canOpen(id))Text("Original unavailable or its current fingerprint differs. The historical report is preserved.",style=MaterialTheme.typography.bodySmall)
    }
   }}
  }
  report.limitations.forEach{Text("Limitation*: $it*")}
  Text("Only selected evidence was analysed. OCR and owner annotations may be incomplete; ordinary disagreement alone does not establish abuse.")
 }
}

/** A retained model citation cannot select a different or changed original. */
internal fun evidenceReportSourceAllowed(catalog:EvidenceCatalog?,analysis:EvidenceCatalog.Analysis,id:String):Boolean {
 val record=catalog?.evidence()?.singleOrNull{it.id()==id}?:return false
 val reference=analysis.sourceReferences().singleOrNull{it.evidenceId()==id}?:return false
 return EvidenceEgress.fingerprint(record)==reference.fingerprint()
}

@Composable internal fun EvidenceReportScreen(controller:EvidenceController,analysis:EvidenceCatalog.Analysis,
 onExport:(String)->Unit,onClose:()->Unit) {
 val state by controller.state.collectAsState()
 var original by remember(controller,analysis.id()){mutableStateOf<String?>(null)}
 var retired by remember(controller){mutableStateOf(false)}
 val details=remember(analysis){runCatching{EvidenceAi.details(analysis)}.getOrNull()}
 DisposableEffect(controller,analysis.id()) {
  val subscription=controller.access.onRetire{android.os.Handler(android.os.Looper.getMainLooper()).post{original=null;retired=true;onClose()}}
  onDispose{subscription.close();original=null}
 }
 if(retired||!controller.current())return
 fun allowed(id:String)=controller.current()&&evidenceReportSourceAllowed(controller.state.value.catalog,analysis,id)
 EvidencePage(onBack=onClose,sensitive=true) {
  Surface(Modifier.fillMaxSize().systemBarsPadding(),color=MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().padding(16.dp)) {
    Row(Modifier.fillMaxWidth()) {
     Text("AI evidence report*",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
     EvidenceSecurityControls();TextButton(onClick=onClose){Text("Close report")}
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     item{Text("${analysis.model()} · generated ${java.time.Instant.ofEpochMilli(analysis.at())}",style=MaterialTheme.typography.bodySmall)}
     item{details?.report?.let{report->EvidenceBehaviourReportContent(report,::allowed){id->if(allowed(id))original=id}}
      ?:Text("This saved report cannot be read safely. Its encrypted record is preserved.")}
     item{Button(enabled=details?.report!=null&&!state.busy,onClick={if(controller.current())onExport(analysis.id())}){Text("Export report only")}}
    }
   }
  }
 }
 original?.let{id->if(allowed(id))EvidenceOriginalView(controller,id){original=null}else SideEffect{original=null}}
}
