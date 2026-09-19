# VPN, Secure Acquisition, and Favourite Collection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GPL-compliant OpenVPN2 Browser tunnel, fail-closed browsing, direct encrypted Browser acquisition, and one generic Favourite Collection destination.

**Architecture:** Private Gallery imports a narrow OpenVPN2-only engine subset, never OpenVPN3. Browser traffic is controlled by an engine-confirmed connection state. A source-neutral Vault import coordinator is the only route for Browser downloads and viewport screenshots. Favourite navigation resolves one collection ID, so Jenna remains data rather than a hard-coded product feature.

**Tech Stack:** Kotlin, Compose, Android VpnService, Android Keystore, WebView, existing AES-GCM Vault repositories, MediaStore/Photo Picker, Gradle/CMake, GitHub Actions.

**Spec:** docs/superpowers/specs/2026-09-19-vpn-vault-favourite-design.md

## Global Constraints

- GPL-2.0-only; retain upstream OpenSSL and Apache-2.0 exceptions/notices.
- Pin ics-openvpn v0.7.65 at ee574f6ff65703e09c1a1f6c2d9243d7940f3f8d, OpenVPN2 at 566ebc9f23259f5d55a8cc763c761073090f0595, OpenSSL at 19a1b558a09892381199b2d1484fe1c0ba6d76cf, and LZO 2.10 source tree 4bac163027dc61c7ee15679e53a71d83326eccce.
- Exclude every OpenVPN3/AGPL/MPL source, native target, generated binding and artifact.
- Never commit/print/export a signing secret, profile, VPN credential, PIN, recovery key, cookie/session, browsing data, Vault data/media, or plaintext user content.
- Preserve the permanent Android signing identity and all existing vault transaction, key and update invariants.
- No public plaintext intermediary for Browser download or screenshot ingestion.
- After release, feature freeze: bugs, security, compatibility and small polish only.

## Review Focus

- VPN engine says Connected but Android permission/tunnel creation fails: Browser must remain blocked.
- VPN drops after WebView navigation begins: new navigation/download work is cancelled; no Vault item is committed.
- Remote download name uses path traversal or blank MIME: opaque Vault payload name is used and unsafe metadata is rejected.
- Existing Jenna collection is renamed/deleted during migration: favourite identity follows stable ID and deletion only clears favourite.
- Locked app during screenshot/download: browser acquisition is cancelled and no plaintext output survives.

---

### Task 1: Licence, notices, reproducible engine boundary

**Files:**
- Create: LICENSE, NOTICE, docs/OPENVPN_DEPENDENCIES.md, third_party/openvpn2/.gitmodules, scripts/verify_openvpn2_only.sh
- Modify: .gitignore, README.md, app/build.gradle.kts, .github/workflows/ci.yml

**Interfaces:**
- Produces: a build-time engine source layout and `verify_openvpn2_only()` CI gate.

- [ ] **Step 1: Write failing audit tests**
```bash
./scripts/verify_openvpn2_only.sh
# Expected before implementation: FAIL: missing dependency manifest / exclusion guard
```

- [ ] **Step 2: Add GPL and notice artefacts**
```text
Private Gallery is GPL-2.0-only.
OpenVPN2, ics-openvpn and LZO notices plus the OpenSSL/Apache-2.0 exceptions are retained verbatim.
```

- [ ] **Step 3: Add exact pin and reject OpenVPN3**
```bash
test "$(cat third_party/openvpn2/REVISION)" = "566ebc9f23259f5d55a8cc763c761073090f0595"
! rg -n "openvpn3|ovpn3|AGPL" third_party/openvpn2/app-source third_party/openvpn2/CMakeLists.txt
```

