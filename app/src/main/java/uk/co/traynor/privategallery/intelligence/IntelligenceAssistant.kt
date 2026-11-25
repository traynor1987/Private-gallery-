package uk.co.traynor.privategallery.intelligence

import android.content.Context
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import uk.co.traynor.privategallery.core.brain.CloudFailure
import uk.co.traynor.privategallery.core.brain.CloudTeacherException
import uk.co.traynor.privategallery.core.brain.NovaBrainConfiguration
import uk.co.traynor.privategallery.core.brain.NovaBrainConnectionStore

internal enum class IntelligenceCloudMode { CONFIGURED_BRAIN, OWNER_PREVIEW }

internal class IntelligenceCloudRequest private constructor(
    val question: String,
    val payload: String,
    val mode: IntelligenceCloudMode,
    val requestId: String,
) {
    private val boundConfiguration = AtomicReference<NovaBrainConfiguration?>()
    val includesContext: Boolean get() = payload != question

    // Once selected, a retry cannot move this UUID to another server or owner account.
    fun bind(configuration: NovaBrainConfiguration): Boolean =
        boundConfiguration.compareAndSet(null, configuration) || boundConfiguration.get() == configuration

    override fun toString() = "IntelligenceCloudRequest(redacted)"

    companion object {
        const val QUESTION_LIMIT = 600
        const val PAYLOAD_LIMIT = 4096
        private const val CONTEXT_TURNS = 3

        fun prepare(question: String, history: List<IntelligenceExchange>, mode: IntelligenceCloudMode): IntelligenceCloudRequest {
            require(question.isNotBlank() && question.length <= QUESTION_LIMIT)
            val prefix = "Earlier cloud conversation for reference:\n"
            val current = "\n\nCurrent question from the owner:\n$question"
            val context = mutableListOf<String>()
            var remaining = PAYLOAD_LIMIT - prefix.length - current.length
            if (mode == IntelligenceCloudMode.CONFIGURED_BRAIN) {
                val eligible = history.asReversed().asSequence().filter { it.cloud && !it.ownerPreview }.take(CONTEXT_TURNS)
                for (turn in eligible) {
                    val excerpt = turn.answer.take(650).dropLastWhile(Char::isHighSurrogate)
                    val block = "Owner: ${turn.question}\nNova: $excerpt"
                    if (block.length + 2 > remaining) break
                    remaining -= block.length + 2
                    context.add(block)
                }
            }
            val payload = if (context.isEmpty()) question else prefix + context.asReversed().joinToString("\n\n") + current
            return IntelligenceCloudRequest(question, payload, mode, UUID.randomUUID().toString())
        }
    }
}

internal data class IntelligenceCloudAnswer(
    val message: String,
    val cloud: Boolean = false,
    val retryable: Boolean = false,
    val retainIdentity: Boolean = false,
    val acknowledge: (suspend () -> Unit)? = null,
) {
    override fun toString() = "IntelligenceCloudAnswer(redacted)"
}

internal interface IntelligenceAssistant {
    fun previewEnabled(): Boolean
    suspend fun check(mayContinue: () -> Boolean): String
    suspend fun ask(request: IntelligenceCloudRequest, maySend: () -> Boolean): IntelligenceCloudAnswer
}

internal class NovaIntelligenceAssistant(context: Context) : IntelligenceAssistant {
    private val context = context.applicationContext
    private val regular = NovaBrainConnectionStore(this.context)
    private val preview = OwnerPreviewStore(this.context)
    private val journal = IntelligenceRequestJournal.encrypted(this.context)

    override fun previewEnabled() = preview.isEnabled()

