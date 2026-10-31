package uk.co.traynor.privategallery.core.evidence

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/** Ordinary PDF inside the encrypted container. Source timestamps and AI remain labelled claims. */
internal object EvidencePdf {
 fun render(plan:EvidenceExportPlan,reader:EvidenceSourceReader):ByteArray {
  EvidenceArchive.offMain();return renderSelected(EvidenceArchive.select(plan,reader),reader)
 }
 internal fun text(selected:EvidenceArchive.Selected):String {
  val out=StringBuilder()
  fun line(value:String=""){require(out.length+value.length+1<=EvidenceArchiveFormat.TRANSCRIPT);out.append(value).append('\n')}
  line("Evidence report — not legal certification")
  line("Owner: ${selected.catalog.owner().name}; selected catalogue revision: ${selected.catalog.revision()}")
  line("Original bytes and annotations are separate. SHA-256 verifies byte integrity, not content authenticity.")
  line("Dates are epoch milliseconds UTC; 0 means unknown. File, embedded and OCR dates are source claims.")
  line("AI is advisory and requires owner review. Only analyses entirely associated with selected contributors are included.")
  for(i in selected.catalog.incidents()){
   line();line("Incident ${i.id()}: ${i.title()}");line("Owner range: ${i.startAt()} to ${i.endAt()}; archived=${i.archived()}")
   line("Description: ${i.description()}");line("Notes: ${i.notes()}");line("Tags: ${i.tags().joinToString()}")
   line("Linked evidence IDs: ${i.evidenceIds().joinToString()}");line("Timeline order: ${if(i.manualOrder())"owner manual"else"known dates chronological; unknown dates last"}")
   for(e in selected.catalog.timeline(i.id())){line("Event ${e.id()}: ${e.title()}");line("Timestamp ${e.timestamp()} — ${e.provenance()}${if(e.provenance()=="AI_INFERRED")"; ADVISORY AI; unverified"else""}");line("Evidence IDs: ${e.evidenceIds().joinToString()}");line(e.notes())}
   line("Associated advisory IDs: ${i.analysisIds().joinToString()}")
   line("All retained contributing source IDs: ${i.lineage().joinToString{it.sourceId()}}")
  }
  for(e in selected.catalog.evidence()){
   line();line("Evidence ${e.id()}: ${e.name()}");line("Source ID: ${e.sourceId()}");line("Source filename (claim): ${e.metadata().sourceName()}")
   line("MIME ${e.mime()}; original size ${e.size()}; SHA-256 ${e.sha256()}")
   val m=e.metadata();line("Imported: ${e.importedAt()}; file created: ${m.fileCreatedAt()}; file modified: ${m.fileModifiedAt()}; added: ${m.dateAddedAt()}; capture claim: ${m.captureAt()}; dimensions ${m.width()}×${m.height()}")
   for((key,value) in m.embedded().toSortedMap())line("Embedded/provider claim [$key]: $value")
   line("Description: ${e.description()}");line("Notes: ${e.notes()}");line("Tags: ${e.tags().joinToString()}");line("Owner participants: ${e.participants().joinToString()}")
   for(o in e.ocr()){
    line("Raw OCR/extraction ${o.id()} (${o.engine()}, ${o.createdAt()}, confidence ${o.confidence()}, pages ${o.pageCount()}, partial=${o.partial()}, rotation=${o.rotation()}):")
    line(o.text());for(t in o.times())line("Visible timestamp claim page ${t.pageIndex()+1}: ${t.text()} => ${t.epochMillis()} (${t.provenance()}; confidence ${t.confidence()})")
   }
   for(c in e.corrections()){line("Owner correction ${c.id()} to raw extraction ${c.ocrId()} at ${c.createdAt()}:");line(c.text())}
   line("Current transcript (owner corrections preferred):");line(e.transcript())
  }
  for(a in selected.catalog.analyses()){
   val d=EvidenceAi.details(a)
   line();line("ADVISORY AI — ${a.id()}; model ${a.model()}; task ${a.task()}; generated ${a.at()}")
   line("Status ${d.status}; refusal=${d.refusal}; owner applied=${a.applied()}; input/output tokens ${a.inputTokens()}/${a.outputTokens()}; cached input tokens ${d.cachedInputTokens}")
   line("Input digest ${a.inputDigest()}; attached evidence IDs ${a.evidenceIds().joinToString()}")
   for(r in a.sourceReferences())line("Validated reference ${r.evidenceId()} => source ${r.fingerprint().sourceId()}; ${if(selected.catalog.evidence().any{it.id()==r.evidenceId()})"attached record"else"not attached — historical analysis reference"}")
   line(d.summary)
   for(s in d.suggestions)line("AI suggestion (owner review): ${s.kind}: ${s.title}; ${s.reasons.joinToString()}; evidence IDs ${s.evidenceIds.joinToString()}")
   for(e in d.events)line("AI event (unverified): ${e.title}; quoted date ${e.dateTimeQuote?:"unknown"}; ${e.provenance}; ${e.uncertainty}; evidence IDs ${e.evidenceIds.joinToString()}")
   line("Contributing source IDs: ${a.lineage().joinToString{it.sourceId()}}")
  }
  line();line("Source/hash index — all contributing lineage")
  for(s in selected.sources){val attached=selected.originals.any{it.sourceId()==s.sourceId()};line("${s.sourceId()} | ${s.mime()} | ${s.size()} bytes | SHA-256 ${s.sha256()} | ${if(attached)EvidenceArchive.path(s.sourceId())else"not attached — retained contributing fingerprint only"}")}
  return out.toString()
 }
 internal fun renderSelected(selected:EvidenceArchive.Selected,reader:EvidenceSourceReader):ByteArray {
  EvidenceArchive.offMain();EvidenceArchive.allowed(selected,reader)
  val report=text(selected);val document=PdfDocument();val output=EvidenceWipingBuffer(EvidenceArchiveFormat.REPORT){reader.current()}
  var page:PdfDocument.Page?=null;var number=0;var y=0f
  val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=10f;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
  fun nextPage(){page?.let(document::finishPage);check(reader.current());require(number<4096);number++;page=document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create());y=44f;page!!.canvas.drawText("Evidence report • page $number",36f,y,paint);y+=24f}
  try {
   nextPage()
   for(paragraph in report.split('\n')){
    check(reader.current());if(paragraph.isEmpty()){y+=14f;continue};var at=0
    while(at<paragraph.length){check(reader.current());if(y>804f)nextPage()
     var count=paint.breakText(paragraph,at,paragraph.length,true,523f,null).coerceAtLeast(1)
     if(at+count<paragraph.length&&Character.isHighSurrogate(paragraph[at+count-1]))count--
     if(count<1)count=minOf(2,paragraph.length-at)
     if(at+count<paragraph.length){val space=paragraph.lastIndexOf(' ',at+count-1);if(space>at&&space-at>count/2)count=space-at+1}
     page!!.canvas.drawText(paragraph,at,at+count,36f,y,paint);at+=count;y+=14f
    }
   }
   page?.let(document::finishPage);page=null;EvidenceArchive.allowed(selected,reader);document.writeTo(output);EvidenceArchive.allowed(selected,reader)
   return output.toByteArray()
  }finally{page?.let{runCatching{document.finishPage(it)}};document.close();output.close()}
 }
}
