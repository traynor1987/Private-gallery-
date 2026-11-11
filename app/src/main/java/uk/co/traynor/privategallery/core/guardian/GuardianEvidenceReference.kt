package uk.co.traynor.privategallery.core.guardian

private val guardianMessageId = Regex("[0-9a-f]{64}")

internal fun guardianEvidenceEventId(messageId: String): String = "guardian-$messageId"

internal fun guardianMessageFromEvidenceEvent(eventId: String): String? =
    eventId.takeIf { it.startsWith("guardian-") }?.removePrefix("guardian-")
        ?.takeIf(guardianMessageId::matches)
