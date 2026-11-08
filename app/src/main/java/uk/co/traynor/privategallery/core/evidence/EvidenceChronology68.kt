package uk.co.traynor.privategallery.core.evidence

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.math.abs

/** Read-only interpretation of owner-scoped records. Original bytes, OCR and incidents stay in their existing stores. */
internal object EvidenceChronology68 {
    enum class Confidence { CONFIRMED, HIGH_CONFIDENCE, NEEDS_REVIEW, UNKNOWN }
    enum class Provenance { UNKNOWN, VAULT_CAPTURE, EXIF_WITH_OFFSET, EXIF_LOCAL_ONLY, OCR_VISIBLE, OCR_EXPLICIT_OFFSET, OCR_RELATIVE_DATE, OWNER_CONFIRMED }
    enum class ReviewReason {
        AMBIGUOUS_DATE, AMBIGUOUS_ZONE, INFERRED_DATE, CONFLICTING_TIMESTAMPS, UNKNOWN_PARTICIPANT,
        AMBIGUOUS_OCR, POSSIBLE_DUPLICATE, SUGGESTED_INCIDENT, PROCESSING_LIMIT
    }

    data class Timestamp(
        val instant: Instant? = null,
        val date: LocalDate? = null,
        val clock: LocalTime? = null,
        val provenance: Provenance = Provenance.UNKNOWN,
        val confidence: Confidence = Confidence.UNKNOWN,
        val sourceText: String? = null,
        val zone: String? = null,
    )

    /** Transcript version makes a correction incapable of inheriting a timestamp from an older line. */
    data class MessageLocation(val evidenceId: String, val transcriptVersion: String, val startLine: Int, val endLine: Int)

    data class Screenshot(
        val evidenceId: String,
        val sourceId: String,
        val sha256: String,
        val originalName: String,
        val fileCreatedAt: Long,
        val fileModifiedAt: Long,
        val importedAt: Long,
        val width: Int,
        val height: Int,
        val capture: Timestamp,
        val captureClaims: List<Timestamp>,
        val participants: List<String>,
    )

    data class Message(
        val sender: String?,
        val text: String,
        val visibleClock: String,
        val timestamp: Timestamp,
        val locations: List<MessageLocation>,
        val sourceUncertain: Boolean = false,
    )

    data class IncidentSuggestion(val title: String, val evidenceIds: List<String>, val reason: String)
    data class Review(val reason: ReviewReason, val evidenceIds: List<String>, val location: MessageLocation? = null)
    data class Result(
        val screenshots: List<Screenshot>,
        val messages: List<Message>,
        val incidents: List<IncidentSuggestion>,
        val review: List<Review>,
    )