- [ ] **Step 4: Run audit gate**
Run: `./scripts/verify_openvpn2_only.sh`  
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add LICENSE NOTICE docs/OPENVPN_DEPENDENCIES.md third_party scripts .github app/build.gradle.kts README.md .gitignore
git commit -m "build: add GPL OpenVPN2 source compliance"
```

### Task 2: Restricted profile parser and encrypted credentials

**Files:**
- Create: app/src/main/java/uk/co/traynor/privategallery/core/vpn/OpenVpnProfilePolicy.kt, OpenVpnProfileRepository.kt, VpnCredentialStore.kt
- Test: app/src/test/java/uk/co/traynor/privategallery/core/vpn/OpenVpnProfilePolicyTest.kt, VpnCredentialStoreTest.kt
- Modify: AndroidManifest.xml, MainActivity.kt

**Interfaces:**
- Produces: `ValidatedOpenVpnProfile`, `OpenVpnProfilePolicy.parse(text): Result<ValidatedOpenVpnProfile>`, and `VpnCredentialStore`.

- [ ] **Step 1: Write failing parser tests**
```kotlin
@Test fun inlineProfileIsAccepted() =
  assertTrue(OpenVpnProfilePolicy.parse("client\nremote vpn.example 1194\n<ca>cert</ca>").isSuccess)

@Test fun scriptsAndExternalFilesAreRejected() =
  assertTrue(OpenVpnProfilePolicy.parse("up /system/bin/sh").isFailure)
