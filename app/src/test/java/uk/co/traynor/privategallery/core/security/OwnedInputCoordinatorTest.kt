package uk.co.traynor.privategallery.core.security

import uk.co.traynor.privategallery.core.vault.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class OwnedInputCoordinatorTest {
    @Test fun actualCoordinatorKeepsCompleteOriginalManifestWithOnlyTwoSlots() = isolated { guard ->
        val holds = (1..14).map { guard.connection { Connection() } }; val native = Connection(); var consumed = 0
        try {
            val result = VaultImportCoordinator(sink(guard)).acquire(VaultImportSource("synthetic.bin","application/octet-stream",
                { error("raw") }, onConsumed = { consumed++ }, openOwnedStream = { original -> original.connectedOwnedInput({ native }) }))
            assertTrue(result is ImportResult.Imported); assertEquals(1, native.closed); assertEquals(1, consumed)
        } finally { holds.forEach { it.close() } }
    }
    @Test fun actualCoordinatorCancellationForwarderDeniesSelectionAndRetiresOriginal() = isolated { guard ->
        var cancelled = false; val native = object : Connection() {
            override fun getInputStream(): InputStream = object : ByteArrayInputStream(byteArrayOf(1,2)) {
                override fun read(): Int { cancelled = true; return super.read() }
            }
        }; var consumed = 0; var commits = 0
        val sink = object : VaultImportSink {
            override fun importVerified(source: VaultImportSource): ImportResult = runScopedImport(guard,Any(), { error("raw") }, {
                assertEquals(1,it.read()); it.read()
            }, { commits++; imported() }, ownedSource = { source.openOwnedStream!!(guard) })
        }
        assertThrows(IOException::class.java) { VaultImportCoordinator(sink).acquire(VaultImportSource("synthetic.bin","application/octet-stream",
            { error("raw") }, isCancelled = { cancelled }, onConsumed = { consumed++ }, openOwnedStream = { it.connectedOwnedInput({ native }) })) }
        assertEquals(0,commits); assertEquals(1,native.closed); assertEquals(1,consumed)
    }
    @Test fun cancelledBulkForwarderWipesOnlyRequestedDestinationSlice() = isolated { guard ->
        var cancelled = false; val native = Connection(); val destination = ByteArray(4) { 9 }
        val sink = object : VaultImportSink {
            override fun importVerified(source: VaultImportSource): ImportResult = runScopedImport(guard,Any(), { error("raw") }, { input ->
                cancelled = true
                assertThrows(IOException::class.java) { input.read(destination,1,2) }
                assertArrayEquals(byteArrayOf(9,0,0,9),destination)
                throw IOException("synthetic stop before selection")
            }, { error("commit") }, ownedSource = { source.openOwnedStream!!(guard) })
        }
        assertThrows(IOException::class.java) { VaultImportCoordinator(sink).acquire(VaultImportSource("synthetic.bin","application/octet-stream",
            { error("raw") },isCancelled = { cancelled },openOwnedStream = { it.connectedOwnedInput({ native }) })) }
        assertEquals(1,native.closed)
    }
    @Test fun actualCoordinatorRejectsForeignGuardAndRetiresItsOriginal() = isolated { guard ->
        val foreignAuthority = testPrimaryAuthority(); foreignAuthority.open(ByteArray(32)); val op = foreignAuthority.operationOrNull(PrimaryScope.entries.toSet())!!
        val foreign = ScopedIoGuard(op,PrimaryScope.WRITE); val native = Connection(); val handle = foreign.connectedOwnedInput({ native })
        try {
            assertThrows(IllegalStateException::class.java) { VaultImportCoordinator(sink(guard)).acquire(VaultImportSource("synthetic.bin","application/octet-stream",
                { error("raw") }, openOwnedStream = { handle })) }
            handle.close(); assertEquals(1,native.closed)
        } finally { op.close(); foreignAuthority.revoke() }
    }
    private fun sink(guard: ScopedIoGuard) = object : VaultImportSink {
        override fun importVerified(source: VaultImportSource): ImportResult = runScopedImport(guard,Any(), { error("raw") }, {
            assertEquals(42,it.read())
        }, { imported() }, ownedSource = { source.openOwnedStream!!(guard) })
    }
    private fun imported(): ImportResult = ImportResult.Imported(VaultItem("synthetic","application/octet-stream","synthetic.bin",0,1,
        ByteArray(32),ByteArray(12),VaultItemState.COMPLETE))
    private open class Connection : HttpURLConnection(URL("https://invalid.example/")) {
        @Volatile var closed = 0
        override fun disconnect() { closed++ }
        override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf(42))
        override fun usingProxy() = false
        override fun connect() = Unit
    }
    private fun isolated(test: (ScopedIoGuard) -> Unit) {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32)); val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        try { test(ScopedIoGuard(op,PrimaryScope.WRITE)) } finally { op.close(); authority.revoke() }
    }
}
