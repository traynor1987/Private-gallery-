package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A screen-scoped owner capability. No key, original bytes or plaintext index is persisted here. */
internal class EvidenceController(context:Context,val access:EvidenceAccess):AutoCloseable {
 data class State(val catalog:EvidenceCatalog?=null,val originals:List<EvidenceOriginal> = emptyList(),
  val busy:Boolean=false,val message:String?=null,val integrity:Map<String,String> = emptyMap(),val reviewImportIds:List<String> = emptyList(),
  val collections:List<EvidenceCollectionOption> = emptyList(),
  val connections:List<EvidenceCollectionConnection> = emptyList(),
  val collectionProgress:EvidenceCollectionProgress? = null,
  val syncingCollections:Boolean=false,
  val chronology:EvidenceChronology68.Result?=null)
 private val importAcquisitions=java.util.concurrent.ConcurrentHashMap<String,EvidenceOriginal>()
 private val main=Handler(Looper.getMainLooper())
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val mutex=Mutex()
 private val ocr=EvidenceOcr(context.applicationContext)
 private val collectionSync=EvidenceCollectionSync(access)
 private val reviewStore=lazy { EvidenceReview68(context.applicationContext,access) }
 private var collectionJob:Job?=null
 private var collectionPaused=false
 private val reviewRevision=MutableStateFlow(0)
 private val mutable=MutableStateFlow(State())
 val state=mutable.asStateFlow()
 private val retirement=access.onRetire {
  scope.cancel()
  clearOnMain()
 }
 private fun clearOnMain() {
  importAcquisitions.clear()
  if(Looper.myLooper()===Looper.getMainLooper())mutable.value=State() else main.post{mutable.value=State()}
 }
 private fun publish(change:(State)->State)=access.publishIfCurrent{mutable.value=change(mutable.value)}
 init {
  refresh()
  scope.launch {
   try {
    collectionSync.changes().collectLatest {
     if(!collectionPaused && mutable.value.connections.isNotEmpty()) syncCollections(retryPending=false)
    }
   }catch(cancelled:CancellationException){throw cancelled}
   catch(_:Exception){publish{it.copy(message="Collection updates could not be watched. You can check for new evidence manually.")}}
  }
  scope.launch {
   kotlinx.coroutines.flow.combine(mutable.map{it.catalog}.distinctUntilChanged(),reviewRevision){catalog,_->catalog}
    .collectLatest {catalog->
     if(catalog==null||!current())return@collectLatest
     try {
      val analysis=withContext(Dispatchers.IO){
       ensureActive()
       val reviewed=reviewStore.value
       val skipped=reviewed.skipped()
       EvidenceChronology68.analyse(catalog.evidence(),confirmed=reviewed.confirmed(),current={access.current()}).let { result ->
        result.copy(review=result.review.filterNot { EvidenceReview68.key(it) in skipped })
       }.also{access.checkCurrent();ensureActive()}
      }
      publish{it.copy(chronology=analysis)}
     }catch(cancelled:CancellationException){throw cancelled}
     catch(_:Exception){publish{it.copy(chronology=null)}}
    }
  }
 }
 fun current()=access.current()
 private class BulkRegistrationFailure(val sourceIds:List<String>,cause:Throwable):Exception(cause)
 private fun perform(label:String,success:((EvidenceCatalog)->String)?=null,work:suspend ()->EvidenceCatalog):Job {
  return scope.launch {
   mutex.withLock {
    if(!current())return@withLock
    if(!publish{it.copy(busy=true,message=null)})return@withLock
    try {
     val next=withContext(Dispatchers.IO){ensureActive();access.checkCurrent();work().also{access.checkCurrent();ensureActive()}}
     publish{it.copy(catalog=next,busy=false,message=success?.invoke(next)?:label)}
    }catch(cancelled:CancellationException){publish{it.copy(busy=false)};throw cancelled
    }catch(failure:Throwable){
     // A prior item may have committed, or promotion may have preceded an fsync failure.
     // Reread instead of hiding committed records or blindly replaying the operation.
     val authoritative=withContext(Dispatchers.IO){runCatching{access.store.read()}.getOrNull()}
     val message=if(failure is BulkRegistrationFailure){
      val committed=authoritative?.evidence()?.count{it.sourceId() in failure.sourceIds}
      if(committed==null)"Bulk registration stopped. Reopen to review which records committed. Originals were preserved."
      else "Bulk registration stopped: $committed of ${failure.sourceIds.size} selected originals are registered. Review the committed records before retrying. Originals were preserved."
     }else if(failure is IllegalArgumentException&&failure.message=="Encoded evidence quota exceeded")
      "The change was not saved: encrypted catalogue quota exceeded. Existing annotations remain readable. Explicitly shorten or remove owner annotations before trying again."
     else "Operation could not complete. No original was removed. Reopen or verify the selected evidence."
     publish{it.copy(catalog=authoritative?:it.catalog,busy=false,message=message)}
    }
   }
  }
 }
 fun refresh() {perform("Evidence Locker ready") {
  val catalog=access.store.read();val sources=access.sources.list();val byId=sources.associateBy{it.item.id}
  // One cheap live catalogue snapshot, never a substitute for authenticated full-byte verification.
  val integrity=catalog.evidence().associate{record ->
   val source=byId[record.sourceId()]
   val status=if(source==null)"Original unavailable in this vault; reverify before relying on this reference" else try {
    checkSource(record,source)
    "Live metadata fingerprint and policy match; full original SHA-256 has not been reverified by this refresh"
   }catch(_:IllegalStateException){"Original unavailable or changed; do not rely on this reference"}
   record.id() to status
  }
  val collections=collectionSync.available()
  val connections=collectionSync.connections()
  withContext(Dispatchers.Main.immediate){publish{it.copy(catalog=catalog,originals=sources,integrity=integrity,
    collections=collections,connections=connections)}}
  catalog
 }.invokeOnCompletion {
  scope.launch { if(!collectionPaused && mutable.value.connections.isNotEmpty())syncCollections() }
 }}
 fun connectCollection(id:String) {
  perform("Collection connected") {
   val connections=collectionSync.connect(id)
   withContext(Dispatchers.Main.immediate){publish{it.copy(connections=connections)}}
   access.store.read()
  }.invokeOnCompletion {scope.launch {syncCollections()}}
 }
 fun disconnectCollection(id:String) {
  perform("Collection disconnected. Existing evidence was kept.") {
   val connections=collectionSync.disconnect(id)
   withContext(Dispatchers.Main.immediate){publish{it.copy(connections=connections)}}
   access.store.read()
  }
 }
 fun syncCollections() = syncCollections(retryPending=true)
 private fun syncCollections(retryPending:Boolean) {
  if(!current()||collectionJob?.isActive==true||mutable.value.connections.isEmpty())return
  collectionPaused=false
  publish{it.copy(syncingCollections=true,collectionProgress=null)}
  val job=perform("Collection scan complete") {
   collectionSync.sync(progress={progress->
    main.post {if(mutable.value.syncingCollections)publish{it.copy(collectionProgress=progress)}}
   },register={original->registerRecord(original.item.id,extract=true,acquired=original)},
   retryExtraction=if(retryPending)({record->extractRecord(record,0)})else null)
  }
  collectionJob=job
  job.invokeOnCompletion {scope.launch {
   if(collectionJob===job)publish{it.copy(syncingCollections=false,
    message=if(collectionPaused)"Collection processing paused. Saved evidence was kept." else it.message)}
  }}
 }
 fun cancelCollectionSync() {collectionPaused=true;collectionJob?.cancel()}
 fun confirmMessageTime(location:EvidenceChronology68.MessageLocation,timestamp:Long) {
  perform("Message date confirmed") {
   val catalog=access.store.read()
   check(EvidenceChronology68.analyse(catalog.evidence(),current={access.current()}).messages.any{location in it.locations})
   reviewStore.value.confirm(location,timestamp)
   withContext(Dispatchers.Main.immediate){reviewRevision.value++}
   catalog
  }
 }
 fun skipReview(review:EvidenceChronology68.Review) {
  perform("Review item skipped") {
   reviewStore.value.skip(review)
   withContext(Dispatchers.Main.immediate){reviewRevision.value++}
   access.store.read()
  }
 }
 /** Transforms are trusted local UI operations, never model output or imported JSON. */
 fun mutate(label:String,transform:(EvidenceCatalog)->EvidenceCatalog)=perform(label){access.store.update(transform)}
 fun register(sourceId:String,extract:Boolean=true)=perform("Evidence added; original preserved") {registerRecord(sourceId,extract)}
 /** One reviewed, bounded owner action; each authenticated original has its own durable commit. */
 fun registerMany(sourceIds:List<String>,extract:Boolean=true) {
  require(sourceIds.isNotEmpty()&&sourceIds.size<=128&&sourceIds.distinct().size==sourceIds.size){"Select 1 to 128 distinct originals"}
  val selection=sourceIds.toList()
  perform("Bulk registration complete",success={catalog->
   "Bulk registration complete: ${catalog.evidence().count{it.sourceId() in selection}} of ${selection.size} selected originals are registered. Originals were preserved."
  }) {
   try {var latest=access.store.read();for(id in selection){currentCoroutineContext().ensureActive();access.checkCurrent();latest=registerRecord(id,extract)};latest}
   catch(cancelled:CancellationException){throw cancelled}
   catch(failure:Throwable){throw BulkRegistrationFailure(selection,failure)}
  }
 }
 /** Acquire in the originating encrypted repository, then bind provider claims to its verified identity. */
 fun importAndRegister(uri:Uri,extract:Boolean=true)=perform("Evidence imported; original preserved") {
  val acquired=access.sources.import(uri)
  registerRecord(acquired.item.id,extract,acquired)
 }
 private suspend fun registerRecord(sourceId:String,extract:Boolean,acquired:EvidenceOriginal?=null):EvidenceCatalog {
  val operation=currentCoroutineContext()
  val existing=access.store.read().evidence().firstOrNull{it.sourceId()==sourceId}
  if(existing!=null){checkSource(existing,access.sources.inspect(sourceId));return access.store.read()}
  val registered=readRegistration(sourceId,acquired)
   operation.ensureActive()
   val saved=access.store.update{catalog ->
    operation.ensureActive();checkSource(registered,access.sources.inspect(sourceId))
    if(catalog.evidence().any{it.sourceId()==sourceId})catalog else catalog.upsertEvidence(registered,now(),"IMPORT_EVIDENCE")
   }
   // OCR follows a completely authenticated source read. Registration survives extraction failure.
   return if(extract && registered.size()<=OCR_LIMIT) {
    runCatching{extractRecord(saved.evidence().single{it.sourceId()==sourceId},0)}.getOrElse {
     if(it is CancellationException)throw it
     access.checkCurrent();saved
    }
   } else saved
 }
 private suspend fun readRegistration(sourceId:String,acquired:EvidenceOriginal?=null):EvidenceCatalog.Evidence {
  val operation=currentCoroutineContext()
  var sample:ByteArray?=null
  try {
   val registered=access.reader{!operation.isActive}.readVerified(sourceId){source,input ->
    val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536)
    val chunks=ArrayList<ByteArray>();var total=0L;var retained=0
    try {
     while(true){access.checkCurrent();val n=input.read(buffer);if(n<0)break
      total+=n;check(total<=source.item.plaintextSize);digest.update(buffer,0,n)
      if(retained<METADATA_LIMIT){val count=minOf(n,METADATA_LIMIT-retained);chunks+=buffer.copyOf(count);retained+=count}
     }
     val hash=digest.digest();check(total==source.item.plaintextSize&&MessageDigest.isEqual(hash,source.item.plaintextSha256))
     sample=ByteArray(retained)
     var offset=0;for(chunk in chunks){chunk.copyInto(sample!!,offset);offset+=chunk.size}
     val base=if(acquired==null)source.metadata else EvidenceMetadata.acquisition(source,acquired)
     val metadata=EvidenceMetadata.extract(source.item,sample!!,base)
     EvidenceCatalog.Evidence(UUID.randomUUID().toString(),source.item.id,source.item.displayName,source.item.mimeType,
      hash.joinToString(""){"%02x".format(it.toInt() and 255)},total,System.currentTimeMillis(),metadata,"","",emptyList(),emptyList(),emptyList(),emptyList())
    }finally{buffer.fill(0);chunks.forEach{it.fill(0)}}
   }
   operation.ensureActive();access.checkCurrent();return registered
  }finally{sample?.fill(0)}
 }

 /** Transient owner review: no catalogue mutation before the explicit Save action. */
 suspend fun prepareImport(sourceIds:List<String>):EvidenceCatalog=withContext(Dispatchers.IO){
  require(sourceIds.isNotEmpty()&&sourceIds.size<=128&&sourceIds.distinct().size==sourceIds.size)
  val operation=currentCoroutineContext();var draft=EvidenceCatalog.empty(access.owner)
  for(id in sourceIds){
   operation.ensureActive();access.checkCurrent()
   val record=readRegistration(id,importAcquisitions[id]);draft=draft.upsertEvidence(record,now(),"REVIEW_IMPORT")
   if(record.size()<=OCR_LIMIT) {
    val extraction=withVerifiedBytes(access.reader{!operation.isActive},id,OCR_LIMIT,{checkSource(record,it)}) {bytes->ocr.recognize(bytes,record.mime(),0){current()&&operation.isActive}}
    draft=draft.saveOcr(record.id(),extraction,now())
   }
  }
  access.checkCurrent();operation.ensureActive();draft
 }
 fun saveReviewedImport(draft:EvidenceCatalog)=perform("Owner-reviewed evidence saved") {
  require(draft.owner()==access.owner)
  access.store.update {current ->
   var result=current
   for(record in draft.evidence()) {
    checkSource(record,access.sources.inspect(record.sourceId()))
    check(result.evidence().none{it.sourceId()==record.sourceId()}){"This source is already registered; review it in the library"}
    result=result.upsertEvidence(record,now(),"OWNER_APPROVE_IMPORT")
   }
   for(incident in draft.incidents())result=result.upsertIncident(incident,now(),"OWNER_APPROVE_IMPORT_GROUP")
   result
  }
 }
 fun importOriginalsForReview(uris:List<Uri>)=perform("Original imported encrypted; use Add existing evidence to review before registration") {
  require(uris.isNotEmpty()&&uris.size<=128)
  val imported=uris.map{uri->access.checkCurrent();access.sources.import(uri).also{acquired->importAcquisitions[acquired.item.id]=acquired}}
  val sources=access.sources.list()
  withContext(Dispatchers.Main.immediate){publish{it.copy(originals=sources,reviewImportIds=imported.map{original->original.item.id}.distinct())}}
  access.store.read()
 }
 fun extract(evidenceId:String,rotation:Int=0)=perform("Extraction saved as unverified text") {
  val record=access.store.read().evidence().single{it.id()==evidenceId};extractRecord(record,rotation)
 }
 /** Bytes are borrowed on IO only after complete verification; do not retain them beyond consumer return.
  * This returns Unit: plaintext ownership never crosses the cancellable IO-to-caller handoff.
  */
 suspend fun withOriginalBytes(evidenceId:String,limit:Int=OCR_LIMIT,consumer:suspend(ByteArray)->Unit):Unit=withContext(Dispatchers.IO) {
  require(limit in 1..OCR_LIMIT)
  val operation=currentCoroutineContext()
  val record=access.store.read().evidence().single{it.id()==evidenceId}
  withVerifiedBytes(access.reader{!operation.isActive},record.sourceId(),limit,{checkSource(record,it)},consumer)
 }
 private suspend fun extractRecord(record:EvidenceCatalog.Evidence,rotation:Int):EvidenceCatalog {
  require(record.size()<=OCR_LIMIT){"Original exceeds local OCR limit"}
  val operation=currentCoroutineContext()
  return withVerifiedBytes(access.reader{!operation.isActive},record.sourceId(),OCR_LIMIT,{checkSource(record,it)}){bytes ->
   val result=ocr.recognize(bytes,record.mime(),rotation){current()&&operation.isActive}
   operation.ensureActive();access.checkCurrent()
   access.store.update{catalog->
    operation.ensureActive();checkSource(record,access.sources.inspect(record.sourceId()))
    val present=catalog.evidence().single{it.id()==record.id()};check(present.sourceId()==record.sourceId()&&present.sha256()==record.sha256())
    catalog.saveOcr(record.id(),result,now())
   }
  }
 }

 fun verify(evidenceId:String)=perform("Original integrity checked") {
  val catalog=access.store.read();val record=catalog.evidence().single{it.id()==evidenceId}
  val status=try {
   val operation=currentCoroutineContext()
   access.reader{!operation.isActive}.readVerified(record.sourceId()){source,input ->
    checkSource(record,source);val buffer=ByteArray(65536)
    try{while(input.read(buffer)>=0)access.checkCurrent()}finally{buffer.fill(0)}
   };"SHA-256 matches the imported original; this does not establish content authenticity"
  }catch(failure:Throwable){if(failure is CancellationException)throw failure;access.checkCurrent();"Original unavailable or changed; do not rely on this reference"}
  withContext(Dispatchers.Main.immediate){publish{it.copy(integrity=it.integrity+(evidenceId to status))}}
  catalog
 }
 fun cancelOperations(){close()}
 override fun close(){access.close();retirement.close();scope.cancel();if(reviewStore.isInitialized())reviewStore.value.close();clearOnMain()}
 companion object {
  private const val METADATA_LIMIT=2*1024*1024
  private const val OCR_LIMIT=32*1024*1024
  fun now()=System.currentTimeMillis()
  fun annotated(e:EvidenceCatalog.Evidence,name:String,description:String,notes:String,tags:List<String>,participants:List<String>)=
   EvidenceCatalog.Evidence(e.id(),e.sourceId(),name,e.mime(),e.sha256(),e.size(),e.importedAt(),e.metadata(),description,notes,tags,participants,e.ocr(),e.corrections())
  fun checkSource(record:EvidenceCatalog.Evidence,source:EvidenceOriginal){
   check(record.sourceId()==source.item.id&&record.mime()==source.item.mimeType&&record.size()==source.item.plaintextSize&&record.metadata().vaultOnly()==source.item.vaultOnly)
   check(record.sha256()==source.item.plaintextSha256.joinToString(""){"%02x".format(it.toInt() and 255)}){"Original changed"}
  }
  /** Own the callback-created array before any outer repository/access postcondition can fail. */
  internal suspend fun <T> withVerifiedBytes(reader:EvidenceSourceReader,sourceId:String,limit:Int,
   validate:(EvidenceOriginal)->Unit,consumer:suspend(ByteArray)->T):T {
   require(limit in 1..OCR_LIMIT)
   val operation=currentCoroutineContext();var owned:ByteArray?=null
   try {
    reader.readVerified(sourceId){source,input ->
     validate(source)
     readBounded(input,limit){reader.current()&&operation.isActive}.also{owned=it}
    }
    operation.ensureActive();check(reader.current())
    return consumer(checkNotNull(owned))
   }finally{owned?.fill(0)}
  }
  fun readBounded(input:InputStream,limit:Int,current:()->Boolean):ByteArray {
   val chunks=ArrayList<Pair<ByteArray,Int>>();var size=0
   try {
    while(true){check(current());val chunk=ByteArray(minOf(65536,limit-size+1));val count=try{input.read(chunk)}catch(failure:Throwable){chunk.fill(0);throw failure}
     if(count<0){chunk.fill(0);break};chunks+=chunk to count;size+=count;require(size<=limit){"Original exceeds operation limit"}}
    check(current());val out=ByteArray(size)
    try{var offset=0;for((chunk,count) in chunks){chunk.copyInto(out,offset,0,count);offset+=count};return out}
    catch(failure:Throwable){out.fill(0);throw failure}
   }finally{chunks.forEach{it.first.fill(0)}}
  }
 }
}
