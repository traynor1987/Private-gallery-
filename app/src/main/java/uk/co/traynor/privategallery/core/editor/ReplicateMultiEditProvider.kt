package uk.co.traynor.privategallery.core.editor

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class ReplicateMultiEditProvider(
    private val readCredential: () -> ByteArray?,
    private val selectedModel: () -> ReplicateEditModel,
    private val seedream: ReplicateSeedreamProvider,
    private val api: ReplicateModelEditApi,
    private val prepareImage: (ByteArray) -> ByteArray,
    private val createMask: (ByteArray, List<MaskStroke>) -> ByteArray = OpenAiMaskRenderer::renderReplicateFill,
) : AiImageEditProvider {
    override val id = ReplicateSeedreamProvider.ID // Reuses the existing Keystore entry and consent.
    override val displayName = "Replicate"
    override val modelId: String get() = selectedModel().modelId
    override val capabilities: Set<AiCapability> get() = selectedModel().tools
    private val active = AtomicBoolean(true)
    private val jobs = ConcurrentHashMap.newKeySet<Job>()
    override fun invalidate() {
        active.set(false)
        seedream.invalidate()
        jobs.forEach { it.cancel(CancellationException("AI configuration removed")) }
    }
    override suspend fun edit(request: AiEditRequest): ByteArray {
        if (!active.get()) throw AiEditFailure("AI configuration was removed.")
        val model = selectedModel()
        if (request.parameters.capability !in model.tools || request.parameters.aspect != null ||
            (model != ReplicateEditModel.FILL && request.parameters.strokes.isNotEmpty()) ||
            (model == ReplicateEditModel.FILL && request.parameters.strokes.isEmpty()))
            throw AiEditFailure("This model does not support the selected edit.")
        if (model == ReplicateEditModel.SEEDREAM) return seedream.edit(request)
        val job = currentCoroutineContext().job
        jobs.add(job)
        var token: ByteArray? = null
        var prepared: ByteArray? = null
        var mask: ByteArray? = null
        var result: ByteArray? = null
        try {
            withContext(Dispatchers.IO) {
                token = readCredential() ?: throw AiEditFailure("Set up Replicate in AI editing settings.")
                ensureActive()
                prepared = prepareImage(request.image)
                if (model == ReplicateEditModel.FILL) mask = createMask(prepared!!, request.parameters.strokes)
            }
            currentCoroutineContext().ensureActive()
            result = api.edit(token!!, model, prepared!!, request.parameters.prompt, mask)
            currentCoroutineContext().ensureActive()
            return result!!.also { result = null }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: AiEditFailure) { throw failure
        } catch (_: Exception) { throw AiEditFailure("AI editing could not complete. Check the selected model and try again.")
        } finally { jobs.remove(job); token?.fill(0); prepared?.fill(0); mask?.fill(0); result?.fill(0) }
    }
}
