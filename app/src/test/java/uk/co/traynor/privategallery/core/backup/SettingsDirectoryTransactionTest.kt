package uk.co.traynor.privategallery.core.backup
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
class SettingsDirectoryTransactionTest {
 private fun fixture(action:(File,File)->Unit){val base=Files.createTempDirectory("settings-txn-").toFile();try{val root=File(base,"home2").apply{mkdir()};File(root,"prefs.enc").writeText("old ciphertext");val next=File(base,"home2-backup-next-test").apply{mkdir()};File(next,"prefs.enc").writeText("new ciphertext");action(root,next)}finally{base.deleteRecursively()}}
 @Test fun failureDuringParentSyncRestoresOriginalBytes(){fixture{root,next->
  val txn=SettingsDirectoryTransaction(root){if(it==root.parentFile&&File(root.parentFile,"home2-backup-old").exists())throw IOException("Parent sync failed")}
  assertThrows(IOException::class.java){txn.install(next,{true}){it()}}
  assertEquals("old ciphertext",File(root,"prefs.enc").readText())
 }}
 @Test fun retirementAfterPromotionRollsBack(){fixture{root,next->var live=true;var promoted=false
  val txn=SettingsDirectoryTransaction(root){if(it==root.parentFile&&File(root.parentFile,"home2-backup-old").exists()){live=false;promoted=File(root,"prefs.enc").readText()=="new ciphertext"}}
  assertThrows(IllegalStateException::class.java){txn.install(next,{live}){it()}}
  assertTrue(promoted);assertEquals("old ciphertext",File(root,"prefs.enc").readText())
 }}
 @Test fun failureDuringCommitReceiptSyncRollsBackAndRemovesReceipt(){fixture{root,next->var parentSyncs=0
  val txn=SettingsDirectoryTransaction(root){if(it==root.parentFile&&++parentSyncs==2)throw IOException("Receipt durability failed")}
  assertThrows(IOException::class.java){txn.install(next,{true}){it()}}
  assertEquals("old ciphertext",File(root,"prefs.enc").readText());assertFalse(File(root.parentFile,"home2-backup-commit").exists())
 }}
 @Test fun crashWithBothDirectoriesWithoutReceiptChoosesOld(){fixture{root,next->
  root.renameTo(File(root.parentFile,"home2-backup-old"));next.renameTo(root)
  SettingsDirectoryTransaction(root).recover();assertEquals("old ciphertext",File(root,"prefs.enc").readText())
 }}
 @Test fun successfulDurableReceiptKeepsNewButInvalidReceiptFallsBackToOld(){
  for(corrupt in listOf(false,true))fixture{root,next->
   SettingsDirectoryTransaction(root).install(next,{true}){it()}
   assertTrue(File(root.parentFile,"home2-backup-old").exists())
   if(corrupt)File(root.parentFile,"home2-backup-commit").writeText("incomplete")
   SettingsDirectoryTransaction(root).recover()
   assertEquals(if(corrupt)"old ciphertext"else "new ciphertext",File(root,"prefs.enc").readText())
  }
 }
}
