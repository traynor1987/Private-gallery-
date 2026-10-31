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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A screen-scoped owner capability. No key, original bytes or plaintext index is persisted here. */
internal class EvidenceController(context:Context,val access:EvidenceAccess):AutoCloseable {
 data class State(val catalog:EvidenceCatalog?=null,val originals:List<EvidenceOriginal> = emptyList(),
  val busy:Boolean=false,val message:String?=null,val integrity:Map<String,String> = emptyMap())
 private val main=Handler(Looper.getMainLooper())
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val mutex=Mutex()
 private val ocr=EvidenceOcr(context.applicationContext)
 private val mutable=MutableStateFlow(State())
 val state=mutable.asStateFlow()
 private val retirement=access.onRetire {
  scope.cancel()
  clearOnMain()
 }
 private fun clearOnMain() {
  if(Looper.myLooper()===Looper.getMainLooper())mutable.value=State() else main.post{mutable.value=State()}
 }
 private fun publish(change:(State)->State)=access.publishIfCurrent{mutable.value=change(mutable.value)}
 init {refresh()}
 fun current()=access.current()
 private fun perform(label:String,work:suspend ()->EvidenceCatalog) {
  scope.launch {
   mutex.withLock {
    if(!current())return@withLock
    if(!publish{it.copy(busy=true,message=null)})return@withLock
    try {
     val next=withContext(Dispatchers.IO){ensureActive();access.checkCurrent();work().also{access.checkCurrent();ensureActive()}}
     publish{it.copy(catalog=next,busy=false,message=label)}
    }catch(cancelled:CancellationException){throw cancelled
    }catch(_:Throwable){publish{it.copy(busy=false,message="Operation could not complete. No original was removed. Reopen or verify the selected evidence.")}}
   }
  }
 }
 fun refresh()=perform("Evidence Locker ready") {
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
  withContext(Dispatchers.Main.immediate){publish{it.copy(catalog=catalog,originals=sources,integrity=integrity)}}
  catalog
 }
 /** Transforms are trusted local UI operations, never model output or imported JSON. */
 fun mutate(label:String,transform:(EvidenceCatalog)->EvidenceCatalog)=perform(label){access.store.update(transform)}
 fun register(sourceId:String,extract:Boolean=true)=perform("Evidence added; original preserved") {registerRecord(sourceId,extract)}
 /** Acquire in the originating encrypted repository, then bind provider claims to its verified identity. */
 fun importAndRegister(uri:Uri,extract:Boolean=true)=perform("Evidence imported; original preserved") {
  val acquired=access.sources.import(uri)
  registerRecord(acquired.item.id,extract,acquired)
 }
 private suspend fun registerRecord(sourceId:String,extract:Boolean,acquired:EvidenceOriginal?=null):EvidenceCatalog {
  val operation=currentCoroutineContext()
  val existing=access.store.read().evidence().firstOrNull{it.sourceId()==sourceId}
  if(existing!=null){checkSource(existing,access.sources.inspect(sourceId));return access.store.read()}
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
  }finally{sample?.fill(0)}
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
 override fun close(){access.close();retirement.close();scope.cancel();clearOnMain()}
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
