package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class EvidenceStaging179Test {
 @Test fun restartCleanupDeletesOnlyNamedOrphanCiphertextAndPreservesVaultsAndUnknownFiles(){
  val app=InstrumentationRegistry.getInstrumentation().targetContext
  val root=File(app.cacheDir,"evidence-cleanup179-${UUID.randomUUID()}").apply{mkdirs()}
  val context=object:ContextWrapper(app){override fun getNoBackupFilesDir()=root}
  try {
   val original=File(root,"hidden-media-v1/original.enc").apply{parentFile!!.mkdirs();writeText("synthetic encrypted original sentinel")}
   val stage=File(root,"evidence179-ciphertext-staging/${UUID.randomUUID()}/${UUID.randomUUID()}.cipher").apply{parentFile!!.mkdirs();writeBytes(byteArrayOf(1,2,3))}
   val opened=File(root,"evidence-open-hidden/encrypted-123456.stage").apply{parentFile!!.mkdirs();writeBytes(byteArrayOf(4,5))}
   val unknown=File(root,"evidence-open-hidden/preserve-owner-file").apply{writeText("must not delete")}
   EvidenceStagingCleanup.cleanOwned(context)
   assertFalse(stage.exists());assertFalse(opened.exists());assertEquals("synthetic encrypted original sentinel",original.readText());assertEquals("must not delete",unknown.readText())
  }finally{root.deleteRecursively()}
 }
}
