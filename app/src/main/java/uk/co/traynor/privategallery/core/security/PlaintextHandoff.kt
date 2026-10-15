package uk.co.traynor.privategallery.core.security

import kotlinx.coroutines.suspendCancellableCoroutine

/** One callback transfers one original. Late distinct originals are retired; a
 * duplicate of the accepted facade cannot revoke its live consumer. */
internal suspend fun awaitOwnedPlaintext(
    register: ((Result<OwnedPlaintext>) -> Unit) -> Unit,
): OwnedPlaintext = suspendCancellableCoroutine { continuation ->
    val gate = Any()
    var claimed = false
    var accepted: OwnedPlaintext? = null
    val callback: (Result<OwnedPlaintext>) -> Unit = { result ->
        val value = result.getOrNull()
        val first = synchronized(gate) {
            if (claimed) false else { claimed = true; accepted = value; true }
        }
        if (!first) {
            val duplicate = synchronized(gate) { value === accepted }
            if (!duplicate) value?.close()
        } else if (!continuation.isActive) value?.close()
        else result.fold({ continuation.resume(it) { value?.close() } }, { continuation.resumeWith(Result.failure(it)) })
    }
    try { register(callback) }
    catch (failure: Throwable) { callback(Result.failure(failure)) }
}

/** post must enqueue (the Android caller uses Handler.post), never run inline.
 * Successful results are already funded; this only transfers their exact facade.
 * A queued result is not discharged until its original retirement acknowledges. */
internal fun postOwnedPlaintext(
    post: (Runnable) -> Boolean,
    allowed: () -> Boolean,
    result: Result<OwnedPlaintext>,
    consume: (Result<OwnedPlaintext>) -> Unit,
) {
    val value = result.getOrNull()
    try {
        if (!post(Runnable {
            try { if (allowed()) consume(result) else value?.close() }
            catch (failure: Throwable) { value?.close(); throw failure }
        })) value?.close()
    } catch (failure: Throwable) { value?.close(); throw failure }
}
