package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vault.*

class EvidenceAccess179Test {
 private fun scoped(root:File):Context=object:ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
  override fun getFilesDir()=File(root,"files").apply{mkdirs()}
  override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
  override fun getApplicationContext():Context=this
 }
 private fun refused(action:()->Unit){try{action();fail("Retired or foreign source admitted")}catch(expected:IllegalStateException){}}
 @Test fun source_references_are_same_vault_verified_and_read_only() {
  val root=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"evidence-access-${UUID.randomUUID()}");val context=scoped(root)
  val key=ByteArray(32){37};val hidden=HiddenSession{100L};assertTrue(hidden.complete(hidden.beginAuthentication(),ByteArray(32){61}))
  val primaryBytes="Primary evidence original\n".toByteArray();val hiddenBytes="Hidden evidence original\n".toByteArray()
  val primaryRepo=AndroidVaultRepository(context,key);val first=(primaryRepo.importVerified(VaultImportSource("primary.txt","text/plain",{ByteArrayInputStream(primaryBytes)}))as ImportResult.Imported).item
  val hiddenRepo=HiddenRepository(context,hidden);val second=hiddenRepo.importMedia("hidden.txt","text/plain",ByteArrayInputStream(hiddenBytes))
  val live=AtomicBoolean(true);val primary=EvidenceAccess.primary(context,key,{key},{live.get()},{key.copyOf()},Any());val secret=EvidenceAccess.hidden(context,hidden){live.get()}
  try {
   assertEquals(listOf(first.id),primary.sources.list().map{it.item.id});assertEquals(listOf(second.id),secret.sources.list().map{it.item.id})
   refused{primary.sources.inspect(second.id)};refused{secret.sources.inspect(first.id)}
   val actual=primary.sources.readVerified(first.id,{false}){source,input->assertEquals(first.id,source.item.id);input.readBytes()}
   try{assertArrayEquals(primaryBytes,actual)}finally{actual.fill(0)}
   val other=secret.sources.readVerified(second.id,{false}){_,input->input.readBytes()};try{assertArrayEquals(hiddenBytes,other)}finally{other.fill(0)}
   assertEquals(listOf(first.id),primaryRepo.items().map{it.id});assertEquals(listOf(second.id),hiddenRepo.items().map{it.id});assertArrayEquals(first.plaintextSha256,primaryRepo.items().single().plaintextSha256);assertArrayEquals(second.plaintextSha256,hiddenRepo.items().single().plaintextSha256)
   secret.close();assertFalse(secret.current());assertTrue("Closing Evidence Locker does not lock the Hidden Vault",hidden.isUnlocked)
   refused{secret.sources.list()};primary.close();refused{primary.store.read()};assertEquals(listOf(first.id),primaryRepo.items().map{it.id})
  }finally{primary.close();secret.close();hidden.lock();key.fill(0);primaryBytes.fill(0);hiddenBytes.fill(0);root.deleteRecursively()}
 }
 @Test fun revocation_and_cancelled_verified_reads_never_return_late_plaintext() {
  val root=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"evidence-revoke-${UUID.randomUUID()}");val context=scoped(root);val key=ByteArray(32){17};val live=AtomicBoolean(true)
  val item=(AndroidVaultRepository(context,key).importVerified(VaultImportSource("proof.txt","text/plain",{ByteArrayInputStream("original proof".toByteArray())}))as ImportResult.Imported).item
  val access=EvidenceAccess.primary(context,key,{key},{live.get()},{key.copyOf()},Any())
  try {
   refused{access.sources.readVerified(item.id,{false}){_,input->input.read();live.set(false);input.readBytes()}}
   assertFalse(access.current());live.set(true);access.close();assertFalse(access.current());refused{access.sources.inspect(item.id)}
  }finally{access.close();key.fill(0);root.deleteRecursively()}
 }
 /** Deletion while the fd is open must invalidate the otherwise authentic EOF result. */
 @Test fun primary_post_eof_rechecks_trashed_changed_policy_fingerprint_and_missing_payload() {
  for(change in listOf("trash","policy","fingerprint","missing")) {
   val root=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"evidence-live-${UUID.randomUUID()}")
   val context=scoped(root);val key=ByteArray(32){31};val repo=AndroidVaultRepository(context,key)
   val item=(repo.importVerified(VaultImportSource("proof.txt","text/plain",{ByteArrayInputStream("proof".toByteArray())}))as ImportResult.Imported).item
   val access=EvidenceAccess.primary(context,key,{key},{true},{key.copyOf()},Any())
   val entered=java.util.concurrent.CountDownLatch(1);val resumed=java.util.concurrent.CountDownLatch(1)
   val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
   try {
    val read=worker.submit<Boolean> {
     try { access.reader().readVerified(item.id){_,input->
      entered.countDown();check(resumed.await(5,java.util.concurrent.TimeUnit.SECONDS));input.readBytes().also{it.fill(0)}
     };false }catch(expected:IllegalStateException){true}
    }
    assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
    when(change) {
     "trash"->AndroidVaultRepository(context,key).moveToRecentlyDeleted(item.id)
     "missing"->assertTrue(File(context.filesDir,"vault/payloads/${item.id}.vault").delete())
     else->{val index=EncryptedIndexStore(File(context.filesDir,"vault"));val snapshot=index.loadSnapshot(key)
      index.saveSnapshot(snapshot.copy(items=snapshot.items.map{if(it.id!=item.id)it else if(change=="policy")it.copy(vaultOnly=true)else it.copy(plaintextSha256=ByteArray(32){7})}),key)}
    }
    resumed.countDown();assertTrue("Late $change accepted",read.get(5,java.util.concurrent.TimeUnit.SECONDS))
    assertTrue(access.store.read().evidence().isEmpty())
   }finally{resumed.countDown();worker.shutdownNow();access.close();key.fill(0);root.deleteRecursively()}
  }
 }
 @Test fun publication_gate_rejects_every_result_after_exact_retirement_boundary() {
  val root=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"evidence-publish-${UUID.randomUUID()}")
  val context=scoped(root);val key=ByteArray(32){19};val gate=Any();val live=AtomicBoolean(true)
  val access=EvidenceAccess.primary(context,key,{key},{live.get()},{key.copyOf()},gate)
  val prechecked=java.util.concurrent.CountDownLatch(1);val retired=java.util.concurrent.CountDownLatch(1)
  val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
  val retiring=worker.submit {check(prechecked.await(5,java.util.concurrent.TimeUnit.SECONDS));synchronized(gate){live.set(false);access.close()};retired.countDown()}
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
    assertTrue(access.current()) // Pause exactly in the former check/write gap.
    prechecked.countDown();assertTrue(retired.await(5,java.util.concurrent.TimeUnit.SECONDS))
    for(kind in listOf("refresh","operation","integrity","error","busy","UI bitmap/text")) {
     var emitted=false;assertFalse(kind,access.publishIfCurrent{emitted=true});assertFalse(kind,emitted)
    }
   }
   retiring.get(5,java.util.concurrent.TimeUnit.SECONDS)
  }finally{prechecked.countDown();worker.shutdownNow();access.close();key.fill(0);root.deleteRecursively()}
 }

}
