package uk.co.traynor.privategallery.core.evidence

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

/** Controller policy tests use immutable source identities, independently of rendered UI. */
class EvidenceController179Test {
 @Test fun editsPreserveOriginalFingerprintAndOcrHistory() {
  val e=EvidenceCatalog.Evidence(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"Original","text/plain","ab".repeat(32),3,1,
   EvidenceCatalog.Metadata("source",0,0,1,0,0,0,emptyMap(),false),"","",emptyList(),emptyList(),emptyList(),emptyList())
  val edited=EvidenceController.annotated(e,"Renamed","Description","Notes",listOf("tag"),listOf("Owner identified"))
  assertEquals(e.sourceId(),edited.sourceId());assertEquals(e.sha256(),edited.sha256());assertEquals(e.metadata(),edited.metadata())
  assertEquals("Notes",edited.notes());assertEquals(e.ocr(),edited.ocr())
 }
 @Test fun boundedReadRejectsOversizeAndLeavesNoPlaintextOutputFile() {
  val original=ByteArray(1025){42}
  try { EvidenceController.readBounded(original.inputStream(),1024){true};fail("oversize accepted") }catch(expected:IllegalArgumentException){}
  val result=EvidenceController.readBounded(original.inputStream(),1025){true}
  assertArrayEquals(original,result);result.fill(0)
  try { EvidenceController.readBounded(original.inputStream(),1025){false};fail("retired read accepted") }catch(expected:IllegalStateException){}
 }
 @Test fun scoped_bytes_are_wiped_when_outer_reader_rejects_after_consumer_returns() = kotlinx.coroutines.runBlocking {
  var retained:ByteArray?=null;var consumed=false
  val reader=object:EvidenceSourceReader {
   override val owner=EvidenceCatalog.Owner.PRIMARY
   override fun current()=true
   override fun inspect(sourceId:String):EvidenceOriginal=error("unused")
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,java.io.InputStream)->T):T {
    val item=original();val result=consume(item,"sensitive".byteInputStream());retained=result as ByteArray
    error("Outer final authority check rejected")
   }
  }
  try{EvidenceController.withVerifiedBytes(reader,"source",100,{}){consumed=true};fail("late reader failure accepted")}catch(expected:IllegalStateException){}
  assertFalse(consumed);assertNotNull(retained);assertTrue(retained!!.all{it==0.toByte()})
 }
 @Test fun cancellation_during_io_return_cannot_abandon_owned_bytes() = kotlinx.coroutines.runBlocking {
  var retained:ByteArray?=null;var delivered=false
  val queue=java.util.concurrent.LinkedBlockingQueue<Runnable>()
  val caller=object:kotlinx.coroutines.CoroutineDispatcher() {
   override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){queue.add(block)}
  }
  val reader=object:EvidenceSourceReader {
   override val owner=EvidenceCatalog.Owner.PRIMARY
   override fun current()=true
   override fun inspect(sourceId:String):EvidenceOriginal=error("unused")
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,java.io.InputStream)->T):T=consume(original(),"sensitive".byteInputStream())
  }
  val job=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job()+caller).launch {
   kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    EvidenceController.withVerifiedBytes(reader,"source",100,{}) {bytes->retained=bytes}
   }
   delivered=true
  }
  checkNotNull(queue.poll(5,java.util.concurrent.TimeUnit.SECONDS)).run()
  val delivery=checkNotNull(queue.poll(5,java.util.concurrent.TimeUnit.SECONDS)) // IO result awaiting caller/Main dispatch.
  assertNotNull(retained);assertTrue(retained!!.all{it==0.toByte()})
  job.cancel();delivery.run();job.join();assertTrue(job.isCancelled);assertFalse(delivered)
 }
 @Test fun acquisition_claims_are_bounded_bound_to_original_and_unavailable_for_existing_reference() {
  val source=original();val claim=EvidenceCatalog.Metadata("provider proof.txt",0,123456L,1,0,0,0,
   mapOf("sourceNameProvenance" to "Authorised document provider filename claim","fileModifiedProvenance" to "Document provider millisecond timestamp claim"),false)
  val acquired=EvidenceOriginal(source.item,claim)
  val retained=EvidenceMetadata.acquisition(source,acquired)
  assertEquals("provider proof.txt",retained.sourceName());assertEquals(123456L,retained.fileModifiedAt())
  assertEquals(claim.embedded()["fileModifiedProvenance"],retained.embedded()["fileModifiedProvenance"])
  assertEquals(0L,EvidenceMetadata.fromItem(source.item).fileModifiedAt())
  val bounded=EvidenceMetadata.acquisition(source,acquired.copy(metadata=EvidenceCatalog.Metadata("x".repeat(1000),0,1,1,0,0,0,
   mapOf("sourceNameProvenance" to "n".repeat(1000),"providerUri" to "content://must-not-be-retained"),false)))
  assertEquals(200,bounded.sourceName().length);assertEquals(200,bounded.embedded()["sourceNameProvenance"]!!.length)
  assertFalse(bounded.embedded().containsKey("providerUri"))
  try{EvidenceMetadata.acquisition(source,acquired.copy(item=source.item.copy(vaultOnly=true)));fail("foreign policy claim accepted")}catch(expected:IllegalStateException){}
 }
 @Test fun refresh_flags_live_missing_changed_policy_and_fingerprint_without_full_read() = kotlinx.coroutines.runBlocking {
  val base=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  val root=java.io.File(base.cacheDir,"evidence-refresh-${UUID.randomUUID()}")
  val context=object:android.content.ContextWrapper(base) {
   override fun getFilesDir()=java.io.File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=java.io.File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():android.content.Context=this
  }
  val key=ByteArray(32){41};val repo=uk.co.traynor.privategallery.core.vault.AndroidVaultRepository(context,key)
  val items=listOf("match","trash","policy","fingerprint","missing").associateWith{kind->
   (repo.importVerified(uk.co.traynor.privategallery.core.vault.VaultImportSource("$kind.txt","text/plain",{"proof-$kind".byteInputStream()}))as uk.co.traynor.privategallery.core.vault.ImportResult.Imported).item
  }
  val access=EvidenceAccess.primary(context,key,{key},{true},{key.copyOf()},Any())
  val records=items.mapValues{(_,item)->EvidenceCatalog.Evidence(UUID.randomUUID().toString(),item.id,item.displayName,item.mimeType,
   item.plaintextSha256.joinToString(""){"%02x".format(it.toInt() and 255)},item.plaintextSize,1,EvidenceMetadata.fromItem(item),"","",emptyList(),emptyList(),emptyList(),emptyList())}
  access.store.update{initial->records.values.fold(initial){catalog,record->catalog.upsertEvidence(record,1,"IMPORT_EVIDENCE")}}
  repo.moveToRecentlyDeleted(items.getValue("trash").id)
  val index=uk.co.traynor.privategallery.core.vault.EncryptedIndexStore(java.io.File(context.filesDir,"vault"));val snapshot=index.loadSnapshot(key)
  index.saveSnapshot(snapshot.copy(items=snapshot.items.map{item->when(item.id){
   items.getValue("policy").id->item.copy(vaultOnly=true)
   items.getValue("fingerprint").id->item.copy(plaintextSha256=ByteArray(32){8})
   else->item
  }}),key)
  assertTrue(java.io.File(context.filesDir,"vault/payloads/${items.getValue("missing").id}.vault").delete())
  var controller:EvidenceController?=null
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  try {
   instrumentation.runOnMainSync{controller=EvidenceController(context,access)}
   val state=kotlinx.coroutines.withTimeout(10000){controller!!.state.first{!it.busy&&it.message=="Evidence Locker ready"}}
   assertEquals(records.size,state.integrity.size)
   val matching=state.integrity.getValue(records.getValue("match").id())
   assertTrue(matching.contains("metadata"));assertTrue(matching.contains("not been reverified"))
   for(kind in listOf("trash","missing"))assertTrue(kind,state.integrity.getValue(records.getValue(kind).id()).contains("unavailable"))
   for(kind in listOf("policy","fingerprint"))assertTrue(kind,state.integrity.getValue(records.getValue(kind).id()).contains("changed"))
   assertFalse(state.originals.any{it.item.id==items.getValue("missing").id||it.item.id==items.getValue("trash").id})
   assertEquals(records.size,access.store.read().evidence().size) // Warnings do not rewrite retained references.
  }finally{controller?.let{owned->instrumentation.runOnMainSync{owned.close()}}?:access.close();key.fill(0);root.deleteRecursively()}
 }
 @Test fun controller_state_paths_clear_at_retirement_and_reject_late_operation() = kotlinx.coroutines.runBlocking {
  val base=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  val root=java.io.File(base.cacheDir,"evidence-state-${UUID.randomUUID()}")
  val context=object:android.content.ContextWrapper(base) {
   override fun getFilesDir()=java.io.File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=java.io.File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():android.content.Context=this
  }
  val key=ByteArray(32){49};val access=EvidenceAccess.primary(context,key,{key},{true},{key.copyOf()},Any())
  val source=original();val record=EvidenceCatalog.Evidence(UUID.randomUUID().toString(),source.item.id,"Reference","text/plain",
   source.item.plaintextSha256.joinToString(""){"%02x".format(it.toInt() and 255)},9,1,source.metadata,"","",emptyList(),emptyList(),emptyList(),emptyList())
  access.store.update{it.upsertEvidence(record,1,"IMPORT_EVIDENCE")}
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  lateinit var controller:EvidenceController
  val retired=java.util.concurrent.atomic.AtomicBoolean(false)
  val afterRetirement=java.util.concurrent.CopyOnWriteArrayList<EvidenceController.State>()
  val observerScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main.immediate)
  val entered=java.util.concurrent.CountDownLatch(1);val resumed=java.util.concurrent.CountDownLatch(1);val returned=java.util.concurrent.CountDownLatch(1)
  var controllerRef:EvidenceController?=null
  try {
   instrumentation.runOnMainSync{controller=EvidenceController(context,access);controllerRef=controller;observerScope.launch{controller.state.collect{if(retired.get())afterRetirement+=it}}}
   kotlinx.coroutines.withTimeout(10000){controller.state.first{!it.busy&&it.message=="Evidence Locker ready"}}
   instrumentation.runOnMainSync{controller.verify(record.id())}
   val verified=kotlinx.coroutines.withTimeout(10000){controller.state.first{!it.busy&&it.message=="Original integrity checked"}}
   assertTrue(verified.integrity[record.id()]!!.contains("unavailable"))
   instrumentation.runOnMainSync{controller.mutate("must fail"){error("controlled operation error")}}
   kotlinx.coroutines.withTimeout(10000){controller.state.first{!it.busy&&it.message?.startsWith("Operation could not complete")==true}}
   instrumentation.runOnMainSync{controller.refresh()}
   kotlinx.coroutines.withTimeout(10000){controller.state.first{!it.busy&&it.message=="Evidence Locker ready"}}
   instrumentation.runOnMainSync{controller.mutate("late result"){entered.countDown();try{check(resumed.await(5,java.util.concurrent.TimeUnit.SECONDS));it}finally{returned.countDown()}}}
   assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(controller.state.value.busy)
   instrumentation.runOnMainSync{retired.set(true);controller.close();assertEquals(EvidenceController.State(),controller.state.value)}
   resumed.countDown();assertTrue(returned.await(5,java.util.concurrent.TimeUnit.SECONDS));instrumentation.waitForIdleSync()
   assertEquals(EvidenceController.State(),controller.state.value)
   assertTrue(afterRetirement.isNotEmpty());assertTrue(afterRetirement.all{it==EvidenceController.State()})
  }finally{resumed.countDown();controllerRef?.let{owned->instrumentation.runOnMainSync{owned.close()}}?:access.close();observerScope.coroutineContext[kotlinx.coroutines.Job]?.cancel();key.fill(0);root.deleteRecursively()}
 }
 /** Real import adapter + controller registration, with an in-process authorised-provider fixture. */
 @Test fun import_register_retains_provider_claims_and_reference_removal_preserves_original() = kotlinx.coroutines.runBlocking {
  val base=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  val root=java.io.File(base.cacheDir,"evidence-provider-${UUID.randomUUID()}").apply{mkdirs()}
  val original="provider proof,unchanged\n".toByteArray();val file=java.io.File(root,"provider-fixture.txt").apply{writeBytes(original)}
  val provider=object:android.content.ContentProvider() {
   override fun onCreate()=true
   override fun getType(uri:android.net.Uri)="text/plain"
   override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor {
    val columns=projection?:arrayOf(android.provider.OpenableColumns.DISPLAY_NAME,android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    return android.database.MatrixCursor(columns).apply{addRow(columns.map{if(it==android.provider.OpenableColumns.DISPLAY_NAME)"provider claim.txt" else 123456L})}
   }
   override fun openAssetFile(uri:android.net.Uri,mode:String)=android.content.res.AssetFileDescriptor(android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY),0,original.size.toLong())
   override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=error("read only")
   override fun delete(uri:android.net.Uri,selection:String?,selectionArgs:Array<out String>?):Int=error("read only")
   override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=error("read only")
  }
  provider.attachInfo(base,android.content.pm.ProviderInfo().apply{authority="evidence.fixture";exported=false})
  val resolver=android.content.ContentResolver.wrap(provider)
  val context=object:android.content.ContextWrapper(base) {
   override fun getFilesDir()=java.io.File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=java.io.File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():android.content.Context=this
   override fun getContentResolver():android.content.ContentResolver=resolver
  }
  val key=ByteArray(32){53};val access=EvidenceAccess.primary(context,key,{key},{true},{key.copyOf()},Any())
  var controllerRef:EvidenceController?=null
  lateinit var controller:EvidenceController
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  try {
   instrumentation.runOnMainSync{controller=EvidenceController(context,access);controllerRef=controller;controller.importAndRegister(android.net.Uri.parse("content://evidence.fixture/proof"),extract=false)}
   val added=kotlinx.coroutines.withTimeout(10000){controller.state.first{!it.busy&&it.catalog?.evidence()?.size==1}}
   val record=added.catalog!!.evidence().single()
   assertEquals("provider claim.txt",record.metadata().sourceName());assertEquals(123456L,record.metadata().fileModifiedAt())
   assertEquals("Authorised document provider filename claim",record.metadata().embedded()["sourceNameProvenance"])
   assertEquals("Document provider millisecond timestamp claim",record.metadata().embedded()["fileModifiedProvenance"])
   assertFalse(record.metadata().embedded().values.any{it.contains("content://")})
   controller.withOriginalBytes(record.id()){assertArrayEquals(original,it)}
   instrumentation.runOnMainSync{controller.mutate("Reference removed"){it.removeEvidence(record.id(),EvidenceController.now())}}
   kotlinx.coroutines.withTimeout(10000){controller.state.first{!it.busy&&it.message=="Reference removed"}}
   assertEquals(record.sourceId(),access.sources.list().single().item.id)
   access.reader().readVerified(record.sourceId()){_,input->val bytes=input.readBytes();try{assertArrayEquals(original,bytes)}finally{bytes.fill(0)}}
  }finally{controllerRef?.let{owned->instrumentation.runOnMainSync{owned.close()}}?:access.close();key.fill(0);original.fill(0);root.deleteRecursively()}
 }
 private fun original():EvidenceOriginal {
  val item=uk.co.traynor.privategallery.core.vault.VaultItem(UUID.randomUUID().toString(),"text/plain","proof.txt",1,9,
   java.security.MessageDigest.getInstance("SHA-256").digest("sensitive".toByteArray()),ByteArray(12),uk.co.traynor.privategallery.core.vault.VaultItemState.COMPLETE)
  return EvidenceOriginal(item,EvidenceMetadata.fromItem(item))
 }

}
