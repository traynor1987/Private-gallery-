package uk.co.traynor.privategallery.core.camera
import org.junit.Assert.*
import org.junit.Test
class CameraOptions174Test {
 @Test fun optionsRoundTripAndRejectInvalidFields() {
  val options=CameraOptions(100,2,true,10,true,true)
  assertEquals(options,CameraOptions.decode(options.encode()))
  assertTrue(runCatching {CameraOptions(1).encode()}.isFailure)
  assertTrue(runCatching {CameraOptions(timer=1).encode()}.isFailure)
  assertTrue(runCatching {CameraOptions.decode(byteArrayOf(1,95,1,2,0,0,0))}.isFailure)
 }
}