    override suspend fun check(mayContinue: () -> Boolean): String = runInterruptible(Dispatchers.IO) {
        if (!mayContinue() || !intelligencePrivateAllowed(context)) return@runInterruptible PAUSED
        try {
            if (previewEnabled()) {
                val connection = checkNotNull(preview.read())
                val status = OwnerPreviewClient().check(connection) {
                    mayContinue() && intelligencePrivateAllowed(context) && preview.isCurrent(connection)
                }
                val availability = when {
                    !status.armed -> "staging, paid analysis disabled"
                    status.allowed -> "funded, ${status.remaining} approved requests remaining"
                    else -> "paid requests unavailable, ${status.remaining} approved requests remaining"
                }
                "Owner Preview authenticated · $availability"
            } else {
                val configuration = regular.read() ?: return@runInterruptible "Cloud is disconnected. Configure Nova Brain in Settings."
                val client = regularClient {
                    mayContinue() && intelligencePrivateAllowed(context) && !previewEnabled() && regular.read() == configuration
                }
                client.status(configuration).unavailableMessage
                    ?: "Cloud chat is authenticated and within the current spending limits."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IntelligenceRouteUnavailable) {
            checkNotNull(failure.message)
        } catch (failure: CloudTeacherException) {
            assistantFailureMessage(failure.failure)
        } catch (_: Exception) {
            "Nova Brain connection settings could not be verified. Local commands are available."
        }
    }

    override suspend fun ask(request: IntelligenceCloudRequest, maySend: () -> Boolean): IntelligenceCloudAnswer =
        runInterruptible(Dispatchers.IO) {
            if (!maySend() || !intelligencePrivateAllowed(context)) return@runInterruptible IntelligenceCloudAnswer(PAUSED)
            val usingPreview = request.mode == IntelligenceCloudMode.OWNER_PREVIEW
            if (usingPreview != previewEnabled()) return@runInterruptible IntelligenceCloudAnswer(CONFIGURATION_CHANGED)
            try {
                if (usingPreview) askPreview(request, maySend) else askRegular(request, maySend)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IntelligenceRouteUnavailable) {
                IntelligenceCloudAnswer("${failure.message} Local commands are available.", retryable = !usingPreview)
            } catch (failure: CloudTeacherException) {
                val retryable = failure.failure in setOf(CloudFailure.OFFLINE, CloudFailure.RATE_LIMIT,
                    CloudFailure.UNKNOWN_OUTCOME, CloudFailure.INVALID_RESPONSE)
                IntelligenceCloudAnswer(assistantFailureMessage(failure.failure), retryable = !usingPreview && retryable,
                    retainIdentity = !usingPreview && failure.failure.mayHaveCharged)
            } catch (_: Exception) {
                IntelligenceCloudAnswer("Nova Brain connection settings could not be verified. Local commands are available.")
            }
        }

    private fun askRegular(request: IntelligenceCloudRequest, maySend: () -> Boolean): IntelligenceCloudAnswer {
        val configuration = regular.read() ?: return IntelligenceCloudAnswer(
            "Cloud is disconnected. You can configure Nova Brain in Settings or use local commands.")
        if (!request.bind(configuration)) return IntelligenceCloudAnswer(CONFIGURATION_CHANGED)
        val current = {
            maySend() && intelligencePrivateAllowed(context) && !previewEnabled() && regular.read() == configuration
        }
        val client = regularClient(current)
        val status = client.status(configuration)
        status.unavailableMessage?.let { return IntelligenceCloudAnswer("$it Local commands are available.") }
        if (request.includesContext && (status.contextMaxChars == IntelligenceCloudRequest.QUESTION_LIMIT ||
                request.payload.length > status.contextMaxChars)) {
            return IntelligenceCloudAnswer("This Nova Brain server does not support the approved conversation context. Local commands are available.")
        }
        if (!current()) throw CancellationException()
        val reservation = try { journal.reserve(configuration, request) }
        catch (_: Exception) {
            return IntelligenceCloudAnswer("Paid request history could not be verified on this phone. Local commands are available.")
        }
        val pending = when (reservation) {
            IntelligenceRequestJournal.Decision.Full -> return IntelligenceCloudAnswer(
                "Too many cloud requests have an unverified outcome. Review the previous requests against the Nova Brain server ledger before sending more.")
            IntelligenceRequestJournal.Decision.PayloadChanged -> return IntelligenceCloudAnswer(
                "An earlier cloud request for this question has different context and an unverified outcome. Restore the original encrypted conversation to retry it or check the Nova Brain server ledger.")
            is IntelligenceRequestJournal.Decision.Ready -> reservation
        }
        val answer = try {
            client.ask(configuration, request.payload, approved = true, pending.entry.requestId, status, current)
        } catch (failure: CloudTeacherException) {
            // A no-charge response to a retry cannot settle an earlier uncertain attempt.
            if (pending.newEntry && !failure.failure.mayHaveCharged) runCatching { journal.resolve(pending.entry) }
            throw failure
        }
        return IntelligenceCloudAnswer(answer, cloud = true, acknowledge = {
            runInterruptible(Dispatchers.IO) { journal.resolve(pending.entry) }
        })
    }

    private fun askPreview(request: IntelligenceCloudRequest, maySend: () -> Boolean): IntelligenceCloudAnswer {
        val fixture = OwnerPreviewTest.ASK_NOVA
        if (request.payload != fixture.fixture) return IntelligenceCloudAnswer(
            "Owner Preview only accepts the approved synthetic question. Local commands are available.")
        val connection = preview.read() ?: return IntelligenceCloudAnswer(CONFIGURATION_CHANGED)
        if (!request.bind(connection.configuration())) return IntelligenceCloudAnswer(CONFIGURATION_CHANGED)
        val reply = OwnerPreviewClient().send(connection, fixture, approved = true, requestId = request.requestId) {
            maySend() && intelligencePrivateAllowed(context) && preview.isCurrent(connection)
        }
        return IntelligenceCloudAnswer(reply.text, cloud = true)
    }

    private fun regularClient(mayContinue: () -> Boolean) = IntelligenceCloud(get = { configuration ->
        requestIntelligence(configuration) { if (!mayContinue()) throw CancellationException() }
    })

    private fun assistantFailureMessage(failure: CloudFailure) = when (failure) {
        CloudFailure.DISABLED -> "Paid cloud analysis is disabled. Local commands are available."
        CloudFailure.OFFLINE -> "Nova Brain is unreachable. Local commands are available."
        else -> failure.userMessage
    }

    private companion object {
        const val PAUSED = "Cloud chat is paused. Local commands are available."
        const val CONFIGURATION_CHANGED = "Nova Brain configuration changed. Review the destination before sending a new question."
    }
}
