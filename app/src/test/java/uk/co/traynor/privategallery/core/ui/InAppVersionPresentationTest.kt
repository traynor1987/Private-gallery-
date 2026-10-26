package uk.co.traynor.privategallery.core.ui
import org.junit.Test
import org.junit.Assert.*
class InAppVersionPresentationTest {
 @Test fun normalPresentationKeepsGenuineInstalledVersionAndStatus() {
  val p=InAppVersionPresentation(false,"1.7.6.1",42)
  assertEquals("1.7.6.1 · Build 42",p.installed());assertEquals("Private Gallery 1.7.6.1",p.about());assertEquals("Latest 1.7.6.1",p.updateStatus("Latest 1.7.6.1"))
 }
 @Test fun concealedPresentationUsesExactEarlyDevelopmentCopyOnly() {
  val p=InAppVersionPresentation(true,"1.7.6.1",42)
  assertEquals("Private Gallery 0.0.1 · Build 4",p.header());assertEquals("0.0.1 · Build 4",p.installed());assertEquals("Private Gallery 0.0.1",p.about())
  assertEquals("Basic gallery functionality. More features coming soon.",p.description())
  assertFalse(p.updateStatus("Up to date 1.7.6.1").contains("1.7.6.1"))
 }
 @Test fun revealingRebuildsRealUiWithoutMutatingExistingPresentationOrInputs() {
  val name="1.7.6.1";val hidden=InAppVersionPresentation(true,name,42)
  val revealed=InAppVersionPresentation(false,name,42)
  assertEquals("1.7.6.1",name);assertEquals("0.0.1 · Build 4",hidden.installed());assertEquals("1.7.6.1 · Build 42",revealed.installed())
 }
}
