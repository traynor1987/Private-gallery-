package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletionStage

/** Posting release is insufficient: the returned stage acknowledges actual platform release. */
interface AcknowledgedCloseable : AutoCloseable {
    fun closeAcknowledged(): CompletionStage<Unit>
    override fun close() { closeAcknowledged() }
}
