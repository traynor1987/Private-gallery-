package uk.co.traynor.privategallery.core.gallery
import org.junit.Assert.*
import org.junit.Test
class MediaCaptureDateTest {
 @Test fun originalTimestampUsesExplicitOffset() {
  assertEquals(java.time.Instant.parse("2020-06-01T11:30:00Z").toEpochMilli(),MediaCaptureDate.parseExifTimestamp("2020:06:01 12:30:00","+01:00"))
 }
 @Test fun missingTimezoneAndInvalidDatesAreUnknownRatherThanGuessed() {
  assertNull(MediaCaptureDate.parseExifTimestamp("2020:06:01 12:30:00",null))
  assertNull(MediaCaptureDate.parseExifTimestamp("2020:02:31 12:30:00","Z"))
  assertNull(MediaCaptureDate.parseExifTimestamp("garbage","+99:00"))
 }
}
