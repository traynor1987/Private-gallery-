package uk.co.traynor.privategallery.core.security

/** The exact pre-funded resource and its normal retirement, shared with revocation. */
class OwnedResource<T : AutoCloseable> internal constructor(
    val value: T,
    private val original: ReleaseReservation,
) : AutoCloseable {
    override fun close() = original.release()
}
