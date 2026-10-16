package uk.co.traynor.privategallery.core.security

import uk.co.traynor.privategallery.core.domain.SecondaryAuthAttempt
import uk.co.traynor.privategallery.core.domain.SecondaryOperation
import uk.co.traynor.privategallery.core.domain.SecondaryScope
import uk.co.traynor.privategallery.core.domain.SecondarySessionAuthority

/** Closed set of exact original owners. No ambient session resolution or temporary unlock.
 * Each live credentials operation must have been issued for this exact request; preparatory
 * owners retain the original opaque attempt. Reservation alone never grants authentication. */
internal sealed class BiometricOriginalOwner {
    abstract fun check()
    abstract fun <T : AutoCloseable> create(manifest: OwnedResourceManifest, factory: OwnedFactoryScope.() -> T): OwnedResource<T>

    class PrimaryCredentials(private val guard: ScopedIoGuard) : BiometricOriginalOwner() {
        override fun check() { guard.requireNativeOutsideAuthorityGate(); guard.requireScope(PrimaryScope.CREDENTIALS) }
        override fun <T : AutoCloseable> create(manifest: OwnedResourceManifest, factory: OwnedFactoryScope.() -> T) = guard.createOwned(manifest, factory)
    }
    class PrimaryPreparation(private val authority: PrimarySessionAuthority, private val attempt: PrimaryAuthAttempt) : BiometricOriginalOwner() {
        override fun check() { authority.requireNativeOutsideGate(); authority.checkPreparation(attempt) }
        override fun <T : AutoCloseable> create(manifest: OwnedResourceManifest, factory: OwnedFactoryScope.() -> T) = authority.createAttemptOwned(attempt, manifest, factory)
    }
    class HiddenCredentials(private val operation: SecondaryOperation) : BiometricOriginalOwner() {
        override fun check() { operation.requireNativeOutsideAuthorityGate(); operation.requireScope(SecondaryScope.CREDENTIALS) }
        override fun <T : AutoCloseable> create(manifest: OwnedResourceManifest, factory: OwnedFactoryScope.() -> T) = operation.createOwned(manifest, factory)
    }
    class HiddenPreparation(private val authority: SecondarySessionAuthority, private val attempt: SecondaryAuthAttempt) : BiometricOriginalOwner() {
        override fun check() { authority.requireNativeOutsideGate(); authority.checkPreparation(attempt) }
        override fun <T : AutoCloseable> create(manifest: OwnedResourceManifest, factory: OwnedFactoryScope.() -> T) = authority.createAttemptOwned(attempt, manifest, factory)
    }
}
