package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vault.AndroidVaultRepository
import uk.co.traynor.privategallery.core.vault.ImportResult
import uk.co.traynor.privategallery.core.vault.VaultImportSource

class EvidenceCollectionSync68Test {
    @Test fun incrementalSyncReusesAuthenticatedOriginalsAndKeepsRecordsAfterDisconnect() = runBlocking {
        withVaults { context, key, _ ->
            val vault = AndroidVaultRepository(context, key)
            val collection = vault.createCollection("Evidence Collection")
            val first = addPrimary(vault, "first transcript")
            val duplicate = addPrimary(vault, "first transcript")
            val outside = addPrimary(vault, "outside this connection")
            vault.addItemsToCollection(collection.id, listOf(first, duplicate))
            val identity = Any()
            fun open() = EvidenceAccess.primary(context, identity, { identity }, { true }, { key.copyOf() }, identity)
            open().use { access ->
                val sync = EvidenceCollectionSync(access)
                assertEquals(listOf(EvidenceCollectionOption(collection.id, collection.name)), sync.available())
                assertTrue(sync.connections().isEmpty())
                sync.connect(collection.id)
                val progress = mutableListOf<EvidenceCollectionProgress>()
                val catalog = sync.sync(progress = { progress += it }, register = { register(access, it) })
                assertEquals(1, catalog.evidence().size)
                assertTrue(catalog.evidence().single().sourceId() in listOf(first, duplicate))
                assertEquals(collection.id, catalog.evidence().single().metadata().embedded()["sourceCollectionId"])
                assertEquals(1, progress.last().duplicates)
                assertEquals(2, progress.last().processed)
            }
            // Reopening the originating vault resumes a durable connection without rescanning payloads.
            val added = addPrimary(vault, "second transcript")
            vault.addItemsToCollection(collection.id, listOf(added))
            open().use { access ->
                val sync = EvidenceCollectionSync(access)
                assertEquals(listOf(EvidenceCollectionConnection(collection.id, collection.name)), sync.connections())
                val new = mutableListOf<String>()
                val catalog = sync.sync(register = { new += it.item.id; register(access, it) })
                assertEquals(listOf(added), new)
                assertEquals(2, catalog.evidence().size)
                assertTrue(catalog.evidence().none { it.sourceId() == outside })
                vault.deleteCollection(collection.id)
                val unavailable = mutableListOf<EvidenceCollectionProgress>()
                sync.sync(progress = { unavailable += it }, register = { error("Deleted collection indexed") })
                assertEquals(1, unavailable.last().unavailableCollections)
                sync.disconnect(collection.id)
                assertTrue(sync.connections().isEmpty())
                assertEquals(2, access.store.read().evidence().size)
                assertEquals(4, vault.items().size)
            }
        }
    }

    @Test fun hiddenConnectionsRequireOriginatingAuthenticationAndResumeWithSameKey() = runBlocking {
        withVaults { context, key, hidden ->
            val namespace = "collection-${UUID.randomUUID()}"
            val credentials = HiddenCredentialStore(context, namespace)
            val alias = "${context.packageName}.hidden.v1.$namespace"
            try {
                assertTrue(hidden.complete(hidden.beginAuthentication(), credentials.create("728491".toCharArray())))
                val repo = HiddenRepository(context, hidden)
                val collection = repo.createCollection("Hidden evidence fixture")
                val item = repo.importMedia("hidden.txt", "text/plain", ByteArrayInputStream("private fixture".toByteArray()))
                repo.addItemsToCollection(collection.id, listOf(item.id))
                val identity = Any()
                EvidenceAccess.primary(context, identity, { identity }, { true }, { key.copyOf() }, identity).use { primary ->
                    val sync = EvidenceCollectionSync(primary)
                    assertTrue(sync.available().none { it.id == collection.id })
                    assertTrue(runCatching { sync.connect(collection.id) }.isFailure)
                    EvidenceAccess.hidden(context, hidden) { true }.use { access ->
                        val hiddenSync = EvidenceCollectionSync(access)
                        hiddenSync.connect(collection.id)
                        assertTrue(sync.connections().isEmpty())
                        hidden.lock()
                        assertTrue(runCatching { hiddenSync.connections() }.isFailure)
                        assertTrue(runCatching { hiddenSync.sync(register = { error("Locked source indexed") }) }.isFailure)
                    }
                    assertNull(credentials.unlockForComparison("100001".toCharArray()))
                    assertTrue(runCatching { EvidenceAccess.hidden(context, hidden) { true } }.isFailure)
                    assertTrue(hidden.complete(hidden.beginAuthentication(), credentials.unlock("728491".toCharArray())))
                    EvidenceAccess.hidden(context, hidden) { true }.use { access ->
                        val hiddenSync = EvidenceCollectionSync(access)
                        assertEquals(listOf(EvidenceCollectionConnection(collection.id, collection.name)), hiddenSync.connections())
                        assertEquals(item.id, hiddenSync.sync(register = { register(access, it) }).evidence().single().sourceId())
                    }
                    assertTrue(primary.store.read().evidence().isEmpty())
                }
            } finally {
                hidden.lock()
                KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
            }
        }
    }

