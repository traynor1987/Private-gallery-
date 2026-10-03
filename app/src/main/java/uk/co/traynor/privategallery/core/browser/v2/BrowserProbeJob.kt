package uk.co.traynor.privategallery.core.browser.v2

import kotlinx.coroutines.Job
import kotlinx.coroutines.InternalCoroutinesApi
import uk.co.traynor.privategallery.core.security.PrimaryOperation

/** The exact already preclaimed lazy probe Job; hook installation and start stay outside gates. */
@OptIn(InternalCoroutinesApi::class)
internal fun startOwnedBrowserProbe(operation: PrimaryOperation, job: Job) {
    try {
        // A blocked native read cannot wait for the producer to complete before its
        // independent, already funded unblockers dispatch. This callback only retires
        // this exact reserved probe fork; actual Job completion still owns its ack.
        job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { operation.close() }
        operation.checkValid()
        job.start()
    } catch (failure: Throwable) {
        operation.close()
        throw failure
    }
}
