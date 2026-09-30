# Legacy format, restoration and fault evidence

## Scope and source

SOURCE FACT: Audited baseline `a19218479eb9b9bcdb35ff0035fc78522263c891`. Phase 0 contract and Task 4 govern these changes. All fixture identities, keys, PIN/recovery secrets and media bytes are synthetic; no owner data is used. Primary AES-GCM and PGVIDEO1 authenticated contexts, serialization bytes and VDEK semantics remain unchanged. No Hidden store, migration, transfer, Tor, VPS or release was implemented.

## Implemented storage boundaries

IMPLEMENTED PHASE 0 CHANGE: `EncryptedIndexStore.save/saveSnapshot` and `EncryptedPayloadStore.writeAndVerify` accept optional final `commit: ((() -> Unit) -> Unit) = { it() }`. Whole payload and PGVIDEO1 renames run inside that callback; operation revocation can deny promotion after expensive preparation. Index encrypt/sync/authenticated byte-for-byte read-back happens before its guarded rename. Failure cleans staging; an exception after rename can leave a valid committed index or unindexed ciphertext orphan, preserving the existing copy. A callback that silently skips promotion cannot report success.

IMPLEMENTED PHASE 0 CHANGE: Per-instance `PrimaryWriteFaults.NONE` defaults to no-op. Eight named checkpoints for index and payload surround write/sync/verification/promotion. Faults never configure a global production mode and expose ciphertext files only. Existing video-migration behavior is not changed or forced; no owner video is rewritten by this task.

IMPLEMENTED PHASE 0 CHANGE: Local index reads reject encrypted length outside 28 bytes through 64 MiB + nonce/tag, bound decrypted plaintext to 64 MiB, refuse a missing index when `.vault`/`.deleting`/`.legacy` payloads survive or payload inventory cannot be read, validate legacy duplicate IDs/deletion state and negative plaintext lengths, and reject trailing bytes in all formats. Failures throw and leave damaged index bytes intact, without empty recovery or writes. This is a parser/resource admission bound, not a new format.

## Immutable inputs

IMPLEMENTED PHASE 0 CHANGE: `app/src/test/resources/phase0/legacy-v1/SHA256SUMS` contains 27 immutable corpus digests. Inputs include independently authored v1–v6 ledgers, whole payload and two-chunk PGVIDEO1, PIN/recovery envelopes, a full portable archive, expected logical data, authenticated malformed indices and tampered archives. Python `struct`/AESGCM/scrypt/HMAC/ZIP authored the bytes once, outside Kotlin serialization. Tests never invoke the generator. Android archive/expected asset copies are checked against frozen hashes.

## Observed RED/GREEN

TESTED EVIDENCE: Direct JUnit baseline run: 6 frozen tests, 1 expected failure, `authenticatedParserCorruptionFailsClosedWithoutReplacingIndex`: baseline accepted `legacy-duplicate` with no exception. After parser hardening, the same suite passed 6/6. Log locations during execution: `/tmp/phase0-legacy-direct-red.log` and `/tmp/phase0-legacy-direct-green.log`.

TESTED EVIDENCE: Guard/checkpoint scaffolding RED: `PrimaryWriteFaultsTest` ran 6 tests with 5 expected behavior failures: absent checkpoint errors, ignored revoked index/payload callbacks and accepted corrupted staged index. After implementation, 8 legacy/storage classes passed 40 tests. This runner used Kotlin 1.9.24, JUnit 4.13.2, BC 1.79 and declared org.json 20240303, compiling production crypto/store sources directly. It is additional evidence, not a substitute for the repository's Android/Kotlin 2.0.21 build. Baseline compiler emitted existing redundant-initializer warnings in `EncryptedPayloadStore`.

TESTED EVIDENCE: Missing-index regression ran 9 frozen tests with 1 expected failure: a surviving frozen payload was incorrectly treated as an empty Vault. After the guard, the expanded direct legacy/storage suite passed 48 tests across nine classes, including exclusion-canary export, local oversized-index rejection, durable-payload missing-index denial three proposed durability-model tests, skipped-promotion denial and duplicate destination detection at final promotion. Logs: `/tmp/phase0-missing-index-direct-red.log` and `/tmp/phase0-legacy-faults-direct-green.log`.

UNRESOLVED at storage commit: Full Gradle targeted tests reached unit compilation with full JDK17/Kotlin2.0.21 but were blocked by concurrently unfinished root Activity helper/setup-guard tests. Test-APK build was blocked by root `MainActivity.kt` line 463 (`key`) and line 1522 (`commit` function reference). These are integration-in-progress observations, not a final build verdict or omitted green claim. Final Android Gradle/test-APK outcomes will be recorded after integration settles. The targeted command is `:app:testDebugUnitTest --tests '*Phase0FrozenLegacyTest' --tests '*PrimaryWriteFaultsTest' --tests '*EncryptedIndexStoreTest' --tests '*EncryptedIndexMigrationFixtureTest' --tests '*EncryptedPayloadStoreTest' --tests '*ChunkedVaultVideoStoreTest' --tests '*VaultBackupArchiveTest' --tests '*VaultCollectionsIndexTest'`; canonical environment/serialized invocation is in the rehearsal document.

## What tests establish

Real store fault tests inject exception/ENOSPC category, cancellation, corrupt/truncated staging and final callback denial. Every index checkpoint is exercised; each prepromotion failure leaves the previous index byte-identical/authenticated and supports retry. Every whole/PGVIDEO1 checkpoint is exercised; the existing verified copy survives, cancelled staging is removed, postpromotion orphans safely reject duplicate overwrite, and restart-equivalent reconstruction restores indexed `.deleting` ciphertext. Frozen restore authenticates actual PGVIDEO1 header/chunks, full media digest and all collection/favourite/edit/provenance/vaultOnly/trash metadata. Production archive export includes only exact index/payload/manifest entries and excludes Browser/provider/staging/upload/migration canaries.

PROPOSED 2.0 DESIGN: `ProposedDurabilityProtocolModelTest` is wholly test-side. It models stage → authenticated verify → durable index → bound receipt → root promotion → reopened verification → optional source deletion. It checks every modeled interruption, mismatched operation/container/generation/revision/hash, corrupt root and revoked authority. It is not production journal/root-selection/receipt implementation and is not evidence of actual transfer or power-failure safety.

PHYSICAL ACCEPTANCE REQUIRED: Android process kill/reboot, decoder playback, actual disk-full/power-loss/directory durability, same-signer owner upgrade, rollback and OEM transfer remain unmeasured. The safe separate procedure is `docs/phase0/SYNTHETIC_RESTORE_REHEARSAL.md`.
