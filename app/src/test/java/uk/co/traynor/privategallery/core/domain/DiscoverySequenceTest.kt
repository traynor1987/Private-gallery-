package uk.co.traynor.privategallery.core.domain

import org.junit.Assert.*
import org.junit.Test

class DiscoverySequenceTest {
    private fun prefix(sequence: DiscoverySequence) {
        repeat(5) { assertNull(sequence.consume(DiscoveryEvent.INSTALLED)) }
        assertNull(sequence.consume(DiscoveryEvent.VERSION))
        repeat(3) { assertNull(sequence.consume(DiscoveryEvent.INSTALLED)) }
    }
    @Test fun `exact sequence produces a single transient challenge and immediately resets`() {
        val sequence = DiscoverySequence { 0 }
        prefix(sequence)
        assertNotNull(sequence.consume(DiscoveryEvent.INSTALLED))
        assertNull(sequence.consume(DiscoveryEvent.INSTALLED))
        sequence.reset(); prefix(sequence)
        assertNotNull(sequence.consume(DiscoveryEvent.INSTALLED))
    }
    @Test fun `wrong order other and explicit exit all reset`() {
        for (reset in listOf<(DiscoverySequence) -> Unit>(
            { it.consume(DiscoveryEvent.VERSION) }, { it.consume(DiscoveryEvent.OTHER) }, { it.reset() }
        )) {
            val sequence = DiscoverySequence { 0 }
            repeat(4) { sequence.consume(DiscoveryEvent.INSTALLED) }
            reset(sequence)
            assertNull(sequence.consume(DiscoveryEvent.INSTALLED))
            assertNull(sequence.consume(DiscoveryEvent.VERSION))
            repeat(4) { assertNull(sequence.consume(DiscoveryEvent.INSTALLED)) }
            sequence.reset(); prefix(sequence); assertNotNull(sequence.consume(DiscoveryEvent.INSTALLED))
        }
        val sequence = DiscoverySequence { 0 }
        repeat(6) { assertNull(sequence.consume(DiscoveryEvent.INSTALLED)) }
        assertNull(sequence.consume(DiscoveryEvent.VERSION))
        repeat(4) { assertNull(sequence.consume(DiscoveryEvent.INSTALLED)) }
    }
    @Test fun `whole sequence deadline is strict monotonic bounded and cold instances reset`() {
        var now = 100L
        val sequence = DiscoverySequence { now }; prefix(sequence)
        now = 30_100; assertNull(sequence.consume(DiscoveryEvent.INSTALLED))
        sequence.reset(); prefix(sequence); now += 29_999
        assertNotNull(sequence.consume(DiscoveryEvent.INSTALLED))
        val cold = DiscoverySequence { now }; repeat(4) { assertNull(cold.consume(DiscoveryEvent.INSTALLED)) }
        sequence.reset(); prefix(sequence); now--
        assertNull(sequence.consume(DiscoveryEvent.INSTALLED))
    }
}
