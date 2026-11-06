package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PayloadRestoreCancellationTest {
 @Test fun retirementDuringSecondReadbackNeverPromotesImageDocumentOrVideo() {
  for(kind in listOf("image","document","video")) {
   val root=Files.createTempDirectory("restore-cancel-").toFile()
   try {
    var closed=false;var verificationChecks=0
    val source=object:ByteArrayInputStream(ByteArray(3*1024*1024){(it%251).toByte()}){override fun close(){closed=true;super.close()}}
    val store=EncryptedPayloadStore(root,syncOutput={})
    assertThrows(IllegalStateException::class.java) {
     store.writeAndVerify(kind,source,ByteArray(32){it.toByte()},kind=="video") {
      // Deterministic retirement in the real second-pass call, after at least one chunk.
      if(Thread.currentThread().stackTrace.any{it.methodName in setOf("verify","verifyAll")})++verificationChecks>=(if(kind=="video")2 else 3) else false
     }
    }
    assertEquals(if(kind=="video")2 else 3,verificationChecks);assertTrue(closed)
    assertFalse(File(root,"payloads/$kind.vault").exists());assertFalse(File(root,"staging/$kind.part").exists())
   }finally{root.deleteRecursively()}
  }
 }
 @Test fun retirementWhileEncryptingClosesSourceAndRemovesStage() {
  for(video in listOf(false,true)) {
   val root=Files.createTempDirectory("encrypt-cancel-").toFile()
   try {
    var closed=false;var reads=0
    val source=object:ByteArrayInputStream(ByteArray(2*1024*1024)){
     override fun read(b:ByteArray,off:Int,len:Int):Int {reads++;return super.read(b,off,len)}
     override fun close(){closed=true;super.close()}
    }
    assertThrows(IllegalStateException::class.java){EncryptedPayloadStore(root,syncOutput={}).writeAndVerify("cancel",source,ByteArray(32),video){reads>0}}
    assertTrue(closed);assertFalse(File(root,"payloads/cancel.vault").exists());assertFalse(File(root,"staging/cancel.part").exists())
   }finally{root.deleteRecursively()}
  }
 }
}
