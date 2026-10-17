package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore

class HiddenCredentialSeparationTest {
    @Test fun setupChangeRecoveryAndFreshPrimaryCreationRejectMatchingCredentials() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val parent = File(base.noBackupFilesDir, "separation-test-$id")
        val preferenceNames = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getPackageName() = "${base.packageName}.test.$id"
            override fun getNoBackupFilesDir(): File = parent
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val qualified = "test-$id-$name"
                preferenceNames.add(qualified)
                return base.getSharedPreferences(qualified, mode)
            }
        }
        val primary = PinVaultKeyStore(context)
        val hidden = HiddenCredentialStore(context)
        var primaryKey: ByteArray? = null
        var hiddenKey: ByteArray? = null
        try {
            val pk = primary.create("123456".toCharArray())
            primaryKey = pk
            val matches: (CharArray) -> Boolean = { candidate ->
                try { primary.unlock(candidate).also { it.fill(0) }; true }
                catch (_: uk.co.traynor.privategallery.core.crypto.InvalidPinException) { false }
            }
            assertTrue(runCatching { hidden.createDifferentFromPrimary("123456".toCharArray(), matches) }.isFailure)
            assertFalse(hidden.isConfigured)
            val hk = hidden.createDifferentFromPrimary("728491".toCharArray(), matches)
            hiddenKey = hk
            assertFalse(pk.contentEquals(hk))
            assertTrue(runCatching { hidden.unlock("123456".toCharArray()) }.isFailure)
            assertTrue(runCatching { primary.unlock("728491".toCharArray()) }.isFailure)
            val record = File(parent, "hidden-v1-default/credential")
            val before = record.readBytes()
            assertTrue(runCatching { primary.changePin("123456".toCharArray(), "728491".toCharArray()) }.isFailure)
            primary.unlock("123456".toCharArray()).fill(0)
            assertTrue(runCatching { primary.replacePinForRecoveredVault("728491".toCharArray(), pk) }.isFailure)
            primary.clearFailedRestore()
            assertTrue(runCatching { primary.create("728491".toCharArray()) }.isFailure)
            primary.replacePinForRecoveredVault("654321".toCharArray(), pk)
            val restored = primary.unlock("654321".toCharArray())
            try { assertArrayEquals(pk, restored) } finally { restored.fill(0) }
            assertArrayEquals(before, record.readBytes())
            val unlocked = hidden.unlock("728491".toCharArray())
            try { assertArrayEquals(hk, unlocked) } finally { unlocked.fill(0) }
        } finally {
            primaryKey?.fill(0); hiddenKey?.fill(0)
            preferenceNames.forEach { base.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("${context.packageName}.hidden.v1.default") }
            parent.deleteRecursively()
        }
    }
}
