package uk.co.traynor.privategallery.core.vault
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class VaultPresentationStoreTest {
 @Test fun adjustedDatePreservesOriginalAndCanBeRestoredAfterReopen() {
  val root=root();val key=ByteArray(32){9}
  try {
   val item=VaultItem("date","video/mp4","clip.mp4",1000,123,byteArrayOf(1,2),byteArrayOf(3),VaultItemState.COMPLETE)
   val store=VaultPresentationStore(root,"hidden",key)
   store.record(item.id,2000,100)
   store.adjustDate(item.id,3000,"Europe/London")
   val reopened=VaultPresentationStore(root,"hidden",key).decorate(listOf(item)).single()
   assertEquals(100L,reopened.capturedAtEpochMillis)
   assertEquals(3000L,reopened.adjustedTakenAtEpochMillis)
   assertEquals("Europe/London",reopened.adjustedTimeZone)
   assertArrayEquals(item.plaintextSha256,reopened.plaintextSha256)
   store.adjustDate(item.id,null,null)
   assertNull(store.decorate(listOf(item)).single().adjustedTakenAtEpochMillis)
   assertEquals(100L,store.decorate(listOf(item)).single().capturedAtEpochMillis)
  } finally {key.fill(0);root.deleteRecursively()}
 }
 @Test fun missingOriginalDateAndRevokedAdjustmentAreSafe() {
  val root=root();val key=ByteArray(32){8}
  try {
   val store=VaultPresentationStore(root,"hidden",key);store.sort("LARGEST")
   store.adjustDate("missing-original",4000,"UTC")
   val before=dataFile(root).readBytes()
   assertTrue(runCatching {VaultPresentationStore(root,"hidden",key,{throw SecurityException("revoked")}).adjustDate("missing-original",5000,"UTC")}.isFailure)
   assertArrayEquals(before,dataFile(root).readBytes())
   assertTrue(runCatching {store.adjustDate("bad",-1,"UTC")}.isFailure)
   assertTrue(runCatching {store.adjustDate("bad",5000,"Not/AZone")}.isFailure)
  } finally {key.fill(0);root.deleteRecursively()}
 }

 @Test fun historicalRecordsBeyondTwentyThousandRemainReadableAndPruneOnNewPublication() {
  val root=root();val key=ByteArray(32){5}
  try {
   val dates=org.json.JSONObject()
   repeat(20_001){dates.put("old-$it",org.json.JSONObject().put("added",2000).put("captured",100))}
   val plain=org.json.JSONObject().put("version",1).put("dates",dates).put("sort","NAME").toString().toByteArray()
   val sealed=java.io.ByteArrayOutputStream()
   try {
    val header=uk.co.traynor.privategallery.core.crypto.VaultCipher.encrypt(java.io.ByteArrayInputStream(plain),sealed,key,"private-gallery/presentation/v1/primary".toByteArray())
    dataFile(root).writeBytes(header.nonce+sealed.toByteArray())
   } finally {plain.fill(0)}
   val store=VaultPresentationStore(root,"primary",key)
   assertEquals("NAME",store.sort())
   store.record("new",3000,200,setOf("old-0"))
   val items=listOf("old-0","old-1","new").map {VaultItem(it,"image/jpeg",it,1000,0,byteArrayOf(),byteArrayOf(),VaultItemState.COMPLETE)}
   val got=store.decorate(items)
   assertEquals(2000L,got[0].addedAtEpochMillis);assertNull(got[1].addedAtEpochMillis);assertEquals(3000L,got[2].addedAtEpochMillis)
   assertEquals("NAME",store.sort())
  } finally {key.fill(0);root.deleteRecursively()}
 }
 private fun dataFile(root:File)=File(root,"presentation-v1/state.enc").also {it.parentFile!!.mkdirs()}
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
   assertFalse(dataFile(root).readBytes().toString(Charsets.ISO_8859_1).contains("PRIVATE-ITEM-SENTINEL"))
  } finally {key.fill(0);root.deleteRecursively()}
 }
 @Test fun domainsRemainIndependentAndCorruptionNeverReplacesExistingFile() {
  val root=root();val primary=File(root,"primary");val hidden=File(root,"hidden").apply {mkdirs()};val key=ByteArray(32){7}
  try {
   VaultPresentationStore(primary,"primary",key).sort("NAME")
   val store=VaultPresentationStore(hidden,"hidden",key);assertNull(store.sort())
   dataFile(primary).copyTo(dataFile(hidden))
   val before=dataFile(hidden).readBytes();assertNull(store.sort())
   assertTrue(runCatching {store.sort("NAME_DESC")}.isFailure)
   assertArrayEquals(before,dataFile(hidden).readBytes())
  } finally {key.fill(0);root.deleteRecursively()}
 }
 @Test fun revokedPublicationLeavesPreviousEncryptedMetadataUntouched() {
  val root=root();val key=ByteArray(32){4}
  try {
   VaultPresentationStore(root,"hidden",key).sort("OLDEST")
   val before=dataFile(root).readBytes()
   assertTrue(runCatching {VaultPresentationStore(root,"hidden",key,{throw SecurityException("revoked")}).sort("NAME")}.isFailure)
   assertArrayEquals(before,dataFile(root).readBytes())
   assertEquals(1,root.walkTopDown().count {it.isFile})
  } finally {key.fill(0);root.deleteRecursively()}
 }
}
