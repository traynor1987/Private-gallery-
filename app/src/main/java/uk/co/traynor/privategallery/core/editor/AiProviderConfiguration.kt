package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AiCredentials {
    fun isConfigured(): Boolean
    fun read(): ByteArray?
    fun save(credential: ByteArray)
    fun clear()
}
enum class AiConnectionStatus { NOT_CONFIGURED, CONFIGURED, CONNECTED }

/** Only successful read-only verification may commit a new credential. No secret in observable state.
 * Removal increments a generation counter so an old verification cannot resurrect a deleted key.
 */
class AiProviderConfiguration(
    private val credentials: AiCredentials,
    private val test: suspend (ByteArray) -> Unit,
    private val createProvider: (() -> ByteArray?) -> AiImageEditProvider,
    private val providerName: String = "Replicate",
) {
    private val lock = Any()
    private var generation = 0L
    @Volatile var provider: AiImageEditProvider? = if (credentials.isConfigured()) createProvider { credentials.read() } else null
        private set
    private val mutableStatus = MutableStateFlow(if (provider == null) AiConnectionStatus.NOT_CONFIGURED else AiConnectionStatus.CONFIGURED)
    val status = mutableStatus.asStateFlow()

    /** Takes ownership of candidate, wipes on every exit. Null tests the saved token. */
    suspend fun connect(candidate: ByteArray? = null) {
        var token: ByteArray? = candidate
        val expected = synchronized(lock) {
            if (candidate == null && provider != null) mutableStatus.value = AiConnectionStatus.CONFIGURED
            generation
        }
        try {
            withContext(Dispatchers.IO) { if (token == null) token = synchronized(lock) { credentials.read() } }
            val value = token ?: throw AiEditFailure("Enter your $providerName API key.")
            if (value.isEmpty() || value.size > 8192 || value.any { (it.toInt() and 255) !in 33..126 }) throw AiEditFailure("Enter a valid $providerName API key.")
            withTimeout(30_000) { test(value) }
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.IO) {
                synchronized(lock) {
                    ensureActive()
                    if (generation != expected) throw AiEditFailure("Configuration changed. Please try again.")
                    if (candidate != null) credentials.save(value)
                    provider?.invalidate()
                    provider = createProvider { synchronized(lock) { credentials.read() } }
                    generation++
                    mutableStatus.value = AiConnectionStatus.CONNECTED
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: AiEditFailure) { throw failure
        } catch (_: Exception) { throw AiEditFailure("Could not securely save this configuration. Try again.")
        } finally { token?.fill(0) }
    }
    fun remove(clearConsent: () -> Unit) = synchronized(lock) {
        generation++
        provider?.invalidate()
        provider = null
        credentials.clear()
        clearConsent()
        mutableStatus.value = AiConnectionStatus.NOT_CONFIGURED
    }
}
