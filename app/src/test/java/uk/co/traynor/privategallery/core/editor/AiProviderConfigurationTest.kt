package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiProviderConfigurationTest {
    private class MemoryCredentials : AiCredentials {
        var saved: ByteArray? = null
        override fun isConfigured() = saved != null
        override fun read() = saved?.copyOf()
        override fun save(credential: ByteArray) { saved = credential.copyOf() }
        override fun clear() { saved?.fill(0); saved = null }
    }
    private fun config(store: AiCredentials, check: suspend (ByteArray) -> Unit = {}) = AiProviderConfiguration(store, check) { read ->
        ReplicateSeedreamProvider(read, ReplicateSeedreamApi(object : AiHttpTransport {
            override suspend fun execute(request: AiHttpRequest): AiHttpResponse = error("No live network in tests")
        }), { it.copyOf() })
    }
    @Test fun onlyVerifiedCredentialIsStoredAndProviderEnabled() = runBlocking {
        val store = MemoryCredentials(); val c = config(store)
        assertNull(c.provider)
        val token = "synthetic-token".toByteArray()
        c.connect(token)
        assertTrue(token.all { it == 0.toByte() })
        assertEquals(AiConnectionStatus.CONNECTED, c.status.value)
        assertNotNull(c.provider)
        assertEquals(setOf(AiCapability.GENERATIVE_EDIT), c.provider!!.capabilities)
        assertNotNull(config(store).provider)
    }
    @Test fun failedReplacementPreservesOldCredentialAndDoesNotRevealError() = runBlocking {
        val store = MemoryCredentials().apply { save("old-token".toByteArray()) }
        val c = config(store) { throw AiEditFailure("Token not accepted.") }
        val token = "new-token".toByteArray()
        try { c.connect(token); fail() } catch (_: AiEditFailure) { }
        assertEquals("old-token", store.saved!!.toString(Charsets.UTF_8))
        assertTrue(token.all { it == 0.toByte() })
    }
    @Test fun cancellationCannotPersistConfiguration() = runBlocking {
        val store = MemoryCredentials(); val started = CompletableDeferred<Unit>()
        val c = config(store) { started.complete(Unit); awaitCancellation() }
        val token = "synthetic-token".toByteArray()
        val job = launch { c.connect(token) }; started.await(); job.cancelAndJoin()
        assertNull(store.saved); assertNull(c.provider); assertTrue(token.all { it == 0.toByte() })
    }
    @Test fun removalInvalidatesOldProviderAndClearsConsent() = runBlocking {
        val store = MemoryCredentials(); var cleared = false
        val c = config(store); c.connect("synthetic-token".toByteArray()); val old = c.provider!!
        c.remove { cleared = true }
        assertNull(c.provider); assertNull(store.saved); assertTrue(cleared)
        try { old.edit(AiEditRequest(byteArrayOf(1),AiParameters(prompt="edit"))); fail() } catch (_: AiEditFailure) { }
    }
    @Test fun removeDuringTestCannotResurrectSavedKey() = runBlocking {
        val store = MemoryCredentials(); val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val c = config(store) { started.complete(Unit); finish.await() }
        val job = launch { try { c.connect("synthetic-token".toByteArray()) } catch (_: AiEditFailure) { } }
        started.await(); c.remove {}; finish.complete(Unit); job.join()
        assertNull(store.saved); assertNull(c.provider)
    }
}
