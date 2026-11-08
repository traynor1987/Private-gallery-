package uk.co.traynor.privategallery.core.evidence

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceChronology68.Confidence
import uk.co.traynor.privategallery.core.evidence.EvidenceChronology68.Provenance
import uk.co.traynor.privategallery.core.evidence.EvidenceChronology68.ReviewReason

class EvidenceChronology68Test {
    private val london = ZoneId.of("Europe/London")
    private val capture = Instant.parse("2025-05-12T21:18:00Z")

    @Test fun retiredAccessCancelsBeforeReturningDerivedEvidence() {
        val evidence = screenshot("12 May 2025\nAlex: Can you call me? 21:46")
        var authorityChecks = 0
        assertThrows(CancellationException::class.java) {
            EvidenceChronology68.analyse(listOf(evidence), london, current = { ++authorityChecks < 3 })
        }
    }

    @Test fun captureAndMessageAreDifferentEvents() {
        val evidence = screenshot("""
            12 May 2025
            Alex: Can you call me? 21:46
            You: I am busy. 21:50
        """.trimIndent(), capture.toEpochMilli())
        val result = EvidenceChronology68.analyse(listOf(evidence), london)
        assertEquals(capture, result.screenshots.single().capture.instant)
        assertEquals(Provenance.VAULT_CAPTURE, result.screenshots.single().capture.provenance)
        assertEquals(listOf("Can you call me?", "I am busy."), result.messages.map { it.text })
        assertEquals(Instant.parse("2025-05-12T20:46:00Z"), result.messages.first().timestamp.instant)
        assertEquals(Confidence.HIGH_CONFIDENCE, result.messages.first().timestamp.confidence)
    }

    @Test fun numericDatesClockOnlyAndNoZoneStayUnresolved() {
        val evidence = screenshot("""
            05/06/2025
            Alex: Can you call? 21:46
            13 May 2025
            Alex: Please reply. 09:17
        """.trimIndent())
        val result = EvidenceChronology68.analyse(listOf(evidence))
        assertNull(result.screenshots.single().capture.instant)
        assertNull(result.messages[0].timestamp.date)
        assertEquals(LocalTime.of(21, 46), result.messages[0].timestamp.clock)
        assertEquals(LocalDate.of(2025, 5, 13), result.messages[1].timestamp.date)
        assertTrue(result.messages.all { it.timestamp.instant == null })
        assertTrue(result.review.any { it.reason == ReviewReason.AMBIGUOUS_DATE })
    }

    @Test fun dstOverlapAndFutureMessageRequireReview() {
        val overlap = screenshot("26 October 2025\nAlex: During the clock change 01:30")
        val future = screenshot("12 May 2025\nAlex: This looks too late 22:58", capture.toEpochMilli(), id = 2)
        val result = EvidenceChronology68.analyse(listOf(overlap, future), london)
        assertTrue(result.messages.all { it.timestamp.instant == null })
        assertTrue(result.review.any { it.reason == ReviewReason.AMBIGUOUS_ZONE })
        assertTrue(result.review.any { it.reason == ReviewReason.CONFLICTING_TIMESTAMPS })
    }

    @Test fun midnightWithoutNewSeparatorAndRelativeDatesAreFlagged() {
        val midnight = screenshot("12 May 2025\nAlex: Before midnight 23:59\nYou: After midnight 00:01")
        val relative = screenshot("Yesterday\nAlex: Call me after dinner 20:00", capture.toEpochMilli(), id = 2)
        val result = EvidenceChronology68.analyse(listOf(midnight, relative), london)
        val afterMidnight = result.messages.single { it.text == "After midnight" }
        assertNull(afterMidnight.timestamp.instant)
        val yesterday = result.messages.single { it.text == "Call me after dinner" }
        assertEquals(Instant.parse("2025-05-11T19:00:00Z"), yesterday.timestamp.instant)
        assertEquals(Confidence.NEEDS_REVIEW, yesterday.timestamp.confidence)
        assertTrue(result.review.any { it.reason == ReviewReason.INFERRED_DATE })
    }

    @Test fun correctedTranscriptAndOwnerTimestampPreserveOriginalText() {
        val original = screenshot("12 May 2025\nAlex: I am flee. 21:46")
        val correction = EvidenceCatalog.Correction(uuid(40), original.ocr().single().id(), 11,
            "12 May 2025\nAlex: I am free. 21:46")
        val evidence = copyWithCorrection(original, correction)
        val initial = EvidenceChronology68.analyse(listOf(evidence), london).messages.single()
        val correctedTime = Instant.parse("2025-05-12T20:45:00Z")
        val confirmed = EvidenceChronology68.analyse(listOf(evidence), london, mapOf(initial.locations.single() to correctedTime))
        assertEquals(correction.id(), initial.locations.single().transcriptVersion)
        assertEquals("I am free.", confirmed.messages.single().text)
        assertEquals(Confidence.CONFIRMED, confirmed.messages.single().timestamp.confidence)
        assertEquals(correctedTime, confirmed.messages.single().timestamp.instant)
        assertTrue(evidence.ocr().single().text().contains("flee"))
    }

