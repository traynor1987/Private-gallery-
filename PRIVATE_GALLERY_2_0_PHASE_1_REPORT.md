# Private Gallery 2.0 — Phase 1 report

1 October 2026. PRIMARY-only scoped security architecture. Implementation and synthetic automated evidence are separate from signed-build and physical acceptance. **Phase 1 acceptance remains open. The former candidate `77c95d14339309ba5ec1dbab6e4c6db82276bb3f` is SUPERSEDED and must not be installed, accepted, merged or promoted.** No Phase 2 work is authorized by this report.

## Provenance and candidate identity

| Field | Independently verified evidence |
| --- | --- |
| Authoritative starting main / merged Phase 0 | `0a582c6458e2899dde3b1b7be57eba3f4f1c2b20`, PR #56 merged. |
| Main CI | Run `36867322599`, completed/success. Phase 0 closeout remains authoritative; satisfied gates were not reopened. |
| Recovered local Phase 1 | Worktree survived with commit `1e0a5eede6a2df14b09d86603322fa27814eb8e5` plus actual adapter diffs. Source was inspected and preserved, rather than inferred from previous messages. |
| Dedicated branch / review | `phase1/primary-scoped-security`; draft [PR #57](https://github.com/traynor1987/Private-gallery-/pull/57). No merge or release. |
| Reviewed implementation milestone | `cd363624318bb6b03926bbb3ddbf4c2c7a92f2ae`; source-identical tree `efea63368fda4880ff8aac6c4a27827e95ad8bf0`. Later integrated video/Gallery callback coverage is recorded by this report's commit. |
| Final exact acceptance candidate | The pushed branch HEAD after this report's final validation addendum. The handoff records its full SHA; the signed workflow must record exactly that SHA in `build-sha.txt`. A changed HEAD requires renewed candidate verification. |
| Signed/device status | NOT RUN for Phase 1. Existing Phase 0 artifacts and owner acceptance do not establish Phase 1 acceptance. |

Recovery details and preserved local/remote commit mappings are in [RECOVERY_AND_REQUIREMENTS.md](docs/phase1/RECOVERY_AND_REQUIREMENTS.md). The inherited admission-stop report is preserved as [historical evidence](docs/phase1/HISTORICAL_ADMISSION_REPORT.md), not the current status. The architecture audit dated 2026-09-29 was retrieved and consulted, including sections 26–28. The current user specification and merged Phase 0 contract govern this implementation.

Exact changed files are listed in [CHANGED_FILES.txt](docs/phase1/CHANGED_FILES.txt). The delta covers scoped authority/legacy adapters, Primary repository/media/UI callers, network ownership, synthetic tests, CI branch admission and documentation; frozen fixtures and crypto format specifications have no delta.

## Implemented architecture

The existing Primary root, VDEK and encodings remain behind a fixed legacy Primary adapter. `LegacyPrimaryContainer` rejects a foreign identity before consulting context/root state and has no second production root. `ContainerId.PRIMARY` is the only production factory. Synthetic identities exist only for isolation tests and have no production key/root configuration.

Each successful unlock opens a new immutable `SessionEpoch`. Each `PrimaryOperation` has its own `OperationId`, immutable scope set and revocable copied-key lease. `PrimarySessionBinding` lets UI callbacks retain their originating epoch without retaining a key. UI callbacks issue the concrete permission set they need inside the original binding; they cannot revive after lock/unlock. Default operations grant READ only. Forks attenuate permissions and reject escalation.

Permissions cover READ, WRITE, LOCAL_EDIT, REMOTE_AI_EGRESS, BROWSER_UPLOAD_EGRESS, CREDENTIALS, BACKUP and existing generic EGRESS for restore/backup policy. Production does not issue the full enum set. A synthetic fixture may explicitly grant all scopes to exercise legacy behavior; this does not establish a production god-capability.

Item handles include container, originating epoch, item ID and immutable payload SHA revision. The repository validates identity before lookup, resolves the authenticated current record, checks revision and rechecks live authority. Policy decisions use that record, not caller-supplied copies. Presentation revisions also include crop state. Collection handles include container, epoch, ID and creation generation; public collection APIs accept handles. Internal legacy Primary UI string adapters resolve only inside an already captured Primary repository/session. No handle is serialized into legacy indexes, equality, copies or saved-instance state.

Deadline authorization uses Android monotonic elapsed time. Read, decrypt, network, commit and publication entry points check expiry; authorization never relies on delivery of the timer task. Existing serialized final commit/revocation gates are retained. Crypto preparation and PIN KDF work happen before final promotion checks. Lock invalidates admission first; the same VDEK on a subsequent unlock never makes an old epoch current.

The inherited resource registry owns jobs, streams, descriptors and session resources. Operation completion closes copied-key leases. Intentionally delivered UI/video resources transfer to the original session registry. Revocation cancels work, closes resources, removes owned temporary plaintext and wipes mutable key/buffer/bitmap copies where controllable. Reopening refuses incomplete/failed cleanup. Publication after an operation's key lease closes is intentionally permitted only for its still-live original epoch; decrypt, commit and fork require an open lease. Native, immutable string, JVM, codec, GPU and Chromium erasure is not claimed.

## Protected asynchronous path audit

| Path | Origin and final boundary |
| --- | --- |
| Index/items/collections/Favourite/Recently Deleted | Original operation-backed repository; typed media/collection handles; guarded metadata promotion and epoch publication. Load failures set CORRUPT or UNAVAILABLE instead of manufacturing an empty result. |
| COPY/MOVE imports and source-deletion callbacks | Captured operation, guarded streams and durable index/payload promotion. Platform deletion completion retains original items and operation. Sole verified copy protections and Phase 0 fault infrastructure remain. |
| Previews and scrolling | Validate the supplied item before memory/disk cache admission; identity contains Primary namespace, item and revision/crop state. Original-epoch delivery and registered mutable pixels. No alternate root or cache fallback. |
| Fullscreen images and Gallery thumbnails | Original owned job, read capability and epoch publication. Protected buffers/pixels are session-owned; Gallery thumbnail cache is cleared on revocation. Gallery remains external device media, not a second encrypted container. |
| Video | Scoped item resolution before payload lookup; bounded authenticated PGVIDEO1 reader/session registered to the original operation/session. Future reads fail and live DataSources close on revocation. Post-read authorization denies and wipes plaintext if revocation occurs during a read. Legacy playback conversion retains existing behavior and now requires WRITE; chunked playback works with READ only. |
| Crop/editor/local copies | Original handles and LOCAL_EDIT/WRITE authority; mutable decoded resources and jobs owned. Source resolution and final metadata/copy commits cannot choose a new session at callback time. |
| AI Edit/Create Image/Replicate lifecycle | Scoped references, original operation, explicit REMOTE_AI_EGRESS transport guard, owned connections/body/result streams, bounded/sanitized output and originating encrypted destination. Old results cannot import or publish after lock/unlock. Provider/model/moderation choices are unchanged. |
| AI settings | CREDENTIALS required at token save/removal, REMOTE_AI_EGRESS for network verification. The removal job is owned and final credential mutation is guarded. Standalone fake configuration adapters are used only by tests; production supplies the captured guard. |
| Browser Vault upload | Immutable selected Primary handles plus chooser callback/origin identity. BROWSER_UPLOAD_EGRESS required before plaintext preparation. Owned staging files removed on completion/revocation. A stale request cannot hand off another chooser's selection. |
| Browser downloads, image/video/stream acquisition | Captured Primary repository destination; unscoped network opener refuses execution. Guarded network/result reads and durable encrypted import. Metadata probes capture their own operation, own jobs/connections and check after HEAD before fallback GET/redirect/manifest work. |
| Backup export | Primary-only root/snapshot and BACKUP/EGRESS authority. Missing-index initialization additionally requires WRITE and cannot occur over existing material. Synthetic foreign/browser plaintext canaries cannot enter the archive. |
| Fresh backup restore | Fixed Primary restore staging and original authentication-attempt token, not an unlocked-session/global destination. Attempt guard covers final install and rollback ownership. Existing backup archive behavior and fault tests remain. |
| PIN/recovery/biometric | Fixed `PrimaryKeySlots`; CREDENTIALS required for unlocked slot mutations. Recovery authentication and locked unlock use captured authentication/biometric attempt identities. PIN rewrap/recovery preserve the Primary VDEK and slot formats. Biometric enrollment commits through the Primary boundary. |
| WireGuard, settings and updates | Existing single Primary Browser/VPN behavior retained; Browser state callbacks retain their owner. Non-Vault update machinery and provider models are not redesigned. No update/release is published. |

Low-level format primitives remain compatibility implementations; their production callers carry scoped authority. Phase 1 does not make a second repository, root, key slot or browser profile selectable.

## Corruption and write behavior

Repository/UI state distinguishes EMPTY, LOCKED, UNAVAILABLE, CORRUPT and READY. Missing index plus durable or unrecognized partial root material fails closed. Authentication, parse, version and inventory failures propagate; they cannot become an empty writable Vault. Existing/partial PIN, recovery or biometric material blocks fresh setup even when the PIN slot is incomplete. Failed operations preserve verified ciphertext and use only their owned staging/rollback resources. No owner data was inspected, migrated or rewritten during development.

## Required negative test matrix

These are synthetic automated tests, not physical-device claims. All listed tests must pass on the final candidate CI. Integrated tests exercise actual adapters where appropriate; pure authority tests additionally prove denial before side effects.

| # | Requirement | Test evidence |
| --- | --- | --- |
| 1 | Stale epoch cannot decrypt | `PrimaryRepositorySessionTest.forgedAndOldItemsCannotReachPayloadOrMetadata`, authority ABA tests. |
| 2 | Stale preview cannot publish | `ProtectedMediaOwnershipTest.oldEpochCannotHandOffPixelsDuringNewSession`, `MainActivitySessionTest` queued delivery. |
| 3 | Stale metadata cannot commit | `PrimaryRepositorySessionTest.staleRepositoryCannotReadOrCommitAfterSameKeyReauthentication`. |
| 4 | Stale AI result cannot import | `Phase1AsyncDestinationTest.oldAiAndBrowserResultsCannotImportAfterReauthentication`; unchanged index bytes. |
| 5 | Stale Browser download cannot import | Same test rejects the original captured sink before the download opener runs. |
| 6 | Lock/unlock cannot revive callback | `PrimaryBindingTest`, queued Activity delivery and `BrowserUploadRequestTest` callback replacement. |
| 7 | Expiry without timer rejects work | `PrimarySessionAuthorityTest`, `ScopedIoGuardTest`, Browser acquisition and delayed PIN promotion tests. |
| 8 | Video denied/closed after revocation | `VaultVideoDataSourceTest` close/descriptor/wipe tests and integrated scoped-video expiry test. |
| 9 | Foreign handle denied before lookup | `PrimaryScopeTest` lookup counter; `Phase1PrimarySlotsTest.foreignIdentityRejectedBeforeAnyRootAccess`; repository foreign handles. |
| 10 | Foreign colliding preview denied | `PrimaryScopeTest` same ID/revision foreign identity denied before cache identity/lookup; preview entry point validates before cache access. |
| 11 | Corruption cannot become writable empty | `PrimaryRepositorySessionTest.corruptionNeverBecomesEmptyWritableVault`, frozen corrupt/version/index tests. |
| 12 | Partial slots/root block fresh setup | `Phase1PrimarySlotsTest.partialRecoveryOrBiometricSlotsNeverPermitFreshSetup`, `PrimaryVaultSetupGuardTest`, `PartialPrimaryRootTest`. |
| 13 | PIN mutation Primary-only | `Phase1PrimarySlotsTest.pinChangeAndRecoveryTouchOnlyFixedPrimarySlotsAndPreserveVdek`; foreign slot canary unchanged. |
| 14 | Recovery mutation Primary-only | Same test, READ-only denial and preserved Primary VDEK/recovery compatibility tests. |
| 15 | Backup excludes synthetic foreign | `Phase1AsyncDestinationTest.primaryBackupCannotEnumerateSyntheticForeignRootOrPlaintextCanaries`; archive inventory checked. |
| 16 | AI result retains original destination | Old captured repository result test plus original-epoch Create/Image/Edit source audit. |
| 17 | Browser upload retains selected handle | `BrowserUploadRequestTest`; immutable handles/origin/callback, authoritative item policy and epoch guard. |
| 18 | Browser download retains destination | `Phase1AsyncDestinationTest.deadlineDuringBrowserAcquisitionCannotPromoteIndexOrPayload`; original sink and guarded opener. |
| 19 | Recreation retains intentional authority only | `ConfigurationRetentionTest.explicitActivityRecreationRetainsOnlyInProcessUnlockedSession`, recovery recreation tests. |
| 20 | Process restart begins locked | `ConfigurationRetentionTest.destroyedActivityCannotRestoreAuthorityIntoNewViewModel`. This exercises cold authority reconstruction; actual process kill/reboot remains a physical checkpoint. |

Additional tests cover collection foreign/stale handles, scoped video foreign collisions and expired readers after re-unlock, read-only reconcile/backup denial, and delayed Browser HEAD cancellation. Existing seek/tamper/format/fault tests remain.

## Adversarial review and observed failures

An independent whole-branch reviewer inspected the recovered implementation at `570bc845897273de792e395eb2eada4fad177a4b`. It found unowned Browser metadata probes, video read-completion revocation, implicit write authority in reconciliation/legacy video/backup initialization, and AI credential-removal ownership. The fixes are in the reviewed milestone and its follow-up. The reviewer did not run tests or provide physical assurance.

An isolated test-only regression branch (`phase1/regression-probe`, `69a87547f7e700daeeaca577127e86ba7f515203`) tested unchanged pre-fix production source. [Run 36894047101](https://github.com/traynor1987/Private-gallery-/actions/runs/36894047101) observed exactly the two expected assertion failures: video returned after revocation, and READ-only reconcile was admitted. The RED harness succeeded because the product tests failed as predicted; this is not a passing product gate. Fixes were applied after those failures were independently observed. No claim is made that every added test had an observed pre-fix RED run.

Fresh adapter CI at `570bc84` compiled/unit-tested successfully but failed instrumentation: one PIN adapter reused an input wiped by the compatibility unwrap; three standalone AI UI tests supplied no session authority. The PIN adapter now preserves a temporary input copy and rechecks at final promotion. UI tests inject explicit synthetic authority rather than permit unscoped production execution. These failed runs are retained as evidence, not accepted as green.

## Compatibility and immutable evidence

The existing Primary VDEK, PIN/recovery/biometric envelopes, v1–v6 index readers, v6 writes, payload encryption, PGVIDEO1, collections/Favourite, edits/provenance, origin/vaultOnly, Recently Deleted and backup/restore formats are retained. No new production crypto format, root or owner migration exists. Existing bounded legacy playback conversion is not a new Phase 1 owner-data migration program.

All 27 immutable fixture objects verify against the unchanged manifest. Manifest SHA-256: `cd26479fe9b077d81cd52b0c674900f0c475508e7081324cee396512770cc7bc`. Both fixture directories have zero diff from authoritative main; instrumentation archive/expected copies are byte-identical. `Phase0FrozenLegacyTest` checks old envelopes/indexes/payload/PGVIDEO1/archive and current v6 output against frozen expectations. The frozen corpus was not regenerated.

Fresh local source checks passed: diff whitespace, frozen hashes/copies, backup exclusion source policy and mutation checks, future format vectors, Browser Node tests, no-secret scan and WireGuard-only production path. A packaged mutation test is intentionally skipped without a fresh APK; fresh CI must run the packaged verifier. Historical retained Gradle outputs are not current evidence.

## Performance review and limits

Ordinary operations copy an already authenticated scoped key lease; they perform no KDF or key unwrap per preview/media read. The UI epoch binding retains no key copy. Payload revision hex formatting is linear without per-byte formatting overhead. Previews retain bounded encrypted disk/memory caching; PGVIDEO1 retains authenticated bounded chunking and seek. AI/Browser preparations remain bounded and stream where supported; new checks are lightweight monotonic/identity guards. PIN KDF is only a credential flow and final authorization follows preparation.

No fresh physical latency, scrolling, startup/seek, AI preparation or upload preparation measurements are claimed. The signed device checklist records those against the owner's platform and retained baseline. Immutable/base64 strings, codec/GPU state, provider-side processing already submitted and OEM behavior remain bounded platform risks, not claims of perfect erasure or remote cancellation.

## CI, signed packaging and physical gate

Fresh reviewed-milestone [push CI 36896863507](https://github.com/traynor1987/Private-gallery-/actions/runs/36896863507) completed/success on exact `cd363624318bb6b03926bbb3ddbf4c2c7a92f2ae`: every required step passed, including complete Android instrumentation. The additional integrated video/cold-session/Gallery follow-up is at `3ac11c67f7271268d7395261e5bab8a1834a4bc0`; [push 36898388860](https://github.com/traynor1987/Private-gallery-/actions/runs/36898388860) and [PR 36898395014](https://github.com/traynor1987/Private-gallery-/actions/runs/36898395014) were in progress at this report update. The final documentation-only handoff commit must also complete its own push/PR runs; the final response records their exact HEAD and terminal conclusions. Pending runs are not passing evidence. Required Android CI includes JVM suites, lint, debug/test APKs, all source/frozen/vector/no-secret/VPN gates, merged/packaged backup checks, isolated Phase 0 evidence package compilation/tests and complete API36 Google-ATD instrumentation. Existing Phase 0 evidence is preserved; no satisfied admission gate is reopened.

The required workflow is [Signed Device Acceptance Build](https://github.com/traynor1987/Private-gallery-/actions/workflows/signed-device-test.yml), dispatched on `phase1/primary-scoped-security` at the exact handoff SHA. Its branch allowlist includes Phase 1. It requires the permanent release signer, refuses debug-signed release, tests/lints/builds release, verifies release backup exclusions/retired runtime/signature and uploads APK/SHA256/certificate/build SHA. It is artifact-only and never creates a release. Standing owner authorization now permits autonomous dispatch through the GitHub workflow UI when replacement exact-head CI is green. Superseded signed run #52 / `36903070049` succeeded on `77c95d1`; its APK is withdrawn from acceptance and does not satisfy the replacement candidate gate.

[PHYSICAL_ACCEPTANCE.md](docs/phase1/PHYSICAL_ACCEPTANCE.md) describes the in-place same-signer checks and safe synthetic-only fault/credential cases. The replacement Phase 1 candidate has no accepted signed artifact, public certificate comparison or physical pass until those actions return measured evidence. Do not uninstall, clear owner data, downgrade, replace the signer or run destructive synthetic instrumentation against the owner installation. Do not rotate owner recovery for testing.

## Post-candidate setup admission correction

The owner paused physical acceptance after [RED probe 36902691921](https://github.com/traynor1987/Private-gallery-/actions/runs/36902691921), source `cc733b15c7a39f95e3ac1f59bbf118ae45012002`. Its successful harness observed three expected product assertion failures, including `inaccessibleNestedPrimaryMaterialCannotPermitFreshKeyCreation`; it is RED evidence, not a product pass. The guard blob `37f4aa3367b9727dade8e42bab6fb24075151de0` is identical to the superseded candidate. `walkTopDown` could skip inaccessible nested directories while only top-level listing failure was checked. `PinVaultKeyStore.isConfigured/create` consumed that result and could admit fresh key creation.

`PrimaryStorageInventory` replaces permissive traversal with checked NIO enumeration and NOFOLLOW attribute reads. Missing paths require a readable/searchable parent inventory and an explicit missing-path stat; files, unknown entries, special types, links, inaccessible/failed traversal and partial material deny. Existing empty directories are completely enumerated. Parent/child identity, modification time and names are rechecked; unavailable file identity denies. Restore-stage absence and Vault inventory share one parent snapshot. Traversal and listing are bounded to 1024 visited directories and 4096 entries per directory; excess inventory denies instead of consuming unbounded resources. The missing-index path uses the same check. No crypto formats or owner data are rewritten.

Related tests cover inaccessible nested material and empty directories, execute-permission failure, missing-index denial, unknown/unreadable files, missing/non-directory parents, dangling/root/nested links and cycles, unavailable filesystems, traversal bounds, and genuine initial key creation/unlock. A separate test-only [probe branch](https://github.com/traynor1987/Private-gallery-/tree/phase1/setup-admission-red), `cbc84d9b265a6376eb2eef15be8e8cc25239d0cf`, retains unchanged superseded production inputs and adds permission/concurrency probes plus a genuine-empty positive control. [Run 36905797503](https://github.com/traynor1987/Private-gallery-/actions/runs/36905797503) completed successfully as a RED harness: exactly five named admission assertions failed, with zero errors; genuine-empty setup/key creation passed. This independently reproduces the permission flaw and setup/restore concurrency gap on unchanged superseded production source.

Independent read-only adversarial review confirmed the traversal fix and identified a pre-existing setup/restore transaction race. The correction serializes initial setup admission and final credential promotion with the repository Primary I/O transaction, with KDF preparation outside that lock and consistent I/O-before-preferences ordering. The real restore adapter is paused before root/stage resolution in the concurrency test; fresh key creation must wait and cannot save credentials while restore owns the transaction. The RED run observed the old adapter saving before that transaction released. The fix pass addresses this review finding; no second reviewer or physical assurance is claimed. Checked snapshots detect observed changes; arbitrary out-of-process mutation after validation is not represented as an atomic filesystem guarantee. Exact replacement CI must prove the targeted regression suite, full Phase 1 matrix, frozen fixtures, Phase 0 evidence package and Android instrumentation. Local Gradle execution was blocked before compilation because its absent wrapper distribution could not be downloaded in this environment; no local JVM/Android pass is claimed. Source checks passed: 27 frozen hashes and unchanged fixture copies, backup source policy, Python tests (one SDK-dependent packaged-mutation skip locally), five format vectors, three Node tests, WireGuard-only audit and whitespace. CI must execute packaged mutation checks with its SDK.

The corrected candidate remains pending until green push AND PR CI and a new permanent-signer build on its exact HEAD. Physical acceptance is NOT RUN. The original report's historical CI does not validate this correction. No artifact from `77c95d1` may be offered for owner acceptance.

Standing owner authorization (1 October 2026) permits routine milestone branches/commits/pushes/PRs, CI/retries, internal signed builds/artifact inspection, legitimate scoped fixes, evidence updates, and safe merge/post-merge verification once every mandatory implementation/automated/signed/physical/security gate passes. It does not authorize data destruction, signer/recovery changes, skipped physical evidence, a public release, or automatically starting the next phase. Owner physical action remains the stop boundary.

## Exit recommendation

Implementation and automated gates do not substitute for signed release packaging and required physical acceptance. Phase 2 review is not admitted until the final exact candidate CI, permanent-signer build and in-place device acceptance pass. This report was finalized before terminal handoff CI; successful CI alone still leaves signing and physical acceptance open. No production Hidden container/key/slots/recovery/UI, Jenna Protocol, transfers, Hidden Browser, Social Hub, VPS, Tor, disguises, owner migration or public release was created. Stop after Phase 1; merge only after all required gates pass under standing authorization, and do not begin Phase 2 automatically.

PHASE 1 RESULT: NO-GO FOR PHASE 2
