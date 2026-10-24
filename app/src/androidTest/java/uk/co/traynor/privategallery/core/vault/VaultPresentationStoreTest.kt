package uk.co.traynor.privategallery.core.vault
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class VaultPresentationStoreTest {
 private fun root()=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"presentation-${UUID.randomUUID()}").apply {mkdirs()}
 @Test fun oldCaptureImportedTodayHasIndependentDatesAndEncryptedPreference() {
  val root=root();val key=ByteArray(32){3}
  try {
   val store=VaultPresentationStore(root,"primary",key)
   store.record("PRIVATE-ITEM-SENTINEL",2000,100);store.sort("NAME")
   val item=VaultItem("PRIVATE-ITEM-SENTINEL","image/jpeg","old.jpg",1000,0,byteArrayOf(),byteArrayOf(),VaultItemState.COMPLETE)
   val got=VaultPresentationStore(root,"primary",key).decorate(listOf(item)).single()
   assertEquals(2000L,got.addedAtEpochMillis);assertEquals(100L,got.capturedAtEpochMillis)
   assertEquals(1000L,got.importedAtEpochMillis);assertEquals("NAME",store.sort())
   assertFalse(File(root,"presentation-v1.enc").readBytes().toString(Charsets.ISO_8859_1).contains("PRIVATE-ITEM-SENTINEL"))
  } finally {key.fill(0);root.deleteRecursively()}
 }
 @Test fun domainsRemainIndependentAndCorruptionNeverReplacesExistingFile() {
  val root=root();val primary=File(root,"primary");val hidden=File(root,"hidden").apply {mkdirs()};val key=ByteArray(32){7}
  try {
   VaultPresentationStore(primary,"primary",key).sort("NAME")
   val store=VaultPresentationStore(hidden,"hidden",key);assertNull(store.sort())
   File(primary,"presentation-v1.enc").copyTo(File(hidden,"presentation-v1.enc"))
   val before=File(hidden,"presentation-v1.enc").readBytes();assertNull(store.sort())
   assertTrue(runCatching {store.sort("NAME_DESC")}.isFailure)
   assertArrayEquals(before,File(hidden,"presentation-v1.enc").readBytes())
  } finally {key.fill(0);root.deleteRecursively()}
 }
 @Test fun revokedPublicationLeavesPreviousEncryptedMetadataUntouched() {
  val root=root();val key=ByteArray(32){4}
  try {
   VaultPresentationStore(root,"hidden",key).sort("OLDEST")
   val before=File(root,"presentation-v1.enc").readBytes()
   assertTrue(runCatching {VaultPresentationStore(root,"hidden",key,{throw SecurityException("revoked")}).sort("NAME")}.isFailure)
   assertArrayEquals(before,File(root,"presentation-v1.enc").readBytes())
   assertEquals(1,root.listFiles()!!.size)
  } finally {key.fill(0);root.deleteRecursively()}
 }
}
