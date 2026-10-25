package uk.co.traynor.privategallery.core.media
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class ExternalEditing176Test {
 @Test fun handoffProviderIsReadOnlyExpiresAndNeverPublishesVaultPaths() {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val root=File(ctx.cacheDir,"external-edit")
  val file=ExternalEditLease.create(root,byteArrayOf(1,2,3),System.currentTimeMillis())
  val uri=FileProvider.getUriForFile(ctx,"${ctx.packageName}.fileprovider",file)
  try {
   assertEquals("external_edit_copy",uri.pathSegments.first())
   assertArrayEquals(byteArrayOf(1,2,3),ctx.contentResolver.openInputStream(uri)!!.use {it.readBytes()})
   assertTrue(runCatching {ctx.contentResolver.openOutputStream(uri)!!.use {it.write(0)}}.isFailure)
   assertTrue(file.setLastModified(System.currentTimeMillis()-ExternalEditLease.LIFETIME-1))
   assertTrue(runCatching {ctx.contentResolver.openInputStream(uri)!!.use {it.readBytes()}}.isFailure)
   assertFalse(file.exists())
  } finally {file.delete()}
 }
 @Test fun leaseCleanupDoesNotDeleteUnrelatedFilesOrFreshCopy() {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val dir=File(ctx.cacheDir,"external-edit-fixture-${java.util.UUID.randomUUID()}").apply {mkdirs()}
  try {
   val fresh=ExternalEditLease.create(dir,byteArrayOf(1),System.currentTimeMillis())
   val unrelated=File(dir,"owner-file.png").apply {writeBytes(byteArrayOf(2))}
   ExternalEditLease.cleanup(dir,System.currentTimeMillis());assertTrue(fresh.exists());assertTrue(unrelated.exists())
  } finally {dir.deleteRecursively()}
 }
}
