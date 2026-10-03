package uk.co.traynor.privategallery.core.security

/** The exact pre-funded resource and its normal retirement, shared with revocation. */
class OwnedResource<T : AutoCloseable> internal constructor(
    val value: T,
    internal val original: ReleaseReservation,
) : AutoCloseable {
    val retirement: RetirementAcknowledgement get() = original.retirement
    val releaseFailed: Boolean get() = original.failed
    override fun close() = original.release()
}

/** Declares every independently blocking child before a supported factory can begin. */
class OwnedResourceManifest private constructor(
    internal val children: List<String>,
    internal val presentation: Boolean,
) {
    init {
        require(children.isNotEmpty() && children.size <= 16)
        require(children.all { it.isNotBlank() } && children.distinct().size == children.size)
    }
    companion object {
        fun io(vararg children: String) = OwnedResourceManifest(children.toList(), false)
        fun presentation(vararg children: String) = OwnedResourceManifest(children.toList(), true)
    }
}

/** Exact original manifest. Attach each acquired child before any next throwing/blocking step. */
class OwnedFactoryScope internal constructor(
    private val original: ReleaseReservation,
    private val manifest: OwnedResourceManifest,
) {
    fun copyBytes(name: String, source: ByteArray, readerMutex: Any? = null): ByteArray {
        val index = manifest.children.indexOf(name)
        check(index >= 0) { "Child absent from original factory manifest" }
        return original.copyBytes(index, source, readerMutex)
    }
    fun <T : AutoCloseable> attach(name: String, child: T): T {
        val index = manifest.children.indexOf(name)
        check(index >= 0) { "Child absent from original factory manifest" }
        return original.attach(index, child)
    }
}
