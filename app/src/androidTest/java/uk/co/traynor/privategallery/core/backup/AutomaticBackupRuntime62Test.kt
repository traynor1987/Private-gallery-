package uk.co.traynor.privategallery.core.backup
import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.*

class AutomaticBackupRuntime62Test {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val key="synthetic automatic recovery key 0123456789".toCharArray()
 private fun due(){context.getSharedPreferences("nova-backup-due",0).edit().putLong("configured",System.currentTimeMillis()-86400001L).putLong("due",System.currentTimeMillis()).putLong("attempt",0).commit()}
 private fun tree():Uri=checkNotNull(BackupDocuments62.call("tree").getParcelable("uri",Uri::class.java))
 private fun fixture(block:(BackupFixture62)->Unit){
  val root=BackupFixture62.temporary("automatic62");val prefs=context.getSharedPreferences("nova-backup-due",0);val old=prefs.all.toMap();val scheduler=context.getSystemService(android.app.job.JobScheduler::class.java);val job=scheduler.getPendingJob(171162)
  try{BackupFixture62(root).use(block)}finally{root.deleteRecursively();scheduler.cancel(171162);if(job!=null)scheduler.schedule(job);prefs.edit().clear().also{e->old.forEach{(k,v)->when(v){is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Boolean->e.putBoolean(k,v);is String->e.putString(k,v)}}}.commit()}
 }
 @Test fun encryptedOptInRunsOnEligibleForegroundWithoutManualBackupAndVerifiesUniqueFiles()=fixture{f->
  val destination=tree();f.insert();AutomaticBackupSettings.arm(f.context,f.coordinator.space,destination,key.copyOf(),1,false,false)
  assertFalse(f.coordinator.space.backupValues().keys.any{it.contains(AutomaticBackupSettings.NAME)})
  File(f.access.root,"home2").walkTopDown().filter{it.isFile}.forEach{val raw=it.readBytes().toString(Charsets.ISO_8859_1);assertFalse(raw.contains(key.concatToString()));assertFalse(raw.contains(destination.toString()))}
  uk.co.traynor.privategallery.core.home2.SpaceContext(f.context,f.access).use{space->checkNotNull(AutomaticBackupSettings.read(space)).use{assertArrayEquals(key,it.recovery);assertEquals(destination,it.tree)}}
  val widgets=f.coordinator.space.getSharedPreferences("nova_widgets_v1",0)
  check(widgets.edit().putBoolean("clock:primary",true).commit());val portable=f.coordinator.space.backupValues()
  try {
   check(widgets.edit().putBoolean("clock:primary",false).commit());f.coordinator.space.restoreBackupValues(portable)
   assertTrue(widgets.getBoolean("clock:primary",false))
   checkNotNull(AutomaticBackupSettings.read(f.coordinator.space)).use{assertArrayEquals(key,it.recovery);assertEquals(destination,it.tree)}
   val before=File(f.access.root,"home2").listFiles()!!.associate{it.name to it.readBytes().toList()}
   assertThrows(Exception::class.java){f.coordinator.space.restoreBackupValues(mapOf("preferences:${AutomaticBackupSettings.NAME}" to byteArrayOf(1)))}
   assertEquals(before,File(f.access.root,"home2").listFiles()!!.associate{it.name to it.readBytes().toList()})
  } finally {portable.values.forEach{it.fill(0)}}
  AutomaticBackupController(f.context,f.access).use{runner->
   due();assertFalse("Locked/unavailable foreground cannot run",runner.runIfEligible());assertFalse(f.coordinator.history().contains("Last successful backup"))
   runner.resume();assertTrue(runner.runIfEligible());assertTrue(f.access.current());assertTrue(f.coordinator.history().contains("Last successful backup"));assertFalse("Not due again after success",runner.runIfEligible())
   val first=checkNotNull(BackupDocuments62.call("latest").getString("id"));val firstUri=android.provider.DocumentsContract.buildDocumentUri(BackupDocumentsFixtureProvider.AUTHORITY,first)
   f.coordinator.preview(SafBackupProvider(f.context,firstUri,f.access),key.copyOf()).use{assertEquals(1,it.media)}
   due();assertTrue(runner.runIfEligible());val second=checkNotNull(BackupDocuments62.call("latest").getString("id"));assertNotEquals(first,second)
   BackupDocuments62.delete(firstUri);BackupDocuments62.delete(android.provider.DocumentsContract.buildDocumentUri(BackupDocumentsFixtureProvider.AUTHORITY,second))
   AutomaticBackupSettings.disable(f.context,f.coordinator.space);due();assertFalse(runner.runIfEligible());assertFalse(AutomaticBackupSettings.armed(f.coordinator.space))
  }
 }
 @Test fun failedScheduledDestinationWaitsForBoundedRetryAndNeverClaimsVerified()=fixture{f->
  AutomaticBackupSettings.arm(f.context,f.coordinator.space,tree(),key.copyOf(),1,false,false);due();BackupDocuments62.call("next-mode","fail-write")
  var first:String?=null;var second:String?=null
  try{AutomaticBackupController(f.context,f.access).use{runner->runner.resume();assertFalse(runner.runIfEligible());first=BackupDocuments62.call("latest").getString("id");assertNotNull(first);assertFalse(f.coordinator.history().contains("Last successful backup"));assertTrue(f.coordinator.history().contains("Failed"))
   repeat(3){assertFalse(runner.runIfEligible());assertEquals(first,BackupDocuments62.call("latest").getString("id"))}
   context.getSharedPreferences("nova-backup-due",0).edit().putLong("attempt",System.currentTimeMillis()-900001L).commit()
   assertTrue(runner.runIfEligible());second=BackupDocuments62.call("latest").getString("id");assertNotEquals(first,second);assertTrue(f.coordinator.history().contains("Last successful backup"))
  }}finally{BackupDocuments62.call("next-mode","normal");listOfNotNull(first,second).forEach{BackupDocuments62.delete(android.provider.DocumentsContract.buildDocumentUri(BackupDocumentsFixtureProvider.AUTHORITY,it))}}
 }
 @Test fun pauseCancelsAutomaticRealProviderIoAndNoHiddenAuthorityCanArmOrRun()=fixture{f->
  AutomaticBackupSettings.arm(f.context,f.coordinator.space,tree(),key.copyOf(),1,false,false);due()
  BackupDocuments62.call("next-mode","block-write");val before=BackupDocuments62.stats().getInt("blocked");val cancelled=BackupDocuments62.stats().getInt("cancelled");val worker=Executors.newSingleThreadExecutor()
  try{AutomaticBackupController(f.context,f.access).use{runner->runner.resume();val result=worker.submit<Boolean>{runner.runIfEligible()};val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
   while(BackupDocuments62.stats().getInt("blocked")==before&&System.nanoTime()<until)Thread.sleep(20)
   assertTrue(BackupDocuments62.stats().getInt("blocked")>before);runner.pause();assertFalse(result.get(5,TimeUnit.SECONDS));assertTrue(BackupDocuments62.stats().getInt("cancelled")>cancelled);assertFalse(f.coordinator.history().contains("Last successful backup"));assertTrue(f.access.current())
  }}finally{worker.shutdownNow();BackupDocuments62.call("next-mode","normal");BackupDocuments62.call("latest").getString("id")?.let{BackupDocuments62.delete(android.provider.DocumentsContract.buildDocumentUri(BackupDocumentsFixtureProvider.AUTHORITY,it))}}
  val hiddenRoot=BackupFixture62.temporary("automatic-hidden62")
  try{BackupFixture62(hiddenRoot,true).use{hidden->assertThrows(Exception::class.java){AutomaticBackupSettings.arm(hidden.context,hidden.coordinator.space,tree(),key.copyOf(),1,false,false)};AutomaticBackupController(hidden.context,hidden.access).use{it.resume();assertFalse(it.runIfEligible())};assertTrue(hidden.coordinator.space.getSharedPreferences(AutomaticBackupSettings.NAME,0).all.isEmpty())}}finally{hiddenRoot.deleteRecursively()}
 }
 @Test fun originRetirementAtFinalHistoryCommitCannotPublishVerified()=fixture{f->
  f.insert("authority-boundary original");AutomaticBackupSettings.arm(f.context,f.coordinator.space,tree(),key.copyOf(),1,false,false);due()
  val ready=CountDownLatch(1);val release=CountDownLatch(1);val commits=java.util.concurrent.atomic.AtomicInteger()
  // Decorate the real origin lease only to stop immediately before its final commit boundary.
  // Media, encryption, actual SAF export/readback and history preferences remain production code.
  val factory=uk.co.traynor.privategallery.core.evidence.EvidenceEncryptedStore.LeaseFactory {
   val original=f.access.leases.acquire()
   object:uk.co.traynor.privategallery.core.evidence.EvidenceEncryptedStore.KeyLease {
    override fun key()=original.key()
    override fun current()=original.current()
    override fun close()=original.close()
    override fun commit(action:uk.co.traynor.privategallery.core.evidence.EvidenceEncryptedStore.CommitAction){
     if(commits.incrementAndGet()==2){ready.countDown();check(release.await(15,TimeUnit.SECONDS))}
     original.commit(action)
    }
   }
  }
  val guard:()->Boolean={f.access.current()};val boundary:((()->Unit)->Unit)={action->synchronized(f.primaryGate){action()}}
  val sources=uk.co.traynor.privategallery.core.evidence.EvidenceAccess::class.java.getDeclaredField("sourceFactory").apply{isAccessible=true}.get(f.access)
  val constructor=uk.co.traynor.privategallery.core.evidence.EvidenceAccess::class.java.declaredConstructors.single{it.parameterTypes.size==7}.apply{isAccessible=true}
  val origin=constructor.newInstance(f.access.owner,f.access.root,factory,guard,boundary,sources,null) as uk.co.traynor.privategallery.core.evidence.EvidenceAccess
  val worker=Executors.newSingleThreadExecutor()
  try{AutomaticBackupController(f.context,origin).use{runner->
   runner.resume();val result=worker.submit<Boolean>{runner.runIfEligible()};assertTrue("Reached actual Verified-history publication",ready.await(15,TimeUnit.SECONDS));assertEquals(2,commits.get())
   val before=File(f.access.root,"home2").listFiles()!!.filter{it.name.startsWith("prefs-")}.associate{it.name to it.readBytes().toList()}
   origin.close();release.countDown();assertFalse(result.get(5,TimeUnit.SECONDS))
   assertEquals(before,File(f.access.root,"home2").listFiles()!!.filter{it.name.startsWith("prefs-")}.associate{it.name to it.readBytes().toList()})
   assertFalse(File(f.access.root,"home2").listFiles()!!.any{it.name.startsWith("space-")})
   val history=f.coordinator.space.getSharedPreferences("nova_backup_history_v1",0)
   assertNull(history.getString("lastBackup",null));assertEquals("Unverified",org.json.JSONArray(history.getString("events","[]")).getJSONObject(0).getString("status"))
   assertFalse(f.coordinator.history().contains("Last successful backup"));assertTrue(f.access.current());assertEquals("authority-boundary original",f.read(f.items().single().id).decodeToString())
  }}finally{release.countDown();origin.close();worker.shutdownNow();BackupDocuments62.call("latest").getString("id")?.let{BackupDocuments62.delete(android.provider.DocumentsContract.buildDocumentUri(BackupDocumentsFixtureProvider.AUTHORITY,it))}}
 }

}
