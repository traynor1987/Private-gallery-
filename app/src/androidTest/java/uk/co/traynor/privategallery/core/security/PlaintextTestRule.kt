package uk.co.traynor.privategallery.core.security

import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** Synthetic sources only; each test owns and retires its exact originals. */
class PlaintextTestRule : ExternalResource() {
    private val authority = PrimarySessionAuthority { 0 }.apply { open(ByteArray(32) { 21 }) }
    private val originals = mutableListOf<OwnedPlaintext>()
    fun own(bytes: ByteArray): OwnedPlaintext = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ))).use {
        it.createOwnedPlaintext { bytes }.also(originals::add)
    }
    fun awaitRetired() { originals.forEach { assertTrue(it.retirement.await(5, TimeUnit.SECONDS)) } }
    override fun after() {
        authority.revoke()
        originals.forEach { assertTrue(it.retirement.await(5, TimeUnit.SECONDS)) }
    }
}
fun assertOwnedPlaintextEquals(expected: ByteArray, actual: OwnedPlaintext) {
    try { actual.useBytes { assertArrayEquals(expected, it) } }
    finally { actual.close(); assertTrue(actual.retirement.await(5, TimeUnit.SECONDS)) }
}
