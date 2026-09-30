package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.*
import org.junit.Test

/**
 * PROPOSED 2.0 DESIGN ONLY: executable test-side protocol model. These tests are
 * not evidence that production has a journal, generations, root selector or
 * destination receipts. No production storage/Hidden/transfer API is invoked.
 */
class ProposedDurabilityProtocolModelTest {
    private data class Identity(val container:String,val generation:Long,val item:String,val revision:Long)
    private data class Receipt(val operation:String,val source:Identity,val destination:Identity,val cipherHash:String,val indexHash:String)
    private data class Generation(val identity:Identity,val cipherHash:String,val indexHash:String,val authenticated:Boolean)
    private enum class Step { STAGE, VERIFY_PAYLOAD, DURABLE_INDEX, DURABLE_RECEIPT, PROMOTE_ROOT, REOPEN_VERIFY, DELETE_SOURCE }
    private val source=Identity("test-source",3,"same-item-id",7)
    private val destination=Identity("test-destination",4,"same-item-id",8)
    private val generation=Generation(destination,"cipher-hash","index-hash",true)
    private val receipt=Receipt("test-operation",source,destination,"cipher-hash","index-hash")

    private class Model(private val source:Identity,private val expected:Receipt) {
        var sourcePresent=true; private set
        var staged=false; private set
        var payloadVerified=false; private set
        var durableGeneration:Generation?=null; private set
        var durableReceipt:Receipt?=null; private set
        var selectedRoot:Identity?=null; private set
        var reopened=false; private set
        fun step(step:Step,candidate:Generation,receipt:Receipt,operationCurrent:Boolean=true) {
            when(step) {
                Step.STAGE -> staged=true
                Step.VERIFY_PAYLOAD -> { check(staged && candidate.authenticated); payloadVerified=true }
                Step.DURABLE_INDEX -> { check(payloadVerified && candidate.authenticated); durableGeneration=candidate }
                Step.DURABLE_RECEIPT -> { check(durableGeneration!=null); durableReceipt=receipt }
                Step.PROMOTE_ROOT -> {
                    check(operationCurrent && matches())
                    selectedRoot=checkNotNull(durableGeneration).identity
                }
                Step.REOPEN_VERIFY -> { check(selectedRoot==candidate.identity && candidate.authenticated && matches()); reopened=true }
                Step.DELETE_SOURCE -> { check(operationCurrent && reopened && matches() && selectedRoot==expected.destination); sourcePresent=false }
            }
        }
        private fun matches():Boolean {
            val g=durableGeneration ?: return false
            val r=durableReceipt ?: return false
            return r==expected && r.source==source && r.destination==g.identity && r.cipherHash==g.cipherHash && r.indexHash==g.indexHash && g.authenticated
        }
        fun verifiedDestinationPresent()=reopened && matches() && selectedRoot==expected.destination
    }

    @Test fun interruptionAfterEveryProposedJournalStepKeepsAVerifiedCopyAndRetryCanDuplicateSafely() {
        for(stop in Step.entries.indices) {
            val disk=Model(source,receipt)
            Step.entries.take(stop+1).forEach { disk.step(it,generation,receipt) }
            assertTrue("failure after ${Step.entries[stop]} lost sole copy",disk.sourcePresent || disk.verifiedDestinationPresent())
            if(stop<Step.DELETE_SOURCE.ordinal) assertTrue(disk.sourcePresent)
            // Idempotent durable replay can leave a duplicate; deletion requires full durable receipt/root/reopen.
            Step.entries.forEach { disk.step(it,generation,receipt) }
            assertTrue(disk.verifiedDestinationPresent()); assertFalse(disk.sourcePresent)
        }
    }

    @Test fun mismatchedReceiptGenerationOperationNamespaceRevisionOrHashCannotSelectRootOrDelete() {
        val alternatives=listOf(receipt.copy(operation="other-operation"),receipt.copy(source=source.copy(generation=2)),
            receipt.copy(destination=destination.copy(container="test-other-container")),receipt.copy(destination=destination.copy(revision=7)),
            receipt.copy(cipherHash="corrupt"),receipt.copy(indexHash="corrupt"))
        for(bad in alternatives) {
            val disk=Model(source,receipt)
            Step.entries.take(Step.PROMOTE_ROOT.ordinal).forEach { disk.step(it,generation,bad) }
            assertThrows(IllegalStateException::class.java) { disk.step(Step.PROMOTE_ROOT,generation,bad) }
            assertThrows(IllegalStateException::class.java) { disk.step(Step.DELETE_SOURCE,generation,bad) }
            assertNull(disk.selectedRoot); assertTrue(disk.sourcePresent)
        }
    }

    @Test fun validReceiptIsNotAuthorityAndCorruptRootDoesNotAuthorizeDeletion() {
        val disk=Model(source,receipt)
        Step.entries.take(Step.PROMOTE_ROOT.ordinal).forEach { disk.step(it,generation,receipt) }
        assertThrows(IllegalStateException::class.java) { disk.step(Step.PROMOTE_ROOT,generation,receipt,operationCurrent=false) }
        disk.step(Step.PROMOTE_ROOT,generation,receipt)
        assertThrows(IllegalStateException::class.java) { disk.step(Step.REOPEN_VERIFY,generation.copy(authenticated=false),receipt) }
        assertThrows(IllegalStateException::class.java) { disk.step(Step.DELETE_SOURCE,generation,receipt) }
        disk.step(Step.REOPEN_VERIFY,generation,receipt)
        assertThrows(IllegalStateException::class.java) { disk.step(Step.DELETE_SOURCE,generation,receipt,operationCurrent=false) }
        assertTrue(disk.sourcePresent)
    }
}