    @Test fun cancellationPreservesCommittedRegistrationAndReopeningResumesTheRemainder() = runBlocking {
        withVaults { context, key, _ ->
            val vault = AndroidVaultRepository(context, key)
            val collection = vault.createCollection("Evidence Collection")
            vault.addItemsToCollection(collection.id, listOf(addPrimary(vault, "one"), addPrimary(vault, "two")))
            val identity = Any()
            fun open() = EvidenceAccess.primary(context, identity, { identity }, { true }, { key.copyOf() }, identity)
            var committed = ""
            open().use { access ->
                val sync = EvidenceCollectionSync(access)
                sync.connect(collection.id)
                val interruption = runCatching {
                    sync.sync(register = { source ->
                        if (committed.isNotEmpty()) throw CancellationException("Fixture interrupted")
                        register(access, source).also { committed = source.item.id }
                    })
                }
                assertTrue(interruption.exceptionOrNull() is CancellationException)
                assertEquals(committed, access.store.read().evidence().single().sourceId())
            }
            open().use { access ->
                var registrations = 0
                val catalog = EvidenceCollectionSync(access).sync(register = { source ->
                    assertNotEquals(committed, source.item.id)
                    registrations++
                    register(access, source)
                })
                assertEquals(1, registrations)
                assertEquals(2, catalog.evidence().size)
            }
        }
    }

    @Test fun authenticatedCollectionWatcherDetectsVaultMembershipChanges() = runBlocking {
        withVaults { context, key, _ ->
            val vault = AndroidVaultRepository(context, key)
            val collection = vault.createCollection("Evidence Collection")
            val item = addPrimary(vault, "watcher fixture")
            val identity = Any()
            EvidenceAccess.primary(context, identity, { identity }, { true }, { key.copyOf() }, identity).use { access ->
                val sync = EvidenceCollectionSync(access)
                sync.connect(collection.id)
                coroutineScope {
                    val signals = Channel<Unit>(Channel.UNLIMITED)
                    val watch = launch { sync.changes().collect { signals.send(Unit) } }
                    try {
                        withTimeout(5_000) { signals.receive() }
                        vault.addItemsToCollection(collection.id, listOf(item))
                        withTimeout(5_000) { signals.receive() }
                        assertEquals(item, sync.sync(register = { register(access, it) }).evidence().single().sourceId())
                    } finally {
                        watch.cancel()
                        signals.cancel()
                    }
                }
            }
        }
    }

    private suspend fun withVaults(action: suspend (Context, ByteArray, HiddenSession) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "evidence-collections-${UUID.randomUUID()}")
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
        }
        val key = ByteArray(32) { 37 }
        val hidden = HiddenSession { 100L }
        try { action(context, key, hidden) } finally {
            hidden.lock()
            key.fill(0)
            root.deleteRecursively()
        }
    }

    private fun addPrimary(vault: AndroidVaultRepository, text: String): String =
        (vault.importVerified(VaultImportSource("transcript.txt", "text/plain",
            { ByteArrayInputStream(text.toByteArray()) }, createDistinctCopy = true)) as ImportResult.Imported).item.id

    private fun register(access: EvidenceAccess, original: EvidenceOriginal): EvidenceCatalog =
        access.reader().readVerified(original.item.id) { verified, input ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            } finally { buffer.fill(0) }
            assertArrayEquals(verified.item.plaintextSha256, digest.digest())
            val record = EvidenceCatalog.Evidence(
                UUID.randomUUID().toString(), verified.item.id, verified.item.displayName, verified.item.mimeType,
                verified.item.plaintextSha256.joinToString("") { "%02x".format(it.toInt() and 255) },
                verified.item.plaintextSize, 100, EvidenceMetadata.acquisition(verified, original), "", "",
                emptyList(), emptyList(), emptyList(), emptyList(),
            )
            access.store.update { it.upsertEvidence(record, 100, "COLLECTION_FIXTURE") }
        }
}
