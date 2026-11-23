package uk.co.traynor.privategallery.intelligence

import androidx.compose.runtime.mutableStateOf
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class IntelligenceExchange(
    val question: String,
    val answer: String,
    val cloud: Boolean = false,
    val timestamp: Instant? = null,
    val ownerPreview: Boolean = false,
) {
    override fun toString() = "IntelligenceExchange(redacted)"
}

internal class IntelligenceConversation(private val hostScope: CoroutineScope? = null) {
    val history = mutableStateOf(emptyList<IntelligenceExchange>())
    val input = mutableStateOf("")
    val taskDate = mutableStateOf<LocalDate?>(null)
    val showCoverChat = mutableStateOf(false)
    val activeCloud = mutableStateOf<IntelligenceCloudRequest?>(null)
    val retryCloud = mutableStateOf<IntelligenceCloudRequest?>(null)
    val feedback = mutableStateOf<String?>(null)
    private var cloudJob: Job? = null
    private var unresolved: IntelligenceCloudRequest? = null

    fun retainedRequest(question: String, mode: IntelligenceCloudMode): IntelligenceCloudRequest? =
        sequenceOf(retryCloud.value, unresolved).filterNotNull().firstOrNull { it.question == question && it.mode == mode }

    fun append(question: String, answer: String, cloud: Boolean = false, preview: Boolean = false) {
        val exchange = IntelligenceExchange(question, answer, cloud, Instant.now(), preview)
        history.value = (history.value + exchange).takeLast(IntelligenceWorkspace.HISTORY_LIMIT)
    }

    fun sendCloud(
        request: IntelligenceCloudRequest,
        screenScope: CoroutineScope,
        assistant: IntelligenceAssistant,
        allowed: () -> Boolean,
    ) {
        if (activeCloud.value != null || !allowed()) return
        feedback.value = null
        retryCloud.value = null
        activeCloud.value = request
        val job = (hostScope ?: screenScope).launch(start = CoroutineStart.LAZY) {
            try {
                val reply = assistant.ask(request) { isActive && allowed() }
                ensureActive()
                if (!allowed() || activeCloud.value !== request) return@launch
                if (reply.retainIdentity) unresolved = request
                else if (reply.cloud && unresolved === request) unresolved = null
                if (reply.retryable) {
                    feedback.value = reply.message
                    retryCloud.value = request
                } else {
                    append(request.question, reply.message, reply.cloud,
                        preview = reply.cloud && request.mode == IntelligenceCloudMode.OWNER_PREVIEW)
                    try {
                        reply.acknowledge?.invoke()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        feedback.value = "The reply arrived. Duplicate protection for this request is still active."
                    }
                }
            } catch (cancelled: CancellationException) {
                if (isActive && activeCloud.value === request) {
                    feedback.value = "Cloud chat was interrupted. Local commands are available."
                    rememberRetry(request)
                }
                throw cancelled
            } catch (_: Exception) {
                if (activeCloud.value === request) {
                    feedback.value = "Nova Brain could not complete the request. Local commands are available."
                    rememberRetry(request)
                }
            } finally {
                if (activeCloud.value === request) {
                    activeCloud.value = null
                    cloudJob = null
                }
            }
        }
        cloudJob = job
        job.start()
    }

    fun cancelCloud(keepRetry: Boolean = true) {
        val request = activeCloud.value ?: return
        activeCloud.value = null
        cloudJob?.cancel()
        cloudJob = null
        if (keepRetry) {
            feedback.value = "Stopped waiting for the reply. The server may already have received the approved request."
            rememberRetry(request)
        }
    }

    private fun rememberRetry(request: IntelligenceCloudRequest) {
        if (request.mode == IntelligenceCloudMode.CONFIGURED_BRAIN) {
            retryCloud.value = request
            unresolved = request
        }
    }

    fun clear() {
        cancelCloud(keepRetry = false)
        history.value = emptyList()
        input.value = ""
        taskDate.value = null
        retryCloud.value = null
        unresolved = null
        feedback.value = null
    }
}
