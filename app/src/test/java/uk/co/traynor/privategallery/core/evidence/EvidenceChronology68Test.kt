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

    @Test fun conversationKeepsWrappedMessagesAndMissedCallWithSpacedClock() {
        val evidence = screenshot("""
            19:44
            Alex
            12 May 2025
            Can you collect the parcel after your shift and
            leave it at reception please?
            21 : 46
            Missed voice call
            Tap to call back
            21:49
            I can after work. 21:51 ✓✓
        """.trimIndent())
        val result = EvidenceChronology68.analyse(listOf(evidence))
        assertEquals(listOf(
            "Can you collect the parcel after your shift and\nleave it at reception please?",
            "Missed voice call",
            "I can after work.",
        ), result.messages.map { it.text })
        assertEquals(listOf(LocalTime.of(21, 46), LocalTime.of(21, 49), LocalTime.of(21, 51)),
            result.messages.map { it.timestamp.clock })
        assertTrue(result.messages.all { it.timestamp.instant == null && it.sender == null })
        assertEquals(listOf(evidence.id()), result.incidents.single().evidenceIds)
        assertTrue(result.review.any { it.reason == ReviewReason.UNKNOWN_PARTICIPANT })
        assertEquals(EvidenceChronology68.Kind.CALL, result.messages[1].kind)
    }

    @Test fun untimedTextSurvivesAsUncertainWithoutUsingImportTime() {
        val evidence = screenshot("Could you look at the gate please?\nIt was open when I arrived.")
        val result = EvidenceChronology68.analyse(listOf(evidence))
        val fragment = result.messages.single()
        assertEquals(evidence.transcript(), fragment.text)
        assertNull(fragment.sender)
        assertNull(fragment.timestamp.date)
        assertNull(fragment.timestamp.instant)
        assertEquals("", fragment.visibleClock)
        assertTrue(fragment.sourceUncertain)
        assertTrue(result.review.any { it.reason == ReviewReason.AMBIGUOUS_OCR })
    }

    @Test fun adjacentBubbleLayoutSeparatesUntimedReplyWithoutAssigningNames() {
        val lines = listOf(
            "12 May 2025",
            "Bring the spare access card",
            "if reception is closed.",
            "I'll leave it on my desk. 21:46",
        )
        val geometry = listOf(
            fixtureWords(lines[0], 410, 660, 140),
            fixtureWords(lines[1], 60, 590, 220),
            fixtureWords(lines[2], 60, 470, 243),
            fixtureWords(lines[3], 570, 1030, 278),
        ).flatten()
        val record = screenshot(lines.joinToString("\n"), words = geometry)
        val messages = EvidenceChronology68.analyse(listOf(record)).messages
        assertEquals(listOf("Bring the spare access card\nif reception is closed.", "I'll leave it on my desk."), messages.map { it.text })
        assertEquals(listOf(EvidenceChronology68.Direction.INCOMING, EvidenceChronology68.Direction.OUTGOING),
            messages.map { it.direction })
        assertTrue(messages.all { it.sender == null && it.senderConfidence == Confidence.UNKNOWN })
        assertNull(messages[0].timestamp.clock)
        assertEquals(LocalTime.of(21, 46), messages[1].timestamp.clock)
        assertEquals(listOf(2 to 3, 4 to 4), messages.map { it.locations.single().startLine to it.locations.single().endLine })
    }

    @Test fun timeMentionedInsideMessageIsKeptAsContentWithoutGeometryEvidence() {
        val record = screenshot("12 May 2025\nAlex: The next bus leaves at 21:46")
        val result = EvidenceChronology68.analyse(listOf(record))
        val message = result.messages.single()
        assertEquals("The next bus leaves at 21:46", message.text)
        assertEquals("Alex", message.sender)
        assertEquals(Confidence.NEEDS_REVIEW, message.senderConfidence)
        assertNull(message.timestamp.clock)
        assertEquals(LocalDate.of(2025, 5, 12), message.timestamp.date)
    }

    @Test fun visibleDateWithoutYearAndNarrowSpaceClockRemainUncertain() {
        val record = screenshot("Monday, 12 May\nAlex: I'll call after the delivery. 9:46\u202fPM")
        val time = EvidenceChronology68.analyse(listOf(record), london).messages.single().timestamp
        assertEquals("Monday, 12 May", time.visibleDate)
        assertEquals(LocalTime.of(21, 46), time.clock)
        assertNull(time.date)
        assertNull(time.instant)
        assertEquals(Confidence.NEEDS_REVIEW, time.confidence)
    }

    @Test fun visibleWeekdayConflictingWithCalendarDateIsNotSilentlyAccepted() {
        val record = screenshot("Tuesday, 12 May 2025\nAlex: I'll call after the delivery. 21:46")
        val time = EvidenceChronology68.analyse(listOf(record), london).messages.single().timestamp
        assertEquals("Tuesday, 12 May 2025", time.visibleDate)
        assertNull(time.date)
        assertNull(time.instant)
    }

    @Test fun statusAndContactHeaderAreNotAttributedAsMessageWithoutDateSeparator() {
        val lines = listOf("19:44", "Alex", "I have your parcel. 21:46")
        val record = screenshot(lines.joinToString("\n"), words = listOf(
            fixtureWords(lines[0], 60, 150, 40),
            fixtureWords(lines[1], 140, 245, 108),
            fixtureWords(lines[2], 60, 590, 370),
        ).flatten())
        val message = EvidenceChronology68.analyse(listOf(record)).messages.single()
        assertEquals("I have your parcel.", message.text)
        assertNull(message.sender)
        assertEquals(LocalTime.of(21, 46), message.timestamp.clock)
        assertNull(message.timestamp.date)
    }

    @Test fun sharedOwnerParticipantAndDateSuggestGroupingWithoutMergingDifferentMessages() {
        val first = screenshot("12 May 2025\nAlex: The first receipt is ready. 21:46", participants = listOf("Alex"))
        val second = screenshot("12 May 2025\nAlex: I found the other receipt. 21:47", id = 2, participants = listOf("Alex"))
        val result = EvidenceChronology68.analyse(listOf(first, second))
        assertEquals(2, result.messages.size)
        assertEquals(setOf(first.id(), second.id()), result.incidents.single().evidenceIds.toSet())
        assertTrue(result.messages.all { it.senderConfidence == Confidence.HIGH_CONFIDENCE })
    }

    @Test fun applyingLayoutDoesNotDetachPriorReviewedLineRange() {
        val lines = listOf("12 May 2025", "Text without a visible clock.", "Existing reviewed line. 21:46")
        val record = screenshot(lines.joinToString("\n"), words = listOf(
            fixtureWords(lines[0], 410, 660, 140),
            fixtureWords(lines[1], 60, 590, 220),
            fixtureWords(lines[2], 570, 1030, 278),
        ).flatten())
        val legacyLocation = EvidenceChronology68.MessageLocation(record.id(), record.ocr().single().id(), 2, 3)
        val time = Instant.parse("2025-05-12T20:46:00Z")
        val reviewed = EvidenceChronology68.analyse(listOf(record), confirmed = mapOf(legacyLocation to time)).messages.single()
        assertEquals(legacyLocation, reviewed.locations.single())
        assertEquals(time, reviewed.timestamp.instant)
        assertEquals(Confidence.CONFIRMED, reviewed.timestamp.confidence)
    }

    private fun fixtureWords(line: String, left: Int, right: Int, top: Int): List<EvidenceCatalog.Word> {
        val parts = line.split(' ')
        val width = (right - left) / parts.size
        return parts.mapIndexed { index, part ->
            EvidenceCatalog.Word(0, part, 95f, left + index * width, top, left + (index + 1) * width - 6, top + 18)
        }
    }

    private fun screenshot(
        text: String, captureAt: Long = 0, id: Int = 1, embedded: Map<String, String> = emptyMap(),
        words: List<EvidenceCatalog.Word> = emptyList(), participants: List<String> = emptyList(),
    ): EvidenceCatalog.Evidence {
        val ocr = EvidenceCatalog.Ocr(uuid(id + 20), 10, "fixture", 0, 1080, 2400, text, 92f,
            words, emptyList(), 1, false)
        return EvidenceCatalog.Evidence(uuid(id), uuid(id + 10), "Screenshot.png", "image/png",
            id.toString().padStart(64, '0'), 100, 456,
            EvidenceCatalog.Metadata("Screenshot.png", 0, 123, 400, captureAt, 1080, 2400, embedded, false),
            "", "", emptyList(), participants, listOf(ocr), emptyList())
    }

    private fun copyWithCorrection(value: EvidenceCatalog.Evidence, correction: EvidenceCatalog.Correction) =
        EvidenceCatalog.Evidence(value.id(), value.sourceId(), value.name(), value.mime(), value.sha256(),
            value.size(), value.importedAt(), value.metadata(), value.description(), value.notes(), value.tags(),
            value.participants(), value.ocr(), listOf(correction))

    private fun uuid(value: Int) = UUID(0, value.toLong()).toString()
}
