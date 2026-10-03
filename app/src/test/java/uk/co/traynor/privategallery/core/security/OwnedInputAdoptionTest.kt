package uk.co.traynor.privategallery.core.security

import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.runScopedImport

class OwnedInputAdoptionTest {
    @Test fun importAdoptsExactOriginalPairWithOnlyTwoSlotsWithoutForwardingReleaseWorker() = isolated { authority, guard, _ ->
        val holds = (1..14).map { guard.connection { Connection() } }
        val storage = Any(); val disconnected = CountDownLatch(1); var created = 0; var committed = 0
        val connection = object : Connection() {
            override fun disconnect() { assertFalse(Thread.holdsLock(storage)); super.disconnect(); disconnected.countDown() }
            override fun getInputStream(): InputStream = object : ByteArrayInputStream(byteArrayOf(42)) {
                override fun close() { assertFalse(Thread.holdsLock(storage)); assertTrue(disconnected.await(5, TimeUnit.SECONDS)) }
            }
        }
        try {
            val result = runScopedImport(guard, storage, { error("raw callback must not run") }, { it.read() }, {
                assertEquals(1, connection.closes); committed++; it
            }, ownedSource = { created++; guard.connectedOwnedInput({ connection }) })
            assertEquals(42, result); assertEquals(1, created); assertEquals(1, committed)
        } finally { holds.forEach { it.close() } }
    }
    @Test fun mismatchedGuardOnSameOperationRejectsAndRetiresExactOriginalBeforePrepare() = isolated { _, guard, op ->
        val native = Connection(); val handle = guard.connectedOwnedInput({ native }); var prepared = 0; var committed = 0
        assertThrows(IllegalStateException::class.java) {
            runScopedImport(ScopedIoGuard(op, PrimaryScope.WRITE), Any(), { error("raw") }, { prepared++; it.read() }, { committed++ }, ownedSource = { handle })
        }
        assertEquals(0, prepared); assertEquals(0, committed); assertTrue(native.closed.await(5, TimeUnit.SECONDS)); assertEquals(1,native.closes)
    }
    @Test fun directHandleRetirementDeniesCachedFacadeReadsAndWipesDestinationSlice() = isolated { _, guard, _ ->
        val handle = guard.connectedOwnedInput({ Connection() }); val input = handle.adopt(guard); handle.close()
        assertThrows(IllegalStateException::class.java) { input.available() }
        assertThrows(IllegalStateException::class.java) { input.markSupported() }
        val out = ByteArray(4) { 9 }; assertThrows(IllegalStateException::class.java) { input.read(out,1,2) }
        assertArrayEquals(byteArrayOf(9,0,0,9),out)
    }
    @Test fun staleHandleRejectsPreparationAndSelection() = isolated { _, guard, _ ->
        val handle = guard.connectedOwnedInput({ Connection() }); handle.close(); var commits = 0
        assertThrows(IllegalStateException::class.java) {
            runScopedImport(guard, Any(), { error("raw") }, { error("prepare") }, { commits++ }, ownedSource = { handle })
        }
        assertEquals(0,commits)
    }
    @Test fun failedNativeInputCannotCommitOrReturnPairQuota() = isolated { authority, guard, _ ->
        var commits = 0
        assertThrows(IOException::class.java) {
            runScopedImport(guard, Any(), { error("raw") }, { it.read() }, { commits++ }, ownedSource = {
                guard.connectedOwnedInput({ Connection() }, open = { object : ByteArrayInputStream(byteArrayOf(1)) {
                    override fun close(): Unit = throw IOException("synthetic native close failure")
                } })
            })
        }
        assertEquals(0,commits); authority.revoke(); assertFalse(authority.cleanupComplete)
        val key = ByteArray(32) { 5 }; assertThrows(IllegalStateException::class.java) { authority.open(key) }; assertArrayEquals(ByteArray(32),key)
    }
    @Test fun forwardingCancellationViewPreservesOriginalPairAndNoExtraAdmission() = isolated { _, guard, _ ->
        val holds = (1..14).map { guard.connection { Connection() } }; var cancelled = false; var commits = 0
        try {
            val result = runScopedImport(guard, Any(), { error("raw") }, { it.read() }, { commits++; it }, ownedSource = {
                val original = guard.connectedOwnedInput({ Connection() })
                original.forward(object : java.io.FilterInputStream(original.stream) {
                    override fun read(): Int { if (cancelled) throw IOException("cancelled"); return super.read() }
                })
            })
            assertEquals(42,result); assertEquals(1,commits)
            cancelled = true
        } finally { holds.forEach { it.close() } }
    }
    private open class Connection : HttpURLConnection(URL("https://invalid.example/")) {
        @Volatile var closes = 0; val closed = CountDownLatch(1)
        override fun disconnect() { closes++; closed.countDown() }
        override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf(42))
        override fun usingProxy() = false
        override fun connect() = Unit
    }
    private fun isolated(test: (PrimarySessionAuthority, ScopedIoGuard, PrimaryOperation) -> Unit) {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32)); val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        try { test(authority,ScopedIoGuard(op,PrimaryScope.WRITE),op) } finally { op.close(); authority.revoke() }
    }
}
