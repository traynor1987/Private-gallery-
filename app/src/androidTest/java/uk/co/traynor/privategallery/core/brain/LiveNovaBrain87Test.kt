package uk.co.traynor.privategallery.core.brain

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Actual existing public VPS, ordinary Android TLS; no owner credential or private payload. */
class LiveNovaBrain87Test {
    @Test fun actualVpsValidatesHttpsAndRejectsSyntheticCredentialsWithoutClaimingAuthenticatedConnectivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".hiddenprototype"))
        assertEquals(37, android.os.Build.VERSION.SDK_INT)
        val config = NovaBrainConfiguration("https://brain.jamestraynor.co.uk", "nova-build87-invalid-token-"+java.util.UUID.randomUUID())
        val status = NovaBrainConnection().check(config)
        assertEquals("Real VPS health/TLS or unauthenticated status differs: ${status.message}", BrainConnectionPhase.AUTHENTICATION_REQUIRED, status.phase)
        assertFalse(status.analysisEnabledOnServer)
        assertFalse(status.message.contains(config.token))
    }
}
