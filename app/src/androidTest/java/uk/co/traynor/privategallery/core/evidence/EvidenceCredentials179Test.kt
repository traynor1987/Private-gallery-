package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.editor.AiCredentialStore
import java.security.KeyStore

class EvidenceCredentials179Test {
 @Test fun sharedOwnerAcquireVersusRemoveAndReplacementUsesOneGenerationAndWipesCopies() {
  for(replace in listOf(false,true)){
   val shared=EvidenceCredentials.SharedState();val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
   val mutating=java.util.concurrent.CountDownLatch(1);val mutated=java.util.concurrent.CountDownLatch(1)
   var returned:ByteArray?=null;var bytes:ByteArray?="fixture-old-key".toByteArray()
   val backend=object:EvidenceCredentials.Backend {
    override fun configured()=bytes!=null
    override fun read():ByteArray? {val copy=bytes?.copyOf();returned=copy;entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS));return copy}
    override fun save(value:ByteArray){bytes?.fill(0);bytes=value.copyOf()}
    override fun clear(){bytes?.fill(0);bytes=null}
   }
   val guarded:((()->Unit)->Unit)={action->action()}
   val first=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{true},guarded,backend,{},shared)
   val second=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{true},guarded,backend,{},shared)
   val worker=java.util.concurrent.Executors.newFixedThreadPool(2);val replacement="fixture-new-key".toCharArray()
   try{
    val acquiring=worker.submit<EvidenceCredentials.Key?>{try{first.acquireForSend(EvidenceCatalog.Owner.PRIMARY)}catch(_:IllegalStateException){null}}
    assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
    val mutation=worker.submit{mutating.countDown();try{if(replace)second.setup(replacement)else second.remove()}finally{mutated.countDown()}}
    assertTrue(mutating.await(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1L,mutated.count)
    release.countDown();val admitted=acquiring.get(5,java.util.concurrent.TimeUnit.SECONDS);mutation.get(5,java.util.concurrent.TimeUnit.SECONDS)
    assertTrue(returned!!.all{it==0.toByte()})
    if(admitted!=null){assertFalse(admitted.current());val owned=EvidenceCredentials.Key::class.java.getDeclaredField("bytes").apply{isAccessible=true}.get(admitted) as ByteArray;assertTrue(owned.all{it==0.toByte()});admitted.close()}
    assertEquals(replace,second.isConfigured())
   }finally{release.countDown();worker.shutdownNow();replacement.fill('\u0000');returned?.fill(0);bytes?.fill(0)}
  }
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
    override fun save(value:ByteArray)=store.save(id,value)
    override fun clear()=store.clear(id)
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