```

- [ ] **Step 2: Run targeted tests**
Run: `./gradlew testDebugUnitTest --tests '*OpenVpnProfilePolicyTest'`  
Expected: FAIL because policy does not exist.

- [ ] **Step 3: Implement minimal restrictive parser**
```kotlin
data class ValidatedOpenVpnProfile(val id: String, val sanitizedConfig: String, val requiresCredentials: Boolean)
fun parse(text: String): Result<ValidatedOpenVpnProfile>
```
Reject script/plugin/management/external-file directives before persistence.

- [ ] **Step 4: Add Keystore-backed credential persistence tests and implementation**
```kotlin
interface VpnCredentialStore {
  fun save(profileId: String, username: String, password: CharArray)
  fun loadUsername(profileId: String): String?
  fun forget(profileId: String)
}
```
Only encrypted bytes are persisted; password is never returned or logged.

- [ ] **Step 5: Run targeted tests and commit**
Run: `./gradlew testDebugUnitTest --tests '*OpenVpnProfilePolicyTest' --tests '*VpnCredentialStoreTest'`  
Expected: PASS.  
Commit: `feat: add private OpenVPN profile storage`.

### Task 3: App-owned VPN engine and Browser fail-closed controller

**Files:**
- Create: app/src/main/java/uk/co/traynor/privategallery/core/vpn/BrowserVpnController.kt, PrivateGalleryOpenVpnService.kt, OpenVpnEngine.kt
- Modify: AndroidManifest.xml, MainActivity.kt, core/browser/BrowserPolicy.kt, ui/PrivateBrowser.kt, core/ui/SettingsSections.kt
- Test: app/src/test/java/uk/co/traynor/privategallery/core/vpn/BrowserVpnControllerTest.kt

**Interfaces:**
- Consumes: ValidatedOpenVpnProfile and VpnCredentialStore.
- Produces: `VpnConnectionState`, `BrowserVpnController.canUseNetwork: Boolean`, `connect()`, `disconnectAfterGrace()`, `onLock()`.

- [ ] **Step 1: Write failing lifecycle tests**
```kotlin
@Test fun requireVpnBlocksBrowserUntilEngineConfirmsConnected() {
  val controller = BrowserVpnController(requireVpn = true, engine = FakeEngine())
  controller.connect()
  assertFalse(controller.canUseNetwork)
  controller.onEngineState(EngineState.Connected)
  assertTrue(controller.canUseNetwork)
}
```

- [ ] **Step 2: Run the controller test**
Run: `./gradlew testDebugUnitTest --tests '*BrowserVpnControllerTest'`  
Expected: FAIL because controller does not exist.

- [ ] **Step 3: Implement state machine and engine adapter**
```kotlin
sealed interface VpnConnectionState { data object Connecting; data object Connected; data class Failed(val category: FailureCategory) }
interface OpenVpnEngine { fun connect(profile: ValidatedOpenVpnProfile); fun disconnect(); val state: StateFlow<EngineState> }
```
Only the OpenVPN2 native service is linked. Browser policy rejects navigation and download requests unless the controller reports engine-confirmed Connected.

- [ ] **Step 4: Add Settings UI and Browser status tests**
Test configured/unconfigured, auto-connect, require-VPN, reconnect, existing-VPN conflict, 30-second grace cancellation and lock teardown.

- [ ] **Step 5: Run tests and commit**
Run: `./gradlew testDebugUnitTest --tests '*BrowserVpnControllerTest' --tests '*BrowserPolicyTest'`  
Expected: PASS.  
Commit: `feat: gate Browser traffic on OpenVPN connection`.

### Task 4: Source-neutral verified Vault ingestion

**Files:**
- Create: app/src/main/java/uk/co/traynor/privategallery/core/vault/VaultImportCoordinator.kt, VaultImportSource.kt
- Modify: AndroidVaultRepository.kt, MoveToVaultUseCase.kt, DeviceGalleryRepository.kt
- Test: app/src/test/java/uk/co/traynor/privategallery/core/vault/VaultImportCoordinatorTest.kt

**Interfaces:**
- Produces: `VaultImportCoordinator.import(source): Result<VaultItem>`.
- Consumes: source stream, safe display metadata, authenticated Vault session.

- [ ] **Step 1: Write failing verification-order test**
```kotlin
@Test fun failedVerificationDoesNotCommitVaultItem() {
  val result = coordinator.import(FailingVerificationSource())
  assertTrue(result.isFailure)
  assertTrue(index.items().isEmpty())
}
```

- [ ] **Step 2: Run the import test**
Run: `./gradlew testDebugUnitTest --tests '*VaultImportCoordinatorTest'`  
Expected: FAIL because coordinator does not exist.

- [ ] **Step 3: Implement staged stream encryption**
```kotlin
interface VaultImportSource {
  val proposedMimeType: String?
  val proposedDisplayName: String?
  fun open(): InputStream
}
```
Stream to staged encrypted payload; verify authenticated decrypt/read; commit metadata only on success; clean staging on every failure.

- [ ] **Step 4: Move existing sources to coordinator and rerun lifecycle tests**
Keep Move deletion after verification only.

- [ ] **Step 5: Commit**
```bash
git commit -m "refactor: share verified Vault ingestion"
```

### Task 5: Browser download and viewport screenshot to Vault

**Files:**
- Create: app/src/main/java/uk/co/traynor/privategallery/core/browser/BrowserDownloadSource.kt, BrowserScreenshotSource.kt
- Modify: ui/PrivateBrowser.kt, MainActivity.kt, VaultImportCoordinator.kt
- Test: app/src/test/java/uk/co/traynor/privategallery/core/browser/BrowserDownloadPolicyTest.kt, app/src/test/java/uk/co/traynor/privategallery/core/vault/BrowserAcquisitionTest.kt

**Interfaces:**
- Consumes: BrowserVpnController, VaultImportCoordinator.
- Produces: `Save to Vault` download action and Browser overflow screenshot action.

- [ ] **Step 1: Write failing download safety tests**
```kotlin
@Test fun vpnLossDuringDownloadDoesNotCommit() = assertTrue(downloadResult.isFailure)
@Test fun traversalNameNeverBecomesPayloadPath() = assertFalse(payloadPath.contains(".."))
```

- [ ] **Step 2: Run download tests**
Run: `./gradlew testDebugUnitTest --tests '*BrowserDownloadPolicyTest' --tests '*BrowserAcquisitionTest'`  
Expected: FAIL because Browser sources do not exist.

- [ ] **Step 3: Implement user-confirmed direct stream**
WebView detects a download, presents Save to Vault/Cancel, obtains a VPN-gated response stream, sanitises metadata and calls VaultImportCoordinator. No Android Downloads write is used.

- [ ] **Step 4: Implement in-memory viewport capture**
```kotlin
fun captureWebViewport(webView: WebView): BrowserScreenshotSource
```
Capture only the WebView content layer; encode in memory and import. Keep FLAG_SECURE state unchanged.

- [ ] **Step 5: Run tests and commit**
Run: `./gradlew testDebugUnitTest --tests '*BrowserDownloadPolicyTest' --tests '*BrowserAcquisitionTest'`  
Expected: PASS.  
Commit: `feat: save Browser downloads and screenshots to Vault`.

### Task 6: Generic single Favourite Collection

**Files:**
- Create: app/src/main/java/uk/co/traynor/privategallery/core/vault/FavouriteCollection.kt
- Modify: VaultCollections.kt, EncryptedIndexStore.kt, MainActivity.kt, core/ui/AppNavigationPolicy.kt
- Test: app/src/test/java/uk/co/traynor/privategallery/core/vault/FavouriteCollectionTest.kt, ui/FavouriteDestinationTest.kt

**Interfaces:**
- Produces: `FavouriteCollectionStore.get(): String?`, `set(collectionId)`, `clearIfDeleted(collectionId)`.

- [ ] **Step 1: Write failing invariant tests**
```kotlin
@Test fun selectingBReplacesA() { store.set("a"); store.set("b"); assertEquals("b", store.get()) }
@Test fun freshStateDoesNotCreateJenna() { assertNull(store.get()) }
```

- [ ] **Step 2: Run favourite tests**
Run: `./gradlew testDebugUnitTest --tests '*FavouriteCollectionTest'`  
Expected: FAIL because no favourite store exists.

- [ ] **Step 3: Implement ID-based store and one-time Jenna migration**
Read legacy pinned Jenna only when it exists; write its stable ID once. Never create Jenna on fresh installation.

- [ ] **Step 4: Update Collection actions and centre destination**
Set as favourite replaces current favourite atomically. Centre label derives from live collection name and truncates visually; accessibility retains full text. Deleting favourite clears only the ID.

- [ ] **Step 5: Run tests and commit**
Run: `./gradlew testDebugUnitTest --tests '*FavouriteCollectionTest' --tests '*FavouriteDestinationTest' --tests '*VaultCollectionsIndexTest'`  
Expected: PASS.  
Commit: `feat: generalise favourite collection navigation`.

### Task 7: Migrations, release quality gates and signed release preparation

**Files:**
- Modify: app/build.gradle.kts, .github/workflows/ci.yml, .github/workflows/release.yml, docs/RELEASE_SIGNING.md, README.md
- Create: docs/FEATURE_FREEZE.md, scripts/no_secret_scan.sh
- Test: app/src/androidTest/java/uk/co/traynor/privategallery/MigrationRegressionTest.kt

**Interfaces:**
- Consumes: all prior feature boundaries.
- Produces: release-ready source, licence and no-secret gates.

- [ ] **Step 1: Write failing migration/no-secret gate tests**
```bash
./scripts/no_secret_scan.sh
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=MigrationRegressionTest
```

- [ ] **Step 2: Implement migration fixture**
Install pre-feature encrypted index fixture with Jenna, memberships, crop/recovery state; verify update preserves it and selects Jenna favourite once.

- [ ] **Step 3: Run the complete local verification matrix**
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./scripts/verify_openvpn2_only.sh
./scripts/no_secret_scan.sh
```

- [ ] **Step 4: Commit and push**
```bash
git commit -m "chore: prepare secure VPN Vault release"
git push origin feature/vpn-vault-favourite
```

- [ ] **Step 5: Verify trusted CI and prepare signed release**
Confirm CI ran tests, lint, both debug assemblies, source/no-secret gates and OpenVPN3 exclusion. Merge only after green. Increment version/build, keep existing signer, then prepare the trusted manual tag dispatch.
