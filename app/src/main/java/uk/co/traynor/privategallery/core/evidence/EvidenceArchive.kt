package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Owner-authenticated portable export. Callers own output publication and partial destination cleanup. */
internal object EvidenceArchive {
 internal data class Selected(val plan:EvidenceExportPlan,val catalog:EvidenceCatalog,val sources:List<EvidenceCatalog.SourceFingerprint>) {
  val originals get()=catalog.evidence().distinctBy{it.sourceId()}
 }
 internal fun offMain(){check(Looper.myLooper()!=Looper.getMainLooper()){"Evidence archive work must run off Main"}}
 internal fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt() and 255)}
 internal fun sha(bytes:ByteArray)=hex(MessageDigest.getInstance("SHA-256").digest(bytes))
 internal fun select(plan:EvidenceExportPlan,reader:EvidenceSourceReader):Selected {
  require(plan.digest==EvidenceEgress.digest(plan.catalog)){"Evidence selection changed"}
  val initial=EvidenceEgress.requireAllowed(plan.catalog,plan.evidenceIds,plan.incidentIds,reader)
  require(plan.evidenceIds.isNotEmpty()||plan.incidentIds.isNotEmpty()){"Select evidence or incidents"}
  val incidents=plan.incidentIds.map{id->plan.catalog.incidents().single{it.id()==id}}
  val ids=(plan.evidenceIds+incidents.flatMap{it.evidenceIds()+it.events().flatMap{e->e.evidenceIds()}}).distinct()
  val records=ids.map{id->plan.catalog.evidence().single{it.id()==id}}
  val contributing=(initial+records.map(EvidenceEgress::fingerprint)).distinctBy{it.sourceId()}
  val contributingSet=contributing.toSet()
  val analyses=plan.catalog.analyses().filter{a->
   a.lineage().isNotEmpty()&&a.lineage().all{it in contributingSet}&&
    (incidents.any{a.id() in it.analysisIds()}||(a.sourceReferences().isNotEmpty()&&a.sourceReferences().all{it.evidenceId() in ids}))
  }
  val sources=(records.map(EvidenceEgress::fingerprint)+incidents.flatMap{it.lineage()}+analyses.flatMap{it.lineage()}).distinctBy{it.sourceId()}
  require(records.distinctBy{it.sourceId()}.size<=128){"Original count quota exceeded"}
  var sum=0L;for(e in records.distinctBy{it.sourceId()}){require(e.size()<=EvidenceArchiveFormat.MAX_EXPANDED-sum);sum+=e.size()}
  val projected=analyses.map{a->EvidenceAi.details(a);EvidenceCatalog.Analysis(a.id(),a.at(),a.model(),a.task(),a.inputDigest(),a.evidenceIds().filter{it in ids},a.text(),a.inputTokens(),a.outputTokens(),a.applied(),a.lineage(),a.sourceReferences())}
  val subset=EvidenceCatalog(plan.catalog.owner(),plan.catalog.revision(),records,incidents,emptyList(),projected,emptyList())
  return Selected(plan,subset,sources).also{allowed(it,reader)}
 }
 internal fun allowed(selected:Selected,reader:EvidenceSourceReader){
  EvidenceEgress.requireAllowed(selected.plan.catalog,selected.plan.evidenceIds,selected.plan.incidentIds,reader)
  // Explicitly check event-only and retained analysis sources as well as direct originals.
  val id="00000000-0000-4000-8000-000000017905"
  val incident=EvidenceCatalog.Incident(id,"",0,0,"","",emptyList(),emptyList(),emptyList(),false,false,selected.sources)
  val check=EvidenceCatalog(selected.catalog.owner(),0,emptyList(),listOf(incident),emptyList(),emptyList(),emptyList())
  EvidenceEgress.requireAllowed(check,emptyList(),listOf(id),reader)
 }
 internal fun path(source:String)="originals/$source.bin"
 private fun obj(vararg pairs:Pair<String,Any?>)=JSONObject().also{j->pairs.forEach{j.put(it.first,it.second?:JSONObject.NULL)}}
 private fun array(values:Iterable<*>)=JSONArray().also{a->values.forEach{a.put(it)}}
 private fun fingerprint(f:EvidenceCatalog.SourceFingerprint)=obj("sourceId" to f.sourceId(),"mime" to f.mime(),"sha256" to f.sha256(),"size" to f.size(),"vaultOnly" to f.vaultOnly())
 private fun metadata(m:EvidenceCatalog.Metadata)=obj("sourceName" to m.sourceName(),"fileCreatedAt" to m.fileCreatedAt(),"fileModifiedAt" to m.fileModifiedAt(),"dateAddedAt" to m.dateAddedAt(),"captureAt" to m.captureAt(),"width" to m.width(),"height" to m.height(),"embedded" to JSONObject(m.embedded().toSortedMap()),"vaultOnly" to m.vaultOnly())
 private fun record(e:EvidenceCatalog.Evidence)=obj("id" to e.id(),"sourceId" to e.sourceId(),"name" to e.name(),"mime" to e.mime(),"sha256" to e.sha256(),"size" to e.size(),"importedAt" to e.importedAt(),"metadata" to metadata(e.metadata()),"description" to e.description(),"notes" to e.notes(),"tags" to array(e.tags()),"participants" to array(e.participants()),"ocr" to array(e.ocr().map{o->obj("id" to o.id(),"createdAt" to o.createdAt(),"engine" to o.engine(),"rotation" to o.rotation(),"width" to o.width(),"height" to o.height(),"text" to o.text(),"confidence" to o.confidence(),"pageCount" to o.pageCount(),"partial" to o.partial(),"words" to array(o.words().map{w->obj("page" to w.pageIndex(),"text" to w.text(),"confidence" to w.confidence(),"left" to w.left(),"top" to w.top(),"right" to w.right(),"bottom" to w.bottom())}),"times" to array(o.times().map{t->obj("page" to t.pageIndex(),"text" to t.text(),"epochMillis" to t.epochMillis(),"provenance" to t.provenance(),"confidence" to t.confidence())}))}),"corrections" to array(e.corrections().map{c->obj("id" to c.id(),"ocrId" to c.ocrId(),"createdAt" to c.createdAt(),"text" to c.text())}))
 internal fun metadataBytes(catalog:EvidenceCatalog,sources:List<EvidenceCatalog.SourceFingerprint>):ByteArray {
  val encoded=catalog.encode()
  try {
   val document=obj("version" to 2,"owner" to catalog.owner().name,"catalogBinary" to Base64.encodeToString(encoded,Base64.NO_WRAP),
    "records" to array(catalog.evidence().map(::record)),
    "incidents" to array(catalog.incidents().map{i->obj("id" to i.id(),"title" to i.title(),"startAt" to i.startAt(),"endAt" to i.endAt(),"description" to i.description(),"notes" to i.notes(),"tags" to array(i.tags()),"evidenceIds" to array(i.evidenceIds()),"analysisIds" to array(i.analysisIds()),"manualOrder" to i.manualOrder(),"archived" to i.archived(),"lineage" to array(i.lineage().map(::fingerprint)),"events" to array(i.events().map{e->obj("id" to e.id(),"evidenceIds" to array(e.evidenceIds()),"timestamp" to e.timestamp(),"provenance" to e.provenance(),"title" to e.title(),"notes" to e.notes())}))}),
    "analyses" to array(catalog.analyses().map{a->obj("label" to "ADVISORY AI; owner review required", "id" to a.id(),"at" to a.at(),"model" to a.model(),"task" to a.task(),"inputDigest" to a.inputDigest(),"evidenceIds" to array(a.evidenceIds()),"sourceReferences" to array(a.sourceReferences().map{r->obj("evidenceId" to r.evidenceId(),"fingerprint" to fingerprint(r.fingerprint()))}),"rawAcceptedJson" to a.text(),"inputTokens" to a.inputTokens(),"outputTokens" to a.outputTokens(),"applied" to a.applied(),"lineage" to array(a.lineage().map(::fingerprint)))}),
    "sources" to array(sources.map{f->obj("fingerprint" to fingerprint(f),"attached" to catalog.evidence().any{it.sourceId()==f.sourceId()},"path" to if(catalog.evidence().any{it.sourceId()==f.sourceId()})path(f.sourceId())else JSONObject.NULL)}))
   val value=document.toString();EvidenceStrictJson.validate(value,16,EvidenceArchiveFormat.META)
   return boundedMetadata(value)
  }finally{encoded.fill(0)}
 }
 private fun boundedMetadata(value:String):ByteArray {val bytes=value.toByteArray(Charsets.UTF_8);if(bytes.size>EvidenceArchiveFormat.META){bytes.fill(0);throw IllegalArgumentException("Metadata quota exceeded")};return bytes}
 internal fun decodeMetadata(bytes:ByteArray,owner:EvidenceCatalog.Owner):Pair<EvidenceCatalog,List<EvidenceCatalog.SourceFingerprint>> {
  val text=utf8(bytes);EvidenceStrictJson.validate(text,16,EvidenceArchiveFormat.META)
  val j=JSONObject(text);require(j.get("version") is Int&&j.getInt("version")==2){"Unsupported evidence metadata schema"};require(j.get("owner")==owner.name)
  val binary=j.get("catalogBinary");require(binary is String&&binary.length<=EvidenceArchiveFormat.META)
  val encoded=Base64.decode(binary,Base64.NO_WRAP)
  val catalog=try{EvidenceCatalog.decode(encoded,owner)}finally{encoded.fill(0)}
  require(catalog.encodingVersion()==2){"Unsupported evidence catalog schema in export"}
  require(catalog.audit().isEmpty()&&catalog.rejectedSuggestions().isEmpty())
  val sources=(catalog.evidence().map(EvidenceEgress::fingerprint)+catalog.incidents().flatMap{it.lineage()}+catalog.analyses().flatMap{it.lineage()}).distinctBy{it.sourceId()}
  require(sources.none{it.vaultOnly()}&&catalog.evidence().none{it.metadata().vaultOnly()})
  require(catalog.evidence().distinctBy{it.sourceId()}.size<=128)
  var expanded=0L;for(e in catalog.evidence().distinctBy{it.sourceId()}){require(e.size()<=EvidenceArchiveFormat.MAX_EXPANDED-expanded);expanded+=e.size()}
  // Regeneration is an exact schema/projection/reference check, including unknown keys and false claims.
  val canonical=metadataBytes(catalog,sources)
  try{require(MessageDigest.isEqual(bytes,canonical)){"Noncanonical evidence schema"}}finally{canonical.fill(0)}
  return catalog to sources
 }
 internal fun utf8(bytes:ByteArray):String=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
 internal fun hashes(catalog:EvidenceCatalog,sources:List<EvidenceCatalog.SourceFingerprint>,documents:Map<String,Pair<Long,String>>):ByteArray = obj("version" to 1,"algorithm" to "SHA-256","documents" to array(documents.map{(name,value)->obj("path" to name,"bytes" to value.first,"sha256" to value.second)}),"sources" to array(sources.map{f->obj("fingerprint" to fingerprint(f),"path" to if(catalog.evidence().any{it.sourceId()==f.sourceId()})path(f.sourceId())else JSONObject.NULL,"attached" to catalog.evidence().any{it.sourceId()==f.sourceId()})})).toString().let(::boundedMetadata)
 fun write(plan:EvidenceExportPlan,reader:EvidenceSourceReader,passphrase:CharArray,output:OutputStream):EvidenceExportReceipt {
  offMain();val selected=select(plan,reader);if(!plan.plaintextConfirmed)require(passphrase.size>=12){"Use at least 12 passphrase characters"}
  val metadata=metadataBytes(selected.catalog,selected.sources);var pdf:ByteArray?=null;var transcript:ByteArray?=null;var manifest:ByteArray?=null;var key:ByteArray?=null;var encrypt:EvidenceArchiveCrypto.Encrypt?=null
  try {
   val pdfBytes=EvidencePdf.renderSelected(selected,reader);pdf=pdfBytes
   val transcriptBytes=EvidencePdf.text(selected).toByteArray(Charsets.UTF_8);transcript=transcriptBytes;require(transcriptBytes.size<=EvidenceArchiveFormat.TRANSCRIPT)
   val docs=linkedMapOf("evidence.json" to (metadata.size.toLong() to sha(metadata)),"report.pdf" to (pdfBytes.size.toLong() to sha(pdfBytes)),"transcript.txt" to (transcriptBytes.size.toLong() to sha(transcriptBytes)))
   val manifestBytes=hashes(selected.catalog,selected.sources,docs);manifest=manifestBytes
   require(metadata.size<=EvidenceArchiveFormat.META-manifestBytes.size){"Combined metadata quota exceeded"}
   val expanded=selected.originals.sumOf{it.size()}+metadata.size+pdfBytes.size+transcriptBytes.size+manifestBytes.size;require(expanded<=EvidenceArchiveFormat.MAX_EXPANDED)
   val header=EvidenceArchiveFormat.header(ByteArray(16).also{SecureRandom().nextBytes(it)},ByteArray(12).also{SecureRandom().nextBytes(it)})
   if(!plan.plaintextConfirmed)key=EvidenceArchiveCrypto.key(passphrase,header)
   allowed(selected,reader) // No output before complete admission and bounded report/schema construction.
   var writing=true
   val receipt=ReceiptOutput(output,{writing&&reader.current()})
   val sink=if(plan.plaintextConfirmed)receipt else {receipt.write(header);EvidenceArchiveCrypto.Encrypt(receipt,key!!,header){reader.current()}.also{encrypt=it}}
   val zip=ZipOutputStream(sink)
   try {
    fun entry(name:String,bytes:ByteArray){check(reader.current());zip.putNextEntry(ZipEntry(name));var at=0;while(at<bytes.size){check(reader.current());val n=minOf(32768,bytes.size-at);zip.write(bytes,at,n);at+=n};zip.closeEntry()}
    entry("evidence.json",metadata);entry("report.pdf",pdfBytes);entry("transcript.txt",transcriptBytes);entry("hashes.json",manifestBytes)
    val buffer=ByteArray(32768)
    try {for(e in selected.originals){allowed(selected,reader);zip.putNextEntry(ZipEntry(path(e.sourceId())));val digest=MessageDigest.getInstance("SHA-256");var count=0L
     reader.readVerified(e.sourceId()){original,input->
      EvidenceAccess.requireSameOriginal(reader.inspect(e.sourceId()).item,original.item)
      while(true){check(reader.current());val n=input.read(buffer);if(n<0)break;if(n==0)continue;require(count<=e.size()-n){"Original size changed"};count+=n;digest.update(buffer,0,n);zip.write(buffer,0,n)}
      require(count==e.size()&&hex(digest.digest())==e.sha256()){"Original integrity mismatch"}
     }
     allowed(selected,reader);zip.closeEntry()
    }}finally{buffer.fill(0)}
    allowed(selected,reader);zip.finish();encrypt?.finish();allowed(selected,reader);receipt.flush();return receipt.receipt(!plan.plaintextConfirmed)
   }finally{writing=false;zip.deflateEnd()}
  }finally{encrypt?.dispose();key?.fill(0);metadata.fill(0);pdf?.fill(0);transcript?.fill(0);manifest?.fill(0)}
 }
 // close() releases Deflater state; the wrapped sink must not close the caller's destination.
 private fun ZipOutputStream.deflateEnd(){runCatching{close()}}
 internal class ReceiptOutput(private val target:OutputStream,private val current:()->Boolean):OutputStream(){
  private val digest=MessageDigest.getInstance("SHA-256");private var count=0L
  override fun write(b:Int){write(byteArrayOf(b.toByte()))}
  override fun write(b:ByteArray,off:Int,len:Int){check(current());require(count<=EvidenceArchiveFormat.MAX_CIPHER-len);target.write(b,off,len);digest.update(b,off,len);count+=len;check(current())}
  override fun flush(){target.flush()}
  override fun close(){} // caller owns provider/output
  fun receipt(encrypted:Boolean)=EvidenceExportReceipt(hex(digest.digest()),count,encrypted)
 }
 fun open(context:Context,expectedOwner:EvidenceCatalog.Owner,input:InputStream,passphrase:CharArray,current:()->Boolean):EvidenceOpenedArchive = EvidenceOpenedArchive.open(context,expectedOwner,input,passphrase,current)
}
