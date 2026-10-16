package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class PrimaryCredentialMutationTest {
    private val registry = PrimaryCredentialMutationRegistry()
    private val authorities = mutableListOf<PrimarySessionAuthority>()
    private val originals = mutableListOf<OwnedResource<ClaimChild>>()
    private class ClaimChild : AcknowledgedCloseable {
        val returned = CompletableFuture<Unit>()
        override fun closeAcknowledged() = returned
    }
    private fun original(): OwnedResource<ClaimChild> {
        val authority = testPrimaryAuthority().also { authorities += it }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)))
        return operation.createOwned(OwnedResourceManifest.io("credential-command")) {
            attach("credential-command", ClaimChild())
        }.also { originals += it }
    }
    private fun original(pool: ReleasePool): OwnedResource<ClaimChild> {
        val authority = testPrimaryAuthority().also { authorities += it }
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)))
        return operation.createOwned(OwnedResourceManifest.io("credential-command")) {
            attach("credential-command", ClaimChild())
        }.also { originals += it }
    }
    @After fun cleanup() {
        originals.forEach { it.value.returned.complete(Unit); it.close() }
        originals.forEach { assertTrue(it.retirement.await(5, TimeUnit.SECONDS)) }
        authorities.forEach { it.revoke() }

    }
    @Test fun oneProcessClaimRejectsOverlapAndStaleIdentityCannotReleaseReplacement() {
        val firstOwner = original(); val secondOwner = original()
        val first = registry.claim(firstOwner.original, firstOwner.value, PrimaryCredentialPurpose.CHANGE_PIN)
        assertThrows(IllegalStateException::class.java) {
            registry.claim(secondOwner.original, secondOwner.value, PrimaryCredentialPurpose.RECOVERY)
        }
        first.decideFinished()
        firstOwner.value.returned.complete(Unit); firstOwner.close()
        assertTrue(firstOwner.retirement.await(5, TimeUnit.SECONDS))
        val replacement = registry.claim(secondOwner.original, secondOwner.value, PrimaryCredentialPurpose.RECOVERY)
        assertThrows(IllegalStateException::class.java) { first.requireHeld() }
        assertThrows(IllegalStateException::class.java) { first.decideFinished() }
        replacement.requireHeld(); replacement.decideFinished()
    }
    @Test fun claimRequiresTheAttachedOriginalChildBeforeAnyMutation() {
        val owner = original()
        assertThrows(IllegalStateException::class.java) {
            registry.claim(owner.original, ClaimChild(), PrimaryCredentialPurpose.SETUP)
        }
        val actual = registry.claim(owner.original, owner.value, PrimaryCredentialPurpose.SETUP)
        actual.requireHeld(); actual.decideFinished()
    }
    @Test fun finishedBodyAndCompletedCloseStageCannotReleaseClaimBeforeActualOriginalReturn() {
        val held = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ action(); held.countDown(); check(release.await(10, TimeUnit.SECONDS)) }, returned)
        })
        val firstOwner = original(pool); val nextOwner = original()
        val first = registry.claim(firstOwner.original, firstOwner.value, PrimaryCredentialPurpose.CHANGE_PIN)
        first.decideFinished(); firstOwner.value.returned.complete(Unit); firstOwner.close()
        try {
            assertTrue(held.await(5, TimeUnit.SECONDS))
            assertFalse(firstOwner.retirement.isComplete)
            assertThrows("completed close stage is not actual wrapper return", IllegalStateException::class.java) {
                registry.claim(nextOwner.original, nextOwner.value, PrimaryCredentialPurpose.SETUP)
            }
        } finally { release.countDown() }
        assertTrue(firstOwner.retirement.await(5, TimeUnit.SECONDS))
        registry.claim(nextOwner.original, nextOwner.value, PrimaryCredentialPurpose.SETUP).decideFinished()
    }
    @Test fun terminalRetirementWithoutDurableDecisionDoesNotReleaseRestoreClaim() {
        val firstOwner = original(); val nextOwner = original()
        val first = registry.claim(firstOwner.original, firstOwner.value, PrimaryCredentialPurpose.RESTORE)
        firstOwner.value.returned.complete(Unit); firstOwner.close()
        assertTrue(firstOwner.retirement.await(5, TimeUnit.SECONDS))
        assertThrows(IllegalStateException::class.java) {
            registry.claim(nextOwner.original, nextOwner.value, PrimaryCredentialPurpose.SETUP)
        }
        first.decideFinished()
        registry.claim(nextOwner.original, nextOwner.value, PrimaryCredentialPurpose.SETUP).decideFinished()
    }
    @Test fun laterRollbackDecisionCannotEraseAnEarlierProviderAmbiguityQuarantine() {
        val firstOwner = original(); val nextOwner = original()
        val first = registry.claim(firstOwner.original, firstOwner.value, PrimaryCredentialPurpose.RESTORE)
        first.markAmbiguous(); first.decideRolledBack()
        firstOwner.value.returned.complete(Unit); firstOwner.close()
        assertTrue(firstOwner.retirement.await(5, TimeUnit.SECONDS))
        assertThrows(IllegalStateException::class.java) {
            registry.claim(nextOwner.original, nextOwner.value, PrimaryCredentialPurpose.SETUP)
        }
    }
}
