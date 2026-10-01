package uk.co.traynor.privategallery.core.domain

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecondaryStorageAdapterTest {
  @Test fun realAndroidPinnedStorageConfirmsRestartsAndAtomicallyReplacesSelection() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val dir = File(context.cacheDir, "secondary-adapter-${UUID.randomUUID()}").apply { mkdir() }
    try {
      val primary = File(dir, "vault").apply { mkdir() }
      File(primary, "canary").writeText("primary")
      val store = SecondaryStore(dir)
      assertEquals(SecondaryPreflight.FRESH, store.preflight())
      val pending = store.create("1234567890123456".toCharArray(), {}, { it() })
      val recovery = pending.recoverySecret
      try {
        assertEquals(SecondaryPreflight.PENDING, SecondaryStore(dir).preflight())
        store.confirm(pending, recovery, {}, { it() }).close()
        val restarted = SecondaryStore(dir)
        assertEquals(SecondaryPreflight.READY, restarted.preflight())
        restarted.authenticateRecovery(recovery).use { recovered ->
          restarted.authenticatePin("1234567890123456".toCharArray()).use { pin ->
            val a = recovered.takeMaster(); val b = pin.takeMaster()
            try { assertArrayEquals(a, b) } finally { a.fill(0); b.fill(0) }
          }
        }
        assertEquals("primary", File(primary, "canary").readText())
        assertFalse(dir.listFiles()!!.any { it.name.startsWith("domain-stage-") })
      } finally { recovery.fill(0); pending.close() }
    } finally { dir.deleteRecursively() }
  }
}
