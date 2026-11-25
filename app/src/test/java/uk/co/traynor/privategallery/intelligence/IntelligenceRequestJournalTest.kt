package uk.co.traynor.privategallery.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.core.brain.NovaBrainConfiguration

class IntelligenceRequestJournalTest {
    private val configuration = NovaBrainConfiguration("https://brain.example", "fixture-owner-token-".repeat(2))
    private val fixture = "A synthetic question for durable retry"
    private var bytes: ByteArray? = null

    private fun open() = IntelligenceRequestJournal({ bytes?.copyOf() }) { bytes = it.copyOf() }

    @Test fun retryAfterReopenReusesTheRecordedIdAndNeverPersistsMessageText() {
        val first = request()
        val reserved = ready(open().reserve(configuration, first))
        assertTrue(reserved.newEntry)
        assertEquals(first.requestId, reserved.entry.requestId)
        val contents = checkNotNull(bytes).decodeToString()
        assertFalse(contents.contains(fixture))
        assertFalse(contents.contains(configuration.token))
        assertFalse(contents.contains(configuration.origin))

        val typedAgain = request()
        assertNotEquals(first.requestId, typedAgain.requestId)
        val retry = ready(open().reserve(configuration.copy(origin = "https://BRAIN.example:443/"), typedAgain))
        assertFalse(retry.newEntry)
        assertEquals(first.requestId, retry.entry.requestId)
        val withContext = request(listOf(IntelligenceExchange("An earlier question", "An earlier reply", cloud = true)))
        assertEquals(IntelligenceRequestJournal.Decision.PayloadChanged, open().reserve(configuration, withContext))

        val anotherAccount = configuration.copy(token = "another-owner-token-".repeat(2))
        assertTrue(ready(open().reserve(anotherAccount, typedAgain)).newEntry)
        open().resolve(reserved.entry)
        assertEquals(typedAgain.requestId, ready(open().reserve(configuration, typedAgain)).entry.requestId)
        assertFalse(ready(open().reserve(anotherAccount, typedAgain)).newEntry)
    }

    @Test fun fullJournalPreservesUncertainIdentitiesUntilTheyAreResolved() {
        val journal = open()
        val entries = (1..24).map { index ->
            val request = IntelligenceCloudRequest.prepare("Question $index", emptyList(), IntelligenceCloudMode.CONFIGURED_BRAIN)
            ready(journal.reserve(configuration, request)).entry
        }
        assertTrue(checkNotNull(bytes).size < 8192)
        assertEquals(IntelligenceRequestJournal.Decision.Full, open().reserve(configuration, request()))
        journal.resolve(entries.first())
        assertTrue(ready(open().reserve(configuration, request())).newEntry)
    }

    private fun request(history: List<IntelligenceExchange> = emptyList()) =
        IntelligenceCloudRequest.prepare(fixture, history, IntelligenceCloudMode.CONFIGURED_BRAIN)

    private fun ready(decision: IntelligenceRequestJournal.Decision) = decision as IntelligenceRequestJournal.Decision.Ready
}
