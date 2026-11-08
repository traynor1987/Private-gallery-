package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import java.security.MessageDigest
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.editor.AiHttpRequest
import uk.co.traynor.privategallery.core.editor.AiHttpResponse
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vault.*

data class EvidenceOriginal(val item:VaultItem,val metadata:EvidenceCatalog.Metadata)
internal interface EvidenceSources {
 fun list():List<EvidenceOriginal>
 fun inspect(sourceId:String):EvidenceOriginal
 fun <T> readVerified(sourceId:String,cancelled:()->Boolean,consume:(EvidenceOriginal,InputStream)->T):T
 fun import(uri:Uri):EvidenceOriginal
 fun imageCrop(sourceId:String):NormalizedCrop?=null
 fun collections():List<VaultCollection> = emptyList()
 fun collectionSnapshot(ids:Set<String>):EvidenceCollectionSnapshot = EvidenceCollectionSnapshot(emptyMap(),emptySet())
 fun collectionIndexFile():File? = null
}
internal interface EvidenceSourceReader {
 val owner:EvidenceCatalog.Owner
 fun current():Boolean
 fun inspect(sourceId:String):EvidenceOriginal
 fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T
}
internal interface EvidenceRoute:AutoCloseable {
 val owner:EvidenceCatalog.Owner
 val label:String
 val identity:String
 fun current():Boolean
 suspend fun execute(request:AiHttpRequest):AiHttpResponse
 override fun close()
}
internal data class EvidenceExportPlan(val catalog:EvidenceCatalog,val incidentIds:List<String>,val evidenceIds:List<String>,val digest:String,val plaintextConfirmed:Boolean)
internal data class EvidenceExportReceipt(val sha256:String,val bytes:Long,val encrypted:Boolean)

