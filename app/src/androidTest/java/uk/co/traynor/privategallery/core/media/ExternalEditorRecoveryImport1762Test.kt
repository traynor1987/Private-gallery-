package uk.co.traynor.privategallery.core.media

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.*
import java.io.*

class ExternalEditorRecoveryImport1762Test {
 @Test fun primaryCommittedSaveNewMarkerReusesVerifiedCopyAfterReopening()=checkPrimary(false)
 @Test fun primaryCommittedReplacementMarkerReusesVerifiedCopyAfterReopening()=checkPrimary(true)
 private fun checkPrimary(replace:Boolean) {
  val base=ApplicationProvider.getApplicationContext<Context>();val root=File(base.cacheDir,"editor-fixture-${java.util.UUID.randomUUID()}").apply {mkdirs()}
  val ctx=object:ContextWrapper(base) {
   override fun getApplicationContext():Context=this
   override fun getFilesDir()=File(root,"files").apply {mkdirs()}
   override fun getNoBackupFilesDir()=File(root,"no-backup").apply {mkdirs()}
   override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
  }
  val key=ByteArray(32){37};val image=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
  val bytes=try {ByteArrayOutputStream().use {out ->check(image.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}}finally{image.recycle()}
  try {
   val repo=AndroidVaultRepository(ctx,key)
   val original=(repo.importVerified(VaultImportSource("test.png","image/png",{ByteArrayInputStream(bytes)})) as ImportResult.Imported).item
   val operation="external-edit:v1|${java.util.UUID.randomUUID()}"
   val copy=ExternalEditImport.primary(ctx,key,original.id,bytes,replace,{true},{it()},"$operation|$replace")
   val reopened=AndroidVaultRepository(ctx,key)
   val marked=reopened.items().single {it.sourceUri=="$operation|$replace"}
   reopened.verifyTransferDestination(marked){false};assertEquals(copy.id,marked.id)
   assertEquals(replace,reopened.recentlyDeleted().any {it.id==original.id})
   val retry=ExternalEditImport.primary(ctx,key,original.id,bytes,!replace,{true},{it()},"$operation|${!replace}")
   assertEquals(copy.id,retry.id);assertEquals(1,reopened.items().count {it.sourceUri?.startsWith("$operation|")==true})
   assertEquals(replace,reopened.recentlyDeleted().any {it.id==original.id})
  } finally {key.fill(0);bytes.fill(0);root.deleteRecursively()}
 }
}
