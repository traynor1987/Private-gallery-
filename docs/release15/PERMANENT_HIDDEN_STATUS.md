# Private Gallery 1.5 — permanent Hidden integration

TEST ONLY candidate available for disposable-media physical acceptance. Never use owner media before owner acceptance. The old 137-test pass applies to 53e5b2a, not to this integration. Main, Phase 3, PR59 and all V2 preservation refs remain outside this change.

## Implemented scope

The concealed 5 Installed / 1 About version / 4 Installed sequence opens independent Hidden PIN setup/unlock in TEST ONLY builds. Hidden reuses VaultHome, ProtectedAppShell and FullscreenMediaViewer. Adaptive thumbnail grids, sorting, filtering, selection, collections, favourite collection, photo swipe/zoom, protected video player, recently deleted/restore/permanent deletion and return to Primary use Hidden-only callbacks. Unsupported Hidden editing/generation, biometric and public export controls are omitted. Primary defaults and its repository/encryption formats are unchanged.

The runtime repository requires an opaque authority from HiddenSession. That authority belongs to one authenticated generation. Old repositories and queued batch jobs cannot inherit a later unlock. Final file promotions validate authority under the session gate. Close, leave, background and timeout revoke it. Hidden uses a fixed maximum 60-second session; PIN authentication attempts expire after 30 seconds. Photo Picker deliberately locks before launching; the entry and temporary URI results survive only that picker interaction, and import waits for Hidden PIN reauthentication. The entry host lives above Settings so Primary immediate-lock does not destroy picker results; while Primary is locked it renders no Hidden PIN or content, and both credentials remain necessary. No source URI is retained in persisted metadata; originals are never deleted.

## Storage contract

Root: noBackupFilesDir/hidden-media-v1. Credentials remain separately under hidden-v1-default/credential with an independent Keystore alias. All three record kinds (media, thumbnail, metadata) are AES-256-GCM with a fresh random 96-bit nonce and 128-bit tag. Header PGHV + version1 and authenticated data bind the Hidden domain, kind and stable UUID. Random IDs are the only filenames. Metadata version1 includes explicit hidden domain, media names/types/digests/state, collection names/memberships and favourite ID. No Primary key is accepted by the repository API.

Each import stages ciphertext, fsyncs it, decrypts/authenticates/readback-compares it, promotes immutable encrypted media and thumbnail, then atomically replaces authenticated metadata as the final commit point. Directory fsync follows promotions outside the session monitor, with payload directory durability completed before metadata publication. Atomic renames alone retain the short authenticated commit gate. A lock during payload sync rejects the later metadata commit; a lock after metadata rename cannot undo that completed commit. Empty authenticated metadata is committed before the first import, making first-import interruption recoverable. A failed sync after rename has an uncertain outcome: promoted ciphertext is retained. Reconcile authenticates metadata before removing proven encrypted orphans/staging. Corrupt committed metadata/media is retained and fails closed; it is never silently replaced with an empty vault. Deletion commits metadata before removing ciphertext; cleanup failures are reported.

Initial limits: 16 MiB per imported media item; 1 MiB encrypted thumbnail plaintext; 4 MiB metadata; 10,000 items, 1,000 collections and 100,000 memberships. Oversized or unsupported media fails without changing originals. Images are sampled before thumbnail decoding; video thumbnails use in-memory MediaDataSource. Full video viewing is bounded in-memory decryption with the existing Media3 player, not the V2 resource-ownership architecture.

No plaintext media files, disk thumbnail cache, browser handoff, export or Hidden backup API is present. Android backup is disabled/excluded and storage is under noBackupFilesDir. FLAG_SECURE applies to PIN and Hidden gallery dialogs. The master session key is wiped at lock. Operation authority is revoked immediately; an in-flight Cipher keeps its bounded operation key until finally/close wipes it, avoiding a lock race that could encrypt with a zeroed key. Owned byte arrays are wiped on normal supported disposal; shared sensitive thumbnail bitmaps are recycled on disposal. Immutable JVM strings, provider/native/decoder copies and hostile/rooted process memory are not claimed to have provable retirement. This is not V2-equivalent security.

## Upgrade and rollback

The memory prototype has no persisted gallery state to migrate. Its source/fixtures/tests remain preserved. Hidden v1 records have explicit domain/version/kind; future formats must authenticate and verify old records before replacing them, preserve IDs/membership, reject unsupported versions without overwriting, and provide a tested rollback boundary. Future V2 compatibility is a requirement to design and test, not an automatic promise. A downgrade to the old prototype cannot read these records and must not delete them. There is no Hidden credential recovery/backup yet: loss of PIN or device Keystore can mean permanent loss. No PIN change/transfer feature is included in this integration.

## Verification status

Host foundation373 checks, synthetic model checks, operation-generation revocation checks, encrypted record wrong-key/domain/corruption/bounds/interrupted staging/sync-failure checks and source/browser/WireGuard checks pass. First integration CI584 failed compilation because Android does not expose OsConstants.O_DIRECTORY; the corrected directory open uses O_RDONLY and retains fsync. Local Gradle is unavailable because the wrapper distribution download fails with Network is unreachable. GitHub CI supplies the Android compiler, JVM tests, lint and emulator regression suite; final exact-source terminal results are recorded below.

Independent review identified and prompted fixes for first-import interruption, persisted metadata exceeding reader bounds, excessive memory at64MiB (now16MiB), directory sync, late-result cleanup and queued requests inheriting reauthentication. The reviewer verified the opaque-generation fix and the final directory-sync split and found no further concrete blocker by inspection. A RED/GREEN host regression reproduced directory sync blocking the revocation monitor, then verified sync permits another thread to lock and prevents subsequent stale metadata promotion. This is focused review, not certification or a completed physical-device test.

