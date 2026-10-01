# Phase 0 synthetic clean-state restore rehearsal — 2026-10-01

## SOURCE FACT

Scope: `phase0/security-admission`, starting HEAD `fb223993ad69ba3dbaac57b2c5462daffc7f5550` (main `a19218479eb9b9bcdb35ff0035fc78522263c891`). This is synthetic recovery/backup evidence only. It does not authorize owner-data access, migration, Phase 1, Hidden, release, installation on an owner's device, or a Samsung physical acceptance claim.

The existing `Phase0FrozenRestoreRehearsalTest.independentlyFrozenBackupRestoresVideoTrashProvenanceAndRecoveryBytes` already restores the immutable independent Python-authored `backup-v1.pgvault`, checks its checked-in SHA-256, reconstructs a PIN adapter, authenticates image and two-chunk PGVIDEO1 ciphertext, and checks selected metadata/recovery bytes. It remains intact. `VaultBackupRestoreTest` already covers truncated input and PIN/restore ownership races; `RecoveryConfirmationAdapterTest` already covers pending setup, re-entry, restart and confirmation through the Android preference adapter.

Fixture expectation source: `app/src/androidTest/assets/phase0/expected.json` and `docs/phase0/fixtures/freeze_legacy_corpus.py`. Neither immutable assets nor their authoring tool are regenerated or modified by this addition. The frozen video is an actual authenticated PGVIDEO1 container of synthetic bytes, not evidence of a playable MP4 or media-decoder success. The image has synthetic bytes under an image MIME type, not a rendering claim.

## IMPLEMENTED

Three added methods extend the existing frozen rehearsal class; `Phase0SyntheticRestoreFixture.kt` contains all new isolation, fixture and comparison utilities under `androidTest` only. No production code is changed.

`confirmedPrimaryExportRestoresEveryLogicalFieldIntoIndependentEmptyState` performs this chain:

1. Verify the frozen archive and expected JSON against immutable SHA256SUMS, then seed only its existing encrypted index/payload entries in a randomly named synthetic source root. Wrap the public synthetic VDEK in a source PIN envelope, unlock that PIN through `PinVaultKeyStore`, and open a real `PrimarySessionAuthority`/operation.
2. Assert every source item field and all collection/membership/edit/favourite metadata against independent fixture literals. Through the actual `RecoveryVaultKeyStore`, create pending recovery, reconstruct the preference adapter, prove pending recovery cannot export an envelope, re-enter the generated matching secret and CONFIRM through the authenticated operation's commit callback. Reconstruct the confirmed adapter before exporting.
3. Place public synthetic Browser-engine/history, AI preference credential and plaintext-stage canaries in the source namespace. Export through `AndroidVaultRepository.exportBackup`, rather than calling the archive writer directly. Require the exact frozen entry allowlist, exact manifest/recovery field sets, original ciphertext entry bytes, and original newly confirmed recovery-envelope bytes.
4. Assert that a separate destination `ContextWrapper` has a different root and empty files/PIN/recovery preferences. Restore with the matching generated recovery secret and a different local PIN. Reconstruct destination key/recovery adapters; reject the source PIN, unlock the new PIN and compare the VDEK.
5. Compare every item scalar, SHA-256 and nonce, every collection field (including cover/pinned destination), every membership/timestamp, current/previous crop and favourite ID. Exercise destination repository consumers for active/trash/collection/edit state. Authenticate/decrypt image and real PGVIDEO1 payloads in memory, compare complete lengths/digests to immutable expected values, and wipe those bytes. Require only the expected ciphertext file inventory, verbatim ciphertext digests, and preserved recovery-envelope bytes; unlock the restored recovery adapter with the same matching secret.

`wrongRecoveryAndRehashedCorruptVideoLeaveIndependentDestinationUntouched` uses the same actual confirmed source/application export, then tries a generated wrong matching-format recovery secret and a video payload with its final authentication-tag byte changed **and its public archive hash recomputed**. The second input must reach cryptographic payload authentication rather than merely fail the public hash check. After each failure, assert no destination installed/staged files or directories, no PIN/recovery preference changes, unavailable reconstructed destination credentials, and unchanged source file digests/PIN/recovery preferences.

`faultedRestoreCommitBoundariesPreserveAuthenticatedRootAndAllowMatchingRetry` injects a deterministic exception before and after each of the actual three restore commit actions: root rename, recovery-envelope installation and PIN-envelope installation (six independent synthetic destinations). Reconstruct both credential adapters after each failed call; require no partial slots/staging, preservation of the verified ciphertext root whenever rename completed, the full logical snapshot and exact ciphertext digests, and a setup guard that refuses empty Primary creation over retained ciphertext. Retry each case with the same matching archive/recovery secret and a new local PIN; reconstruct the PIN/recovery adapters and verify complete snapshot/key/envelope identity. Source file/preference snapshots and the independent archive digest must remain unchanged throughout.

SOURCE FACT: The production assignment is inside `commit { rollbackPin = keys.installPinForRestoredVault(...) }`. An exception from the hook after its PIN action therefore occurs after assignment of the rollback receipt; the test explicitly requires PIN-slot cleanup. If runtime evidence contradicts this source assessment, record it as a production defect; do not silently alter the implementation. The injected hooks model app-level exception/interruption boundaries, not actual Android process termination, power failure or filesystem durability.

