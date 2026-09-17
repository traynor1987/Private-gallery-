# Private Gallery V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android V1 that encrypts selected photos/videos locally and never deletes a source before a verified vault copy exists.

**Architecture:** Compose screens call focused ViewModels; use cases enforce the media lifecycle; repositories isolate Room, encrypted file streaming, Android Keystore/PIN envelopes, and Android media APIs. The vault data key is random, per-object AES-GCM encryption protects encrypted app-private files, and PIN/biometric envelopes protect only the vault key.

**Tech Stack:** Kotlin, Compose Material 3, AndroidX Security/Crypto-compatible primitives, Room, Hilt, WorkManager, Media3, Android Photo Picker, MediaStore, JUnit, Robolectric, AndroidX test.

**Spec:** `docs/superpowers/specs/2026-09-17-private-gallery-v1-design.md`

## Global Constraints

- Kotlin/Compose native app; `minSdk 26`, `targetSdk 36`, `compileSdk 36`; package `uk.co.traynor.privategallery`.
- Use Android Photo Picker by default; do not request broad media permissions for V1.
- Vault payloads and thumbnails are AES-256-GCM encrypted in app-private storage; never create plaintext temporary media files.
- Never delete an original before encrypted write, authenticated verification, and durable vault record commit succeed.
- Restore-and-remove never deletes vault data before the normal MediaStore restore is verified.
- PIN plaintext, VDEK plaintext, decrypted media, and sensitive filenames are never persisted/logged; Android backup excludes vault and key data.
- Use `FLAG_SECURE` on protected UI and keep components non-exported.
- No AI classification, accounts, sharing, browser, VPN, automatic imports, or recovery backdoor.

---

## File structure

- `app/`: Android app, Compose UI, manifest, Gradle configuration.
- `core/model/`: lifecycle state and media/key metadata.
- `core/crypto/`: AES-GCM object cipher, scrypt PIN envelope, Android Keystore biometric envelope.
- `core/vault/`: staging file store, Room metadata, transaction/reconciliation use cases.
- `core/media/`: Photo Picker/MediaStore adapters and restore/delete request boundary.
- `core/security/`: lock-session policy, secure-window handling, backup configuration.
- `feature/setup`, `feature/lock`, `feature/gallery`, `feature/vault`, `feature/settings`: focused Compose/ViewModel features.
- `docs/`: README, threat model, API choices, test evidence.

### Task 1: Bootstrap the Android project and privacy baseline

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt`
- Create: `app/src/androidTest/java/uk/co/traynor/privategallery/ManifestPrivacyTest.kt`

**Interfaces:** Produces the installable `Private Gallery` app shell and all later source-set/dependency locations.

- [ ] **Step 1: Write failing manifest/privacy tests**

```kotlin
@Test fun app_does_not_request_broad_media_permissions() {
  val requested = packageInfo.requestedPermissions.orEmpty().toSet()
  assertFalse(requested.contains(Manifest.permission.READ_MEDIA_IMAGES))
  assertFalse(requested.contains(Manifest.permission.READ_MEDIA_VIDEO))
}
@Test fun backup_is_disabled_and_components_are_not_exported() { /* inspect PackageManager + ApplicationInfo */ }
```

- [ ] **Step 2: Run the test and verify RED**

```bash
./gradlew :app:connectedDebugAndroidTest --tests '*ManifestPrivacyTest'
```

- [ ] **Step 3: Create the Kotlin/Compose project with SDK 26/36, neutral label, non-exported manifest, disabled backup and `FLAG_SECURE` MainActivity**

```kotlin
class MainActivity : ComponentActivity() {
  override fun onCreate(state: Bundle?) { super.onCreate(state); window.setFlags(FLAG_SECURE, FLAG_SECURE) }
}
```

- [ ] **Step 4: Verify GREEN and debug assembly**

```bash
./gradlew :app:connectedDebugAndroidTest --tests '*ManifestPrivacyTest' :app:assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m 'feat: bootstrap private Android vault app'
```

### Task 2: Implement vault crypto and key envelopes

**Files:**
- Create: `app/src/main/java/uk/co/traynor/privategallery/core/crypto/VaultCipher.kt`, `PinEnvelope.kt`, `BiometricEnvelope.kt`, `KeyMaterial.kt`
- Create: `app/src/test/java/uk/co/traynor/privategallery/core/crypto/VaultCipherTest.kt`, `PinEnvelopeTest.kt`

**Interfaces:** Produces `VaultCipher.encrypt(input, output, key, aad): EncryptionHeader`, `VaultCipher.decrypt(input, output, key, aad)`, `PinEnvelope.create(pin, vdek)`, `PinEnvelope.unwrap(pin, envelope)`, and `PinEnvelope.changePin(oldPin, newPin, envelope)`.

- [ ] **Step 1: Write failing crypto tests**

```kotlin
@Test fun tampered_ciphertext_cannot_decrypt() = assertFailsWith<AEADBadTagException> { decrypt(tampered) }
@Test fun wrong_pin_cannot_unwrap_vault_key() = assertFailsWith<InvalidPinException> { envelope.unwrap("9999") }
@Test fun pin_change_preserves_the_same_vault_key() { assertContentEquals(before, changed.unwrap("5678")) }
```

- [ ] **Step 2: Run unit tests and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*VaultCipherTest' --tests '*PinEnvelopeTest'
```

