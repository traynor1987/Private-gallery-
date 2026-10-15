package uk.co.traynor.privategallery.core.security

import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PlaintextDeliveryTest {
    @Test fun queuedDeliveryRunsOutsideAuthorityAndRetiresStaleResults() {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val op=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        val source=op.createOwnedPlaintext { byteArrayOf(4) };var posted:Runnable?=null;var calls=0
        op.publish { postOwnedPlaintext({posted=it;true},{op.isCurrent},Result.success(source)) { result ->
            result.getOrThrow().useBytes { calls++ }
        } }
        assertEquals(0,calls);posted!!.run();assertEquals(1,calls)
        source.close();assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
        val late=op.createOwnedPlaintext { byteArrayOf(5) }
        postOwnedPlaintext({posted=it;true},{op.isCurrent},Result.success(late)) { calls++ }
        a.revoke();posted!!.run();assertEquals(1,calls);assertTrue(late.retirement.await(5,TimeUnit.SECONDS));op.close()
    }
    @Test fun rejectedPostRetiresAndThrowingConsumerCannotLoseOriginal() {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val op=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        val source=op.createOwnedPlaintext { byteArrayOf(7) }
        postOwnedPlaintext({false},{true},Result.success(source)) { fail("Rejected post delivered") }
        assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
        val throwing=op.createOwnedPlaintext { byteArrayOf(8) };var posted:Runnable?=null
        postOwnedPlaintext({posted=it;true},{true},Result.success(throwing)) { error("Synthetic consumer failure") }
        assertThrows(IllegalStateException::class.java) { posted!!.run() };assertTrue(throwing.retirement.await(5,TimeUnit.SECONDS))
        op.close();a.revoke()
    }
}
