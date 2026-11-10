package uk.co.traynor.privategallery.core.guardian

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GuardianCaptureStore77Test {
    @Test fun encryptedArchiveDeduplicatesUpdatesAcrossStoreReopens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val first = GuardianCaptureEntry(now - 2, "synthetic sender", "sensitive synthetic one")
        val second = GuardianCaptureEntry(now - 1, "synthetic sender", "sensitive synthetic two")
        val main = GuardianCaptureEvent(0, "conversation", now, listOf(first))
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            assertEquals(1, store.append(main, days = 7, now = now).added)
            val reopened = GuardianCaptureStore(context)
            assertEquals(0, reopened.append(main, days = 7, now = now).added)
            assertEquals(1, reopened.append(main.copy(entries = listOf(first, second)), days = 7, now = now).added)
            assertEquals(2, reopened.summary(days = 7, now = now))
            val file = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
            val ciphertext = file.readBytes()
            assertFalse(ciphertext.toString(Charsets.UTF_8).contains(first.text))
            assertFalse(ciphertext.toString(Charsets.UTF_8).contains(second.text))
            assertEquals(0, reopened.summary(days = 1, now = now + 2 * 86_400_000L))
            assertEquals(0, reopened.append(main, days = 1, now = now + 2 * 86_400_000L).added)
            file.writeBytes(ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            assertTrue(runCatching { reopened.summary(days = 7, now = now) }.isFailure)
        } finally {
            store.clear()
        }
        assertEquals(0, GuardianCaptureStore(context).summary(days = 7, now = now))
        assertFalse(File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME).exists())
    }
}
