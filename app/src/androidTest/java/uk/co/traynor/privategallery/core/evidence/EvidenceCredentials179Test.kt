package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.editor.AiCredentialStore
import java.security.KeyStore

class EvidenceCredentials179Test {
 private class MemoryBackend(initial:ByteArray?="fixture-old-key".toByteArray()):EvidenceCredentials.Backend {
  private var bytes:ByteArray?=initial?.copyOf()
  var stageBarrier:()->Unit={};var readBarrier:()->Unit={};var cleanupBarrier:()->Unit={};var configuredBarrier:()->Unit={}
  var commitFailure=false;var stageFailure=false;var finishFailure=false
  var returned:ByteArray?=null;var supplied:ByteArray?=null
  var commits=0;var closedStages=0
  override fun configured():Boolean{configuredBarrier();return synchronized(this){bytes!=null}}
  @Synchronized fun snapshot()=bytes?.copyOf()
  override fun read():ByteArray? {val copy=snapshot();returned=copy;readBarrier();return copy}
  override fun stage(value:ByteArray):AiCredentialStore.PreparedChange {
   supplied=value
   stageBarrier();if(stageFailure)error("fixture pre-promotion failure")
   var pending:ByteArray?=value.copyOf()
   return object:AiCredentialStore.PreparedChange {
    override fun commit(){synchronized(this@MemoryBackend){if(commitFailure)error("fixture transition failure");bytes?.fill(0);bytes=pending;pending=null;commits++}}
    override fun finish(){cleanupBarrier();if(finishFailure)error("fixture uncertain durability")}
    override fun close(){pending?.fill(0);closedStages++}
   }
  }
  override fun removal()=object:AiCredentialStore.PreparedChange {
   override fun commit(){synchronized(this@MemoryBackend){if(commitFailure)error("fixture transition failure");bytes?.fill(0);bytes=null;commits++}}
   override fun finish(){cleanupBarrier();if(finishFailure)error("fixture uncertain durability")}
   override fun close(){}
  }
 }
 private fun failure(action:()->Unit):Throwable {
  try{action();fail("operation unexpectedly succeeded")}catch(failure:IllegalStateException){return failure}
  throw AssertionError("missing failure")
 }
 private fun awaitBlockedAt(thread:Thread,className:String,methodName:String) {
  val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
  while(System.nanoTime()<deadline) {
   if(thread.state==Thread.State.BLOCKED&&thread.stackTrace.any{it.className==className&&it.methodName==methodName})return
   Thread.sleep(5)
  }
  fail("Worker did not block at the expected production monitor: $className.$methodName (${thread.state})")
 }
 @Test fun blockedBackendWorkDoesNotHoldCapturedPrimaryOrHiddenRetirement() {
  // Putting a slow stage/read/cleanup inside guarded must make retirement time out.
  for(retirement in listOf("primary","hidden-revoke","hidden-lock"))for(operation in listOf("stage","read","cleanup","configured")) {
   val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
   val session=HiddenSession{100L};assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){19}))
   val authority=session.authority();val gate=Any();val live=AtomicBoolean(true)
   val hiddenOwner=retirement!="primary"
   val factory=if(hiddenOwner)EvidenceAuthorities.hidden(session,authority,{true})else
    EvidenceAuthorities.primary(gate,{gate},{live.get()},{ByteArray(32){17}},gate)
   val current={if(hiddenOwner)session.isAuthorityCurrent(authority)else live.get()}
   val guarded:((()->Unit)->Unit)={action->factory.acquire().use{lease->lease.commit{action()}}}
   val backend=MemoryBackend()
   val barrier={entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS));Unit}
   when(operation){"stage"->backend.stageBarrier=barrier;"read"->backend.readBarrier=barrier;"configured"->backend.configuredBarrier=barrier;else->backend.cleanupBarrier=barrier}
   val credentials=EvidenceCredentials(if(hiddenOwner)EvidenceCatalog.Owner.HIDDEN else EvidenceCatalog.Owner.PRIMARY,current,guarded,backend,{})
   val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
   try {
    val pending=pool.submit<Boolean>{runCatching{when(operation){"stage"->credentials.setup("fixture-stage-key".toCharArray());"read"->credentials.acquireForSend(credentials.owner).close();"configured"->credentials.isConfigured();else->credentials.remove()}}.isSuccess}
    assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
    val retired=pool.submit{when(retirement){"hidden-revoke"->session.revokeOperations();"hidden-lock"->session.lock();else->synchronized(gate){live.set(false)}}}
    retired.get(1,java.util.concurrent.TimeUnit.SECONDS)
    assertFalse(current());release.countDown()
    assertEquals(operation=="cleanup",pending.get(5,java.util.concurrent.TimeUnit.SECONDS))
    if(operation=="stage"){assertEquals(0,backend.commits);assertEquals(1,backend.closedStages);assertTrue(backend.supplied!!.all{it==0.toByte()})}
    if(operation=="read")assertTrue(backend.returned!!.all{it==0.toByte()})
   }finally{release.countDown();pool.shutdownNow();session.lock()}
  }
 }
 @Test fun sharedOwnerReadVersusRemoveAndReplacementRejectsStaleReadAndWipesCopies() {
  // Reads must not hold the writer mutex/state/auth gate; a concurrent mutation
  // completes before decrypt is released, and that stale read never admits a key.
  for(replace in listOf(false,true)){
   val shared=EvidenceCredentials.SharedState();val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
   val backend=MemoryBackend();backend.readBarrier={entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
   val guarded:((()->Unit)->Unit)={action->action()}
   val first=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{true},guarded,backend,{},shared)
   val second=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{true},guarded,backend,{},shared)
   val worker=java.util.concurrent.Executors.newFixedThreadPool(2);val replacement="fixture-new-key".toCharArray()
   try{
    val acquiring=worker.submit<EvidenceCredentials.Key?>{try{first.acquireForSend(EvidenceCatalog.Owner.PRIMARY)}catch(_:IllegalStateException){null}}
    assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
    val mutation=worker.submit{if(replace)second.setup(replacement)else second.remove()}
    mutation.get(1,java.util.concurrent.TimeUnit.SECONDS)
    release.countDown();assertNull(acquiring.get(5,java.util.concurrent.TimeUnit.SECONDS))
    assertTrue(backend.returned!!.all{it==0.toByte()});assertEquals(replace,second.isConfigured())
   }finally{release.countDown();worker.shutdownNow();replacement.fill('\u0000')}
  }
 }
 @Test fun queuedSameFileMutationDoesNotBlockRetirementOrResurrectStaleStage() {
  val gate=Any();val live=AtomicBoolean(true);val shared=EvidenceCredentials.SharedState();val backend=MemoryBackend()
  val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
  val stageHeld=AtomicBoolean();val queuedThread=AtomicReference<Thread>();val queuedStarted=java.util.concurrent.CountDownLatch(1)
  backend.stageBarrier={
   assertTrue(Thread.holdsLock(shared.operationGate));stageHeld.set(true);entered.countDown()
   try{check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))}finally{stageHeld.set(false)}
  }
  val guarded:((()->Unit)->Unit)={action->synchronized(gate){check(live.get());action()}}
  fun credentials()=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{live.get()},guarded,backend,{},shared)
  val pool=java.util.concurrent.Executors.newFixedThreadPool(3)
  try {
   val setup=pool.submit<Boolean>{runCatching{credentials().setup("fixture-new-key".toCharArray())}.isSuccess}
   assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
   val contender=credentials()
   val remove=pool.submit<Boolean>{queuedThread.set(Thread.currentThread());queuedStarted.countDown();runCatching{contender.remove()}.isSuccess}
   assertTrue(queuedStarted.await(5,java.util.concurrent.TimeUnit.SECONDS))
   // remove enters operationGate before any auth/state work. The first stage
   // demonstrably owns that exact shared monitor while this contender is BLOCKED.
   awaitBlockedAt(queuedThread.get(),EvidenceCredentials::class.java.name,"remove")
   assertTrue(stageHeld.get());assertEquals(1L,release.count)
   pool.submit{synchronized(gate){live.set(false)}}.get(1,java.util.concurrent.TimeUnit.SECONDS)
   assertTrue(stageHeld.get());assertEquals(1L,release.count)
   release.countDown();assertFalse(setup.get(5,java.util.concurrent.TimeUnit.SECONDS));assertFalse(remove.get(5,java.util.concurrent.TimeUnit.SECONDS))
   assertEquals(0,backend.commits);assertEquals("fixture-old-key",backend.snapshot()!!.toString(Charsets.US_ASCII));assertEquals(1,backend.closedStages)
  }finally{
   release.countDown();pool.shutdown()
   val joined=pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)
   if(!joined)pool.shutdownNow()
   assertTrue("Credential test workers did not join after stage release",joined)
  }
 }
 @Test fun realSharedKeystoreGateDoesNotBlockMainOwnerRetirementOrAdmitStaleStage() {
  assertFalse("This barrier test must run off Main",Looper.myLooper()===Looper.getMainLooper())
  val base=ApplicationProvider.getApplicationContext<Context>()
  val keyGate=AiCredentialStore::class.java.getDeclaredField("KEY_GATE").apply{isAccessible=true}.get(null)
  for(owner in EvidenceCatalog.Owner.values()) {
   val root=File(base.cacheDir,"evidence-key-gate-${UUID.randomUUID()}").apply{mkdirs()}
   val context=object:ContextWrapper(base){
    override fun getFilesDir()=File(root,"files").apply{mkdirs()}
    override fun getNoBackupFilesDir()=File(root,"private").apply{mkdirs()}
    override fun getApplicationContext():Context=this
   }
   val alias="evidence179-disposable-${UUID.randomUUID()}"
   val store=AiCredentialStore::class.java.getDeclaredConstructor(Context::class.java,String::class.java).apply{isAccessible=true}.newInstance(context,alias)
   val id="evidence-openai-179-"+owner.name.lowercase()
   val original="fixture-original-key".toByteArray();val replacement="fixture-stale-key".toCharArray()
   val vaultKey=ByteArray(32){23};val live=AtomicBoolean(true);val primaryGate=Any()
   val hidden=HiddenSession{100L};assertTrue(hidden.complete(hidden.beginAuthentication(),ByteArray(32){29}))
   val access=if(owner==EvidenceCatalog.Owner.PRIMARY)
    EvidenceAccess.primary(context,vaultKey,{vaultKey},{live.get()},{vaultKey.copyOf()},primaryGate)
   else EvidenceAccess.hidden(context,hidden){live.get()}
   val reads=AtomicInteger();val stagesPrepared=AtomicInteger();val setupThread=AtomicReference<Thread>();val setupStarted=java.util.concurrent.CountDownLatch(1)
   val gateHeld=java.util.concurrent.CountDownLatch(1);val releaseGate=java.util.concurrent.CountDownLatch(1);val stillHeld=AtomicBoolean()
   val retirementDone=java.util.concurrent.CountDownLatch(1);val retirementFailure=AtomicReference<Throwable?>()
   var retirementPosted=false
   val backend=object:EvidenceCredentials.Backend {
    override fun configured()=store.isConfigured(id)
    override fun read():ByteArray?{reads.incrementAndGet();return store.read(id)}
    override fun stage(value:ByteArray)=store.stageEvidence(id,value).also{stagesPrepared.incrementAndGet()}
    override fun removal()=store.removeEvidence(id)
   }
   // Match the production captured-access commit adapter; only alias/context are disposable.
   val credentials=EvidenceCredentials(owner,access::current,{action->access.leases.acquire().use{lease->lease.commit{access.checkCurrent();action();access.checkCurrent()}}},backend,{})
   val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
   try {
    store.save(id,original)
    val canonical=File(context.noBackupFilesDir,"ai-provider-$id.enc");val originalCiphertext=canonical.readBytes()
    val holder=pool.submit{
     synchronized(keyGate){
      stillHeld.set(true);gateHeld.countDown()
      try{check(releaseGate.await(15,java.util.concurrent.TimeUnit.SECONDS))}finally{stillHeld.set(false)}
     }
    }
    assertTrue(gateHeld.await(5,java.util.concurrent.TimeUnit.SECONDS))
    val pending=pool.submit<Boolean>{setupThread.set(Thread.currentThread());setupStarted.countDown();runCatching{credentials.setup(replacement)}.isSuccess}
    assertTrue(setupStarted.await(5,java.util.concurrent.TimeUnit.SECONDS))
    // This is the real store key() monitor acquisition, after setup's captured
    // auth/generation check; no fake stage barrier or production hook is used.
    awaitBlockedAt(setupThread.get(),AiCredentialStore::class.java.name,"key")
    assertTrue(stillHeld.get());assertEquals(1L,releaseGate.count);assertEquals(0,stagesPrepared.get())
    retirementPosted=Handler(Looper.getMainLooper()).post{
     try {
      check(Looper.myLooper()===Looper.getMainLooper())
      if(owner==EvidenceCatalog.Owner.PRIMARY)synchronized(primaryGate){live.set(false);access.close()}
      else hidden.lock()
     }catch(failure:Throwable){retirementFailure.set(failure)}finally{retirementDone.countDown()}
    }
    assertTrue("Main retirement could not be posted",retirementPosted)
    // Asynchronous post plus finally-release prevents a regressed auth hold
    // from deadlocking instrumentation through runOnMainSync or Main cleanup.
    assertTrue("Main owner retirement waited for the shared Keystore gate",retirementDone.await(1,java.util.concurrent.TimeUnit.SECONDS))
    assertNull(retirementFailure.get());assertTrue(stillHeld.get());assertEquals(1L,releaseGate.count);assertFalse(access.current())
    releaseGate.countDown();holder.get(5,java.util.concurrent.TimeUnit.SECONDS)
    assertFalse("Retired setup promoted its staged ciphertext",pending.get(10,java.util.concurrent.TimeUnit.SECONDS))
    assertEquals("Real stage must complete before stale commit denial",1,stagesPrepared.get())
    assertArrayEquals(originalCiphertext,canonical.readBytes())
    val actual=store.read(id);try{assertArrayEquals(original,actual)}finally{actual?.fill(0)}
    assertFalse(context.noBackupFilesDir.listFiles().orEmpty().any{it.name.contains(".stage-")||it.name.contains(".retired-")})
    var admitted:EvidenceCredentials.Key?=null
    try{admitted=credentials.acquireForSend(owner);fail("Retired access admitted a send key")}catch(_:IllegalStateException){}finally{admitted?.close()}
    assertEquals(0,reads.get())
   }finally {
    // Release first on EVERY failure path, including Main timeout. Main can then
    // exit even if a regression had held auth while waiting for KEY_GATE.
    releaseGate.countDown();pool.shutdown()
    val workersJoined=pool.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS)
    if(!workersJoined)pool.shutdownNow()
    val mainJoined=!retirementPosted||retirementDone.await(5,java.util.concurrent.TimeUnit.SECONDS)
    original.fill(0);replacement.fill('\u0000');vaultKey.fill(0)
    // Never delete an alias/file while a failed-to-join worker might still use it.
    if(workersJoined&&mainJoined) {
     access.close();hidden.lock();root.deleteRecursively()
     check(alias.startsWith("evidence179-disposable-"))
     KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(alias)}
    }
    assertTrue("Credential workers did not join after Keystore gate release",workersJoined)
    assertTrue("Main retirement did not join after Keystore gate release",mainJoined)
   }
  }
 }
 @Test fun transitionAndDurabilityFailuresPreserveTruthAndRevokeBeforeCleanup() {
  for(removing in listOf(false,true))for(phase in listOf("transition","durability")) {
   val backend=MemoryBackend();val shared=EvidenceCredentials.SharedState()
   val credential=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{true},{it()},backend,{},shared)
   val key=credential.acquireForSend(EvidenceCatalog.Owner.PRIMARY);var cancelled=false;key.cancelOnRevocation{cancelled=true}
   backend.commitFailure=phase=="transition";backend.finishFailure=phase=="durability"
   backend.cleanupBarrier={assertTrue(cancelled);assertFalse(key.current())}
   failure{if(removing)credential.remove()else credential.setup("fixture-new-key".toCharArray())}
   assertTrue(cancelled);assertFalse(key.current())
   val owned=EvidenceCredentials.Key::class.java.getDeclaredField("bytes").apply{isAccessible=true}.get(key) as ByteArray
   assertTrue(owned.all{it==0.toByte()})
   failure{key.authorization()}
   assertEquals(if(phase=="transition")"fixture-old-key" else if(removing)null else "fixture-new-key",backend.snapshot()?.toString(Charsets.US_ASCII))
   if(!removing){assertEquals(1,backend.closedStages);assertTrue(backend.supplied!!.all{it==0.toByte()})}
  }
  val backend=MemoryBackend();backend.stageFailure=true
  val credential=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{true},{it()},backend,{})
  failure{credential.setup("fixture-new-key".toCharArray())}
  assertEquals("fixture-old-key",backend.snapshot()!!.toString(Charsets.US_ASCII));assertTrue(backend.supplied!!.all{it==0.toByte()})
 }
 @Test fun invalidReadCopiesAreWipedAndNotificationFailureDoesNotSkipDurabilityCleanup() {
  for(value in listOf(ByteArray(0),ByteArray(8193){65},byteArrayOf(32,65),byteArrayOf(-1,65))) {
   val backend=MemoryBackend(value)
   val credential=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{true},{it()},backend,{})
   try{credential.acquireForSend(EvidenceCatalog.Owner.PRIMARY).close();fail("invalid key admitted")}catch(_:IllegalArgumentException){}
   assertTrue(backend.returned!!.all{it==0.toByte()})
  }
  val backend=MemoryBackend();var finished=false;backend.cleanupBarrier={finished=true}
  val credential=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{true},{it()},backend,{error("fixture notification failure")})
  failure{credential.remove()};assertTrue(finished);assertFalse(credential.isConfigured())
 }
 @Test fun actualCiphertextStagesPreserveOldUntilPromotionAndDetachAtomicFileCompanions() {
  val base=ApplicationProvider.getApplicationContext<Context>()
  val root=File(base.cacheDir,"evidence-stage-${UUID.randomUUID()}").apply{mkdirs()}
  val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root;override fun getApplicationContext():Context=this}
  val alias="evidence179-disposable-${UUID.randomUUID()}"
  val store=AiCredentialStore::class.java.getDeclaredConstructor(Context::class.java,String::class.java).apply{isAccessible=true}.newInstance(context,alias)
  val id="evidence-openai-179-primary";val old="fixture-old-key".toByteArray();val replacement="fixture-new-key".toByteArray()
  val canonical=File(root,"ai-provider-$id.enc");val backup=File(canonical.path+".bak");val unfinished=File(canonical.path+".new")
  try {
   store.save(id,old)
   store.stageEvidence(id,replacement).use {
    assertArrayEquals(old,store.read(id));assertTrue(root.listFiles()!!.any{it.name.contains(".stage-")})
    // Cancelling an unpromoted stage keeps the original and unrelated file.
    File(root,"unrelated.enc").writeBytes(byteArrayOf(19))
   }
   assertArrayEquals(old,store.read(id));assertFalse(root.listFiles()!!.any{it.name.contains(".stage-")});assertTrue(File(root,"unrelated.enc").isFile)
   backup.writeBytes(canonical.readBytes());unfinished.writeBytes(canonical.readBytes())
   store.stageEvidence(id,replacement).use {change->
    // Force the final rename to fail after companions were quarantined.
    val stage=change.javaClass.getDeclaredField("stage").apply{isAccessible=true}.get(change) as File
    assertTrue(stage.delete())
    try{change.commit();fail("missing stage promoted")}catch(_:java.io.IOException){}
   }
   assertArrayEquals(old,store.read(id));assertTrue(backup.isFile);assertTrue(unfinished.isFile)
   store.stageEvidence(id,replacement).use{it.commit();it.finish()}
   assertArrayEquals(replacement,store.read(id));assertFalse(backup.exists());assertFalse(unfinished.exists())
   backup.writeBytes(canonical.readBytes());unfinished.writeBytes(canonical.readBytes())
   store.removeEvidence(id).use{it.commit();assertFalse(canonical.exists());assertFalse(backup.exists());assertFalse(unfinished.exists());it.finish()}
   assertNull(store.read(id));assertFalse(store.isConfigured(id))
   try{android.util.AtomicFile(canonical).openRead().close();fail("AtomicFile resurrected removed key")}catch(_:java.io.FileNotFoundException){}
   assertFalse(root.listFiles()!!.any{it.name.contains(".retired-")||it.name.contains(".stage-")});assertTrue(File(root,"unrelated.enc").isFile)
  }finally{old.fill(0);replacement.fill(0);root.deleteRecursively();check(alias.startsWith("evidence179-disposable-"));KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(alias)}}
 }
 @Test fun ownerScopedKeystoreCredentialsAreIsolatedEncryptedRemovedAndRetired() {
  val base=ApplicationProvider.getApplicationContext<Context>()
  val root=File(base.cacheDir,"evidence-credentials-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"private").apply{mkdirs()};override fun getApplicationContext():Context=this}
  val vaultKey=ByteArray(32){37};val live=AtomicBoolean(true)
  val hidden=HiddenSession{100L};assertTrue(hidden.complete(hidden.beginAuthentication(),ByteArray(32){61}))
  val primary=EvidenceAccess.primary(context,vaultKey,{vaultKey},{live.get()},{vaultKey.copyOf()},Any())
  val secret=EvidenceAccess.hidden(context,hidden){live.get()}
  val alias="evidence179-disposable-${UUID.randomUUID()}"
  val primaryState=EvidenceCredentials.SharedState();val hiddenState=EvidenceCredentials.SharedState()
  fun credential(access:EvidenceAccess,state:EvidenceCredentials.SharedState):EvidenceCredentials {
   val store=AiCredentialStore::class.java.getDeclaredConstructor(Context::class.java,String::class.java).apply{isAccessible=true}.newInstance(context,alias)
   val id="evidence-openai-179-"+access.owner.name.lowercase()
   val backend=object:EvidenceCredentials.Backend {
    override fun configured()=store.isConfigured(id)
    override fun read()=store.read(id)
    override fun stage(value:ByteArray)=store.stageEvidence(id,value)
    override fun removal()=store.removeEvidence(id)
   }
   return EvidenceCredentials(access.owner,access::current,{action->access.leases.acquire().use{lease->lease.commit{access.checkCurrent();action();access.checkCurrent()}}},backend,{},state)
  }
  val first=credential(primary,primaryState);val second=credential(secret,hiddenState)
  val primaryKey="fixture-primary-owner-key".toCharArray();val hiddenKey="fixture-hidden-owner-key".toCharArray()
  try {
   assertFalse(first.isConfigured());assertFalse(second.isConfigured());first.setup(primaryKey);assertTrue(first.isConfigured());assertFalse(second.isConfigured());second.setup(hiddenKey)
   first.acquireForSend(EvidenceCatalog.Owner.PRIMARY).use{assertEquals("Bearer fixture-primary-owner-key",it.authorization())}
   second.acquireForSend(EvidenceCatalog.Owner.HIDDEN).use{assertEquals("Bearer fixture-hidden-owner-key",it.authorization())}
   try{first.acquireForSend(EvidenceCatalog.Owner.HIDDEN);fail("foreign key") }catch(_:IllegalArgumentException){}
   for(file in context.noBackupFilesDir.listFiles().orEmpty())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("fixture-"))
   val admitted=first.acquireForSend(EvidenceCatalog.Owner.PRIMARY);var cancelled=false;admitted.cancelOnRevocation{cancelled=true}
   credential(primary,primaryState).remove();assertTrue(cancelled);assertFalse(admitted.current());assertFalse(first.isConfigured());assertTrue(second.isConfigured());admitted.close()
   second.remove();assertFalse(second.isConfigured());live.set(false)
   try{first.setup(primaryKey);fail("retired setup")}catch(_:IllegalStateException){}
   assertFalse(context.noBackupFilesDir.listFiles().orEmpty().any{it.name.startsWith("ai-provider-evidence-")})
  }finally{primaryKey.fill('\u0000');hiddenKey.fill('\u0000');primary.close();secret.close();hidden.lock();vaultKey.fill(0);root.deleteRecursively();check(alias.startsWith("evidence179-disposable-"));KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(alias)}}
 }
}
