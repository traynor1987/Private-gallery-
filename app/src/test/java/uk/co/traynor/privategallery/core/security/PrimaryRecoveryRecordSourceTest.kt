package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

class PrimaryRecoveryRecordSourceTest {
    private class Persistence : RecoveryEnvelopePersistence {
        var values = emptyMap<String, String>()
        @Volatile var unavailable = false
        var reject = false
        var reads = 0
        override val hasReadFailure: Boolean get() = unavailable
        override fun read(): Map<String, String> { reads++; return values }
        override fun commit(expected: Map<String, String>, values: Map<String, String>): Boolean {
            if (reject || this.values != expected) return false
            this.values = values; return true
        }
    }
    private fun record(unpadded: Boolean = false) = mapOf("salt" to Base64.getEncoder().encodeToString(ByteArray(16) { 1 }),
        "nonce" to Base64.getEncoder().encodeToString(ByteArray(12) { 2 }),
        "ciphertext" to Base64.getEncoder().encodeToString(ByteArray(48) { 3 }))
        .mapValues { if (unpadded) it.value.trimEnd('=') else it.value }
    private fun preferences(values: Map<String, Any?>, read: () -> Unit = {}): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { proxy, method, args ->
        when (method.name) {
            "getAll" -> { read(); values }
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            "toString" -> "SyntheticRecoveryPreferences"
            else -> error("Unexpected preference call ${method.name}")
        }
    } as SharedPreferences
    private fun source(preferences: SharedPreferences, lifecycle: RecoverySetupLifecycle): RecoveryVaultKeyStore.ConfirmedRecordSource {
        val constructor = RecoveryVaultKeyStore.ConfirmedRecordSource::class.java.getDeclaredConstructor(
            SharedPreferences::class.java, RecoverySetupLifecycle::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(preferences, lifecycle)
    }
    private fun pool(authority: PrimarySessionAuthority): ReleasePool = authority.javaClass.getDeclaredField("ioReleasePool")
        .apply { isAccessible = true }.get(authority) as ReleasePool
    private fun clean(authority: PrimarySessionAuthority) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < end) Thread.yield()
        assertTrue(authority.cleanupComplete); assertEquals(0, pool(authority).occupied)
    }
    @Test fun exactLegacyAndConfirmedRecordsKeepBytesPaddingAndPossessionFlags() {
        for (unpadded in listOf(false, true)) for (origin in listOf(null, "new", "legacy", "restored")) {
            val values = record(unpadded) + if (origin == null) emptyMap() else mapOf("state" to "confirmed", "origin" to origin)
            val persistence = Persistence(); val lifecycle = RecoverySetupLifecycle(persistence)
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); lateinit var prefs: SharedPreferences
            prefs = preferences(values) { assertFalse(Thread.holdsLock(prefs)); assertEquals(2, pool(authority).occupied) }
            val owned = source(prefs, lifecycle).readConfirmed(authority, attempt)
            val descriptor = owned.value.descriptor()
            assertEquals(values["salt"], descriptor.salt); assertEquals(values["nonce"], descriptor.nonce)
            assertEquals(values["ciphertext"], descriptor.ciphertext)
            assertEquals(origin == null || origin == "legacy", descriptor.legacyExisting)
            assertEquals(origin != null, descriptor.possessionVerified)
            assertEquals(16, Base64.getDecoder().decode(descriptor.salt).size)
            assertEquals(12, Base64.getDecoder().decode(descriptor.nonce).size)
            assertEquals(48, Base64.getDecoder().decode(descriptor.ciphertext).size)
            assertEquals(0, persistence.reads); assertNull(authority.bindingOrNull()); assertEquals(1, pool(authority).occupied)
            owned.close(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
            assertThrows(IllegalStateException::class.java) { owned.value.descriptor() }
            clean(authority); authority.revoke()
        }
    }
    @Test fun noncanonicalUnusedPadBitsAcceptedByJavaArePreservedWithoutReencoding() {
        val values = record().toMutableMap(); values["salt"] = values.getValue("salt").dropLast(3) + "R=="
        assertEquals(16, Base64.getDecoder().decode(values.getValue("salt")).size)
        val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
        val owned = source(preferences(values), RecoverySetupLifecycle(Persistence())).readConfirmed(authority, attempt)
        assertEquals(values["salt"], owned.value.descriptor().salt)
        owned.close(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); clean(authority); authority.revoke()
    }
    @Test fun pendingUnknownExtraMistypedAndCorruptRecordsCannotBecomeConfirmedDescriptors() {
        val good = record()
        val bad = mutableListOf<Map<String, Any?>>(emptyMap(), good + ("unexpected" to "value"), good - "nonce",
            good + mapOf("state" to "pending", "origin" to "new"), good + mapOf("state" to "confirmed", "origin" to "other"),
            good + mapOf("state" to "confirmed"), mapOf("pending_salt" to good["salt"], "pending_nonce" to good["nonce"],
                "pending_ciphertext" to good["ciphertext"], "state" to "pending"))
        for (field in good.keys) {
            bad += good + (field to 123)
            bad += good + (field to null)
            for (text in listOf("", "A".repeat(4096), "!".repeat(good.getValue(field).length),
                " " + good.getValue(field), good.getValue(field) + "\n", "-" + good.getValue(field).drop(1),
                "_" + good.getValue(field).drop(1), "=" + good.getValue(field).drop(1))) bad += good + (field to text)
        }
        bad += good + ("salt" to "A".repeat(22) + "=")
        for (values in bad) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); val persistence = Persistence()
            assertThrows(IllegalStateException::class.java) { source(preferences(values), RecoverySetupLifecycle(persistence)).readConfirmed(authority, attempt) }
            assertEquals(0, persistence.reads); clean(authority); authority.revoke()
        }
    }
    @Test fun lifecycleDelegateAndSharedIdentityQuarantinesDenyBeforePreferenceRead() {
        for (kind in listOf("lifecycle", "delegate", "shared-reconstruction")) {
            var reads = 0; val prefs = preferences(record()) { reads++ }; val delegate = Persistence()
            val lifecycle: RecoverySetupLifecycle
            when (kind) {
                "lifecycle" -> {
                    delegate.reject = true; lifecycle = RecoverySetupLifecycle(delegate)
                    assertThrows(IllegalStateException::class.java) { lifecycle.installForRestoredVault(RecoveryWrappedKey(ByteArray(16), ByteArray(12), ByteArray(48))) }
                }
                "delegate" -> { delegate.unavailable = true; lifecycle = RecoverySetupLifecycle(FailClosedRecoveryPersistence(prefs, delegate)) }
                else -> {
                    delegate.reject = true; val original = FailClosedRecoveryPersistence(prefs, delegate)
                    assertFalse(original.commit(emptyMap(), record()))
                    lifecycle = RecoverySetupLifecycle(FailClosedRecoveryPersistence(prefs, Persistence()))
                }
            }
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
            assertThrows(IllegalStateException::class.java) { source(prefs, lifecycle).readConfirmed(authority, attempt) }
            assertEquals(0, reads); clean(authority); authority.revoke()
        }
    }
    @Test fun sharedFailureDuringActualUnlockedReadDeniesAfterReturnAndReconstruction() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val failure = AtomicReference<Throwable>()
        lateinit var prefs: SharedPreferences
        prefs = preferences(record()) { assertFalse(Thread.holdsLock(prefs)); entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        val delegate = Persistence().also { it.reject = true }; val persistence = FailClosedRecoveryPersistence(prefs, delegate)
        val lifecycle = RecoverySetupLifecycle(persistence); val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
        val worker = Thread { try { source(prefs, lifecycle).readConfirmed(authority, attempt); failure.set(AssertionError("failed record returned")) }
            catch (problem: Throwable) { failure.set(problem) } }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(2, pool(authority).occupied)
            assertFalse(persistence.commit(emptyMap(), record()))
        } finally { release.countDown(); worker.join(10000) }
        assertFalse(worker.isAlive); assertTrue(failure.get() is IllegalStateException); clean(authority)
        val fresh = authority.beginAuthentication()
        assertThrows(IllegalStateException::class.java) { source(prefs, RecoverySetupLifecycle(FailClosedRecoveryPersistence(prefs, Persistence()))).readConfirmed(authority, fresh) }
        clean(authority); authority.revoke()
    }
    @Test fun cancellationDuringHeldActualReadRetainsOriginalChargeAndDeniesLateDescriptor() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val failure = AtomicReference<Throwable>()
        val prefs = preferences(record()) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
        val worker = Thread { try { source(prefs, RecoverySetupLifecycle(Persistence())).readConfirmed(authority, attempt); failure.set(AssertionError("stale descriptor returned")) }
            catch (problem: Throwable) { failure.set(problem) } }
        worker.start()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.cancelAuthentication(attempt)
            assertFalse(authority.cleanupComplete); assertEquals(2, pool(authority).occupied)
        } finally { release.countDown(); worker.join(10000) }
        assertFalse(worker.isAlive); assertNotNull(failure.get()); clean(authority); authority.revoke()
    }
    @Test fun inheritedGatesAndIncompleteCapacityDenyBeforeReadOrTicketStartup() {
        for (kind in listOf("authority", "storage", "preferences", "persistence", "capacity")) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); val persistence = Persistence()
            var reads = 0; val prefs = preferences(record()) { reads++ }; val lifecycle = RecoverySetupLifecycle(persistence)
            var forbidden: Any? = null; var tickets = 0
            val isolated = ReleasePool(16, allocateTicket = { owner, slot -> if (forbidden?.let { Thread.holdsLock(it) } == true) tickets++; ReleaseTicket(owner, slot) })
            authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, isolated)
            if (kind == "capacity") {
                val held = authority.createAttemptOwned(attempt, OwnedResourceManifest.io(*Array(15) { "held$it" })) {
                    val root = attach("held0", AutoCloseable {}); for (i in 1..14) attach("held$i", AutoCloseable {}); root
                }
                try { assertThrows(Exception::class.java) { source(prefs, lifecycle).readConfirmed(authority, attempt) } }
                finally { held.close(); assertTrue(held.retirement.await(5, TimeUnit.SECONDS)) }
            } else {
                val gate = when (kind) {
                    "authority" -> authority.javaClass.getDeclaredField("gate").apply { isAccessible = true }.get(authority)
                    "storage" -> PrimaryVaultSetupGuard.storageLock
                    "preferences" -> prefs
                    else -> persistence
                }
                forbidden = gate
                synchronized(gate) { assertThrows(IllegalStateException::class.java) { source(prefs, lifecycle).readConfirmed(authority, attempt) } }
            }
            assertEquals(0, reads); assertEquals(0, tickets); clean(authority); authority.revoke()
        }
    }
    @Test fun completedReadStageCannotReplaceActualReleaseReturnAndFinalQuarantineCheck() {
        for (failDuringCleanup in listOf(false, true)) {
            val held = CountDownLatch(1); val release = CountDownLatch(1); val first = java.util.concurrent.atomic.AtomicBoolean(true)
            val isolated = ReleasePool(16, allocateInvocation = { action, returned -> ReleaseInvocation({ action()
                if (first.compareAndSet(true, false)) { held.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }, returned) })
            val authority = testPrimaryAuthority()
            authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, isolated)
            val persistence = Persistence(); val lifecycle = RecoverySetupLifecycle(persistence)
            val attempt = authority.beginAuthentication(); val result = AtomicReference<OwnedResource<PreparedPrimaryRecoveryRecord>>()
            val failure = AtomicReference<Throwable>()
            val worker = Thread { try { result.set(source(preferences(record()), lifecycle).readConfirmed(authority, attempt)) }
                catch (problem: Throwable) { failure.set(problem) } }
            worker.start()
            try {
                assertTrue(held.await(5, TimeUnit.SECONDS)); assertNull(result.get()); assertFalse(authority.cleanupComplete)
                if (failDuringCleanup) persistence.unavailable = true
            } finally { release.countDown(); worker.join(10000) }
            assertFalse(worker.isAlive)
            if (failDuringCleanup) { assertNull(result.get()); assertTrue(failure.get() is IllegalStateException) }
            else { assertNull(failure.get()); result.get().close(); assertTrue(result.get().retirement.await(5, TimeUnit.SECONDS)) }
            clean(authority); authority.revoke()
        }
    }
    @Test fun interruptedReadRetirementKeepsInterruptAndPhysicalCharge() {
        val held = CountDownLatch(1); val release = CountDownLatch(1); val first = java.util.concurrent.atomic.AtomicBoolean(true)
        val isolated = ReleasePool(16, allocateInvocation = { action, returned -> ReleaseInvocation({ action()
            if (first.compareAndSet(true, false)) { held.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }, returned) })
        val authority = testPrimaryAuthority()
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, isolated)
        val attempt = authority.beginAuthentication(); val failure = AtomicReference<Throwable>()
        val interrupted = java.util.concurrent.atomic.AtomicBoolean()
        val worker = Thread { try { source(preferences(record()), RecoverySetupLifecycle(Persistence())).readConfirmed(authority, attempt) }
            catch (problem: Throwable) { failure.set(problem); interrupted.set(Thread.currentThread().isInterrupted) } }
        worker.start()
        try {
            assertTrue(held.await(5, TimeUnit.SECONDS)); worker.interrupt(); worker.join(5000)
            assertFalse(worker.isAlive); assertTrue(failure.get() is InterruptedException); assertTrue(interrupted.get())
            assertTrue(isolated.occupied > 0); assertFalse(authority.cleanupComplete)
        } finally { release.countDown(); worker.join(10000) }
        clean(authority); authority.revoke()
    }
    @Test fun failedPhysicalReadRetirementStaysChargedAndDeniesEveryFreshSnapshot() {
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        val isolated = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ if (fail.compareAndSet(true, false)) error("controlled record release failure"); action() }, returned) })
        val authority = testPrimaryAuthority()
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, isolated)
        var reads = 0; val prefs = preferences(record()) { reads++ }; val selected = source(prefs, RecoverySetupLifecycle(Persistence()))
        assertThrows(java.io.IOException::class.java) { selected.readConfirmed(authority, authority.beginAuthentication()) }
        assertTrue(isolated.hasFailedRetirement); assertTrue(isolated.occupied > 0); assertFalse(authority.cleanupComplete)
        repeat(2) { assertThrows(IllegalStateException::class.java) { selected.readConfirmed(authority, authority.beginAuthentication()) } }
        assertEquals(1, reads); assertTrue(isolated.hasFailedRetirement); authority.revoke()
    }

}
