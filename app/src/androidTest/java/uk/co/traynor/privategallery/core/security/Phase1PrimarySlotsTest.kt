package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Phase1PrimarySlotsTest {
    @Test fun foreignIdentityRejectedBeforeAnyRootAccess() {
        var accesses = 0
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getFilesDir(): File { accesses++; error("foreign root consulted") }
        }
        assertThrows(IllegalStateException::class.java) { LegacyPrimaryContainer(context, ContainerId.synthetic()) }
        assertEquals(0, accesses)
    }

    @Test fun partialRecoveryOrBiometricSlotsNeverPermitFreshSetup() = isolated { context ->
        for (slot in listOf("vault-recovery-envelope", "vault-biometric-envelope", "vault-key-envelope")) {
            val prefs = context.getSharedPreferences(slot, Context.MODE_PRIVATE)
            prefs.edit().putString("partial", "synthetic-canary").commit()
            val keys = PinVaultKeyStore(context)
            assertTrue(keys.isConfigured)
            assertThrows(IllegalStateException::class.java) { keys.create("123456".toCharArray()) }
            assertEquals("synthetic-canary", prefs.getString("partial", null))
            prefs.edit().clear().commit()
        }
    }

    @Test fun pinChangeAndRecoveryTouchOnlyFixedPrimarySlotsAndPreserveVdek() = isolated { context ->
        val foreign = context.getSharedPreferences("synthetic-foreign-slot", Context.MODE_PRIVATE)
        foreign.edit().putString("canary", "untouched").commit()
        val before = foreign.all.toMap()
        val slots = PrimaryKeySlots(context)
        val original = slots.pin.create("123456".toCharArray())
        val authority = PrimarySessionAuthority()
        var secret: CharArray? = null
        try {
            authority.open(original.copyOf())
            checkNotNull(authority.operationOrNull()).use { operation ->
                secret = slots.prepareRecovery(operation, false)
                slots.confirmRecovery(operation, checkNotNull(secret).copyOf())
                slots.changePin(operation, "123456".toCharArray(), "654321".toCharArray())
                val changed = slots.pin.unlock("654321".toCharArray())
                try { assertArrayEquals(original, changed) } finally { changed.fill(0) }
                operation.fork(setOf(PrimaryScope.READ)).use { readOnly ->
                    assertThrows(IllegalStateException::class.java) { slots.changePin(readOnly, "654321".toCharArray(), "111111".toCharArray()) }
                    assertThrows(IllegalStateException::class.java) { slots.confirmRecovery(readOnly, checkNotNull(secret).copyOf()) }
                }
            }
            authority.revoke()
            val recovered = slots.recover(checkNotNull(secret).copyOf(), "222222".toCharArray()) { it() }
            try { assertArrayEquals(original, recovered) } finally { recovered.fill(0) }
            val after = slots.pin.unlock("222222".toCharArray())
            try { assertArrayEquals(original, after) } finally { after.fill(0) }
            assertEquals(before, foreign.all)
        } finally { original.fill(0); secret?.fill('\u0000'); authority.revoke() }
    }

    private fun isolated(test: (Context) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val id = "phase1-slots-${java.util.UUID.randomUUID()}"
        val root = File(base.cacheDir, id).apply { mkdirs() }
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val scoped = "$id-$name"; names += scoped
                return base.getSharedPreferences(scoped, mode)
            }
        }
        try { test(context) } finally { names.forEach { base.getSharedPreferences(it, 0).edit().clear().commit() }; root.deleteRecursively() }
    }
}
