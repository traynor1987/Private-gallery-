package uk.co.traynor.privategallery.core.backup

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.*

class BackupSafRuntimeTest {
 @Test fun realDocumentDescriptorsRoundtripIndependentKeysInBothSpaces(){
  for(hidden in listOf(false,true)){val root=BackupFixture62.temporary("saf62");val uri=BackupDocuments62.create()
   try{BackupFixture62(File(root,"source"),hidden,ByteArray(32){14}).use{s->BackupFixture62(File(root,"replacement"),hidden,ByteArray(32){91}).use{d->
    s.insert();val provider=SafBackupProvider(s.context,uri,s.access);val key="synthetic actual SAF recovery key".toCharArray()
    val receipt=s.coordinator.export(provider,key.copyOf());assertTrue(receipt.bytes>0);assertTrue(s.coordinator.history().contains("Verified"))
    d.coordinator.preview(SafBackupProvider(d.context,uri,d.access),key.copyOf()).use{assertEquals(1,d.coordinator.restoreMedia(it))}
    assertArrayEquals("original preserved".encodeToByteArray(),d.read(d.items().single().id))
    assertTrue(BackupDocuments62.stats().getInt("opened")>=3)
   }}}finally{BackupDocuments62.delete(uri);root.deleteRecursively()}
  }
 }
 @Test fun openedReadAndWriteDescriptorsCloseWhenTheirAuthorityRetires(){
  for(hidden in listOf(false,true))for(reading in listOf(false,true)){
   val root=BackupFixture62.temporary("saf-close62");val uri=BackupDocuments62.create()
   try{BackupFixture62(root,hidden).use{f->
    val p=SafBackupProvider(f.context,uri,f.access)
    if(reading)p.write().use{it.write(byteArrayOf(1,2,3))}
    val stream:java.io.Closeable=if(reading)p.read()else p.write()
    val opened=BackupDocuments62.stats().getInt("opened")
    if(hidden)f.session.lock()else f.access.close()
    val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
    while(BackupDocuments62.stats().getInt("closed")<opened&&System.nanoTime()<until)Thread.sleep(20)
    assertEquals("Remote provider observed descriptor closure",opened,BackupDocuments62.stats().getInt("closed"));stream.close()
   }}finally{BackupDocuments62.delete(uri);root.deleteRecursively()}
  }
 }
 @Test fun providerDenialTruncationTamperAndReliablePipeErrorsNeverBecomeVerified(){
  for(fault in listOf("deny-write","fail-write","truncate","tamper","close-error")){val root=BackupFixture62.temporary("saf-fault62");val uri=BackupDocuments62.create(fault)
   try{BackupFixture62(root).use{f->val item=f.insert();val before=f.metadata().readBytes()
    assertThrows(Exception::class.java){f.coordinator.export(SafBackupProvider(f.context,uri,f.access),"fault recovery key".toCharArray())}
    assertFalse(f.coordinator.history().contains("Last successful backup"));assertArrayEquals(before,f.metadata().readBytes());assertArrayEquals("original preserved".encodeToByteArray(),f.read(item.id))
   }}finally{BackupDocuments62.delete(uri);root.deleteRecursively()}
  }
 }
 @Test fun revokedGrantAndRemovedDocumentCannotBeRead(){
  val root=BackupFixture62.temporary("saf-grant62");val uri=BackupDocuments62.create()
  try{BackupFixture62(root).use{f->val p=SafBackupProvider(f.context,uri,f.access);p.write().use{it.write(byteArrayOf(1))};BackupDocuments62.call("revoke",uri.lastPathSegment)
   assertThrows(Exception::class.java){p.read().use{it.read()}}
   BackupDocuments62.delete(uri);assertThrows(Exception::class.java){p.write().close()}
  }}finally{BackupDocuments62.delete(uri);root.deleteRecursively()}
 }
 @Test fun retirementCancelsRealProviderOpenAndPreservesOriginalsInBothSpaces(){
  for(hidden in listOf(false,true))for(mode in listOf("block-write","block-read")){val root=BackupFixture62.temporary("saf-cancel62");val uri=BackupDocuments62.create(mode);val worker=Executors.newSingleThreadExecutor()
   try{BackupFixture62(root,hidden).use{f->f.insert();val before=f.metadata().readBytes();val blocked=BackupDocuments62.stats().getInt("blocked");val cancelled=BackupDocuments62.stats().getInt("cancelled")
    val result=worker.submit<Boolean>{runCatching{f.coordinator.export(SafBackupProvider(f.context,uri,f.access),"cancel recovery key".toCharArray())}.isFailure}
    val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(BackupDocuments62.stats().getInt("blocked")==blocked&&System.nanoTime()<until)Thread.sleep(20)
    assertTrue("Operation reached actual provider boundary",BackupDocuments62.stats().getInt("blocked")>blocked)
    if(hidden)f.session.lock()else f.access.close()
    assertTrue("Retired operation completes unsuccessfully",result.get(5,TimeUnit.SECONDS));assertTrue(BackupDocuments62.stats().getInt("cancelled")>cancelled);assertArrayEquals(before,f.metadata().readBytes())
   }}finally{worker.shutdownNow();BackupDocuments62.delete(uri);root.deleteRecursively()}
  }
 }
}