    @Test fun adjacentMatchingMessagesAreDeduplicatedWithBothScreenshotReferences() {
        val first = screenshot("12 May 2025\nAlex: Are you free tonight? 21:44\nYou: I can call tomorrow. 21:46")
        val second = screenshot("12 May 2025\nAlex: Are you free tonight? 21:44\nYou: I can call tomorrow. 21:46\nAlex: Tomorrow works too. 21:47", id = 2)
        val result = EvidenceChronology68.analyse(listOf(first, second), london)
        assertEquals(3, result.messages.size)
        assertEquals(listOf(2, 2, 1), result.messages.map { it.locations.size })
        assertEquals(setOf(first.id(), second.id()), result.incidents.single().evidenceIds.toSet())
        val noZone = EvidenceChronology68.analyse(listOf(first, second))
        assertEquals(3, noZone.messages.size)
        assertTrue(noZone.messages.all { it.timestamp.instant == null })
    }

    @Test fun singleMatchingMessageInDifferentScreenshotsIsOnlyAPossibleDuplicate() {
        val first = screenshot("12 May 2025\nAlex: Please call me soon. 21:46")
        val second = screenshot("12 May 2025\nAlex: Please call me soon. 21:46", id = 2)
        val result = EvidenceChronology68.analyse(listOf(first, second), london)
        assertEquals(2, result.messages.size)
        assertTrue(result.incidents.isEmpty())
        assertTrue(result.review.any { it.reason == ReviewReason.POSSIBLE_DUPLICATE })
    }

    @Test fun offsetExifDoesNotUseFileOrImportTimesAsCapture() {
        val withExif = screenshot("", embedded = mapOf(
            "embedded.DateTimeOriginal" to "2025:05:12 22:18:23",
            "embedded.OffsetTimeOriginal" to "+01:00"
        ))
        val missingExif = screenshot("", id = 2, embedded = mapOf("embedded.DateTimeOriginal" to "2025:05:12 22:18:23"))
        val result = EvidenceChronology68.analyse(listOf(withExif, missingExif))
        assertEquals(Instant.parse("2025-05-12T21:18:23Z"), result.screenshots[0].capture.instant)
        assertEquals(Provenance.EXIF_WITH_OFFSET, result.screenshots[0].capture.provenance)
        assertNull(result.screenshots[1].capture.instant)
        assertEquals(123L, result.screenshots[1].fileModifiedAt)
        assertEquals(456L, result.screenshots[1].importedAt)
    }

    @Test fun anExplicitMessageOffsetNeedsNoAssumedDeviceZone() {
        val evidence = screenshot("Alex: This is the last message. 2025-05-12T21:46:23+01:00")
        val message = EvidenceChronology68.analyse(listOf(evidence)).messages.single()
        assertEquals("This is the last message.", message.text)
        assertEquals(Instant.parse("2025-05-12T20:46:23Z"), message.timestamp.instant)
        assertEquals(Provenance.OCR_EXPLICIT_OFFSET, message.timestamp.provenance)
        assertEquals("+01:00", message.timestamp.zone)
    }

    @Test fun conflictingCaptureClaimsAreNotSilentlyChosen() {
        val evidence = screenshot("", capture.toEpochMilli(), embedded = mapOf(
            "embedded.DateTimeOriginal" to "2025:05:13 08:00:00",
            "embedded.OffsetTimeOriginal" to "+01:00"
        ))
        val result = EvidenceChronology68.analyse(listOf(evidence))
        assertNull(result.screenshots.single().capture.instant)
        assertEquals(Confidence.NEEDS_REVIEW, result.screenshots.single().capture.confidence)
        assertEquals(2, result.screenshots.single().captureClaims.size)
    }

    private fun screenshot(text: String, captureAt: Long = 0, id: Int = 1, embedded: Map<String, String> = emptyMap()): EvidenceCatalog.Evidence {
        val ocr = EvidenceCatalog.Ocr(uuid(id + 20), 10, "fixture", 0, 1080, 2400, text, 92f,
            emptyList(), emptyList(), 1, false)
        return EvidenceCatalog.Evidence(uuid(id), uuid(id + 10), "Screenshot.png", "image/png",
            id.toString().padStart(64, '0'), 100, 456,
            EvidenceCatalog.Metadata("Screenshot.png", 0, 123, 400, captureAt, 1080, 2400, embedded, false),
            "", "", emptyList(), emptyList(), listOf(ocr), emptyList())
    }

    private fun copyWithCorrection(value: EvidenceCatalog.Evidence, correction: EvidenceCatalog.Correction) =
        EvidenceCatalog.Evidence(value.id(), value.sourceId(), value.name(), value.mime(), value.sha256(),
            value.size(), value.importedAt(), value.metadata(), value.description(), value.notes(), value.tags(),
            value.participants(), value.ocr(), listOf(correction))

    private fun uuid(value: Int) = UUID(0, value.toLong()).toString()
}
