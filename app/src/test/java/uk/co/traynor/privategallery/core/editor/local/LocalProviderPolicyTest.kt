package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*

class LocalProviderPolicyTest {
    private val capable = DeviceResources(36, true, true, 24 * ModelCatalog.GIB, 12 * ModelCatalog.GIB, false, false)
    @Test fun hardwareAndInstallationAreIndependentGates() {
        val model = ModelCatalog.advanced
        assertEquals(LocalAvailability.MODEL_NOT_INSTALLED, LocalCapabilityPolicy.evaluate(model, capable, false))
        assertEquals(LocalAvailability.SUPPORTED_SLOWER, LocalCapabilityPolicy.evaluate(model, capable, true))
        assertEquals(LocalAvailability.INSUFFICIENT_RAM, LocalCapabilityPolicy.evaluate(model, capable.copy(totalRam = 4 * ModelCatalog.GIB), true))
        assertEquals(LocalAvailability.UNSUPPORTED_CHIPSET, LocalCapabilityPolicy.evaluate(model, capable.copy(abiSupported = false), true))
        assertEquals(LocalAvailability.RUNTIME_NOT_AVAILABLE, LocalCapabilityPolicy.evaluate(model, capable.copy(runtimeAvailable = false), true))
        assertEquals(LocalAvailability.THERMAL_LIMIT, LocalCapabilityPolicy.evaluate(model, capable.copy(tooHot = true), true))
        assertEquals(LocalAvailability.MEMORY_PRESSURE, LocalCapabilityPolicy.evaluate(model, capable.copy(availableRam = ModelCatalog.GIB), true))
    }
    private class Provider(override val processing: AiProcessing, override val ready: Boolean = true) : AiImageEditProvider {
        override val id = "fixture"
        override val displayName = "Fixture"
        override val capabilities = setOf(AiCapability.RESTYLE)
        var calls = 0
        var input: ByteArray? = null
        override suspend fun edit(request: AiEditRequest): ByteArray { calls++; input = request.image; return byteArrayOf(4, 5) }
    }
    @Test fun autoSelectsInstalledLocalWithoutRemoteConsent() = runBlocking {
        val local = Provider(AiProcessing.ON_DEVICE)
        val cloud = Provider(AiProcessing.CLOUD)
        val auto = AutoAiProvider({ listOf(local) }, { cloud })
        val original = byteArrayOf(1, 2, 3)
        val result = AiEditPipeline({ it.copyOf() }).generate(auto, false, original, AiParameters(AiCapability.RESTYLE, "fixture"))
        assertArrayEquals(byteArrayOf(4, 5), result)
        assertArrayEquals(byteArrayOf(1, 2, 3), original)
        assertTrue(local.input!!.all { it == 0.toByte() })
        assertEquals(1, local.calls); assertEquals(0, cloud.calls)
    }
    @Test fun rememberedConsentCannotSilentlyApproveAutoCloudFallback() = runBlocking {
        val local = Provider(AiProcessing.ON_DEVICE, false)
        val cloud = Provider(AiProcessing.CLOUD)
        val auto = AutoAiProvider({ listOf(local) }, { cloud })
        val pipeline = AiEditPipeline({ it.copyOf() })
        val params = AiParameters(AiCapability.RESTYLE, "fixture")
        assertTrue(runCatching { pipeline.generate(auto, true, byteArrayOf(1), params) }.exceptionOrNull() is AiEditFailure)
        assertEquals(0, cloud.calls)
        pipeline.generate(auto, true, byteArrayOf(1), params, cloudFallbackConfirmed = true)
        assertEquals(1, cloud.calls)
    }
    @Test fun autoWithoutInstalledModelsOrCloudIsNotConfigured() {
        val absent = object : AiImageEditProvider {
            override val id = "absent"
            override val displayName = "Absent fixture"
            override val configured = false
            override val ready = false
            override val processing = AiProcessing.ON_DEVICE
            override val capabilities = setOf(AiCapability.RESTYLE)
            override suspend fun edit(request: AiEditRequest): ByteArray = error("Must not run")
        }
        val auto = AutoAiProvider({ listOf(absent) }, { null })
        assertFalse(auto.configured)
        assertFalse(auto.ready)
        assertNull(auto.resolve(AiCapability.RESTYLE))
    }
    @Test fun downloadRedirectsCannotEscapeReviewedHttpsHosts() {
        assertTrue(ModelHttps.allowed(java.net.URI("https://cas-bridge.xethub.hf.co/model")))
        listOf("http://huggingface.co/a", "https://huggingface.co.evil.test/a", "https://evil.test/a", "https://user@huggingface.co/a", "https://huggingface.co:8080/a").forEach {
            assertFalse(ModelHttps.allowed(java.net.URI(it)))
        }
    }
}
