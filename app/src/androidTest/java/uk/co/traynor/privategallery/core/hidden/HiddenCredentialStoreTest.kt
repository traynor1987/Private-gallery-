package uk.co.traynor.privategallery.core.hidden

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Disposable namespaced credentials only. Never accesses the owner/default vault or media. */
class HiddenCredentialStoreTest {
    private fun withStore(test: (HiddenCredentialStore, File, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-${UUID.randomUUID()}"
        val root = File(context.noBackupFilesDir, "hidden-v1-$namespace")
        val alias = "${context.packageName}.hidden.v1.$namespace"
        try { test(HiddenCredentialStore(context, namespace), root, alias) }
        finally {
            root.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }

    @Test fun independentlyGeneratedKeySurvivesAdapterRecreationAndWrongPinDoesNotOverwrite() = withStore { store, root, _ ->
        assertFalse(store.isConfigured)
        val pin = "728491".toCharArray()
        val key = store.create(pin)
        try {
            assertArrayEquals(CharArray(6), pin)
            assertTrue(store.isConfigured)
            val before = File(root, "credential").readBytes()
            val wrong = "123456".toCharArray()
            assertTrue(runCatching { store.unlock(wrong) }.isFailure)
            assertArrayEquals(CharArray(6), wrong)
            assertArrayEquals(before, File(root, "credential").readBytes())
            assertTrue(runCatching { store.create("728491".toCharArray()) }.isFailure)
            val namespace = root.name.removePrefix("hidden-v1-")
            val fresh = HiddenCredentialStore(InstrumentationRegistry.getInstrumentation().targetContext, namespace)
            val unlocked = fresh.unlock("728491".toCharArray())
            try { assertArrayEquals(key, unlocked) } finally { unlocked.fill(0) }
            assertEquals(116, before.size)
        } finally { key.fill(0) }
    }

    @Test fun corruptionAndMissingDeviceKeyFailClosedWithoutReplacingCredentials() = withStore { store, root, alias ->
        store.create("728491".toCharArray()).fill(0)
        val record = File(root, "credential")
        val original = record.readBytes()
        val corrupt = original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        record.writeBytes(corrupt)
        assertTrue(runCatching { store.unlock("728491".toCharArray()) }.isFailure)
        assertArrayEquals(corrupt, record.readBytes())
        record.writeBytes(original)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        assertTrue(runCatching { store.unlock("728491".toCharArray()) }.isFailure)
        assertTrue(runCatching { store.create("728491".toCharArray()) }.isFailure)
        assertArrayEquals(original, record.readBytes())
    }
    @Test fun foreignNamespaceAndInvalidOuterLengthsAreRejectedWithoutMutation() = withStore { first, root, _ ->
        val firstKey = first.create("728491".toCharArray())
        try {
            withStore { second, otherRoot, _ ->
                val secondKey = second.create("728491".toCharArray())
                try { assertFalse(firstKey.contentEquals(secondKey)) } finally { secondKey.fill(0) }
                val foreign = File(otherRoot, "credential").readBytes()
                val target = File(root, "credential")
                for (invalid in listOf(foreign, foreign.copyOf(115), foreign.copyOf(117))) {
                    target.writeBytes(invalid)
                    assertTrue(runCatching { first.unlock("728491".toCharArray()) }.isFailure)
                    assertArrayEquals(invalid, target.readBytes())
                }
            }
        } finally { firstKey.fill(0) }
    }

    @Test fun atomicBackupIsRecoveredAndOrphanNewRecordDoesNotPermitReplacement() = withStore { store, root, _ ->
        val key = store.create("728491".toCharArray())
        try {
            val target = File(root, "credential")
            val before = target.readBytes()
            assertTrue(target.renameTo(File(root, "credential.bak")))
            assertTrue(store.isConfigured)
            val unlocked = store.unlock("728491".toCharArray())
            try { assertArrayEquals(key, unlocked) } finally { unlocked.fill(0) }
            assertArrayEquals(before, target.readBytes())
            assertTrue(target.renameTo(File(root, "credential.new")))
            assertTrue(store.isConfigured)
            assertTrue(runCatching { store.unlock("728491".toCharArray()) }.isFailure)
            assertTrue(runCatching { store.create("728491".toCharArray()) }.isFailure)
            assertArrayEquals(before, File(root, "credential.new").readBytes())
        } finally { key.fill(0) }
    }

    @Test fun unavailableStorageDoesNotReportSuccessfulSetupAndCanRetry() = withStore { store, root, _ ->
        assertTrue(root.createNewFile())
        val pin = "728491".toCharArray()
        assertTrue(runCatching { store.create(pin) }.isFailure)
        assertArrayEquals(CharArray(6), pin)
        assertFalse(store.isConfigured)
        assertTrue(root.delete())
        store.create("728491".toCharArray()).fill(0)
        val unlocked = store.unlock("728491".toCharArray())
        try { assertEquals(32, unlocked.size) } finally { unlocked.fill(0) }
    }

}
