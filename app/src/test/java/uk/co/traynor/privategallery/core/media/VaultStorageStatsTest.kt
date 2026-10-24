package uk.co.traynor.privategallery.core.media
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import uk.co.traynor.privategallery.core.vault.*
class VaultStorageStatsTest {
 @Test fun onlyScopedFilesCountAndDuplicatesRequireSameDigestAndSize() {
  val root=Files.createTempDirectory("storage174").toFile()
  try {
   val primary=File(root,"primary").apply {mkdirs()}
   File(root,"hidden").apply {mkdirs()}.resolve("secret.enc").writeBytes(ByteArray(999))
   File(primary,"photo.vault").writeBytes(ByteArray(128));File(primary,"video.vault").writeBytes(ByteArray(256));File(primary,"state.enc").writeBytes(ByteArray(16))
   val a=VaultItem("a","image/jpeg","a.jpg",1,100,ByteArray(32){1},byteArrayOf(),VaultItemState.COMPLETE)
   val b=a.copy(id="b",displayName="b.jpg")
   val c=a.copy(id="c",plaintextSize=101)
   val result=VaultStorageStats.calculate(primary,listOf(a,b,c),{item->File(primary,if(item.id=="c")"video.vault" else "photo.vault")})
   assertEquals(400L,result.totalBytes)
   assertEquals(listOf(listOf("a","b")),result.duplicates.map {group->group.map {it.id}})
   assertEquals(listOf("c","a","b"),result.largest.map {it.id})
  } finally {root.deleteRecursively()}
 }
}
