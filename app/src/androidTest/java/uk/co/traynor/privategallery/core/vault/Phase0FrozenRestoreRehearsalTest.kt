package uk.co.traynor.privategallery.core.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.RecoveryVaultKeyStore
import uk.co.traynor.privategallery.core.security.PrimarySessionAuthority
import uk.co.traynor.privategallery.core.security.RecoverySetupState
import uk.co.traynor.privategallery.core.crypto.RecoveryKey
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

/** Only random test namespaces under cache; never accesses filesDir/vault or owner PIN slots. */
@RunWith(AndroidJUnit4::class)
class Phase0FrozenRestoreRehearsalTest {
    /** Catches dropped metadata/trash/video, source PIN reuse, envelope rewrapping and broad directory export. */
    @Test fun confirmedPrimaryExportRestoresEveryLogicalFieldIntoIndependentEmptyState() {
        withConfirmedSyntheticBackup { fixture, source, archive, secret, envelope ->
            val archived = phase0ArchiveEntries(archive)
            val names = fixture.expected.getJSONArray("expectedEntries").let { values ->
                (0 until values.length()).map { values.getString(it) }.toSet()
            }
            // Exact allowlist excludes Browser engine, AI preference, and plaintext staging canaries.
            assertEquals(names, archived.keys)
            fixture.ciphertextNames.forEach { name ->
                assertArrayEquals("Ciphertext must be copied verbatim: $name", fixture.entries.getValue(name), archived.getValue(name))
            }
            val manifest = JSONObject(archived.getValue("manifest.json").toString(Charsets.UTF_8))
            assertEquals(setOf("version", "hashes", "recovery"), manifest.keys().asSequence().toSet())
            assertEquals(1, manifest.getInt("version"))
            assertEquals(fixture.ciphertextNames, manifest.getJSONObject("hashes").keys().asSequence().toSet())
            fixture.ciphertextNames.forEach { name ->
                assertEquals(phase0Sha256(fixture.entries.getValue(name)), manifest.getJSONObject("hashes").getString(name))
            }
            val wrap = manifest.getJSONObject("recovery")
            assertEquals(setOf("salt", "nonce", "ciphertext"), wrap.keys().asSequence().toSet())
            assertRecoveryEnvelope(envelope, RecoveryWrappedKey(
                java.util.Base64.getDecoder().decode(wrap.getString("salt")),
                java.util.Base64.getDecoder().decode(wrap.getString("nonce")),
                java.util.Base64.getDecoder().decode(wrap.getString("ciphertext"))))

            Phase0SyntheticRestoreContext("destination").use { destination ->
                assertNotEquals(source.folder.canonicalPath, destination.folder.canonicalPath)
                assertTrue(destination.files().isEmpty())
                assertTrue(destination.envelopePreferences().values.all { it.isEmpty() })
                val keys = PinVaultKeyStore(destination.context)
                val recovery = RecoveryVaultKeyStore(destination.context)
                assertFalse(keys.isConfigured)
                assertEquals(RecoverySetupState.NOT_CONFIGURED, recovery.setupState)
                val newPin = "864201".toCharArray()
                try {
                    val restored = AndroidVaultRepository.restoreBackup(destination.context, archive.inputStream(),
                        secret.copyOf(), newPin.copyOf(), keys, recovery)
                    try { assertArrayEquals(fixture.key, restored) } finally { restored.fill(0) }
                    assertThrows(Exception::class.java) {
                        PinVaultKeyStore(destination.context).unlock(fixture.expected.getString("pin").toCharArray()).fill(0)
                    }
                    // Adapter reconstruction does not reuse source preferences, key lease, or in-memory index.
                    val reopened = PinVaultKeyStore(destination.context).unlock(newPin.copyOf())
                    val authority = PrimarySessionAuthority()
                    try {
                        assertArrayEquals(fixture.key, reopened)
                        authority.open(reopened.copyOf())
                        checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())).use { operation ->
                            val root = File(destination.folder, "vault")
                            val actual = EncryptedIndexStore(root).loadSnapshot(operation.key)
                            assertPhase0LogicalSnapshot(fixture.logicalSnapshot(), actual)
                            val repository = AndroidVaultRepository(destination.context, operation)
                            assertEquals(listOf(fixture.expected.getString("imageId")), repository.items().map { it.id })
                            assertEquals(listOf(fixture.expected.getString("videoId")), repository.recentlyDeleted().map { it.id })
                            assertEquals(actual.collections, repository.collections())
                            assertEquals(listOf(fixture.expected.getString("imageId")),
                                repository.itemsInCollection(fixture.expected.getString("collectionId")).map { it.id })
                            assertEquals(actual.imageEdits.getValue(fixture.expected.getString("imageId")),
                                repository.imageEdit(repository.scopedHandle(repository.items().single { it.id == fixture.expected.getString("imageId") })))
                            actual.items.forEach { item ->
                                val file = File(root, "payloads/${item.id}.vault")
                                val stored = StoredPayload(item.id, file, item.plaintextSize, item.plaintextSha256, item.payloadNonce)
                                assertTrue(EncryptedPayloadStore(root).verify(stored, operation.key))
                                assertEquals(item.mimeType == "video/mp4", ChunkedVaultVideoStore.isChunked(file))
                                val plaintext = EncryptedPayloadStore(root).decryptToBytes(stored, operation.key)
                                try {
                                    assertEquals(item.plaintextSize, plaintext.size.toLong())
                                    assertEquals(fixture.expected.getString(if (item.mimeType == "video/mp4") "videoSha256" else "imageSha256"),
                                        phase0Sha256(plaintext))
                                } finally { plaintext.fill(0) }
                            }
                        }
                        val recoveryReopened = RecoveryVaultKeyStore(destination.context)
                        assertTrue(recoveryReopened.isConfigured)
                        assertRecoveryEnvelope(envelope, recoveryReopened.exportEnvelope())
                        val recovered = recoveryReopened.unlock(secret.copyOf())
                        try { assertArrayEquals(fixture.key, recovered) } finally { recovered.fill(0) }
                        assertEquals(fixture.ciphertextNames.map { "vault/$it" }.toSet(), destination.files().keys)
                        fixture.ciphertextNames.forEach { name ->
                            assertEquals(phase0Sha256(fixture.entries.getValue(name)), destination.files().getValue("vault/$name"))
                        }
                        // Only public synthetic digests; never write PINs, VDEKs or recovery material.
                        val evidence = JSONObject()
                            .put("fixture", "immutable-phase0-legacy-v1")
                            .put("frozen_backup_sha256", "27fd27c5574cd31182effcaba8a8e2389108666df9e320e17d4b46ddcd7251cb")
                            .put("confirmed_export_sha256", phase0Sha256(archive))
                            .put("destination_started_empty", true)
                            .put("source_and_destination_distinct", true)
                            .put("new_pin_and_matching_recovery_authenticated", true)
                            .put("ciphertext_sha256", JSONObject(destination.files()))
                            .put("image_plaintext_sha256", fixture.expected.getString("imageSha256"))
                            .put("pgvideo1_plaintext_sha256", fixture.expected.getString("videoSha256"))
                            .put("all_fixture_metadata_verified", true)
                            .put("android_api", android.os.Build.VERSION.SDK_INT)
                            .put("device_model", android.os.Build.MODEL)
                        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                            ?.let(::File) ?: checkNotNull(destination.context.getExternalFilesDir("phase0-synthetic-evidence"))
                        val record = File(output, "phase0-restore-${UUID.randomUUID()}.json")
                        check(record.parentFile!!.isDirectory || record.parentFile!!.mkdirs())
                        record.writeText(evidence.toString(2))
                    } finally { authority.revoke(); reopened.fill(0) }
                } finally { newPin.fill('\u0000') }
            }
        }
    }

    /** Catches installation before recovery/tag authentication and partial staging/PIN/recovery persistence. */
    @Test fun wrongRecoveryAndRehashedCorruptVideoLeaveIndependentDestinationUntouched() {
        withConfirmedSyntheticBackup { fixture, source, archive, secret, _ ->
            val corrupt = phase0CorruptVideoAndRehash(archive, fixture.expected.getString("videoId"))
            val wrongSecret = RecoveryKey.generate()
            val sourceFiles = source.files()
            val sourcePreferences = source.envelopePreferences()
            try {
                Phase0SyntheticRestoreContext("rejected").use { destination ->
                    assertTrue(destination.files().isEmpty())
                    val preferencesBefore = destination.envelopePreferences()
                    assertTrue(preferencesBefore.values.all { it.isEmpty() })
                    listOf("wrong matching-format recovery" to (archive to wrongSecret),
                        "PGVIDEO1 tag corrupted with recomputed public manifest hash" to (corrupt to secret)).forEach { (label, input) ->
                        assertThrows(label, Exception::class.java) {
                            AndroidVaultRepository.restoreBackup(destination.context, input.first.inputStream(), input.second.copyOf(),
                                "864201".toCharArray(), PinVaultKeyStore(destination.context), RecoveryVaultKeyStore(destination.context)).fill(0)
                        }
                        assertTrue("No installed/staged bytes after $label", destination.files().isEmpty())
                        assertEquals("No credential change after $label", preferencesBefore, destination.envelopePreferences())
                        assertFalse(File(destination.folder, "vault").exists())
                        assertFalse(File(destination.folder, "vault-restore-staging").exists())
                        assertFalse(PinVaultKeyStore(destination.context).isConfigured)
                        assertEquals(RecoverySetupState.NOT_CONFIGURED, RecoveryVaultKeyStore(destination.context).setupState)
                        assertEquals(sourceFiles, source.files())
                        assertEquals(sourcePreferences, source.envelopePreferences())
                    }
                }
            } finally { corrupt.fill(0); wrongSecret.fill('\u0000') }
        }
    }

    /** Catches destructive cleanup/slot leakage at every real adapter commit and unsafe empty setup on retry. */
    @Test fun faultedRestoreCommitBoundariesPreserveAuthenticatedRootAndAllowMatchingRetry() {
        withConfirmedSyntheticBackup { fixture, source, archive, secret, envelope ->
            val sourceFiles = source.files()
            val sourcePreferences = source.envelopePreferences()
            val archiveDigest = phase0Sha256(archive)
            val boundaries = listOf("root rename", "recovery envelope install", "PIN envelope install")
            boundaries.forEachIndexed { boundaryIndex, boundary ->
                listOf(false, true).forEach { afterAction ->
                    val label = "$boundary ${if (afterAction) "after" else "before"} action"
                    Phase0SyntheticRestoreContext("commit-fault-${boundaryIndex + 1}-${if (afterAction) "after" else "before"}").use { destination ->
                        assertTrue(destination.files().isEmpty())
                        assertTrue(destination.envelopePreferences().values.all { it.isEmpty() })
                        val injected = java.io.IOException("Synthetic commit interruption: $label")
                        var commits = 0
                        val failure = assertThrows(label, java.io.IOException::class.java) {
                            AndroidVaultRepository.restoreBackup(destination.context, archive.inputStream(), secret.copyOf(),
                                "864201".toCharArray(), PinVaultKeyStore(destination.context), RecoveryVaultKeyStore(destination.context)) { action ->
                                val interruptedBoundary = ++commits == boundaryIndex + 1
                                if (interruptedBoundary && !afterAction) throw injected
                                action()
                                if (interruptedBoundary && afterAction) throw injected
                            }.fill(0)
                        }
                        assertSame("Must observe the injected commit failure: $label", injected, failure)
                        assertEquals(boundaryIndex + 1, commits)
                        assertFalse("Staging cleared: $label", File(destination.folder, "vault-restore-staging").exists())
                        val root = File(destination.folder, "vault")
                        val retainedRoot = boundaryIndex != 0 || afterAction
                        assertEquals("Authenticated root ownership: $label", retainedRoot, root.exists())
                        val keys = PinVaultKeyStore(destination.context)
                        val recovery = RecoveryVaultKeyStore(destination.context)
                        // An after-PIN hook failure exercises the rollback receipt assigned by the real action.
                        assertFalse("PIN rollback receipt ownership: $label", keys.hasEnvelopeMaterial)
                        assertEquals("Recovery rollback ownership: $label", RecoverySetupState.NOT_CONFIGURED, recovery.setupState)
                        assertTrue("No partial slot records: $label", destination.envelopePreferences().values.all { it.isEmpty() })
                        assertEquals("Existing ciphertext blocks empty setup: $label", retainedRoot, keys.isConfigured)
                        if (retainedRoot) {
                            assertEquals(fixture.ciphertextNames.map { "vault/$it" }.toSet(), destination.files().keys)
                            fixture.ciphertextNames.forEach { name ->
                                assertEquals("Retained ciphertext untouched: $label/$name", phase0Sha256(fixture.entries.getValue(name)),
                                    destination.files().getValue("vault/$name"))
                            }
                            assertPhase0LogicalSnapshot(fixture.logicalSnapshot(), EncryptedIndexStore(root).loadSnapshot(fixture.key))
                            assertThrows("Never create an empty vault over retained ciphertext: $label", IllegalStateException::class.java) {
                                keys.create("147258".toCharArray()).fill(0)
                            }
                        } else assertTrue(destination.files().isEmpty())
                        assertEquals("Source remains available: $label", sourceFiles, source.files())
                        assertEquals(sourcePreferences, source.envelopePreferences())
                        assertEquals("Independent backup remains available: $label", archiveDigest, phase0Sha256(archive))

                        val restored = AndroidVaultRepository.restoreBackup(destination.context, archive.inputStream(), secret.copyOf(),
                            "963852".toCharArray(), PinVaultKeyStore(destination.context), RecoveryVaultKeyStore(destination.context))
                        try { assertArrayEquals(fixture.key, restored) } finally { restored.fill(0) }
                        val reopened = PinVaultKeyStore(destination.context).unlock("963852".toCharArray())
                        try {
                            assertArrayEquals(fixture.key, reopened)
                            assertPhase0LogicalSnapshot(fixture.logicalSnapshot(), EncryptedIndexStore(root).loadSnapshot(reopened))
                        } finally { reopened.fill(0) }
                        assertRecoveryEnvelope(envelope, RecoveryVaultKeyStore(destination.context).exportEnvelope())
                        assertFalse(File(destination.folder, "vault-restore-staging").exists())
                        assertEquals(fixture.ciphertextNames.map { "vault/$it" }.toSet(), destination.files().keys)
                        assertEquals(sourceFiles, source.files())
                        assertEquals(sourcePreferences, source.envelopePreferences())
                        assertEquals(archiveDigest, phase0Sha256(archive))
                    }
                }
            }
        }
    }

    private fun withConfirmedSyntheticBackup(block: (Phase0FrozenRestoreFixture, Phase0SyntheticRestoreContext,
                                                    ByteArray, CharArray, RecoveryWrappedKey) -> Unit) {
        val fixture = Phase0FrozenRestoreFixture()
        val authority = PrimarySessionAuthority()
        var secret: CharArray? = null
        var archive: ByteArray? = null
        try {
            Phase0SyntheticRestoreContext("source").use { source ->
                val pin = fixture.expected.getString("pin").toCharArray()
                val pinKeys = PinVaultKeyStore(source.context)
                pinKeys.replacePinForRecoveredVault(pin.copyOf(), fixture.key)
                fixture.seedCiphertext(File(source.folder, "vault"))
                val unlocked = pinKeys.unlock(pin.copyOf())
                try { authority.open(unlocked.copyOf()) } finally { unlocked.fill(0); pin.fill('\u0000') }
                checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())).use { operation ->
                    assertPhase0LogicalSnapshot(fixture.logicalSnapshot(), EncryptedIndexStore(File(source.folder, "vault")).loadSnapshot(operation.key))
                    val pending = RecoveryVaultKeyStore(source.context)
                    secret = pending.create(operation.key, operation::commit)
                    val recovery = RecoveryVaultKeyStore(source.context)
                    assertEquals(RecoverySetupState.PENDING_CONFIRMATION, recovery.setupState)
                    assertFalse(recovery.isConfigured)
                    assertThrows(IllegalStateException::class.java) { recovery.exportEnvelope() }
                    recovery.confirm(checkNotNull(secret).copyOf(), operation.key, operation::commit)
                    val confirmed = RecoveryVaultKeyStore(source.context)
                    assertTrue(confirmed.isConfigured)
                    assertTrue(confirmed.isPossessionVerified)
                    val envelope = confirmed.exportEnvelope()
                    // Synthetic canaries outside and inside the vault demonstrate export's entry allowlist.
                    listOf("browser-engine/cookies.db", "vault/plaintext-staging/upload.jpg",
                        "vault/browser-engine/history.db", "cache/ai-edit-stage.png").forEach { path ->
                        File(source.folder, path).apply { check(parentFile!!.isDirectory || parentFile!!.mkdirs()) }
                            .writeText("PUBLIC SYNTHETIC EXCLUSION CANARY: $path")
                    }
                    source.context.getSharedPreferences("ai_provider_selection", Context.MODE_PRIVATE).edit()
                        .putString("synthetic_credential", "PUBLIC SYNTHETIC AI CANARY").commit().also { assertTrue(it) }
                    val output = ByteArrayOutputStream()
                    AndroidVaultRepository(source.context, operation).exportBackup(checkNotNull(secret).copyOf(), envelope, output)
                    archive = output.toByteArray()
                    block(fixture, source, checkNotNull(archive), checkNotNull(secret), envelope)
                }
            }
        } finally { authority.revoke(); fixture.key.fill(0); secret?.fill('\u0000'); archive?.fill(0) }
    }

    private fun assertRecoveryEnvelope(expected: RecoveryWrappedKey, actual: RecoveryWrappedKey) {
        assertArrayEquals(expected.salt, actual.salt)
        assertArrayEquals(expected.nonce, actual.nonce)
        assertArrayEquals(expected.ciphertext, actual.ciphertext)
    }

    @Test fun independentlyFrozenBackupRestoresVideoTrashProvenanceAndRecoveryBytes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val namespace = "phase0-synthetic-restore-${UUID.randomUUID()}"
        val folder = File(app.cacheDir,namespace).apply { check(mkdirs()) }
        val context = object: ContextWrapper(app) {
            override fun getFilesDir() = folder
            override fun getSharedPreferences(name:String,mode:Int) = app.getSharedPreferences("$namespace-$name",mode)
        }
        val expected = instrumentation.context.assets.open("phase0/expected.json").use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        val key = expected.getString("keyHex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val recovery = expected.getString("recovery").toCharArray()
        val archive = instrumentation.context.assets.open("phase0/backup-v1.pgvault").use { it.readBytes() }
        val frozenHashes = instrumentation.context.assets.open("phase0/SHA256SUMS").use { it.readBytes().toString(Charsets.UTF_8) }
            .lineSequence().filter { it.isNotBlank() }.associate { val fields=it.split("  ",limit=2); fields[1] to fields[0] }
        assertEquals(frozenHashes.getValue("backup-v1.pgvault"),hex(MessageDigest.getInstance("SHA-256").digest(archive)))
        val expectedBytes=instrumentation.context.assets.open("phase0/expected.json").use { it.readBytes() }
        assertEquals(frozenHashes.getValue("expected.json"),hex(MessageDigest.getInstance("SHA-256").digest(expectedBytes)))
        val pinKeys = PinVaultKeyStore(context)
        val recoveryKeys = RecoveryVaultKeyStore(context)
        try {
            assertFalse(pinKeys.isConfigured)
            val restored = AndroidVaultRepository.restoreBackup(context,archive.inputStream(),recovery.copyOf(),expected.getString("pin").toCharArray(),pinKeys,recoveryKeys)
            assertArrayEquals(key,restored)
            restored.fill(0)
            // Reconstruction is a restart-equivalent adapter test, not a claimed Android process-kill measurement.
            val reopened = PinVaultKeyStore(context).unlock(expected.getString("pin").toCharArray())
            try {
                assertArrayEquals(key,reopened)
                val root = File(folder,"vault")
                val snapshot = EncryptedIndexStore(root).loadSnapshot(reopened)
                assertEquals(2,snapshot.items.size)
                assertEquals("Synthetic favourites",snapshot.collections.single().name)
                assertEquals(expected.getString("collectionId"),snapshot.favouriteCollectionId)
                assertEquals(2,snapshot.memberships.size)
                assertEquals(NormalizedCrop(.1f,.2f,.8f,.9f),snapshot.imageEdits.getValue(expected.getString("imageId")).crop)
                assertEquals(NormalizedCrop.ORIGINAL,snapshot.imageEdits.getValue(expected.getString("imageId")).previousCrop)
                val image = snapshot.items.first(); val video = snapshot.items.last()
                assertEquals(MediaOrigin.LOCAL_EDIT,image.origin); assertEquals(MediaOrigin.REMOTE_AI_EDIT,video.origin)
                assertFalse(image.vaultOnly); assertTrue(video.vaultOnly)
                assertEquals(VaultItemState.TRASHED,video.state); assertEquals(expected.getLong("deletedAt"),video.deletedAtEpochMillis)
                assertEquals("content://synthetic/video",video.sourceUri)
                snapshot.items.forEach { item ->
                    val stored = StoredPayload(item.id,File(root,"payloads/${item.id}.vault"),item.plaintextSize,item.plaintextSha256,item.payloadNonce)
                    assertTrue(EncryptedPayloadStore(root).verify(stored,reopened))
                    assertEquals(item.id==video.id,ChunkedVaultVideoStore.isChunked(stored.file))
                    val plaintext = EncryptedPayloadStore(root).decryptToBytes(stored,reopened)
                    assertEquals(if(item.id==video.id) expected.getString("videoSha256") else expected.getString("imageSha256"),hex(MessageDigest.getInstance("SHA-256").digest(plaintext)))
                    plaintext.fill(0)
                }
                assertEquals(setOf("vault-index.enc","payloads/${image.id}.vault","payloads/${video.id}.vault"),root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet())
                val exported = recoveryKeys.exportEnvelope()
                java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
                    while(true) {
                        val entry=zip.nextEntry ?: break
                        if(entry.name=="manifest.json") {
                            val wrap=JSONObject(zip.readBytes().toString(Charsets.UTF_8)).getJSONObject("recovery")
                            assertArrayEquals(java.util.Base64.getDecoder().decode(wrap.getString("salt")),exported.salt)
                            assertArrayEquals(java.util.Base64.getDecoder().decode(wrap.getString("nonce")),exported.nonce)
                            assertArrayEquals(java.util.Base64.getDecoder().decode(wrap.getString("ciphertext")),exported.ciphertext)
                        }
                    }
                }
            } finally { reopened.fill(0) }
        } finally {
            key.fill(0); recovery.fill('\u0000'); archive.fill(0)
            folder.deleteRecursively()
            app.getSharedPreferences("$namespace-vault-key-envelope",Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("$namespace-vault-recovery-envelope",Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private fun hex(bytes:ByteArray)=bytes.joinToString("") { "%02x".format(it) }
}
