package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class RecoveryConfirmationAdapterTest {
    @Test fun reconstructedPendingSetupRequiresReentryAndAuthenticatedRestartPreservesVaultKey() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "phase0-recovery-${java.util.UUID.randomUUID()}"
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("$namespace-$name", mode)
        }
        val key = ByteArray(32) { 27 }
        val authority = PrimarySessionAuthority()
        var first: CharArray? = null
        var second: CharArray? = null
        try {
            authority.open(key.copyOf())
            checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())).use { operation ->
                first = RecoveryVaultKeyStore(context).create(operation.key, operation::commit)
            }
            authority.revoke()
            val reopened = RecoveryVaultKeyStore(context)
            assertEquals(RecoverySetupState.PENDING_CONFIRMATION, reopened.setupState)
            assertFalse(reopened.isConfigured)
            assertThrows(IllegalStateException::class.java) { reopened.exportEnvelope() }
            assertThrows(Exception::class.java) { reopened.unlock(first!!.copyOf()) }
            authority.open(key.copyOf())
            checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())).use { operation ->
                second = reopened.restartPending(operation.key, operation::commit)
                assertThrows(Exception::class.java) { reopened.confirm(first!!.copyOf(), operation.key, operation::commit) }
                assertEquals(RecoverySetupState.PENDING_CONFIRMATION, reopened.setupState)
                reopened.confirm(second!!.copyOf(), operation.key, operation::commit)
            }
            val confirmed = RecoveryVaultKeyStore(context)
            assertTrue(confirmed.isConfigured)
            assertTrue(confirmed.isPossessionVerified)
            val recovered = confirmed.unlock(second!!.copyOf())
            try { assertArrayEquals(key, recovered) } finally { recovered.fill(0) }
            assertFalse(app.getSharedPreferences("$namespace-vault-recovery-envelope", Context.MODE_PRIVATE).all.values.any {
                it == first!!.concatToString() || it == second!!.concatToString()
            })
        } finally {
            authority.revoke(); key.fill(0); first?.fill('\u0000'); second?.fill('\u0000')
            app.getSharedPreferences("$namespace-vault-recovery-envelope", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
