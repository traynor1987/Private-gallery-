@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.domain.*
import uk.co.traynor.privategallery.core.vault.*

/** Actual Primary preferences/repository/archive versus the fixed Secondary filesystem adapter. */
class Phase2DomainAdaptersTest {
  @Test fun credentialRecoveryRepositoryAndBackupMutationsStayInTheirOwnDomain() {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val id = "phase2-adapters-${UUID.randomUUID()}"
    val root = File(base.cacheDir, id).apply { mkdirs() }
    val names = mutableSetOf<String>()
    val context = object : ContextWrapper(base) {
      override fun getFilesDir() = root
      override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
        names += "$id-$name"; return base.getSharedPreferences("$id-$name", mode)
      }
    }
    val primary = PrimarySessionAuthority(android.os.SystemClock::elapsedRealtime)
    val secondary = SecondarySessionAuthority(android.os.SystemClock::elapsedRealtime)
    var recovery: CharArray? = null
    try {
      val slots = PrimaryKeySlots(context)
      val primaryMaster = slots.pin.create("111111111111".toCharArray())
      primary.open(primaryMaster.copyOf()); primaryMaster.fill(0)
      val p = checkNotNull(primary.operationOrNull(PrimaryScope.entries.toSet()))
      p.use {
        recovery = slots.prepareRecovery(p, false)
        val repository = AndroidVaultRepository(context, p)
        val item = (repository.importVerified(VaultImportSource("primary-synthetic.png", "image/png", { byteArrayOf(1,2,3).inputStream() })) as ImportResult.Imported).item
        val store = SecondaryStore(root)
        val pending = store.create("222222222222".toCharArray(), {}, { it() })
        val secret = pending.recoverySecret
        try {
          store.confirm(pending, secret, {}, { it() }).use { authenticated ->
            val attempt = secondary.beginAuthentication()
            assertTrue(secondary.completeAuthentication(attempt, authenticated.takeMaster()))
          }
          fun secondaryFiles() = File(root, "domain-store").walkTopDown().filter { it.isFile }.associate {
            it.relativeTo(root).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()
          }
          fun preferences() = names.associateWith { base.getSharedPreferences(it, 0).all.toMap() }
          val beforeSecondary = secondaryFiles()
          slots.changePin(p, "111111111111".toCharArray(), "333333333333".toCharArray())
          val replacement = slots.prepareRecovery(p, true)
          recovery?.fill('\u0000'); recovery = replacement
          slots.confirmRecovery(p, replacement.copyOf())
          assertEquals(beforeSecondary, secondaryFiles())
          val beforePrimary = preferences()
          val primaryIndex = File(root, "vault/vault-index.enc").readBytes()
          checkNotNull(secondary.operationOrNull(SecondaryScope.entries.toSet())).use { s ->
            store.changePin(s, "444444444444".toCharArray())
            store.replaceRecovery(s).use { replacementSecondary ->
              val newSecret = replacementSecondary.recoverySecret
              try { store.confirmReplacement(s, replacementSecondary, newSecret) } finally { newSecret.fill(0) }
            }
          }
          assertEquals(beforePrimary, preferences())
          assertArrayEquals(primaryIndex, File(root, "vault/vault-index.enc").readBytes())
          assertEquals(listOf(item.id), repository.items().map { it.id })
          assertArrayEquals(byteArrayOf(1,2,3), repository.readForViewing(item))
          assertTrue(File(root, "domain-store/payloads").listFiles()!!.isEmpty())
          val archive = ByteArrayOutputStream()
          repository.exportBackup(checkNotNull(recovery).copyOf(), slots.recoveryEnvelope(p), archive)
          val entries = mutableListOf<String>()
          ZipInputStream(archive.toByteArray().inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries += entry.name; zip.closeEntry() }
          }
          assertFalse(entries.any { "domain-store" in it })
          assertTrue(entries.all { it == "manifest.json" || it == "vault-index.enc" || it.startsWith("payloads/") })
          assertEquals(SecondaryPreflight.READY, store.preflight())
          File(root, "vault/vault-index.enc").writeBytes(byteArrayOf(0))
          assertThrows(Exception::class.java) { repository.items() }
          assertEquals(SecondaryPreflight.READY, store.preflight())
          store.authenticatePin("444444444444".toCharArray()).close()
        } finally { secret.fill(0); pending.close() }
      }
    } finally {
      recovery?.fill('\u0000'); primary.revoke(); secondary.revoke()
      names.forEach { base.getSharedPreferences(it,0).edit().clear().commit() }; root.deleteRecursively()
    }
  }
}
