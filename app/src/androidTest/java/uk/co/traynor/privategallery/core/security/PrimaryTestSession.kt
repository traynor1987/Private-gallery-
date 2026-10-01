package uk.co.traynor.privategallery.core.security

/** Test-only compatibility adapter. Synthetic keys never enter a production constructor. */
fun primaryTestOperation(key: ByteArray): PrimaryOperation = PrimarySessionAuthority().let {
    it.open(key.copyOf())
    checkNotNull(it.operationOrNull())
}
