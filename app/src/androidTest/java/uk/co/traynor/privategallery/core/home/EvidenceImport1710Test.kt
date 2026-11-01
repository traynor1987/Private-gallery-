package uk.co.traynor.privategallery.core.home

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.vault.*

class EvidenceImport1710Test {
 @Test fun offlineDraftIsNotRegisteredUntilExplicitSaveAndOriginalIsPreserved()=runBlocking {
  val base=InstrumentationRegistry.getInstrumentation().targetContext
  val root=File(base.cacheDir,"import1710-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getApplicationContext():Context=this}
  val key=ByteArray(32){39};val identity=Any()
  val access=EvidenceAccess.primary(context,identity,{identity},{true},{key.copyOf()},Any())
  val repository=AndroidVaultRepository(context,key)
  val bytes="Synthetic offline evidence 2026-11-01T10:00:00Z".toByteArray()
  val imported=(repository.importVerified(VaultImportSource("review1710.txt","text/plain",{bytes.inputStream()})) as ImportResult.Imported).item
  val controller=EvidenceController(context,access)
  try{
   val draft=controller.prepareImport(listOf(imported.id))
   assertEquals(1,draft.evidence().size);assertTrue(access.store.read().evidence().isEmpty())
   assertEquals(imported.id,draft.evidence().single().sourceId())
   assertTrue(draft.evidence().single().ocr().single().text().contains("Synthetic offline evidence"))
   assertArrayEquals(imported.plaintextSha256,repository.items().single().plaintextSha256)
   controller.saveReviewedImport(draft)
   val deadline=android.os.SystemClock.elapsedRealtime()+10000
   while(access.store.read().evidence().isEmpty()&&android.os.SystemClock.elapsedRealtime()<deadline)android.os.SystemClock.sleep(25)
   assertEquals(1,access.store.read().evidence().size)
   assertArrayEquals(imported.plaintextSha256,repository.items().single().plaintextSha256)
  }finally{controller.close();access.close();bytes.fill(0);key.fill(0);root.deleteRecursively()}
 }
}
