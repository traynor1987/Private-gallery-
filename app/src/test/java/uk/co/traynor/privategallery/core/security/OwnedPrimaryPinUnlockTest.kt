package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.security.AlgorithmParameters
import java.security.Key
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.SecretKey
import org.bouncycastle.crypto.generators.SCrypt
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.crypto.PinEnvelope

class OwnedPrimaryPinUnlockTest {
    private fun prepare(authority: PrimarySessionAuthority, attempt: PrimaryAuthAttempt, pin: CharArray,
        preferences: SharedPreferences): OwnedAuthenticationKey = preparePrimaryPinAuthentication(authority, attempt, pin, source(preferences))
    private fun source(preferences: SharedPreferences,
        decode: (String) -> ByteArray = { Base64.getDecoder().decode(it) },
        derive: (ByteArray, ByteArray) -> ByteArray = { pin, salt -> SCrypt.generate(pin, salt, 32768, 8, 1, 32) },
        cipher: () -> Cipher = { Cipher.getInstance("AES/GCM/NoPadding") }): PrimaryPinUnlockSource {
        val constructor = PrimaryPinUnlockSource::class.java.getDeclaredConstructor(SharedPreferences::class.java,
            Function1::class.java, Function2::class.java, Function0::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(preferences, decode, derive, cipher)
    }
    private fun preferences(values: Map<String, String> = mapOf(
        "salt" to Base64.getEncoder().encodeToString(ByteArray(16)),
        "nonce" to Base64.getEncoder().encodeToString(ByteArray(12)),
        "ciphertext" to Base64.getEncoder().encodeToString(ByteArray(48))), beforeRead: () -> Unit = {}): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
    ) { _, method, _ ->
        check(method.name == "getAll"); beforeRead()
        values
    } as SharedPreferences
    private fun pool(authority: PrimarySessionAuthority): ReleasePool = authority.javaClass.getDeclaredField("ioReleasePool")
        .apply { isAccessible = true }.get(authority) as ReleasePool
    private fun awaitCleanup(authority: PrimarySessionAuthority) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < end) Thread.yield()
        assertTrue(authority.cleanupComplete); assertEquals(0, pool(authority).occupied)
    }
    private fun rootDestination(authority: PrimarySessionAuthority): ByteArray? {
        val resources = authority.javaClass.getDeclaredField("reservedResources").apply { isAccessible = true }.get(authority) as Set<*>
        for (resource in resources) {
            val children = resource!!.javaClass.getDeclaredField("actualIdentities").apply { isAccessible = true }.get(resource) as Array<*>
            val root = children[0]
            if (root is OriginalAuthenticationKey) return root.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(root) as ByteArray?
        }
        error("Missing original result")
    }

    @Test fun completePhaseBudgetMustExistBeforeEncryptedSnapshotOrProviderAcquisition() {
        val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
        val held = authority.createAttemptOwned(attempt, OwnedResourceManifest.io("hold0", "hold1", "hold2", "hold3", "hold4", "hold5")) {
            val root = attach("hold0", AutoCloseable {})
            for (index in 1..5) attach("hold$index", AutoCloseable {})
            root
        }
        var reads = 0; val pin = "1234".toCharArray()
        try {
            assertThrows(Exception::class.java) { prepare(authority, attempt, pin, preferences { reads++ }) }
            assertEquals("all eleven obligations must be funded before reading", 0, reads)
        } finally {
            held.close(); assertTrue(held.retirement.await(5, TimeUnit.SECONDS)); authority.revoke(); awaitCleanup(authority)
        }
        assertArrayEquals(CharArray(4), pin)
    }

    @Test fun legacyAsciiPinsAndFourDigitVaultsKeepTheirExistingCryptoFormat() {
        for (text in listOf("1234", "123456", "a!\u007f", "a".repeat(257), "")) {
            val expected = ByteArray(32) { 29 }
            val envelope = PinEnvelope.create(text.toCharArray(), expected)
            val encoded = mapOf("salt" to Base64.getEncoder().encodeToString(envelope.salt),
                "nonce" to Base64.getEncoder().encodeToString(envelope.nonce),
                "ciphertext" to Base64.getEncoder().encodeToString(envelope.ciphertext))
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); val pin = text.toCharArray()
            val result = prepare(authority, attempt, pin, preferences(encoded) {
                assertEquals(11, pool(authority).occupied); assertNotNull(rootDestination(authority))
            })
            assertArrayEquals(CharArray(text.length), pin)
            assertEquals(1, pool(authority).occupied)
            assertTrue(authority.completeAuthentication(attempt, result))
            assertTrue(result.retirement.await(5, TimeUnit.SECONDS))
            checkNotNull(authority.operationOrNull()).use { assertArrayEquals(expected, it.key) }
            authority.revoke(); awaitCleanup(authority)
        }
    }

    @Test fun cancellationDuringCipherRetainsExactBorrowThenWipesLateOutputOnReturnOrThrow() {
        for (throwAfterWrite in listOf(false, true)) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
            val pin = "1234".toCharArray(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val arrays = mutableListOf<ByteArray>(); val failure = AtomicReference<Throwable>()
            val destination = AtomicReference<ByteArray>(); val early = AtomicReference<ByteArray>()
            val spi = TestCipherSpi()
            spi.final = { output ->
                destination.set(output); assertSame(early.get(), output)
                output.fill(7); entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
                assertEquals('1', pin[0]); assertEquals(7.toByte(), output[0]); assertTrue(spi.encoded!!.any { it != 0.toByte() })
                output.fill(9)
                if (throwAfterWrite) throw java.io.IOException("late provider failure")
                32
            }
            val src = source(preferences { early.set(checkNotNull(rootDestination(authority))); assertEquals(11, pool(authority).occupied) },
                decode = { Base64.getDecoder().decode(it).also(arrays::add) },
                derive = { bytes, _ -> arrays += bytes; ByteArray(32) { 4 }.also(arrays::add) }, cipher = { TestCipher(spi) })
            val worker = Thread { try { preparePrimaryPinAuthentication(authority, attempt, pin, src) } catch (problem: Throwable) { failure.set(problem) } }
            worker.start()
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.cancelAuthentication(attempt)
                assertFalse(authority.cleanupComplete); assertEquals(11, pool(authority).occupied)
                assertEquals(7.toByte(), destination.get()[0]); assertTrue(spi.encoded!!.any { it != 0.toByte() })
                assertThrows(IllegalStateException::class.java) { authority.open(ByteArray(32)) }
            } finally { release.countDown(); worker.join(10000) }
            assertFalse(worker.isAlive); assertNotNull(failure.get()); awaitCleanup(authority)
            assertArrayEquals(ByteArray(32), destination.get()); arrays.forEach { assertTrue(it.all { byte -> byte == 0.toByte() }) }
            assertArrayEquals(CharArray(4), pin); assertNull(authority.bindingOrNull())
            assertThrows(IllegalStateException::class.java) { spi.key!!.encoded }
        }
    }

    @Test fun cancellationDuringKdfKeepsInputAndReturnedWrappingKeyOwnedWithoutCipherEntry() {
        val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); val pin = "1234".toCharArray()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val input = AtomicReference<ByteArray>()
        val output = ByteArray(32) { 8 }; val failure = AtomicReference<Throwable>(); var cipherCalls = 0
        val src = source(preferences(), derive = { bytes, _ ->
            input.set(bytes); entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); assertEquals('1'.code.toByte(), bytes[0]); output
        }, cipher = { cipherCalls++; error("No fresh Cipher after revoke") })
        val worker = Thread { try { preparePrimaryPinAuthentication(authority, attempt, pin, src) } catch (problem: Throwable) { failure.set(problem) } }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertEquals('1'.code.toByte(), input.get()[0]); assertEquals('1', pin[0]); assertEquals(11, pool(authority).occupied)
        } finally { release.countDown(); worker.join(10000) }
        assertFalse(worker.isAlive); assertNotNull(failure.get()); assertEquals(0, cipherCalls); awaitCleanup(authority)
        assertArrayEquals(ByteArray(32), output); assertTrue(input.get().all { it == 0.toByte() }); assertArrayEquals(CharArray(4), pin)
    }

    @Test fun allPartialProviderFailuresRetireEveryAcquiredArrayAndTheEarlyResult() {
        for (point in listOf("snapshot", "decode", "kdf", "cipher", "init", "final", "bad-tag")) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); val pin = "1234".toCharArray()
            val arrays = mutableListOf<ByteArray>(); var destination: ByteArray? = null; var decoded = 0
            val expected = IllegalStateException("failure at $point")
            val spi = TestCipherSpi().also {
                it.initializing = { if (point == "init") throw expected }
                it.final = { output -> output.fill(3); if (point == "bad-tag") throw javax.crypto.AEADBadTagException(); throw expected }
            }
            val src = source(preferences { destination = rootDestination(authority); if (point == "snapshot") throw expected },
                decode = { if (point == "decode" && ++decoded == 2) throw expected; Base64.getDecoder().decode(it).also(arrays::add) },
                derive = { input, _ -> arrays += input; if (point == "kdf") throw expected; ByteArray(32) { 6 }.also(arrays::add) },
                cipher = { if (point == "cipher") throw expected; TestCipher(spi) })
            val caught = assertThrows(Exception::class.java) { preparePrimaryPinAuthentication(authority, attempt, pin, src) }
            if (point == "bad-tag") assertTrue(caught is uk.co.traynor.privategallery.core.crypto.InvalidPinException) else assertSame(expected, caught)
            awaitCleanup(authority); assertNotNull(destination); assertArrayEquals(ByteArray(32), destination)
            arrays.forEach { assertTrue(it.all { byte -> byte == 0.toByte() }) }; assertArrayEquals(CharArray(4), pin)
            authority.revoke()
        }
    }

    @Test fun nonAsciiAndStaleAttemptsCannotEnterKdf() {
        for (stale in listOf(false, true)) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); var kdfCalls = 0
            if (stale) authority.cancelAuthentication(attempt)
            val pin = "12\u00e934".toCharArray()
            val src = source(preferences(), derive = { _, _ -> kdfCalls++; ByteArray(32) })
            assertThrows(Exception::class.java) { preparePrimaryPinAuthentication(authority, attempt, pin, src) }
            awaitCleanup(authority); assertEquals(0, kdfCalls); assertArrayEquals(CharArray(5), pin); authority.revoke()
        }
    }

    @Test fun heldActualPhaseReleaseReturnPreventsHandoffDespiteCompletedChildStages() {
        val held = CountDownLatch(1); val release = CountDownLatch(1)
        val first = java.util.concurrent.atomic.AtomicBoolean(true)
        val pool = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ action(); if (first.compareAndSet(true, false)) { held.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }, returned)
        })
        val authority = testPrimaryAuthority()
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        val attempt = authority.beginAuthentication(); val result = AtomicReference<OwnedAuthenticationKey>(); val failure = AtomicReference<Throwable>()
        val src = source(preferences(), derive = { _, _ -> ByteArray(32) { 2 } }, cipher = { TestCipher(TestCipherSpi()) })
        val worker = Thread { try { result.set(preparePrimaryPinAuthentication(authority, attempt, "1234".toCharArray(), src)) } catch (problem: Throwable) { failure.set(problem) } }
        worker.start()
        try {
            assertTrue(held.await(5, TimeUnit.SECONDS)); assertNull(result.get()); assertNull(authority.bindingOrNull())
            assertTrue(pool.occupied >= 4); assertFalse(authority.cleanupComplete)
        } finally { release.countDown(); worker.join(10000) }
        assertFalse(worker.isAlive); assertNull(failure.get())
        val handoff = checkNotNull(result.get()); assertTrue(authority.completeAuthentication(attempt, handoff))
        assertTrue(handoff.retirement.await(5, TimeUnit.SECONDS)); authority.revoke(); awaitCleanup(authority)
    }

    @Test fun failedPhaseReleaseStaysChargedAndDeniesEveryFreshAttempt() {
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        val pool = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ if (fail.compareAndSet(true, false)) error("controlled actual cleanup failure"); action() }, returned)
        })
        val authority = testPrimaryAuthority()
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        val attempt = authority.beginAuthentication(); val failure = AtomicReference<Throwable>(); val interrupted = java.util.concurrent.atomic.AtomicBoolean()
        var output: ByteArray? = null
        val spi = TestCipherSpi().also { it.final = { destination -> output = destination; destination.fill(3); 32 } }
        val src = source(preferences(), derive = { _, _ -> ByteArray(32) { 2 } }, cipher = { TestCipher(spi) })
        val worker = Thread {
            try { preparePrimaryPinAuthentication(authority, attempt, "1234".toCharArray(), src) }
            catch (problem: Throwable) { failure.set(problem); interrupted.set(Thread.currentThread().isInterrupted) }
        }
        worker.start()
        try {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!pool.hasFailedRetirement && System.nanoTime() < end) Thread.yield()
            assertTrue(pool.hasFailedRetirement); assertFalse(authority.cleanupComplete)
        } finally { worker.interrupt(); worker.join(10000) }
        assertFalse(worker.isAlive); assertTrue(failure.get() is InterruptedException); assertTrue(interrupted.get())
        assertArrayEquals(ByteArray(32), checkNotNull(output)); assertTrue(pool.occupied > 0)
        var reads = 0
        repeat(2) {
            val next = authority.beginAuthentication(); val pin = "1234".toCharArray()
            assertThrows(IllegalStateException::class.java) { prepare(authority, next, pin, preferences { reads++ }) }
            assertArrayEquals(CharArray(4), pin)
        }
        assertEquals(0, reads); assertTrue(pool.hasFailedRetirement); assertFalse(authority.cleanupComplete); authority.revoke()
    }

    @Test fun rawKeyBorrowRejectsForeignThreadSerializationAndPostReturnReads() {
        val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); val spi = TestCipherSpi()
        val wrapping = ByteArray(32) { 3 }; val failure = AtomicReference<Throwable>()
        spi.initializing = {
            assertSame(wrapping, spi.encoded)
            val foreign = Thread { try { spi.key!!.encoded; failure.set(AssertionError("Foreign key read admitted")) } catch (_: IllegalStateException) { } }
            foreign.start(); foreign.join(5000); assertFalse(foreign.isAlive); assertNull(failure.get())
            assertThrows(java.io.NotSerializableException::class.java) {
                java.io.ObjectOutputStream(java.io.ByteArrayOutputStream()).use { it.writeObject(spi.key) }
            }
        }
        val src = source(preferences(), derive = { _, _ -> wrapping }, cipher = { TestCipher(spi) })
        val result = preparePrimaryPinAuthentication(authority, attempt, "1234".toCharArray(), src)
        assertArrayEquals(ByteArray(32), wrapping); assertThrows(IllegalStateException::class.java) { spi.key!!.encoded }
        result.close(); assertTrue(result.retirement.await(5, TimeUnit.SECONDS)); authority.revoke(); awaitCleanup(authority)
    }

    @Test fun inheritedAuthorityStorageAndPreferenceGatesRejectBeforeSnapshotRead() {
        for (gate in listOf("authority", "storage", "preferences")) {
            val authority = testPrimaryAuthority(); var ticketsUnderGate = 0; var forbidden: Any? = null
            val isolated = ReleasePool(16, allocateTicket = { owner, slot ->
                if (forbidden?.let { Thread.holdsLock(it) } == true) ticketsUnderGate++
                ReleaseTicket(owner, slot)
            })
            authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, isolated)
            val attempt = authority.beginAuthentication(); var reads = 0
            val prefs = preferences { reads++ }; val pin = "1234".toCharArray()
            val lock = when (gate) {
                "authority" -> authority.javaClass.getDeclaredField("gate").apply { isAccessible = true }.get(authority)
                "storage" -> PrimaryVaultSetupGuard.storageLock
                else -> prefs
            }
            forbidden = lock
            synchronized(lock) { assertThrows(IllegalStateException::class.java) { prepare(authority, attempt, pin, prefs) } }
            awaitCleanup(authority); assertEquals(0, reads); assertArrayEquals(CharArray(4), pin); authority.revoke()
            assertEquals("No release-worker startup or retirement wait under $gate gate", 0, ticketsUnderGate)
        }
    }

    @Test fun oversizedEncryptedRecordsRejectBeforeAnyDecodeOrProvider() {
        for (field in listOf("salt", "nonce", "ciphertext")) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
            val values = mapOf("salt" to "A".repeat(24), "nonce" to "A".repeat(16), "ciphertext" to "A".repeat(64)).toMutableMap()
            values[field] = "A".repeat(4096)
            var decodes = 0; var providers = 0; val pin = "1234".toCharArray()
            val src = source(preferences(values), decode = { decodes++; Base64.getDecoder().decode(it) },
                derive = { _, _ -> providers++; ByteArray(32) }, cipher = { providers++; TestCipher(TestCipherSpi()) })
            assertThrows(IllegalStateException::class.java) { preparePrimaryPinAuthentication(authority, attempt, pin, src) }
            awaitCleanup(authority); assertEquals(0, decodes); assertEquals(0, providers)
            assertArrayEquals(CharArray(4), pin); authority.revoke()
        }
    }

    @Test fun decodedWrongSizeIsOwnedAndWipedBeforeAnyKdfOrCipher() {
        for ((field, wrongSize) in listOf("salt" to 15, "nonce" to 11, "ciphertext" to 47)) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
            val values = mapOf("salt" to ByteArray(16) { 1 }, "nonce" to ByteArray(12) { 2 }, "ciphertext" to ByteArray(48) { 3 }).toMutableMap()
            val selected = listOf("salt", "nonce", "ciphertext").indexOf(field)
            val arrays = mutableListOf<ByteArray>(); var providers = 0; val pin = "1234".toCharArray()
            val src = source(preferences(values.mapValues { Base64.getEncoder().encodeToString(it.value) }),
                decode = { (if (arrays.size == selected) ByteArray(wrongSize) { 4 } else Base64.getDecoder().decode(it)).also(arrays::add) },
                derive = { _, _ -> providers++; ByteArray(32) }, cipher = { providers++; TestCipher(TestCipherSpi()) })
            assertThrows(IllegalStateException::class.java) { preparePrimaryPinAuthentication(authority, attempt, pin, src) }
            awaitCleanup(authority); assertTrue(arrays.isNotEmpty()); assertEquals(0, providers)
            arrays.forEach { assertArrayEquals(ByteArray(it.size), it) }; assertArrayEquals(CharArray(4), pin); authority.revoke()
        }
    }

    @Test fun legacySkippedCharactersAndOptionalPaddingUseBoundedDecoderInputs() {
        val expected = ByteArray(32) { 19 }; val envelope = PinEnvelope.create("1234".toCharArray(), expected)
        val original = mapOf("salt" to envelope.salt, "nonce" to envelope.nonce, "ciphertext" to envelope.ciphertext)
            .mapValues { Base64.getEncoder().encodeToString(it.value) }
        for (unpadded in listOf(false, true)) {
            val values = original.mapValues { (_, value) ->
                (if (unpadded) value.trimEnd('=') else value).toCharArray().joinToString(" \r\n\t!?_\u00e9\ud83d\ude00\ud800") + " ".repeat(4096)
            }
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); var decodes = 0
            val src = source(preferences(values), decode = {
                assertTrue(it.length <= listOf(24, 16, 64)[decodes++]); Base64.getDecoder().decode(it)
            })
            val result = preparePrimaryPinAuthentication(authority, attempt, "1234".toCharArray(), src)
            assertEquals(3, decodes); assertTrue(authority.completeAuthentication(attempt, result))
            assertTrue(result.retirement.await(5, TimeUnit.SECONDS))
            checkNotNull(authority.operationOrNull()).use { assertArrayEquals(expected, it.key) }
            authority.revoke(); awaitCleanup(authority)
        }
    }

    @Test fun malformedRecordShapeRejectsBeforeAnyDecoderOrProvider() {
        for (bad in listOf("", "A".repeat(21), "A".repeat(22) + "=", "=" + "A".repeat(21) + "==", "A".repeat(24))) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication(); var calls = 0
            val src = source(preferences(mapOf("salt" to bad, "nonce" to "A".repeat(16), "ciphertext" to "A".repeat(64))),
                decode = { calls++; error("Invalid record reached decoder") }, derive = { _, _ -> calls++; ByteArray(32) },
                cipher = { calls++; TestCipher(TestCipherSpi()) })
            assertThrows(IllegalStateException::class.java) { preparePrimaryPinAuthentication(authority, attempt, "1234".toCharArray(), src) }
            awaitCleanup(authority); assertEquals(0, calls); authority.revoke()
        }
    }

    @Test fun timeoutDuringHeldCipherInitRetainsBorrowUntilReturnAndDeniesDecrypt() {
        val now = java.util.concurrent.atomic.AtomicLong(0)
        val authority = testPrimaryAuthority { now.get() }; val attempt = authority.beginAuthentication()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val failure = AtomicReference<Throwable>()
        val wrapping = ByteArray(32) { 6 }; val pin = "1234".toCharArray(); var finals = 0
        val spi = TestCipherSpi().also {
            it.initializing = { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); assertEquals(6.toByte(), wrapping[0]) }
            it.final = { finals++; 32 }
        }
        val src = source(preferences(), derive = { _, _ -> wrapping }, cipher = { TestCipher(spi) })
        val worker = Thread { try { preparePrimaryPinAuthentication(authority, attempt, pin, src) } catch (problem: Throwable) { failure.set(problem) } }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); now.set(60_000)
            assertThrows(IllegalStateException::class.java) { authority.checkPreparation(attempt) }
            assertEquals(11, pool(authority).occupied); assertEquals('1', pin[0]); assertEquals(6.toByte(), wrapping[0])
        } finally { release.countDown(); worker.join(10000) }
        assertFalse(worker.isAlive); assertNotNull(failure.get()); assertEquals(0, finals); awaitCleanup(authority)
        assertArrayEquals(ByteArray(32), wrapping); assertArrayEquals(CharArray(4), pin); assertNull(authority.bindingOrNull())
        assertThrows(IllegalStateException::class.java) { spi.key!!.encoded }; authority.revoke()
    }

    @Test fun foreignAttemptAndRepeatedCompletedAttemptNeverAcquireAgain() {
        val authority = testPrimaryAuthority(); val other = testPrimaryAuthority()
        val foreign = other.beginAuthentication(); var reads = 0
        val pin = "1234".toCharArray()
        assertThrows(IllegalStateException::class.java) { prepare(authority, foreign, pin, preferences { reads++ }) }
        assertEquals(0, reads); assertArrayEquals(CharArray(4), pin); awaitCleanup(authority)
        val attempt = authority.beginAuthentication()
        val src = source(preferences { reads++ }, derive = { _, _ -> ByteArray(32) }, cipher = { TestCipher(TestCipherSpi()) })
        val result = preparePrimaryPinAuthentication(authority, attempt, "1234".toCharArray(), src)
        assertTrue(authority.completeAuthentication(attempt, result)); assertTrue(result.retirement.await(5, TimeUnit.SECONDS))
        val repeatPin = "1234".toCharArray()
        assertThrows(IllegalStateException::class.java) { preparePrimaryPinAuthentication(authority, attempt, repeatPin, src) }
        assertEquals(1, reads); assertArrayEquals(CharArray(4), repeatPin)
        authority.revoke(); other.revoke(); awaitCleanup(authority); awaitCleanup(other)
    }

    private class TestCipher(spi: CipherSpi) : Cipher(spi, TestProvider(), "AES/GCM/NoPadding")
    private class TestProvider : Provider("ControlledPin", 1.0, "Synchronous private host fixture")
    private class TestCipherSpi : CipherSpi() {
        var encoded: ByteArray? = null; var key: SecretKey? = null
        var initializing: () -> Unit = {}
        var final: (ByteArray) -> Int = { it.fill(5); 32 }
        override fun engineInit(mode: Int, key: Key, random: SecureRandom?) = engineInit(mode, key, null as AlgorithmParameterSpec?, random)
        override fun engineInit(mode: Int, key: Key, parameters: AlgorithmParameters?, random: SecureRandom?) = engineInit(mode, key, null as AlgorithmParameterSpec?, random)
        override fun engineInit(mode: Int, key: Key, parameters: AlgorithmParameterSpec?, random: SecureRandom?) {
            this.key = key as SecretKey; encoded = key.encoded; assertSame(encoded, key.encoded); initializing()
        }
        override fun engineDoFinal(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int {
            assertEquals(0, outputOffset); return final(output)
        }
        override fun engineDoFinal(input: ByteArray, offset: Int, length: Int): ByteArray = error("Caller-owned output required")
        override fun engineSetMode(mode: String) = Unit
        override fun engineSetPadding(padding: String) = Unit
        override fun engineGetBlockSize() = 16
        override fun engineGetOutputSize(length: Int) = 32
        override fun engineGetIV(): ByteArray? = null
        override fun engineGetParameters(): AlgorithmParameters? = null
        override fun engineUpdate(input: ByteArray, offset: Int, length: Int): ByteArray = error("No update")
        override fun engineUpdate(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int = error("No update")
    }
}