    private const val MAX_LINES_PER_RECORD = 1_000
    private const val MAX_MESSAGES = 25_000
    private const val MAX_REVIEW = 8_192
    private val exifFormatter = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    private val dateFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.UK).withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.UK).withResolverStyle(ResolverStyle.STRICT),
    )
    private val numericDate = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})$")
    private val weekday = Regex("^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)$", RegexOption.IGNORE_CASE)
    private val namePrefix = Regex("^([\\p{L}][\\p{L}\\p{M} .’'-]{0,59}):\\s+(.+)$")
    private val offsetTimestamp = Regex("(?<!\\d)(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:\\d{2}))\\s*[✓✔]*$")
    private val trailingClock = Regex("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?:\\s*(am|pm))?\\s*[✓✔]*$", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")

    fun analyse(
        records: List<EvidenceCatalog.Evidence>,
        conversationZone: ZoneId? = null,
        confirmed: Map<MessageLocation, Instant> = emptyMap(),
        current: () -> Boolean = { true },
    ): Result {
        ensureCurrent(current)
        val screenshots = mutableListOf<Screenshot>()
        val messages = mutableListOf<Message>()
        val review = linkedSetOf<Review>()
        val recordMessages = linkedMapOf<String, IntRange>()
        for (record in records) {
            ensureCurrent(current)
            if (!record.mime().startsWith("image/")) continue
            val (capture, claims) = captureClaims(record.metadata())
            val id = record.id()
            if (capture.confidence == Confidence.NEEDS_REVIEW && claims.count { it.instant != null } > 1) {
                review += Review(ReviewReason.CONFLICTING_TIMESTAMPS, listOf(id))
            }
            val start = messages.size
            val remaining = MAX_MESSAGES - start
            if (remaining > 0) {
                messages += parseMessages(record, capture, conversationZone, confirmed, review, remaining, current)
                recordMessages[id] = start until messages.size
            } else if (record.ocr().isNotEmpty()) {
                review += Review(ReviewReason.PROCESSING_LIMIT, listOf(id))
            }
            val participants = record.participants() + messages.subList(start, messages.size).mapNotNull { it.sender }
            val metadata = record.metadata()
            screenshots += Screenshot(id, record.sourceId(), record.sha256(), metadata.sourceName(),
                metadata.fileCreatedAt(), metadata.fileModifiedAt(), record.importedAt(),
                metadata.width(), metadata.height(), capture, claims, participants.distinct())
        }
        screenshots.groupBy { it.sha256 }.values.filter { it.size > 1 }.forEach { matches ->
            review += Review(ReviewReason.POSSIBLE_DUPLICATE, matches.map { it.evidenceId })
        }
        val (merged, incidents) = overlap(messages, recordMessages, screenshots, review, current)
        ensureCurrent(current)
        return Result(screenshots, merged.sortedWith(compareBy<Message, Instant?>(nullsLast()) { it.timestamp.instant }),
            incidents, review.take(MAX_REVIEW))
    }

    private fun captureClaims(metadata: EvidenceCatalog.Metadata): Pair<Timestamp, List<Timestamp>> {
        val claims = mutableListOf<Timestamp>()
        if (metadata.captureAt() > 0L) {
            claims += Timestamp(Instant.ofEpochMilli(metadata.captureAt()), provenance = Provenance.VAULT_CAPTURE,
                confidence = Confidence.NEEDS_REVIEW, sourceText = metadata.embedded()["captureTimeProvenance"])
        }
        val exif = metadata.embedded()["embedded.DateTimeOriginal"]
        if (exif != null) {
            val local = runCatching { LocalDateTime.parse(exif.trim(), exifFormatter) }.getOrNull()
            val offset = metadata.embedded()["embedded.OffsetTimeOriginal"]?.let {
                runCatching { ZoneOffset.of(it.trim()) }.getOrNull()
            }
            claims += if (local != null && offset != null) {
                Timestamp(local.toInstant(offset), local.toLocalDate(), local.toLocalTime(),
                    Provenance.EXIF_WITH_OFFSET, Confidence.HIGH_CONFIDENCE, exif, offset.id)
            } else {
                Timestamp(date = local?.toLocalDate(), clock = local?.toLocalTime(),
                    provenance = Provenance.EXIF_LOCAL_ONLY, confidence = Confidence.NEEDS_REVIEW, sourceText = exif)
            }
        }
        val absolute = claims.mapNotNull { it.instant }
        val capture = when {
            absolute.size > 1 && abs(absolute[0].epochSecond - absolute[1].epochSecond) > 60 ->
                Timestamp(confidence = Confidence.NEEDS_REVIEW)
            else -> claims.lastOrNull { it.instant != null } ?: claims.lastOrNull() ?: Timestamp()
        }
        return capture to claims
    }

    private data class DateAnchor(val date: LocalDate?, val provenance: Provenance)
    private data class PendingLine(val number: Int, val text: String)

    private fun parseMessages(
        record: EvidenceCatalog.Evidence,
        capture: Timestamp,
        zone: ZoneId?,
        confirmed: Map<MessageLocation, Instant>,
        review: MutableSet<Review>,
        remaining: Int,
        current: () -> Boolean,
    ): List<Message> {
        val ocr = record.ocr().lastOrNull() ?: return emptyList()
        val correction = record.corrections().lastOrNull { it.ocrId() == ocr.id() }
        val version = correction?.id() ?: ocr.id()
        val recordId = listOf(record.id())
        val sourceUncertain = correction == null && (ocr.partial() || ocr.confidence() < 60f)
        if (sourceUncertain) {
            review += Review(ReviewReason.AMBIGUOUS_OCR, recordId)
        }
        val result = mutableListOf<Message>()
        val body = mutableListOf<PendingLine>()
        var anchor: DateAnchor? = null
        var previousClock: LocalTime? = null
        for ((index, raw) in record.transcript().lineSequence().withIndex()) {
            if (index % 16 == 0) ensureCurrent(current)
            if (index >= MAX_LINES_PER_RECORD || result.size >= remaining) {
                review += Review(ReviewReason.PROCESSING_LIMIT, recordId)
                break
            }
            val line = raw.trim()
            if (line.isEmpty()) continue
            val date = parseDate(line, capture, zone)
            if (date != null) {
                anchor = date
                previousClock = null
                body.clear()
                if (date.date == null) review += Review(ReviewReason.AMBIGUOUS_DATE, recordId)
                continue
            }
            val offsetEnd = offsetTimestamp.find(line)
            val end = offsetEnd ?: trailingClock.find(line)
            if (end == null) {
                body += PendingLine(index + 1, line)
                continue
            }
            val explicit = offsetEnd?.let { runCatching { OffsetDateTime.parse(it.groupValues[1].replace(' ', 'T')) }.getOrNull() }
            if (offsetEnd != null && explicit == null) {
                review += Review(ReviewReason.AMBIGUOUS_OCR, recordId)
                continue
            }
            val clock = explicit?.toLocalTime() ?: clock(end) ?: continue
            val sameLine = line.substring(0, end.range.first).trimEnd()
            if (sameLine.isNotEmpty()) body += PendingLine(index + 1, sameLine)
            if (body.isEmpty()) continue
            val location = MessageLocation(record.id(), version, body.first().number, index + 1)
            val prefix = namePrefix.matchEntire(body.first().text)
            val knownName = record.participants().firstOrNull { it.equals(body.first().text, ignoreCase = true) }
            val sender = prefix?.groupValues?.get(1) ?: knownName
            val content = when {
                prefix != null -> listOf(prefix.groupValues[2]) + body.drop(1).map { it.text }
                knownName != null && body.size > 1 -> body.drop(1).map { it.text }
                else -> body.map { it.text }
            }.joinToString("\n")
            body.clear()
            if (content.isBlank()) continue
            // An OCR sequence crossing midnight without a date separator cannot certify the next date.
            if (previousClock?.hour?.let { it >= 20 } == true && clock.hour < 4) anchor = null
            previousClock = clock
            val sourceText = end.value.trimEnd(' ', '\t', '✓', '✔')
            val visibleClock = explicit?.toLocalTime()?.toString() ?: sourceText
            val timestamp = messageTime(anchor, clock, sourceText, capture, zone, confirmed[location], sourceUncertain, explicit)
            if (sender == null) review += Review(ReviewReason.UNKNOWN_PARTICIPANT, recordId, location)
            if (timestamp.confidence != Confidence.CONFIRMED) {
                val reason = when {
                    timestamp.date == null -> ReviewReason.AMBIGUOUS_DATE
                    explicit == null && (zone == null || zone.rules.getValidOffsets(timestamp.date.atTime(clock)).size != 1) ->
                        ReviewReason.AMBIGUOUS_ZONE
                    timestamp.instant == null -> ReviewReason.CONFLICTING_TIMESTAMPS
                    timestamp.provenance == Provenance.OCR_RELATIVE_DATE -> ReviewReason.INFERRED_DATE
                    else -> null
                }
                if (reason != null) review += Review(reason, recordId, location)
            }
            result += Message(sender, content, visibleClock, timestamp, listOf(location), sourceUncertain)
        }
        if (result.isEmpty() && record.transcript().isNotBlank()) review += Review(ReviewReason.AMBIGUOUS_OCR, recordId)
        return result
    }

    private fun parseDate(line: String, capture: Timestamp, zone: ZoneId?): DateAnchor? {
        if (line.length > 40) return null
        val normalized = line.replace(whitespace, " ")
        if (weekday.matches(normalized)) return DateAnchor(null, Provenance.OCR_RELATIVE_DATE)
        val hints = EvidenceTextHints.scan(normalized)
        val standalone = hints.firstOrNull { it.text().equals(normalized, ignoreCase = true) } ?: return null
        return when (standalone.kind()) {
            "DATE" -> DateAnchor(parseExplicitDate(normalized), Provenance.OCR_VISIBLE)
            "RELATIVE_DATE" -> {
                val localCapture = if (zone != null) capture.instant?.atZone(zone)?.toLocalDate() else null
                val date = when (normalized.lowercase(Locale.ROOT)) {
                    "today" -> localCapture
                    "yesterday" -> localCapture?.minusDays(1)
                    else -> null
                }
                DateAnchor(date, Provenance.OCR_RELATIVE_DATE)
            }
            else -> null
        }
    }

    private fun parseExplicitDate(text: String): LocalDate? {
        val number = numericDate.matchEntire(text)
        if (number != null) {
            val (_, first, second, year) = number.groupValues
            if (year.length != 4) return null
            val a = first.toInt()
            val b = second.toInt()
            val dayFirst = a > 12 || a == b
            if (!dayFirst && b <= 12) return null
            return runCatching { LocalDate.of(year.toInt(), if (dayFirst) b else a, if (dayFirst) a else b) }.getOrNull()
        }
        val words = text.split(' ').map { word ->
            if (word.all(Char::isLetter)) word.lowercase(Locale.UK).replaceFirstChar { it.titlecase(Locale.UK) } else word
        }.joinToString(" ")
        return dateFormatters.firstNotNullOfOrNull { formatter -> runCatching { LocalDate.parse(words, formatter) }.getOrNull() }
    }

    private fun clock(match: MatchResult): LocalTime? {
        val (_, hour, minute, period) = match.groupValues
        val h = hour.toInt()
        if (period.isNotEmpty() && h !in 1..12) return null
        val normalized = if (period.isEmpty()) h else h % 12 + if (period.equals("pm", true)) 12 else 0
        return LocalTime.of(normalized, minute.toInt())
    }

    private fun messageTime(
        anchor: DateAnchor?,
        clock: LocalTime,
        visible: String,
        capture: Timestamp,
        zone: ZoneId?,
        confirmed: Instant?,
        partial: Boolean,
        explicit: OffsetDateTime?,
    ): Timestamp {
        val date = explicit?.toLocalDate() ?: anchor?.date
        val zoneId = explicit?.offset?.id ?: zone?.id
        if (confirmed != null) return Timestamp(confirmed, date, clock, Provenance.OWNER_CONFIRMED,
            Confidence.CONFIRMED, visible, zoneId)
        val local = date?.atTime(clock)
        val offset = local?.let { zone?.rules?.getValidOffsets(it)?.singleOrNull() }
        val instant = explicit?.toInstant() ?: if (local != null && offset != null) local.toInstant(offset) else null
        val conflicting = instant != null && capture.instant != null && instant > capture.instant
        val accepted = instant.takeUnless { conflicting }
        val confidence = if (accepted != null && !partial && (explicit != null || anchor?.provenance == Provenance.OCR_VISIBLE))
            Confidence.HIGH_CONFIDENCE else Confidence.NEEDS_REVIEW
        val provenance = if (explicit != null) Provenance.OCR_EXPLICIT_OFFSET else anchor?.provenance ?: Provenance.OCR_VISIBLE
        return Timestamp(accepted, date, clock, provenance, confidence, visible, zoneId)
    }

    private data class MessageKey(val sender: String, val content: String, val absolute: Instant?, val local: LocalDateTime?)
    private data class PairOccurrence(val evidenceId: String, val first: Int, val second: Int)
    private fun key(message: Message): MessageKey? {
        val sender = message.sender?.lowercase(Locale.ROOT) ?: return null
        val time = message.timestamp
        val instant = time.instant?.takeIf { time.confidence == Confidence.CONFIRMED || time.confidence == Confidence.HIGH_CONFIDENCE }
        // Matching a visible date and clock does not require inventing the conversation's timezone.
        val local = if (instant == null && time.zone == null && time.provenance == Provenance.OCR_VISIBLE &&
            !message.sourceUncertain && time.clock != null) time.date?.atTime(time.clock) else null
        if (instant == null && local == null) return null
        val content = message.text.lowercase(Locale.ROOT).replace(whitespace, " ").trim()
        if (content.length < 8) return null
        return MessageKey(sender, content, instant, local)
    }

    private fun overlap(
        messages: List<Message>,
        ranges: Map<String, IntRange>,
        screenshots: List<Screenshot>,
        review: MutableSet<Review>,
        current: () -> Boolean,
    ): Pair<List<Message>, List<IncidentSuggestion>> {
        val keys = messages.mapIndexed { index, message ->
            if (index % 16 == 0) ensureCurrent(current)
            key(message)
        }
        val pairs = linkedMapOf<Pair<MessageKey, MessageKey>, MutableList<PairOccurrence>>()
        for ((id, range) in ranges) {
            ensureCurrent(current)
            for (index in range) {
                if (index == range.last) continue
                val first = keys[index] ?: continue
                val second = keys[index + 1] ?: continue
                if (first != second) pairs.getOrPut(first to second, ::mutableListOf) += PairOccurrence(id, index, index + 1)
            }
        }
        val messageGroups = Groups(messages.size, messages.map { it.locations.single().evidenceId })
        val screenshotGroups = Groups(screenshots.size)
        val screenshotIndex = screenshots.mapIndexed { index, screenshot -> screenshot.evidenceId to index }.toMap()
        for (occurrences in pairs.values) {
            ensureCurrent(current)
            // A repeated pair within one screenshot cannot safely establish position across screenshots.
            if (occurrences.size < 2 || occurrences.map { it.evidenceId }.distinct().size != occurrences.size) continue
            val initial = occurrences.first()
            for (other in occurrences.drop(1)) {
                if (!messageGroups.canLink(initial.first, other.first) ||
                    !messageGroups.canLink(initial.second, other.second)) continue
                messageGroups.link(initial.first, other.first)
                messageGroups.link(initial.second, other.second)
                screenshotGroups.link(screenshotIndex.getValue(initial.evidenceId), screenshotIndex.getValue(other.evidenceId))
            }
        }
        val incidents = screenshots.indices.groupBy(screenshotGroups::root).values.filter { it.size > 1 }.map { indices ->
            val ids = indices.map { screenshots[it].evidenceId }
            val known = indices.flatMap { screenshots[it].participants }.filterNot { it.equals("you", true) }.distinct()
            val title = if (known.isEmpty()) "Overlapping conversation" else "Conversation with ${known.take(3).joinToString(", ")}"
            review += Review(ReviewReason.SUGGESTED_INCIDENT, ids)
            IncidentSuggestion(title, ids, "At least two consecutive messages match text, sender and date/time across screenshots.")
        }
        for (indices in keys.indices.filter { keys[it] != null }.groupBy { keys[it] }.values) {
            if (indices.map(messageGroups::root).distinct().size <= 1) continue
            val ids = indices.map { messages[it].locations.single().evidenceId }.distinct()
            if (ids.size > 1) review += Review(ReviewReason.POSSIBLE_DUPLICATE, ids)
        }
        val merged = messages.indices.groupBy(messageGroups::root).values.map { indices ->
            val candidates = indices.map(messages::get)
            val preferred = candidates.firstOrNull { it.timestamp.confidence == Confidence.CONFIRMED } ?: candidates.first()
            preferred.copy(locations = candidates.flatMap { it.locations })
        }
        return merged to incidents
    }

    private fun ensureCurrent(current: () -> Boolean) {
        if (!current()) throw CancellationException("Evidence analysis authority ended")
    }

    private class Groups(size: Int, private val labels: List<String>? = null) {
        private val parent = IntArray(size) { it }
        private val linkedLabels = mutableMapOf<Int, MutableSet<String>>()
        fun root(index: Int): Int {
            var value = index
            while (parent[value] != value) {
                parent[value] = parent[parent[value]]
                value = parent[value]
            }
            return value
        }
        private fun labels(root: Int): Set<String> = linkedLabels[root] ?: setOf(checkNotNull(labels)[root])
        fun canLink(a: Int, b: Int): Boolean {
            val first = root(a)
            val second = root(b)
            return first == second || labels == null || labels(first).none { it in labels(second) }
        }
        fun link(a: Int, b: Int) {
            val first = minOf(root(a), root(b))
            val second = maxOf(root(a), root(b))
            if (first == second) return
            if (labels != null) {
                linkedLabels.getOrPut(first) { mutableSetOf(labels[first]) }.addAll(labels(second))
                linkedLabels.remove(second)
            }
            parent[second] = first
        }
    }
}
