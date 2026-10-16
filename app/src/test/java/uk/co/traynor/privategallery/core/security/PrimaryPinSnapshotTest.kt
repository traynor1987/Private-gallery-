package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class PrimaryPinSnapshotTest {
    private fun record(suffix: String = "old"): Map<String, Any?> = mapOf("salt" to "salt-$suffix", "nonce" to "nonce-$suffix", "ciphertext" to "ciphertext-$suffix")
    private val decode: (String?) -> ByteArray = { checkNotNull(it).toByteArray() }
    @Test fun oneSnapshotFeedsUnwrapOutsidePreferenceMonitorAndWipesPin() {
        val prefs = Preferences(record()); val pin = "123456".toCharArray(); val key = ByteArray(32) { 4 }
        val actual = unlockPrimaryPinSnapshot(prefs, pin, decode) { borrowed, envelope ->
            assertFalse(Thread.holdsLock(prefs)); assertSame(pin, borrowed); assertTrue(pin.any { it != '\u0000' })
            assertEquals("salt-old", envelope.salt.toString(Charsets.UTF_8))
            assertEquals("nonce-old", envelope.nonce.toString(Charsets.UTF_8))
            assertEquals("ciphertext-old", envelope.ciphertext.toString(Charsets.UTF_8)); key
        }
        assertSame(key, actual); assertEquals(1, prefs.reads.get()); assertTrue(pin.all { it == '\u0000' }); key.fill(0)
    }
    @Test fun inheritedMonitorDeniesBeforeReadingOrKdfAndWipesInput() {
        val prefs = Preferences(record()); val pin = "123456".toCharArray(); var entered = false
        synchronized(prefs) {
            assertThrows(IllegalStateException::class.java) {
                unlockPrimaryPinSnapshot(prefs, pin, decode) { _, _ -> entered = true; ByteArray(32) }
            }
        }
        assertFalse(entered); assertEquals(0, prefs.reads.get()); assertTrue(pin.all { it == '\u0000' })
    }
    @Test fun missingAndMalformedEntriesFailBeforeUnwrap() {
        for (values in listOf(record().minus("nonce"), record() + ("nonce" to 123))) {
            val prefs = Preferences(values); val pin = "123456".toCharArray(); var entered = false
            assertThrows(IllegalStateException::class.java) {
                unlockPrimaryPinSnapshot(prefs, pin, decode) { _, _ -> entered = true; ByteArray(32) }
            }
            assertFalse(entered); assertEquals(1, prefs.reads.get()); assertTrue(pin.all { it == '\u0000' })
        }
    }
    @Test fun preferenceFailureWipesInputWithoutUnwrap() {
        val expected = IOException("controlled read failure"); val prefs = Preferences(record()) { throw expected }
        val pin = "123456".toCharArray(); var entered = false
        assertSame(expected, assertThrows(IOException::class.java) {
            unlockPrimaryPinSnapshot(prefs, pin, decode) { _, _ -> entered = true; ByteArray(32) }
        })
        assertFalse(entered); assertTrue(pin.all { it == '\u0000' })
    }
    @Test fun unwrapFailureWipesInputAfterActualReturn() {
        val prefs = Preferences(record()); val pin = "123456".toCharArray(); val expected = SecurityException("controlled unwrap failure")
        assertSame(expected, assertThrows(SecurityException::class.java) {
            unlockPrimaryPinSnapshot(prefs, pin, decode) { _, _ -> assertTrue(pin.any { it != '\u0000' }); throw expected }
        })
        assertTrue(pin.all { it == '\u0000' })
    }
    @Test fun writerCanProgressWhileKdfConsumesOneOriginalSnapshot() {
        val prefs = Preferences(record()); val pin = "123456".toCharArray(); val key = ByteArray(32) { 3 }
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val written = CountDownLatch(1)
        val failure = AtomicReference<Throwable>(); val result = AtomicReference<ByteArray>()
        val reader = Thread {
            try { result.set(unlockPrimaryPinSnapshot(prefs, pin, decode) { _, envelope ->
                assertFalse(Thread.holdsLock(prefs)); entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
                assertEquals("salt-old", envelope.salt.toString(Charsets.UTF_8)); assertEquals("nonce-old", envelope.nonce.toString(Charsets.UTF_8))
                assertEquals("ciphertext-old", envelope.ciphertext.toString(Charsets.UTF_8)); key
            }) } catch (problem: Throwable) { failure.set(problem) }
        }
        val writer = Thread { synchronized(prefs) { prefs.values = record("new") }; written.countDown() }
        reader.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); writer.start()
            assertTrue("KDF must not retain the application preference monitor", written.await(1, TimeUnit.SECONDS))
            assertTrue(pin.any { it != '\u0000' })
        } finally { release.countDown(); reader.join(10000); if (writer.state != Thread.State.NEW) writer.join(5000) }
        assertFalse(reader.isAlive); assertFalse(writer.isAlive); assertNull(failure.get()); assertSame(key, result.get())
        assertEquals(record("new"), prefs.values); assertEquals(1, prefs.reads.get()); assertTrue(pin.all { it == '\u0000' }); key.fill(0)
    }
    private class Preferences(@Volatile var values: Map<String, Any?>, val beforeRead: () -> Unit = {}) : SharedPreferences {
        val reads = AtomicInteger()
        override fun getAll(): MutableMap<String, *> = synchronized(this) { reads.incrementAndGet(); beforeRead(); LinkedHashMap(values) }
        override fun getString(key: String?, defValue: String?): String? = error("No fallback getter")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = error("No fallback getter")
        override fun getInt(key: String?, defValue: Int): Int = error("No fallback getter")
        override fun getLong(key: String?, defValue: Long): Long = error("No fallback getter")
        override fun getFloat(key: String?, defValue: Float): Float = error("No fallback getter")
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = error("No fallback getter")
        override fun contains(key: String?): Boolean = error("No fallback getter")
        override fun edit(): SharedPreferences.Editor = error("Read only")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = error("No listener")
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = error("No listener")
    }
}
