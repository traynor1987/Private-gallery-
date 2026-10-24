package uk.co.traynor.privategallery.core.media
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File
class DisposablePreview174Test {
 @Test fun clearingGeneratedPreviewsNeverDeletesOtherFilesOrPayloads() {
  val root=Files.createTempDirectory("preview174").toFile()
  try {
   val previews=File(root,"previews").apply {mkdirs()}
   val disposable=File(previews,"${"a".repeat(64)}-${"b".repeat(64)}.enc").apply {writeBytes(byteArrayOf(1,2))}
   val unknown=File(previews,"original.enc").apply {writeBytes(byteArrayOf(3))}
   val payload=File(root,"payloads").apply {mkdirs()}.resolve("photo.vault").apply {writeBytes(byteArrayOf(4))}
   assertEquals(2L,EncryptedPreviewCache(previews).clearDisposable())
   assertFalse(disposable.exists());assertTrue(unknown.exists());assertTrue(payload.exists())
  }finally{root.deleteRecursively()}
 }
}