The test-only context overrides files, cache, no-backup, application context and preference lookup. All roots live under random cache namespaces; preference names also receive a unique prefix. Cleanup removes only those synthetic roots/preferences and wipes held key/secret/archive buffers. No MediaStore, owner vault path, owner key slot, Browser engine, AI network call or Android process-kill is invoked.

Named mutations caught:

| Mutation | Observable failure |
| --- | --- |
| Export traverses arbitrary files/preferences or includes plaintext/browser/provider state | Exact archive entry/manifest allowlists fail on seeded canaries |
| Export drops trashed video or changes encrypted bytes | Frozen ciphertext inventory/byte equality fails |
| Restore loses/changes the populated fixture metadata values | Independent full logical snapshot comparison fails |
| Restore binds the source PIN instead of the requested new PIN | Old-PIN rejection/new-PIN unlock fails |
| Restore rewraps/replaces recovery envelope | Salt/nonce/ciphertext identity or same-secret recovery unlock fails |
| Reader trusts recomputed public digest and skips PGVIDEO1 authentication | Corrupted-video restore must throw and leave destination empty |
| Restore installs ciphertext or credential slots before authentication / leaves staged files | Wrong-secret/corruption unchanged-state assertions fail |
| Commit exception deletes verified root/source/backup, leaks PIN/recovery slots, or permits empty setup over retained ciphertext | Six-case reconstructed boundary matrix and matching-archive retry fail |

## AUTOMATED TEST EVIDENCE — executed PASS on clean API36 emulator

Root's fresh local `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed (5m14s; 382 JVM tests, zero failures/errors/skips). All new instrumentation sources compiled. Local merged-manifest/debug packaged exclusions passed. The local environment uses official API36/build-tools and OpenJDK17.0.20 modules with missing CLI launchers reconstructed; ordinary CI uses its unchanged Temurin17 clean toolchain. Actual PR Android CI [36858153696](https://github.com/traynor1987/Private-gallery-/actions/runs/36858153696), job `110355501042`, on exact test commit `1cf38b6e23cfd2964ac82c415e305149b489b59b` completed SUCCESS. Its downloaded XML reports 142 Android cases with zero failures/errors/skips; all four frozen rehearsal methods passed. New method durations: six commit-boundary cases 14.297s, wrong-secret/rehash-tamper rejection 3.286s, complete confirmed export/new-PIN restore 4.618s. Unchanged independent frozen restore 1.403s. Device: Google ATD built for x86_64, API36; this is emulator evidence, not Samsung physical acceptance.

The successful complete-restore test now writes only public synthetic digests and assertions to `additionalTestOutputDir` (fallback test evidence directory): run-specific confirmed export hash, restored ciphertext inventory/digests, expected authenticated media digests, distinct/empty destination facts and API/model. It writes no PIN, VDEK, envelope or recovery secret. CI's existing upload captures additional test output. That test-only output addition is separately compiled before CI. Static `git diff --check` is only a whitespace check and is not build/test evidence. Non-null pinned collection destinations are not populated by this immutable fixture; an implementation that always defaults that field to null would not be detected here. No production mutation run has been performed; the table names intended mutation sensitivity, not measured mutation-test results.

The executed ordinary CI scope includes the existing `Phase0FrozenRestoreRehearsalTest` class plus `VaultBackupRestoreTest` and `RecoveryConfirmationAdapterTest` on the authorized synthetic API 36 emulator. Run no Gradle process concurrently with the root's serialized toolchain work.

Actual process death/reboot, playable image/video decoding, Samsung/OEM durability, owner same-signer upgrade, biometric hardware and rollback are outside this rehearsal. The owner upgrade is independently reported; current mandatory versus residual physical dispositions are in the focused closeout report. This rehearsal supplies no such evidence.

## Retained CI artifact and exact restore identity

Artifact `11161166357` (`private-gallery-debug-and-instrumentation-diagnostics`, 62,295,577 bytes), run `36858153696`, was downloaded and independently SHA256-checked: `bcfa87759f2f98c57995955e1a8801739db3690b3758d55761510c18a9ef2d5a`. The artifact's debug JVM XML reports 382/89 suites and evidence JVM XML 389/90 suites; all zero failures/errors/skips. Its normal instrumentation XML reports 142/zero failures/errors/skips.

Additional output `phase0-restore-3cd0cbf4-c3bf-4c55-8fb6-e3069408b317.json` was extracted unchanged to [clean-state-restore-1cf38b6.json](clean-state-restore-1cf38b6.json), SHA256 `fdd510c1bc48460013c3482dde6f670346e573f5c2f9a2309f5f233f91b815c2`. Run-specific confirmed application export SHA256: `6d788b639bbb177a8d46fcd839c71cda0b9177b22102faeda97e8e778cab99b0`. Destination started empty with distinct source ownership; new local PIN and matching recovery authenticated. Restored encrypted index SHA256 `19f1fc8f99fec890c77c335bcac86d66dd61ab251ad5df131b5ca19e721c592d`; image ciphertext `696a27efd39c1958152c61a5b0228c2315cc5c516b10df227e36f46f44dfd9af`; PGVIDEO1 ciphertext `281fdad875af45982f79dd8ed15816a338833283a7c0b68812f29b38abeccb79`. Whole authenticated image/video plaintext digests match the immutable expectations recorded in the JSON. Every populated fixture metadata field was asserted, not only archive readability.

No generated recovery secret, VDEK, PIN or plaintext media is included in the retained JSON. Test-generated secret/archive buffers and disposable roots are cleaned; the independent immutable corpus/archive remains checked in unchanged. The report does not claim the generated random-secret archive was retained for owner use.
