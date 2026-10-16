package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PreparedPrimaryPinWriteTest {
    private data class Scope(val authority: PrimarySessionAuthority, val operation: PrimaryOperation, val pool: ReleasePool)
    private data class Handle(val original: ReleaseReservation, val write: PreparedPrimaryPinWrite)
    private val registry = PrimaryCredentialMutationRegistry()
    private val scopes = mutableListOf<Scope>()
    private val handles = mutableListOf<Handle>()
    private val record get() = PrimaryPinRecord("s".repeat(24), "n".repeat(16), "c".repeat(64))
    private fun scope(pool: ReleasePool = ReleasePool(16)): Scope {
        val authority = testPrimaryAuthority()
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        authority.open(ByteArray(32))
        return Scope(authority, checkNotNull(authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS))), pool).also { scopes += it }
    }
    private fun prepare(scope: Scope, prefs: Preferences, restore: Boolean = false, registry: PrimaryCredentialMutationRegistry = this.registry, production: Boolean = false): Handle {
        var handle: Handle? = null
        try {
            val names = if (restore) arrayOf("pin-write", "pin-rollback") else arrayOf("pin-write")
            scope.operation.createOwned(OwnedResourceManifest.io(*names)) {
                val purpose = if (restore) PrimaryCredentialPurpose.RESTORE else PrimaryCredentialPurpose.CHANGE_PIN
                val write = attach("pin-write", if (production) PreparedPrimaryPinWrite.create(scope.operation, original, purpose)
                    else syntheticPreparedPinWrite(scope.operation, original, purpose, registry))
                handle = Handle(original, write).also { handles += it }
                if (restore) write.attachRollback(attach("pin-rollback", PreparedPrimaryPinRollback(scope.operation, original)))
                write.claimSlot()
                write.prepare(prefs, null, record)
                write
            }
            return checkNotNull(handle)
        } catch (failure: Throwable) {
            handle?.let { it.write.invocationReturned(); it.original.release(); assertTrue(it.original.terminalRetirement.await(5, TimeUnit.SECONDS)) }
            throw failure
        }
    }
    private fun retire(handle: Handle) {
        handle.write.invocationReturned(); handle.original.release()
        assertTrue(handle.original.terminalRetirement.await(5, TimeUnit.SECONDS))
    }
    private fun busy(scope: Scope, registry: PrimaryCredentialMutationRegistry = this.registry) {
        val prefs = Preferences()
        assertThrows(IllegalStateException::class.java) { prepare(scope, prefs, registry = registry) }
        assertEquals(0, prefs.editors)
    }
    @After fun cleanup() {
        handles.forEach { it.write.invocationReturned(); it.original.release() }
        handles.filter { !it.original.failed }.forEach { assertTrue(it.original.terminalRetirement.await(5, TimeUnit.SECONDS)) }
        scopes.forEach { it.operation.close(); it.authority.revoke() }

    }
    @Test fun exactEditorIsPreparedOnceAdmittedOnceAndExecutedOutsideAuthority() {
        val scope = scope(); val prefs = Preferences()
        prefs.checkOutside = { scope.operation.requireNativeOutsideAuthorityGate(); assertFalse(Thread.holdsLock(prefs)); assertFalse(Thread.holdsLock(PrimaryVaultSetupGuard.storageLock)) }
        val handle = prepare(scope, prefs)
        assertEquals(1, prefs.editors); assertEquals(0, prefs.commits)
        assertThrows(IllegalStateException::class.java) { handle.write.commitExact() }
        handle.write.admit(); assertThrows(IllegalStateException::class.java) { handle.write.admit() }
        handle.write.commitExact(); assertEquals(1, prefs.commits)
        assertThrows(IllegalStateException::class.java) { handle.write.commitExact() }
        assertThrows(IllegalStateException::class.java) { handle.write.prepare(prefs, null, record) }
        handle.write.finishInstalled(); retire(handle)
        assertEquals(mapOf("salt" to "s".repeat(24), "nonce" to "n".repeat(16), "ciphertext" to "c".repeat(64)), prefs.values)
        assertEquals(0, scope.pool.occupied)
    }
    @Test fun productionFactoryIsHardwiredToOneSharedProcessRegistry() {
        val first = scope(); val second = scope(); val handle = prepare(first, Preferences(), production = true)
        assertThrows(IllegalStateException::class.java) { prepare(second, Preferences(), production = true) }
        retire(handle)
        val replacement = prepare(second, Preferences(), production = true); retire(replacement)
    }
    @Test fun preAdmissionRevokeWritesNothingAndOriginalRetires() {
        val scope = scope(); val prefs = Preferences(); val handle = prepare(scope, prefs)
        scope.authority.revoke()
        assertThrows(IllegalStateException::class.java) { handle.write.admit() }
        assertEquals(0, prefs.commits); retire(handle)
        assertEquals(0, scope.pool.occupied)
    }
    @Test fun revokeBetweenRollbackAttachmentAndLinkRetiresBothKnownUnenteredChildren() {
        for (revokeBeforeAttachment in listOf(false, true)) {
            val scope = scope(); val prefs = Preferences()
            var original: ReleaseReservation? = null
            assertThrows(IllegalStateException::class.java) {
                scope.operation.createOwned(OwnedResourceManifest.io("pin-write", "pin-rollback")) {
                    original = this.original
                    val write = attach("pin-write", syntheticPreparedPinWrite(scope.operation, this.original,
                        PrimaryCredentialPurpose.RESTORE, registry))
                    try {
                        if (revokeBeforeAttachment) scope.authority.revoke()
                        val rollback = attach("pin-rollback", PreparedPrimaryPinRollback(scope.operation, this.original))
                        if (!revokeBeforeAttachment) scope.authority.revoke()
                        throw assertThrows("cleanup linkage must not grant live use after revoke", IllegalStateException::class.java) {
                            write.attachRollback(rollback)
                        }
                    } finally { write.invocationReturned() }
                }
            }
            val owner = checkNotNull(original); owner.release()
            assertTrue("both acquired unentered children must retire", owner.terminalRetirement.await(1, TimeUnit.SECONDS))
            assertFalse(owner.failed); assertEquals(0, scope.pool.occupied)
            assertEquals(0, prefs.editors); assertEquals(0, prefs.commits)
            scope.authority.open(ByteArray(32))
            assertNotNull(scope.authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)))
        }
    }
    @Test fun metadataEntryCannotAdmitOutsideTheExactAuthorityGate() {
        val scope = scope(); val prefs = Preferences(); val handle = prepare(scope, prefs)
        assertThrows(IllegalStateException::class.java) { handle.write.admitMetadata() }
        assertEquals(0, prefs.commits); retire(handle)
    }
    @Test fun anotherOperationFromTheSameEpochCannotAdmitThisOriginal() {
        val scope = scope(); val prefs = Preferences(); val handle = prepare(scope, prefs)
        val other = checkNotNull(scope.authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)))
        try { assertThrows(IllegalStateException::class.java) { other.admitPreparedPin(handle.write) } }
        finally { other.close() }
        assertEquals(0, prefs.commits); retire(handle)
    }
    @Test fun healthyLiveOriginalIsAllowedButItsIncompleteRetirementDeniesBeforeEditorAcquisition() {
        val scope = scope(); val done = java.util.concurrent.CompletableFuture<Unit>()
        val other = scope.operation.createOwned(OwnedResourceManifest.io("independent-live-child")) {
            attach("independent-live-child", object : AcknowledgedCloseable { override fun closeAcknowledged() = done })
        }
        try {
            val allowed = prepare(scope, Preferences()); retire(allowed)
            other.close()
            val prefs = Preferences()
            assertThrows(IllegalStateException::class.java) { prepare(scope, prefs) }
            assertEquals(0, prefs.editors)
        } finally { done.complete(Unit); other.close(); assertTrue(other.retirement.await(5, TimeUnit.SECONDS)) }
        val retry = prepare(scope, Preferences()); retire(retry)
    }
    @Test fun inheritedAuthorityAndStorageGatesAreDeniedBeforeEditorAcquisition() {
        val scope = scope(); val prefs = Preferences()
        assertThrows(IllegalStateException::class.java) { scope.operation.commit { prepare(scope, prefs) } }
        assertThrows(IllegalStateException::class.java) { synchronized(PrimaryVaultSetupGuard.storageLock) { prepare(scope, prefs) } }
        assertEquals(0, prefs.editors)
    }
    @Test fun completeCapacityIsReservedBeforeEitherRestoreEditor() {
        val scope = scope(); val prefs = Preferences()
        val held = scope.pool.reserveAll(Any(), 15)
        try { assertThrows(IllegalStateException::class.java) { prepare(scope, prefs, restore = true) }; assertEquals(0, prefs.editors) }
        finally { held.forEach { it.release() } }
    }
    @Test fun partialEditorPreparationRetiresAndDoesNotQuarantineAnUnexecutedCommand() {
        val scope = scope(); val prefs = Preferences(); val expected = IOException("controlled prepare failure")
        prefs.beforeEdit = { throw expected }
        assertSame(expected, assertThrows(IOException::class.java) { prepare(scope, prefs) })
        val retry = prepare(scope, Preferences()); retry.write.admit(); retry.write.commitExact(); retry.write.finishInstalled(); retire(retry)
    }
    @Test fun partialRestorePreparationBeforeAnyEnteredWriteRetiresBothChildrenAndSealsTheDecision() {
        val scope = scope(); val prefs = Preferences(); val expected = IOException("second editor failed")
        prefs.beforeEdit = { if (prefs.editors == 1) throw expected }
        assertSame(expected, assertThrows(IOException::class.java) { prepare(scope, prefs, restore = true) })
        assertEquals(0, prefs.commits); assertEquals(0, scope.pool.occupied)
        val retry = prepare(scope, Preferences()); retire(retry)
    }
    @Test fun foreignThreadCannotAdmitOrReplaceTheOriginalCommand() {
        val scope = scope(); val prefs = Preferences(); val handle = prepare(scope, prefs)
        val failure = AtomicReference<Throwable>()
        val foreign = Thread { try { handle.write.admit() } catch (problem: Throwable) { failure.set(problem) } }
        foreign.start(); foreign.join(5000); assertFalse(foreign.isAlive); assertTrue(failure.get() is IllegalStateException)
        handle.write.admit(); handle.write.commitExact(); handle.write.finishInstalled(); retire(handle)
    }
    @Test fun admittedCommitSurvivesRevokeButRetainsClaimAndChargeUntilActualReturn() {
        val scope = scope(); val contender = scope(); val prefs = Preferences()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val revoked = CountDownLatch(1)
        prefs.onCommit = { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); true }
        val failure = AtomicReference<Throwable>(); val handle = AtomicReference<Handle>()
        val writer = Thread {
            try { prepare(scope, prefs).also { handle.set(it); it.write.admit(); it.write.commitExact(); it.write.finishInstalled(); retire(it) } }
            catch (problem: Throwable) { failure.set(problem) }
        }
        writer.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val revoker = Thread { scope.authority.revoke(); revoked.countDown() }; revoker.start()
            assertTrue(revoked.await(1, TimeUnit.SECONDS)); revoker.join(5000)
            assertFalse(scope.authority.cleanupComplete); assertTrue(scope.pool.occupied > 0); busy(contender)
        } finally { release.countDown(); writer.join(10000) }
        assertFalse(writer.isAlive); assertNull(failure.get()); assertEquals(1, prefs.commits)
        assertNull(scope.authority.bindingOrNull()); assertTrue(checkNotNull(handle.get()).original.terminalRetirement.isComplete)
        val next = prepare(contender, Preferences()); retire(next)
    }
    @Test fun actualReleaseWrapperReturnStillBlocksReplacementAfterCommitAndCloseStageComplete() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ action(); entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }, returned)
        })
        val scope = scope(pool); val contender = scope(); val handle = prepare(scope, Preferences())
        handle.write.admit(); handle.write.commitExact(); handle.write.finishInstalled(); handle.write.invocationReturned(); handle.original.release()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); assertFalse(handle.original.terminalRetirement.isComplete); busy(contender) }
        finally { release.countDown() }
        assertTrue(handle.original.terminalRetirement.await(5, TimeUnit.SECONDS))
        val next = prepare(contender, Preferences()); retire(next)
    }
    @Test fun restoreRetainsSameClaimAfterWriteReturnUntilExplicitInstalledDecision() {
        val scope = scope(); val contender = scope(); val handle = prepare(scope, Preferences(), restore = true)
        handle.write.admit(); handle.write.commitExact(); handle.write.invocationReturned(); handle.original.release()
        busy(contender); assertFalse(handle.original.terminalRetirement.isComplete)
        assertTrue("undecided rollback must remain physically charged", scope.pool.occupied > 0)
        handle.write.finishInstalled(); assertTrue(handle.original.terminalRetirement.await(5, TimeUnit.SECONDS))
        val next = prepare(contender, Preferences()); retire(next)
    }
    @Test fun exactRestoreRollbackAfterRevokeClearsOnlyItsInstalledRecordAndCannotRunAgain() {
        val scope = scope(); val contender = scope(); val prefs = Preferences(); val handle = prepare(scope, prefs, restore = true)
        assertEquals(2, prefs.editors)
        handle.write.admit(); handle.write.commitExact(); scope.authority.revoke(); handle.write.invocationReturned()
        handle.write.rollbackExact(); retire(handle)
        assertTrue(prefs.values.isEmpty()); assertEquals(2, prefs.commits)
        val replacement = prepare(contender, prefs)
        replacement.write.admit(); replacement.write.commitExact(); replacement.write.finishInstalled(); retire(replacement)
        assertThrows(IllegalStateException::class.java) { handle.write.rollbackExact() }
        assertFalse("even identical new bytes belong to the replacement claim", prefs.values.isEmpty())
    }
    @Test fun falseAndThrowCommitsKeepFailedChargedOriginalsAndStickyQuarantineAcrossNewStoreObjects() {
        for (throws in listOf(false, true)) {
            val isolated = PrimaryCredentialMutationRegistry()
            val scope = scope(); val contender = scope(); val prefs = Preferences()
            prefs.onCommit = { if (throws) throw IOException("controlled durable ambiguity"); false }
            val handle = prepare(scope, prefs, registry = isolated); handle.write.admit()
            assertThrows(Exception::class.java) { handle.write.commitExact() }
            handle.write.invocationReturned(); handle.original.release(); awaitFailed(handle.original)
            assertTrue("failed commit may publish in-memory bytes", prefs.values.isNotEmpty())
            assertTrue(scope.pool.occupied > 0); busy(contender, isolated)

        }
    }
    @Test fun successfulRollbackCannotAcknowledgeEarlierAmbiguousProviderWork() {
        val scope = scope(); val contender = scope(); val prefs = Preferences()
        prefs.onCommit = { prefs.commits > 1 }
        val handle = prepare(scope, prefs, restore = true); handle.write.admit()
        assertThrows(IllegalStateException::class.java) { handle.write.commitExact() }
        handle.write.invocationReturned(); busy(contender)
        handle.write.rollbackExact(); handle.original.release(); awaitFailed(handle.original)
        assertTrue(prefs.values.isEmpty()); assertTrue(scope.pool.occupied > 0); busy(contender)
    }
    private fun awaitFailed(original: ReleaseReservation) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!original.failed && !original.terminalRetirement.isComplete) { check(System.nanoTime() < end); Thread.yield() }
        assertTrue("Ambiguous or failed provider cleanup must keep the original failed", original.failed)
        assertFalse(original.terminalRetirement.isComplete)
    }
    @Test fun falseCommitCanReturnWhileDiskWorkIsHeldAndLaterUnobservedReturnCannotFreeOriginal() {
        val scope = scope(); val contender = scope(); val prefs = Preferences()
        val submitted = CountDownLatch(1); val diskEntered = CountDownLatch(1); val releaseDisk = CountDownLatch(1)
        val diskReturned = CountDownLatch(1)
        val disk = Thread {
            check(submitted.await(10, TimeUnit.SECONDS)); diskEntered.countDown()
            check(releaseDisk.await(10, TimeUnit.SECONDS)); diskReturned.countDown()
        }
        prefs.onCommit = { submitted.countDown(); check(diskEntered.await(5, TimeUnit.SECONDS)); false }
        disk.start()
        val handle = prepare(scope, prefs); handle.write.admit()
        try {
            assertThrows(IllegalStateException::class.java) { handle.write.commitExact() }
            assertEquals(1L, diskReturned.count)
            handle.write.invocationReturned(); handle.original.release(); awaitFailed(handle.original)
            assertTrue(scope.pool.occupied > 0); busy(contender)
            assertThrows(IllegalStateException::class.java) { scope.authority.open(ByteArray(32)) }
        } finally { releaseDisk.countDown(); disk.join(5000) }
        assertFalse(disk.isAlive); assertEquals(0L, diskReturned.count)
        // The fake's observer is deliberately not a supported receipt on the command.
        assertTrue(handle.original.failed); assertFalse(handle.original.terminalRetirement.isComplete); busy(contender)
    }
    @Test fun reentrantProviderCannotDeclareWriteReturnWhileCommitIsStillOnTheStack() {
        val scope = scope(); val prefs = Preferences(); val handle = prepare(scope, prefs)
        prefs.onCommit = {
            assertThrows(IllegalStateException::class.java) { handle.write.invocationReturned() }
            assertFalse(handle.original.terminalRetirement.isComplete)
            true
        }
        handle.write.admit(); handle.write.commitExact(); handle.write.finishInstalled(); retire(handle)
    }
    @Test fun reentrantRollbackProviderCannotFinishOrRepeatItsStillRunningCleanup() {
        val scope = scope(); val prefs = Preferences(); val handle = prepare(scope, prefs, restore = true)
        handle.write.admit(); handle.write.commitExact(); handle.write.invocationReturned()
        prefs.onCommit = {
            assertThrows(IllegalStateException::class.java) { handle.write.finishInstalled() }
            assertThrows(IllegalStateException::class.java) { handle.write.rollbackExact() }
            true
        }
        handle.write.rollbackExact(); retire(handle); assertEquals(2, prefs.commits)
    }
    @Test fun failedRollbackStaysChargedAndQuarantinedEvenIfMemoryLooksEmpty() {
        val scope = scope(); val contender = scope(); val prefs = Preferences()
        prefs.onCommit = { prefs.commits == 1 }
        val handle = prepare(scope, prefs, restore = true)
        handle.write.admit(); handle.write.commitExact(); handle.write.invocationReturned()
        assertThrows(IllegalStateException::class.java) { handle.write.rollbackExact() }
        handle.original.release(); awaitFailed(handle.original)
        assertTrue(prefs.values.isEmpty()); assertTrue(scope.pool.occupied > 0); busy(contender)
    }
    @Test fun rollbackMismatchPreservesForeignWinnerAndKeepsFailedOriginalCharged() {
        val scope = scope(); val contender = scope(); val prefs = Preferences()
        val handle = prepare(scope, prefs, restore = true)
        handle.write.admit(); handle.write.commitExact(); handle.write.invocationReturned()
        val winner = mapOf("foreign-winner" to "must-remain")
        prefs.values = winner
        assertThrows(IllegalStateException::class.java) { handle.write.rollbackExact() }
        handle.original.release(); awaitFailed(handle.original)
        assertEquals(winner, prefs.values); assertEquals(1, prefs.commits); busy(contender)
    }

    private class Preferences : SharedPreferences {
        @Volatile var values: Map<String, Any?> = emptyMap()
        var editors = 0; var commits = 0
        var checkOutside: () -> Unit = {}; var beforeEdit: () -> Unit = {}; var onCommit: () -> Boolean = { true }
        override fun getAll(): MutableMap<String, *> { checkOutside(); return LinkedHashMap(values) }
        override fun edit(): SharedPreferences.Editor {
            checkOutside(); beforeEdit(); editors++
            return object : SharedPreferences.Editor {
                val updates = linkedMapOf<String, String?>(); var clear = false
                override fun putString(key: String?, value: String?) = apply { updates[checkNotNull(key)] = value }
                override fun clear() = apply { clear = true }
                override fun remove(key: String?) = apply { updates[checkNotNull(key)] = null }
                override fun commit(): Boolean {
                    checkOutside(); commits++
                    val next = if (clear) linkedMapOf() else LinkedHashMap(values)
                    updates.forEach { (key, value) -> if (value == null) next.remove(key) else next[key] = value }
                    values = next
                    return onCommit()
                }
                override fun apply() = error("Exact synchronous command only")
                override fun putStringSet(key: String?, values: MutableSet<String>?) = error("Unsupported field")
                override fun putInt(key: String?, value: Int) = error("Unsupported field")
                override fun putLong(key: String?, value: Long) = error("Unsupported field")
                override fun putFloat(key: String?, value: Float) = error("Unsupported field")
                override fun putBoolean(key: String?, value: Boolean) = error("Unsupported field")
            }
        }
        override fun getString(key: String?, defValue: String?): String? = error("Snapshot only")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = error("Snapshot only")
        override fun getInt(key: String?, defValue: Int): Int = error("Snapshot only")
        override fun getLong(key: String?, defValue: Long): Long = error("Snapshot only")
        override fun getFloat(key: String?, defValue: Float): Float = error("Snapshot only")
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = error("Snapshot only")
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = error("No listener")
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = error("No listener")
    }
}
