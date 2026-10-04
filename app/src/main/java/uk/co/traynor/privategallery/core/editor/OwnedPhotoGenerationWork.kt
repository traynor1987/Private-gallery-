package uk.co.traynor.privategallery.core.editor

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.security.*

/** Completion is only a UI observation, never evidence of original retirement. */
internal class PhotoGenerationWork internal constructor(val job: Job,
    private val done: AtomicBoolean, val retirement: RetirementAcknowledgement) {
    val completed: Boolean get() = done.get()
}

/** Fixed two-child input/producer manifest. Renderer/provider/output obligations are separate.
 * The structured consumer must not retain the input or start unjoined children. */
internal fun launchPhotoGenerationWork(owner: PrimaryOperation, scope: CoroutineScope, source: ByteArray,
    block: suspend CoroutineScope.(ByteArray) -> Unit): PhotoGenerationWork {
    val guard = ScopedIoGuard(owner, PrimaryScope.LOCAL_EDIT)
    guard.checkPhotoProducerAdmission()
    require(source.isNotEmpty() && source.size <= PhotoRenderer.MAX_SOURCE_BYTES) { "Photo input size unavailable" }
    val done = AtomicBoolean(false)
    var producer: Job? = null
    val owned = guard.createOwned(OwnedResourceManifest.io("input", "producer")) {
        val input = PhotoGenerationInput(guard, original, source)
        original.attach(0, input)
        guard.checkPhotoProducerAdmission()
        producer = original.createJob(1) { attach ->
            scope.launch(start = CoroutineStart.LAZY) {
                input.consume { bytes -> coroutineScope { block(bytes) } }
            }.also(attach)
        }.actualJob
        input
    }
    try {
        val job = checkNotNull(producer)
        val work = PhotoGenerationWork(job, done, owned.retirement)
        // Bounded original dispatch only; no UI callback, posting or dependent worker wait.
        job.invokeOnCompletion { try { owned.close() } finally { done.set(true) } }
        guard.checkPhotoProducerAdmission()
        if (!job.start()) throw CancellationException("Photo producer unavailable")
        return work
    } catch (failure: Throwable) { owned.close(); throw failure }
}

private class PhotoGenerationInput(private val guard: ScopedIoGuard,
    private val original: ReleaseReservation, source: ByteArray): AcknowledgedCloseable {
    private val gate = Any()
    private val acknowledged = CompletableFuture<Unit>()
    private var retired = false
    private var borrowed = false
    private var wiped = false
    // All metadata/acknowledgement exists before this last allocation; caller binds literal0 immediately.
    private val actual = run { guard.requirePhotoInputConstruction(original); source.copyOf() }

    suspend fun consume(block: suspend (ByteArray) -> Unit) {
        guard.requirePhotoInputUse(original, this)
        synchronized(gate) {
            if (retired) throw CancellationException("Photo input retired")
            check(!borrowed && !wiped) { "Original photo input already consumed" }
            borrowed = true
        }
        try {
            guard.requirePhotoInputUse(original, this)
            block(actual)
            currentCoroutineContext().ensureActive()
            guard.requirePhotoInputUse(original, this)
        } finally {
            val complete = synchronized(gate) { actual.fill(0); wiped = true; borrowed = false; retired }
            if (complete) acknowledged.complete(Unit)
        }
    }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        val complete = synchronized(gate) {
            retired = true
            if (borrowed) false else { if (!wiped) { actual.fill(0); wiped = true }; true }
        }
        if (complete) acknowledged.complete(Unit)
        return acknowledged
    }
}