/** Owns no retained key. Every source/catalogue operation acquires the captured authentication lease. */
internal class EvidenceAccess private constructor(
 val owner:EvidenceCatalog.Owner,val root:File,private val rawLeases:EvidenceEncryptedStore.LeaseFactory,
 private val guard:()->Boolean,private val retireBoundary:((()->Unit)->Unit),
 private val sourceFactory:(EvidenceAccess)->EvidenceSources,
 internal val hiddenSession:HiddenSession?=null,
):AutoCloseable {
 private val closed=AtomicBoolean()
 private val retired=AtomicBoolean()
 private val activeLeases=ConcurrentHashMap.newKeySet<EvidenceEncryptedStore.KeyLease>()
 private val resources=ConcurrentHashMap.newKeySet<AutoCloseable>()
 private val callbacks=ConcurrentHashMap.newKeySet<()->Unit>()
 @Volatile private var revocation:AutoCloseable?=null
 private val cleanup=Runnable {
  if(retired.compareAndSet(false,true)) {
   // CHM key-set size can change between Kotlin toList's size==1 fast path and next().
   // Collection.toArray supplies a concurrency-safe snapshot while leases remove themselves.
   callbacks.toTypedArray().forEach{runCatching(it)};callbacks.clear()
   resources.toTypedArray().forEach{runCatching{it.close()}};resources.clear()
   activeLeases.toTypedArray().forEach{runCatching{it.close()}};activeLeases.clear()
   runCatching{revocation?.close()};revocation=null
  }
 }
 fun current():Boolean {
  if(closed.get())return false
  if(!runCatching(guard).getOrDefault(false)){markRevoked();return false}
  return true
 }
 /** Main-only final publication fence. Retirement uses the same originating authority monitor.
  * The action must only assign UI state/resources; never block, acquire repository locks or clean up.
  * Hidden onRevoked remains a flag+enqueue callback under its existing session monitor.
  */
 fun publishIfCurrent(action:()->Unit):Boolean {
  check(Looper.myLooper()===Looper.getMainLooper()){"Evidence publication must run on Main"}
  var published=false
  retireBoundary {if(current()){action();published=true}}
  return published
 }
 fun checkCurrent(){check(current()){"Evidence access retired; unlock the originating vault again"}}
 /** Hidden revocation callback uses only this atomic flag and a preallocated cleanup enqueue. */
 private fun markRevoked(){if(closed.compareAndSet(false,true))retirement.execute(cleanup)}
 fun onRetire(action:()->Unit):AutoCloseable {
  callbacks.add(action)
  if(closed.get()){callbacks.remove(action);runCatching(action)}
  return AutoCloseable{callbacks.remove(action)}
 }
 private fun own(resource:AutoCloseable):AutoCloseable {
  resources.add(resource)
  if(!current()){resources.remove(resource);runCatching{resource.close()};error("Evidence access retired")}
  return AutoCloseable{resources.remove(resource);resource.close()}
 }
 val leases=EvidenceEncryptedStore.LeaseFactory {
  checkCurrent();val original=rawLeases.acquire()
  val managed=object:EvidenceEncryptedStore.KeyLease {
   private val ended=AtomicBoolean()
   override fun current()= !ended.get() && this@EvidenceAccess.current() && original.current()
   override fun key():ByteArray{check(current());return original.key()}
   override fun commit(action:EvidenceEncryptedStore.CommitAction){check(current());original.commit{check(current());action.run();check(current())}}
   override fun close(){if(ended.compareAndSet(false,true)){activeLeases.remove(this);original.close()}}
  }
  activeLeases.add(managed)
  if(!current()){managed.close();error("Evidence access retired")}
  managed
 }
 val store=EvidenceEncryptedStore(root,owner,leases)
 val sources=sourceFactory(this)
 internal fun providerStream(context:Context,uri:Uri):InputStream=ProviderAccess.providerStream(context,uri,this)
 fun reader(cancelled:()->Boolean={false}):EvidenceSourceReader=object:EvidenceSourceReader {
  override val owner=this@EvidenceAccess.owner
  override fun current()=this@EvidenceAccess.current()&&!cancelled()
  override fun inspect(sourceId:String):EvidenceOriginal{check(current());return sources.inspect(sourceId).also{check(current())}}
  override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T {
   check(current());var captured:EvidenceOriginal?=null
   return sources.readVerified(sourceId,{!current()}){source,input->captured=source;consume(source,input)}.also {
    check(current());requireSameOriginal(checkNotNull(captured).item,sources.inspect(sourceId).item);check(current())
   }
  }
 }
 /** Short scope-state changes must serialize with the same authenticated commit/retirement gate.
  * Never perform provider IO, repository work or resource cleanup inside this boundary. */
 internal fun withRetirementBoundary(action:()->Unit)=retireBoundary(action)
 /** Restricts an existing Primary authority without copying its key into a new authority.
  * Child leases delegate through the parent; child close only retires child resources/leases. */
 internal fun primaryChild(authorized:()->Boolean):EvidenceAccess {
  check(owner==EvidenceCatalog.Owner.PRIMARY);checkCurrent()
  val child=EvidenceAccess(owner,root,leases,{this@EvidenceAccess.current()&&authorized()},retireBoundary,sourceFactory)
  child.revocation=onRetire{child.markRevoked()}
  if(!child.current()){
   child.close();child.revocation?.close();child.revocation=null
   error("Originating evidence authority retired")
  }
  return child
 }
 override fun close(){retireBoundary{markRevoked()}}
 companion object {
  internal fun requireSameOriginal(expected:VaultItem,actual:VaultItem) {
   check(expected.state==VaultItemState.COMPLETE&&actual.state==VaultItemState.COMPLETE&&
    expected.id==actual.id&&expected.mimeType==actual.mimeType&&expected.plaintextSize==actual.plaintextSize&&
    expected.vaultOnly==actual.vaultOnly&&MessageDigest.isEqual(expected.plaintextSha256,actual.plaintextSha256)&&
    MessageDigest.isEqual(expected.payloadNonce,actual.payloadNonce)){"Original unavailable or changed"}
  }
  // Same fixed encrypted repository layouts; existence is rechecked after the authenticated fd closes.
  private fun primaryPayload(context:Context,id:String)=File(context.filesDir,"vault/payloads/$id.vault")
  private fun hiddenPayload(context:Context,id:String)=File(context.noBackupFilesDir,"hidden-media-v1/media-$id.enc")
  private val retirement=Executors.newSingleThreadExecutor{r->Thread(r,"evidence-retirement").apply{isDaemon=true}}
  fun primary(context:Context,identity:Any,currentIdentity:()->Any?,authorized:()->Boolean,copyKey:()->ByteArray,gate:Any):EvidenceAccess {
   val factory=EvidenceAuthorities.primary(identity,{currentIdentity()},{authorized()},{copyKey()},gate)
   return EvidenceAccess(EvidenceCatalog.Owner.PRIMARY,File(context.filesDir,"evidence-state"),factory,
    {synchronized(gate){currentIdentity()===identity&&authorized()}},{action->synchronized(gate){action()}},{access->PrimarySources(context,access)}).also{it.checkCurrent()}
  }
  fun hidden(context:Context,session:HiddenSession,authorized:()->Boolean):EvidenceAccess {
   val authority=session.authority();val valid={authorized()&&session.isAuthorityCurrent(authority)}
   val factory=EvidenceAuthorities.hidden(session,authority,{authorized()})
   val access=EvidenceAccess(EvidenceCatalog.Owner.HIDDEN,File(context.noBackupFilesDir,"evidence-state"),factory,
    valid,{action->synchronized(session){action()}},{owner->HiddenSources(context,session,owner)},session)
   access.revocation=session.onRevoked(authority){access.markRevoked()}
   if(!access.current()){access.close();access.revocation?.close();error("Hidden evidence authority retired")}
   return access
  }
 }
 private class PrimarySources(private val context:Context,private val access:EvidenceAccess):EvidenceSources {
  override fun collections() = repository { repo, _ -> repo.collections() }
  override fun collectionSnapshot(ids:Set<String>) = repository { repo, _ ->
   evidenceCollections(repo.backupSnapshot(), ids) { primaryPayload(context, it).isFile }
  }
  override fun collectionIndexFile() = File(context.filesDir, "vault/vault-index.enc")

  private fun <T> repository(action:(AndroidVaultRepository,EvidenceEncryptedStore.KeyLease)->T):T=access.leases.acquire().use{lease ->
   access.checkCurrent();val repo=AndroidVaultRepository(context,lease.key(),{publish->lease.commit{publish()}},{access.current()&&lease.current()})
   action(repo,lease).also{access.checkCurrent();check(lease.current())}
  }
  override fun list()=repository{repo,_->repo.items().filter{it.state==VaultItemState.COMPLETE&&EvidenceMetadata.supported(it.mimeType)&&primaryPayload(context,it.id).isFile}.map{EvidenceOriginal(it,EvidenceMetadata.fromItem(it))}}
  override fun imageCrop(sourceId:String):NormalizedCrop?=repository{repo,_->
   check(repo.items().any{it.id==sourceId&&it.state==VaultItemState.COMPLETE})
   repo.imageEdit(sourceId)?.crop
  }
  override fun inspect(sourceId:String):EvidenceOriginal {
   val source=list().singleOrNull{it.item.id==sourceId}?:error("Original unavailable in this vault")
   check(primaryPayload(context,sourceId).isFile){"Original payload unavailable"};return source
  }
  override fun <T> readVerified(sourceId:String,cancelled:()->Boolean,consume:(EvidenceOriginal,InputStream)->T):T=repository{repo,_ ->
   var captured:VaultItem?=null
   repo.withHiddenCopySource(sourceId,{!access.current()||cancelled()}){item,input ->
    check(EvidenceMetadata.supported(item.mimeType));captured=item
    consume(EvidenceOriginal(item,EvidenceMetadata.fromItem(item)),input)
   }.also {
    check(!cancelled());access.checkCurrent()
    requireSameOriginal(checkNotNull(captured),checkNotNull(repo.items().singleOrNull{it.id==sourceId&&it.state==VaultItemState.COMPLETE}))
    check(primaryPayload(context,sourceId).isFile){"Original payload unavailable"}
    access.checkCurrent();check(!cancelled())
   }
  }
  override fun import(uri:Uri):EvidenceOriginal=repository{repo,lease ->
   val acquired=ProviderAccess.provider(context,uri)
   val result=VaultImportCoordinator(repo).acquire(VaultImportSource(acquired.name,acquired.mime,{ProviderAccess.providerStream(context,uri,access)},
    isCancelled={!access.current()},publishIfAllowed={publish->lease.commit{publish()}}))
   val item=when(result){is ImportResult.Imported->result.item;is ImportResult.Duplicate->result.existing}
   EvidenceOriginal(item,acquired.metadata(EvidenceMetadata.fromItem(item)))
  }
 }
 private class HiddenSources(context:Context,session:HiddenSession,private val access:EvidenceAccess):EvidenceSources {
  private val context=context
  private val repo=HiddenRepository(context,session,publishIfAllowed={publish->synchronized(session){access.checkCurrent();publish();access.checkCurrent()}})

  override fun collections():List<VaultCollection> {
   access.checkCurrent()
   return repo.collections().also { access.checkCurrent() }
  }
  override fun collectionSnapshot(ids:Set<String>):EvidenceCollectionSnapshot {
   access.checkCurrent()
   return evidenceCollections(repo.backupSnapshot(), ids) { hiddenPayload(context, it).isFile }
    .also { access.checkCurrent() }
  }
  override fun collectionIndexFile() = File(context.noBackupFilesDir,
   "hidden-media-v1/metadata-00000000-0000-0000-0000-000000000000.enc")
  override fun list():List<EvidenceOriginal>{access.checkCurrent();return repo.items().filter{it.state==VaultItemState.COMPLETE&&EvidenceMetadata.supported(it.mimeType)&&hiddenPayload(context,it.id).isFile}.map{EvidenceOriginal(it,EvidenceMetadata.fromItem(it))}.also{access.checkCurrent()}}
  override fun inspect(sourceId:String):EvidenceOriginal {
   val source=list().singleOrNull{it.item.id==sourceId}?:error("Original unavailable in this vault")
   check(hiddenPayload(context,sourceId).isFile){"Original payload unavailable"};return source
  }
  override fun <T> readVerified(sourceId:String,cancelled:()->Boolean,consume:(EvidenceOriginal,InputStream)->T):T {
   access.checkCurrent();var captured:VaultItem?=null
   return repo.withTransferSource(sourceId,{!access.current()||cancelled()}){item,input ->
    check(item.state==VaultItemState.COMPLETE&&EvidenceMetadata.supported(item.mimeType));captured=item
    consume(EvidenceOriginal(item,EvidenceMetadata.fromItem(item)),input)
   }.also {
    access.checkCurrent();check(!cancelled())
    requireSameOriginal(checkNotNull(captured),inspect(sourceId).item)
    access.checkCurrent();check(!cancelled())
   }
  }
  override fun import(uri:Uri):EvidenceOriginal {
   access.checkCurrent();val acquired=ProviderAccess.provider(context,uri)
   val item=ProviderAccess.providerStream(context,uri,access).use{input->repo.importMedia(acquired.name,acquired.mime,input)}
   access.checkCurrent();return EvidenceOriginal(item,acquired.metadata(EvidenceMetadata.fromItem(item)))
  }
 }
 private data class Provider(val name:String,val mime:String,val modified:Long) {
  fun metadata(base:EvidenceCatalog.Metadata):EvidenceCatalog.Metadata = EvidenceCatalog.Metadata(
   name.take(200),0,modified.coerceAtLeast(0),base.dateAddedAt(),base.captureAt(),base.width(),base.height(),
   base.embedded()+mapOf(
    "sourceNameProvenance" to "Authorised document provider filename claim",
    "fileModifiedProvenance" to if(modified>0) "Document provider millisecond timestamp claim" else "Unavailable",
   ),base.vaultOnly(),
  )
 }
 private object ProviderAccess {
  fun provider(context:Context,uri:Uri):Provider {
   require(uri.scheme=="content"){"Choose evidence through the authorised document picker"}
   val mime=context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase()?.trim()?:error("Document type unavailable")
   require(EvidenceMetadata.supported(mime)){"Supported evidence is images, PDF, UTF8 text or CSV"}
   var name="Evidence original";var modified=0L
   runCatching{context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use{cursor ->
    if(cursor.moveToFirst()){cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf{it>=0}?.let{column->if(!cursor.isNull(column))name=cursor.getString(column).take(200)};cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED).takeIf{it>=0}?.let{column->if(!cursor.isNull(column))modified=cursor.getLong(column).coerceAtLeast(0)}}
   }}
   return Provider(name.ifBlank{"Evidence original"},mime,modified)
  }
  fun providerStream(context:Context,uri:Uri,access:EvidenceAccess):InputStream {
   access.checkCurrent();val signal=CancellationSignal();val cancellation=access.onRetire{signal.cancel()}
   var pendingDescriptor:android.content.res.AssetFileDescriptor?=null
   try {
    val descriptor=checkNotNull(context.contentResolver.openAssetFileDescriptor(uri,"r",signal));pendingDescriptor=descriptor
    val input=descriptor.createInputStream();val owned=access.own(input)
    return object:java.io.FilterInputStream(input) {
     override fun read():Int{access.checkCurrent();return super.read().also{access.checkCurrent()}}
     override fun read(b:ByteArray,off:Int,len:Int):Int{access.checkCurrent();return super.read(b,off,len).also{access.checkCurrent()}}
     override fun close(){try{owned.close()}finally{cancellation.close();descriptor.close()}}
    }
   }catch(failure:Throwable){cancellation.close();signal.cancel();runCatching{pendingDescriptor?.close()};throw failure}
  }
 }
}
