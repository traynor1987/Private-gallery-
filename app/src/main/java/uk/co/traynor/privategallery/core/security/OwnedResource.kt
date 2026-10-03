package uk.co.traynor.privategallery.core.security

/** The exact pre-funded resource and its normal retirement, shared with revocation. */
class OwnedResource<T : AutoCloseable> internal constructor(
    val value: T,
    internal val original: ReleaseReservation,
) : AutoCloseable {
    /** This original's native return, owning accounting, terminal markers and slot return.
     * Other originals or authority-wide dispatches may still prevent fresh admission. */
    val retirement: RetirementAcknowledgement get() = original.terminalRetirement
    internal val terminalRetirement: RetirementAcknowledgement get() = original.terminalRetirement
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
    internal val original: ReleaseReservation,
    private val manifest: OwnedResourceManifest,
) {
    /** Actual construction finished; outside all gates, dispatch all named unblockers then await. */
    internal fun retireChildren(vararg names: String) = original.retirePhase(phaseIndices(names), absent = false)
    /** Neutralize only exact released-original phase admission. Genuine malformed
     * phase, wrong constructor and failed Native retirement remain failures. */
    internal fun retireProducerChildren(vararg names:String) = original.retirePhase(phaseIndices(names),absent=false,cancelIfReleased=true)
    /** Same exact producer metadata decision for declared never-created children. */
    internal fun discardProducerChildren(vararg names:String) = original.retirePhase(phaseIndices(names),absent=true,cancelIfReleased=true)
    /** No future factory may acquire these original entries after they are sealed absent. */
    internal fun discardChildren(vararg names: String) = original.retirePhase(phaseIndices(names), absent = true)
    private fun phaseIndices(names: Array<out String>): IntArray {
        require(names.isNotEmpty() && names.size < manifest.children.size) { "Invalid original phase" }
        return IntArray(names.size) { index ->
            manifest.children.indexOf(names[index]).also { require(it >= 0) { "Child absent from original manifest" } }
        }
    }

    fun <T : Any> create(name: String, dispose: (T) -> Unit, factory: () -> T): ReservedValue<T> {
        val index = manifest.children.indexOf(name)
        check(index >= 0) { "Child absent from original factory manifest" }
        return original.createValue(index, dispose, factory)
    }

    internal fun <T : Any> guardedNative(name: String, guard: ScopedIoGuard,
        dispose: (T) -> Unit, factory: () -> T): OwnedNativeUse<T> {
        val index = manifest.children.indexOf(name)
        check(index > 0) { "Native invocation child absent from original secondary manifest" }
        return OwnedNativeUse.create(guard, original, index, dispose, factory)
    }

    internal fun nativeOutputBuffer(name: String, guard: ScopedIoGuard, size: Int): ReservedValue<OwnedNativeOutputBuffer> {
        val index = manifest.children.indexOf(name)
        check(index >= 0) { "Child absent from original factory manifest" }
        return original.createValue(index, { it.close() }) { OwnedNativeOutputBuffer(guard, original, index, size) }
    }

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