CI587 on a56f0a88113adfba5f78b04592328c19121d89f0 passed JVM tests, lint, both APK builds and all157 Android tests (0failures/errors/skipped). Actual artifact ZIP checksum and JUnit XML were independently inspected; see evidence/ci587-summary.txt. The final directory-sync fix, source058f29499e5fc4e5b9813d3079c206d09137de4c, independently passed CI588: JVM tests, lint, both APK builds and157/157 Android tests (0failures/errors/skipped). Its downloaded artifact digest and actual JUnit XML were verified; see evidence/ci588-summary.txt.

Still required for acceptance: actual Photo Picker handoff; physical folded/unfolded Fold layouts, process kill/restart and low-space behaviour; video lock/background on device; permanent signer only after TEST ONLY acceptance. Actual disk-full fault injection and power-loss testing must not be represented by source interruption or simulated sync failure. No owner-media acceptance, installation, production release, or permanent-signing claim is made here.

Supported API references: [Android cryptography](https://developer.android.com/privacy-and-security/cryptography), [Android Os](https://developer.android.com/reference/android/system/Os). These support the chosen primitives/APIs; they are not evidence that this implementation passed tests.

CI585 on775e02 compiled, passed JVM/lint/APK assembly, and passed151/152 Android cases. All11 repository tests and encrypted video FIRST_FRAME/background RELEASED passed. One UI test called the Activity Back dispatcher while a Dialog viewer was open, closing/locking the entry before the favourite assertion. The test now uses the real viewer toolbar and separately verifies fallback Back locks Hidden; its favourite assertion remains. The corrected assertions passed CI587 and CI588. See evidence/ci585-summary.txt.

TEST ONLY state is package/Keystore-bound and cannot be assumed portable to production. Production acceptance must plan fresh Hidden setup or a separately approved and verified migration; no test-to-production data migration is authorized.

CI586 failed only compilation of the added fake-picker test: Intent.setClipData returns Unit, not Intent. The result construction is corrected with apply { clipData=data }; application source and assertions are unchanged. No emulator result is claimed for586. See evidence/ci586-summary.txt.


## Preserved baseline and evidence

V1 baseline: a19218479eb9b9bcdb35ff0035fc78522263c891 (1.0.27, code28). Durable V2 recovery and independently verified preservation branch: d6c9ab454a9f0ea90bcfe12783eb68e51acdd368. Exact local-history archive: 24c92a6ee9019069de48671cd29576418e0de8f1; its bundle checksum and restoration evidence remain in STATUS.md. Main93ed56f and Phase3/PR59 head79bb192 remain unchanged. PR59 remains open/draft/unmerged. No claim is made about unpublished state in inaccessible Work executions; the recovered V2 workspace was left untouched.

The original 137-test prototype result is CI583 at53e5b2a3a0b117bc20a16a0d91176dd99751ad54. It remains historical evidence, not the verification of this permanent implementation.

## Changed implementation areas

- MainActivity.kt: optional shared Vault capabilities and preview ownership, concealed entry host and Primary lock coordination. Defaults preserve Primary presentation.
- HiddenFoundationDialog.kt / HiddenVaultDialog.kt: authenticated entry, shared Vault composition, Photo Picker handoff, Hidden-only operations and lifecycle disposal.
- HiddenSession.java: operation key scopes, opaque authenticated generations and short commit gate.
- HiddenRecordStore.java / HiddenRepository.kt: authenticated encrypted records and Hidden-only media/metadata/collection persistence.
- HiddenRepositoryTest.kt / HiddenVaultIntegrationTest.kt and host runners: focused storage, UI, isolation, revocation and regression evidence.

No existing Primary storage/encryption format, browser or WireGuard implementation is replaced. Existing V1 regression success is bounded to automated synthetic cases; it cannot prove the owner's physical library remains readable without the owner's device acceptance. No real owner media was accessed.


## Final verified TEST ONLY candidate

Tested application source: **058f29499e5fc4e5b9813d3079c206d09137de4c**. [CI588](https://github.com/traynor1987/Private-gallery-/actions/runs/39037386121) is SUCCESS. Later evidence commits alter documentation only; no app/build/test input changes. There are no unresolved automated failures at this source; the earlier real compiler/test failures remain documented above.

[Download TEST ONLY APK and test reports](https://github.com/traynor1987/Private-gallery-/actions/runs/39037386121/artifacts/12103326506) (GitHub artifact ZIP; extract outputs/apk/debug/app-debug.apk). APK SHA256:65985742bcdfb48e1a117281cfa6e3be9303df4601e306862287150a159c288f. Separate package uk.co.traynor.privategallery.hiddenprototype, version1.0.27-TEST-ONLY/code28; Android Debug certificate SHA256:ac08a4bf391a78f1bb984f7811cf2f8c77c36705e293653911b158c6bad86783. Manifest and APK v2 signature/content digest were inspected independently. Package separation supports side-by-side installation; actual owner-device installation is not claimed. No permanently signed production candidate was prepared.

The shared Vault UI, independent encrypted persistence and PIN/session integration are implemented and pass automated verification. Device acceptance remains open. Follow [PHYSICAL_TEST_CHECKLIST.md](PHYSICAL_TEST_CHECKLIST.md) using only the [disposable fixture kit](fixtures/hidden-vault-disposable-media.zip). Hidden is still disabled in normal production builds. Next authorized work is TEST ONLY physical acceptance and any fixes it identifies; production package/signing and real-media use require separate acceptance/approval. No V2-equivalent guarantee is made.
