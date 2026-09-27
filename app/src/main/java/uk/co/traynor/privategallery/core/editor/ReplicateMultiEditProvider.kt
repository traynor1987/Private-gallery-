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
import kotlinx.coroutines.flow.MutableStateFlow

class ReplicateMultiEditProvider(
    private val readCredential: () -> ByteArray?,
    private val selectedModel: () -> ReplicateEditModel,
    private val seedream: ReplicateSeedreamProvider,
    private val api: ReplicateModelEditApi,
    private val prepareImage: (ByteArray) -> ByteArray,
    private val createMask: (ByteArray, List<MaskStroke>) -> ByteArray = OpenAiMaskRenderer::renderReplicateFill,
    private val enhancement: PromptEnhancementStore? = null,
    private val proResolution: () -> String = { "1K" },
) : AiImageEditProvider {
    override val id = ReplicateSeedreamProvider.ID // Reuses the existing Keystore entry and consent.
    override val displayName = "Replicate"
    override val modelId: String get() = selectedModel().modelId
    override val capabilities: Set<AiCapability> get() = selectedModel().tools
    private val progressState = MutableStateFlow<String?>(null)
    override val progress: kotlinx.coroutines.flow.StateFlow<String?> get() = progressState
    private val diagnosticState = MutableStateFlow<ReplicatePredictionSnapshot?>(null)
    val predictionDiagnostic: kotlinx.coroutines.flow.StateFlow<ReplicatePredictionSnapshot?> get() = diagnosticState
    private val active = AtomicBoolean(true)
    private val jobs = ConcurrentHashMap.newKeySet<Job>()
    override fun invalidate() {
        active.set(false)
        diagnosticState.value = null
        seedream.invalidate()
        jobs.forEach { it.cancel(CancellationException("AI configuration removed")) }
    }
    override suspend fun edit(request: AiEditRequest): ByteArray {
        if (!active.get()) throw AiEditFailure("AI configuration was removed.")
        val model = selectedModel()
        diagnosticState.value = null
        if (request.parameters.capability !in model.tools || request.parameters.aspect != null ||
            (model != ReplicateEditModel.FILL && request.parameters.strokes.isNotEmpty()) ||
            (model == ReplicateEditModel.FILL && request.parameters.strokes.isEmpty()))
            throw AiEditFailure("This model does not support the selected edit.")
        val kind = if (model == ReplicateEditModel.FILL) PromptKind.MASK else PromptKind.EDIT
        val effective = try { enhancement?.effective(request.parameters.prompt, kind, model.modelId, request.parameters.enhancePrompt)
            ?: request.parameters.prompt } catch (failure: IllegalArgumentException) { throw AiEditFailure(failure.message ?: "Invalid prompt enhancement.") }
        if (model == ReplicateEditModel.SEEDREAM) {
            progressState.value = "Generating with Replicate…"
            return try { seedream.edit(request.copy(parameters = request.parameters.copy(prompt = effective))) }
            finally { progressState.value = null }
        }
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
            result = api.edit(token!!, model, prepared!!, effective, mask, resolution = proResolution()) { snapshot ->
                diagnosticState.value = snapshot
                progressState.value = when (snapshot.state) {
                    ReplicatePredictionState.SUBMITTING -> "Submitting to Replicate…"
                    ReplicatePredictionState.POLL_TIMEOUT, ReplicatePredictionState.POLL_NETWORK_FAILURE -> "Connection interrupted. Checking the same prediction…"
                    ReplicatePredictionState.PROVIDER_STILL_PROCESSING -> "Replicate is still processing this edit…"
                    ReplicatePredictionState.PROVIDER_SUCCEEDED -> "Receiving image…"
                    else -> "Generating…"
                }
            }
            currentCoroutineContext().ensureActive()
            return result!!.also { result = null }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: AiEditFailure) { throw failure
        } catch (_: Exception) { throw AiEditFailure("AI editing could not complete. Check the selected model and try again.")
        } finally { progressState.value = null; jobs.remove(job); token?.fill(0); prepared?.fill(0); mask?.fill(0); result?.fill(0) }
    }
}
