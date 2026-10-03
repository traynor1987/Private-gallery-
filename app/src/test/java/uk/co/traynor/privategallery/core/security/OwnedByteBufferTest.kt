package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test

class OwnedByteBufferTest {
    @Test fun normalRetirementWipesTheExactActualArrayBeforeAcknowledgement() = isolated { _, guard ->
        val root = buffer(guard)
        lateinit var actual: ByteArray
        assertTrue(root.value.value.useBytes { actual = it; it.fill(7); true })
        guard.retire(root)
        assertTrue(root.retirement.isComplete)
        assertTrue(actual.all { it == 0.toByte() })
    }
    @Test fun retiredBufferCannotInvokeAnotherConsumerWithStillLiveLease() = isolated { _, guard ->
        val root = buffer(guard); guard.retire(root); guard.check()
        var calls = 0
        assertThrows(IllegalStateException::class.java) { root.value.value.useBytes { calls++;true } }
        assertEquals(0,calls)
    }
    @Test fun originalRevocationInsideConsumerDeniesResultAndWipesAfterReaderReturns() = isolated { authority, guard ->
        val root = buffer(guard); lateinit var actual: ByteArray
        assertThrows(IllegalStateException::class.java) {
            root.value.value.useBytes { actual = it; it.fill(9); authority.revoke(); true }
        }
        guard.retire(root)
        assertTrue(actual.all { it == 0.toByte() }); assertTrue(authority.cleanupComplete)
    }
    @Test fun invalidBufferBoundRetiresOriginalWithoutCreatingAnActualArray() = isolated { authority, guard ->
        for (size in listOf(-1,0,64 * 1024 + 1)) {
            assertThrows(IllegalArgumentException::class.java) { buffer(guard,size) }
        }
        authority.revoke()
        awaitCleanup(authority)
    }
    @Test fun closedOriginalDeniesBufferFactoryBeforeConstruction() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = authority.operationOrNull(setOf(PrimaryScope.READ))!!;val guard = ScopedIoGuard(operation,PrimaryScope.READ)
        operation.close(); var calls = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                guard.createOwned(OwnedResourceManifest.io("buffer")) {
                    create("buffer", { actual: OwnedByteBuffer -> actual.close() }) { calls++;OwnedByteBuffer(guard,16) }
                }
            }
            assertEquals(0,calls)
        } finally { authority.revoke() }
    }
    @Test fun consumerFailureStillWipesActualArrayUnderOriginalNormalRetirement() = isolated { _, guard ->
        val root = buffer(guard); lateinit var actual: ByteArray
        try {
            assertThrows(java.io.IOException::class.java) {
                root.value.value.useBytes { actual = it; it.fill(5); throw java.io.IOException("synthetic reader failure") }
            }
        } finally { guard.retire(root) }
        assertTrue(actual.all { it == 0.toByte() }); assertTrue(root.retirement.isComplete)
    }
    private fun buffer(guard: ScopedIoGuard,size: Int = 32) = guard.createOwned(OwnedResourceManifest.io("buffer")) {
        create("buffer", { actual: OwnedByteBuffer -> actual.close() }) { OwnedByteBuffer(guard,size) }
    }
    private fun isolated(test: (PrimarySessionAuthority,ScopedIoGuard) -> Unit) {
        val authority = testPrimaryAuthority();authority.open(ByteArray(32) { 5 })
        val operation = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        try { test(authority,ScopedIoGuard(operation,PrimaryScope.READ)) }
        finally { operation.close();authority.revoke() }
    }
    private fun awaitCleanup(authority: PrimarySessionAuthority) {
        val deadline = System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime()<deadline) Thread.yield()
        assertTrue(authority.cleanupComplete)
    }
}
