package uk.co.traynor.privategallery.core.media
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import uk.co.traynor.privategallery.core.vault.*
class VaultStorageStatsTest {
 @Test fun cameraRecoveryBytesAreScopedAndNeverClassifiedDisposable() {
  val root=Files.createTempDirectory("camera-storage174").toFile()
  try {
   val vault=File(root,"vault").apply {mkdirs()};val camera=File(root,"camera-primary-v1").apply {mkdirs()}
   File(camera,"options.enc").writeBytes(ByteArray(40))
   File(camera,"capture-id").apply {mkdirs()}.resolve("media.enc").writeBytes(ByteArray(300))
   File(root,"camera-hidden-v1").apply {mkdirs()}.resolve("secret.enc").writeBytes(ByteArray(999))
   val result=VaultStorageStats.calculate(vault,emptyList(),camera){error("No originals")}
   assertEquals(340L,result.totalBytes);assertEquals(300L,result.temporaryBytes);assertEquals(40L,result.otherBytes)
  }finally{root.deleteRecursively()}
 }
 @Test fun onlyScopedFilesCountAndDuplicatesRequireSameDigestAndSize() {
  val root=Files.createTempDirectory("storage174").toFile()
  try {
   val primary=File(root,"primary").apply {mkdirs()}
   File(root,"hidden").apply {mkdirs()}.resolve("secret.enc").writeBytes(ByteArray(999))
   File(primary,"photo.vault").writeBytes(ByteArray(128));File(primary,"video.vault").writeBytes(ByteArray(256));File(primary,"state.enc").writeBytes(ByteArray(16))
   val a=VaultItem("a","image/jpeg","a.jpg",1,100,ByteArray(32){1},byteArrayOf(),VaultItemState.COMPLETE)
   val b=a.copy(id="b",displayName="b.jpg")
   val c=a.copy(id="c",plaintextSize=101)
   val result=VaultStorageStats.calculate(primary,listOf(a,b,c),payload={item->File(primary,if(item.id=="c")"video.vault" else "photo.vault")})
   assertEquals(400L,result.totalBytes)
   assertEquals(listOf(listOf("a","b")),result.duplicates.map {group->group.map {it.id}})
   assertEquals(listOf("c","a","b"),result.largest.map {it.id})
  } finally {root.deleteRecursively()}
 }
}