- [ ] **Step 3: Implement AES-256-GCM streaming and scrypt-derived AES-GCM VDEK envelopes; add Keystore biometric wrapping behind `BiometricPrompt.CryptoObject`**

```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
```

- [ ] **Step 4: Verify crypto tests GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests '*VaultCipherTest' --tests '*PinEnvelopeTest'
```

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m 'feat: add authenticated vault key protection'
```

### Task 3: Implement encrypted storage, metadata, and interruption reconciliation

**Files:**
- Create: `core/model/VaultItem.kt`, `core/vault/VaultItemDao.kt`, `VaultDatabase.kt`, `EncryptedVaultStore.kt`, `VaultReconciler.kt`
- Create: `core/vault/EncryptedVaultStoreTest.kt`, `VaultReconcilerTest.kt`

**Interfaces:** Produces `stageAndVerify(source, descriptor, key): VerifiedStaging`, `publish(staging): VaultFiles`, `reconcile(): ReconciliationResult`, and Room states `IMPORTING`, `VERIFIED`, `DELETE_PENDING`, `COMPLETE`, `FAILED`.

- [ ] **Step 1: Write failing storage/reconciliation tests**

```kotlin
@Test fun uncommitted_staging_file_is_removed_on_restart() { assertFalse(store.stagingFile(id).exists()) }
@Test fun corrupt_verified_payload_is_marked_failed_without_source_delete() { assertEquals(FAILED, item.state) }
@Test fun duplicate_source_hash_returns_existing_complete_item() { assertEquals(first.id, second.id) }
```

- [ ] **Step 2: Run tests and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*EncryptedVaultStoreTest' --tests '*VaultReconcilerTest'
```

- [ ] **Step 3: Implement private staging/atomic publish, encrypted thumbnails, Room metadata, hash duplicate detection, and conservative startup reconciliation**

```kotlin
require(file.parentFile == stagingDirectory)
Files.move(staging.toPath(), final.toPath(), ATOMIC_MOVE)
```

- [ ] **Step 4: Verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests '*EncryptedVaultStoreTest' --tests '*VaultReconcilerTest'
```

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m 'feat: add encrypted vault storage and recovery'
```

### Task 4: Implement safe copy/move/restore lifecycle use cases

**Files:**
- Create: `core/media/MediaSource.kt`, `MediaStoreGateway.kt`, `core/vault/ImportMediaUseCase.kt`, `RestoreMediaUseCase.kt`, `DeleteVaultItemUseCase.kt`
- Create: `core/vault/ImportMediaUseCaseTest.kt`, `RestoreMediaUseCaseTest.kt`

**Interfaces:** `import(source, mode): ImportResult`; `confirmSourceDeletion(token, accepted): MoveResult`; `restore(itemId, removeFromVault): RestoreResult`.

- [ ] **Step 1: Write failing lifecycle invariant tests**

```kotlin
@Test fun encryption_failure_never_calls_source_delete() = assertEquals(0, gateway.deleteRequests)
@Test fun deletion_rejected_keeps_verified_vault_item() = assertEquals(COMPLETE, result.item.state)
@Test fun restore_failure_keeps_vault_even_when_remove_requested() = assertTrue(store.exists(id))
@Test fun successful_move_requests_delete_only_after_verified_commit() { assertEquals(listOf("verify", "commit", "delete"), events) }
```

- [ ] **Step 2: Run tests and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*ImportMediaUseCaseTest' --tests '*RestoreMediaUseCaseTest'
```

- [ ] **Step 3: Implement stream-only Photo Picker source import, MediaStore pending restore, and system delete-request tokens; never delete automatically on reconciliation**

```kotlin
val request = MediaStore.createDeleteRequest(contentResolver, listOf(sourceUri))
return DeleteConfirmation(request.intentSender)
```

- [ ] **Step 4: Verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests '*ImportMediaUseCaseTest' --tests '*RestoreMediaUseCaseTest'
```

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m 'feat: add safe media import and restore transactions'
```

### Task 5: Build lock, setup, and settings security flows

