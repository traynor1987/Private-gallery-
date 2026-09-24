package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class AiEditPipelineTest {
    private class Provider : AiImageEditProvider {
        override val id = "test-provider"
        override val displayName = "Test provider"
        override val capabilities = setOf(AiCapability.GENERATIVE_EDIT, AiCapability.OBJECT_REMOVAL)
        var request: AiEditRequest? = null
        var response = byteArrayOf(7, 8)
        var failure: Exception? = null
        override suspend fun edit(request: AiEditRequest): ByteArray {
            this.request = request
            failure?.let { throw it }
            return response
        }
    }
    @Test fun providerReceivesSanitizedSelectionOnlyAndBuffersAreWiped() = runBlocking {
        val provider = Provider()
        var sanitizations = 0
        val pipeline = AiEditPipeline({ input -> sanitizations++; input.map { (it + 1).toByte() }.toByteArray() })
        val original = byteArrayOf(1, 2)
        val result = pipeline.generate(provider, true, original, AiParameters(prompt = "change colour"))
        assertEquals(2, sanitizations)
        assertArrayEquals(byteArrayOf(8, 9), result)
        assertArrayEquals(byteArrayOf(0, 0), provider.request!!.image)
        assertArrayEquals(byteArrayOf(0, 0), provider.response)
        assertArrayEquals(byteArrayOf(1, 2), original)
        assertEquals("change colour", provider.request!!.parameters.prompt)
    }
    @Test fun consentAndConfigurationBlockBeforeReadingImage() = runBlocking {
        val p = Provider(); var sanitized = false
        val pipeline = AiEditPipeline({ sanitized = true; it.copyOf() })
        try { pipeline.generate(null, true, byteArrayOf(1), AiParameters(prompt = "edit")); fail() } catch (_: AiEditFailure) { }
        try { pipeline.generate(p, false, byteArrayOf(1), AiParameters(prompt = "edit")); fail() } catch (_: AiEditFailure) { }
        assertFalse(sanitized); assertNull(p.request)
    }
    @Test fun cancellationAndFailureWipeOutboundData() = runBlocking {
        for (error in listOf(CancellationException(), IllegalStateException())) {
            val p = Provider().apply { failure = error }
            try { AiEditPipeline({ it.copyOf() }).generate(p, true, byteArrayOf(9), AiParameters(prompt = "edit")); fail() } catch (_: Exception) { }
            assertArrayEquals(byteArrayOf(0), p.request!!.image)
        }
    }
    @Test fun unsupportedCapabilityDoesNotContactProvider() = runBlocking {
        val p = Provider()
        try { AiEditPipeline({ it.copyOf() }).generate(p, true, byteArrayOf(9), AiParameters(AiCapability.OUTPAINT, "expand")); fail() } catch (_: AiEditFailure) { }
        assertNull(p.request)
    }
}
