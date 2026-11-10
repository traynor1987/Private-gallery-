package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class NotificationProbeTest {
    @Test fun onlyAnExactEnrolledProfileCanBeIdentifiedAsFriends() {
        assertEquals(ProbeAccount.MAIN, probeAccount(true, 0, 12))
        assertEquals(ProbeAccount.FRIENDS, probeAccount(false, 12, 12))
        assertEquals(ProbeAccount.UNRESOLVED, probeAccount(false, 13, 12))
        assertEquals(ProbeAccount.UNRESOLVED, probeAccount(false, -1, 12))
        assertEquals(ProbeAccount.UNRESOLVED, probeAccount(false, 12, null))
        assertEquals(ProbeAccount.UNRESOLVED, probeAccount(true, -1, 12))
        assertEquals(ProbeAccount.UNRESOLVED, probeAccount(true, 12, 12))
    }

    @Test fun conversationUpdatesCanCarryGenuinelyNewMessages() {
        val probe = NotificationProbe()
        val first = ProbeMessage(100, "sender", "one")
        val second = ProbeMessage(101, "sender", "two")
        fun post(key: String, entries: List<ProbeMessage>, summary: Boolean = false) =
            probe.record(ProbeAccount.MAIN, key, true, false, false, true, summary, "conversation", entries)
        post("first-key", listOf(first))
        post("first-key", listOf(first, second))
        post("second-key", listOf(second))
        post("group", listOf(first, second), summary = true)
        val result = probe.results.getValue(ProbeAccount.MAIN)
        assertEquals(4, result.posts)
        assertEquals(2, result.estimatedMessages)
        assertEquals(1, result.updates)
        assertEquals(1, result.summaries)
    }

    @Test fun repeatedKeysAreUpdatesOnlyWithinTheSameAccountAndProbe() {
        val probe = NotificationProbe()
        probe.record(ProbeAccount.MAIN, "same", text = true, dnd = true, quietChannel = false, grouped = false)
        probe.record(ProbeAccount.FRIENDS, "same", text = false, dnd = false, quietChannel = true, grouped = true)
        probe.record(ProbeAccount.MAIN, "same", text = true, dnd = false, quietChannel = false, grouped = true)
        assertEquals(ProbeResult(2, 2, 1, 0, 1, 1, unestimatedEvents = 2), probe.results.getValue(ProbeAccount.MAIN))
        assertEquals(ProbeResult(1, 0, 0, 1, 1, 0, unestimatedEvents = 1), probe.results.getValue(ProbeAccount.FRIENDS))
        val nextProbe = NotificationProbe()
        nextProbe.record(ProbeAccount.MAIN, "same", text = false, dnd = false, quietChannel = false, grouped = false)
        assertEquals(0, nextProbe.results.getValue(ProbeAccount.MAIN).updates)
    }
}