**Files:**
- Create: `core/security/LockSession.kt`, `feature/setup/SetupScreen.kt`, `feature/lock/LockScreen.kt`, `feature/settings/SecuritySettingsScreen.kt`
- Create: `core/security/LockSessionTest.kt`, `feature/setup/SetupViewModelTest.kt`

**Interfaces:** `LockSession.unlockWithPin(pin)`, `unlockWithBiometric()`, `onAppBackgrounded()`, `onTimerElapsed()`, and `SecurityPreferences(autoLock, biometricEnabled)`.

- [ ] **Step 1: Write failing setup/lock tests**

```kotlin
@Test fun incorrect_pin_does_not_unlock_session() { assertFalse(session.isUnlocked.value) }
@Test fun immediate_policy_locks_on_background() { session.onAppBackgrounded(); assertFalse(session.isUnlocked.value) }
@Test fun delayed_policy_locks_after_exact_timeout() { clock.advanceBy(30.seconds); assertFalse(session.isUnlocked.value) }
```

- [ ] **Step 2: Run tests and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*LockSessionTest' --tests '*SetupViewModelTest'
```

- [ ] **Step 3: Implement first-launch PIN confirmation/unrecoverability disclosure, optional biometric enrollment, lock overlay/navigation clearing, preferences and PIN change**

```kotlin
enum class AutoLockTimeout { IMMEDIATELY, SECONDS_30, MINUTE_1, MINUTES_5 }
```

- [ ] **Step 4: Verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests '*LockSessionTest' --tests '*SetupViewModelTest'
```

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m 'feat: add vault lock and setup security'
```

### Task 6: Build protected gallery UI and Android integrations

**Files:**
- Create: `feature/gallery/NormalGalleryScreen.kt`, `feature/vault/VaultScreen.kt`, `VaultViewerScreen.kt`, `AppNavHost.kt`, `MainViewModel.kt`
- Create: `feature/vault/VaultViewModelTest.kt`, `app/src/androidTest/java/.../SecureWindowTest.kt`

**Interfaces:** UI invokes `PickMultipleVisualMedia`, `ImportMediaUseCase`, `RestoreMediaUseCase`, `DeleteVaultItemUseCase`, and observes encrypted thumbnail streams only while unlocked.

- [ ] **Step 1: Write failing UI/view-model tests**

```kotlin
@Test fun move_confirmation_is_not_shown_until_verified_import() { assertFalse(state.showDeleteRequest) }
@Test fun locking_clears_vault_items_and_selected_ids_from_ui_state() { assertTrue(state.items.isEmpty()) }
```

- [ ] **Step 2: Run tests and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*VaultViewModelTest'
```

- [ ] **Step 3: Implement Photo Picker launch, selection/multi-select grid, protected vault grid/viewer/video playback, delete confirmations and all requested move/restore messaging**

```kotlin
val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris -> viewModel.copyOrMove(uris, mode) }
```

- [ ] **Step 4: Verify unit/instrumentation UI tests GREEN**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

- [ ] **Step 5: Commit**

```bash
git add . && git commit -m 'feat: add protected gallery and vault UI'
```

### Task 7: Documentation, CI, security audit, and release evidence

**Files:**
- Create: `README.md`, `docs/SECURITY.md`, `docs/THREAT_MODEL.md`, `.github/workflows/android.yml`
- Modify: `docs/superpowers/specs/2026-09-17-private-gallery-v1-design.md`

**Interfaces:** Produces documented build/install/testing instructions and a CI debug APK artifact.

- [ ] **Step 1: Write failing CI/configuration assertions**

```bash
test -f .github/workflows/android.yml
rg 'assembleDebug|testDebugUnitTest|lintDebug|upload-artifact' .github/workflows/android.yml
rg 'no recovery|AES-256-GCM|Photo Picker|MediaStore' README.md docs/SECURITY.md
```

- [ ] **Step 2: Run checks and verify RED**

```bash
test -f .github/workflows/android.yml
```

- [ ] **Step 3: Add README/security docs/CI and execute security audit for temporary plaintext, logs, backups, exported components, URI/path handling, auth recreation, and thumbnail cache**

```yaml
- run: ./gradlew lintDebug testDebugUnitTest assembleDebug
- uses: actions/upload-artifact@v4
  with: { name: private-gallery-debug-apk, path: app/build/outputs/apk/debug/app-debug.apk }
```

- [ ] **Step 4: Run the full final verification suite**

```bash
./gradlew clean lintDebug testDebugUnitTest assembleDebug
git diff --check
rg -n 'Log\.|println\(|READ_MEDIA|android:exported="true"' app/src || true
```

- [ ] **Step 5: Commit and push branch**

```bash
git add . && git commit -m 'docs: add V1 security and build guidance'
git push -u origin feat/private-gallery-v1
```
