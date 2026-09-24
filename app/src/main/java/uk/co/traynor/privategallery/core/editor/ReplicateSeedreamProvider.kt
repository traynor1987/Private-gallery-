package uk.co.traynor.privategallery.core.editor

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

class ReplicateSeedreamProvider(
    private val readCredential: () -> ByteArray?,
    private val api: ReplicateSeedreamApi,
    private val prepareImage: (ByteArray) -> ByteArray,
) : AiImageEditProvider {
    override val id = ID
    override val displayName = "Replicate · Seedream 4.5"
    // This model has no documented mask/alpha/outpaint contract. Do not advertise those tools.
    override val capabilities = setOf(AiCapability.GENERATIVE_EDIT)
    private val active = AtomicBoolean(true)
    private val jobs = ConcurrentHashMap.newKeySet<Job>()
    fun invalidate() { active.set(false); jobs.forEach { it.cancel(CancellationException("AI configuration removed")) } }
    override suspend fun edit(request: AiEditRequest): ByteArray = coroutineScope {
        if (!active.get()) throw AiEditFailure("AI configuration was removed. Set up the provider again.")
        if (request.parameters.capability !in capabilities || request.parameters.strokes.isNotEmpty() || request.parameters.aspect != null) throw AiEditFailure("This provider supports prompt-based editing only.")
        val job = currentCoroutineContext().job
        jobs.add(job)
        var token: ByteArray? = null
        var prepared: ByteArray? = null
        var result: ByteArray? = null
        try {
            if (!active.get()) throw AiEditFailure("AI configuration was removed.")
            withContext(Dispatchers.IO) {
                token = readCredential() ?: throw AiEditFailure("Set up Replicate in AI editing settings.")
                ensureActive()
                prepared = prepareImage(request.image)
            }
            ensureActive()
            withTimeout(90_000) { result = api.edit(token!!, prepared!!, request.parameters.prompt) }
            ensureActive()
            result!!.also { result = null }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: AiEditFailure) { throw failure
        } catch (_: Exception) { throw AiEditFailure("AI editing could not start. Check your provider configuration.")
        } finally { jobs.remove(job); token?.fill(0); prepared?.fill(0); result?.fill(0) }
    }
    companion object { const val ID = "replicate-seedream-45" }
}
