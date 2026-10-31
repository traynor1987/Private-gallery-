package uk.co.traynor.privategallery.core.evidence

import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream

class EvidenceEgress179Test {
 @Test fun foreignOwnerIsRejectedBeforeSourceEnumeration() {
  val reader=object:EvidenceSourceReader {
   override val owner=EvidenceCatalog.Owner.HIDDEN
   override fun current()=true
   override fun inspect(sourceId:String):EvidenceOriginal=error("must not inspect")
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T=error("must not read")
  }
  try {EvidenceEgress.requireAllowed(EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY),emptyList(),emptyList(),reader);fail("cross owner allowed")}
  catch(expected:SecurityException){}
 }
 @Test fun digestIsBoundToWholeCatalogueAndOwner() {
  val primary=EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY)
  val hidden=EvidenceCatalog.empty(EvidenceCatalog.Owner.HIDDEN)
  assertEquals(EvidenceEgress.digest(primary),EvidenceEgress.digest(primary))
  assertNotEquals(EvidenceEgress.digest(primary),EvidenceEgress.digest(hidden))
 }
}
