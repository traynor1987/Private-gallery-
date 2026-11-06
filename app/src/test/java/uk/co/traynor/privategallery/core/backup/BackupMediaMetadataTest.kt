package uk.co.traynor.privategallery.core.backup
import java.nio.file.Files
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.*
class BackupMediaMetadataTest {
 private fun item()=VaultItem(UUID.randomUUID().toString(),"image/jpeg","photo",100,10,ByteArray(32),ByteArray(12),VaultItemState.COMPLETE,addedAtEpochMillis=101,capturedAtEpochMillis=90,adjustedTakenAtEpochMillis=80,adjustedTimeZone="Europe/London",modifiedAtEpochMillis=102)
 @Test fun portableDatesAreCompleteAndRejectInvalidZonesOrTypes(){
  val source=item();val restored=BackupMedia.item(BackupMedia.metadata(source))
  assertEquals(source.addedAtEpochMillis,restored.addedAtEpochMillis);assertEquals(source.capturedAtEpochMillis,restored.capturedAtEpochMillis)
  assertEquals(source.adjustedTakenAtEpochMillis,restored.adjustedTakenAtEpochMillis);assertEquals(source.adjustedTimeZone,restored.adjustedTimeZone);assertEquals(source.modifiedAtEpochMillis,restored.modifiedAtEpochMillis)
  assertThrows(Exception::class.java){BackupMedia.item(BackupMedia.metadata(source).put("dates",org.json.JSONObject().put("adjusted",80).put("zone","not/a/zone")))}
 }
 @Test fun remapsCollectionsPinsCropsAndFavouriteWithoutReplacingExistingChoices(){
  val source=item();val copy=source.copy(id=UUID.randomUUID().toString());val crop=ImageEditState(NormalizedCrop(.1f,.1f,.9f,.9f),NormalizedCrop.ORIGINAL)
  val archive=VaultIndexSnapshot(listOf(source),listOf(VaultCollection("source","Album",100,coverVaultItemId=source.id,isPinned=true)),listOf(VaultCollectionMembership("source",source.id,100)),mapOf(source.id to crop),"source")
  val bytes=BackupMedia.collections(archive)
  val restored=BackupMedia.merge(VaultIndexSnapshot(emptyList()),listOf(copy),mapOf(source.id to copy.id),bytes)
  val c=restored.collections.single();assertNotEquals("source",c.id);assertEquals(copy.id,c.coverVaultItemId);assertTrue(c.isPinned);assertEquals(crop,restored.imageEdits[copy.id]);assertNull(restored.favouriteCollectionId)
  // Explicit confirmation is required even on an empty replacement device.
  val confirmed=BackupMedia.merge(VaultIndexSnapshot(emptyList()),listOf(copy),mapOf(source.id to copy.id),bytes,true)
  assertEquals(confirmed.collections.single().id,confirmed.favouriteCollectionId)
  val existing=VaultIndexSnapshot(emptyList(),listOf(VaultCollection("existing","Keep",100)),favouriteCollectionId="existing")
  assertEquals("existing",BackupMedia.merge(existing,listOf(copy),mapOf(source.id to copy.id),bytes,true).favouriteCollectionId)
 }
 @Test fun mergedCollectionCapacityFailurePreservesExistingIndex(){
  val root=Files.createTempDirectory("backup-capacity-").toFile();try{
   val store=EncryptedIndexStore(root,syncOutput={});store.saveSnapshot(VaultIndexSnapshot(emptyList()),ByteArray(32));val before=File(root,"vault-index.enc").readBytes()
   assertThrows(IllegalArgumentException::class.java){store.saveSnapshot(VaultIndexSnapshot(emptyList(),List(10001){VaultCollection("album-$it","Album",100)}),ByteArray(32))}
   assertArrayEquals(before,File(root,"vault-index.enc").readBytes())
  }finally{root.deleteRecursively()}
 }
 @Test fun encryptedIndexRoundTripPreservesImportedPresentationAndCrop(){
  val root=Files.createTempDirectory("backup-metadata-").toFile();try{
   val item=item();val snapshot=VaultIndexSnapshot(listOf(item),listOf(VaultCollection("album","Album",100,isPinned=true)),imageEdits=mapOf(item.id to ImageEditState(NormalizedCrop.ORIGINAL)))
   val store=EncryptedIndexStore(root,syncOutput={});store.saveSnapshot(snapshot,ByteArray(32));val bytes=File(root,"vault-index.enc").readBytes();val loaded=store.loadSnapshot(ByteArray(32))
   assertEquals(item.adjustedTakenAtEpochMillis,loaded.items.single().adjustedTakenAtEpochMillis);assertEquals(item.adjustedTimeZone,loaded.items.single().adjustedTimeZone);assertTrue(loaded.collections.single().isPinned);assertEquals(snapshot.imageEdits,loaded.imageEdits)
   assertArrayEquals(bytes,File(root,"vault-index.enc").readBytes())
  }finally{root.deleteRecursively()}
 }
}
